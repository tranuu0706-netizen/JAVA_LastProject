package com.codeanalyzer.ui;

import com.codeanalyzer.config.AppConfig;
import com.codeanalyzer.crawler.CrawlScheduler;
import com.codeanalyzer.model.CrawlJob;
import com.codeanalyzer.service.CrawlService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Panel Crawl: quản lý scheduler, crawl tất cả, lịch sử crawl.
 */
public class CrawlPanel extends JPanel {

    private final CrawlService crawlService;
    private final CrawlScheduler crawlScheduler;
    private final AtomicBoolean crawlRunning;

    private final JLabel lblSchedulerStatus = new JLabel("---");
    private final JLabel lblCrawlStatus = new JLabel("---");
    private final JLabel lblPlan = new JLabel("---");
    private final JSpinner spinInterval = new JSpinner(new SpinnerNumberModel(24, 1, 168, 1));
    private final JTextField tfStartTime = new JTextField("02:00", 6);
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextArea logArea = new JTextArea(5, 40);

    public CrawlPanel(CrawlService crawlService, CrawlScheduler crawlScheduler, AtomicBoolean crawlRunning) {
        this.crawlService = crawlService;
        this.crawlScheduler = crawlScheduler;
        this.crawlRunning = crawlRunning;

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(UIHelper.BG_DARK);

        // Header
        JButton btnRefresh = UIHelper.createButton("Làm mới", UIHelper.PRIMARY);
        btnRefresh.addActionListener(e -> refreshData());
        JButton btnCrawlAll = UIHelper.createButton("Crawl tất cả", UIHelper.SUCCESS);
        btnCrawlAll.addActionListener(e -> startCrawlAll());
        add(UIHelper.createHeader("Crawl định kỳ", btnRefresh, btnCrawlAll), BorderLayout.NORTH);

        // --- Top panel: Status + Config ---
        JPanel topGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        topGrid.setOpaque(false);

        // Status card
        JPanel statusCard = new JPanel();
        statusCard.setLayout(new BoxLayout(statusCard, BoxLayout.Y_AXIS));
        statusCard.setBackground(UIHelper.CARD_BG);
        statusCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        statusCard.add(createStatusRow("Scheduler:", lblSchedulerStatus));
        statusCard.add(Box.createVerticalStrut(8));
        statusCard.add(createStatusRow("Crawl hiện tại:", lblCrawlStatus));
        statusCard.add(Box.createVerticalStrut(8));
        statusCard.add(createStatusRow("Lịch chạy:", lblPlan));
        statusCard.add(Box.createVerticalStrut(12));

        JPanel schedulerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        schedulerBtns.setOpaque(false);
        JButton btnStart = UIHelper.createSmallButton("Bật", UIHelper.SUCCESS);
        btnStart.addActionListener(e -> { crawlScheduler.start(); refreshStatus(); });
        JButton btnStop = UIHelper.createSmallButton("Tắt", UIHelper.DANGER);
        btnStop.addActionListener(e -> { crawlScheduler.stop(); refreshStatus(); });
        JButton btnRestart = UIHelper.createSmallButton("Khởi động lại", UIHelper.PRIMARY);
        btnRestart.addActionListener(e -> { crawlScheduler.restart(); refreshStatus(); });
        schedulerBtns.add(btnStart);
        schedulerBtns.add(btnStop);
        schedulerBtns.add(btnRestart);
        statusCard.add(schedulerBtns);

        // Config card
        JPanel configCard = new JPanel();
        configCard.setLayout(new BoxLayout(configCard, BoxLayout.Y_AXIS));
        configCard.setBackground(UIHelper.CARD_BG);
        configCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        JLabel cfgTitle = UIHelper.styledLabel("Cấu hình lịch", new Font("Segoe UI", Font.BOLD, 15), Color.WHITE);
        cfgTitle.setAlignmentX(LEFT_ALIGNMENT);
        configCard.add(cfgTitle);
        configCard.add(Box.createVerticalStrut(12));

        JPanel cfgRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cfgRow1.setOpaque(false);
        cfgRow1.setAlignmentX(LEFT_ALIGNMENT);
        cfgRow1.add(UIHelper.styledLabel("Khoảng cách (giờ):", new Font("Segoe UI", Font.PLAIN, 13), UIHelper.TEXT_MUTED));
        cfgRow1.add(spinInterval);
        configCard.add(cfgRow1);
        configCard.add(Box.createVerticalStrut(8));

        JPanel cfgRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cfgRow2.setOpaque(false);
        cfgRow2.setAlignmentX(LEFT_ALIGNMENT);
        cfgRow2.add(UIHelper.styledLabel("Giờ bắt đầu:", new Font("Segoe UI", Font.PLAIN, 13), UIHelper.TEXT_MUTED));
        cfgRow2.add(tfStartTime);
        configCard.add(cfgRow2);
        configCard.add(Box.createVerticalStrut(12));

        JButton btnSave = UIHelper.createButton("Lưu lịch", UIHelper.PRIMARY);
        btnSave.setAlignmentX(LEFT_ALIGNMENT);
        btnSave.addActionListener(e -> saveSchedulerConfig());
        configCard.add(btnSave);

        topGrid.add(statusCard);
        topGrid.add(configCard);

        // --- Bảng lịch sử crawl ---
        String[] cols = {"ID", "Bắt đầu", "Kết thúc", "Trạng thái", "Số nick", "Submissions mới", "Lỗi"};
        table = UIHelper.createTable(cols);
        tableModel = (DefaultTableModel) table.getModel();
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(6).setPreferredWidth(200);

        // --- Log area ---
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 22, 28));
        logArea.setForeground(UIHelper.TEXT_MUTED);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UIHelper.CARD_BORDER), "Log Crawl",
                0, 0, new Font("Segoe UI", Font.BOLD, 12), UIHelper.TEXT_MUTED));
        logScroll.setPreferredSize(new Dimension(0, 100));

        // Center layout
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(topGrid, BorderLayout.NORTH);
        center.add(UIHelper.wrapInScrollPane(table), BorderLayout.CENTER);
        center.add(logScroll, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    private JPanel createStatusRow(String label, JLabel value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(UIHelper.styledLabel(label, new Font("Segoe UI", Font.PLAIN, 13), UIHelper.TEXT_MUTED));
        value.setFont(new Font("Segoe UI", Font.BOLD, 13));
        row.add(value);
        return row;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void refreshStatus() {
        boolean running = crawlScheduler.isRunning();
        lblSchedulerStatus.setText(running ? "Đang bật" : "Đã tắt");
        lblSchedulerStatus.setForeground(running ? UIHelper.SUCCESS : UIHelper.WARNING);
        lblCrawlStatus.setText(crawlRunning.get() ? "Đang crawl" : "Rảnh");
        lblCrawlStatus.setForeground(crawlRunning.get() ? UIHelper.WARNING : UIHelper.SUCCESS);
        lblPlan.setText("Mỗi " + AppConfig.getCrawlIntervalHours() + " giờ, bắt đầu " + AppConfig.getCrawlStartTime());
        lblPlan.setForeground(UIHelper.TEXT_MAIN);
        spinInterval.setValue(AppConfig.getCrawlIntervalHours());
        tfStartTime.setText(AppConfig.getCrawlStartTime());
    }

    private void startCrawlAll() {
        if (!crawlRunning.compareAndSet(false, true)) {
            JOptionPane.showMessageDialog(this, "Đang có tiến trình crawl!", "Bận", JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Bắt đầu crawl tất cả tài khoản...");
        crawlService.setLogCallback(this::log);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    crawlService.crawlAll();
                } finally {
                    crawlRunning.set(false);
                }
                return null;
            }

            @Override
            protected void done() {
                log("✓ Hoàn tất crawl tất cả.");
                refreshData();
            }
        }.execute();
        refreshStatus();
    }

    private void saveSchedulerConfig() {
        int interval = (int) spinInterval.getValue();
        String startTime = tfStartTime.getText().trim();
        try {
            LocalTime.parse(startTime);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Giờ bắt đầu phải có dạng HH:mm", "Sai định dạng", JOptionPane.ERROR_MESSAGE);
            return;
        }
        AppConfig.set("crawl_interval_hours", String.valueOf(interval));
        AppConfig.set("crawl_start_time", startTime);
        if (crawlScheduler.isRunning()) crawlScheduler.restart();
        refreshStatus();
        log("✓ Đã lưu lịch: mỗi " + interval + " giờ, bắt đầu " + startTime);
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
                    for (CrawlJob job : jobs) {
                        String error = job.getErrorLog();
                        if (error != null && error.length() > 80) error = error.substring(0, 80) + "...";
                        tableModel.addRow(new Object[]{
                                job.getId(),
                                UIHelper.formatDateTime(job.getStartedAt()),
                                UIHelper.formatDateTime(job.getFinishedAt()),
                                job.getStatus(),
                                job.getAccountsProcessed(),
                                job.getSubmissionsCrawled(),
                                error != null ? error : ""
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }
}
