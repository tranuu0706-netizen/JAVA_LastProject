package com.codeanalyzer.ui;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.config.DatabaseConfig;
import com.codeanalyzer.crawler.CrawlScheduler;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panel cài đặt: chỉnh cấu hình DB, Gemini, Edge, crawl và phân tích ngay trong ứng dụng.
 */
public class SettingsPanel extends JPanel {

    private final CrawlScheduler crawlScheduler;

    private final JTextField tfDbUrl = new JTextField();
    private final JTextField tfDbUsername = new JTextField();
    private final JPasswordField pfDbPassword = new JPasswordField();

    private final JPasswordField pfGeminiApiKey = new JPasswordField();
    private final JTextField tfGeminiModel = new JTextField();
    private final JSpinner spinGeminiDelayMs = new JSpinner(new SpinnerNumberModel(10000, 0, 300000, 500));
    private final JSpinner spinGeminiMaxTokens = new JSpinner(new SpinnerNumberModel(4096, 1024, 32768, 256));

    private final JTextField tfEdgeProfilePath = new JTextField();
    private final JTextField tfEdgeDriverPath = new JTextField();

    private final JSpinner spinCrawlIntervalHours = new JSpinner(new SpinnerNumberModel(24, 1, 168, 1));
    private final JTextField tfCrawlStartTime = new JTextField();
    private final JSpinner spinMaxSubmissions = new JSpinner(new SpinnerNumberModel(500, 1, 5000, 10));

    private final JSpinner spinAnalysisBatchSize = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner spinAnalysisMaxCodeLength = new JSpinner(new SpinnerNumberModel(4000, 500, 20000, 100));

    private final JLabel statusLabel = UIHelper.styledLabel(" ", new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_MUTED);
    private final JButton btnSave = UIHelper.createButton("Lưu cấu hình", UIHelper.SUCCESS);
    private final JButton btnReload = UIHelper.createButton("Reload cấu hình", UIHelper.CARD_BORDER);
    private final JButton btnTestConnection = UIHelper.createSmallButton("Test Connection", UIHelper.PRIMARY);

