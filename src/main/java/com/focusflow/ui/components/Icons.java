package com.focusflow.ui.components;

import com.focusflow.utilities.Gfx;

import java.awt.*;
import java.awt.geom.*;

/** Vector icons drawn with Java2D on a 24 unit grid, so they stay crisp at any scale and in both themes. */
public final class Icons {
    private Icons() {}

    public enum Type {
        DASHBOARD, CALENDAR, TASKS, FOCUS, ANALYTICS, SETTINGS, PLUS, CHECK, CHEV_L, CHEV_R, CHEV_D, SEARCH, TRASH,
        CLOCK, WARNING, PLAY, PAUSE, STOP, SUN, MOON, CLOSE, EDIT, FLAG, SIDEBAR, MORE, UNDO, REPEAT, FLAME, RESET,
        ARCHIVE, DOWNLOAD, UPLOAD, FOLDER
    }

    public static void paint(Graphics g0, Type t, double x, double y, double size, Color c) {
        Graphics2D g = Gfx.aa(g0);
        g.translate(x, y);
        double s = size / 24.0;
        g.scale(s, s);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (t) {
            case DASHBOARD -> {
                g.draw(Gfx.rr(3.5, 3.5, 7, 7, 2.5));
                g.draw(Gfx.rr(13.5, 3.5, 7, 7, 2.5));
                g.draw(Gfx.rr(3.5, 13.5, 7, 7, 2.5));
                g.draw(Gfx.rr(13.5, 13.5, 7, 7, 2.5));
            }
            case CALENDAR -> {
                g.draw(Gfx.rr(3.5, 5, 17, 15.5, 4));
                g.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                g.draw(new Line2D.Double(8, 3, 8, 6.5));
                g.draw(new Line2D.Double(16, 3, 16, 6.5));
            }
            case TASKS -> {
                g.draw(new Line2D.Double(9.5, 7, 20, 7));
                g.draw(new Line2D.Double(9.5, 12, 20, 12));
                g.draw(new Line2D.Double(9.5, 17, 20, 17));
                g.fill(new Ellipse2D.Double(3.2, 5.7, 2.6, 2.6));
                g.fill(new Ellipse2D.Double(3.2, 10.7, 2.6, 2.6));
                g.fill(new Ellipse2D.Double(3.2, 15.7, 2.6, 2.6));
            }
            case FOCUS -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.fill(new Ellipse2D.Double(9.5, 9.5, 5, 5));
            }
            case ANALYTICS -> {
                g.draw(new Line2D.Double(6, 19, 6, 12));
                g.draw(new Line2D.Double(12, 19, 12, 5));
                g.draw(new Line2D.Double(18, 19, 18, 9));
            }
            case SETTINGS -> {
                g.draw(new Line2D.Double(4, 8, 7, 8));
                g.draw(new Ellipse2D.Double(7, 5.5, 5, 5));
                g.draw(new Line2D.Double(12, 8, 20, 8));
                g.draw(new Line2D.Double(4, 16, 12.5, 16));
                g.draw(new Ellipse2D.Double(12.5, 13.5, 5, 5));
                g.draw(new Line2D.Double(17.5, 16, 20, 16));
            }
            case PLUS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case CHECK -> g.draw(poly(false, 5, 12.5, 10, 17.5, 19, 7));
            case CHEV_L -> g.draw(poly(false, 14.5, 6, 8.5, 12, 14.5, 18));
            case CHEV_R -> g.draw(poly(false, 9.5, 6, 15.5, 12, 9.5, 18));
            case CHEV_D -> g.draw(poly(false, 6, 9.5, 12, 15.5, 18, 9.5));
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4, 4, 13, 13));
                g.draw(new Line2D.Double(15.5, 15.5, 20, 20));
            }
            case TRASH -> {
                g.draw(new Line2D.Double(4.5, 7, 19.5, 7));
                g.draw(poly(false, 9.5, 7, 9.5, 4.5, 14.5, 4.5, 14.5, 7));
                g.draw(poly(false, 6.5, 7, 7.5, 19.5, 16.5, 19.5, 17.5, 7));
                g.draw(new Line2D.Double(10.2, 10.5, 10.2, 16));
                g.draw(new Line2D.Double(13.8, 10.5, 13.8, 16));
            }
            case CLOCK -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.draw(poly(false, 12, 7.5, 12, 12, 15.5, 14));
            }
            case WARNING -> {
                g.draw(poly(true, 12, 4, 21, 19.5, 3, 19.5));
                g.draw(new Line2D.Double(12, 9.5, 12, 14));
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
            }
            case PLAY -> g.fill(poly(true, 8, 5.5, 19, 12, 8, 18.5));
            case PAUSE -> {
                g.fill(Gfx.rr(6.5, 5, 4, 14, 1.5));
                g.fill(Gfx.rr(13.5, 5, 4, 14, 1.5));
            }
            case STOP -> g.fill(Gfx.rr(6, 6, 12, 12, 3));
            case SUN -> {
                g.draw(new Ellipse2D.Double(8, 8, 8, 8));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(12 + 6.8 * Math.cos(a), 12 + 6.8 * Math.sin(a), 12 + 9.2 * Math.cos(a), 12 + 9.2 * Math.sin(a)));
                }
            }
            case MOON -> {
                Area a = new Area(new Ellipse2D.Double(4.5, 4, 15, 15));
                a.subtract(new Area(new Ellipse2D.Double(9.5, 1.5, 13, 13)));
                g.fill(a);
            }
            case CLOSE -> {
                g.draw(new Line2D.Double(6, 6, 18, 18));
                g.draw(new Line2D.Double(18, 6, 6, 18));
            }
            case EDIT -> {
                g.draw(poly(true, 5, 19, 5.8, 15.2, 16, 5, 19, 8, 8.8, 18.2));
                g.draw(new Line2D.Double(14, 7, 17, 10));
            }
            case FLAG -> {
                g.draw(new Line2D.Double(6, 4, 6, 20));
                g.draw(poly(false, 6, 5, 18, 5, 15, 9, 18, 13, 6, 13));
            }
            case SIDEBAR -> {
                g.draw(Gfx.rr(3.5, 4.5, 17, 15, 4));
                g.draw(new Line2D.Double(9.5, 4.5, 9.5, 19.5));
            }
            case MORE -> {
                g.fill(new Ellipse2D.Double(4.4, 10.4, 3.2, 3.2));
                g.fill(new Ellipse2D.Double(10.4, 10.4, 3.2, 3.2));
                g.fill(new Ellipse2D.Double(16.4, 10.4, 3.2, 3.2));
            }
            case UNDO -> {
                g.draw(poly(false, 8, 6, 4, 10.5, 8, 15));
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 10.5);
                p.lineTo(14, 10.5);
                p.quadTo(20, 10.5, 20, 15.5);
                p.quadTo(20, 20, 14, 20);
                p.lineTo(9, 20);
                g.draw(p);
            }
            case REPEAT -> {
                Path2D a = new Path2D.Double();
                a.moveTo(4, 12); a.lineTo(4, 10); a.quadTo(4, 7, 7, 7); a.lineTo(20, 7);
                g.draw(a);
                Path2D b = new Path2D.Double();
                b.moveTo(20, 12); b.lineTo(20, 14); b.quadTo(20, 17, 17, 17); b.lineTo(4, 17);
                g.draw(b);
                g.draw(poly(false, 17, 4, 20, 7, 17, 10));
                g.draw(poly(false, 7, 14, 4, 17, 7, 20));
            }
            case FLAME -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.curveTo(12.5, 8, 18, 9.5, 18, 15);
                p.curveTo(18, 18.5, 15.3, 21, 12, 21);
                p.curveTo(8.7, 21, 6, 18.5, 6, 15);
                p.curveTo(6, 12, 8, 10.5, 9, 8);
                p.curveTo(10, 9.5, 11.3, 9, 12, 3);
                p.closePath();
                g.fill(p);
            }
            case RESET -> {
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 90, 300, Arc2D.OPEN));
                g.draw(poly(false, 15, 2, 12, 4.5, 15, 7));
            }
            case ARCHIVE -> {
                g.draw(Gfx.rr(3.5, 4, 17, 4.5, 1.5));
                g.draw(Gfx.rr(5, 8.5, 14, 11, 2));
                g.draw(new Line2D.Double(10, 12.5, 14, 12.5));
            }
            case DOWNLOAD -> {
                g.draw(new Line2D.Double(12, 4, 12, 15));
                g.draw(poly(false, 7.5, 10.5, 12, 15, 16.5, 10.5));
                g.draw(new Line2D.Double(5, 19.5, 19, 19.5));
            }
            case UPLOAD -> {
                g.draw(new Line2D.Double(12, 16, 12, 5));
                g.draw(poly(false, 7.5, 9.5, 12, 5, 16.5, 9.5));
                g.draw(new Line2D.Double(5, 19.5, 19, 19.5));
            }
            case FOLDER -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 7.5); p.quadTo(3.5, 5.5, 5.5, 5.5); p.lineTo(9.5, 5.5); p.lineTo(11.5, 8);
                p.lineTo(18.5, 8); p.quadTo(20.5, 8, 20.5, 10); p.lineTo(20.5, 17.5); p.quadTo(20.5, 19.5, 18.5, 19.5);
                p.lineTo(5.5, 19.5); p.quadTo(3.5, 19.5, 3.5, 17.5); p.closePath();
                g.draw(p);
            }
        }
        g.dispose();
    }

    private static Path2D poly(boolean close, double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        if (close) p.closePath();
        return p;
    }
}
