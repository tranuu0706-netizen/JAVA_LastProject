package com.codeanalyzer.crawler;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.model.Submission;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Crawler cho Codeforces sử dụng Selenium Edge.
 * Crawl danh sách submission và lấy source code.
 */
public class CodeforcesCrawler {

    private final BrowserManager browserManager;
    private Consumer<String> logCallback;

    public CodeforcesCrawler(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String message) {
        System.out.println("[CF Crawler] " + message);
        if (logCallback != null) logCallback.accept("[CF] " + message);
    }

    /**
     * Crawl submissions cho một username trên Codeforces.
     * @return Danh sách submissions đã crawl (chỉ AC, chưa có trong DB).
     */
    public List<Submission> crawlSubmissions(String username, int accountId, 
            java.util.function.BiPredicate<String, String> existsCheck) {
        return crawlSubmissions(username, accountId, existsCheck, null);
    }

    public List<Submission> crawlSubmissions(String username, int accountId,
            java.util.function.BiPredicate<String, String> existsCheck,
            java.util.function.BiConsumer<String, LocalDateTime> submittedAtUpdater) {
        List<Submission> results = new ArrayList<>();
        WebDriver driver = browserManager.getDriver();
        int maxSubs = AppConfig.getMaxSubmissionsPerCrawl();

        try {
            String url = "https://codeforces.com/submissions/" + username;
            log("Đang mở trang submissions: " + url);
            driver.get(url);
            BrowserManager.randomDelay();

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table.status-frame-datatable")));

            List<WebElement> rowElements = driver.findElements(By.cssSelector("table.status-frame-datatable tr[data-submission-id]"));
            log("Tìm thấy " + rowElements.size() + " submissions trên trang");

            List<String> subIds = new ArrayList<>();
            for (WebElement r : rowElements) {
                try {
                    String id = r.getAttribute("data-submission-id");
                    if (id != null && !id.isEmpty()) subIds.add(id);
                } catch (StaleElementReferenceException e) { }
            }

            if (subIds.isEmpty()) {
                BrowserManager.randomDelay();
                rowElements = driver.findElements(By.cssSelector("table.status-frame-datatable tr[data-submission-id]"));
                for (WebElement r : rowElements) {
                    try {
                        String id = r.getAttribute("data-submission-id");
                        if (id != null && !id.isEmpty()) subIds.add(id);
                    } catch (Exception ignored) {}
                }
            }

            Map<String, LocalDateTime> submittedAtById = fetchCodeforcesSubmittedAt(username, Math.max(subIds.size(), maxSubs));

            int crawled = 0;
            for (String subId : subIds) {
                if (crawled >= maxSubs) break;
                try {
                    // Bỏ qua submission đã có - KHÔNG đếm vào giới hạn
                    if (existsCheck.test(subId, "CODEFORCES")) {
                        LocalDateTime submittedAt = submittedAtById.get(subId);
                        if (submittedAt != null && submittedAtUpdater != null) {
                            submittedAtUpdater.accept(subId, submittedAt);
                        }
                        log("Bỏ qua submission " + subId + " (đã có)");
                        continue;
                    }

                    List<WebElement> matchRows = driver.findElements(By.cssSelector("tr[data-submission-id='" + subId + "']"));
                    if (matchRows.isEmpty()) continue;
                    WebElement row = matchRows.get(0);

                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    if (cells.size() < 6) continue;

                    int verdictIdx = -1;
                    for (int i = 3; i < cells.size(); i++) {
                        String txt = cells.get(i).getText().trim();
                        if (txt.contains("Accepted") || txt.equals("AC")) {
                            verdictIdx = i;
                            break;
                        }
                    }

                    if (verdictIdx == -1) continue;

                    // Parse submission info
                    String timeText = cells.get(1).getText().trim();
                    String language = cells.get(verdictIdx - 1).getText().trim();
                    int problemIdx = Math.max(2, verdictIdx - 2);
                    String problemText = cells.get(problemIdx).getText().trim();

                    String problemId = "";
                    String problemName = problemText;
                    String contestId = "";
                    try {
                        WebElement problemLink = cells.get(problemIdx).findElement(By.tagName("a"));
                        String href = problemLink.getAttribute("href");
                        if (href != null && href.contains("/problem/")) {
                            String[] parts = href.split("/");
                            String problemIndex = "";
                            for (int i = 0; i < parts.length; i++) {
                                if ("contest".equals(parts[i]) && i + 1 < parts.length) {
                                    contestId = parts[i + 1];
                                }
                                if ("problem".equals(parts[i]) && i + 1 < parts.length) {
                                    if (i > 0 && "problemset".equals(parts[i - 1]) && i + 2 < parts.length) {
                                        contestId = parts[i + 1];
                                        problemIndex = parts[i + 2];
                                    } else {
                                        problemIndex = parts[i + 1];
                                    }
                                }
                            }
                            problemId = contestId + problemIndex;
                        }
                        problemName = problemLink.getText().trim();
                    } catch (Exception ignored) {}

                    Submission sub = new Submission();
                    sub.setAccountId(accountId);
                    sub.setSubmissionId(subId);
                    sub.setProblemId(problemId);
                    sub.setProblemName(problemName);
                    sub.setContestId(contestId);
                    sub.setLanguage(language);
                    sub.setVerdict("AC");
                    sub.setPlatform("CODEFORCES");
                    LocalDateTime submittedAt = submittedAtById.get(subId);
                    if (submittedAt == null) {
                        submittedAt = parseCodeforcesTimeText(timeText);
                    }
                    if (submittedAt == null) {
                        System.err.println("Không thể parse thời gian CF: " + timeText);
                    }
                    sub.setSubmittedAt(submittedAt);

                    results.add(sub);
                    crawled++;

                } catch (StaleElementReferenceException e) {
                    log("Element stale, bỏ qua row");
                } catch (Exception e) {
                    log("Lỗi parse row: " + e.getMessage());
                }
            }

            // Lấy source code sau khi đã parse xong DOM để tránh StaleElementReferenceException
            List<Submission> finalResults = new ArrayList<>();
            for (Submission sub : results) {
                String sourceCode = crawlSourceCode(driver, sub.getContestId(), sub.getSubmissionId());
                if (sourceCode != null && !sourceCode.isEmpty()) {
                    sub.setSourceCode(sourceCode);
                    finalResults.add(sub);
                    log("Crawled: " + sub.getProblemId() + " - " + sub.getProblemName() + " [" + sub.getSubmissionId() + "]");
                }
                BrowserManager.randomDelay();
            }
            results = finalResults;

            log("Hoàn tất crawl CF cho " + username + ": " + results.size() + " submissions");

        } catch (TimeoutException e) {
            log("Timeout khi load trang submissions cho " + username);
        } catch (Exception e) {
            log("Lỗi crawl CF: " + e.getMessage());
        }

        return results;
    }

