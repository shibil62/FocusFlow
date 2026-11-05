package com.focusflow.ui.focus;

import com.focusflow.app.AppContext;
import com.focusflow.models.FocusSession;
import com.focusflow.models.Task;
import com.focusflow.services.SettingsService;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.utilities.Time;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Power Mode setup: pick a duration and optionally a task, then start the immersive timer. */
public class FocusView extends JPanel {
    private static final int[] PRESETS = {15, 25, 30, 45, 60, 90};

    private final Segmented presets;
    private JSpinner minutes;
    private final JComboBox<Object> taskBox = new JComboBox<>();
    private final VStack history = new VStack();
    private final Lbl todayLine = Lbl.sub("");
    private final List<Task> choices = new ArrayList<>();
    private boolean syncing;
    private Long preselect;

    public FocusView() {
        super(new BorderLayout());
        setOpaque(false);
        int last = AppContext.settings.getInt(SettingsService.FOCUS_LAST_MINUTES, 25);
        int idx = 1;
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == last) idx = i;
        presets = new Segmented(new String[]{"15", "25", "30", "45", "60", "90"}, idx, i -> {
            syncing = true;
            minutes.setValue(PRESETS[i]);
            syncing = false;
        });
        minutes = new JSpinner(new SpinnerNumberModel(last, 1, 600, 5));
        minutes.setEditor(new JSpinner.NumberEditor(minutes, "0' min'"));
        minutes.addChangeListener(e -> {
            if (syncing) return;
            int v = (Integer) minutes.getValue();
            for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == v) { presets.setSelected(i); return; }
        });

        VStack page = new VStack();
        page.setBorder(new EmptyBorder(26, 28, 30, 28));
        page.add(Lbl.title("Power Mode"), 0);
        page.add(Lbl.sub("One task, one timer, nothing else on screen."), 2);

        Card card = new Card(24, new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0; card.add(Lbl.h2("Duration"), g);
        g.gridy = 1; g.insets = new Insets(10, 0, 0, 0);
        JPanel pr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pr.setOpaque(false);
        pr.add(presets);
        card.add(pr, g);
        g.gridy = 2; g.insets = new Insets(10, 0, 0, 0);
        JPanel cm = new JPanel(new BorderLayout(10, 0));
        cm.setOpaque(false);
        cm.add(Lbl.sub("Custom"), BorderLayout.WEST);
        minutes.setPreferredSize(new Dimension(120, 36));
        JPanel mw = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        mw.setOpaque(false);
        mw.add(minutes);
        cm.add(mw, BorderLayout.CENTER);
        card.add(cm, g);
        g.gridy = 3; g.insets = new Insets(22, 0, 0, 0); card.add(Lbl.h2("Focus on"), g);
        g.gridy = 4; g.insets = new Insets(10, 0, 0, 0);
        taskBox.setPreferredSize(new Dimension(100, 38));
        card.add(taskBox, g);
        g.gridy = 5; g.insets = new Insets(8, 0, 0, 0);
        card.add(Lbl.caption("Starting a session never completes the task for you."), g);
        Btn start = new Btn("Start focus session", Btn.Kind.PRIMARY, Icons.Type.PLAY);
        start.setPreferredSize(new Dimension(100, 46));
        start.addActionListener(e -> begin());
        g.gridy = 6; g.insets = new Insets(22, 0, 0, 0); card.add(start, g);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        center.setOpaque(false);
        card.setPreferredSize(new Dimension(520, 380));
        center.add(card);
        page.add(center, 18);

        page.add(Lbl.h2("Recent sessions"), 26);
        page.add(todayLine, 2);
        history.setBorder(new EmptyBorder(0, 0, 0, 0));
        page.add(history, 8);
        add(Refresher.scroll(page), BorderLayout.CENTER);
        Refresher.install(this, this::refresh, AppContext.tasks, AppContext.focus);
        refresh();
    }

    public void selectTask(long id) {
        preselect = id;
        refresh();
    }

    private void begin() {
        Long id = null;
        int i = taskBox.getSelectedIndex();
        if (i > 0 && i <= choices.size()) id = choices.get(i - 1).id;
        AppContext.focus.start((Integer) minutes.getValue(), id);
        AppContext.frame.showImmersive();
    }

    public void refresh() {
        Object sel = taskBox.getSelectedIndex() > 0 && taskBox.getSelectedIndex() <= choices.size() ? choices.get(taskBox.getSelectedIndex() - 1).id : null;
        if (preselect != null) { sel = preselect; preselect = null; }
        choices.clear();
        for (Task t : AppContext.tasks.all()) if (!t.isCompleted()) choices.add(t);
        choices.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        taskBox.removeAllItems();
        taskBox.addItem("No task (free focus)");
        int pick = 0;
        for (int i = 0; i < choices.size(); i++) {
            taskBox.addItem(choices.get(i).title);
            if (sel != null && sel.equals(choices.get(i).id)) pick = i + 1;
        }
        taskBox.setSelectedIndex(pick);

        long today = AppContext.analytics.today().focusSeconds();
        todayLine.setText(today == 0 ? "No focus time yet today." : Time.seconds(today) + " focused today.");
        history.clear();
        List<FocusSession> all = AppContext.focus.sessions();
        if (all.isEmpty()) {
            history.add(new EmptyState(Icons.Type.FOCUS, "No sessions yet", "Your finished sessions will appear here.", 150), 0);
        }
        for (int i = 0; i < Math.min(8, all.size()); i++) history.add(new SessionRow(all.get(i)), i == 0 ? 0 : 6);
        history.revalidate();
        history.repaint();
    }

    /** One finished or ended session. */
    public static final class SessionRow extends JComponent {
        private final FocusSession s;

        public SessionRow(FocusSession s) { this.s = s; }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 48); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = com.focusflow.utilities.Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.surface);
            g2.fill(com.focusflow.utilities.Gfx.rr(0.5, 0.5, w - 1, h - 1, 14));
            g2.setColor(Theme.separator);
            g2.draw(com.focusflow.utilities.Gfx.rr(0.5, 0.5, w - 1, h - 1, 14));
            Color c = s.completed ? Theme.success : Theme.text3;
            Icons.paint(g2, s.completed ? Icons.Type.CHECK : Icons.Type.CLOCK, 16, h / 2.0 - 9, 18, c);
            Task t = s.taskId == null ? null : AppContext.tasks.get(s.taskId);
            String name = t != null ? t.title : "Free focus";
            var dt = Time.dt(s.startedAt);
            FontMetrics fm = g2.getFontMetrics(Theme.semi(13f));
            String dur = Time.seconds(s.actualSeconds);
            int dw = fm.stringWidth(dur);
            com.focusflow.utilities.Gfx.text(g2, com.focusflow.utilities.Gfx.fit(name, fm, w - dw - 160), 46, 21, Theme.semi(13f), Theme.text);
            com.focusflow.utilities.Gfx.text(g2, Time.relativeDay(dt.toLocalDate()) + ", " + Time.time(dt) + (s.completed ? "" : " · ended early"), 46, 38, Theme.font(11f), Theme.text2);
            com.focusflow.utilities.Gfx.text(g2, dur, w - 16 - dw, h / 2 + 5, Theme.semi(13f), Theme.text);
            g2.dispose();
        }
    }
}
