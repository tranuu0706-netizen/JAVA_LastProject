package com.codeanalyzer.crawler;

import com.codeanalyzer.config.AppConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Quản lý Selenium Edge WebDriver.
 * Sử dụng Edge profile đã đăng nhập để bypass Codeforces firewall.
 */
public class BrowserManager {

    private WebDriver driver;

    public synchronized WebDriver getDriver() {
        if (driver == null) {
            driver = createDriver();
        }
        return driver;
    }

    private WebDriver createDriver() {
        String customDriverPath = AppConfig.getEdgeDriverPath();
        if (customDriverPath != null && !customDriverPath.isEmpty()) {
            if (!customDriverPath.toLowerCase().endsWith(".exe")) {
                customDriverPath += "\\msedgedriver.exe";
            }
            System.setProperty("webdriver.edge.driver", customDriverPath);
            System.out.println("[EdgeDriver] Using configured driver: " + customDriverPath);
        } else if (Files.exists(Path.of("msedgedriver.exe"))) {
            String localDriverPath = Path.of("msedgedriver.exe").toAbsolutePath().toString();
            System.setProperty("webdriver.edge.driver", localDriverPath);
            System.out.println("[EdgeDriver] Using local project driver: " + localDriverPath);
        } else {
            try {
                WebDriverManager.edgedriver().setup();
            } catch (Exception e) {
                System.err.println("Lỗi WebDriverManager: " + e.getMessage());
                System.err.println("Không tải được msedgedriver tự động. Hãy đặt msedgedriver.exe vào thư mục project "
                        + "hoặc cấu hình edge.driver.path trong application.properties.");
            }
        }

        EdgeOptions options = new EdgeOptions();
        String profilePath = AppConfig.getEdgeProfilePath();
        if (profilePath != null && !profilePath.isEmpty()) {
            options.addArguments("--user-data-dir=" + profilePath);
            options.addArguments("--profile-directory=Default");
            // Không xóa cookies/session khi khởi động
            options.addArguments("--disable-clearing-browsing-data");
            System.out.println("[EdgeDriver] Using profile: " + profilePath);
        }
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        // Tránh DevToolsActivePort conflict
        options.addArguments("--remote-debugging-port=0");
        // Cho phép localhost và file:// để tránh CORS issues
        options.addArguments("--disable-web-resources");
        // Tránh Chrome instance exited error
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-renderer-backgrounding");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        EdgeDriver edgeDriver = new EdgeDriver(options);
        edgeDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        edgeDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        edgeDriver.manage().window().maximize();
        return edgeDriver;
    }

    public synchronized void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                System.err.println("Lỗi đóng browser: " + e.getMessage());
            }
            driver = null;
        }
    }

    public boolean isAlive() {
        if (driver == null) return false;
        try {
            driver.getTitle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Delay ngẫu nhiên để tránh bị ban.
     */
    public static void randomDelay() {
        try {
            long delay = 2000 + (long)(Math.random() * 3000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
