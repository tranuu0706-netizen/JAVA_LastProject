package com.codeanalyzer;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.crawler.CrawlScheduler;
import com.codeanalyzer.dao.AiAnalysisDAO;
import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.AnalysisService;
import com.codeanalyzer.service.CrawlService;
import com.codeanalyzer.service.EvaluationService;
import com.codeanalyzer.ui.*;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainSwing {

    public static void main(String[] args) {
        // Thiết lập FlatLaf Dark theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        } catch (Exception e) {
            System.err.println("Không thể load FlatLaf: " + e.getMessage());
        }

        // Load cấu hình
        AppConfig.loadFromDatabase();

        // Khởi tạo service
        AccountService accountService = new AccountService();
        AnalysisService analysisService = new AnalysisService();
        CrawlService crawlService = new CrawlService();
        EvaluationService evaluationService = new EvaluationService();
        SubmissionDAO submissionDAO = new SubmissionDAO();
        AiAnalysisDAO aiAnalysisDAO = new AiAnalysisDAO();
        CrawlScheduler crawlScheduler = new CrawlScheduler();
        AtomicBoolean crawlRunning = new AtomicBoolean(false);

        // Gắn task cho scheduler
        crawlScheduler.setCrawlTask(() -> {
            if (!crawlRunning.compareAndSet(false, true)) {
                System.out.println("[Scheduler] Bỏ qua vì đang có crawl khác chạy.");
                return;
            }
            try {
                crawlService.crawlAll();
            } finally {
                crawlRunning.set(false);
            }
        });
        crawlScheduler.start();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            crawlScheduler.shutdown();
            DatabaseConfig.shutdown();
        }));

        // Tạo giao diện trên EDT
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CodeAnalyzer - Phân tích năng lực lập trình");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 800);
            frame.setMinimumSize(new Dimension(960, 600));
            frame.setLocationRelativeTo(null);

            // --- Sidebar ---
            String[] menuItems = {
                "  Dashboard",
                "  Quản lý Nick",
                "  Crawl",
                "  Submissions",
                "  Phân tích AI",
                "  Đánh giá"
            };
            String[] panelKeys = {"dashboard", "accounts", "crawl", "submissions", "analysis", "evaluation"};

            JList<String> navList = new JList<>(menuItems);
            navList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            navList.setSelectedIndex(0);
            navList.setFixedCellHeight(48);
            navList.setFont(new Font("Segoe UI", Font.BOLD, 15));
            navList.setBackground(new Color(30, 32, 38));
            navList.setForeground(new Color(200, 200, 210));
            navList.setSelectionBackground(new Color(79, 70, 229));
            navList.setSelectionForeground(Color.WHITE);
            navList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            JLabel brandLabel = new JLabel("  CodeAnalyzer", SwingConstants.LEFT);
            brandLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            brandLabel.setForeground(new Color(129, 140, 248));
            brandLabel.setPreferredSize(new Dimension(220, 56));
            brandLabel.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

            JPanel sidebarPanel = new JPanel(new BorderLayout());
            sidebarPanel.setBackground(new Color(30, 32, 38));
            sidebarPanel.setPreferredSize(new Dimension(220, 0));
            sidebarPanel.add(brandLabel, BorderLayout.NORTH);
            sidebarPanel.add(new JScrollPane(navList), BorderLayout.CENTER);

            // --- Main content với CardLayout ---
            CardLayout cardLayout = new CardLayout();
            JPanel contentPanel = new JPanel(cardLayout);

            // Tạo các panel
            DashboardPanel dashboardPanel = new DashboardPanel(accountService, submissionDAO, analysisService);
            AccountPanel accountPanel = new AccountPanel(accountService, submissionDAO, crawlService, analysisService, evaluationService, crawlRunning);
            CrawlPanel crawlPanel = new CrawlPanel(crawlService, crawlScheduler, crawlRunning);
            SubmissionPanel submissionPanel = new SubmissionPanel(submissionDAO, accountService);
            AnalysisPanel analysisPanel = new AnalysisPanel(aiAnalysisDAO, analysisService);
            EvaluationPanel evaluationPanel = new EvaluationPanel(evaluationService, accountService);

            contentPanel.add(dashboardPanel, "dashboard");
            contentPanel.add(accountPanel, "accounts");
            contentPanel.add(crawlPanel, "crawl");
            contentPanel.add(submissionPanel, "submissions");
            contentPanel.add(analysisPanel, "analysis");
            contentPanel.add(evaluationPanel, "evaluation");

            // Chuyển tab
            navList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int idx = navList.getSelectedIndex();
                    if (idx >= 0) {
                        cardLayout.show(contentPanel, panelKeys[idx]);
                        // Refresh data khi chuyển tab
                        switch (panelKeys[idx]) {
                            case "dashboard" -> dashboardPanel.refreshData();
                            case "accounts" -> accountPanel.refreshData();
                            case "crawl" -> crawlPanel.refreshData();
                            case "submissions" -> submissionPanel.refreshData();
                            case "analysis" -> analysisPanel.refreshData();
                            case "evaluation" -> evaluationPanel.refreshData();
                        }
                    }
                }
            });

            // Layout chính
            frame.setLayout(new BorderLayout());
            frame.add(sidebarPanel, BorderLayout.WEST);
            frame.add(contentPanel, BorderLayout.CENTER);

            // Load dữ liệu ban đầu
            dashboardPanel.refreshData();

            frame.setVisible(true);
            System.out.println("✓ CodeAnalyzer Swing UI đã khởi động.");
        });
    }
}
