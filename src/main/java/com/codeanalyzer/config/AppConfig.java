package com.codeanalyzer.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý cấu hình hệ thống - load từ application.properties trước, sau đó từ DB.
 * Hỗ trợ cache in-memory để tránh query DB liên tục.
 */
public class AppConfig {

    private static final Map<String, String> configCache = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    /**
     * Load cấu hình từ application.properties trước.
     */
    static {
        loadFromProperties();
    }

    private static void loadFromProperties() {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                
                // Map properties keys từ format db.url → db_url
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    String cacheKey = key.replace(".", "_");
                    configCache.put(cacheKey, value);
                }
                System.out.println("✓ Loaded configuration from application.properties");
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load application.properties: " + e.getMessage());
        }
    }

    /**
     * Load toàn bộ cấu hình từ DB vào cache (overlay trên properties).
     */
    public static void loadFromDatabase() {
        String sql = "SELECT config_key, config_value FROM system_config";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                configCache.put(rs.getString("config_key"), rs.getString("config_value"));
            }
            loaded = true;
            System.out.println("✓ Loaded additional configuration from database");
        } catch (Exception e) {
            System.err.println("Warning: Could not load configuration from DB: " + e.getMessage());
        }
    }

    /**
     * Lấy giá trị cấu hình theo key.
     */
    public static String get(String key) {
        return configCache.getOrDefault(key, "");
    }

    /**
     * Lấy giá trị cấu hình với giá trị mặc định.
     */
    public static String get(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }

    /**
     * Lấy giá trị int.
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Cập nhật một cấu hình vào DB và cache.
     */
    public static void set(String key, String value) {
        String sql = "MERGE INTO system_config WITH (HOLDLOCK) AS target " +
                     "USING (SELECT ? AS config_key, ? AS config_value) AS source " +
                     "ON target.config_key = source.config_key " +
                     "WHEN MATCHED THEN UPDATE SET config_value = source.config_value " +
                     "WHEN NOT MATCHED THEN INSERT (config_key, config_value) VALUES (source.config_key, source.config_value);";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            configCache.put(key, value);
        } catch (Exception e) {
            System.err.println("Could not save configuration: " + e.getMessage());
        }
    }

    /**
     * Lấy Gemini API key.
     */
    public static String getGeminiApiKey() {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        String jvmKey = System.getProperty("gemini.api.key");
        if (jvmKey != null && !jvmKey.isBlank()) {
            return jvmKey.trim();
        }
        jvmKey = System.getProperty("GEMINI_API_KEY");
        if (jvmKey != null && !jvmKey.isBlank()) {
            return jvmKey.trim();
        }
        String windowsUserKey = getWindowsUserEnvironmentValue("GEMINI_API_KEY");
        if (windowsUserKey != null && !windowsUserKey.isBlank()) {
            return windowsUserKey.trim();
        }
        return get("gemini_api_key", "").trim();
    }

    /**
     * Fallback cho Windows: đọc biến User Environment đã set bằng setx, kể cả khi process hiện tại chưa nhận env mới.
     */
    private static String getWindowsUserEnvironmentValue(String name) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win")) {
            return "";
        }

        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", name)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return "";
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(name)) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 3);
                if (parts.length == 3) {
                    return parts[2].trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Lấy model Gemini dùng để phân tích.
     */
    public static String getGeminiModel() {
        return get("gemini_model", "gemini-2.0-flash").trim();
    }

    /**
     * Khoảng nghỉ tối thiểu giữa 2 request Gemini để tránh vượt RPM.
     */
    public static int getGeminiRequestDelayMs() {
        return Math.max(0, getInt("gemini_request_delay_ms", 5000));
    }

    /**
     * Số token output tối đa cho mỗi lần Gemini trả JSON.
     */
    public static int getGeminiMaxOutputTokens() {
        return Math.max(1024, getInt("gemini_max_output_tokens", 3072));
    }

    /**
     * Lấy đường dẫn Edge profile.
     */
    public static String getEdgeProfilePath() {
        return firstNonBlank(
                System.getProperty("edge.profile.path"),
                System.getenv("EDGE_PROFILE_PATH"),
                get("edge_profile_path", "")
        );
    }

    /**
     * Lấy đường dẫn Edge Driver.
     */
    public static String getEdgeDriverPath() {
        return firstNonBlank(
                System.getProperty("edge.driver.path"),
                System.getenv("EDGE_DRIVER_PATH"),
                get("edge_driver_path", "")
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * Lấy số submission tối đa mỗi lần crawl.
     */
    public static int getMaxSubmissionsPerCrawl() {
        return getInt("crawl_max_submissions", 500);
    }

    /**
     * Lấy kích thước batch phân tích AI.
     */
    public static int getAnalysisBatchSize() {
        return Math.min(10, Math.max(1, getInt("analysis_batch_size", 10)));
    }

    /**
     * Độ dài code tối đa gửi vào prompt.
     */
    public static int getAnalysisMaxCodeLength() {
        return Math.max(500, getInt("analysis_max_code_length", 2500));
    }

    /**
     * Lấy interval crawl tự động (giờ).
     */
    public static int getCrawlIntervalHours() {
        return getInt("crawl_interval_hours", 24);
    }

    /**
     * Lấy giờ bắt đầu crawl tự động.
     */
    public static String getCrawlStartTime() {
        return get("crawl_start_time", "02:00");
    }

    /**
     * Reload cấu hình từ DB.
     */
    public static void reload() {
        loaded = false;
        loadFromDatabase();
    }
}
