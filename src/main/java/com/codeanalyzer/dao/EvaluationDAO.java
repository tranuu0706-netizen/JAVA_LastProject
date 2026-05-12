package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.AccountEvaluation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EvaluationDAO {

    private static final Gson gson = new Gson();

    public int insert(AccountEvaluation eval) {
        String sql = "INSERT INTO account_evaluations (account_id, total_submissions_analyzed, " +
                     "ds_score, ds_mastered, ds_learning, algo_score, algo_mastered, algo_learning, " +
                     "avg_ai_usage_score, ai_usage_level, overall_level, strengths, weaknesses, recommendation) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, eval.getAccountId());
            ps.setInt(2, eval.getTotalSubmissionsAnalyzed());
            ps.setBigDecimal(3, eval.getDsScore());
            ps.setString(4, gson.toJson(eval.getDsMastered()));
            ps.setString(5, gson.toJson(eval.getDsLearning()));
            ps.setBigDecimal(6, eval.getAlgoScore());
            ps.setString(7, gson.toJson(eval.getAlgoMastered()));
            ps.setString(8, gson.toJson(eval.getAlgoLearning()));
            ps.setBigDecimal(9, eval.getAvgAiUsageScore());
            ps.setString(10, eval.getAiUsageLevel());
            ps.setString(11, eval.getOverallLevel());
            ps.setString(12, eval.getStrengths());
            ps.setString(13, eval.getWeaknesses());
            ps.setString(14, eval.getRecommendation());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) { eval.setId(rs.getInt(1)); return eval.getId(); }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lưu evaluation: " + e.getMessage());
        }
        return -1;
    }

    public AccountEvaluation findLatestByAccountId(int accountId) {
        String sql = "SELECT * FROM account_evaluations WHERE account_id = ? ORDER BY evaluated_at DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm evaluation: " + e.getMessage());
        }
        return null;
    }

    public List<AccountEvaluation> findAll() {
        List<AccountEvaluation> list = new ArrayList<>();
        String sql = "SELECT e.* FROM account_evaluations e " +
                     "INNER JOIN (SELECT account_id, MAX(evaluated_at) as max_date FROM account_evaluations GROUP BY account_id) latest " +
                     "ON e.account_id = latest.account_id AND e.evaluated_at = latest.max_date ORDER BY e.evaluated_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Lỗi lấy evaluations: " + e.getMessage());
        }
        return list;
    }

    private AccountEvaluation mapRow(ResultSet rs) throws SQLException {
        AccountEvaluation e = new AccountEvaluation();
        e.setId(rs.getInt("id"));
        e.setAccountId(rs.getInt("account_id"));
        Timestamp ts = rs.getTimestamp("evaluated_at");
        if (ts != null) e.setEvaluatedAt(ts.toLocalDateTime());
        e.setTotalSubmissionsAnalyzed(rs.getInt("total_submissions_analyzed"));
        e.setDsScore(rs.getBigDecimal("ds_score"));
        String dsm = rs.getString("ds_mastered");
        e.setDsMastered(dsm != null ? gson.fromJson(dsm, new TypeToken<List<String>>(){}.getType()) : new ArrayList<>());
        String dsl = rs.getString("ds_learning");
        e.setDsLearning(dsl != null ? gson.fromJson(dsl, new TypeToken<List<String>>(){}.getType()) : new ArrayList<>());
        e.setAlgoScore(rs.getBigDecimal("algo_score"));
        String am = rs.getString("algo_mastered");
        e.setAlgoMastered(am != null ? gson.fromJson(am, new TypeToken<List<String>>(){}.getType()) : new ArrayList<>());
        String al = rs.getString("algo_learning");
        e.setAlgoLearning(al != null ? gson.fromJson(al, new TypeToken<List<String>>(){}.getType()) : new ArrayList<>());
        e.setAvgAiUsageScore(rs.getBigDecimal("avg_ai_usage_score"));
        e.setAiUsageLevel(rs.getString("ai_usage_level"));
        e.setOverallLevel(rs.getString("overall_level"));
        e.setStrengths(rs.getString("strengths"));
        e.setWeaknesses(rs.getString("weaknesses"));
        e.setRecommendation(rs.getString("recommendation"));
        return e;
    }
}
