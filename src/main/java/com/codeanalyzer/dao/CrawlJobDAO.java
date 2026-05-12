package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.CrawlJob;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CrawlJobDAO {

    public int insert(CrawlJob job) {
        String sql = "INSERT INTO crawl_jobs (status) VALUES (?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, job.getStatus());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) { job.setId(rs.getInt(1)); return job.getId(); }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tạo crawl job: " + e.getMessage());
        }
        return -1;
    }

    public void update(CrawlJob job) {
        String sql = "UPDATE crawl_jobs SET finished_at = ?, status = ?, accounts_processed = ?, " +
                     "submissions_crawled = ?, submissions_analyzed = ?, error_log = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, job.getFinishedAt() != null ? Timestamp.valueOf(job.getFinishedAt()) : null);
            ps.setString(2, job.getStatus());
            ps.setInt(3, job.getAccountsProcessed());
            ps.setInt(4, job.getSubmissionsCrawled());
            ps.setInt(5, job.getSubmissionsAnalyzed());
            ps.setString(6, job.getErrorLog());
            ps.setInt(7, job.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật crawl job: " + e.getMessage());
        }
    }

    public List<CrawlJob> findAll() {
        List<CrawlJob> list = new ArrayList<>();
        String sql = "SELECT * FROM crawl_jobs ORDER BY started_at DESC OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Lỗi lấy crawl jobs: " + e.getMessage());
        }
        return list;
    }

    public CrawlJob findLatest() {
        String sql = "SELECT * FROM crawl_jobs ORDER BY started_at DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { /* ignore */ }
        return null;
    }

    private CrawlJob mapRow(ResultSet rs) throws SQLException {
        CrawlJob j = new CrawlJob();
        j.setId(rs.getInt("id"));
        Timestamp st = rs.getTimestamp("started_at");
        if (st != null) j.setStartedAt(st.toLocalDateTime());
        Timestamp ft = rs.getTimestamp("finished_at");
        if (ft != null) j.setFinishedAt(ft.toLocalDateTime());
        j.setStatus(rs.getString("status"));
        j.setAccountsProcessed(rs.getInt("accounts_processed"));
        j.setSubmissionsCrawled(rs.getInt("submissions_crawled"));
        j.setSubmissionsAnalyzed(rs.getInt("submissions_analyzed"));
        j.setErrorLog(rs.getString("error_log"));
        return j;
    }
}
