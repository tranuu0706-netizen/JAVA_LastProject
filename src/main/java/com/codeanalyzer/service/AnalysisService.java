package com.codeanalyzer.service;

import com.codeanalyzer.ai.*;
import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;

import java.util.List;
import java.util.function.Consumer;

public class AnalysisService {

    private final SubmissionDAO submissionDAO = new SubmissionDAO();
    private final AiAnalysisDAO analysisDAO = new AiAnalysisDAO();
    private final GeminiApiClient geminiClient = new GeminiApiClient();
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void cancel() { this.cancelled = true; }

    private void log(String msg) {
        System.out.println("[AnalysisService] " + msg);
        if (logCallback != null) logCallback.accept(msg);
    }

    /**
     * Phân tích tất cả submissions chưa phân tích.
     */
    public int analyzeUnanalyzed() {
        cancelled = false;
        int batchSize = AppConfig.getAnalysisBatchSize();
        List<Submission> subs = submissionDAO.findUnanalyzed(batchSize);
        log("Tìm thấy " + subs.size() + " submissions cần phân tích");
        return analyzeSubmissions(subs, "submissions");
    }

    /**
     * Phân tích submissions chưa phân tích của 1 account.
     */
    public int analyzeByAccount(int accountId) {
        cancelled = false;
        int batchSize = AppConfig.getAnalysisBatchSize();
        List<Submission> subs = submissionDAO.findUnanalyzedByAccount(accountId, batchSize);
        log("Tìm thấy " + subs.size() + " submissions cần phân tích cho account " + accountId);
        return analyzeSubmissions(subs, "submissions của account " + accountId);
    }

    private int analyzeSubmissions(List<Submission> subs, String label) {
        int analyzed = 0;
        for (Submission sub : subs) {
            if (cancelled) { log("Phân tích bị hủy!"); break; }

            try {
                log("Phân tích: " + sub.getProblemId() + " - " + sub.getProblemName());
                String prompt = CodeAnalysisPrompt.buildAnalysisPrompt(
                        sub.getSourceCode(), sub.getLanguage(), sub.getProblemName());

                String response = geminiClient.sendMessageWithRetry(prompt, 3);
                if (response != null) {
                    AiAnalysis analysis = AnalysisParser.parseAnalysis(response, sub.getId());
                    if (analysis != null) {
                        analysisDAO.insert(analysis);
                        analyzed++;
                        log("✓ Hoàn tất: " + sub.getProblemId() +
                            " | DS: " + analysis.getDataStructures() +
                            " | Algo: " + analysis.getAlgorithms() +
                            " | AI Score: " + analysis.getAiUsageScore());
                    }
                } else {
                    log("✗ Không nhận được response cho " + sub.getProblemId());
                }

            } catch (GeminiApiClient.PermanentGeminiException e) {
                log("Dừng phân tích vì lỗi cấu hình/quota Gemini: " + e.getMessage());
                break;
            } catch (Exception e) {
                log("Lỗi phân tích " + sub.getProblemId() + ": " + e.getMessage());
            }
        }

        log("=== Đã phân tích " + analyzed + "/" + subs.size() + " " + label + " ===");
        return analyzed;
    }

    public AiAnalysis getAnalysis(long submissionId) { return analysisDAO.findBySubmissionId(submissionId); }
    public List<AiAnalysis> getAnalysesByAccount(int accountId) { return analysisDAO.findByAccountId(accountId); }
    public int countAnalyses() { return analysisDAO.count(); }
    public List<String> getAllDataStructures() { return analysisDAO.getAllDataStructures(); }
    public List<String> getAllAlgorithms() { return analysisDAO.getAllAlgorithms(); }
}
