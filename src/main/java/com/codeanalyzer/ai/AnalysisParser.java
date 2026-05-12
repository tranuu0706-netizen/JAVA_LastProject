package com.codeanalyzer.ai;

import com.codeanalyzer.model.AiAnalysis;
import com.codeanalyzer.model.AccountEvaluation;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse kết quả JSON từ Gemini API thành model objects.
 */
public class AnalysisParser {

    private static final Gson gson = new Gson();

    /**
     * Parse JSON response thành AiAnalysis.
     */
    public static AiAnalysis parseAnalysis(String jsonResponse, long submissionId) {
        try {
            // Clean response - extract JSON if wrapped in text
            String json = extractJson(jsonResponse);
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            AiAnalysis analysis = new AiAnalysis();
            analysis.setSubmissionId(submissionId);

            analysis.setDataStructures(parseStringList(obj, "data_structures", "ds"));
            analysis.setAlgorithms(parseStringList(obj, "algorithms", "algo"));
            analysis.setComplexityTime(getStr(obj, "Unknown", "complexity_time", "time"));
            analysis.setComplexitySpace(getStr(obj, "Unknown", "complexity_space", "space"));
            analysis.setAiUsageScore(getInt(obj, 0, "ai_usage_score", "ai"));
            analysis.setAiUsageReason(getStr(obj, "", "ai_usage_reason", "ai_reason"));
            analysis.setDifficultyLevel(getStr(obj, "MEDIUM", "difficulty_level", "diff"));
            analysis.setCodeQualityScore(getInt(obj, 50, "code_quality_score", "quality"));
            analysis.setAnalysisSummary(getStr(obj, "", "analysis_summary", "summary"));

            return analysis;
        } catch (Exception e) {
            System.err.println("Lỗi parse analysis JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse JSON response thành AccountEvaluation.
     */
    public static AccountEvaluation parseEvaluation(String jsonResponse, int accountId, int totalAnalyzed, BigDecimal avgAiScore) {
        try {
            String json = extractJson(jsonResponse);
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            AccountEvaluation eval = new AccountEvaluation();
            eval.setAccountId(accountId);
            eval.setTotalSubmissionsAnalyzed(totalAnalyzed);
            eval.setDsScore(getBigDecimal(obj, "ds_score"));
            eval.setDsMastered(parseStringList(obj, "ds_mastered"));
            eval.setDsLearning(parseStringList(obj, "ds_learning"));
            eval.setAlgoScore(getBigDecimal(obj, "algo_score"));
            eval.setAlgoMastered(parseStringList(obj, "algo_mastered"));
            eval.setAlgoLearning(parseStringList(obj, "algo_learning"));
            eval.setAvgAiUsageScore(avgAiScore);
            eval.setAiUsageLevel(calculateAiLevel(avgAiScore));
            eval.setOverallLevel(getStr(obj, "overall_level", "BEGINNER"));
            eval.setStrengths(getStr(obj, "strengths", ""));
            eval.setWeaknesses(getStr(obj, "weaknesses", ""));
            eval.setRecommendation(getStr(obj, "recommendation", ""));

            return eval;
        } catch (Exception e) {
            System.err.println("Lỗi parse evaluation JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract JSON from response (handle markdown code blocks etc.)
     */
    private static String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        // Remove markdown code blocks
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();
        // Find JSON object boundaries
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static List<String> parseStringList(JsonObject obj, String... keys) {
        try {
            for (String key : keys) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    return gson.fromJson(obj.get(key), new TypeToken<List<String>>(){}.getType());
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private static String getStr(JsonObject obj, String key, String def) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return def;
    }

    private static String getStr(JsonObject obj, String def, String... keys) {
        for (String key : keys) {
            try {
                if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsInt();
        } catch (Exception ignored) {}
        return def;
    }

    private static int getInt(JsonObject obj, int def, String... keys) {
        for (String key : keys) {
            try {
                if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static BigDecimal getBigDecimal(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsBigDecimal();
        } catch (Exception ignored) {}
        return BigDecimal.ZERO;
    }

    private static String calculateAiLevel(BigDecimal avgScore) {
        if (avgScore == null) return "CLEAN";
        double score = avgScore.doubleValue();
        if (score < 15) return "CLEAN";
        if (score < 30) return "LOW";
        if (score < 50) return "MEDIUM";
        if (score < 75) return "HIGH";
        return "VERY_HIGH";
    }
}
