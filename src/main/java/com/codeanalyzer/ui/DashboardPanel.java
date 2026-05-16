package com.codeanalyzer.ui;

import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.model.CrawlJob;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.AnalysisService;
import com.codeanalyzer.service.CrawlService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Panel Trang chính: hiển thị thống kê tổng quan hệ thống.
 */
public class DashboardPanel extends JPanel {

    private final AccountService accountService;
    private final SubmissionDAO submissionDAO;
    private final AnalysisService analysisService;
    private final CrawlService crawlService;

    private final JLabel lblHeroSummary = new JLabel("Đang tải dữ liệu hệ thống...");
    private final JLabel lblSystemSignal = new JLabel("Sẵn sàng");
    private final JLabel lblAccounts = new JLabel("0");
    private final JLabel lblSubmissions = new JLabel("0");
    private final JLabel lblAnalyses = new JLabel("0");
    private final JLabel lblLastCrawl = new JLabel("Chưa có");
    private final CoverageRingPanel coverageRing = new CoverageRingPanel();
    private final InsightPanel insightPanel = new InsightPanel();
    private final RankingPanel dataStructureChart = new RankingPanel("Top cấu trúc dữ liệu", "CTDL được AI nhận diện");
    private final RankingPanel algorithmChart = new RankingPanel("Top thuật toán", "Thuật toán xuất hiện nhiều");

    public DashboardPanel(AccountService accountService, SubmissionDAO submissionDAO,
            AnalysisService analysisService, CrawlService crawlService) {
        this.accountService = accountService;
        this.submissionDAO = submissionDAO;
        this.analysisService = analysisService;
        this.crawlService = crawlService;
        this.crawlService.addJobListener(job -> SwingUtilities.invokeLater(this::refreshData));

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        add(UIHelper.createHeader("Trang chính", btnRefresh), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 12, 0);
        gbc.weightx = 1;

        gbc.gridy = 0;
        gbc.weighty = 0.30;
        body.add(createHeroPanel(), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.20;
        body.add(createStatsGrid(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.50;
        gbc.insets = new Insets(0, 0, 0, 0);
        body.add(createChartsGrid(), gbc);

        add(body, BorderLayout.CENTER);
    }

    private JPanel createHeroPanel() {
        JPanel hero = new JPanel(new BorderLayout(22, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(15, 18, 23));
                g2.fillRect(0, 0, w, h);

                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(44, 54, 83));
                for (int x = 28; x < w; x += 82) {
                    g2.drawLine(x, 0, Math.min(w, x + 120), h);
                }

                g2.setStroke(new BasicStroke(3f));
                g2.setColor(new Color(91, 103, 245, 155));
                g2.drawLine(0, h - 4, w / 3, h - 4);
                g2.setColor(new Color(22, 163, 116, 130));
                g2.drawLine(w / 3, h - 4, w, h - 4);

                g2.dispose();
            }
        };
        hero.setMinimumSize(new Dimension(0, 132));
        hero.setPreferredSize(new Dimension(0, 150));
        hero.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(59, 70, 92)),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel eyebrow = UIHelper.styledLabel("CODEANALYZER INTELLIGENCE CENTER",
                new Font("Segoe UI", Font.BOLD, 12), new Color(134, 239, 172));
        JLabel title = UIHelper.styledLabel("Bảng điều khiển năng lực lập trình",
                new Font("Segoe UI", Font.BOLD, 24), UIHelper.TEXT_MAIN);
        lblHeroSummary.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblHeroSummary.setForeground(new Color(201, 210, 224));
        lblSystemSignal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblSystemSignal.setForeground(UIHelper.TEXT_MUTED);

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(6));
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(lblHeroSummary);
        copy.add(Box.createVerticalStrut(6));
        copy.add(lblSystemSignal);

