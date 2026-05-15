package com.codeanalyzer.ui;

import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.AccountEvaluation;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.EvaluationService;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel đánh giá tổng hợp năng lực từng nick.
 */
public class EvaluationPanel extends JPanel {

    private final EvaluationService evaluationService;
    private final AccountService accountService;

    private final JComboBox<AccountOption> cbAccounts = new JComboBox<>();
    private final JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
    private final JButton btnEvalSelected = UIHelper.createButton("Đánh giá nick", UIHelper.SUCCESS);
    private final JButton btnEvalAll = UIHelper.createButton("Đánh giá tất cả", UIHelper.INFO);
    private final JLabel statusLabel = UIHelper.styledLabel(" ", new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_MUTED);

    private final ScoreChartPanel scoreChart = new ScoreChartPanel();
    private final JTextArea summaryArea = createReadOnlyArea(13, UIHelper.TEXT_MAIN);
    private final JTextArea strengthsArea = createReadOnlyArea(13, UIHelper.TEXT_MAIN);
    private final JTextArea weaknessesArea = createReadOnlyArea(13, UIHelper.TEXT_MAIN);
    private final JTextArea recommendationArea = createReadOnlyArea(13, UIHelper.TEXT_MAIN);

    private Map<Integer, Account> accountById = new HashMap<>();
    private Map<Integer, AccountEvaluation> evaluationByAccountId = new HashMap<>();
    private boolean updatingAccounts = false;

