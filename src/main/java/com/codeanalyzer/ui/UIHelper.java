package com.codeanalyzer.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Tiện ích dùng chung cho giao diện Swing.
 */
public class UIHelper {

    public static final Color BG_DARK = new Color(13, 15, 18);
    public static final Color SIDEBAR_BG = new Color(18, 20, 24);
    public static final Color CARD_BG = new Color(24, 27, 32);
    public static final Color CARD_BG_ALT = new Color(29, 33, 39);
    public static final Color CARD_BORDER = new Color(55, 62, 72);
    public static final Color FIELD_BG = new Color(19, 22, 27);
    public static final Color PRIMARY = new Color(91, 103, 245);
    public static final Color SUCCESS = new Color(22, 163, 116);
    public static final Color WARNING = new Color(217, 119, 6);
    public static final Color DANGER = new Color(220, 60, 68);
    public static final Color INFO = new Color(37, 139, 218);
    public static final Color TEXT_MAIN = new Color(244, 247, 251);
    public static final Color TEXT_MUTED = new Color(158, 169, 184);
    public static final Color TEXT_SUBTLE = new Color(112, 124, 140);

    /**
     * Tạo nút đẹp với màu nền.
     */
    public static JButton createButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        btn.putClientProperty("FlatLaf.style", "arc: 12; borderWidth: 0; focusWidth: 1; innerFocusWidth: 0");
        return btn;
    }

    /**
     * Tạo nút nhỏ.
     */
    public static JButton createSmallButton(String text, Color bgColor) {
        JButton btn = createButton(text, bgColor);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return btn;
    }

    /**
     * Tạo JTable đẹp, không sửa được, có tooltip khi hover.
     */
    public static JTable createTable(String[] columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(40);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setBackground(CARD_BG);
        table.setForeground(TEXT_MAIN);
        table.setSelectionBackground(new Color(47, 55, 112));
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(43, 49, 58));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        // Header style
        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(18, 21, 26));
        header.setForeground(TEXT_MUTED);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER));
        header.setPreferredSize(new Dimension(0, 38));

        // Tooltip renderer - rê chuột vào ô sẽ hiện nội dung đầy đủ
        DefaultTableCellRenderer tooltipRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setForeground(isSelected ? Color.WHITE : TEXT_MAIN);
                setBackground(isSelected ? tbl.getSelectionBackground() :
                        (row % 2 == 0 ? CARD_BG : CARD_BG_ALT));
                if (value != null) {
                    String text = value.toString();
                    // Tooltip dạng HTML để xuống dòng tự động
                    if (text.length() > 50) {
                        setToolTipText("<html><body style='width:400px;padding:4px'>" + text + "</body></html>");
                    } else {
                        setToolTipText(text);
                    }
                } else {
                    setToolTipText(null);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, tooltipRenderer);

        // Double-click vào dòng -> hiện dialog chi tiết
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (Boolean.TRUE.equals(table.getClientProperty("disableDefaultRowDetail"))) {
                    return;
                }
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        showRowDetail(table, row);
                    }
                }
            }
        });

        // Tooltip hiện nhanh hơn
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        return table;
    }

    /**
     * Hiện dialog chi tiết khi double-click vào dòng trong bảng.
     */
    private static void showRowDetail(JTable table, int row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < table.getColumnCount(); col++) {
            String colName = table.getColumnName(col);
            Object value = table.getValueAt(row, col);
            sb.append(colName).append(":\n");
            sb.append(value != null ? value.toString() : "").append("\n\n");
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(FIELD_BG);
        textArea.setForeground(TEXT_MAIN);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 450));
        scrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER));

        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(table),
                scrollPane,
                "Chi tiết dòng " + (row + 1),
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Bọc table trong JScrollPane có style.
     */
    public static JScrollPane wrapInScrollPane(JTable table) {
        JScrollPane sp = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        sp.getViewport().setBackground(CARD_BG);
        return sp;
    }

    /**
     * Tạo header panel với tiêu đề + các nút bên phải.
     */
    public static JPanel createHeader(String titleText, JButton... buttons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setForeground(TEXT_MAIN);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 6));
        titlePanel.setOpaque(false);
        titlePanel.add(title, BorderLayout.NORTH);
        JPanel accent = new JPanel();
        accent.setBackground(PRIMARY);
        accent.setPreferredSize(new Dimension(54, 3));
        JPanel accentWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accentWrap.setOpaque(false);
        accentWrap.add(accent);
        titlePanel.add(accentWrap, BorderLayout.SOUTH);
        header.add(titlePanel, BorderLayout.WEST);

        if (buttons.length > 0) {
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            btnPanel.setOpaque(false);
            for (JButton btn : buttons) {
                btnPanel.add(btn);
            }
            header.add(btnPanel, BorderLayout.EAST);
        }

        return header;
    }

    public static JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return panel;
    }

    /**
     * Tạo label có style.
     */
    public static JLabel styledLabel(String text, Font font, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        lbl.setForeground(color);
        return lbl;
    }

    /**
     * Format datetime cho hiển thị.
     */
    public static String formatDateTime(java.time.LocalDateTime dt) {
        if (dt == null)
            return "";
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
}