    /**
     * Lấy thời điểm nộp chuẩn từ Codeforces API.
     * creationTimeSeconds là epoch time của submission, đáng tin hơn text trong HTML.
     */
    private Map<String, LocalDateTime> fetchCodeforcesSubmittedAt(String username, int count) {
        Map<String, LocalDateTime> result = new HashMap<>();
        try {
            String encodedHandle = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String url = "https://codeforces.com/api/user.status?handle=" + encodedHandle
                    + "&from=1&count=" + Math.max(1, count);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log("Không lấy được thời gian từ Codeforces API, HTTP " + response.statusCode());
                return result;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("status") || !"OK".equals(root.get("status").getAsString())) {
                log("Codeforces API không trả status OK khi lấy thời gian submission");
                return result;
            }

            JsonArray submissions = root.getAsJsonArray("result");
            ZoneId zone = ZoneId.systemDefault();
            for (var item : submissions) {
                JsonObject obj = item.getAsJsonObject();
                if (!obj.has("id") || !obj.has("creationTimeSeconds")) continue;
                String id = obj.get("id").getAsString();
                long epochSeconds = obj.get("creationTimeSeconds").getAsLong();
                result.put(id, LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone));
            }
        } catch (Exception e) {
            log("Không lấy được thời gian từ Codeforces API: " + e.getMessage());
        }
        return result;
    }

    private LocalDateTime parseCodeforcesTimeText(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;
        String cleanTime = timeText
                .replaceAll("UTC[\\+\\-]?\\d+", "")
                .replaceAll("\\s+", " ")
                .trim();

        DateTimeFormatter[] formats = new DateTimeFormatter[] {
                new DateTimeFormatterBuilder().parseCaseInsensitive()
                        .appendPattern("MMM/dd/yyyy HH:mm")
                        .toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive()
                        .appendPattern("MMM/dd/yyyy HH:mm:ss")
                        .toFormatter(Locale.ENGLISH)
        };

        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDateTime.parse(cleanTime, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Crawl source code từ trang submission detail.
     */
    private String crawlSourceCode(WebDriver driver, String contestId, String submissionId) {
        try {
            String currentUrl = driver.getCurrentUrl();
            String subUrl = "https://codeforces.com/contest/" + contestId + "/submission/" + submissionId;
            driver.get(subUrl);
            BrowserManager.randomDelay();

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Try to find source code element
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("program-source-text")));
                WebElement codeElement = driver.findElement(By.id("program-source-text"));
                return codeElement.getText();
            } catch (Exception e1) {
                try {
                    WebElement codeElement = driver.findElement(By.cssSelector("pre#program-source-text"));
                    return codeElement.getText();
                } catch (Exception e2) {
                    try {
                        WebElement codeElement = driver.findElement(By.cssSelector(".source-popup pre"));
                        return codeElement.getText();
                    } catch (Exception e3) {
                        log("Không tìm thấy source code cho submission " + submissionId);
                    }
                }
            }

            // Navigate back
            driver.navigate().back();
            BrowserManager.randomDelay();

        } catch (Exception e) {
            log("Lỗi crawl source code: " + e.getMessage());
        }
        return "";
    }
}