    public EvaluationPanel(EvaluationService evaluationService, AccountService accountService) {
        this.evaluationService = evaluationService;
        this.accountService = accountService;
        this.evaluationService.addLogListener(msg ->
                SwingUtilities.invokeLater(() -> statusLabel.setText(msg)));

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        btnRefresh.addActionListener(e -> refreshData());
        btnEvalSelected.addActionListener(e -> evaluateSelected());
        btnEvalAll.addActionListener(e -> evaluateAll());
        add(UIHelper.createHeader("Đánh giá tài khoản", btnRefresh, btnEvalSelected, btnEvalAll), BorderLayout.NORTH);

        cbAccounts.setPreferredSize(new Dimension(360, 36));
        cbAccounts.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbAccounts.addActionListener(e -> {
            if (!updatingAccounts) {
                displaySelectedEvaluation();
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(createControlPanel(), BorderLayout.NORTH);
        center.add(createDashboardPanel(), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBackground(UIHelper.CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        panel.add(UIHelper.styledLabel("Chọn nick:", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MAIN));
        panel.add(cbAccounts);
        panel.add(statusLabel);
        return panel;
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel topGrid = new JPanel(new GridLayout(1, 2, 12, 0));
        topGrid.setOpaque(false);
        topGrid.add(scoreChart);
        topGrid.add(createSummaryCard());

        JPanel detailGrid = new JPanel(new GridLayout(1, 3, 12, 0));
        detailGrid.setOpaque(false);
        detailGrid.add(createTextCard("Điểm mạnh", strengthsArea, UIHelper.SUCCESS));
        detailGrid.add(createTextCard("Điểm yếu", weaknessesArea, UIHelper.WARNING));
        detailGrid.add(createTextCard("Khuyến nghị", recommendationArea, UIHelper.INFO));

        panel.add(topGrid, BorderLayout.NORTH);
        panel.add(detailGrid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSummaryCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 10));

        JLabel title = UIHelper.styledLabel("Tổng quan năng lực", new Font("Segoe UI", Font.BOLD, 15), Color.WHITE);
        card.add(title, BorderLayout.NORTH);

        summaryArea.setForeground(new Color(134, 239, 172));
        summaryArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane scrollPane = wrapArea(summaryArea);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTextCard(String title, JTextArea area, Color accentColor) {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 8));

        JLabel titleLabel = UIHelper.styledLabel(title, new Font("Segoe UI", Font.BOLD, 14), accentColor);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(wrapArea(area), BorderLayout.CENTER);
        return card;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(UIHelper.CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return panel;
    }

    private JScrollPane wrapArea(JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIHelper.CARD_BG);
        return scrollPane;
    }

    private static JTextArea createReadOnlyArea(int fontSize, Color color) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        area.setForeground(color);
        area.setBackground(UIHelper.CARD_BG);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setCaretPosition(0);
        return area;
    }

    private void evaluateSelected() {
        if (evaluationService.isRunning()) {
            statusLabel.setText("Đang có tiến trình đánh giá khác chạy.");
            return;
        }
        AccountOption option = getSelectedOption();
        if (option == null) {
            JOptionPane.showMessageDialog(this, "Chưa có nick để đánh giá.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setActionButtonsEnabled(false);
        statusLabel.setText("Đang đánh giá " + option.account.getUsername() + "...");

        new SwingWorker<AccountEvaluation, Void>() {
            @Override
            protected AccountEvaluation doInBackground() {
                return evaluationService.evaluate(option.account.getId());
            }

            @Override
            protected void done() {
                setActionButtonsEnabled(true);
                try {
                    AccountEvaluation evaluation = get();
                    if (evaluationService.wasStoppedByAiLimit()) {
                        showAiLimitMessage();
                    } else if (evaluation == null) {
                        JOptionPane.showMessageDialog(EvaluationPanel.this,
                                "Nick này chưa có dữ liệu phân tích AI để đánh giá.",
                                "Thông báo", JOptionPane.WARNING_MESSAGE);
                    }
                    refreshData(option.account.getId());
                } catch (Exception e) {
                    statusLabel.setText("Lỗi đánh giá: " + e.getMessage());
                    if (evaluationService.wasStoppedByAiLimit()) {
                        showAiLimitMessage();
                    }
                }
            }
        }.execute();
    }

    private void evaluateAll() {
        if (evaluationService.isRunning()) {
            statusLabel.setText("Đang có tiến trình đánh giá khác chạy.");
            return;
        }
        setActionButtonsEnabled(false);
        Integer selectedId = getSelectedAccountId();
        statusLabel.setText("Đang đánh giá tất cả tài khoản...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                evaluationService.evaluateAll();
                return null;
            }

            @Override
            protected void done() {
                setActionButtonsEnabled(true);
                refreshData(selectedId);
                if (evaluationService.wasStoppedByAiLimit()) {
                    showAiLimitMessage();
                } else {
                    JOptionPane.showMessageDialog(EvaluationPanel.this,
                            "Đánh giá tất cả hoàn tất.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    public void refreshData() {
        refreshData(getSelectedAccountId());
    }

    private void refreshData(Integer preferredAccountId) {
        new SwingWorker<EvaluationData, Void>() {
            @Override
            protected EvaluationData doInBackground() {
                EvaluationData data = new EvaluationData();
                data.accounts = accountService.getAllAccounts();
                data.evaluations = evaluationService.getAllEvaluations();
                return data;
            }

            @Override
            protected void done() {
                try {
                    EvaluationData data = get();
                    accountById = new HashMap<>();
                    evaluationByAccountId = new HashMap<>();

                    for (Account account : data.accounts) {
                        accountById.put(account.getId(), account);
                    }
                    for (AccountEvaluation evaluation : data.evaluations) {
                        evaluationByAccountId.put(evaluation.getAccountId(), evaluation);
                    }

                    updateAccountCombo(data.accounts, preferredAccountId);
                    displaySelectedEvaluation();
                } catch (Exception e) {
                    statusLabel.setText("Lỗi tải dữ liệu đánh giá: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updateAccountCombo(List<Account> accounts, Integer preferredAccountId) {
        updatingAccounts = true;
        DefaultComboBoxModel<AccountOption> model = new DefaultComboBoxModel<>();
        AccountOption preferredOption = null;

        for (Account account : accounts) {
            AccountOption option = new AccountOption(account);
            model.addElement(option);
            if (preferredAccountId != null && account.getId() == preferredAccountId) {
                preferredOption = option;
            }
        }

        cbAccounts.setModel(model);
        if (preferredOption != null) {
            cbAccounts.setSelectedItem(preferredOption);
        } else if (model.getSize() > 0) {
            cbAccounts.setSelectedIndex(0);
        }
        updatingAccounts = false;
    }

    private void displaySelectedEvaluation() {
        AccountOption option = getSelectedOption();
        if (option == null) {
            displayEmptyState("Chưa có tài khoản.", "Hãy thêm nick ở trang Quản lý tài khoản trước.");
            return;
        }

        AccountEvaluation evaluation = evaluationByAccountId.get(option.account.getId());
        if (evaluation == null) {
            displayEmptyState(
                    "Chưa có đánh giá cho " + option.account.getUsername() + ".",
                    "Bấm Đánh giá nick sau khi nick đã có dữ liệu phân tích AI.");
            return;
        }

        double dsScore = scoreValue(evaluation.getDsScore());
        double algoScore = scoreValue(evaluation.getAlgoScore());
        double aiUsage = scoreValue(evaluation.getAvgAiUsageScore());
        double aiCleanScore = Math.max(0, 100 - aiUsage);
        scoreChart.setScores(dsScore, algoScore, aiCleanScore);

        summaryArea.setText(buildSummary(option.account, evaluation, aiUsage));
        summaryArea.setCaretPosition(0);
        strengthsArea.setText(defaultText(evaluation.getStrengths()));
        weaknessesArea.setText(defaultText(evaluation.getWeaknesses()));
        recommendationArea.setText(defaultText(evaluation.getRecommendation()));
        resetTextCarets();
        statusLabel.setText("Đang xem đánh giá mới nhất của " + option.account.getUsername() + ".");
    }

    private void displayEmptyState(String title, String message) {
        scoreChart.setScores(0, 0, 0);
        summaryArea.setText(title + "\n\n" + message);
        strengthsArea.setText("Chưa có dữ liệu.");
        weaknessesArea.setText("Chưa có dữ liệu.");
        recommendationArea.setText("Chưa có dữ liệu.");
        resetTextCarets();
        statusLabel.setText(title);
    }

    private String buildSummary(Account account, AccountEvaluation evaluation, double aiUsage) {
        return "Nick: " + displayName(account) + "\n"
                + "Mức độ tổng hợp: " + textOrDash(evaluation.getOverallLevel()) + "\n"
                + "Tổng bài phân tích: " + evaluation.getTotalSubmissionsAnalyzed() + "\n"
                + "AI usage: " + textOrDash(evaluation.getAiUsageLevel()) + " (" + formatScore(aiUsage) + "%)\n"
                + "Đánh giá lúc: " + UIHelper.formatDateTime(evaluation.getEvaluatedAt()) + "\n\n"
                + "Lưu ý: Overall Level chỉ dựa trên các bài đã phân tích trong hệ thống, "
                + "không phải kết luận tuyệt đối về toàn bộ năng lực. AI usage là mức nghi ngờ có hỗ trợ AI, "
                + "cần xem như tín hiệu tham khảo.\n\n"
                + "CTDL thành thạo: " + listText(evaluation.getDsMastered()) + "\n"
                + "CTDL đang học: " + listText(evaluation.getDsLearning()) + "\n\n"
                + "Thuật toán thành thạo: " + listText(evaluation.getAlgoMastered()) + "\n"
                + "Thuật toán đang học: " + listText(evaluation.getAlgoLearning());
    }

    private void resetTextCarets() {
        summaryArea.setCaretPosition(0);
        strengthsArea.setCaretPosition(0);
        weaknessesArea.setCaretPosition(0);
        recommendationArea.setCaretPosition(0);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        btnRefresh.setEnabled(enabled);
        btnEvalSelected.setEnabled(enabled);
        btnEvalAll.setEnabled(enabled);
        cbAccounts.setEnabled(enabled);
    }

    private void showAiLimitMessage() {
        JOptionPane.showMessageDialog(this,
                "AI đạt giới hạn rồi", "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    private AccountOption getSelectedOption() {
        Object selected = cbAccounts.getSelectedItem();
        return selected instanceof AccountOption ? (AccountOption) selected : null;
    }

    private Integer getSelectedAccountId() {
        AccountOption option = getSelectedOption();
        return option != null ? option.account.getId() : null;
    }

    private double scoreValue(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }

    private String formatScore(double score) {
        if (Math.rint(score) == score) {
            return String.valueOf((int) score);
        }
        return String.format(java.util.Locale.US, "%.2f", score);
    }

    private String listText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "Chưa có dữ liệu";
        }
        return String.join(", ", values);
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "Chưa có dữ liệu." : value.trim();
    }

    private String textOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String displayName(Account account) {
        if (account == null) {
            return "-";
        }
        if (account.getDisplayName() != null && !account.getDisplayName().isBlank()) {
            return account.getDisplayName() + " (" + account.getUsername() + ")";
        }
        return account.getUsername();
    }

    private static class EvaluationData {
        List<Account> accounts;
        List<AccountEvaluation> evaluations;
    }

    private static class AccountOption {
        private final Account account;

        AccountOption(Account account) {
            this.account = account;
        }

        @Override
        public String toString() {
            String name = account.getDisplayName() != null && !account.getDisplayName().isBlank()
                    ? account.getDisplayName() + " / " + account.getUsername()
                    : account.getUsername();
            return account.getId() + " - " + name + " (" + account.getPlatform() + ")";
        }
    }

    private static class ScoreChartPanel extends JPanel {
        private final String[] labels = {"CTDL", "Thuật toán", "AI Clean"};
        private final double[] scores = {0, 0, 0};
        private final Color[] colors = {
                UIHelper.PRIMARY,
                UIHelper.INFO,
                UIHelper.SUCCESS
        };

        ScoreChartPanel() {
            setBackground(UIHelper.CARD_BG);
            setPreferredSize(new Dimension(440, 270));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                    BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        }

        void setScores(double dsScore, double algoScore, double aiCleanScore) {
            scores[0] = clamp(dsScore);
            scores[1] = clamp(algoScore);
            scores[2] = clamp(aiCleanScore);
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
            int left = 72;
            int right = width - 26;
            int top = 58;
            int bottom = height - 36;
            int chartWidth = Math.max(1, right - left);
            int rowHeight = Math.max(34, (bottom - top) / labels.length);

            drawTitle(g2, width);
            drawGrid(g2, left, top, right, bottom, chartWidth);
            drawBars(g2, left, top, chartWidth, rowHeight);

            g2.dispose();
        }

        private void drawTitle(Graphics2D g2, int width) {
            String title = "Điểm tổng hợp";
            String subtitle = "Điểm";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(UIHelper.TEXT_MAIN);
            FontMetrics titleMetrics = g2.getFontMetrics();
            g2.drawString(title, (width - titleMetrics.stringWidth(title)) / 2, 22);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(UIHelper.TEXT_MUTED);
            FontMetrics subtitleMetrics = g2.getFontMetrics();
            g2.drawString(subtitle, (width - subtitleMetrics.stringWidth(subtitle)) / 2, 42);
        }

        private void drawGrid(Graphics2D g2, int left, int top, int right, int bottom, int chartWidth) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            for (int value = 0; value <= 100; value += 10) {
                int x = left + Math.round(chartWidth * value / 100f);
                g2.setColor(new Color(48, 55, 64));
                g2.drawLine(x, top - 8, x, bottom);
                g2.setColor(UIHelper.TEXT_MUTED);
                String label = String.valueOf(value);
                g2.drawString(label, x - fm.stringWidth(label) / 2, top - 16);
            }
        }

        private void drawBars(Graphics2D g2, int left, int top, int chartWidth, int rowHeight) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int barHeight = 24;

            for (int i = 0; i < labels.length; i++) {
                int centerY = top + i * rowHeight + rowHeight / 2;
                int barY = centerY - barHeight / 2;
                int barWidth = Math.round(chartWidth * (float) scores[i] / 100f);

                g2.setColor(UIHelper.TEXT_MUTED);
                g2.drawString(labels[i], left - fm.stringWidth(labels[i]) - 12, centerY + fm.getAscent() / 2 - 2);

                g2.setColor(UIHelper.FIELD_BG);
                g2.fillRoundRect(left, barY, chartWidth, barHeight, 8, 8);
                g2.setColor(colors[i]);
                g2.fillRoundRect(left, barY, Math.max(2, barWidth), barHeight, 8, 8);

                String value = String.valueOf((int) Math.round(scores[i]));
                g2.setColor(UIHelper.TEXT_MAIN);
                int valueX = Math.min(left + Math.max(8, barWidth) + 8, left + chartWidth - fm.stringWidth(value));
                g2.drawString(value, valueX, centerY + fm.getAscent() / 2 - 2);
            }
        }

        private double clamp(double value) {
            return Math.max(0, Math.min(100, value));
        }
    }
}
