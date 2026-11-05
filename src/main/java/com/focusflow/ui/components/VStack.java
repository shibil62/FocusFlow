package com.focusflow.ui.components;

import javax.swing.*;
import java.awt.*;

/** Vertical stack: children fill the width at their preferred height. Tracks the viewport width in scroll panes. */
public class VStack extends JPanel implements Scrollable {
    private int row;

    public VStack() {
        super(new GridBagLayout());
        setOpaque(false);
        GridBagConstraints f = new GridBagConstraints();
        f.gridy = 100000;
        f.weighty = 1;
        add(Box.createGlue(), f);
    }

    public VStack add(JComponent c, int gapBefore) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row++;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH;
        g.insets = new Insets(gapBefore, 0, 0, 0);
        add(c, g);
        return this;
    }

    public void clear() {
        for (Component c : getComponents()) {
            GridBagConstraints g = ((GridBagLayout) getLayout()).getConstraints(c);
            if (g.gridy != 100000) remove(c);
        }
        row = 0;
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(120, r.height - 60); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
