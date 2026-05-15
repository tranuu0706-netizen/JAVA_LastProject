package com.codeanalyzer.ui;

import com.codeanalyzer.service.AnalysisService;

import javax.swing.*;
import java.awt.*;

/**
 * Panel phân tích AI: chỉ chạy phân tích hàng loạt và hiển thị log tiến trình.
 * Kết quả phân tích chi tiết nằm ở trang Submissions.
 */
public class AnalysisPanel extends JPanel {

    private final AnalysisService analysisService;
    private final JLabel statusLabel = new JLabel("Sẵn sàng");
    private final JTextArea logArea = new JTextArea();
    private final JButton btnAnalyze = UIHelper.createButton("Bắt đầu phân tích", UIHelper.SUCCESS);
    private final JButton btnClearLog = UIHelper.createButton("Xóa log", UIHelper.CARD_BORDER);

    public AnalysisPanel(AnalysisService analysisService) {
        this.analysisService = analysisService;
        this.analysisService.addLogListener(this::log);

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        btnAnalyze.addActionListener(e -> startAnalysis());
        btnClearLog.addActionListener(e -> logArea.setText(""));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(UIHelper.createHeader("Phân tích AI (Gemini)", btnClearLog, btnAnalyze), BorderLayout.NORTH);
        top.add(createStatusPanel(), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(createLogPanel(), BorderLayout.CENTER);
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(UIHelper.CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel title = UIHelper.styledLabel("Log phân tích AI", new Font("Segoe UI", Font.BOLD, 14), UIHelper.TEXT_MAIN);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(UIHelper.TEXT_MUTED);

        panel.add(title, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.EAST);
        return panel;
    }

    private JScrollPane createLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(UIHelper.FIELD_BG);
        logArea.setForeground(UIHelper.TEXT_MUTED);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIHelper.CARD_BORDER));
        scrollPane.getViewport().setBackground(logArea.getBackground());
        return scrollPane;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startAnalysis() {
        if (analysisService.isRunning()) {
            statusLabel.setText("Đang có tiến trình phân tích khác chạy.");
            statusLabel.setForeground(UIHelper.WARNING);
            return;
        }

        btnAnalyze.setEnabled(false);
        statusLabel.setText("Đang phân tích...");
        statusLabel.setForeground(UIHelper.WARNING);
        logArea.setText("");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return analysisService.analyzeUnanalyzed();
            }

            @Override
            protected void done() {
                btnAnalyze.setEnabled(true);
                try {
                    int count = get();
                    if (analysisService.wasStoppedByAiLimit()) {
                        statusLabel.setText("AI đạt giới hạn rồi");
                        statusLabel.setForeground(UIHelper.WARNING);
                    } else {
                        statusLabel.setText("Hoàn tất: đã phân tích " + count + " bài.");
                        statusLabel.setForeground(UIHelper.SUCCESS);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Lỗi: " + e.getMessage());
                    statusLabel.setForeground(UIHelper.DANGER);
                }
            }
        }.execute();
    }

    public void refreshData() {
        statusLabel.setText(analysisService.isRunning() ? "Đang phân tích..." : "Sẵn sàng");
        statusLabel.setForeground(analysisService.isRunning() ? UIHelper.WARNING : UIHelper.TEXT_MUTED);
    }
}
