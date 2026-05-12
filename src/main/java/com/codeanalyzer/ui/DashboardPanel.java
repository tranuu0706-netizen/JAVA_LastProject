package com.codeanalyzer.ui;

import com.codeanalyzer.dao.SubmissionDAO;
import com.codeanalyzer.service.AccountService;
import com.codeanalyzer.service.AnalysisService;

import javax.swing.*;
import java.awt.*;

/**
 * Panel Dashboard: hiển thị thống kê tổng quan hệ thống.
 */
public class DashboardPanel extends JPanel {

    private final AccountService accountService;
    private final SubmissionDAO submissionDAO;
    private final AnalysisService analysisService;

    private final JLabel lblAccounts = new JLabel("0");
    private final JLabel lblSubmissions = new JLabel("0");
    private final JLabel lblAnalyses = new JLabel("0");

    public DashboardPanel(AccountService accountService, SubmissionDAO submissionDAO, AnalysisService analysisService) {
        this.accountService = accountService;
        this.submissionDAO = submissionDAO;
        this.analysisService = analysisService;

        setLayout(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        setBackground(new Color(15, 17, 21));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Tổng quan hệ thống");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JButton btnRefresh = UIHelper.createButton("Làm mới", new Color(79, 70, 229));
        btnRefresh.addActionListener(e -> refreshData());
        header.add(title, BorderLayout.WEST);
        header.add(btnRefresh, BorderLayout.EAST);

        // Stats cards
        JPanel statsGrid = new JPanel(new GridLayout(1, 3, 20, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(createStatCard("TỔNG SỐ NICK", lblAccounts, new Color(79, 70, 229)));
        statsGrid.add(createStatCard("SỐ SUBMISSIONS", lblSubmissions, new Color(16, 185, 129)));
        statsGrid.add(createStatCard("ĐÃ PHÂN TÍCH AI", lblAnalyses, new Color(245, 158, 11)));

        // Filler
        JPanel filler = new JPanel();
        filler.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(statsGrid, BorderLayout.CENTER);
    }

    private JPanel createStatCard(String titleText, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(25, 27, 33));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 52, 60), 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));

        JLabel lbl = new JLabel(titleText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(new Color(156, 163, 175));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 42));
        valueLabel.setForeground(accentColor);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lbl);
        card.add(Box.createVerticalStrut(12));
        card.add(valueLabel);

        return card;
    }

    public void refreshData() {
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                return new int[]{
                        accountService.countAccounts(),
                        submissionDAO.count(),
                        analysisService.countAnalyses()
                };
            }

            @Override
            protected void done() {
                try {
                    int[] stats = get();
                    lblAccounts.setText(String.valueOf(stats[0]));
                    lblSubmissions.setText(String.valueOf(stats[1]));
                    lblAnalyses.setText(String.valueOf(stats[2]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }
}
