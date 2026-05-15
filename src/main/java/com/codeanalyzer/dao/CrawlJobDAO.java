package com.codeanalyzer.dao;

import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.model.CrawlJob;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrawlJobDAO {
    private static final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public int insert(CrawlJob job) {
        ensureSchema();
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
        ensureSchema();
        String sql = "UPDATE crawl_jobs SET finished_at = ?, status = ?, accounts_processed = ?, " +
                     "submissions_scanned = ?, submissions_crawled = ?, submissions_skipped = ?, " +
                     "submissions_analyzed = ?, crawl_log = ?, error_log = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, job.getFinishedAt() != null ? Timestamp.valueOf(job.getFinishedAt()) : null);
            ps.setString(2, job.getStatus());
            ps.setInt(3, job.getAccountsProcessed());
            ps.setInt(4, job.getSubmissionsScanned());
            ps.setInt(5, job.getSubmissionsCrawled());
            ps.setInt(6, job.getSubmissionsSkipped());
            ps.setInt(7, job.getSubmissionsAnalyzed());
            ps.setString(8, job.getCrawlLog());
            ps.setString(9, job.getErrorLog());
            ps.setInt(10, job.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật crawl job: " + e.getMessage());
        }
    }

    public List<CrawlJob> findAll() {
        ensureSchema();
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
        ensureSchema();
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
        j.setSubmissionsScanned(rs.getInt("submissions_scanned"));
        j.setSubmissionsCrawled(rs.getInt("submissions_crawled"));
        j.setSubmissionsSkipped(rs.getInt("submissions_skipped"));
        j.setSubmissionsAnalyzed(rs.getInt("submissions_analyzed"));
        j.setCrawlLog(rs.getString("crawl_log"));
        j.setErrorLog(rs.getString("error_log"));
        return j;
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                    IF COL_LENGTH('dbo.crawl_jobs', 'submissions_scanned') IS NULL
                    BEGIN
                        ALTER TABLE dbo.crawl_jobs ADD submissions_scanned INT NOT NULL
                            CONSTRAINT DF_crawl_jobs_submissions_scanned DEFAULT 0
                    END
                    """);
            st.execute("""
                    IF COL_LENGTH('dbo.crawl_jobs', 'submissions_skipped') IS NULL
                    BEGIN
                        ALTER TABLE dbo.crawl_jobs ADD submissions_skipped INT NOT NULL
                            CONSTRAINT DF_crawl_jobs_submissions_skipped DEFAULT 0
                    END
                    """);
            st.execute("""
                    IF COL_LENGTH('dbo.crawl_jobs', 'crawl_log') IS NULL
                    BEGIN
                        ALTER TABLE dbo.crawl_jobs ADD crawl_log NVARCHAR(MAX) NULL
                    END
                    """);
        } catch (SQLException e) {
            schemaEnsured.set(false);
            System.err.println("Lỗi cập nhật schema crawl_jobs: " + e.getMessage());
        }
    }
}
