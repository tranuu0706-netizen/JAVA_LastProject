package com.codeanalyzer.ui;

import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.model.Account;
import com.codeanalyzer.model.AccountEvaluation;
import com.codeanalyzer.model.CrawlJob;
import com.codeanalyzer.service.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Panel quản lý tài khoản: thêm, xóa nick, crawl/phân tích/đánh giá từng nick.
 */
public class AccountPanel extends JPanel {

    private final AccountService accountService;
    private final SubmissionDAO submissionDAO;
    private final CrawlService crawlService;
    private final AnalysisService analysisService;
    private final EvaluationService evaluationService;
    private final AtomicBoolean crawlRunning;

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextField tfUsername = new JTextField(14);
    private final JTextField tfDisplayName = new JTextField(14);
    private final JButton btnCrawlAll = UIHelper.createButton("Crawl tất cả", UIHelper.SUCCESS);
    private final JButton btnStopCrawl = UIHelper.createButton("Dừng crawl", UIHelper.DANGER);


    public AccountPanel(AccountService accountService, SubmissionDAO submissionDAO,
            CrawlService crawlService, AnalysisService analysisService,
            EvaluationService evaluationService, AtomicBoolean crawlRunning) {
        this.accountService = accountService;
        this.submissionDAO = submissionDAO;
        this.crawlService = crawlService;
        this.analysisService = analysisService;
        this.evaluationService = evaluationService;
        this.crawlRunning = crawlRunning;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        btnCrawlAll.addActionListener(e -> crawlAllAccounts());
        btnStopCrawl.addActionListener(e -> stopCrawl());
        btnStopCrawl.setEnabled(false);
        add(UIHelper.createHeader("Quản lý tài khoản", btnRefresh, btnCrawlAll, btnStopCrawl), BorderLayout.NORTH);

        // --- Form thêm nick ---
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        formPanel.setBackground(UIHelper.CARD_BG);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        formPanel.add(UIHelper.styledLabel("Username:", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MUTED));
        formPanel.add(tfUsername);
        formPanel.add(UIHelper.styledLabel("Tên hiển thị:", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MUTED));
        formPanel.add(tfDisplayName);

        JButton btnAdd = UIHelper.createButton("Thêm Nick", UIHelper.SUCCESS);
        btnAdd.addActionListener(e -> addAccount());
        formPanel.add(btnAdd);

        // --- Bảng danh sách ---
        String[] columns = { "ID", "Username", "Platform", "Tên hiển thị", "Submissions", "Crawl lần cuối" };
        table = UIHelper.createTable(columns);
        tableModel = (DefaultTableModel) table.getModel();
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);

        // --- Nút hành động ---
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionBar.setOpaque(false);

        JButton btnCrawl = UIHelper.createSmallButton("Crawl nick", UIHelper.PRIMARY);
        btnCrawl.addActionListener(e -> crawlSelected());

        JButton btnAnalyze = UIHelper.createSmallButton("Phân tích AI", UIHelper.SUCCESS);
        btnAnalyze.addActionListener(e -> analyzeSelected());

        JButton btnEvaluate = UIHelper.createSmallButton("Đánh giá", UIHelper.INFO);
        btnEvaluate.addActionListener(e -> evaluateSelected());

        JButton btnDelete = UIHelper.createSmallButton("Xóa", UIHelper.DANGER);
        btnDelete.addActionListener(e -> deleteSelected());

        actionBar.add(
                UIHelper.styledLabel("Chọn nick rồi:", new Font("Segoe UI", Font.ITALIC, 12), UIHelper.TEXT_MUTED));
        actionBar.add(btnCrawl);
        actionBar.add(btnAnalyze);
        actionBar.add(btnEvaluate);
        actionBar.add(btnDelete);

