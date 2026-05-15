-- ═══════════════════════════════════════════════════════════
-- CodeAnalyzer Database Schema (SQL Server)
-- Database: code_analyzer
-- ═══════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'code_analyzer')
BEGIN
    CREATE DATABASE code_analyzer;
END
GO

USE code_analyzer;
GO

-- Bảng tài khoản người dùng trong hệ thống
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[accounts]') AND type in (N'U'))
BEGIN
    CREATE TABLE accounts (
        id INT IDENTITY(1,1) PRIMARY KEY,
        username NVARCHAR(100) NOT NULL,
        platform VARCHAR(50) NOT NULL CHECK (platform IN ('CODEFORCES')),
        display_name NVARCHAR(200),
        added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        last_crawled_at DATETIME NULL,
        is_active BIT DEFAULT 1,
        CONSTRAINT uq_account UNIQUE (username, platform)
    );
END
GO

-- Bảng bài nộp (submission) đã crawl được
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[submissions]') AND type in (N'U'))
BEGIN
    CREATE TABLE submissions (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        account_id INT NOT NULL,
        submission_id VARCHAR(100) NOT NULL,
        problem_id VARCHAR(100),
        problem_name NVARCHAR(500),
        contest_id VARCHAR(100),
        language VARCHAR(100),
        verdict VARCHAR(50),
        submitted_at DATETIME,
        source_code NVARCHAR(MAX),
        crawled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        platform VARCHAR(50) NOT NULL CHECK (platform IN ('CODEFORCES')),
        analysis_status VARCHAR(30) NOT NULL DEFAULT 'UNANALYZED',
        analysis_error NVARCHAR(MAX) NULL,
        CONSTRAINT uq_submission UNIQUE (submission_id, platform),
        CONSTRAINT fk_sub_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
    );
END
GO

-- Bảng kết quả phân tích AI cho từng submission
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ai_analysis]') AND type in (N'U'))
BEGIN
    CREATE TABLE ai_analysis (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        submission_id BIGINT NOT NULL UNIQUE,
        data_structures NVARCHAR(MAX),
        algorithms NVARCHAR(MAX),
        complexity_time VARCHAR(50),
        complexity_space VARCHAR(50),
        ai_usage_score INT,
        ai_usage_reason NVARCHAR(MAX),
        difficulty_level VARCHAR(50) CHECK (difficulty_level IN ('BEGINNER','EASY','MEDIUM','HARD','EXPERT')),
        code_quality_score INT,
        analysis_summary NVARCHAR(MAX),
        analyzed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_analysis_sub FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
    );
END
GO

-- Bảng đánh giá tổng hợp theo tài khoản
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[account_evaluations]') AND type in (N'U'))
BEGIN
    CREATE TABLE account_evaluations (
        id INT IDENTITY(1,1) PRIMARY KEY,
        account_id INT NOT NULL,
        evaluated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        total_submissions_analyzed INT DEFAULT 0,
        ds_score DECIMAL(5,2),
        ds_mastered NVARCHAR(MAX),
        ds_learning NVARCHAR(MAX),
        algo_score DECIMAL(5,2),
        algo_mastered NVARCHAR(MAX),
        algo_learning NVARCHAR(MAX),
        avg_ai_usage_score DECIMAL(5,2),
        ai_usage_level VARCHAR(50) CHECK (ai_usage_level IN ('CLEAN','LOW','MEDIUM','HIGH','VERY_HIGH')),
        overall_level VARCHAR(50) CHECK (overall_level IN ('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')),
        strengths NVARCHAR(MAX),
        weaknesses NVARCHAR(MAX),
        recommendation NVARCHAR(MAX),
        CONSTRAINT fk_eval_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
    );
END
GO

-- Bảng lịch sử crawl job
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[crawl_jobs]') AND type in (N'U'))
BEGIN
    CREATE TABLE crawl_jobs (
        id INT IDENTITY(1,1) PRIMARY KEY,
        started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        finished_at DATETIME NULL,
        status VARCHAR(50) CHECK (status IN ('RUNNING','SUCCESS','FAILED','PARTIAL')),
        accounts_processed INT DEFAULT 0,
        submissions_scanned INT DEFAULT 0,
        submissions_crawled INT DEFAULT 0,
        submissions_skipped INT DEFAULT 0,
        submissions_analyzed INT DEFAULT 0,
        crawl_log NVARCHAR(MAX) NULL,
        error_log NVARCHAR(MAX) NULL
    );
END
GO

-- Bảng cấu hình hệ thống
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[system_config]') AND type in (N'U'))
BEGIN
    CREATE TABLE system_config (
        config_key VARCHAR(100) PRIMARY KEY,
        config_value NVARCHAR(MAX),
        description NVARCHAR(500)
    );
END
GO
