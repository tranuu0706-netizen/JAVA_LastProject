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
    private volatile boolean running = false;
    private volatile LocalDateTime nextRunAt;
    private volatile int intervalHours;

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

        intervalHours = AppConfig.getCrawlIntervalHours();
        String startTimeStr = AppConfig.getCrawlStartTime();

        // Calculate initial delay to start time
        long initialDelay = calculateInitialDelay(startTimeStr);
        long periodMs = TimeUnit.HOURS.toMillis(intervalHours);
        nextRunAt = LocalDateTime.now().plus(Duration.ofMillis(initialDelay));

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
            } finally {
                nextRunAt = LocalDateTime.now().plusHours(intervalHours);
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
        nextRunAt = null;
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

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public int getIntervalHours() {
        return intervalHours > 0 ? intervalHours : AppConfig.getCrawlIntervalHours();
    }

    public String getCountdownText() {
        if (!running) {
            return "[Scheduler] Lịch crawl đang tắt";
        }
        LocalDateTime nextRun = nextRunAt;
        if (nextRun == null) {
            return "[Scheduler] Đang tính lịch crawl tiếp theo";
        }

        Duration remaining = Duration.between(LocalDateTime.now(), nextRun);
        if (remaining.isNegative() || remaining.isZero()) {
            return "[Scheduler] Crawl tự động sắp bắt đầu";
        }

        long totalSeconds = remaining.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long totalMinutes = Math.max(1, remaining.toMinutes());

        return String.format("[Scheduler] Lần crawl kế tiếp sau %02d:%02d:%02d (%d phút), lặp lại mỗi %d giờ",
                hours, minutes, seconds, totalMinutes, getIntervalHours());
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