        // Layout trung tâm
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.add(formPanel, BorderLayout.NORTH);
        centerPanel.add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
        add(actionBar, BorderLayout.SOUTH);
    }

    private void log(String msg) {
        System.out.println("[AccountPanel] " + msg);
    }

    private void updateCrawlButtons() {
        boolean running = crawlRunning.get();
        btnCrawlAll.setEnabled(!running);
        btnStopCrawl.setEnabled(running);
    }

    private void addAccount() {
        String username = tfUsername.getText().trim();
        String displayName = tfDisplayName.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập username!", "Thiếu thông tin",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        new SwingWorker<AccountService.AddAccountResult, Void>() {
            @Override
            protected AccountService.AddAccountResult doInBackground() {
                log("Đang kiểm tra nick " + username + " trên Codeforces...");
                return accountService.addAccount(username, "CODEFORCES", displayName);
            }

            @Override
            protected void done() {
                try {
                    AccountService.AddAccountResult result = get();
                    if (result.success()) {
                        log("✓ Thêm thành công: " + username);
                        tfUsername.setText("");
                        tfDisplayName.setText("");
                        refreshData();
                    } else {
                        log("✗ " + result.error());
                        JOptionPane.showMessageDialog(AccountPanel.this, result.error(), "Lỗi",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("Lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    private Account getSelectedAccount() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một nick trong bảng!", "Chưa chọn",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        return accountService.getAccount(id);
    }

    private void crawlSelected() {
        Account acc = getSelectedAccount();
        if (acc == null)
            return;
        if (!crawlRunning.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình crawl khác!", "Bận", JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Bắt đầu crawl: " + acc.getUsername());
        crawlService.setLogCallback(this::log);
        crawlService.resetCancellation();
        updateCrawlButtons();
        new SwingWorker<CrawlJob, Void>() {
            @Override
            protected CrawlJob doInBackground() {
                try {
                    return crawlService.crawlAccount(acc);
                } finally {
                    crawlRunning.set(false);
                }
            }

            @Override
            protected void done() {
                try {
                    CrawlJob job = get();
                    String message;
                    if ("FAILED".equals(job.getStatus())) {
                        message = "Crawl thất bại: " + job.getErrorLog();
                    } else if (crawlService.isCancelled() || "PARTIAL".equals(job.getStatus())) {
                        message = "Đã dừng crawl. Đã lưu " + job.getSubmissionsCrawled() + " bài trước khi dừng.";
                    } else {
                        message = "Hoàn thành crawl được " + job.getSubmissionsCrawled() + " bài.";
                    }
                    log(("SUCCESS".equals(job.getStatus()) ? "✓ " : "") + message);
                    JOptionPane.showMessageDialog(AccountPanel.this, message,
                            "FAILED".equals(job.getStatus()) ? "Lỗi" :
                                    ("PARTIAL".equals(job.getStatus()) ? "Đã dừng" : "Hoàn thành"),
                            "FAILED".equals(job.getStatus()) ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                    updateCrawlButtons();
                    refreshData();
                } catch (Exception e) {
                    log("Lỗi crawl: " + e.getMessage());
                    updateCrawlButtons();
                }
            }
        }.execute();
    }

    private void crawlAllAccounts() {
        if (!crawlRunning.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình crawl khác!", "Bận", JOptionPane.WARNING_MESSAGE);
            return;
        }

        log("Bắt đầu crawl tất cả tài khoản...");
        crawlService.setLogCallback(this::log);
        crawlService.resetCancellation();
        updateCrawlButtons();
        new SwingWorker<CrawlJob, Void>() {
            @Override
            protected CrawlJob doInBackground() {
                try {
                    return crawlService.crawlAll();
                } finally {
                    crawlRunning.set(false);
                }
            }

            @Override
            protected void done() {
                try {
                    CrawlJob job = get();
                    String message = crawlService.isCancelled()
                            ? "Đã dừng crawl tất cả. Đã lưu " + job.getSubmissionsCrawled() + " submissions trước khi dừng."
                            : "Hoàn thành crawl tất cả: " + job.getSubmissionsCrawled() + " submissions mới.";
                    log((crawlService.isCancelled() ? "" : "✓ ") + message);
                    JOptionPane.showMessageDialog(AccountPanel.this, message,
                            crawlService.isCancelled() ? "Đã dừng" : "Hoàn thành",
                            JOptionPane.INFORMATION_MESSAGE);
                    updateCrawlButtons();
                    refreshData();
                } catch (Exception e) {
                    log("Lỗi crawl tất cả: " + e.getMessage());
                    updateCrawlButtons();
                }
            }
        }.execute();
    }

    private void stopCrawl() {
        if (!crawlRunning.get()) {
            JOptionPane.showMessageDialog(this, "Không có tiến trình crawl đang chạy.", "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        crawlService.cancel();
        log("Đang yêu cầu dừng crawl...");
    }

    private void analyzeSelected() {
        Account acc = getSelectedAccount();
        if (acc == null)
            return;
        if (analysisService.isRunning()) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình phân tích AI khác chạy.", "Bận",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Bắt đầu phân tích AI: " + acc.getUsername());
        analysisService.setLogCallback(this::log);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return analysisService.analyzeByAccount(acc.getId());
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    if (analysisService.wasStoppedByAiLimit()) {
                        log("AI đạt giới hạn rồi");
                        JOptionPane.showMessageDialog(AccountPanel.this, "AI đạt giới hạn rồi", "Thông báo",
                                JOptionPane.WARNING_MESSAGE);
                    } else if (!analysisService.getLastStopReason().isBlank()) {
                        String message = analysisService.getLastStopReason();
                        log("Lỗi phân tích AI: " + message);
                        JOptionPane.showMessageDialog(AccountPanel.this, message, "Lỗi phân tích AI",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        String message = "Hoàn thành phân tích xong " + count + " bài.";
                        log("✓ " + message);
                        JOptionPane.showMessageDialog(AccountPanel.this, message, "Hoàn thành",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    log("Lỗi phân tích: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void evaluateSelected() {
        Account acc = getSelectedAccount();
        if (acc == null)
            return;
        if (evaluationService.isRunning()) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình đánh giá khác chạy.", "Bận",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Bắt đầu đánh giá: " + acc.getUsername());
        evaluationService.setLogCallback(this::log);
        new SwingWorker<AccountEvaluation, Void>() {
            @Override
            protected AccountEvaluation doInBackground() {
                return evaluationService.evaluate(acc.getId());
            }

            @Override
            protected void done() {
                try {
                    AccountEvaluation eval = get();
                    if (evaluationService.wasStoppedByAiLimit()) {
                        log("AI đạt giới hạn rồi");
                        JOptionPane.showMessageDialog(AccountPanel.this, "AI đạt giới hạn rồi", "Thông báo",
                                JOptionPane.WARNING_MESSAGE);
                    } else if (eval == null) {
                        String message = "Chưa có dữ liệu phân tích để đánh giá.";
                        log(message);
                        JOptionPane.showMessageDialog(AccountPanel.this, message, "Thông báo",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        String message = "Hoàn thành đánh giá tài khoản " + acc.getUsername() + ".";
                        log("✓ " + message);
                        JOptionPane.showMessageDialog(AccountPanel.this, message, "Hoàn thành",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    if (evaluationService.wasStoppedByAiLimit()) {
                        log("AI đạt giới hạn rồi");
                        JOptionPane.showMessageDialog(AccountPanel.this, "AI đạt giới hạn rồi", "Thông báo",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        log("Lỗi đánh giá: " + e.getMessage());
                        JOptionPane.showMessageDialog(AccountPanel.this, "Lỗi đánh giá: " + e.getMessage(), "Lỗi",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void deleteSelected() {
        Account acc = getSelectedAccount();
        if (acc == null)
            return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Xóa nick " + acc.getUsername()
                        + "?\nToàn bộ submissions, phân tích và đánh giá liên quan cũng sẽ bị xóa.",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION)
            return;

        accountService.deleteAccount(acc.getId());
        log("✓ Đã xóa: " + acc.getUsername());
        refreshData();
    }

    public void refreshData() {
        updateCrawlButtons();
        new SwingWorker<List<Account>, Void>() {
            @Override
            protected List<Account> doInBackground() {
                return accountService.getAllAccounts();
            }

            @Override
            protected void done() {
                try {
                    List<Account> accounts = get();
                    tableModel.setRowCount(0);
                    for (Account acc : accounts) {
                        int subCount = submissionDAO.countByAccount(acc.getId());
                        tableModel.addRow(new Object[] {
                                acc.getId(),
                                acc.getUsername(),
                                acc.getPlatform(),
                                acc.getDisplayName(),
                                subCount,
                                UIHelper.formatDateTime(acc.getLastCrawledAt())
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }
}
