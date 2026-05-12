package com.codeanalyzer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Model đại diện cho đánh giá tổng hợp năng lực của một tài khoản.
 */
public class AccountEvaluation {
    private int id;
    private int accountId;
    private LocalDateTime evaluatedAt;
    private int totalSubmissionsAnalyzed;

    // Điểm CTDL
    private BigDecimal dsScore;            // 0-100
    private List<String> dsMastered;       // CTDL thành thạo
    private List<String> dsLearning;       // CTDL đang học

    // Điểm Thuật toán
    private BigDecimal algoScore;          // 0-100
    private List<String> algoMastered;     // Thuật toán thành thạo
    private List<String> algoLearning;     // Thuật toán đang học

    // AI Usage
    private BigDecimal avgAiUsageScore;
    private String aiUsageLevel;           // CLEAN/LOW/MEDIUM/HIGH/VERY_HIGH

    // Tổng hợp
    private String overallLevel;           // BEGINNER/INTERMEDIATE/ADVANCED/EXPERT
    private String strengths;
    private String weaknesses;
    private String recommendation;

    public AccountEvaluation() {}

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public int getTotalSubmissionsAnalyzed() { return totalSubmissionsAnalyzed; }
    public void setTotalSubmissionsAnalyzed(int totalSubmissionsAnalyzed) { this.totalSubmissionsAnalyzed = totalSubmissionsAnalyzed; }

    public BigDecimal getDsScore() { return dsScore; }
    public void setDsScore(BigDecimal dsScore) { this.dsScore = dsScore; }

    public List<String> getDsMastered() { return dsMastered; }
    public void setDsMastered(List<String> dsMastered) { this.dsMastered = dsMastered; }

    public List<String> getDsLearning() { return dsLearning; }
    public void setDsLearning(List<String> dsLearning) { this.dsLearning = dsLearning; }

    public BigDecimal getAlgoScore() { return algoScore; }
    public void setAlgoScore(BigDecimal algoScore) { this.algoScore = algoScore; }

    public List<String> getAlgoMastered() { return algoMastered; }
    public void setAlgoMastered(List<String> algoMastered) { this.algoMastered = algoMastered; }

    public List<String> getAlgoLearning() { return algoLearning; }
    public void setAlgoLearning(List<String> algoLearning) { this.algoLearning = algoLearning; }

    public BigDecimal getAvgAiUsageScore() { return avgAiUsageScore; }
    public void setAvgAiUsageScore(BigDecimal avgAiUsageScore) { this.avgAiUsageScore = avgAiUsageScore; }

    public String getAiUsageLevel() { return aiUsageLevel; }
    public void setAiUsageLevel(String aiUsageLevel) { this.aiUsageLevel = aiUsageLevel; }

    public String getOverallLevel() { return overallLevel; }
    public void setOverallLevel(String overallLevel) { this.overallLevel = overallLevel; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
