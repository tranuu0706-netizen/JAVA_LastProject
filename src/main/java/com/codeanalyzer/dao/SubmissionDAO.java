package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.Submission;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho bảng submissions.
 */
public class SubmissionDAO {

    /**
     * Thêm submission mới.
     * @return ID tự sinh, hoặc -1 nếu lỗi.
     */
    public long insert(Submission sub) {
        String sql = "INSERT INTO submissions (account_id, submission_id, problem_id, problem_name, " +
                     "contest_id, language, verdict, submitted_at, source_code, platform) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sub.getAccountId());
            ps.setString(2, sub.getSubmissionId());
            ps.setString(3, sub.getProblemId());
            ps.setString(4, sub.getProblemName());
            ps.setString(5, sub.getContestId());
            ps.setString(6, sub.getLanguage());
            ps.setString(7, sub.getVerdict());
            ps.setTimestamp(8, sub.getSubmittedAt() != null ? Timestamp.valueOf(sub.getSubmittedAt()) : null);
            ps.setString(9, sub.getSourceCode());
            ps.setString(10, sub.getPlatform());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    sub.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            // Duplicate submission - bỏ qua
            if (!e.getMessage().contains("Duplicate")) {
                System.err.println("Lỗi thêm submission: " + e.getMessage());
            }
        }
        return -1;
    }

    /**
     * Kiểm tra submission đã tồn tại chưa.
     */
    public boolean exists(String submissionId, String platform) {
        String sql = "SELECT 1 FROM submissions WHERE submission_id = ? AND platform = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submissionId);
            ps.setString(2, platform);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra submission: " + e.getMessage());
        }
        return false;
    }

    /**
     * Cập nhật lại thời điểm nộp gốc cho submission đã có trong DB.
     */
    public boolean updateSubmittedAt(String submissionId, String platform, java.time.LocalDateTime submittedAt) {
        if (submittedAt == null) return false;

        String sql = "UPDATE submissions SET submitted_at = ? WHERE submission_id = ? AND platform = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(submittedAt));
            ps.setString(2, submissionId);
            ps.setString(3, platform);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật submitted_at: " + e.getMessage());
        }
        return false;
    }

    /**
     * Lấy tất cả submissions của một account.
     */
    public List<Submission> findByAccountId(int accountId) {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT * FROM submissions WHERE account_id = ? ORDER BY submitted_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy submissions: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy submission theo ID nội bộ.
     */
    public Submission findById(long id) {
        String sql = "SELECT * FROM submissions WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm submission: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy submissions chưa được phân tích AI.
     */
    public List<Submission> findUnanalyzed(int limit) {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT s.* FROM submissions s " +
                     "LEFT JOIN ai_analysis a ON s.id = a.submission_id " +
                     "WHERE a.id IS NULL AND s.source_code IS NOT NULL AND s.source_code != '' " +
                     "ORDER BY s.submitted_at DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy unanalyzed submissions: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy submissions chưa phân tích của 1 account.
     */
    public List<Submission> findUnanalyzedByAccount(int accountId, int limit) {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT s.* FROM submissions s " +
                     "LEFT JOIN ai_analysis a ON s.id = a.submission_id " +
                     "WHERE a.id IS NULL AND s.account_id = ? AND s.source_code IS NOT NULL AND s.source_code != '' " +
                     "ORDER BY s.submitted_at DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy unanalyzed submissions: " + e.getMessage());
        }
        return list;
    }

    /**
     * Tìm kiếm/lọc submissions.
     */
    public List<Submission> search(Integer accountId, String platform, String verdict, String language) {
        List<Submission> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM submissions WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (accountId != null) {
            sql.append("AND account_id = ? ");
            params.add(accountId);
        }
        if (platform != null && !platform.isEmpty()) {
            sql.append("AND platform = ? ");
            params.add(platform);
        }
        if (verdict != null && !verdict.isEmpty()) {
            sql.append("AND verdict = ? ");
            params.add(verdict);
        }
        if (language != null && !language.isEmpty()) {
            sql.append("AND language LIKE ? ");
            params.add("%" + language + "%");
        }
        sql.append("ORDER BY submitted_at DESC OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, (String) p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi search submissions: " + e.getMessage());
        }
        return list;
    }

    /**
     * Đếm tổng submissions.
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM submissions";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm submissions: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Đếm submissions theo account.
     */
    public int countByAccount(int accountId) {
        String sql = "SELECT COUNT(*) FROM submissions WHERE account_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đếm submissions: " + e.getMessage());
        }
        return 0;
    }

    private Submission mapRow(ResultSet rs) throws SQLException {
        Submission s = new Submission();
        s.setId(rs.getLong("id"));
        s.setAccountId(rs.getInt("account_id"));
        s.setSubmissionId(rs.getString("submission_id"));
        s.setProblemId(rs.getString("problem_id"));
        s.setProblemName(rs.getString("problem_name"));
        s.setContestId(rs.getString("contest_id"));
        s.setLanguage(rs.getString("language"));
        s.setVerdict(rs.getString("verdict"));
        Timestamp subTs = rs.getTimestamp("submitted_at");
        if (subTs != null) s.setSubmittedAt(subTs.toLocalDateTime());
        s.setSourceCode(rs.getString("source_code"));
        Timestamp crawlTs = rs.getTimestamp("crawled_at");
        if (crawlTs != null) s.setCrawledAt(crawlTs.toLocalDateTime());
        s.setPlatform(rs.getString("platform"));
        return s;
    }
}
