package com.codeanalyzer.service;

import com.codeanalyzer.ai.*;
import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AnalysisService {

    private final SubmissionDAO submissionDAO = new SubmissionDAO();
    private final AiAnalysisDAO analysisDAO = new AiAnalysisDAO();
    private final GeminiApiClient geminiClient = new GeminiApiClient();
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;
    private volatile boolean stoppedByAiLimit = false;
    private volatile String lastStopReason = "";

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void addLogListener(Consumer<String> listener) {
        if (listener != null && !logListeners.contains(listener)) {
            logListeners.add(listener);
        }
    }

    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    public void cancel() { this.cancelled = true; }

    public boolean isRunning() { return running.get(); }

    public boolean wasStoppedByAiLimit() { return stoppedByAiLimit; }

    public String getLastStopReason() { return lastStopReason; }

    private void log(String msg) {
        System.out.println("[AnalysisService] " + msg);
        if (logCallback != null) logCallback.accept(msg);
        for (Consumer<String> listener : logListeners) {
            listener.accept(msg);
        }
    }

    /**
     * Phân tích tất cả submissions chưa phân tích.
     */
    public int analyzeUnanalyzed() {
        if (!running.compareAndSet(false, true)) {
            log("Đang có tiến trình phân tích AI khác chạy. Bỏ qua yêu cầu mới.");
            return 0;
        }
        try {
            cancelled = false;
            stoppedByAiLimit = false;
            lastStopReason = "";
            int batchSize = AppConfig.getAnalysisBatchSize();
            List<Submission> subs = submissionDAO.findUnanalyzed(batchSize);
            log("Tìm thấy " + subs.size() + " submissions cần phân tích");
            return analyzeSubmissions(subs, "submissions");
        } finally {
            running.set(false);
        }
    }

    /**
     * Phân tích submissions chưa phân tích của 1 account.
     */
    public int analyzeByAccount(int accountId) {
        if (!running.compareAndSet(false, true)) {
            log("Đang có tiến trình phân tích AI khác chạy. Bỏ qua yêu cầu mới.");
            return 0;
        }
        try {
            cancelled = false;
            stoppedByAiLimit = false;
            lastStopReason = "";
            int batchSize = AppConfig.getAnalysisBatchSize();
            List<Submission> subs = submissionDAO.findUnanalyzedByAccount(accountId, batchSize);
            log("Tìm thấy " + subs.size() + " submissions cần phân tích cho account " + accountId);
            return analyzeSubmissions(subs, "submissions của account " + accountId);
        } finally {
            running.set(false);
        }
    }

    /**
     * Phân tích một submission cụ thể, dùng cho trang chi tiết submission.
     */
    public AiAnalysis analyzeSubmission(long submissionDbId, boolean reanalyze) {
        if (!running.compareAndSet(false, true)) {
            log("Đang có tiến trình phân tích AI khác chạy. Bỏ qua yêu cầu mới.");
            return null;
        }
        Submission sub = null;
        try {
            cancelled = false;
            stoppedByAiLimit = false;
            lastStopReason = "";

            sub = submissionDAO.findById(submissionDbId);
            if (sub == null) {
                log("Không tìm thấy submission #" + submissionDbId);
                return null;
            }

            AiAnalysis existing = analysisDAO.findBySubmissionId(submissionDbId);
            if (existing != null && !reanalyze) {
                log("Submission " + sub.getSubmissionId() + " đã có phân tích AI.");
                return existing;
            }

            return analyzeSingleSubmission(sub, reanalyze);
        } catch (GeminiApiClient.PermanentGeminiException e) {
            if (GeminiApiClient.LIMIT_REACHED_MESSAGE.equals(e.getMessage())) {
                stoppedByAiLimit = true;
                log(GeminiApiClient.LIMIT_REACHED_MESSAGE);
            } else {
                lastStopReason = toUserFriendlyGeminiError(e.getMessage());
                log("Dừng phân tích vì lỗi cấu hình Gemini: " + lastStopReason);
            }
            if (sub != null && !GeminiApiClient.LIMIT_REACHED_MESSAGE.equals(e.getMessage())) {
                markAnalysisError(sub, lastStopReason);
            }
            return null;
        } catch (Exception e) {
            lastStopReason = e.getMessage() != null ? e.getMessage() : "Lỗi phân tích AI.";
            log("Lỗi phân tích submission #" + submissionDbId + ": " + lastStopReason);
            if (sub != null) {
                markAnalysisError(sub, lastStopReason);
            }
            return null;
        } finally {
            running.set(false);
        }
    }

    private int analyzeSubmissions(List<Submission> subs, String label) {
        int analyzed = 0;
        for (Submission sub : subs) {
            if (cancelled) { log("Phân tích bị hủy!"); break; }

            try {
                AiAnalysis analysis = analyzeSingleSubmission(sub, false);
                if (analysis != null) {
                    analyzed++;
                }

            } catch (GeminiApiClient.PermanentGeminiException e) {
                if (GeminiApiClient.LIMIT_REACHED_MESSAGE.equals(e.getMessage())) {
                    stoppedByAiLimit = true;
                    log(GeminiApiClient.LIMIT_REACHED_MESSAGE);
                } else {
                    lastStopReason = toUserFriendlyGeminiError(e.getMessage());
                    log("Dừng phân tích vì lỗi cấu hình Gemini: " + lastStopReason);
                    markAnalysisError(sub, lastStopReason);
                }
                break;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "Lỗi phân tích AI.";
                log("Lỗi phân tích " + sub.getProblemId() + ": " + reason);
                markAnalysisError(sub, reason);
            }
        }

        log("=== Đã phân tích " + analyzed + "/" + subs.size() + " " + label + " ===");
        return analyzed;
    }

    private AiAnalysis analyzeSingleSubmission(Submission sub, boolean reanalyze) {
        if (sub.getSourceCode() == null || sub.getSourceCode().isBlank()) {
            log("Submission " + sub.getSubmissionId() + " chưa có source code để phân tích.");
            markAnalysisError(sub, "Submission chưa có source code để phân tích.");
            return null;
        }

        log((reanalyze ? "Phân tích lại: " : "Phân tích: ")
                + sub.getProblemId() + " - " + sub.getProblemName());
        String prompt = CodeAnalysisPrompt.buildAnalysisPrompt(
                sub.getSourceCode(), sub.getLanguage(), sub.getProblemName());

        String response = geminiClient.sendMessageWithRetry(prompt, 3);
        if (response == null) {
            log("✗ Không nhận được response cho " + sub.getProblemId());
            markAnalysisError(sub, "Không nhận được response từ Gemini.");
            return null;
        }

        AiAnalysis analysis = AnalysisParser.parseAnalysis(response, sub.getId());
        if (analysis == null) {
            log("✗ Không parse được kết quả AI cho " + sub.getProblemId());
            markAnalysisError(sub, "Không parse được kết quả JSON từ Gemini.");
            return null;
        }

        long id = reanalyze ? analysisDAO.saveOrUpdate(analysis) : analysisDAO.insert(analysis);
        if (id <= 0) {
            log("✗ Không lưu được kết quả AI cho " + sub.getProblemId());
            markAnalysisError(sub, "Không lưu được kết quả AI vào database.");
            return null;
        }

        submissionDAO.updateAnalysisStatus(sub.getId(), SubmissionDAO.STATUS_ANALYZED, null);
        log("✓ Hoàn tất: " + sub.getProblemId() +
            " | DS: " + analysis.getDataStructures() +
            " | Algo: " + analysis.getAlgorithms() +
            " | AI Score: " + analysis.getAiUsageScore());
        AiAnalysis savedAnalysis = analysisDAO.findBySubmissionId(sub.getId());
        return savedAnalysis != null ? savedAnalysis : analysis;
    }

    private void markAnalysisError(Submission sub, String message) {
        if (sub == null) {
            return;
        }
        submissionDAO.updateAnalysisStatus(sub.getId(), SubmissionDAO.STATUS_ERROR,
                message != null && !message.isBlank() ? message : "Lỗi phân tích AI.");
    }

    private String toUserFriendlyGeminiError(String message) {
        if (message == null || message.isBlank()) {
            return "Lỗi cấu hình Gemini.";
        }

        String lower = message.toLowerCase();
        if (lower.contains("reported as leaked")) {
            return "Gemini API key đã bị Google đánh dấu là bị lộ. Hãy tạo API key mới và cập nhật trong trang Cài đặt.";
        }
        if (lower.contains("api_key_invalid") || lower.contains("api key not valid")) {
            return "Gemini API key không hợp lệ. Hãy kiểm tra hoặc tạo API key mới trong Google AI Studio.";
        }
        if (lower.contains("permission_denied") || lower.contains("403")) {
            return "Gemini API bị từ chối quyền 403. Hãy kiểm tra API key hoặc tạo API key mới.";
        }
        if (lower.contains("invalid_argument")) {
            return "Gemini từ chối request. Hãy kiểm tra model, max token hoặc nội dung gửi lên AI.";
        }

        String firstLine = message.lines().findFirst().orElse(message).trim();
        if (firstLine.length() > 220) {
            firstLine = firstLine.substring(0, 220) + "...";
        }
        return "Lỗi Gemini: " + firstLine;
    }

    public AiAnalysis getAnalysis(long submissionId) { return analysisDAO.findBySubmissionId(submissionId); }
    public List<AiAnalysis> getAnalysesByAccount(int accountId) { return analysisDAO.findByAccountId(accountId); }
    public int countAnalyses() { return analysisDAO.count(); }
    public List<String> getAllDataStructures() { return analysisDAO.getAllDataStructures(); }
    public List<String> getAllAlgorithms() { return analysisDAO.getAllAlgorithms(); }
}