    public SettingsPanel(CrawlScheduler crawlScheduler) {
        this.crawlScheduler = crawlScheduler;

        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 26, 30));
        setBackground(UIHelper.BG_DARK);

        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        btnSave.addActionListener(e -> saveSettings());
        btnReload.addActionListener(e -> reloadSettings());
        add(UIHelper.createHeader("Cài đặt", btnRefresh), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(1, 2, 14, 0));
        content.setOpaque(false);

        JPanel leftColumn = createColumn();
        leftColumn.add(createDatabaseSection());
        leftColumn.add(Box.createVerticalStrut(12));
        leftColumn.add(createGeminiSection());
        leftColumn.add(Box.createVerticalGlue());

        JPanel rightColumn = createColumn();
        rightColumn.add(createEdgeSection());
        rightColumn.add(Box.createVerticalStrut(12));
        rightColumn.add(createCrawlSection());
        rightColumn.add(Box.createVerticalStrut(12));
        rightColumn.add(createAnalysisSection());
        rightColumn.add(Box.createVerticalGlue());

        content.add(leftColumn);
        content.add(rightColumn);

        JScrollPane scrollPane = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UIHelper.BG_DARK);
        add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(UIHelper.CARD_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(btnReload);
        actions.add(btnSave);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel createColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        return column;
    }

    private JPanel createDatabaseSection() {
        JPanel section = createSection("Cấu hình Database");
        GridBagConstraints gbc = baseConstraints();
        int row = 0;
        addRow(section, gbc, row++, "JDBC URL", tfDbUrl, null);
        addRow(section, gbc, row++, "Username", tfDbUsername, null);
        addRow(section, gbc, row++, "Password", pfDbPassword, null);

        btnTestConnection.addActionListener(e -> testConnection());
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        section.add(btnTestConnection, gbc);
        return section;
    }

    private JPanel createGeminiSection() {
        JPanel section = createSection("Gemini API");
        GridBagConstraints gbc = baseConstraints();
        int row = 0;
        addRow(section, gbc, row++, "API Key", pfGeminiApiKey, null);
        addRow(section, gbc, row++, "Model", tfGeminiModel, null);
        addRow(section, gbc, row++, "Delay/request", spinGeminiDelayMs, null);
        addRow(section, gbc, row, "Max tokens", spinGeminiMaxTokens, null);
        return section;
    }

    private JPanel createEdgeSection() {
        JPanel section = createSection("Microsoft Edge");
        GridBagConstraints gbc = baseConstraints();
        int row = 0;
        addRow(section, gbc, row++, "Profile path", tfEdgeProfilePath,
                createBrowseButton(tfEdgeProfilePath, true));
        addRow(section, gbc, row, "Driver path", tfEdgeDriverPath,
                createBrowseButton(tfEdgeDriverPath, false));
        return section;
    }

    private JPanel createCrawlSection() {
        JPanel section = createSection("Cấu hình Crawl");
        GridBagConstraints gbc = baseConstraints();
        int row = 0;
        addRow(section, gbc, row++, "Khoảng cách", spinCrawlIntervalHours, null);
        addRow(section, gbc, row++, "Giờ bắt đầu", tfCrawlStartTime, null);
        addRow(section, gbc, row, "Max/crawl", spinMaxSubmissions, null);
        return section;
    }

    private JPanel createAnalysisSection() {
        JPanel section = createSection("Cấu hình Phân tích AI");
        GridBagConstraints gbc = baseConstraints();
        int row = 0;
        addRow(section, gbc, row++, "Batch size", spinAnalysisBatchSize, null);
        addRow(section, gbc, row, "Max code length", spinAnalysisMaxCodeLength, null);
        return section;
    }

    private JPanel createSection(String title) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UIHelper.CARD_BG);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                        title,
                        0,
                        0,
                        new Font("Segoe UI", Font.BOLD, 13),
                        UIHelper.TEXT_MAIN),
                BorderFactory.createEmptyBorder(8, 12, 12, 12)));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        return form;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String labelText,
            JComponent field, JButton trailingButton) {
        JLabel label = UIHelper.styledLabel(labelText, new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED);
        label.setPreferredSize(new Dimension(104, 28));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(label, gbc);

        field.setPreferredSize(new Dimension(260, 32));
        field.setMinimumSize(new Dimension(120, 32));
        styleInput(field);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        if (trailingButton != null) {
            panel.add(trailingButton, gbc);
        } else {
            panel.add(Box.createHorizontalStrut(1), gbc);
        }
    }

    private void styleInput(JComponent field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setToolTipText(field instanceof JTextField ? ((JTextField) field).getText() : null);
    }

    private JButton createBrowseButton(JTextField target, boolean directoriesOnly) {
        JButton button = UIHelper.createSmallButton("Chọn...", UIHelper.CARD_BORDER);
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(directoriesOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            String currentValue = target.getText().trim();
            if (!currentValue.isEmpty()) {
                chooser.setSelectedFile(Path.of(currentValue).toFile());
            }
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    public void refreshData() {
        tfDbUrl.setText(AppConfig.get("db_url", DatabaseConfig.getDbUrl()));
        tfDbUsername.setText(AppConfig.get("db_username", DatabaseConfig.getDbUsername()));
        pfDbPassword.setText(AppConfig.get("db_password", DatabaseConfig.getDbPassword()));

        pfGeminiApiKey.setText(AppConfig.get("gemini_api_key", ""));
        tfGeminiModel.setText(AppConfig.getGeminiModel());
        spinGeminiDelayMs.setValue(AppConfig.getGeminiRequestDelayMs());
        spinGeminiMaxTokens.setValue(AppConfig.getGeminiMaxOutputTokens());

        tfEdgeProfilePath.setText(AppConfig.getEdgeProfilePath());
        tfEdgeDriverPath.setText(AppConfig.getEdgeDriverPath());

        spinCrawlIntervalHours.setValue(AppConfig.getCrawlIntervalHours());
        tfCrawlStartTime.setText(AppConfig.getCrawlStartTime());
        spinMaxSubmissions.setValue(AppConfig.getMaxSubmissionsPerCrawl());

        spinAnalysisBatchSize.setValue(AppConfig.getAnalysisBatchSize());
        spinAnalysisMaxCodeLength.setValue(AppConfig.getAnalysisMaxCodeLength());
        statusLabel.setText("Đã tải cấu hình hiện tại.");
    }

    private void testConnection() {
        SettingsValues values = collectValues(false);
        if (values == null) {
            return;
        }

        setButtonsEnabled(false);
        statusLabel.setText("Đang test kết nối database...");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return DatabaseConfig.testConnection(values.dbUrl, values.dbUsername, values.dbPassword);
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                try {
                    boolean ok = get();
                    statusLabel.setText(ok ? "Kết nối database thành công." : "Kết nối database thất bại.");
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            ok ? "Kết nối database thành công." : "Kết nối database thất bại.",
                            ok ? "Thành công" : "Lỗi",
                            ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    statusLabel.setText("Lỗi test database: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void saveSettings() {
        SettingsValues values = collectValues(true);
        if (values == null) {
            return;
        }

        setButtonsEnabled(false);
        statusLabel.setText("Đang lưu cấu hình...");
        new SwingWorker<Boolean, Void>() {
            private String errorMessage = "";

            @Override
            protected Boolean doInBackground() {
                try {
                    if (!DatabaseConfig.testConnection(values.dbUrl, values.dbUsername, values.dbPassword)) {
                        errorMessage = "Không thể kết nối database với cấu hình mới.";
                        return false;
                    }

                    AppConfig.saveToLocalProperties(values.toDotKeyMap());
                    DatabaseConfig.initialize(values.dbUrl, values.dbUsername, values.dbPassword);
                    AppConfig.reload();
                    saveRuntimeAndDbConfig(values);

                    if (crawlScheduler != null && crawlScheduler.isRunning()) {
                        crawlScheduler.restart();
                    }
                    return true;
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                try {
                    boolean ok = get();
                    if (ok) {
                        statusLabel.setText("Đã lưu cấu hình. Một số thay đổi sẽ áp dụng cho tác vụ kế tiếp.");
                        JOptionPane.showMessageDialog(SettingsPanel.this,
                                "Đã lưu cấu hình thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        refreshData();
                    } else {
                        statusLabel.setText(errorMessage);
                        JOptionPane.showMessageDialog(SettingsPanel.this,
                                errorMessage, "Lỗi lưu cấu hình", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Lỗi lưu cấu hình: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void reloadSettings() {
        setButtonsEnabled(false);
        statusLabel.setText("Đang reload cấu hình...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                AppConfig.reload();
                return null;
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                refreshData();
                statusLabel.setText("Đã reload cấu hình từ file local và DB.");
            }
        }.execute();
    }

    private void saveRuntimeAndDbConfig(SettingsValues values) {
        Map<String, String> cacheValues = values.toCacheKeyMap();
        for (Map.Entry<String, String> entry : cacheValues.entrySet()) {
            AppConfig.setRuntime(entry.getKey(), entry.getValue());
            AppConfig.set(entry.getKey(), entry.getValue());
        }
    }

    private SettingsValues collectValues(boolean validateAll) {
        SettingsValues values = new SettingsValues();
        values.dbUrl = tfDbUrl.getText().trim();
        values.dbUsername = tfDbUsername.getText().trim();
        values.dbPassword = new String(pfDbPassword.getPassword());
        values.geminiApiKey = new String(pfGeminiApiKey.getPassword()).trim();
        values.geminiModel = tfGeminiModel.getText().trim();
        values.geminiDelayMs = (int) spinGeminiDelayMs.getValue();
        values.geminiMaxOutputTokens = (int) spinGeminiMaxTokens.getValue();
        values.edgeProfilePath = tfEdgeProfilePath.getText().trim();
        values.edgeDriverPath = tfEdgeDriverPath.getText().trim();
        values.crawlIntervalHours = (int) spinCrawlIntervalHours.getValue();
        values.crawlStartTime = tfCrawlStartTime.getText().trim();
        values.maxSubmissions = (int) spinMaxSubmissions.getValue();
        values.analysisBatchSize = (int) spinAnalysisBatchSize.getValue();
        values.analysisMaxCodeLength = (int) spinAnalysisMaxCodeLength.getValue();

        if (values.dbUrl.isEmpty()) {
            showValidationError("JDBC URL không được để trống.");
            return null;
        }
        if (values.dbUsername.isEmpty()) {
            showValidationError("Username database không được để trống.");
            return null;
        }
        if (!validateAll) {
            return values;
        }
        if (values.geminiModel.isEmpty()) {
            showValidationError("Gemini model không được để trống.");
            return null;
        }
        try {
            LocalTime.parse(values.crawlStartTime);
        } catch (Exception e) {
            showValidationError("Giờ bắt đầu crawl phải có dạng HH:mm, ví dụ 02:00.");
            return null;
        }
        return values;
    }

    private void showValidationError(String message) {
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(this, message, "Sai cấu hình", JOptionPane.WARNING_MESSAGE);
    }

    private void setButtonsEnabled(boolean enabled) {
        btnSave.setEnabled(enabled);
        btnReload.setEnabled(enabled);
        btnTestConnection.setEnabled(enabled);
    }

    private static class SettingsValues {
        String dbUrl;
        String dbUsername;
        String dbPassword;
        String geminiApiKey;
        String geminiModel;
        int geminiDelayMs;
        int geminiMaxOutputTokens;
        String edgeProfilePath;
        String edgeDriverPath;
        int crawlIntervalHours;
        String crawlStartTime;
        int maxSubmissions;
        int analysisBatchSize;
        int analysisMaxCodeLength;

        Map<String, String> toDotKeyMap() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("db.url", dbUrl);
            values.put("db.username", dbUsername);
            values.put("db.password", dbPassword);
            values.put("gemini.api.key", geminiApiKey);
            values.put("gemini.model", geminiModel);
            values.put("gemini.request.delay.ms", String.valueOf(geminiDelayMs));
            values.put("gemini.max.output.tokens", String.valueOf(geminiMaxOutputTokens));
            values.put("edge.profile.path", edgeProfilePath);
            values.put("edge.driver.path", edgeDriverPath);
            values.put("crawl.interval.hours", String.valueOf(crawlIntervalHours));
            values.put("crawl.start.time", crawlStartTime);
            values.put("crawl.max.submissions", String.valueOf(maxSubmissions));
            values.put("analysis.batch.size", String.valueOf(analysisBatchSize));
            values.put("analysis.max.code.length", String.valueOf(analysisMaxCodeLength));
            return values;
        }

        Map<String, String> toCacheKeyMap() {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : toDotKeyMap().entrySet()) {
                values.put(entry.getKey().replace(".", "_"), entry.getValue());
            }
            return values;
        }
    }
}
