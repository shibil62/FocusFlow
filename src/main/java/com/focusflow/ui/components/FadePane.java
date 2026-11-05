package com.focusflow.ui.components;

import com.focusflow.animations.Animator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Holds one child and cross-fades (with a small slide) from the old content to the new when it changes. */
public class FadePane extends JPanel {
    private BufferedImage snap;
    private double p = 1;
    private int dir;

    public FadePane() {
        super(new BorderLayout());
        setOpaque(false);
    }

    public void transition(Runnable change, int direction) {
        if (Animator.reduceMotion || getWidth() <= 0 || getHeight() <= 0 || !isShowing()) {
            change.run();
            revalidate();
            repaint();
            return;
        }
        double sc = 1;
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc != null) sc = gc.getDefaultTransform().getScaleX();
        snap = null;
        BufferedImage img = new BufferedImage((int) Math.max(1, getWidth() * sc), (int) Math.max(1, getHeight() * sc), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(sc, sc);
        printAll(g);
        g.dispose();
        change.run();
        validate();
        snap = img;
        dir = direction;
        p = 0;
        Animator.tween(240, v -> { p = v; repaint(); }, () -> { snap = null; repaint(); });
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (snap != null && p < 1) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - p)));
            g2.drawImage(snap, (int) (-dir * 18 * p), 0, getWidth(), getHeight(), null);
            g2.dispose();
        }
    }
}
