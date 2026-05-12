package com.codeanalyzer;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.crawler.CrawlScheduler;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.AnalysisService;
import com.codeanalyzer.service.CrawlService;
import com.codeanalyzer.service.EvaluationService;
import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.dao.AiAnalysisDAO;
import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.AccountEvaluation;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainWeb {
    public static void main(String[] args) {
        AppConfig.loadFromDatabase();

        AccountService accountService = new AccountService();
        AnalysisService analysisService = new AnalysisService();
        CrawlService crawlService = new CrawlService();
        EvaluationService evaluationService = new EvaluationService();
        SubmissionDAO submissionDAO = new SubmissionDAO();
        AiAnalysisDAO aiAnalysisDAO = new AiAnalysisDAO();
        CrawlScheduler crawlScheduler = new CrawlScheduler();
        AtomicBoolean crawlRunning = new AtomicBoolean(false);
        AtomicBoolean analysisRunning = new AtomicBoolean(false);
        AtomicBoolean evaluationRunning = new AtomicBoolean(false);

        crawlScheduler.setCrawlTask(() -> {
            if (!crawlRunning.compareAndSet(false, true)) {
                System.out.println("[Scheduler] Bỏ qua lượt crawl vì đang có tiến trình crawl khác chạy.");
                return;
            }
            try {
                crawlService.crawlAll();
            } finally {
                crawlRunning.set(false);
            }
        });

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.jsonMapper(new JavalinJackson().updateMapper(m -> m.registerModule(new JavaTimeModule())));
        }).start(7070);

        System.out.println("Web server started at http://localhost:7070");
        crawlScheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            crawlScheduler.shutdown();
            com.codeanalyzer.config.DatabaseConfig.shutdown();
        }));

        // API Endpoints

        // Dashboard stats
        app.get("/api/stats", ctx -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAccounts", accountService.countAccounts());
            stats.put("totalSubmissions", submissionDAO.count());
            stats.put("totalAnalyses", analysisService.countAnalyses());
            ctx.json(stats);
        });

        // Get all accounts
        app.get("/api/accounts", ctx -> {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Account acc : accountService.getAllAccounts()) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", acc.getId());
                m.put("username", acc.getUsername());
                m.put("platform", acc.getPlatform());
                m.put("displayName", acc.getDisplayName());
                m.put("active", acc.isActive());
                m.put("addedAt", acc.getAddedAt() != null ? acc.getAddedAt().toString() : null);
                m.put("lastCrawledAt", acc.getLastCrawledAt() != null ? acc.getLastCrawledAt().toString() : null);
                m.put("submissionCount", submissionDAO.countByAccount(acc.getId()));
                result.add(m);
            }
            ctx.json(result);
        });

        // Add account
        app.post("/api/accounts", ctx -> {
            Account acc = ctx.bodyAsClass(Account.class);
            AccountService.AddAccountResult result = accountService.addAccount(
                    acc.getUsername(), acc.getPlatform(), acc.getDisplayName());
            if (result.success()) {
                ctx.status(201).json(Map.of("message", "Account added", "id", result.id()));
            } else {
                ctx.status(400).json(Map.of("error", result.error()));
            }
        });

        // Toggle active
        app.post("/api/accounts/{id}/toggle", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            accountService.toggleActive(id);
            ctx.json(Map.of("message", "Toggled"));
        });

        // Delete account
        app.delete("/api/accounts/{id}", ctx -> {
            if (crawlRunning.get() || analysisRunning.get() || evaluationRunning.get()) {
                ctx.status(409).json(Map.of("error", "Không thể xóa khi crawl/phân tích/đánh giá đang chạy"));
                return;
            }
            int id = Integer.parseInt(ctx.pathParam("id"));
            Account account = accountService.getAccount(id);
            if (account == null) {
                ctx.status(404).json(Map.of("error", "Account not found"));
                return;
            }
            accountService.deleteAccount(id);
            ctx.json(Map.of("message", "Deleted"));
        });

        // Crawl for an account
        app.post("/api/crawl/{id}", ctx -> {
            if (!crawlRunning.compareAndSet(false, true)) {
                ctx.status(409).json(Map.of("error", "Crawl is already running"));
                return;
            }
            int id = Integer.parseInt(ctx.pathParam("id"));
            Account account = accountService.getAccount(id);
            if (account != null) {
                new Thread(() -> {
                    try {
                        crawlService.crawlAccount(account);
                    } finally {
                        crawlRunning.set(false);
                    }
                }, "CrawlAccount-" + id).start();
                ctx.json(Map.of("message", "Crawl started for " + account.getUsername()));
            } else {
                crawlRunning.set(false);
                ctx.status(404).json(Map.of("error", "Account not found"));
            }
        });

        // Crawl all active accounts manually
        app.post("/api/crawl-all", ctx -> {
            if (!startCrawlAllAsync(crawlService, crawlRunning)) {
                ctx.status(409).json(Map.of("error", "Crawl is already running"));
                return;
            }
            ctx.json(Map.of("message", "Crawl all started"));
        });

        // Crawl job history
        app.get("/api/crawl/jobs", ctx -> ctx.json(crawlService.getCrawlHistory()));

        // Scheduler status/control
        app.get("/api/scheduler/status", ctx -> ctx.json(buildSchedulerStatus(crawlScheduler, crawlRunning)));

        app.post("/api/scheduler/start", ctx -> {
            crawlScheduler.start();
            ctx.json(buildSchedulerStatus(crawlScheduler, crawlRunning));
        });

        app.post("/api/scheduler/stop", ctx -> {
            crawlScheduler.stop();
            ctx.json(buildSchedulerStatus(crawlScheduler, crawlRunning));
        });

        app.post("/api/scheduler/restart", ctx -> {
            crawlScheduler.restart();
            ctx.json(buildSchedulerStatus(crawlScheduler, crawlRunning));
        });

        app.get("/api/config/scheduler", ctx -> {
            ctx.json(Map.of(
                    "intervalHours", AppConfig.getCrawlIntervalHours(),
                    "startTime", AppConfig.getCrawlStartTime()
            ));
        });

        app.post("/api/config/scheduler", ctx -> {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            int intervalHours = parseInt(body.get("intervalHours"), AppConfig.getCrawlIntervalHours());
            String startTime = String.valueOf(body.getOrDefault("startTime", AppConfig.getCrawlStartTime())).trim();

            if (intervalHours < 1 || intervalHours > 168) {
                ctx.status(400).json(Map.of("error", "intervalHours must be between 1 and 168"));
                return;
            }
            try {
                LocalTime.parse(startTime);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "startTime must use HH:mm format"));
                return;
            }

            AppConfig.set("crawl_interval_hours", String.valueOf(intervalHours));
            AppConfig.set("crawl_start_time", startTime);
            if (crawlScheduler.isRunning()) {
                crawlScheduler.restart();
            }
            ctx.json(buildSchedulerStatus(crawlScheduler, crawlRunning));
        });

        // Get submissions (exclude sourceCode to keep response small)
        app.get("/api/submissions", ctx -> {
            String accountIdParam = ctx.queryParam("accountId");
            List<com.codeanalyzer.model.Submission> subs;
            if (accountIdParam != null) {
                int accId = Integer.parseInt(accountIdParam);
                subs = submissionDAO.findByAccountId(accId);
            } else {
                subs = submissionDAO.search(null, null, null, null);
            }
            // Strip sourceCode to reduce payload size
            Map<Integer, Account> accountById = new HashMap<>();
            for (Account acc : accountService.getAllAccounts()) {
                accountById.put(acc.getId(), acc);
            }
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (com.codeanalyzer.model.Submission sub : subs) {
                Account owner = accountById.get(sub.getAccountId());
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", sub.getId());
                m.put("accountId", sub.getAccountId());
                m.put("username", owner != null ? owner.getUsername() : "");
                m.put("displayName", owner != null ? owner.getDisplayName() : "");
                m.put("submissionId", sub.getSubmissionId());
                m.put("problemId", sub.getProblemId());
                m.put("problemName", sub.getProblemName());
                m.put("contestId", sub.getContestId());
                m.put("language", sub.getLanguage());
                m.put("verdict", sub.getVerdict());
                m.put("submittedAt", sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : null);
                m.put("platform", sub.getPlatform());
                result.add(m);
            }
            ctx.json(result);
        });

        // Start analysis
        app.post("/api/analyze", ctx -> {
            if (!analysisRunning.compareAndSet(false, true)) {
                ctx.status(409).json(Map.of("error", "Analysis is already running"));
                return;
            }
            new Thread(() -> {
                try {
                    analysisService.analyzeUnanalyzed();
                } finally {
                    analysisRunning.set(false);
                }
            }).start();
            ctx.json(Map.of("message", "Analysis started"));
        });

        // Start analysis for one account
        app.post("/api/accounts/{id}/analyze", ctx -> {
            if (!analysisRunning.compareAndSet(false, true)) {
                ctx.status(409).json(Map.of("error", "Analysis is already running"));
                return;
            }
            int id = Integer.parseInt(ctx.pathParam("id"));
            Account account = accountService.getAccount(id);
            if (account == null) {
                analysisRunning.set(false);
                ctx.status(404).json(Map.of("error", "Account not found"));
                return;
            }
            new Thread(() -> {
                try {
                    analysisService.analyzeByAccount(id);
                } finally {
                    analysisRunning.set(false);
                }
            }).start();
            ctx.json(Map.of("message", "Analysis started for " + account.getUsername()));
        });

        // Get all analyses with problem info
        app.get("/api/analyses", ctx -> {
            ctx.json(aiAnalysisDAO.findAllWithSubmissionInfo());
        });

        // Get evaluations
        app.get("/api/evaluations", ctx -> {
            Map<Integer, Account> accountById = new HashMap<>();
            for (Account acc : accountService.getAllAccounts()) {
                accountById.put(acc.getId(), acc);
            }

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (AccountEvaluation eval : evaluationService.getAllEvaluations()) {
                Account owner = accountById.get(eval.getAccountId());
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", eval.getId());
                m.put("accountId", eval.getAccountId());
                m.put("username", owner != null ? owner.getUsername() : "");
                m.put("displayName", owner != null ? owner.getDisplayName() : "");
                m.put("platform", owner != null ? owner.getPlatform() : "");
                m.put("evaluatedAt", eval.getEvaluatedAt() != null ? eval.getEvaluatedAt().toString() : null);
                m.put("totalSubmissionsAnalyzed", eval.getTotalSubmissionsAnalyzed());
                m.put("dsScore", eval.getDsScore());
                m.put("dsMastered", eval.getDsMastered());
                m.put("dsLearning", eval.getDsLearning());
                m.put("algoScore", eval.getAlgoScore());
                m.put("algoMastered", eval.getAlgoMastered());
                m.put("algoLearning", eval.getAlgoLearning());
                m.put("avgAiUsageScore", eval.getAvgAiUsageScore());
                m.put("aiUsageLevel", eval.getAiUsageLevel());
                m.put("overallLevel", eval.getOverallLevel());
                m.put("strengths", eval.getStrengths());
                m.put("weaknesses", eval.getWeaknesses());
                m.put("recommendation", eval.getRecommendation());
                result.add(m);
            }
            ctx.json(result);
        });

        // Evaluate account
        app.post("/api/evaluate/{id}", ctx -> {
            if (!evaluationRunning.compareAndSet(false, true)) {
                ctx.status(409).json(Map.of("error", "Evaluation is already running"));
                return;
            }
            int id = Integer.parseInt(ctx.pathParam("id"));
            new Thread(() -> {
                try {
                    evaluationService.evaluate(id);
                } finally {
                    evaluationRunning.set(false);
                }
            }).start();
            ctx.json(Map.of("message", "Evaluation started"));
        });

        // Evaluate all active accounts
        app.post("/api/evaluate-all", ctx -> {
            if (!evaluationRunning.compareAndSet(false, true)) {
                ctx.status(409).json(Map.of("error", "Evaluation is already running"));
                return;
            }
            new Thread(() -> {
                try {
                    for (Account acc : accountService.getAllAccounts()) {
                        evaluationService.evaluate(acc.getId());
                    }
                } finally {
                    evaluationRunning.set(false);
                }
            }).start();
            ctx.json(Map.of("message", "Evaluating all accounts"));
        });
    }

    private static boolean startCrawlAllAsync(CrawlService crawlService, AtomicBoolean crawlRunning) {
        if (!crawlRunning.compareAndSet(false, true)) {
            return false;
        }

        new Thread(() -> {
            try {
                crawlService.crawlAll();
            } finally {
                crawlRunning.set(false);
            }
        }, "CrawlAll").start();
        return true;
    }

    private static Map<String, Object> buildSchedulerStatus(CrawlScheduler scheduler, AtomicBoolean crawlRunning) {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("schedulerRunning", scheduler.isRunning());
        status.put("crawlRunning", crawlRunning.get());
        status.put("intervalHours", AppConfig.getCrawlIntervalHours());
        status.put("startTime", AppConfig.getCrawlStartTime());
        return status;
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
