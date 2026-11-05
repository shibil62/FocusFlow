package com.focusflow.ui.settings;

import com.focusflow.app.AppContext;
import com.focusflow.config.AppPaths;
import com.focusflow.models.Category;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.utilities.Gfx;
import com.focusflow.utilities.StartupRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Consumer;

/** Preferences, deadline thresholds, categories and data management. */
public class SettingsView extends JPanel {
    private final SettingsService s = AppContext.settings;
    private final VStack catList = new VStack();

    public SettingsView() {
        super(new BorderLayout());
        setOpaque(false);
        VStack page = new VStack();
        page.setBorder(new EmptyBorder(26, 28, 30, 28));
        page.add(Lbl.title("Settings"), 0);

        page.add(group("Appearance",
                row("Theme", "Light or dark. Switches smoothly.", new Segmented(new String[]{"Light", "Dark"}, Theme.dark ? 1 : 0,
                        i -> AppContext.frame.setDark(i == 1))),
                row("Reduce motion", "Replaces slides and springs with quick fades.", toggle(SettingsService.REDUCE_MOTION, false, v -> com.focusflow.animations.Animator.reduceMotion = v))), 16);

        page.add(group("Deadline intelligence",
                note("An urgency level is picked from the time left. The score also weighs workload and priority, and can raise the level."),
                row("Approaching begins", "Days before a deadline", number(SettingsService.APPROACHING_DAYS, 7, 2, 60, 1, "' days'")),
                row("Urgent begins", "Hours before a deadline", number(SettingsService.URGENT_HOURS, 72, 2, 400, 1, "' h'")),
                row("Critical begins", "Hours before a deadline", number(SettingsService.CRITICAL_HOURS, 24, 1, 200, 1, "' h'"))), 16);

        Segmented ws = new Segmented(new String[]{"Monday", "Sunday"}, "SUNDAY".equals(s.get(SettingsService.WEEK_START, "MONDAY")) ? 1 : 0,
                i -> s.set(SettingsService.WEEK_START, i == 1 ? "SUNDAY" : "MONDAY"));
        page.add(group("Calendar",
                row("Week starts on", "", ws),
                row("Default task length", "Used when you click an empty slot", number(SettingsService.DEFAULT_DURATION, 60, 15, 480, 15, "0' min'"))), 16);

        page.add(group("Notifications",
                row("Notifications", "Master switch for reminders", toggle(SettingsService.NOTIFY_ENABLED, true, null)),
                row("Approaching deadlines", "When a task becomes critical", toggle(SettingsService.NOTIFY_DEADLINES, true, null)),
                row("Scheduled start", "Shortly before a scheduled task begins", toggle(SettingsService.NOTIFY_START, true, null)),
                row("Reminder lead time", "Minutes before the start", number(SettingsService.NOTIFY_LEAD, 10, 1, 120, 5, "0' min'")),
                row("Overdue tasks", "A daily summary and tasks that just became overdue", toggle(SettingsService.NOTIFY_OVERDUE, true, null)),
                row("Focus timer", "When a Power Mode session ends", toggle(SettingsService.NOTIFY_FOCUS, true, null)),
                row("Daily summary", "Around 8 PM", toggle(SettingsService.NOTIFY_DAILY, false, null)),
                row("Weekly summary", "Sunday evening", toggle(SettingsService.NOTIFY_WEEKLY, false, null))), 16);

        page.add(categoriesCard(), 16);

        Btn backup = new Btn("Back up now", Btn.Kind.TINTED, Icons.Type.DOWNLOAD);
        backup.addActionListener(e -> backupNow());
        Btn restore = new Btn("Restore…", Btn.Kind.TINTED, Icons.Type.UPLOAD);
        restore.addActionListener(e -> restore());
        Btn csv = new Btn("Export CSV", Btn.Kind.TINTED);
        csv.addActionListener(e -> export(true));
        Btn json = new Btn("Export JSON", Btn.Kind.TINTED);
        json.addActionListener(e -> export(false));
        Btn open = new Btn("Open folder", Btn.Kind.TINTED, Icons.Type.FOLDER);
        open.addActionListener(e -> {
            try { Desktop.getDesktop().open(AppPaths.dataDir().toFile()); } catch (Exception ex) { Toast.show("Could not open the folder."); }
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        for (Btn b : new Btn[]{backup, restore, csv, json, open}) btns.add(b);
        page.add(group("Data and backup",
                note("Everything is stored on this computer in " + AppPaths.dataDir() + ". A backup is also made automatically once a day."),
                btns,
                note("Restoring first saves a copy of your current data, so nothing is lost by accident.")), 16);

        page.add(group("System",
                row("Launch at Windows startup", StartupRegistry.supported() ? "Opens FocusFlow when you sign in" : "Only available on Windows",
                        startupToggle()),
                row("Keep running in the tray", "Closing the window hides it so reminders keep working", toggle(SettingsService.CLOSE_TO_TRAY, false, null)),
                row("Start maximized", "", toggle(SettingsService.START_MAXIMIZED, false, null))), 16);

        page.add(group("Keyboard shortcuts",
                note("Ctrl+N new task    Ctrl+K search    Ctrl+T today    Ctrl+Z undo    Ctrl+D dark mode    Ctrl+B sidebar\n"
                        + "Ctrl+1 to 6 switch sections    Ctrl+, settings    Left/Right arrows move around the calendar    Space pauses the timer")), 16);
        page.add(note("FocusFlow 1.0  ·  Local-first. No account, no cloud, no tracking."), 18);

        add(Refresher.scroll(page), BorderLayout.CENTER);
        Refresher.install(this, this::rebuildCategories, AppContext.categories);
        rebuildCategories();
    }

    // ------------------------------------------------------------- builders

    private Card group(String title, JComponent... rows) {
        Card c = new Card(20, new BorderLayout(0, 8));
        c.add(Lbl.h2(title), BorderLayout.NORTH);
        VStack v = new VStack();
        boolean first = true;
        for (JComponent r : rows) { v.add(r, first ? 0 : 6); first = false; }
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JComponent note(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
        Lbl l = new Lbl("<html><div style='width:640px'>" + safe + "</div></html>", Theme.font(12f), Lbl.Role.TEXT2);
        return l;
    }

    private JComponent row(String title, String sub, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 0, 6, 0));
        JPanel t = new JPanel(new GridLayout(sub.isEmpty() ? 1 : 2, 1));
        t.setOpaque(false);
        t.add(new Lbl(title, Theme.semi(14f), Lbl.Role.TEXT));
        if (!sub.isEmpty()) t.add(Lbl.caption(sub));
        p.add(t, BorderLayout.CENTER);
        JPanel c = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        c.setOpaque(false);
        c.add(control);
        p.add(c, BorderLayout.EAST);
        return p;
    }

    private Toggle toggle(String key, boolean def, Consumer<Boolean> extra) {
        return new Toggle(s.getBool(key, def), v -> {
            s.set(key, v);
            if (extra != null) extra.accept(v);
        });
    }

    private JSpinner number(String key, int def, int min, int max, int step, String fmt) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(s.getInt(key, def), min, max, step));
        sp.setEditor(new JSpinner.NumberEditor(sp, fmt));
        sp.setPreferredSize(new Dimension(110, 34));
        sp.addChangeListener(e -> {
            int v = (Integer) sp.getValue();
            int ap = key.equals(SettingsService.APPROACHING_DAYS) ? v : s.getInt(SettingsService.APPROACHING_DAYS, 7);
            int ur = key.equals(SettingsService.URGENT_HOURS) ? v : s.getInt(SettingsService.URGENT_HOURS, 72);
            int cr = key.equals(SettingsService.CRITICAL_HOURS) ? v : s.getInt(SettingsService.CRITICAL_HOURS, 24);
            if (key.startsWith("thr.") && !(ap * 24 > ur && ur > cr)) {
                Toast.show("Thresholds must shrink: approaching > urgent > critical.");
                sp.setValue(s.getInt(key, def));
                return;
            }
            s.set(key, v);
        });
        return sp;
    }

    private Toggle startupToggle() {
        Toggle t = new Toggle(s.getBool(SettingsService.LAUNCH_ON_STARTUP, false), null);
        t.setEnabled(StartupRegistry.supported());
        Toggle[] ref = {t};
        return new Toggle(t.isOn(), v -> new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { StartupRegistry.set(v); return null; }
            @Override protected void done() {
                try {
                    get();
                    s.set(SettingsService.LAUNCH_ON_STARTUP, v);
                    Toast.show(v ? "FocusFlow will start with Windows." : "Startup entry removed.");
                } catch (Exception ex) {
                    Toast.show("Could not change the startup setting.");
                }
            }
        }.execute()) {{ setEnabled(StartupRegistry.supported()); }};
    }

    // ------------------------------------------------------------ categories

    private Card categoriesCard() {
        Card c = new Card(20, new BorderLayout(0, 8));
        c.add(Lbl.h2("Categories"), BorderLayout.NORTH);
        c.add(catList, BorderLayout.CENTER);
        return c;
    }

    private void rebuildCategories() {
        catList.clear();
        for (Category cat : AppContext.categories.all()) catList.add(new CatRow(cat), 6);
        JTextField name = new JTextField();
        name.putClientProperty("JTextField.placeholderText", "New category name");
        Color[] chosen = {new Color(0x0A84FF)};
        Swatch sw = new Swatch(chosen[0], col -> chosen[0] = col);
        Btn add = new Btn("Add", Btn.Kind.PRIMARY, Icons.Type.PLUS);
        Runnable doAdd = () -> {
            String n = name.getText().trim();
            if (n.isEmpty()) return;
            try {
                AppContext.categories.add(n, String.format("#%06X", chosen[0].getRGB() & 0xFFFFFF));
                Toast.show("Category added");
            } catch (RuntimeException ex) {
                Toast.show("A category with that name already exists.");
            }
        };
        add.addActionListener(e -> doAdd.run());
        name.addActionListener(e -> doAdd.run());
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 0, 0, 0));
        p.add(sw, BorderLayout.WEST);
        p.add(name, BorderLayout.CENTER);
        p.add(add, BorderLayout.EAST);
        catList.add(p, 8);
        catList.revalidate();
        catList.repaint();
    }

    private static final class Swatch extends JComponent {
        private Color color;
        private final Consumer<Color> onPick;

        Swatch(Color c, Consumer<Color> onPick) {
            this.color = c;
            this.onPick = onPick;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Choose a colour");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    Color n = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(Swatch.this), "Category colour", color);
                    if (n != null) { color = n; onPick.accept(n); repaint(); }
                }
            });
        }

        void setColor(Color c) { color = c; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(34, 34); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setColor(color);
            g2.fill(new java.awt.geom.Ellipse2D.Double(5, 5, 24, 24));
            g2.setColor(Gfx.alpha(Color.BLACK, 0.12));
            g2.draw(new java.awt.geom.Ellipse2D.Double(5, 5, 24, 24));
            g2.dispose();
        }
    }

    private static final class CatRow extends JPanel {
        CatRow(Category cat) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            JTextField name = new JTextField(cat.name);
            Swatch sw = new Swatch(cat.awt(), col -> {
                cat.color = String.format("#%06X", col.getRGB() & 0xFFFFFF);
                AppContext.categories.update(cat);
            });
            Runnable commit = () -> {
                String n = name.getText().trim();
                if (n.isEmpty() || n.equals(cat.name)) { name.setText(cat.name); return; }
                String old = cat.name;
                cat.name = n;
                try { AppContext.categories.update(cat); } catch (RuntimeException ex) {
                    cat.name = old;
                    name.setText(old);
                    Toast.show("A category with that name already exists.");
                }
            };
            name.addActionListener(e -> commit.run());
            name.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { commit.run(); } });
            Btn del = Btn.icon(Icons.Type.TRASH, Btn.Kind.GHOST, "Delete category");
            del.addActionListener(e -> Sheet.confirm("Delete “" + cat.name + "”?", "Tasks in this category are kept and become uncategorised.", "Delete", true,
                    () -> AppContext.categories.delete(cat.id)));
            add(sw, BorderLayout.WEST);
            add(name, BorderLayout.CENTER);
            add(del, BorderLayout.EAST);
        }
    }

    // ----------------------------------------------------------------- data

    private void backupNow() {
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() { return AppContext.backup.backupNow(); }
            @Override protected void done() {
                try { Toast.show("Backup saved: " + get().getFileName()); } catch (Exception e) { Toast.show("Backup failed: " + e.getCause().getMessage()); }
            }
        }.execute();
    }

    private void restore() {
        JFileChooser fc = new JFileChooser(AppPaths.backupDir().toFile());
        fc.setDialogTitle("Choose a FocusFlow backup");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("FocusFlow backup (*.db)", "db"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        Sheet.confirm("Restore this backup?", "Your current data is replaced by “" + f.getName() + "”. A safety copy of what you have now is saved first.",
                "Restore", false, () -> new SwingWorker<Path, Void>() {
                    @Override protected Path doInBackground() throws Exception { return AppContext.backup.restore(f.toPath()); }
                    @Override protected void done() {
                        try {
                            get();
                            AppContext.frame.afterRestore();
                            Toast.show("Backup restored");
                        } catch (Exception e) {
                            Toast.show("Restore failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                        }
                    }
                }.execute());
    }

    private void export(boolean csv) {
        JFileChooser fc = new JFileChooser(AppPaths.exportDir().toFile());
        fc.setSelectedFile(new File(AppPaths.exportDir().toFile(), "focusflow-tasks-" + LocalDate.now() + (csv ? ".csv" : ".json")));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = fc.getSelectedFile().toPath();
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                return csv ? AppContext.backup.exportCsv(target) : AppContext.backup.exportJson(target);
            }
            @Override protected void done() {
                try { Toast.show("Exported to " + get().getFileName()); } catch (Exception e) { Toast.show("Export failed."); }
            }
        }.execute();
    }
}
