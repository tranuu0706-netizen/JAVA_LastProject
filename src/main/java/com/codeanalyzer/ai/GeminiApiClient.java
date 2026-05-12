package com.codeanalyzer.ai;

import com.codeanalyzer.config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client gọi Google Gemini API để phân tích code.
 */
public class GeminiApiClient {

    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Gson gson = new Gson();
    private static final Object RATE_LIMIT_LOCK = new Object();
    private static long nextAllowedRequestAtMs = 0L;
    private final HttpClient httpClient;

    public GeminiApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Gửi prompt tới Gemini API và nhận response.
     * 
     * @param prompt Nội dung prompt
     * @return Response text từ Gemini
     */
    public String sendMessage(String prompt) throws IOException, InterruptedException {
        String apiKey = AppConfig.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new PermanentGeminiException(
                    "Gemini API key chưa được cấu hình. Đặt GEMINI_API_KEY trong terminal đang chạy app, "
                            + "hoặc truyền -Dgemini.api.key=KEY_MOI, hoặc cập nhật gemini.api.key trong application.properties.");
        }

        // Build request body for Gemini API
        JsonObject requestBody = new JsonObject();

        com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
        JsonObject content = new JsonObject();

        com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);

        content.add("parts", parts);
        contents.add(content);

        requestBody.add("contents", contents);

        String model = AppConfig.getGeminiModel();

        // Add generation config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", AppConfig.getGeminiMaxOutputTokens());
        genConfig.addProperty("temperature", 0.0);
        genConfig.addProperty("responseMimeType", "application/json");
        if (model.contains("2.5")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingBudget", 0);
            genConfig.add("thinkingConfig", thinkingConfig);
        }
        requestBody.add("generationConfig", genConfig);

        String url = String.format(API_URL_TEMPLATE, model) + "?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .timeout(Duration.ofSeconds(60))
                .build();

        waitForRequestSlot();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            long waitSeconds = parseRetryDelay(response.body());
            if (isQuotaExhausted(response.body())) {
                throw new DailyQuotaException("Gemini báo hết hoặc chưa cấp quota: " + summarizeError(response.body()));
            }
            throw new RateLimitException("Gemini đang giới hạn tốc độ/RPM/TPM: " + summarizeError(response.body()), waitSeconds);
        }

        if (response.statusCode() != 200) {
            String errorMessage = "Gemini API error " + response.statusCode() + ": " + response.body();
            if (isPermanentApiError(response.body())) {
                throw new PermanentGeminiException(errorMessage);
            }
            throw new IOException(errorMessage);
        }

        // Parse response
        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
        if (responseJson.has("candidates")) {
            com.google.gson.JsonArray candidates = responseJson.getAsJsonArray("candidates");
            if (!candidates.isEmpty()) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                String finishReason = getJsonString(candidate, "finishReason");
                if ("MAX_TOKENS".equals(finishReason)) {
                    throw new IOException("Gemini trả JSON bị cắt do chạm maxOutputTokens="
                            + AppConfig.getGeminiMaxOutputTokens()
                            + ". Hãy tăng gemini.max.output.tokens hoặc rút ngắn prompt/code.");
                }
                if (candidate.has("content")) {
                    JsonObject contentObj = candidate.getAsJsonObject("content");
                    if (contentObj.has("parts")) {
                        com.google.gson.JsonArray partArray = contentObj.getAsJsonArray("parts");
                        if (!partArray.isEmpty()) {
                            JsonObject firstPart = partArray.get(0).getAsJsonObject();
                            if (firstPart.has("text")) {
                                return firstPart.get("text").getAsString();
                            }
                        }
                    }
                }
            }
        }
        throw new IOException("Không thể parse response từ Gemini API");
    }

    /**
     * Chặn mọi thread gửi request Gemini quá sát nhau.
     */
    private void waitForRequestSlot() throws InterruptedException {
        int delayMs = AppConfig.getGeminiRequestDelayMs();
        if (delayMs <= 0) return;

        synchronized (RATE_LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = nextAllowedRequestAtMs - now;
            if (waitMs > 0) {
                Thread.sleep(waitMs);
            }
            nextAllowedRequestAtMs = System.currentTimeMillis() + delayMs;
        }
    }

    /**
     * Parse retry delay (giây) từ error response của Gemini API.
     */
    private long parseRetryDelay(String responseBody) {
        try {
            JsonObject error = extractErrorObject(responseBody);
            if (error != null && error.has("details")) {
                for (var detailEl : error.getAsJsonArray("details")) {
                    if (!detailEl.isJsonObject()) continue;
                    JsonObject detail = detailEl.getAsJsonObject();
                    if (detail.has("retryDelay")) {
                        String retryDelay = detail.get("retryDelay").getAsString();
                        Matcher matcher = Pattern.compile("(\\d+)s").matcher(retryDelay);
                        if (matcher.find()) {
                            return Long.parseLong(matcher.group(1));
                        }
                    }
                }
            }
            // Tìm pattern "retryDelay": "42s" trong response
            Pattern pattern = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"");
            Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
            // Tìm pattern "Please retry in 42.3s"
            Pattern pattern2 = Pattern.compile("retry in (\\d+)\\.?\\d*s");
            Matcher matcher2 = pattern2.matcher(responseBody);
            if (matcher2.find()) {
                return Long.parseLong(matcher2.group(1));
            }
        } catch (Exception ignored) {
        }
        return 45; // Mặc định chờ 45 giây
    }

    private boolean isQuotaExhausted(String responseBody) {
        if (responseBody == null) return false;
        String body = responseBody.toLowerCase();
        return body.contains("quota")
                || body.contains("free_tier")
                || body.contains("perday")
                || body.contains("per_day")
                || body.contains("per day")
                || body.contains("requestsperday")
                || body.contains("daily");
    }

    private boolean isPermanentApiError(String responseBody) {
        if (responseBody == null) return false;
        return responseBody.contains("API_KEY_INVALID")
                || responseBody.contains("API key expired")
                || responseBody.contains("PERMISSION_DENIED")
                || responseBody.contains("INVALID_ARGUMENT");
    }

    private String summarizeError(String responseBody) {
        try {
            JsonObject error = extractErrorObject(responseBody);
            if (error != null) {
                StringBuilder summary = new StringBuilder();
                if (error.has("message")) {
                    summary.append(error.get("message").getAsString());
                }
                if (error.has("details") && error.get("details").isJsonArray()) {
                    for (var detailEl : error.getAsJsonArray("details")) {
                        if (!detailEl.isJsonObject()) continue;
                        JsonObject detail = detailEl.getAsJsonObject();
                        if (detail.has("violations") && detail.get("violations").isJsonArray()) {
                            for (var violationEl : detail.getAsJsonArray("violations")) {
                                if (!violationEl.isJsonObject()) continue;
                                JsonObject violation = violationEl.getAsJsonObject();
                                summary.append(System.lineSeparator()).append("- quota_metric: ")
                                        .append(getJsonString(violation, "quotaMetric"));
                                summary.append(System.lineSeparator()).append("  quota_id: ")
                                        .append(getJsonString(violation, "quotaId"));
                                if (violation.has("quotaDimensions") && violation.get("quotaDimensions").isJsonObject()) {
                                    JsonObject dimensions = violation.getAsJsonObject("quotaDimensions");
                                    for (String key : dimensions.keySet()) {
                                        summary.append(System.lineSeparator()).append("  ")
                                                .append(key).append(": ")
                                                .append(dimensions.get(key).getAsString());
                                    }
                                }
                                if (violation.has("quotaValue")) {
                                    summary.append(System.lineSeparator()).append("  quota_value: ")
                                            .append(violation.get("quotaValue").getAsString());
                                }
                            }
                        }
                    }
                }
                return summary.toString();
            }
        } catch (Exception ignored) {
        }
        if (responseBody == null) return "";
        return responseBody;
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private JsonObject extractErrorObject(String responseBody) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root != null && root.has("error") && root.get("error").isJsonObject()) {
            return root.getAsJsonObject("error");
        }
        return null;
    }

    /**
     * Gửi message với retry logic thông minh.
     * Tự động chờ khi bị rate limit (429).
     */
    public String sendMessageWithRetry(String prompt, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return sendMessage(prompt);
            } catch (DailyQuotaException e) {
                System.err.println("❌ " + e.getMessage());
                throw new PermanentGeminiException(e.getMessage());
            } catch (RateLimitException e) {
                long waitSec = e.getRetryAfterSeconds();
                if (i >= maxRetries) {
                    System.err.println("❌ Vẫn bị rate limit sau " + (i + 1) + " lần thử: " + e.getMessage());
                    return null;
                }
                System.err.println("⏳ " + e.getMessage() + ". Chờ " + waitSec + "s... (lần " + (i + 1) + ")");
                try {
                    Thread.sleep(waitSec * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (PermanentGeminiException e) {
                System.err.println("❌ Lỗi vĩnh viễn, dừng batch: " + e.getMessage());
                throw e;
            } catch (IOException e) {
                String msg = e.getMessage();
                // Lỗi vĩnh viễn → KHÔNG retry, thoát ngay
                if (msg != null && isPermanentApiError(msg)) {
                    System.err.println("❌ Lỗi vĩnh viễn, dừng batch: " + msg);
                    throw new PermanentGeminiException(msg);
                }
                System.err.println("Gemini API lỗi (lần " + (i + 1) + "): " + msg);
                if (i < maxRetries) {
                    try {
                        long backoffMs = msg != null && msg.contains("503") ? 10000L * (i + 1) : 3000L * (i + 1);
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi không xác định: " + e.getMessage());
                break;
            }
        }
        return null;
    }

    /**
     * Exception riêng cho Rate Limit (429).
     */
    public static class RateLimitException extends IOException {
        private final long retryAfterSeconds;

        public RateLimitException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class DailyQuotaException extends IOException {
        public DailyQuotaException(String message) {
            super(message);
        }
    }

    public static class PermanentGeminiException extends RuntimeException {
        public PermanentGeminiException(String message) {
            super(message);
        }
    }
}
