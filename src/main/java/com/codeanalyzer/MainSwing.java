package com.codeanalyzer;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.crawler.CrawlScheduler;
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
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Button.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            UIManager.put("ScrollBar.track", UIHelper.BG_DARK);
            UIManager.put("ScrollBar.thumb", new Color(73, 82, 96));
            UIManager.put("TextField.background", UIHelper.FIELD_BG);
            UIManager.put("TextField.foreground", UIHelper.TEXT_MAIN);
            UIManager.put("TextField.caretForeground", UIHelper.TEXT_MAIN);
            UIManager.put("PasswordField.background", UIHelper.FIELD_BG);
            UIManager.put("PasswordField.foreground", UIHelper.TEXT_MAIN);
            UIManager.put("ComboBox.background", UIHelper.FIELD_BG);
            UIManager.put("ComboBox.foreground", UIHelper.TEXT_MAIN);
            UIManager.put("Spinner.background", UIHelper.FIELD_BG);
            UIManager.put("TabbedPane.background", UIHelper.CARD_BG);
            UIManager.put("TabbedPane.foreground", UIHelper.TEXT_MUTED);
            UIManager.put("TabbedPane.selectedBackground", UIHelper.FIELD_BG);
            UIManager.put("ToolTip.background", UIHelper.FIELD_BG);
            UIManager.put("ToolTip.foreground", UIHelper.TEXT_MAIN);
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
        CrawlScheduler crawlScheduler = new CrawlScheduler();
        AtomicBoolean crawlRunning = new AtomicBoolean(false);

        // Gắn task cho scheduler
        crawlScheduler.setCrawlTask(() -> {
            if (!crawlRunning.compareAndSet(false, true)) {
                System.out.println("[Scheduler] Bỏ qua vì đang có crawl khác chạy.");
                return;
            }
            try {
                crawlService.resetCancellation();
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
                "Trang chính",
                "Quản lý tài khoản",
                "Lịch sử Crawl",
                "Submissions",
                "Phân tích AI",
                "Đánh giá",
                "Cài đặt"
            };
            String[] panelKeys = {"dashboard", "accounts", "crawl", "submissions", "analysis", "evaluation", "settings"};

            JList<String> navList = new JList<>(menuItems);
            navList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            navList.setSelectedIndex(0);
            navList.setFixedCellHeight(50);
            navList.setFont(new Font("Segoe UI", Font.BOLD, 14));
            navList.setBackground(UIHelper.SIDEBAR_BG);
            navList.setForeground(UIHelper.TEXT_MUTED);
            navList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            navList.setCellRenderer(new NavCellRenderer());

            JPanel sidebarPanel = new JPanel(new BorderLayout());
            sidebarPanel.setBackground(UIHelper.SIDEBAR_BG);
            sidebarPanel.setPreferredSize(new Dimension(244, 0));
            sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIHelper.CARD_BORDER));
            sidebarPanel.add(createBrandPanel(), BorderLayout.NORTH);

            JScrollPane navScroll = new JScrollPane(navList,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            navScroll.setBorder(BorderFactory.createEmptyBorder());
            navScroll.getViewport().setBackground(UIHelper.SIDEBAR_BG);
            sidebarPanel.add(navScroll, BorderLayout.CENTER);

            // --- Main content với CardLayout ---
            CardLayout cardLayout = new CardLayout();
            JPanel contentPanel = new JPanel(cardLayout);
            contentPanel.setBackground(UIHelper.BG_DARK);

            // Tạo các panel
            DashboardPanel dashboardPanel = new DashboardPanel(accountService, submissionDAO, analysisService, crawlService);
            AccountPanel accountPanel = new AccountPanel(accountService, submissionDAO, crawlService, analysisService, evaluationService, crawlRunning);
            CrawlPanel crawlPanel = new CrawlPanel(crawlService, crawlScheduler, crawlRunning);
            SubmissionPanel submissionPanel = new SubmissionPanel(submissionDAO, accountService, analysisService);
            AnalysisPanel analysisPanel = new AnalysisPanel(analysisService);
            EvaluationPanel evaluationPanel = new EvaluationPanel(evaluationService, accountService);
            SettingsPanel settingsPanel = new SettingsPanel(crawlScheduler);

            contentPanel.add(dashboardPanel, "dashboard");
            contentPanel.add(accountPanel, "accounts");
            contentPanel.add(crawlPanel, "crawl");
            contentPanel.add(submissionPanel, "submissions");
            contentPanel.add(analysisPanel, "analysis");
            contentPanel.add(evaluationPanel, "evaluation");
            contentPanel.add(settingsPanel, "settings");

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
                            case "settings" -> settingsPanel.refreshData();
                        }
                    }
                }
            });

            // Layout chính
            frame.setLayout(new BorderLayout());
            frame.getContentPane().setBackground(UIHelper.BG_DARK);
            frame.add(sidebarPanel, BorderLayout.WEST);
            frame.add(contentPanel, BorderLayout.CENTER);

            // Load dữ liệu ban đầu
            dashboardPanel.refreshData();

            frame.setVisible(true);
            System.out.println("✓ CodeAnalyzer Swing UI đã khởi động.");
        });
    }

    private static JPanel createBrandPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(UIHelper.SIDEBAR_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));

        JLabel brand = new JLabel("CodeAnalyzer");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 22));
        brand.setForeground(UIHelper.TEXT_MAIN);

        JLabel subtitle = new JLabel("Phân tích năng lực lập trình");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(UIHelper.TEXT_SUBTLE);

        panel.add(brand, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private static class NavCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JPanel marker = new JPanel();
        private final JLabel label = new JLabel();

        NavCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            marker.setPreferredSize(new Dimension(4, 0));
            label.setFont(new Font("Segoe UI", Font.BOLD, 14));
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            add(marker, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(value);
            label.setForeground(isSelected ? Color.WHITE : UIHelper.TEXT_MUTED);
            marker.setBackground(isSelected ? UIHelper.PRIMARY : UIHelper.SIDEBAR_BG);
            setBackground(isSelected ? new Color(39, 45, 82) : UIHelper.SIDEBAR_BG);
            return this;
        }
    }
}
