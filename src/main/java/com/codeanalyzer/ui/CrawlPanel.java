package com.codeanalyzer.ui;

import com.codeanalyzer.crawler.CrawlScheduler;
import com.codeanalyzer.model.CrawlJob;
import com.codeanalyzer.service.CrawlService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Panel lịch sử crawl: chạy crawl thủ công, dừng crawl và xem lịch sử/log.
 */
public class CrawlPanel extends JPanel {

    private final CrawlService crawlService;
    private final AtomicBoolean crawlRunning;

    private final JButton btnStartCrawl = UIHelper.createButton("Bắt đầu Crawl", UIHelper.PRIMARY);
    private final JButton btnStopCrawl = UIHelper.createButton("Dừng", UIHelper.CARD_BORDER);
    private final JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.CARD_BORDER);
    private final JButton btnShowJobLog = UIHelper.createButton("Xem log job", UIHelper.CARD_BORDER);
    private final JLabel statusLabel = UIHelper.styledLabel("Sẵn sàng", new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED);

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextArea logArea = new JTextArea();
    private final Map<Integer, CrawlJob> jobById = new HashMap<>();

    public CrawlPanel(CrawlService crawlService, CrawlScheduler crawlScheduler, AtomicBoolean crawlRunning) {
        this.crawlService = crawlService;
        this.crawlRunning = crawlRunning;
        this.crawlService.addLogListener(this::log);
        this.crawlService.addJobListener(job -> SwingUtilities.invokeLater(this::refreshData));

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        setBackground(UIHelper.BG_DARK);

        JLabel title = UIHelper.styledLabel("Lịch sử Crawl Submission", new Font("Segoe UI", Font.BOLD, 24), Color.WHITE);
        add(title, BorderLayout.NORTH);

        btnStartCrawl.addActionListener(e -> startCrawlAll());
        btnStopCrawl.addActionListener(e -> stopCrawl());
        btnRefresh.addActionListener(e -> refreshData());
        btnShowJobLog.addActionListener(e -> showSelectedJobLog());
        btnStopCrawl.setEnabled(false);

        String[] cols = {"ID", "Bắt đầu", "Kết thúc", "Trạng thái", "Accounts", "Scanned", "New", "Skipped", "Analyzed"};
        table = UIHelper.createTable(cols);
        table.putClientProperty("disableDefaultRowDetail", true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableModel = (DefaultTableModel) table.getModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(90);
        table.getColumnModel().getColumn(8).setPreferredWidth(90);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    showSelectedJobLog();
                }
            }
        });

        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(UIHelper.FIELD_BG);
        logArea.setForeground(UIHelper.TEXT_MUTED);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(createToolbar(), BorderLayout.NORTH);

        JPanel historyAndLog = new JPanel(new BorderLayout(0, 8));
        historyAndLog.setOpaque(false);
        historyAndLog.add(createHistoryPanel(), BorderLayout.NORTH);
        historyAndLog.add(createLogPanel(), BorderLayout.CENTER);
        content.add(historyAndLog, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(UIHelper.CARD_BG);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel statusBox = new JPanel(new BorderLayout());
        statusBox.setBackground(UIHelper.FIELD_BG);
        statusBox.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        statusBox.setPreferredSize(new Dimension(260, 34));
        statusBox.add(statusLabel, BorderLayout.CENTER);

        toolbar.add(btnStartCrawl);
        toolbar.add(btnStopCrawl);
        toolbar.add(btnRefresh);
        toolbar.add(btnShowJobLog);
        toolbar.add(statusBox);
        return toolbar;
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        JLabel title = UIHelper.styledLabel("Lịch sử Crawl", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MAIN);
        panel.add(title, BorderLayout.NORTH);

        JScrollPane tableScroll = UIHelper.wrapInScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(0, 190));
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        JLabel title = UIHelper.styledLabel("Log", new Font("Segoe UI", Font.BOLD, 13), UIHelper.TEXT_MAIN);
        panel.add(title, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        logScroll.setBorder(BorderFactory.createLineBorder(UIHelper.CARD_BORDER));
        logScroll.getViewport().setBackground(logArea.getBackground());
        panel.add(logScroll, BorderLayout.CENTER);
        return panel;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void refreshStatus() {
        boolean running = crawlRunning.get();
        btnStartCrawl.setEnabled(!running);
        btnStopCrawl.setEnabled(running);
        statusLabel.setText(running ? "Đang crawl..." : "Sẵn sàng");
        statusLabel.setForeground(running ? UIHelper.WARNING : UIHelper.TEXT_MUTED);
    }

    private void startCrawlAll() {
        if (!crawlRunning.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình crawl khác!", "Bận", JOptionPane.WARNING_MESSAGE);
            refreshStatus();
            return;
        }

        logArea.setText("");
        log("Bắt đầu crawl tất cả tài khoản...");
        crawlService.resetCancellation();
        refreshStatus();

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
                    if (crawlService.isCancelled()) {
                        log("Đã dừng crawl theo yêu cầu.");
                    } else {
                        log("Hoàn tất crawl: " + job.getSubmissionsCrawled() + " submissions mới.");
                    }
                } catch (Exception e) {
                    log("Lỗi crawl: " + e.getMessage());
                }
                refreshStatus();
                refreshData();
            }
        }.execute();
    }

    private void stopCrawl() {
        if (!crawlRunning.get()) {
            log("Không có tiến trình crawl đang chạy.");
            refreshStatus();
            return;
        }

        log("Đang yêu cầu dừng crawl...");
        crawlService.cancel();
        refreshStatus();
    }

    private void showSelectedJobLog() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            log("Chọn một dòng lịch sử crawl để xem log riêng của job.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object idValue = tableModel.getValueAt(modelRow, 0);
        if (!(idValue instanceof Integer jobId)) {
            return;
        }
        CrawlJob job = jobById.get(jobId);
        if (job == null) {
            log("Không tìm thấy dữ liệu job #" + jobId + ". Hãy bấm Làm mới.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("=== Crawl job #").append(job.getId()).append(" ===\n");
        text.append("Trạng thái: ").append(job.getStatus()).append("\n");
        text.append("Bắt đầu: ").append(UIHelper.formatDateTime(job.getStartedAt())).append("\n");
        text.append("Kết thúc: ").append(UIHelper.formatDateTime(job.getFinishedAt())).append("\n");
        text.append("Accounts: ").append(job.getAccountsProcessed()).append("\n");
        text.append("Scanned: ").append(job.getSubmissionsScanned()).append("\n");
        text.append("New: ").append(job.getSubmissionsCrawled()).append("\n");
        text.append("Skipped: ").append(job.getSubmissionsSkipped()).append("\n");
        text.append("Analyzed: ").append(job.getSubmissionsAnalyzed()).append("\n\n");

        if (job.getCrawlLog() != null && !job.getCrawlLog().isBlank()) {
            text.append(job.getCrawlLog());
        } else {
            text.append("Job này chưa có log chi tiết. Các job cũ trước khi nâng cấp có thể chỉ có thống kê tổng.");
        }
        if (job.getErrorLog() != null && !job.getErrorLog().isBlank()) {
            text.append("\n\n=== Errors ===\n").append(job.getErrorLog());
        }

        logArea.setText(text.toString());
        logArea.setCaretPosition(0);
    }

    public void refreshData() {
        refreshStatus();
        new SwingWorker<List<CrawlJob>, Void>() {
            @Override
            protected List<CrawlJob> doInBackground() {
                return crawlService.getCrawlHistory();
            }

            @Override
            protected void done() {
                try {
                    List<CrawlJob> jobs = get();
                    tableModel.setRowCount(0);
                    jobById.clear();
                    for (CrawlJob job : jobs) {
                        jobById.put(job.getId(), job);
                        tableModel.addRow(new Object[]{
                                job.getId(),
                                UIHelper.formatDateTime(job.getStartedAt()),
                                UIHelper.formatDateTime(job.getFinishedAt()),
                                job.getStatus(),
                                job.getAccountsProcessed(),
                                job.getSubmissionsScanned(),
                                job.getSubmissionsCrawled(),
                                job.getSubmissionsSkipped(),
                                job.getSubmissionsAnalyzed()
                        });
                    }
                } catch (Exception e) {
                    log("Lỗi tải lịch sử crawl: " + e.getMessage());
                }
            }
        }.execute();
    }
}
