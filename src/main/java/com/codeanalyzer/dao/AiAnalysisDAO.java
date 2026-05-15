package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.AiAnalysis;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AiAnalysisDAO {

    private static final Gson gson = new Gson();

    public long insert(AiAnalysis analysis) {
        String sql = "INSERT INTO ai_analysis (submission_id, data_structures, algorithms, " +
                     "complexity_time, complexity_space, ai_usage_score, ai_usage_reason, " +
                     "difficulty_level, code_quality_score, analysis_summary) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, analysis.getSubmissionId());
            ps.setString(2, gson.toJson(analysis.getDataStructures()));
            ps.setString(3, gson.toJson(analysis.getAlgorithms()));
            ps.setString(4, analysis.getComplexityTime());
            ps.setString(5, analysis.getComplexitySpace());
            ps.setInt(6, analysis.getAiUsageScore());
            ps.setString(7, analysis.getAiUsageReason());
            ps.setString(8, analysis.getDifficultyLevel());
            ps.setInt(9, analysis.getCodeQualityScore());
            ps.setString(10, analysis.getAnalysisSummary());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    analysis.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lưu AI analysis: " + e.getMessage());
        }
        return -1;
    }

    public long saveOrUpdate(AiAnalysis analysis) {
        AiAnalysis existing = findBySubmissionId(analysis.getSubmissionId());
        if (existing == null) {
            return insert(analysis);
        }
        analysis.setId(existing.getId());
        return update(analysis) ? existing.getId() : -1;
    }

    public boolean update(AiAnalysis analysis) {
        String sql = "UPDATE ai_analysis SET data_structures = ?, algorithms = ?, " +
                     "complexity_time = ?, complexity_space = ?, ai_usage_score = ?, " +
                     "ai_usage_reason = ?, difficulty_level = ?, code_quality_score = ?, " +
                     "analysis_summary = ?, analyzed_at = CURRENT_TIMESTAMP WHERE submission_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gson.toJson(analysis.getDataStructures()));
            ps.setString(2, gson.toJson(analysis.getAlgorithms()));
            ps.setString(3, analysis.getComplexityTime());
            ps.setString(4, analysis.getComplexitySpace());
            ps.setInt(5, analysis.getAiUsageScore());
            ps.setString(6, analysis.getAiUsageReason());
            ps.setString(7, analysis.getDifficultyLevel());
            ps.setInt(8, analysis.getCodeQualityScore());
            ps.setString(9, analysis.getAnalysisSummary());
            ps.setLong(10, analysis.getSubmissionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật AI analysis: " + e.getMessage());
        }
        return false;
    }

    public AiAnalysis findBySubmissionId(long submissionId) {
        String sql = "SELECT * FROM ai_analysis WHERE submission_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm analysis: " + e.getMessage());
        }
        return null;
    }

    public List<AiAnalysis> findByAccountId(int accountId) {
        List<AiAnalysis> list = new ArrayList<>();
        String sql = "SELECT a.* FROM ai_analysis a JOIN submissions s ON a.submission_id = s.id " +
                     "WHERE s.account_id = ? ORDER BY a.analyzed_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy analyses: " + e.getMessage());
        }
        return list;
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM ai_analysis";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { /* ignore */ }
        return 0;
    }

    public List<String> getAllDataStructures() {
        List<String> all = new ArrayList<>();
        String sql = "SELECT data_structures FROM ai_analysis WHERE data_structures IS NOT NULL";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString("data_structures");
                if (json != null && !json.isEmpty()) {
                    List<String> ds = parseStringList(json);
                    if (ds != null) all.addAll(ds);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy CTDL thống kê: " + e.getMessage());
        }
        return all;
    }

    public List<String> getAllAlgorithms() {
        List<String> all = new ArrayList<>();
        String sql = "SELECT algorithms FROM ai_analysis WHERE algorithms IS NOT NULL";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString("algorithms");
                if (json != null && !json.isEmpty()) {
                    List<String> algos = parseStringList(json);
                    if (algos != null) all.addAll(algos);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy thuật toán thống kê: " + e.getMessage());
        }
        return all;
    }

    private AiAnalysis mapRow(ResultSet rs) throws SQLException {
        AiAnalysis a = new AiAnalysis();
        a.setId(rs.getLong("id"));
        a.setSubmissionId(rs.getLong("submission_id"));
        String dsJson = rs.getString("data_structures");
        a.setDataStructures(dsJson != null ? parseStringList(dsJson) : new ArrayList<>());
        String algoJson = rs.getString("algorithms");
        a.setAlgorithms(algoJson != null ? parseStringList(algoJson) : new ArrayList<>());
        a.setComplexityTime(rs.getString("complexity_time"));
        a.setComplexitySpace(rs.getString("complexity_space"));
        a.setAiUsageScore(rs.getInt("ai_usage_score"));
        a.setAiUsageReason(rs.getString("ai_usage_reason"));
        a.setDifficultyLevel(rs.getString("difficulty_level"));
        a.setCodeQualityScore(rs.getInt("code_quality_score"));
        a.setAnalysisSummary(rs.getString("analysis_summary"));
        Timestamp ts = rs.getTimestamp("analyzed_at");
        if (ts != null) a.setAnalyzedAt(ts.toLocalDateTime());
        return a;
    }

    private List<String> parseStringList(String json) {
        try {
            List<String> values = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
            return values != null ? values : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Lấy tất cả analyses kèm thông tin bài tập (JOIN submissions).
     */
    public List<java.util.Map<String, Object>> findAllWithSubmissionInfo() {
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        String sql = "SELECT a.*, s.problem_id, s.problem_name, s.language, " +
                     "acc.id AS account_id, acc.username, acc.display_name " +
                     "FROM ai_analysis a " +
                     "JOIN submissions s ON a.submission_id = s.id " +
                     "JOIN accounts acc ON s.account_id = acc.id " +
                     "ORDER BY a.analyzed_at DESC " +
                     "OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("problemId", rs.getString("problem_id"));
                m.put("problemName", rs.getString("problem_name"));
                m.put("language", rs.getString("language"));
                m.put("accountId", rs.getInt("account_id"));
                m.put("username", rs.getString("username"));
                m.put("displayName", rs.getString("display_name"));
                m.put("dataStructures", rs.getString("data_structures"));
                m.put("algorithms", rs.getString("algorithms"));
                m.put("complexityTime", rs.getString("complexity_time"));
                m.put("complexitySpace", rs.getString("complexity_space"));
                m.put("aiUsageScore", rs.getInt("ai_usage_score"));
                m.put("aiUsageReason", rs.getString("ai_usage_reason"));
                m.put("difficultyLevel", rs.getString("difficulty_level"));
                m.put("codeQualityScore", rs.getInt("code_quality_score"));
                m.put("analysisSummary", rs.getString("analysis_summary"));
                Timestamp ts = rs.getTimestamp("analyzed_at");
                m.put("analyzedAt", ts != null ? ts.toLocalDateTime().toString() : null);
                list.add(m);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy all analyses: " + e.getMessage());
        }
        return list;
    }
}
