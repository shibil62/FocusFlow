package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.app.AppContext;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/** Modal sheet: dimmed scrim plus a rounded panel that slides up with a spring. Never blocks the event thread. */
public final class Sheet extends JPanel {
    private static final List<Sheet> OPEN = new ArrayList<>();

    private final JComponent content;
    private final int w, h;
    private final SpringValue p = new SpringValue(0, 0.38, 0.9, this::tick);
    private boolean closing;
    private Component prevFocus;
    private Runnable onClose;
    private final ComponentAdapter resizer = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) {
            setBounds(0, 0, getParent().getWidth(), getParent().getHeight());
            revalidate();
        }
    };

    private Sheet(JComponent content, int w, int h) {
        super(null);
        this.content = content;
        this.w = w;
        this.h = h;
        setOpaque(false);
        add(content);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!Sheet.this.content.getBounds().contains(e.getPoint())) close();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(new MouseMotionAdapter() {});
        addMouseWheelListener(e -> {});
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!OPEN.isEmpty() && OPEN.get(OPEN.size() - 1) == Sheet.this) close();
            }
        });
        p.onSettled(() -> { if (closing) finish(); });
    }

    public static boolean isOpen() { return !OPEN.isEmpty(); }

    public static Sheet show(JComponent content, int w, int h) {
        Sheet s = new Sheet(content, w, h);
        JLayeredPane lp = AppContext.frame.getLayeredPane();
        s.prevFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        s.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.addComponentListener(s.resizer);
        lp.add(s, Integer.valueOf(JLayeredPane.MODAL_LAYER + OPEN.size() * 2));
        OPEN.add(s);
        s.validate();
        s.p.to(1);
        return s;
    }

    public Sheet onClose(Runnable r) { onClose = r; return this; }

    public void close() {
        if (closing) return;
        closing = true;
        p.to(0);
    }

    private void finish() {
        Container parent = getParent();
        if (parent != null) {
            parent.removeComponentListener(resizer);
            parent.remove(this);
            parent.repaint();
        }
        OPEN.remove(this);
        if (prevFocus != null && prevFocus.isShowing()) prevFocus.requestFocusInWindow();
        if (onClose != null) onClose.run();
    }

    private void tick() {
        doLayout();
        repaint();
    }

    @Override
    public void doLayout() {
        int cw = Math.min(w, getWidth() - 40), ch = Math.min(h, getHeight() - 40);
        int x = (getWidth() - cw) / 2;
        int y = (getHeight() - ch) / 2 + (int) ((1 - p.value) * 30);
        content.setBounds(x, y, cw, ch);
        if (!content.isValid()) content.validate();
    }

    private float alpha() { return (float) Math.max(0, Math.min(1, p.value)); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        g2.setColor(new Color(0, 0, 0, (int) (alpha() * (Theme.dark ? 150 : 90))));
        g2.fillRect(0, 0, getWidth(), getHeight());
        Rectangle r = content.getBounds();
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha()));
        Gfx.shadow(g2, r.x, r.y, r.width, r.height, 24, 14, 0.30);
        g2.setColor(Theme.surface);
        g2.fill(Gfx.rr(r.x, r.y, r.width, r.height, 24));
        g2.setColor(Theme.separator);
        g2.draw(Gfx.rr(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 24));
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha()));
        super.paintChildren(g2);
        g2.dispose();
    }

    // ------------------------------------------------------------ confirm sheet

    public static void confirm(String title, String message, String confirmLabel, boolean destructive, Runnable onConfirm) {
        confirm(title, message, confirmLabel, "Cancel", destructive, onConfirm);
    }

    public static void confirm(String title, String message, String confirmLabel, String cancelLabel,
                               boolean destructive, Runnable onConfirm) {
        JPanel c = new JPanel(new BorderLayout(0, 14));
        c.setOpaque(false);
        c.setBorder(new EmptyBorder(26, 26, 20, 26));
        c.add(new Lbl(title, Theme.bold(18f), Lbl.Role.TEXT), BorderLayout.NORTH);
        String safe = message.replace("&", "&amp;").replace("<", "&lt;");
        c.add(new Lbl("<html><div style='width:300px'>" + safe + "</div></html>", Theme.font(13f), Lbl.Role.TEXT2), BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        Sheet[] ref = new Sheet[1];
        Btn cancel = new Btn(cancelLabel, Btn.Kind.TINTED);
        Btn ok = new Btn(confirmLabel, destructive ? Btn.Kind.DANGER : Btn.Kind.PRIMARY);
        cancel.addActionListener(e -> ref[0].close());
        ok.addActionListener(e -> { ref[0].close(); onConfirm.run(); });
        btns.add(cancel);
        btns.add(ok);
        c.add(btns, BorderLayout.SOUTH);
        ref[0] = show(c, 380, 200);
        SwingUtilities.invokeLater(ok::requestFocusInWindow);
    }
}
