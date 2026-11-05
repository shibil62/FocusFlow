package com.focusflow.ui.components;

import javax.swing.*;
import java.awt.*;

/** Splits the width between children by weight (e.g. 2:1) and uses the tallest child's height. */
public class Cols extends JPanel {
    public Cols(int gap, double[] weights, JComponent... kids) {
        super(null);
        setOpaque(false);
        for (JComponent k : kids) add(k);
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String n, Component c) {}
            @Override public void removeLayoutComponent(Component c) {}
            @Override public Dimension preferredLayoutSize(Container p) {
                int h = 0, w = 0;
                for (Component c : p.getComponents()) { h = Math.max(h, c.getPreferredSize().height); w += c.getPreferredSize().width; }
                return new Dimension(w + gap * Math.max(0, p.getComponentCount() - 1), h);
            }
            @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(100, preferredLayoutSize(p).height); }
            @Override public void layoutContainer(Container p) {
                int n = p.getComponentCount();
                double total = 0;
                for (int i = 0; i < n; i++) total += weights[i];
                double avail = p.getWidth() - gap * (n - 1);
                double x = 0;
                for (int i = 0; i < n; i++) {
                    double w = avail * weights[i] / total;
                    p.getComponent(i).setBounds((int) Math.round(x), 0, (int) Math.round(w), p.getHeight());
                    x += w + gap;
                }
            }
        });
    }
}
