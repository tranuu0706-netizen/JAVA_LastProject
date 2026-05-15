package com.codeanalyzer.service;

import com.codeanalyzer.crawler.*;
import com.codeanalyzer.dao.*;
import com.codeanalyzer.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CrawlService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final SubmissionDAO submissionDAO = new SubmissionDAO();
    private final CrawlJobDAO crawlJobDAO = new CrawlJobDAO();
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CrawlJob>> jobListeners = new CopyOnWriteArrayList<>();
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;
    private volatile BrowserManager activeBrowserManager;
    private volatile CrawlJob activeJob;

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

    public void addJobListener(Consumer<CrawlJob> listener) {
        if (listener != null && !jobListeners.contains(listener)) {
            jobListeners.add(listener);
        }
    }

    public void removeJobListener(Consumer<CrawlJob> listener) {
        jobListeners.remove(listener);
    }

    public void resetCancellation() {
        this.cancelled = false;
    }

    public void cancel() {
        this.cancelled = true;
        BrowserManager browserManager = activeBrowserManager;
        if (browserManager != null) {
            Thread quitThread = new Thread(browserManager::quit, "crawl-cancel-browser-quit");
            quitThread.setDaemon(true);
            quitThread.start();
        }
        log("Đã gửi yêu cầu dừng crawl. Tiến trình sẽ dừng ở bước an toàn gần nhất.");
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private void log(String msg) {
        System.out.println("[CrawlService] " + msg);
        CrawlJob job = activeJob;
        if (job != null) {
            job.appendLog(msg);
        }
        if (logCallback != null) logCallback.accept(msg);
        for (Consumer<String> listener : logListeners) {
            listener.accept(msg);
        }
    }

    private void notifyJobFinished(CrawlJob job) {
        for (Consumer<CrawlJob> listener : jobListeners) {
            listener.accept(job);
        }
    }

    /**
     * Crawl tất cả account active.
     */
    public CrawlJob crawlAll() {
        CrawlJob job = new CrawlJob();
        crawlJobDAO.insert(job);
        activeJob = job;

        BrowserManager browserManager = new BrowserManager();
        activeBrowserManager = browserManager;
        int totalCrawled = 0;
        int totalScanned = 0;
        int totalSkipped = 0;
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
                    cfCrawler.setLogCallback(this::log);
                    cfCrawler.setCancelCheck(this::isCancelled);
                    CodeforcesCrawler.CrawlResult crawlResult = cfCrawler.crawlSubmissionsWithStats(
                            account.getUsername(), account.getId(),
                            submissionDAO::exists,
                            (submissionId, submittedAt) -> submissionDAO.updateSubmittedAt(
                                    submissionId, "CODEFORCES", submittedAt));
                    subs = crawlResult.getSubmissions();
                    totalScanned += crawlResult.getScanned();
                    totalSkipped += crawlResult.getSkippedExisting();

                    // Save submissions to DB
                    int saved = 0;
                    for (Submission sub : subs) {
                        if (cancelled) {
                            log("Dừng lưu submissions còn lại do người dùng yêu cầu.");
                            break;
                        }
                        long id = submissionDAO.insert(sub);
                        if (id > 0) saved++;
                    }
                    totalCrawled += saved;
                    log("Lưu " + saved + "/" + subs.size() + " submissions cho " + account.getUsername());

                    if (!cancelled) {
                        accountDAO.updateLastCrawled(account.getId());
                        accountsProcessed++;
                    }

                } catch (Exception e) {
                    log("Lỗi crawl " + account.getUsername() + ": " + e.getMessage());
                    job.appendError(account.getUsername() + ": " + e.getMessage());
                }

                if (cancelled) {
                    log("Crawl đã dừng theo yêu cầu người dùng.");
                    break;
                }
            }

            job.setStatus(cancelled ? "PARTIAL" : "SUCCESS");
        } catch (Exception e) {
            log("Lỗi nghiêm trọng: " + e.getMessage());
            job.setStatus("FAILED");
            job.appendError(e.getMessage());
        } finally {
            browserManager.quit();
            if (activeBrowserManager == browserManager) {
                activeBrowserManager = null;
            }
        }

        job.setFinishedAt(LocalDateTime.now());
        job.setAccountsProcessed(accountsProcessed);
        job.setSubmissionsScanned(totalScanned);
        job.setSubmissionsCrawled(totalCrawled);
        job.setSubmissionsSkipped(totalSkipped);
        crawlJobDAO.update(job);
        log("=== Hoàn tất crawl: quét " + totalScanned
                + ", mới " + totalCrawled
                + ", bỏ qua " + totalSkipped
                + " từ " + accountsProcessed + " accounts ===");
        crawlJobDAO.update(job);
        if (activeJob == job) {
            activeJob = null;
        }
        notifyJobFinished(job);
        return job;
    }

    /**
     * Crawl một account cụ thể.
     */
    public CrawlJob crawlAccount(Account account) {
        CrawlJob job = new CrawlJob();
        crawlJobDAO.insert(job);
        activeJob = job;

        BrowserManager browserManager = new BrowserManager();
        activeBrowserManager = browserManager;
        int saved = 0;
        int scanned = 0;
        int skipped = 0;
        String accountName = account != null ? account.getUsername() : "unknown";

        try {
            List<Submission> subs;
            if (account == null) {
                throw new IllegalArgumentException("Account không tồn tại.");
            }

            job.setAccountsProcessed(1);
            if (!"CODEFORCES".equals(account.getPlatform())) {
                throw new IllegalArgumentException("Platform không còn được hỗ trợ: " + account.getPlatform());
            }

            log("--- Crawl account: " + account.getUsername() + " (" + account.getPlatform() + ") ---");
            CodeforcesCrawler cfCrawler = new CodeforcesCrawler(browserManager);
            cfCrawler.setLogCallback(this::log);
            cfCrawler.setCancelCheck(this::isCancelled);
            CodeforcesCrawler.CrawlResult crawlResult = cfCrawler.crawlSubmissionsWithStats(
                    account.getUsername(), account.getId(),
                    submissionDAO::exists,
                    (submissionId, submittedAt) -> submissionDAO.updateSubmittedAt(
                            submissionId, "CODEFORCES", submittedAt));
            subs = crawlResult.getSubmissions();
            scanned = crawlResult.getScanned();
            skipped = crawlResult.getSkippedExisting();

            for (Submission sub : subs) {
                if (cancelled) {
                    log("Dừng lưu submissions còn lại do người dùng yêu cầu.");
                    break;
                }
                long id = submissionDAO.insert(sub);
                if (id > 0) saved++;
            }
            if (!cancelled) {
                accountDAO.updateLastCrawled(account.getId());
            }
            log("Lưu " + saved + "/" + subs.size() + " submissions cho " + account.getUsername());
            job.setStatus(cancelled ? "PARTIAL" : "SUCCESS");
        } catch (Exception e) {
            log("Lỗi crawl " + accountName + ": " + e.getMessage());
            job.setStatus(cancelled ? "PARTIAL" : "FAILED");
            job.appendError(accountName + ": " + e.getMessage());
        } finally {
            browserManager.quit();
            if (activeBrowserManager == browserManager) {
                activeBrowserManager = null;
            }
            job.setFinishedAt(LocalDateTime.now());
            job.setSubmissionsScanned(scanned);
            job.setSubmissionsCrawled(saved);
            job.setSubmissionsSkipped(skipped);
            crawlJobDAO.update(job);
            log("=== Hoàn tất crawl 1 nick: quét " + scanned
                    + ", mới " + saved
                    + ", bỏ qua " + skipped
                    + " từ " + job.getAccountsProcessed() + " accounts ===");
            crawlJobDAO.update(job);
            if (activeJob == job) {
                activeJob = null;
            }
            notifyJobFinished(job);
        }
        return job;
    }

    public List<CrawlJob> getCrawlHistory() { return crawlJobDAO.findAll(); }
    public CrawlJob getLatestJob() { return crawlJobDAO.findLatest(); }
}
