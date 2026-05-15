package com.codeanalyzer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Quản lý kết nối SQL Server qua HikariCP connection pool.
 * Hỗ trợ thay đổi cấu hình DB runtime và test connection.
 */
public class DatabaseConfig {

    private static HikariDataSource dataSource;
    private static final Path LOCAL_PROPERTIES_PATH = Paths.get("application.properties").toAbsolutePath();
    private static String dbUrl;
    private static String dbUsername;
    private static String dbPassword;

    static {
        loadDefaultProperties();
    }

    private static void loadDefaultProperties() {
        Properties props = new Properties();
        try (InputStream is = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load application.properties: " + e.getMessage());
        }
        if (Files.exists(LOCAL_PROPERTIES_PATH)) {
            try (InputStream is = Files.newInputStream(LOCAL_PROPERTIES_PATH)) {
                props.load(is);
            } catch (IOException e) {
                System.err.println("Warning: Could not load local application.properties: " + e.getMessage());
            }
        }

        dbUrl = getConfigValue(props, "db.url", "DB_URL",
                "jdbc:sqlserver://localhost;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;");
        dbUsername = getConfigValue(props, "db.username", "DB_USERNAME", "sa");
        dbPassword = getConfigValue(props, "db.password", "DB_PASSWORD", "");
    }

    private static String getConfigValue(Properties props, String propertyKey, String envKey, String defaultValue) {
        String fromJvm = System.getProperty(propertyKey);
        if (fromJvm != null && !fromJvm.isBlank()) return fromJvm.trim();

        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();

        String fromFile = props.getProperty(propertyKey);
        if (fromFile != null && !fromFile.isBlank()) return fromFile.trim();

        return defaultValue;
    }

    /**
     * Khởi tạo connection pool với cấu hình hiện tại.
     */
    public static synchronized void initialize() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setConnectionTimeout(10000);

        // SQL Server specific configurations can go here if needed

        dataSource = new HikariDataSource(config);
    }

    /**
     * Khởi tạo với cấu hình tùy chỉnh (từ Settings Panel).
     */
    public static synchronized void initialize(String url, String username, String password) {
        shutdown();
        dbUrl = url;
        dbUsername = username;
        dbPassword = password;
        initialize();
    }

    /**
     * Reload cấu hình DB từ application.properties local/classpath và khởi tạo lại pool.
     */
    public static synchronized void reloadFromProperties() {
        shutdown();
        loadDefaultProperties();
        initialize();
    }

    /**
     * Lấy connection từ pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initialize();
        }
        return dataSource.getConnection();
    }

    /**
     * Test kết nối database.
     * @return true nếu kết nối thành công.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test kết nối với cấu hình tùy chỉnh (không thay đổi pool hiện tại).
     */
    public static boolean testConnection(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(5000);

        try (HikariDataSource testDs = new HikariDataSource(config);
             Connection conn = testDs.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Đóng connection pool.
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    public static String getDbUrl() { return dbUrl; }
    public static String getDbUsername() { return dbUsername; }
    public static String getDbPassword() { return dbPassword; }
}
