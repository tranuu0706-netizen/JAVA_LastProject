package com.codeanalyzer.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Model đại diện cho kết quả phân tích AI cho một submission.
 */
public class AiAnalysis {
    private long id;
    private long submissionId;
    private List<String> dataStructures;   // ["Array", "HashMap", "Segment Tree"]
    private List<String> algorithms;       // ["BFS", "DP", "Binary Search"]
    private String complexityTime;         // O(n log n)
    private String complexitySpace;        // O(n)
    private int aiUsageScore;              // 0-100
    private String aiUsageReason;          // Giải thích
    private String difficultyLevel;        // BEGINNER/EASY/MEDIUM/HARD/EXPERT
    private int codeQualityScore;          // 0-100
    private String analysisSummary;        // Tóm tắt tiếng Việt
    private LocalDateTime analyzedAt;

    public AiAnalysis() {}

    // Getters & Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSubmissionId() { return submissionId; }
    public void setSubmissionId(long submissionId) { this.submissionId = submissionId; }

    public List<String> getDataStructures() { return dataStructures; }
    public void setDataStructures(List<String> dataStructures) { this.dataStructures = dataStructures; }

    public List<String> getAlgorithms() { return algorithms; }
    public void setAlgorithms(List<String> algorithms) { this.algorithms = algorithms; }

    public String getComplexityTime() { return complexityTime; }
    public void setComplexityTime(String complexityTime) { this.complexityTime = complexityTime; }

    public String getComplexitySpace() { return complexitySpace; }
    public void setComplexitySpace(String complexitySpace) { this.complexitySpace = complexitySpace; }

    public int getAiUsageScore() { return aiUsageScore; }
    public void setAiUsageScore(int aiUsageScore) { this.aiUsageScore = aiUsageScore; }

    public String getAiUsageReason() { return aiUsageReason; }
    public void setAiUsageReason(String aiUsageReason) { this.aiUsageReason = aiUsageReason; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public int getCodeQualityScore() { return codeQualityScore; }
    public void setCodeQualityScore(int codeQualityScore) { this.codeQualityScore = codeQualityScore; }

    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
