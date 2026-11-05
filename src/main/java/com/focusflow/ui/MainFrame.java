package com.focusflow.ui;

import com.focusflow.animations.Animator;
import com.focusflow.animations.SpringValue;
import com.focusflow.app.AppContext;
import com.focusflow.database.Database;
import com.focusflow.services.FocusService;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.analytics.AnalyticsView;
import com.focusflow.ui.calendar.CalendarView;
import com.focusflow.ui.components.*;
import com.focusflow.ui.dashboard.DashboardView;
import com.focusflow.ui.dialogs.SearchSheet;
import com.focusflow.ui.dialogs.TaskDialog;
import com.focusflow.ui.focus.FocusView;
import com.focusflow.ui.focus.ImmersivePanel;
import com.focusflow.ui.settings.SettingsView;
import com.focusflow.ui.tasks.TasksView;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.Time;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** The window: sidebar, animated view host, overlays (sheets, toasts, Power Mode) and global shortcuts. */
public class MainFrame extends JFrame {
    private final Sidebar sidebar;
    private final FadePane host = new FadePane();
    private final DashboardView dashboard;
    private final CalendarView calendar;
    private final TasksView tasksView;
    private final FocusView focusView;
    private final AnalyticsView analytics;
    private final SettingsView settingsView;
    private final JComponent[] views;
    private int current = 0;
    private ImmersivePanel immersive;
    private boolean immersiveShown, hiding;
    private int stateBeforeImmersive = NORMAL;
    private boolean trayHintShown;

