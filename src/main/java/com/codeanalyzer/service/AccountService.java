package com.codeanalyzer.service;

import com.codeanalyzer.dao.AccountDAO;
import com.codeanalyzer.model.Account;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class AccountService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AddAccountResult addAccount(String username, String platform, String displayName) {
        username = username == null ? "" : username.trim();
        platform = platform == null ? "" : platform.trim().toUpperCase();

        if (username.isEmpty()) {
            return AddAccountResult.failed("Username không được để trống.");
        }
        if (!"CODEFORCES".equals(platform)) {
            return AddAccountResult.failed("Hiện hệ thống chỉ hỗ trợ Codeforces.");
        }

        Account existing = accountDAO.findByUsernameAndPlatform(username, platform);
        if (existing != null) {
            return AddAccountResult.failed("Nick này đã tồn tại trong hệ thống.");
        }

        AccountValidation validation = validateRemoteAccount(username, platform);
        if (!validation.exists()) {
            return AddAccountResult.failed(validation.message());
        }

        Account account = new Account(username, platform);
        account.setDisplayName(displayName != null && !displayName.isEmpty() ? displayName : username);
        int id = accountDAO.insert(account);
        if (id <= 0) {
            return AddAccountResult.failed("Không thể lưu nick vào database.");
        }
        return AddAccountResult.created(id);
    }

    public List<Account> getAllAccounts() { return accountDAO.findAll(); }
    public List<Account> getActiveAccounts() { return accountDAO.findActive(); }
    public Account getAccount(int id) { return accountDAO.findById(id); }

    public void toggleActive(int accountId) {
        Account acc = accountDAO.findById(accountId);
        if (acc != null) {
            accountDAO.setActive(accountId, !acc.isActive());
        }
    }

    public void deleteAccount(int accountId) { accountDAO.delete(accountId); }
    public int countAccounts() { return accountDAO.count(); }
    public void updateLastCrawled(int accountId) { accountDAO.updateLastCrawled(accountId); }

    private AccountValidation validateRemoteAccount(String username, String platform) {
        return validateCodeforcesUser(username);
    }

    private AccountValidation validateCodeforcesUser(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://codeforces.com/api/user.info?handles=" + encoded))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new AccountValidation(false, "Không kiểm tra được Codeforces user, HTTP " + response.statusCode());
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (root.has("status") && "OK".equalsIgnoreCase(root.get("status").getAsString())
                    && root.has("result") && root.getAsJsonArray("result").size() > 0) {
                return new AccountValidation(true, "Codeforces user hợp lệ.");
            }
            return new AccountValidation(false, "Codeforces user không tồn tại.");
        } catch (Exception e) {
            return new AccountValidation(false, "Không kiểm tra được Codeforces user: " + e.getMessage());
        }
    }

    public record AddAccountResult(boolean success, int id, String error) {
        static AddAccountResult created(int id) {
            return new AddAccountResult(true, id, "");
        }

        static AddAccountResult failed(String error) {
            return new AddAccountResult(false, -1, error);
        }
    }

    private record AccountValidation(boolean exists, String message) {}
}
