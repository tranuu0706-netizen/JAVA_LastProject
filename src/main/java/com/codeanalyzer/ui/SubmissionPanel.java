package com.codeanalyzer.ui;

import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.Submission;
import com.codeanalyzer.service.AccountService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel hiển thị danh sách submissions đã crawl.
 */
public class SubmissionPanel extends JPanel {

    private final SubmissionDAO submissionDAO;
    private final AccountService accountService;
    private final JTable table;
    private final DefaultTableModel tableModel;

    public SubmissionPanel(SubmissionDAO submissionDAO, AccountService accountService) {
        this.submissionDAO = submissionDAO;
        this.accountService = accountService;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        add(UIHelper.createHeader("Lịch sử Submissions", btnRefresh), BorderLayout.NORTH);

        // Table
        String[] cols = {"Sub ID", "Người code", "Bài tập", "Platform", "Verdict", "Ngôn ngữ", "Thời gian nộp"};
        table = UIHelper.createTable(cols);
        tableModel = (DefaultTableModel) table.getModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(140);
        table.getColumnModel().getColumn(6).setPreferredWidth(160);

        add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);
    }

    public void refreshData() {
        new SwingWorker<Void, Void>() {
            private List<Submission> subs;
            private Map<Integer, Account> accountById;

            @Override
            protected Void doInBackground() {
                subs = submissionDAO.search(null, null, null, null);
                accountById = new HashMap<>();
                for (Account acc : accountService.getAllAccounts()) {
                    accountById.put(acc.getId(), acc);
                }
                return null;
            }

            @Override
            protected void done() {
                tableModel.setRowCount(0);
                if (subs == null) return;
                for (Submission sub : subs) {
                    Account owner = accountById.get(sub.getAccountId());
                    String coder = owner != null
                            ? (owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername())
                            : "Account #" + sub.getAccountId();
                    tableModel.addRow(new Object[]{
                            sub.getSubmissionId(),
                            coder,
                            sub.getProblemName() != null ? sub.getProblemName() : sub.getProblemId(),
                            sub.getPlatform(),
                            sub.getVerdict(),
                            sub.getLanguage(),
                            UIHelper.formatDateTime(sub.getSubmittedAt())
                    });
                }
            }
        }.execute();
    }
}
