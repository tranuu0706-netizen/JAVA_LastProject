package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.Account;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho bảng accounts - quản lý tài khoản lập trình viên.
 */
public class AccountDAO {

    /**
     * Thêm tài khoản mới.
     * @return ID của account vừa tạo, hoặc -1 nếu lỗi.
     */
    public int insert(Account account) {
        String sql = "INSERT INTO accounts (username, platform, display_name, is_active) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, account.getUsername());
            ps.setString(2, account.getPlatform());
            ps.setString(3, account.getDisplayName());
            ps.setBoolean(4, account.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    account.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi thêm account: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lấy tất cả account.
     */
    public List<Account> findAll() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts ORDER BY added_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách account: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy tất cả account đang active.
     */
    public List<Account> findActive() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts WHERE is_active = 1 ORDER BY username";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy active accounts: " + e.getMessage());
        }
        return list;
    }

    /**
     * Tìm account theo ID.
     */
    public Account findById(int id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm account: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tìm account theo username và platform.
     */
    public Account findByUsernameAndPlatform(String username, String platform) {
        String sql = "SELECT * FROM accounts WHERE username = ? AND platform = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, platform);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm account: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật thời gian crawl cuối.
     */
    public void updateLastCrawled(int accountId) {
        String sql = "UPDATE accounts SET last_crawled_at = GETDATE() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật last_crawled_at: " + e.getMessage());
        }
    }

    /**
     * Kích hoạt/vô hiệu hóa account.
     */
    public void setActive(int accountId, boolean active) {
        String sql = "UPDATE accounts SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái: " + e.getMessage());
        }
    }

    /**
     * Xóa account.
     */
    public void delete(int accountId) {
        String sql = "DELETE FROM accounts WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi xóa account: " + e.getMessage());
        }
    }

    /**
     * Đếm tổng số account.
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM accounts";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm account: " + e.getMessage());
        }
        return 0;
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("id"));
        a.setUsername(rs.getString("username"));
        a.setPlatform(rs.getString("platform"));
        a.setDisplayName(rs.getString("display_name"));
        Timestamp addedTs = rs.getTimestamp("added_at");
        if (addedTs != null) a.setAddedAt(addedTs.toLocalDateTime());
        Timestamp crawledTs = rs.getTimestamp("last_crawled_at");
        if (crawledTs != null) a.setLastCrawledAt(crawledTs.toLocalDateTime());
        a.setActive(rs.getBoolean("is_active"));
        return a;
    }
}
