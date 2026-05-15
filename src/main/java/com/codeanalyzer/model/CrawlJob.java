package com.codeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một crawl job (lịch sử crawl).
 */
public class CrawlJob {
    private int id;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status; // RUNNING, SUCCESS, FAILED, PARTIAL
    private int accountsProcessed;
    private int submissionsScanned;
    private int submissionsCrawled;
    private int submissionsSkipped;
    private int submissionsAnalyzed;
    private String errorLog;
    private String crawlLog;

    public CrawlJob() {
        this.status = "RUNNING";
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAccountsProcessed() { return accountsProcessed; }
    public void setAccountsProcessed(int accountsProcessed) { this.accountsProcessed = accountsProcessed; }

    public int getSubmissionsScanned() { return submissionsScanned; }
    public void setSubmissionsScanned(int submissionsScanned) { this.submissionsScanned = submissionsScanned; }

    public int getSubmissionsCrawled() { return submissionsCrawled; }
    public void setSubmissionsCrawled(int submissionsCrawled) { this.submissionsCrawled = submissionsCrawled; }

    public int getSubmissionsSkipped() { return submissionsSkipped; }
    public void setSubmissionsSkipped(int submissionsSkipped) { this.submissionsSkipped = submissionsSkipped; }

    public int getSubmissionsAnalyzed() { return submissionsAnalyzed; }
    public void setSubmissionsAnalyzed(int submissionsAnalyzed) { this.submissionsAnalyzed = submissionsAnalyzed; }

    public String getErrorLog() { return errorLog; }
    public void setErrorLog(String errorLog) { this.errorLog = errorLog; }

    public String getCrawlLog() { return crawlLog; }
    public void setCrawlLog(String crawlLog) { this.crawlLog = crawlLog; }

    public void appendError(String error) {
        if (this.errorLog == null) {
            this.errorLog = error;
        } else {
            this.errorLog += "\n" + error;
        }
    }

    public void appendLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (this.crawlLog == null || this.crawlLog.isBlank()) {
            this.crawlLog = message;
        } else {
            this.crawlLog += "\n" + message;
        }
    }
}
