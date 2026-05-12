package com.codeanalyzer.crawler;

import com.codeanalyzer.config.AppConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Quản lý lịch crawl tự động bằng ScheduledExecutorService.
 */
public class CrawlScheduler {

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private Runnable crawlTask;
    private Consumer<String> logCallback;
    private boolean running = false;

    public CrawlScheduler() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CrawlScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void setCrawlTask(Runnable crawlTask) {
        this.crawlTask = crawlTask;
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String msg) {
        System.out.println("[Scheduler] " + msg);
        if (logCallback != null) logCallback.accept("[Scheduler] " + msg);
    }

    /**
     * Bắt đầu lịch crawl tự động.
     */
    public synchronized void start() {
        if (running) {
            log("Scheduler đã đang chạy.");
            return;
        }

        int intervalHours = AppConfig.getCrawlIntervalHours();
        String startTimeStr = AppConfig.getCrawlStartTime();

        // Calculate initial delay to start time
        long initialDelay = calculateInitialDelay(startTimeStr);
        long periodMs = TimeUnit.HOURS.toMillis(intervalHours);

        log(String.format("Lên lịch crawl: bắt đầu sau %d phút, lặp lại mỗi %d giờ",
                initialDelay / 60000, intervalHours));

        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                log("Bắt đầu crawl tự động...");
                if (crawlTask != null) {
                    crawlTask.run();
                }
                log("Hoàn tất crawl tự động.");
            } catch (Exception e) {
                log("Lỗi crawl tự động: " + e.getMessage());
            }
        }, initialDelay, periodMs, TimeUnit.MILLISECONDS);

        running = true;
    }

    /**
     * Dừng lịch crawl tự động.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        running = false;
        log("Đã dừng scheduler.");
    }

    /**
     * Khởi động lại scheduler với cấu hình mới.
     */
    public synchronized void restart() {
        stop();
        start();
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Tính delay từ hiện tại đến giờ bắt đầu.
     */
    private long calculateInitialDelay(String startTimeStr) {
        try {
            LocalTime targetTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = now.toLocalDate().atTime(targetTime);
            if (now.isAfter(nextRun)) {
                nextRun = nextRun.plusDays(1);
            }
            return Duration.between(now, nextRun).toMillis();
        } catch (Exception e) {
            // Default: 1 hour from now
            return TimeUnit.HOURS.toMillis(1);
        }
    }

    /**
     * Shutdown scheduler.
     */
    public void shutdown() {
        stop();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
