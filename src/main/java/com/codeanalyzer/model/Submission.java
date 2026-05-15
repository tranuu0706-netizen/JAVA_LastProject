package com.codeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một submission đã crawl được.
 */
public class Submission {
    private long id;
    private int accountId;
    private String submissionId;   // ID gốc từ Codeforces
    private String problemId;      // VD: 1234A
    private String problemName;
    private String contestId;
    private String language;       // C++17, Java, Python...
    private String verdict;        // AC, WA, TLE...
    private LocalDateTime submittedAt;
    private String sourceCode;
    private LocalDateTime crawledAt;
    private String platform;       // CODEFORCES
    private String analysisStatus; // UNANALYZED, ANALYZED, ERROR
    private String analysisError;

    public Submission() {}

    // Getters & Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getProblemName() { return problemName; }
    public void setProblemName(String problemName) { this.problemName = problemName; }

    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public LocalDateTime getCrawledAt() { return crawledAt; }
    public void setCrawledAt(LocalDateTime crawledAt) { this.crawledAt = crawledAt; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }

    public String getAnalysisError() { return analysisError; }
    public void setAnalysisError(String analysisError) { this.analysisError = analysisError; }

    @Override
    public String toString() {
        return problemId + " - " + problemName + " [" + verdict + "]";
    }
}
