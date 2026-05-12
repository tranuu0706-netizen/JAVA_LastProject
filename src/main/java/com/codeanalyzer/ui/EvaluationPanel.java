package com.codeanalyzer.ui;

import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.AccountEvaluation;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.EvaluationService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel đánh giá tổng hợp năng lực từng nick.
 */
public class EvaluationPanel extends JPanel {

    private final EvaluationService evaluationService;
    private final AccountService accountService;
    private final JTable table;
    private final DefaultTableModel tableModel;

    public EvaluationPanel(EvaluationService evaluationService, AccountService accountService) {
        this.evaluationService = evaluationService;
        this.accountService = accountService;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        JButton btnEvalAll = UIHelper.createButton("Đánh giá tất cả", UIHelper.SUCCESS);
        btnEvalAll.addActionListener(e -> evaluateAll(btnEvalAll));
        add(UIHelper.createHeader("Đánh giá tổng hợp", btnRefresh, btnEvalAll), BorderLayout.NORTH);

        // Table
        String[] cols = {"Nick", "Level", "Điểm DS", "Điểm Algo", "Mức dùng AI", "Số bài", "Điểm mạnh", "Điểm yếu", "Khuyến nghị", "Đánh giá lúc"};
        table = UIHelper.createTable(cols);
        tableModel = (DefaultTableModel) table.getModel();
        // Cho phép cuộn ngang để đọc được nội dung
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(160);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(300);
        table.getColumnModel().getColumn(7).setPreferredWidth(300);
        table.getColumnModel().getColumn(8).setPreferredWidth(350);
        table.getColumnModel().getColumn(9).setPreferredWidth(150);

        add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);
    }

    private void evaluateAll(JButton btn) {
        btn.setEnabled(false);
        evaluationService.setLogCallback(msg ->
                SwingUtilities.invokeLater(() -> System.out.println("[EvalUI] " + msg)));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                evaluationService.evaluateAll();
                return null;
            }

            @Override
            protected void done() {
                btn.setEnabled(true);
                refreshData();
                JOptionPane.showMessageDialog(EvaluationPanel.this, "Đánh giá hoàn tất!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    public void refreshData() {
        new SwingWorker<Void, Void>() {
            private List<AccountEvaluation> evals;
            private Map<Integer, Account> accountById;

            @Override
            protected Void doInBackground() {
                evals = evaluationService.getAllEvaluations();
                accountById = new HashMap<>();
                for (Account acc : accountService.getAllAccounts()) {
                    accountById.put(acc.getId(), acc);
                }
                return null;
            }

            @Override
            protected void done() {
                tableModel.setRowCount(0);
                if (evals == null) return;
                for (AccountEvaluation ev : evals) {
                    Account owner = accountById.get(ev.getAccountId());
                    String nick = owner != null
                            ? (owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername())
                            : "Account #" + ev.getAccountId();

                    String aiInfo = (ev.getAiUsageLevel() != null ? ev.getAiUsageLevel() : "")
                            + (ev.getAvgAiUsageScore() != null ? " (" + ev.getAvgAiUsageScore() + "/100)" : "");

                    tableModel.addRow(new Object[]{
                            nick,
                            ev.getOverallLevel(),
                            ev.getDsScore(),
                            ev.getAlgoScore(),
                            aiInfo,
                            ev.getTotalSubmissionsAnalyzed(),
                            ev.getStrengths(),
                            ev.getWeaknesses(),
                            ev.getRecommendation(),
                            UIHelper.formatDateTime(ev.getEvaluatedAt())
                    });
                }
            }
        }.execute();
    }

}