        JPanel right = new JPanel(new GridLayout(1, 2, 14, 0));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(460, 0));
        right.add(coverageRing);
        right.add(insightPanel);

        hero.add(copy, BorderLayout.CENTER);
        hero.add(right, BorderLayout.EAST);
        return hero;
    }

    private JPanel createStatsGrid() {
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 14, 0));
        statsGrid.setOpaque(false);
        statsGrid.setMinimumSize(new Dimension(0, 96));
        statsGrid.setPreferredSize(new Dimension(0, 108));
        statsGrid.add(createStatCard("TÀI KHOẢN", lblAccounts, "Nick đang theo dõi", UIHelper.PRIMARY));
        statsGrid.add(createStatCard("SUBMISSIONS", lblSubmissions, "Source code đã lưu", UIHelper.SUCCESS));
        statsGrid.add(createStatCard("AI ANALYZED", lblAnalyses, "Bài đã phân tích", UIHelper.WARNING));
        statsGrid.add(createStatCard("CRAWL CUỐI", lblLastCrawl, "Mốc dữ liệu mới nhất", UIHelper.DANGER));
        return statsGrid;
    }

    private JPanel createChartsGrid() {
        JPanel chartsGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        chartsGrid.setOpaque(false);
        chartsGrid.setMinimumSize(new Dimension(0, 220));
        chartsGrid.setPreferredSize(new Dimension(0, 280));
        chartsGrid.add(dataStructureChart);
        chartsGrid.add(algorithmChart);
        return chartsGrid;
    }

    private JPanel createStatCard(String titleText, JLabel valueLabel, String description, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(UIHelper.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 3, 0, accentColor),
                        BorderFactory.createLineBorder(UIHelper.CARD_BORDER, 1)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel title = UIHelper.styledLabel(titleText, new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED);
        JLabel desc = UIHelper.styledLabel(description, new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_SUBTLE);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 27));
        valueLabel.setForeground(accentColor);
        valueLabel.setToolTipText(titleText);

        card.add(title, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(desc, BorderLayout.SOUTH);
        return card;
    }

    public void refreshData() {
        new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() {
                DashboardData data = new DashboardData();
                data.accounts = accountService.countAccounts();
                data.submissions = submissionDAO.count();
                data.analyses = analysisService.countAnalyses();
                data.unanalyzed = Math.max(0, data.submissions - data.analyses);
                data.latestJob = crawlService.getLatestJob();
                data.lastCrawlText = formatLastCrawl(data.latestJob);
                data.topDataStructures = countTopItems(analysisService.getAllDataStructures(), 5);
                data.topAlgorithms = countTopItems(analysisService.getAllAlgorithms(), 5);
                return data;
            }

            @Override
            protected void done() {
                try {
                    DashboardData data = get();
                    lblAccounts.setText(String.valueOf(data.accounts));
                    lblSubmissions.setText(String.valueOf(data.submissions));
                    lblAnalyses.setText(String.valueOf(data.analyses));
                    lblLastCrawl.setText(data.lastCrawlText);
                    lblLastCrawl.setToolTipText(data.lastCrawlText);
                    lblHeroSummary.setText(data.accounts + " nick Codeforces | "
                            + data.submissions + " submissions | "
                            + data.analyses + " bài đã phân tích AI");
                    lblSystemSignal.setText(buildSystemSignal(data));
                    lblSystemSignal.setForeground(data.unanalyzed > 0 ? UIHelper.WARNING : UIHelper.SUCCESS);
                    coverageRing.setValues(data.analyses, data.submissions);
                    insightPanel.setData(data);
                    dataStructureChart.setData(data.topDataStructures);
                    algorithmChart.setData(data.topAlgorithms);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private String buildSystemSignal(DashboardData data) {
        if (data.submissions == 0) {
            return "Chưa có submissions. Hãy crawl dữ liệu để bắt đầu phân tích.";
        }
        if (data.unanalyzed > 0) {
            return "Cần phân tích thêm " + data.unanalyzed + " submissions để hoàn thiện dữ liệu AI.";
        }
        return "Dữ liệu AI đã phủ toàn bộ submissions hiện có.";
    }

    private String formatLastCrawl(CrawlJob job) {
        if (job == null) {
            return "Chưa có";
        }

        LocalDateTime crawlTime = job.getFinishedAt() != null ? job.getFinishedAt() : job.getStartedAt();
        if (crawlTime == null) {
            return "Chưa có";
        }

        return crawlTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    private Map<String, Integer> countTopItems(List<String> items, int limit) {
        if (items == null || items.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return items.stream()
                .filter(item -> item != null && !item.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.groupingBy(item -> item, Collectors.summingInt(item -> 1)))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private static class DashboardData {
        int accounts;
        int submissions;
        int analyses;
        int unanalyzed;
        String lastCrawlText;
        CrawlJob latestJob;
        Map<String, Integer> topDataStructures = new LinkedHashMap<>();
        Map<String, Integer> topAlgorithms = new LinkedHashMap<>();
    }

    private static class CoverageRingPanel extends JPanel {
        private int analyzed;
        private int total;

        CoverageRingPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(180, 0));
        }

        void setValues(int analyzed, int total) {
            this.analyzed = Math.max(0, analyzed);
            this.total = Math.max(0, total);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int size = Math.min(getWidth() - 54, getHeight() - 54);
            size = Math.max(74, size);
            int x = (getWidth() - size) / 2;
            int y = 8;
            int percent = total == 0 ? 0 : (int) Math.round(analyzed * 100.0 / total);

            Stroke oldStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(46, 54, 67));
            g2.drawArc(x, y, size, size, 90, -360);
            g2.setColor(percent >= 80 ? UIHelper.SUCCESS : percent >= 45 ? UIHelper.WARNING : UIHelper.PRIMARY);
            g2.drawArc(x, y, size, size, 90, -(int) Math.round(360 * percent / 100.0));
            g2.setStroke(oldStroke);

            String value = percent + "%";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
            g2.setColor(UIHelper.TEXT_MAIN);
            FontMetrics valueMetrics = g2.getFontMetrics();
            g2.drawString(value, x + (size - valueMetrics.stringWidth(value)) / 2,
                    y + size / 2 + valueMetrics.getAscent() / 2 - 6);

            String title = "AI Coverage";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(new Color(134, 239, 172));
            FontMetrics titleMetrics = g2.getFontMetrics();
            g2.drawString(title, x + (size - titleMetrics.stringWidth(title)) / 2, y + size + 22);

            String detail = analyzed + "/" + total + " bài";
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(UIHelper.TEXT_MUTED);
            FontMetrics detailMetrics = g2.getFontMetrics();
            g2.drawString(detail, x + (size - detailMetrics.stringWidth(detail)) / 2, y + size + 38);

            g2.dispose();
        }
    }

    private static class InsightPanel extends JPanel {
        private final JLabel line1 = createLine("Chưa có dữ liệu");
        private final JLabel line2 = createLine("Chưa có dữ liệu");
        private final JLabel line3 = createLine("Chưa có dữ liệu");

        InsightPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JLabel title = UIHelper.styledLabel("Trạng thái hệ thống",
                    new Font("Segoe UI", Font.BOLD, 14), UIHelper.TEXT_MAIN);
            JLabel subtitle = UIHelper.styledLabel("Tín hiệu cần chú ý",
                    new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_MUTED);

            add(title);
            add(Box.createVerticalStrut(2));
            add(subtitle);
            add(Box.createVerticalStrut(10));
            add(line1);
            add(Box.createVerticalStrut(6));
            add(line2);
            add(Box.createVerticalStrut(6));
            add(line3);
        }

        void setData(DashboardData data) {
            line1.setText("Chưa phân tích AI: " + data.unanalyzed);
            line1.setForeground(data.unanalyzed > 0 ? UIHelper.WARNING : UIHelper.SUCCESS);

            CrawlJob job = data.latestJob;
            if (job == null) {
                line2.setText("Job crawl: chưa có");
                line3.setText("Crawl cuối: chưa có");
                line2.setForeground(UIHelper.TEXT_MUTED);
                line3.setForeground(UIHelper.TEXT_MUTED);
                return;
            }

            line2.setText("Job: " + safe(job.getStatus()) + " | mới " + job.getSubmissionsCrawled());
            line2.setForeground(colorForStatus(job.getStatus()));
            line3.setText("Quét " + job.getSubmissionsScanned()
                    + " | bỏ qua " + job.getSubmissionsSkipped());
            line3.setForeground(UIHelper.TEXT_MUTED);
        }

        private static JLabel createLine(String text) {
            JLabel label = UIHelper.styledLabel(text, new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED);
            label.setToolTipText(text);
            return label;
        }

        private static Color colorForStatus(String status) {
            if ("SUCCESS".equals(status)) {
                return UIHelper.SUCCESS;
            }
            if ("PARTIAL".equals(status)) {
                return UIHelper.WARNING;
            }
            if ("FAILED".equals(status)) {
                return UIHelper.DANGER;
            }
            return UIHelper.INFO;
        }

        private static String safe(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }
    }

    private static class RankingPanel extends JPanel {
        private final String title;
        private final String subtitle;
        private Map<String, Integer> data = new LinkedHashMap<>();

        RankingPanel(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
            setBackground(UIHelper.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                    BorderFactory.createEmptyBorder(10, 14, 10, 14)));
            setPreferredSize(new Dimension(420, 280));
        }

        void setData(Map<String, Integer> data) {
            this.data = data != null ? data : new LinkedHashMap<>();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawTitle(g2);
            if (data.isEmpty()) {
                drawEmptyState(g2);
                g2.dispose();
                return;
            }

            int max = data.values().stream().max(Integer::compareTo).orElse(1);
            int top = 58;
            int rowHeight = Math.max(28, (getHeight() - top - 12) / Math.max(1, data.size()));
            int index = 0;

            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                drawRow(g2, entry.getKey(), entry.getValue(), max, top + index * rowHeight, rowHeight, index);
                index++;
            }

            g2.dispose();
        }

        private void drawTitle(Graphics2D g2) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
            g2.setColor(UIHelper.TEXT_MAIN);
            g2.drawString(title, 18, 22);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(UIHelper.TEXT_MUTED);
            g2.drawString(subtitle, 18, 40);
        }

        private void drawEmptyState(Graphics2D g2) {
            String message = "Chưa có dữ liệu phân tích";
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(UIHelper.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(message, (getWidth() - fm.stringWidth(message)) / 2, getHeight() / 2);
        }

        private void drawRow(Graphics2D g2, String label, int value, int max, int y, int rowHeight, int index) {
            int left = 18;
            int right = getWidth() - 22;
            int labelWidth = Math.min(190, Math.max(120, getWidth() / 3));
            int barLeft = left + labelWidth + 14;
            int barRight = right - 42;
            int barWidth = Math.max(1, barRight - barLeft);
            int centerY = y + rowHeight / 2;
            int barHeight = 12;

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(index == 0 ? UIHelper.TEXT_MAIN : UIHelper.TEXT_MUTED);
            g2.drawString(fitText(g2, label, labelWidth), left, centerY + 4);

            g2.setColor(new Color(33, 38, 47));
            g2.fillRoundRect(barLeft, centerY - barHeight / 2, barWidth, barHeight, 10, 10);

            int fillWidth = Math.max(4, Math.round(barWidth * (value / (float) max)));
            Color fill = index == 0 ? UIHelper.SUCCESS : index == 1 ? UIHelper.PRIMARY : UIHelper.INFO;
            g2.setColor(fill);
            g2.fillRoundRect(barLeft, centerY - barHeight / 2, fillWidth, barHeight, 10, 10);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UIHelper.TEXT_MAIN);
            g2.drawString(String.valueOf(value), barRight + 12, centerY + 4);
        }

        private String fitText(Graphics2D g2, String text, int maxWidth) {
            if (text == null) {
                return "";
            }
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String suffix = "...";
            int end = text.length();
            while (end > 1 && fm.stringWidth(text.substring(0, end) + suffix) > maxWidth) {
                end--;
            }
            return text.substring(0, Math.max(1, end)) + suffix;
        }
    }
}
