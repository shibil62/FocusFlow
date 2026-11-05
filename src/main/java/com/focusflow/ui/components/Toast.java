package com.focusflow.ui.components;

import com.focusflow.animations.SpringValue;
import com.focusflow.app.AppContext;
import com.focusflow.ui.Theme;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Transient message pill at the bottom of the window, with an optional action such as Undo. */
public final class Toast {
    private Toast() {}

    private static Panel current;

    public static void show(String message) { show(message, null, null); }

    public static void show(String message, String actionLabel, Runnable action) {
        if (AppContext.frame == null) return;
        SwingUtilities.invokeLater(() -> {
            JLayeredPane lp = AppContext.frame.getLayeredPane();
            if (current != null) current.dismissNow();
            current = new Panel(lp, message, actionLabel, action);
        });
    }

    private static final class Panel extends JComponent {
        private final JLayeredPane lp;
        private final String message, actionLabel;
        private final Runnable action;
        private final SpringValue p = new SpringValue(0, 0.4, 0.85, this::place);
        private final Timer life;
        private boolean closing;
        private int actionX, actionW;

        Panel(JLayeredPane lp, String message, String actionLabel, Runnable action) {
            this.lp = lp;
            this.message = message;
            this.actionLabel = actionLabel;
            this.action = action;
            setOpaque(false);
            FontMetrics fm = getFontMetrics(Theme.font(13f));
            int w = fm.stringWidth(message) + 40;
            if (actionLabel != null) {
                actionW = getFontMetrics(Theme.semi(13f)).stringWidth(actionLabel);
                w += actionW + 24;
            }
            setSize(Math.min(w, 640), 44);
            lp.add(this, JLayeredPane.DRAG_LAYER);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (Panel.this.actionLabel != null && e.getX() >= actionX - 8) {
                        Runnable a = Panel.this.action;
                        dismissNow();
                        if (a != null) a.run();
                    }
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            place();
            p.to(1);
            life = new Timer(actionLabel != null ? 6000 : 3500, e -> dismiss());
            life.setRepeats(false);
            life.start();
        }

        private void place() {
            int x = (lp.getWidth() - getWidth()) / 2;
            int y = lp.getHeight() - getHeight() - 28 + (int) ((1 - p.value) * 36);
            setLocation(x, y);
            repaint();
        }

        void dismiss() {
            if (closing) return;
            closing = true;
            p.onSettled(this::remove);
            p.to(0);
        }

        void dismissNow() {
            closing = true;
            life.stop();
            remove();
        }

        private void remove() {
            if (!closing) return;
            lp.remove(this);
            lp.repaint();
            if (current == this) current = null;
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, p.value))));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.dark ? new Color(0x48484A) : new Color(0x2C2C2E));
            g2.fill(Gfx.rr(0, 0, w, h, h));
            FontMetrics fm = g2.getFontMetrics(Theme.font(13f));
            int maxText = w - 40 - (actionLabel != null ? actionW + 24 : 0);
            Gfx.text(g2, Gfx.fit(message, fm, maxText), 20, (h - fm.getHeight()) / 2 + fm.getAscent(), Theme.font(13f), Color.WHITE);
            if (actionLabel != null) {
                actionX = w - 20 - actionW;
                Gfx.text(g2, actionLabel, actionX, (h - fm.getHeight()) / 2 + fm.getAscent(), Theme.semi(13f), new Color(0x64B5FF));
            }
            g2.dispose();
        }
    }
}
