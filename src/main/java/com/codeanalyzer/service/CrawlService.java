package com.codeanalyzer.service;

import com.codeanalyzer.crawler.*;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

public class CrawlService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final SubmissionDAO submissionDAO = new SubmissionDAO();
    private final CrawlJobDAO crawlJobDAO = new CrawlJobDAO();
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void cancel() { this.cancelled = true; }

    private void log(String msg) {
        System.out.println("[CrawlService] " + msg);
        if (logCallback != null) logCallback.accept(msg);
    }

    /**
     * Crawl tất cả account active.
     */
    public CrawlJob crawlAll() {
        cancelled = false;
        CrawlJob job = new CrawlJob();
        crawlJobDAO.insert(job);

        BrowserManager browserManager = new BrowserManager();
        int totalCrawled = 0;
        int accountsProcessed = 0;

        try {
            List<Account> accounts = accountDAO.findActive();
            log("Bắt đầu crawl " + accounts.size() + " account(s)...");

            for (Account account : accounts) {
                if (cancelled) {
                    log("Crawl bị hủy!");
                    break;
                }

                try {
                    log("--- Crawl account: " + account.getUsername() + " (" + account.getPlatform() + ") ---");
                    List<Submission> subs;

                    if (!"CODEFORCES".equals(account.getPlatform())) {
                        log("Bỏ qua account " + account.getUsername() + " vì platform không còn được hỗ trợ: "
                                + account.getPlatform());
                        job.appendError(account.getUsername() + ": platform không còn được hỗ trợ: "
                                + account.getPlatform());
                        continue;
                    }

                    CodeforcesCrawler cfCrawler = new CodeforcesCrawler(browserManager);
                    cfCrawler.setLogCallback(logCallback);
                    subs = cfCrawler.crawlSubmissions(account.getUsername(), account.getId(),
                            submissionDAO::exists,
                            (submissionId, submittedAt) -> submissionDAO.updateSubmittedAt(
                                    submissionId, "CODEFORCES", submittedAt));

                    // Save submissions to DB
                    int saved = 0;
                    for (Submission sub : subs) {
                        long id = submissionDAO.insert(sub);
                        if (id > 0) saved++;
                    }
                    totalCrawled += saved;
                    log("Lưu " + saved + "/" + subs.size() + " submissions cho " + account.getUsername());

                    accountDAO.updateLastCrawled(account.getId());
                    accountsProcessed++;

                } catch (Exception e) {
                    log("Lỗi crawl " + account.getUsername() + ": " + e.getMessage());
                    job.appendError(account.getUsername() + ": " + e.getMessage());
                }
            }

            job.setStatus(cancelled ? "PARTIAL" : "SUCCESS");
        } catch (Exception e) {
            log("Lỗi nghiêm trọng: " + e.getMessage());
            job.setStatus("FAILED");
            job.appendError(e.getMessage());
        } finally {
            browserManager.quit();
        }

        job.setFinishedAt(LocalDateTime.now());
        job.setAccountsProcessed(accountsProcessed);
        job.setSubmissionsCrawled(totalCrawled);
        crawlJobDAO.update(job);
        log("=== Hoàn tất crawl: " + totalCrawled + " submissions từ " + accountsProcessed + " accounts ===");
        return job;
    }

    /**
     * Crawl một account cụ thể.
     */
    public int crawlAccount(Account account) {
        BrowserManager browserManager = new BrowserManager();
        try {
            List<Submission> subs;
            if (!"CODEFORCES".equals(account.getPlatform())) {
                throw new IllegalArgumentException("Platform không còn được hỗ trợ: " + account.getPlatform());
            }

            CodeforcesCrawler cfCrawler = new CodeforcesCrawler(browserManager);
            cfCrawler.setLogCallback(logCallback);
            subs = cfCrawler.crawlSubmissions(account.getUsername(), account.getId(),
                    submissionDAO::exists,
                    (submissionId, submittedAt) -> submissionDAO.updateSubmittedAt(
                            submissionId, "CODEFORCES", submittedAt));

            int saved = 0;
            for (Submission sub : subs) {
                long id = submissionDAO.insert(sub);
                if (id > 0) saved++;
            }
            accountDAO.updateLastCrawled(account.getId());
            return saved;
        } finally {
            browserManager.quit();
        }
    }

    public List<CrawlJob> getCrawlHistory() { return crawlJobDAO.findAll(); }
    public CrawlJob getLatestJob() { return crawlJobDAO.findLatest(); }
}
