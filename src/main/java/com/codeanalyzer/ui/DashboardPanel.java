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

    private final JLabel lblAccounts = new JLabel("0");
    private final JLabel lblSubmissions = new JLabel("0");
    private final JLabel lblAnalyses = new JLabel("0");
    private final JLabel lblLastCrawl = new JLabel("Chưa có");
    private final BarChartPanel dataStructureChart = new BarChartPanel("Top CTDL phổ biến", "CTDL");
    private final BarChartPanel algorithmChart = new BarChartPanel("Top Thuật toán phổ biến", "Thuật toán");

    public DashboardPanel(AccountService accountService, SubmissionDAO submissionDAO,
            AnalysisService analysisService, CrawlService crawlService) {
        this.accountService = accountService;
        this.submissionDAO = submissionDAO;
        this.analysisService = analysisService;
        this.crawlService = crawlService;
        this.crawlService.addJobListener(job -> SwingUtilities.invokeLater(this::refreshData));

        setLayout(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Tổng quan hệ thống");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        header.add(title, BorderLayout.WEST);
        header.add(btnRefresh, BorderLayout.EAST);

        // Stats cards
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 16, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(createStatCard("TỔNG SỐ NICK", lblAccounts, UIHelper.PRIMARY));
        statsGrid.add(createStatCard("SỐ SUBMISSIONS", lblSubmissions, UIHelper.SUCCESS));
        statsGrid.add(createStatCard("ĐÃ PHÂN TÍCH AI", lblAnalyses, UIHelper.WARNING));
        statsGrid.add(createStatCard("CRAWL CUỐI", lblLastCrawl, UIHelper.DANGER));

        JPanel chartsGrid = new JPanel(new GridLayout(1, 2, 20, 0));
        chartsGrid.setOpaque(false);
        chartsGrid.add(dataStructureChart);
        chartsGrid.add(algorithmChart);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setOpaque(false);
        content.add(statsGrid, BorderLayout.NORTH);
        content.add(chartsGrid, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private JPanel createStatCard(String titleText, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UIHelper.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(3, 0, 0, 0, accentColor),
                        BorderFactory.createLineBorder(UIHelper.CARD_BORDER, 1)),
                BorderFactory.createEmptyBorder(22, 22, 22, 22)
        ));

        JLabel lbl = new JLabel(titleText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(UIHelper.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        valueLabel.setForeground(accentColor);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lbl);
        card.add(Box.createVerticalStrut(12));
        card.add(valueLabel);

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
                data.lastCrawlText = formatLastCrawl(crawlService.getLatestJob());
                data.topDataStructures = countTopItems(analysisService.getAllDataStructures(), 10);
                data.topAlgorithms = countTopItems(analysisService.getAllAlgorithms(), 10);
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
                    dataStructureChart.setData(data.topDataStructures);
                    algorithmChart.setData(data.topAlgorithms);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
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
        String lastCrawlText;
        Map<String, Integer> topDataStructures = new LinkedHashMap<>();
        Map<String, Integer> topAlgorithms = new LinkedHashMap<>();
    }

    private static class BarChartPanel extends JPanel {
        private static final int TOP_PADDING = 44;
        private static final int LEFT_PADDING = 48;
        private static final int RIGHT_PADDING = 18;
        private static final int BOTTOM_PADDING = 58;
        private static final Color BAR_COLOR = UIHelper.INFO;
        private static final Color GRID_COLOR = new Color(48, 55, 64);

        private final String title;
        private final String xAxisLabel;
        private Map<String, Integer> data = new LinkedHashMap<>();

        BarChartPanel(String title, String xAxisLabel) {
            this.title = title;
            this.xAxisLabel = xAxisLabel;
            setBackground(UIHelper.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            setPreferredSize(new Dimension(420, 360));
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

            int width = getWidth();
            int height = getHeight();
            int chartLeft = LEFT_PADDING;
            int chartTop = TOP_PADDING;
            int chartRight = width - RIGHT_PADDING;
            int chartBottom = height - BOTTOM_PADDING;
            int chartWidth = Math.max(1, chartRight - chartLeft);
            int chartHeight = Math.max(1, chartBottom - chartTop);

            drawTitle(g2, width);

            if (data.isEmpty()) {
                drawEmptyState(g2, width, height);
                g2.dispose();
                return;
            }

            int max = data.values().stream().max(Integer::compareTo).orElse(1);
            drawGrid(g2, chartLeft, chartTop, chartRight, chartBottom, chartHeight, max);
            drawBars(g2, chartLeft, chartBottom, chartWidth, chartHeight, max);
            drawAxisLabels(g2, width, height);

            g2.dispose();
        }

        private void drawTitle(Graphics2D g2, int width) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(UIHelper.TEXT_MAIN);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, (width - fm.stringWidth(title)) / 2, 24);
        }

        private void drawEmptyState(Graphics2D g2, int width, int height) {
            String message = "Chưa có dữ liệu phân tích";
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(UIHelper.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(message, (width - fm.stringWidth(message)) / 2, height / 2);
        }

        private void drawGrid(Graphics2D g2, int left, int top, int right, int bottom, int chartHeight, int max) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i <= 5; i++) {
                int value = Math.round(max * i / 5f);
                int y = bottom - Math.round(chartHeight * i / 5f);

                g2.setColor(GRID_COLOR);
                g2.drawLine(left, y, right, y);

                g2.setColor(UIHelper.TEXT_MUTED);
                String label = String.valueOf(value);
                g2.drawString(label, left - fm.stringWidth(label) - 8, y + fm.getAscent() / 2 - 2);
            }

            g2.setColor(UIHelper.CARD_BORDER);
            g2.drawLine(left, top, left, bottom);
            g2.drawLine(left, bottom, right, bottom);
        }

        private void drawBars(Graphics2D g2, int left, int bottom, int chartWidth, int chartHeight, int max) {
            int count = data.size();
            int slotWidth = Math.max(1, chartWidth / count);
            int barWidth = Math.max(12, Math.min(44, (int) (slotWidth * 0.62)));
            int index = 0;

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            FontMetrics labelMetrics = g2.getFontMetrics();

            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                int barHeight = Math.max(2, Math.round(chartHeight * (entry.getValue() / (float) max)));
                int x = left + index * slotWidth + (slotWidth - barWidth) / 2;
                int y = bottom - barHeight;

                g2.setColor(BAR_COLOR);
                g2.fillRoundRect(x, y, barWidth, barHeight, 4, 4);

                g2.setColor(UIHelper.TEXT_MUTED);
                String label = abbreviate(entry.getKey(), Math.max(4, slotWidth / 8));
                int labelX = x + (barWidth - labelMetrics.stringWidth(label)) / 2;
                g2.drawString(label, labelX, bottom + 18);

                index++;
            }
        }

        private void drawAxisLabels(Graphics2D g2, int width, int height) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();

            g2.setColor(UIHelper.TEXT_MAIN);
            g2.drawString(xAxisLabel, (width - fm.stringWidth(xAxisLabel)) / 2, height - 16);

            String yAxisLabel = "Số lần";
            Graphics2D rotated = (Graphics2D) g2.create();
            rotated.rotate(-Math.PI / 2);
            rotated.drawString(yAxisLabel, -(height + fm.stringWidth(yAxisLabel)) / 2, 16);
            rotated.dispose();
        }

        private String abbreviate(String text, int maxLength) {
            if (text == null) {
                return "";
            }
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, Math.max(1, maxLength - 1)) + "...";
        }
    }
}
