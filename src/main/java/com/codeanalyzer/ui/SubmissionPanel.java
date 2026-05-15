package com.codeanalyzer.ui;

import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.AiAnalysis;
import com.codeanalyzer.model.Submission;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.AnalysisService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel hiển thị danh sách submissions đã crawl.
 */
public class SubmissionPanel extends JPanel {

    private final SubmissionDAO submissionDAO;
    private final AccountService accountService;
    private final AnalysisService analysisService;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JComboBox<AccountFilterOption> cbAccountFilter = new JComboBox<>();
    private final List<Long> rowSubmissionIds = new ArrayList<>();
    private JLabel filterStatusLabel;
    private boolean updatingAccountFilter = false;

    public SubmissionPanel(SubmissionDAO submissionDAO, AccountService accountService, AnalysisService analysisService) {
        this.submissionDAO = submissionDAO;
        this.accountService = accountService;
        this.analysisService = analysisService;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        JButton btnDetail = UIHelper.createButton("Xem chi tiết", UIHelper.SUCCESS);
        btnRefresh.addActionListener(e -> refreshData());
        btnDetail.addActionListener(e -> showSelectedSubmissionDetail());
        add(UIHelper.createHeader("Lịch sử Submissions", btnRefresh, btnDetail), BorderLayout.NORTH);

        JPanel filterPanel = createFilterPanel();

        // Table
        String[] cols = {"Sub ID", "Người code", "Bài tập", "Platform", "Verdict", "AI", "Ngôn ngữ", "Thời gian nộp"};
        table = UIHelper.createTable(cols);
        table.putClientProperty("disableDefaultRowDetail", true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableModel = (DefaultTableModel) table.getModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        table.getColumnModel().getColumn(6).setPreferredWidth(140);
        table.getColumnModel().getColumn(7).setPreferredWidth(160);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    showSelectedSubmissionDetail();
                }
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(filterPanel, BorderLayout.NORTH);
        center.add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setBackground(UIHelper.CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JLabel label = UIHelper.styledLabel("Username:", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MAIN);
        JButton btnClear = UIHelper.createSmallButton("Xóa lọc", UIHelper.WARNING);
        filterStatusLabel = UIHelper.styledLabel(" ", new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_MUTED);

        cbAccountFilter.setPreferredSize(new Dimension(220, 32));
        cbAccountFilter.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbAccountFilter.setToolTipText("Chọn username Codeforces để lọc submissions");
        cbAccountFilter.addActionListener(e -> {
            if (!updatingAccountFilter) {
                refreshData();
            }
        });
        btnClear.addActionListener(e -> {
            if (cbAccountFilter.getItemCount() > 0) {
                cbAccountFilter.setSelectedIndex(0);
            }
            refreshData();
        });

        panel.add(label);
        panel.add(cbAccountFilter);
        panel.add(btnClear);
        panel.add(filterStatusLabel);
        return panel;
    }

    public void refreshData() {
        new SwingWorker<Void, Void>() {
            private List<Submission> subs;
            private Map<Integer, Account> accountById;
            private List<Account> accounts;
            private String filterMessage = "";
            private final AccountFilterOption selectedFilter = getSelectedFilterOption();
            private final Integer selectedAccountId = selectedFilter != null ? selectedFilter.accountId : null;

            @Override
            protected Void doInBackground() {
                accountById = new HashMap<>();
                accounts = accountService.getAllAccounts();
                for (Account acc : accounts) {
                    accountById.put(acc.getId(), acc);
                }

                subs = submissionDAO.search(selectedAccountId, null, null, null);
                filterMessage = selectedAccountId == null
                        ? "Đang hiển thị tất cả submissions."
                        : "Đang lọc theo username: " + selectedFilter.username;
                return null;
            }

            @Override
            protected void done() {
                tableModel.setRowCount(0);
                rowSubmissionIds.clear();
                filterStatusLabel.setText(filterMessage);
                updateAccountFilter(accounts, selectedAccountId);
                if (subs == null) return;
                for (Submission sub : subs) {
                    Account owner = accountById.get(sub.getAccountId());
                    String coder = owner != null ? owner.getUsername() : "Account #" + sub.getAccountId();
                    rowSubmissionIds.add(sub.getId());
                    tableModel.addRow(new Object[]{
                            sub.getSubmissionId(),
                            coder,
                            sub.getProblemName() != null ? sub.getProblemName() : sub.getProblemId(),
                            sub.getPlatform(),
                            sub.getVerdict(),
                            displayAnalysisStatus(sub),
                            sub.getLanguage(),
                            UIHelper.formatDateTime(sub.getSubmittedAt())
                    });
                }
            }
        }.execute();
    }

    private void showSelectedSubmissionDetail() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một submission.", "Chưa chọn",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= rowSubmissionIds.size()) {
            JOptionPane.showMessageDialog(this, "Không lấy được submission đã chọn.", "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        long submissionDbId = rowSubmissionIds.get(modelRow);
        new SwingWorker<SubmissionDetailData, Void>() {
            @Override
            protected SubmissionDetailData doInBackground() {
                SubmissionDetailData data = new SubmissionDetailData();
                data.submission = submissionDAO.findById(submissionDbId);
                if (data.submission != null) {
                    data.account = accountService.getAccount(data.submission.getAccountId());
                    data.analysis = analysisService.getAnalysis(data.submission.getId());
                }
                return data;
            }

            @Override
            protected void done() {
                try {
                    SubmissionDetailData data = get();
                    if (data.submission == null) {
                        JOptionPane.showMessageDialog(SubmissionPanel.this,
                                "Submission không còn tồn tại trong database.", "Không tìm thấy",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    SubmissionDetailDialog dialog = new SubmissionDetailDialog(
                            SwingUtilities.getWindowAncestor(SubmissionPanel.this), data);
                    dialog.setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SubmissionPanel.this,
                            "Lỗi mở chi tiết submission: " + e.getMessage(), "Lỗi",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String buildAnalysisText(Submission submission, AiAnalysis analysis) {
        if (analysis == null) {
            String status = displayAnalysisStatus(submission);
            return "Submission này chưa có phân tích AI.\n\n"
                    + "Trạng thái hiện tại: " + status + "\n"
                    + (submission != null && submission.getAnalysisError() != null && !submission.getAnalysisError().isBlank()
                        ? "Lỗi gần nhất: " + submission.getAnalysisError() + "\n\n"
                        : "\n")
                    + "Bấm \"Phân tích bài này\" để Gemini phân tích code hiện tại.";
        }

        return "Cách hiểu điểm:\n"
                + "- AI Score là mức nghi ngờ code có hỗ trợ AI, không phải kết luận chắc chắn.\n"
                + "- Code Quality là điểm chất lượng code dựa trên độ rõ ràng, độ phức tạp, xử lý biên và khả năng bảo trì.\n\n"
                + "CTDL: " + listText(analysis.getDataStructures()) + "\n"
                + "Thuật toán: " + listText(analysis.getAlgorithms()) + "\n"
                + "Độ phức tạp thời gian: " + textOrDash(analysis.getComplexityTime()) + "\n"
                + "Độ phức tạp bộ nhớ: " + textOrDash(analysis.getComplexitySpace()) + "\n"
                + "Độ khó: " + textOrDash(analysis.getDifficultyLevel()) + "\n"
                + "AI Score: " + analysis.getAiUsageScore() + "/100\n"
                + "Chất lượng code: " + analysis.getCodeQualityScore() + "/100\n"
                + "Phân tích lúc: " + UIHelper.formatDateTime(analysis.getAnalyzedAt()) + "\n\n"
                + "Lý do AI:\n" + textOrDash(analysis.getAiUsageReason()) + "\n\n"
                + "Nhận xét chi tiết:\n" + textOrDash(analysis.getAnalysisSummary());
    }

    private String buildSubmissionInfo(Submission submission, Account account) {
        String username = account != null ? account.getUsername() : "Account #" + submission.getAccountId();
        return "Username: " + username + "\n"
                + "Submission ID: " + submission.getSubmissionId() + "\n"
                + "Bài tập: " + textOrDash(submission.getProblemName()) + "\n"
                + "Problem ID: " + textOrDash(submission.getProblemId()) + "\n"
                + "Trạng thái AI: " + displayAnalysisStatus(submission) + "\n"
                + (submission.getAnalysisError() == null || submission.getAnalysisError().isBlank()
                    ? ""
                    : "Lỗi AI gần nhất: " + submission.getAnalysisError() + "\n")
                + "Ngôn ngữ: " + textOrDash(submission.getLanguage()) + "\n"
                + "Verdict: " + textOrDash(submission.getVerdict()) + "\n"
                + "Thời gian nộp: " + UIHelper.formatDateTime(submission.getSubmittedAt());
    }

    private String listText(List<String> values) {
        return values == null || values.isEmpty() ? "Chưa có dữ liệu" : String.join(", ", values);
    }

    private String textOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String displayAnalysisStatus(Submission submission) {
        if (submission == null) {
            return "Chưa phân tích";
        }
        String status = submission.getAnalysisStatus();
        if (SubmissionDAO.STATUS_ANALYZED.equals(status)) {
            return "Đã phân tích";
        }
        if (SubmissionDAO.STATUS_ERROR.equals(status)) {
            return "Lỗi phân tích";
        }
        return "Chưa phân tích";
    }

    private AccountFilterOption getSelectedFilterOption() {
        Object selected = cbAccountFilter.getSelectedItem();
        return selected instanceof AccountFilterOption ? (AccountFilterOption) selected : null;
    }

    private void updateAccountFilter(List<Account> accounts, Integer selectedAccountId) {
        updatingAccountFilter = true;
        DefaultComboBoxModel<AccountFilterOption> model = new DefaultComboBoxModel<>();
        model.addElement(AccountFilterOption.all());

        AccountFilterOption selected = null;
        if (accounts != null) {
            for (Account account : accounts) {
                AccountFilterOption option = new AccountFilterOption(account.getId(), account.getUsername());
                model.addElement(option);
                if (selectedAccountId != null && account.getId() == selectedAccountId) {
                    selected = option;
                }
            }
        }

        cbAccountFilter.setModel(model);
        if (selected != null) {
            cbAccountFilter.setSelectedItem(selected);
        } else {
            cbAccountFilter.setSelectedIndex(0);
        }
        updatingAccountFilter = false;
    }

    private static class AccountFilterOption {
        private final Integer accountId;
        private final String username;

        private AccountFilterOption(Integer accountId, String username) {
            this.accountId = accountId;
            this.username = username;
        }

        static AccountFilterOption all() {
            return new AccountFilterOption(null, "Tất cả nick");
        }

        @Override
        public String toString() {
            return username;
        }
    }

    private static class SubmissionDetailData {
        Submission submission;
        Account account;
        AiAnalysis analysis;
    }

    private class SubmissionDetailDialog extends JDialog {
        private final Submission submission;
        private AiAnalysis analysis;
        private final JTextArea analysisArea = new JTextArea();
        private final JLabel statusLabel = UIHelper.styledLabel(" ", new Font("Segoe UI", Font.PLAIN, 12), UIHelper.TEXT_MUTED);
        private final JButton btnAnalyze = UIHelper.createButton("Phân tích bài này", UIHelper.SUCCESS);

        SubmissionDetailDialog(Window owner, SubmissionDetailData data) {
            super(owner, "Chi tiết submission " + data.submission.getSubmissionId(), Dialog.ModalityType.MODELESS);
            this.submission = data.submission;
            this.analysis = data.analysis;

            setSize(1080, 760);
            setMinimumSize(new Dimension(860, 560));
            setLocationRelativeTo(SubmissionPanel.this);
            setLayout(new BorderLayout(0, 10));
            getContentPane().setBackground(UIHelper.BG_DARK);

            add(createInfoPanel(data), BorderLayout.NORTH);
            add(createTabs(), BorderLayout.CENTER);
            add(createFooter(), BorderLayout.SOUTH);

            updateAnalysisArea();
        }

        private JPanel createInfoPanel(SubmissionDetailData data) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(UIHelper.CARD_BG);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)));

            JTextArea infoArea = createReadOnlyArea(new Font("Segoe UI", Font.PLAIN, 13), true);
            infoArea.setText(buildSubmissionInfo(data.submission, data.account));
            infoArea.setRows(7);
            panel.add(infoArea, BorderLayout.CENTER);
            return panel;
        }

        private JTabbedPane createTabs() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

            JTextArea codeArea = createReadOnlyArea(new Font("Consolas", Font.PLAIN, 13), false);
            codeArea.setText(submission.getSourceCode() == null || submission.getSourceCode().isBlank()
                    ? "Submission này chưa có source code."
                    : submission.getSourceCode());
            codeArea.setCaretPosition(0);

            analysisArea.setEditable(false);
            analysisArea.setLineWrap(true);
            analysisArea.setWrapStyleWord(true);
            analysisArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            analysisArea.setBackground(UIHelper.FIELD_BG);
            analysisArea.setForeground(UIHelper.TEXT_MAIN);
            analysisArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            tabs.addTab("Code", wrapTextArea(codeArea, true));
            tabs.addTab("Đánh giá AI", wrapTextArea(analysisArea, false));
            return tabs;
        }

        private JPanel createFooter() {
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            JButton btnClose = UIHelper.createButton("Đóng", UIHelper.CARD_BORDER);
            btnClose.addActionListener(e -> dispose());
            btnAnalyze.addActionListener(e -> analyzeCurrentSubmission());
            buttons.add(btnAnalyze);
            buttons.add(btnClose);

            footer.add(statusLabel, BorderLayout.WEST);
            footer.add(buttons, BorderLayout.EAST);
            return footer;
        }

        private JTextArea createReadOnlyArea(Font font, boolean wrap) {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setFont(font);
            area.setLineWrap(wrap);
            area.setWrapStyleWord(wrap);
            area.setBackground(UIHelper.FIELD_BG);
            area.setForeground(UIHelper.TEXT_MAIN);
            area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            return area;
        }

        private JScrollPane wrapTextArea(JTextArea area, boolean horizontalScroll) {
            JScrollPane scrollPane = new JScrollPane(area,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    horizontalScroll ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createLineBorder(UIHelper.CARD_BORDER));
            scrollPane.getViewport().setBackground(area.getBackground());
            return scrollPane;
        }

        private void updateAnalysisArea() {
            analysisArea.setText(buildAnalysisText(submission, analysis));
            analysisArea.setCaretPosition(0);
            btnAnalyze.setText(analysis == null ? "Phân tích bài này" : "Phân tích lại");
        }

        private void analyzeCurrentSubmission() {
            if (analysisService.isRunning()) {
                statusLabel.setText("Đang có tiến trình phân tích AI khác chạy.");
                statusLabel.setForeground(UIHelper.WARNING);
                return;
            }

            boolean reanalyze = analysis != null;
            btnAnalyze.setEnabled(false);
            statusLabel.setText(reanalyze ? "Đang phân tích lại submission..." : "Đang phân tích submission...");
            statusLabel.setForeground(UIHelper.WARNING);

            new SwingWorker<AiAnalysis, Void>() {
                @Override
                protected AiAnalysis doInBackground() {
                    return analysisService.analyzeSubmission(submission.getId(), reanalyze);
                }

                @Override
                protected void done() {
                    btnAnalyze.setEnabled(true);
                    try {
                        AiAnalysis result = get();
                        if (analysisService.wasStoppedByAiLimit()) {
                            statusLabel.setText("AI đạt giới hạn rồi.");
                            statusLabel.setForeground(UIHelper.WARNING);
                        } else if (result == null) {
                            statusLabel.setText("Không tạo được kết quả phân tích.");
                            statusLabel.setForeground(UIHelper.DANGER);
                            SubmissionPanel.this.refreshData();
                        } else {
                            analysis = result;
                            updateAnalysisArea();
                            SubmissionPanel.this.refreshData();
                            statusLabel.setText("Đã cập nhật phân tích AI.");
                            statusLabel.setForeground(UIHelper.SUCCESS);
                        }
                    } catch (Exception e) {
                        statusLabel.setText("Lỗi phân tích: " + e.getMessage());
                        statusLabel.setForeground(UIHelper.DANGER);
                        SubmissionPanel.this.refreshData();
                    }
                }
            }.execute();
        }
    }
}
