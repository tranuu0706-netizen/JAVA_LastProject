package com.codeanalyzer.ui;

import com.codeanalyzer.dao.AiAnalysisDAO;
import com.codeanalyzer.service.AnalysisService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Panel phân tích AI: hiển thị kết quả phân tích và nút bắt đầu phân tích.
 */
public class AnalysisPanel extends JPanel {

    private final AiAnalysisDAO aiAnalysisDAO;
    private final AnalysisService analysisService;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea(4, 40);

    public AnalysisPanel(AiAnalysisDAO aiAnalysisDAO, AnalysisService analysisService) {
        this.aiAnalysisDAO = aiAnalysisDAO;
        this.analysisService = analysisService;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        JButton btnAnalyze = UIHelper.createButton("Bắt đầu phân tích", UIHelper.SUCCESS);
        btnAnalyze.addActionListener(e -> startAnalysis(btnAnalyze));
        add(UIHelper.createHeader("Phân tích AI (Gemini)", btnRefresh, btnAnalyze), BorderLayout.NORTH);

        // Status + info
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(UIHelper.CARD_BG);
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel infoLabel = UIHelper.styledLabel(
                "Nhấn \"Bắt đầu phân tích\" để AI phân tích các bài chưa được phân tích.",
                new Font("Segoe UI", Font.PLAIN, 13), UIHelper.TEXT_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(UIHelper.SUCCESS);
        infoPanel.add(infoLabel, BorderLayout.NORTH);
        infoPanel.add(statusLabel, BorderLayout.SOUTH);

        // Table
        String[] cols = {"Người code", "Bài tập", "CTDL", "Thuật toán", "Độ phức tạp", "Độ khó", "AI Score", "Lý do AI", "Chất lượng", "Tóm tắt"};
        table = UIHelper.createTable(cols);
        tableModel = (DefaultTableModel) table.getModel();
        // Cho phép cuộn ngang để đọc được nội dung
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(160);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(300);
        table.getColumnModel().getColumn(8).setPreferredWidth(80);
        table.getColumnModel().getColumn(9).setPreferredWidth(350);

        // Log area
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 22, 28));
        logArea.setForeground(UIHelper.TEXT_MUTED);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER), "Log phân tích",
                0, 0, new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED));
        logScroll.setPreferredSize(new Dimension(0, 100));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(infoPanel, BorderLayout.NORTH);
        center.add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);
        center.add(logScroll, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startAnalysis(JButton btn) {
        btn.setEnabled(false);
        statusLabel.setText("⏳ Đang phân tích...");
        analysisService.setLogCallback(this::log);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return analysisService.analyzeUnanalyzed();
            }

            @Override
            protected void done() {
                btn.setEnabled(true);
                try {
                    int count = get();
                    statusLabel.setText("✅ Hoàn tất: đã phân tích " + count + " bài.");
                    refreshData();
                } catch (Exception e) {
                    statusLabel.setText("❌ Lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    @SuppressWarnings("unchecked")
    public void refreshData() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override
            protected List<Map<String, Object>> doInBackground() {
                return aiAnalysisDAO.findAllWithSubmissionInfo();
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, Object>> data = get();
                    tableModel.setRowCount(0);
                    for (Map<String, Object> a : data) {
                        String coder = str(a, "displayName");
                        if (coder.isEmpty()) coder = str(a, "username");
                        String problem = str(a, "problemName");
                        if (problem.isEmpty()) problem = str(a, "problemId");

                        String ds = str(a, "dataStructures");
                        String algo = str(a, "algorithms");
                        String complexity = str(a, "complexityTime") + " / " + str(a, "complexitySpace");
                        String difficulty = str(a, "difficultyLevel");
                        Object aiScoreObj = a.get("aiUsageScore");
                        String aiScore = aiScoreObj != null ? aiScoreObj + "/100" : "";
                        String aiReason = str(a, "aiUsageReason");
                        Object qualityObj = a.get("codeQualityScore");
                        String quality = qualityObj != null ? qualityObj + "/100" : "";
                        String summary = str(a, "analysisSummary");

                        tableModel.addRow(new Object[]{
                                coder, problem, ds, algo, complexity,
                                difficulty, aiScore, aiReason, quality, summary
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
