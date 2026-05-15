package com.codeanalyzer.service;

import com.codeanalyzer.ai.*;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EvaluationService {

    private final AiAnalysisDAO analysisDAO = new AiAnalysisDAO();
    private final EvaluationDAO evaluationDAO = new EvaluationDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final GeminiApiClient geminiClient = new GeminiApiClient();
    private static final Gson gson = new Gson();
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Consumer<String> logCallback;
    private volatile boolean stoppedByAiLimit = false;

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

    public boolean isRunning() { return running.get(); }

    public boolean wasStoppedByAiLimit() { return stoppedByAiLimit; }

    private void log(String msg) {
        System.out.println("[EvalService] " + msg);
        if (logCallback != null) logCallback.accept(msg);
        for (Consumer<String> listener : logListeners) {
            listener.accept(msg);
        }
    }

    /**
     * Tạo đánh giá tổng hợp cho 1 account.
     */
    public AccountEvaluation evaluate(int accountId) {
        if (!running.compareAndSet(false, true)) {
            log("Đang có tiến trình đánh giá khác chạy. Bỏ qua yêu cầu mới.");
            return null;
        }
        try {
            stoppedByAiLimit = false;
            return evaluateInternal(accountId);
        } finally {
            running.set(false);
        }
    }

    private AccountEvaluation evaluateInternal(int accountId) {
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
            if (GeminiApiClient.LIMIT_REACHED_MESSAGE.equals(e.getMessage())) {
                stoppedByAiLimit = true;
                log(GeminiApiClient.LIMIT_REACHED_MESSAGE);
            } else {
                log("Dừng đánh giá vì lỗi cấu hình Gemini: " + e.getMessage());
            }
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
        if (!running.compareAndSet(false, true)) {
            log("Đang có tiến trình đánh giá khác chạy. Bỏ qua yêu cầu mới.");
            return;
        }
        try {
            stoppedByAiLimit = false;
            List<Account> accounts = accountDAO.findActive();
            for (Account acc : accounts) {
                try {
                    evaluateInternal(acc.getId());
                } catch (GeminiApiClient.PermanentGeminiException e) {
                    break;
                }
            }
        } finally {
            running.set(false);
        }
    }

    public AccountEvaluation getLatestEvaluation(int accountId) {
        return evaluationDAO.findLatestByAccountId(accountId);
    }

    public List<AccountEvaluation> getAllEvaluations() {
        return evaluationDAO.findAll();
    }
}
