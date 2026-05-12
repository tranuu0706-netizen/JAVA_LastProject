import com.codeanalyzer.config.DatabaseConfig;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("Testing connection to: " + DatabaseConfig.getDbUrl());
        System.out.println(DatabaseConfig.testConnection() ? "Success!" : "Failed!");
        DatabaseConfig.shutdown();
    }
}
