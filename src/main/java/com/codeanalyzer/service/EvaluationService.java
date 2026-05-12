package com.codeanalyzer.service;

import com.codeanalyzer.ai.*;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

public class EvaluationService {

    private final AiAnalysisDAO analysisDAO = new AiAnalysisDAO();
    private final EvaluationDAO evaluationDAO = new EvaluationDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final GeminiApiClient geminiClient = new GeminiApiClient();
    private static final Gson gson = new Gson();
    private Consumer<String> logCallback;

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String msg) {
        System.out.println("[EvalService] " + msg);
        if (logCallback != null) logCallback.accept(msg);
    }

    /**
     * Tạo đánh giá tổng hợp cho 1 account.
     */
    public AccountEvaluation evaluate(int accountId) {
        Account account = accountDAO.findById(accountId);
        if (account == null) return null;

        List<AiAnalysis> analyses = analysisDAO.findByAccountId(accountId);
        if (analyses.isEmpty()) {
            log("Chưa có dữ liệu phân tích cho " + account.getUsername());
            return null;
        }

        log("Đánh giá " + account.getUsername() + " với " + analyses.size() + " bài...");

        // Calculate average AI usage score
        double totalAiScore = 0;
        for (AiAnalysis a : analyses) totalAiScore += a.getAiUsageScore();
        BigDecimal avgAiScore = BigDecimal.valueOf(totalAiScore / analyses.size())
                .setScale(2, RoundingMode.HALF_UP);

        // Build analysis summary for Gemini
        StringBuilder sb = new StringBuilder();
        for (AiAnalysis a : analyses) {
            sb.append("- DS: ").append(a.getDataStructures())
              .append(" | Algo: ").append(a.getAlgorithms())
              .append(" | Difficulty: ").append(a.getDifficultyLevel())
              .append(" | Quality: ").append(a.getCodeQualityScore())
              .append(" | AI: ").append(a.getAiUsageScore()).append("%\n");
        }

        try {
            String prompt = CodeAnalysisPrompt.buildEvaluationPrompt(
                    account.getUsername(), sb.toString());
            String response = geminiClient.sendMessageWithRetry(prompt, 3);

            if (response != null) {
                AccountEvaluation eval = AnalysisParser.parseEvaluation(
                        response, accountId, analyses.size(), avgAiScore);
                if (eval != null) {
                    evaluationDAO.insert(eval);
                    log("✓ Đánh giá hoàn tất: " + account.getUsername() +
                        " | Level: " + eval.getOverallLevel() +
                        " | AI: " + eval.getAiUsageLevel());
                    return eval;
                }
            }
        } catch (GeminiApiClient.PermanentGeminiException e) {
            log("Dừng đánh giá vì lỗi cấu hình/quota Gemini: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log("Lỗi đánh giá: " + e.getMessage());
        }
        return null;
    }

    /**
     * Đánh giá tất cả account active.
     */
    public void evaluateAll() {
        List<Account> accounts = accountDAO.findActive();
        for (Account acc : accounts) {
            try {
                evaluate(acc.getId());
            } catch (GeminiApiClient.PermanentGeminiException e) {
                break;
            }
        }
    }

    public AccountEvaluation getLatestEvaluation(int accountId) {
        return evaluationDAO.findLatestByAccountId(accountId);
    }

    public List<AccountEvaluation> getAllEvaluations() {
        return evaluationDAO.findAll();
    }
}