    public MainFrame() {
        super("FocusFlow");
        AppContext.frame = this;
        setIconImages(java.util.List.of(AppIcon.create(256), AppIcon.create(64), AppIcon.create(32), AppIcon.create(16)));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));

        dashboard = new DashboardView();
        calendar = new CalendarView();
        tasksView = new TasksView();
        focusView = new FocusView();
        analytics = new AnalyticsView();
        settingsView = new SettingsView();
        views = new JComponent[]{dashboard, calendar, tasksView, focusView, analytics, settingsView};

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.bg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        sidebar = new Sidebar(this::navigate, TaskDialog::create, () -> setDark(!Theme.dark),
                AppContext.settings.getBool(SettingsService.SIDEBAR_COLLAPSED, false),
                c -> AppContext.settings.setQuiet(SettingsService.SIDEBAR_COLLAPSED, String.valueOf(c)));
        root.add(sidebar, BorderLayout.WEST);
        host.add(dashboard);
        root.add(host, BorderLayout.CENTER);
        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeRequested(); }
        });
        installShortcuts();
        AppContext.focus.addListener(this::syncImmersive);
        restoreBounds();
    }

    // ------------------------------------------------------------- lifecycle

    public void start() {
        setVisible(true);
        AppContext.tray.install(this);
        AppContext.notifications.start();
        playIntro();
        SwingUtilities.invokeLater(this::syncImmersive);
    }

    private void restoreBounds() {
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1360, scr.width - 80), h = Math.min(840, scr.height - 80);
        setSize(w, h);
        setLocationRelativeTo(null);
        String b = AppContext.settings.get(SettingsService.WINDOW_BOUNDS, "");
        try {
            String[] p = b.split(",");
            if (p.length == 5) {
                Rectangle r = new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                boolean visible = false;
                for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                    if (d.getDefaultConfiguration().getBounds().intersects(r)) visible = true;
                }
                if (visible) setBounds(r);
                if (Integer.parseInt(p[4]) == 1) setExtendedState(MAXIMIZED_BOTH);
            }
        } catch (Exception ignored) { }
        if (AppContext.settings.getBool(SettingsService.START_MAXIMIZED, false)) setExtendedState(MAXIMIZED_BOTH);
    }

    private void saveBounds() {
        boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        Rectangle r = getBounds();
        if (max) {
            String old = AppContext.settings.get(SettingsService.WINDOW_BOUNDS, "");
            String[] p = old.split(",");
            if (p.length == 5) { AppContext.settings.setQuiet(SettingsService.WINDOW_BOUNDS, p[0] + "," + p[1] + "," + p[2] + "," + p[3] + ",1"); return; }
        }
        AppContext.settings.setQuiet(SettingsService.WINDOW_BOUNDS, r.x + "," + r.y + "," + r.width + "," + r.height + "," + (max ? 1 : 0));
    }

    private void closeRequested() {
        if (AppContext.settings.getBool(SettingsService.CLOSE_TO_TRAY, false) && AppContext.tray.isAvailable()) {
            saveBounds();
            setVisible(false);
            if (!trayHintShown) {
                trayHintShown = true;
                AppContext.tray.message("FocusFlow is still running", "Reminders stay active. Use the tray icon to reopen or quit.");
            }
            return;
        }
        quit();
    }

    public void quit() {
        try {
            saveBounds();
            AppContext.tray.remove();
            Database.close();
        } finally {
            System.exit(0);
        }
    }

    public void showFromTray() {
        setVisible(true);
        setExtendedState(getExtendedState() & ~ICONIFIED);
        toFront();
        requestFocus();
    }

    // ------------------------------------------------------------ navigation

    public void navigate(int i) {
        if (i == current) return;
        int dir = i > current ? 1 : -1;
        current = i;
        sidebar.setSelected(i);
        host.transition(() -> {
            host.removeAll();
            host.add(views[i]);
            refreshCurrent();
        }, dir);
    }

    private void refreshCurrent() {
        switch (current) {
            case 0 -> dashboard.refresh();
            case 1 -> calendar.refresh();
            case 2 -> tasksView.refresh();
            case 3 -> focusView.refresh();
            case 4 -> analytics.refresh();
            default -> { }
        }
    }

    public void openTasks(int filter) {
        navigate(2);
        tasksView.setFilter(filter);
    }

    public void startFocusFor(long taskId) {
        navigate(3);
        focusView.selectTask(taskId);
    }

    // ------------------------------------------------------------ shortcuts

    private void installShortcuts() {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        key(KeyEvent.VK_N, ctrl, "new", TaskDialog::create);
        key(KeyEvent.VK_K, ctrl, "search", SearchSheet::open);
        key(KeyEvent.VK_T, ctrl, "today", () -> { navigate(1); calendar.goToday(); });
        key(KeyEvent.VK_D, ctrl, "dark", () -> setDark(!Theme.dark));
        key(KeyEvent.VK_B, ctrl, "side", () -> sidebar.setCollapsed(!sidebar.isCollapsed(), true));
        key(KeyEvent.VK_COMMA, ctrl, "settings", () -> navigate(5));
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            key(KeyEvent.VK_1 + i, ctrl, "nav" + i, () -> navigate(idx));
        }
        key(KeyEvent.VK_Z, ctrl, "undo", () -> {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
            String label = AppContext.tasks.undoLast();
            Toast.show(label == null ? "Nothing to undo" : "Undid: " + label.toLowerCase());
        });
    }

    private void key(int code, int mods, String name, Runnable r) {
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, mods), name);
        rp.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (Sheet.isOpen() || immersiveShown) return;
                r.run();
            }
        });
    }

    // -------------------------------------------------------------- theming

    public void setDark(boolean dark) {
        if (dark == Theme.dark) return;
        JLayeredPane lp = getLayeredPane();
        BufferedImage old = null;
        if (!Animator.reduceMotion && lp.getWidth() > 0) {
            double sc = 1;
            GraphicsConfiguration gc = getGraphicsConfiguration();
            if (gc != null) sc = gc.getDefaultTransform().getScaleX();
            old = new BufferedImage((int) (lp.getWidth() * sc), (int) (lp.getHeight() * sc), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = old.createGraphics();
            g.scale(sc, sc);
            lp.printAll(g);
            g.dispose();
        }
        AppContext.settings.setQuiet(SettingsService.THEME, dark ? "dark" : "light");
        Theme.apply(dark);
        FlatLaf.updateUI();
        refreshCurrent();
        repaint();
        if (old == null) return;
        BufferedImage snap = old;
        double[] p = {0};
        JComponent veil = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - p[0])));
                g2.drawImage(snap, 0, 0, getWidth(), getHeight(), null);
                g2.dispose();
            }
        };
        veil.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(veil, Integer.valueOf(450));
        Animator.tween(340, v -> { p[0] = v; veil.repaint(); }, () -> { lp.remove(veil); lp.repaint(); });
    }

    /** Re-applies stored preferences after a backup was restored. */
    public void afterRestore() {
        Animator.reduceMotion = AppContext.settings.getBool(SettingsService.REDUCE_MOTION, false);
        boolean dark = "dark".equals(AppContext.settings.get(SettingsService.THEME, "light"));
        if (dark != Theme.dark) setDark(dark);
        sidebar.setCollapsed(AppContext.settings.getBool(SettingsService.SIDEBAR_COLLAPSED, false), false);
        refreshCurrent();
    }

    // ------------------------------------------------------------ Power Mode

    private void syncImmersive() {
        FocusService.State st = AppContext.focus.state();
        if (st != FocusService.State.IDLE && !immersiveShown) showImmersive();
        else if (st == FocusService.State.IDLE && immersiveShown) hideImmersive();
    }

    public void showImmersive() {
        if (immersiveShown || !isShowing()) return;
        immersiveShown = true;
        hiding = false;
        immersive = new ImmersivePanel();
        JLayeredPane lp = getLayeredPane();
        immersive.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (immersive != null) immersive.setBounds(0, 0, lp.getWidth(), lp.getHeight());
            }
        });
        lp.add(immersive, Integer.valueOf(150));
        stateBeforeImmersive = getExtendedState();
        setExtendedState(MAXIMIZED_BOTH);
        immersive.appear();
        immersive.requestFocusInWindow();
    }

    public void hideImmersive() {
        if (!immersiveShown || hiding) return;
        hiding = true;
        ImmersivePanel p = immersive;
        p.disappear(() -> {
            getLayeredPane().remove(p);
            getLayeredPane().repaint();
            immersive = null;
            immersiveShown = false;
            hiding = false;
            setExtendedState(stateBeforeImmersive);
            focusView.refresh();
            if (AppContext.focus.state() != FocusService.State.IDLE) syncImmersive();
        });
    }

    // ----------------------------------------------------------------- intro

    private void playIntro() {
        JLayeredPane lp = getLayeredPane();
        BufferedImage icon = AppIcon.create(160);
        double[] p = {0};
        JComponent intro = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                float a = (float) Math.max(0, 1 - p[0]);
                g2.setComposite(AlphaComposite.SrcOver.derive(a));
                g2.setColor(Theme.bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                double s = 1 + p[0] * 0.25;
                int sz = (int) (96 * s);
                g2.drawImage(icon, (getWidth() - sz) / 2, (getHeight() - sz) / 2 - 16, sz, sz, null);
                Gfx.textCentered(g2, "FocusFlow", getWidth() / 2.0, getHeight() / 2.0 + 62, Theme.semi(22f), Theme.text);
                g2.dispose();
            }
        };
        intro.addMouseListener(new MouseAdapter() {});
        intro.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(intro, Integer.valueOf(500));
        Timer hold = new Timer(Animator.reduceMotion ? 0 : 450, e -> Animator.tween(500, v -> { p[0] = v; intro.repaint(); },
                () -> { lp.remove(intro); lp.repaint(); }));
        hold.setRepeats(false);
        hold.start();
    }
}
