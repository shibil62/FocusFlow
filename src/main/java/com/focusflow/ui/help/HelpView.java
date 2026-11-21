package com.focusflow.ui.help;

import com.focusflow.app.AppContext;
import com.focusflow.app.AppInfo;
import com.focusflow.config.AppPaths;
import com.focusflow.ui.Theme;
import com.focusflow.ui.components.*;
import com.focusflow.utilities.Gfx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Help and About: version, who made it, and a plain-language guide to every feature. */
public class HelpView extends JPanel {

    public HelpView() {
        super(new BorderLayout());
        setOpaque(false);
        VStack page = new VStack();
        page.setBorder(new EmptyBorder(26, 28, 30, 28));
        page.add(Lbl.title("Help"), 0);

        page.add(aboutCard(), 16);

        page.add(group("Getting started",
                text("<b>1. Add a task.</b> Press <b>Ctrl+N</b> or click <b>New task</b>. Only the title is needed. Everything else is optional."),
                text("<b>2. Pick what fits.</b> You can plan <i>when you will work</i> on it (Schedule), say <i>when it must be finished</i> (Deadline), or both."),
                text("<b>3. Tick it off.</b> Click the circle in front of a task when it is done. A finished task stops showing on the days after the day you finished it."),
                text("<b>4. Check the dashboard.</b> It shows what is planned today, what is overdue and which deadlines need attention first.")), 16);

        page.add(group("Colour themes",
                text("Pick a look in <b>Settings &gt; Appearance &gt; Colour theme</b>. The same eight colours are used by the desktop widget, and the app icon (title bar, taskbar while the app is open, sidebar and tray) changes colour with the theme."),
                text("<b>Classic</b> is the plain light or dark look with a choice of accent colour. The sun/moon button in the sidebar switches back to Classic.")), 16);

        page.add(group("Exams and other things that just happen on a day",
                text("You do not need a time for everything. In the task editor choose <b>Just a date</b> and pick the day. Example: an exam on 5 December. There is no time, no all-day setting and nothing to tick off."),
                text("Until that day you see a countdown (\"In 3 days\"). <b>On the day itself it becomes an emergency</b>: Critical level, red. The day after, it goes by itself (it moves to Completed)."),
                text("Need a deadline with a date but no time for something you do have to finish? Choose <b>Has a time</b>, turn on <b>Deadline</b> and then <b>Date only (no time)</b>."),
                text("Want to study for it across several days? Add a second task with <b>Schedule</b> and <b>All day</b>, and use <b>Until</b> for the last study day.")), 16);

        page.add(group("Schedule, deadline and urgency",
                text("<b>Schedule</b> is when you plan to work. <b>Deadline</b> is when it has to be finished. They are separate on purpose."),
                text("Every task with a deadline gets a level and a score from 0 to 100:<br>"
                        + "<b>Comfortable</b> more than a week left.   <b>Approaching</b> getting close.   <b>Urgent</b> within 3 days.   "
                        + "<b>Critical</b> within 24 hours (or due today for date-only deadlines).   <b>Overdue</b> the deadline has passed."),
                text("The score also looks at how much work is left (estimated duration and subtasks) and the priority. It is an estimate to help you plan, not a prediction. "
                        + "You can change the thresholds in <b>Settings</b>.")), 16);

        page.add(group("Calendar tips",
                text("Click or drag on an empty slot to create a task. Drag a task to move it. Drag its bottom edge to change its length."),
                text("Tasks can run past midnight (for example 9 PM to 3 AM). Pick the next day as the end date."),
                text("Every task has its own colour so you can tell them apart. Change it in the task editor."),
                text("Use the arrow keys to move around the calendar. <b>Ctrl+T</b> jumps to today.")), 16);

        page.add(group("Desktop calendar widget",
                text("A small calendar that lives on your Windows desktop, behind your apps: you only see it when you look at the desktop, and it never covers anything. It shows your tasks. Turn it on in <b>Settings &gt; Desktop widget</b> or from the tray icon."),
                text("<b>Point at a date</b> (no click needed): the dates around it zoom, and bubbles show every task on that day. <b>Click a bubble</b> to open the task. "
                        + "Move the mouse away and it zooms back out."),
                text("<b>Resize it any way you like</b> by dragging an edge or corner. When it gets small it hides details (first the bubbles and big date, then the weekday row and arrows) so it stays easy to read. "
                        + "In small sizes, hold the pointer on a date to see its tasks in a tip."),
                text("<b>Add and manage tasks without opening the app:</b> click the <b>+</b> button to add a task (just a date, or at a time). "
                        + "Click a bubble to see it, rename it, mark it done or delete it. Click the coloured circle on a bubble to tick it off straight away."),
                text("<b>It stays on your desktop.</b> Closing the FocusFlow window does not close the widget or your reminders. The app starts with Windows (only the widget and the tray icon appear), "
                        + "and the window opens whenever you want it from the tray icon or the widget's right-click menu. To quit completely, use <b>Quit FocusFlow</b> in either menu."),
                text("Scroll the mouse wheel over the dates to change month. <b>Right-click</b> the widget for colours, transparency and options. Drag it to move it.")), 16);

        page.add(group("Your data",
                text("Everything is stored on this computer in <b>" + esc(AppPaths.dataDir().toString()) + "</b>. Nothing is uploaded anywhere."),
                text("A backup is made automatically once a day. You can also back up, restore or export your tasks in <b>Settings</b>.")), 16);

        page.add(group("Keyboard shortcuts",
                text("<b>Ctrl+N</b> new task    <b>Ctrl+K</b> search    <b>Ctrl+T</b> today    <b>Ctrl+Z</b> undo    <b>Ctrl+D</b> dark mode    <b>Ctrl+B</b> sidebar<br>"
                        + "<b>Ctrl+1</b> to <b>Ctrl+7</b> switch sections    <b>Ctrl+,</b> settings    <b>F1</b> this page    <b>Space</b> pauses the timer")), 16);

        page.add(group("Questions",
                text("<b>A finished task still shows up?</b> It stays on the day you completed it (so you can see what you did) and then disappears from later days. "
                        + "If an unfinished task keeps showing, check its <i>Until</i> or end date in the editor."),
                text("<b>A task is red and I did not expect it?</b> Open it and look at the deadline. A date-only deadline is an emergency for the whole day it is due.")), 16);

        page.add(Lbl.caption(AppInfo.NAME + " " + AppInfo.VERSION + "  ·  Made by " + AppInfo.CREATOR + "  ·  Local-first. No account, no cloud, no tracking."), 18);

        add(Refresher.scroll(page), BorderLayout.CENTER);
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }

    private static JComponent text(String html) {
        return new Lbl("<html><div style='width:640px'>" + html + "</div></html>", Theme.font(13f), Lbl.Role.TEXT2);
    }

    private Card group(String title, JComponent... rows) {
        Card c = new Card(20, new BorderLayout(0, 8));
        c.add(Lbl.h2(title), BorderLayout.NORTH);
        VStack v = new VStack();
        boolean first = true;
        for (JComponent r : rows) { v.add(r, first ? 0 : 8); first = false; }
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private Card aboutCard() {
        Card c = new Card(22, new BorderLayout(20, 0));
        JComponent logo = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(84, 84); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                g2.drawImage(AppIcon.cached(160), 0, 0, 84, 84, null);
                g2.dispose();
            }
        };
        JPanel logoWrap = new JPanel(new BorderLayout());
        logoWrap.setOpaque(false);
        logoWrap.add(logo, BorderLayout.NORTH);
        c.add(logoWrap, BorderLayout.WEST);

        VStack info = new VStack();
        info.add(new Lbl(AppInfo.NAME, Theme.bold(24f), Lbl.Role.TEXT), 0);
        info.add(new Lbl("Version " + AppInfo.VERSION, Theme.semi(14f), Lbl.Role.ACCENT), 2);
        info.add(Lbl.sub(AppInfo.TAGLINE), 8);
        info.add(detail("Created by", AppInfo.CREATOR), 14);
        info.add(detail("Instagram", "@" + AppInfo.INSTAGRAM_HANDLE), 4);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        Btn insta = new Btn("Open Instagram", Btn.Kind.TINTED);
        insta.addActionListener(e -> {
            try { Desktop.getDesktop().browse(java.net.URI.create(AppInfo.INSTAGRAM_URL)); }
            catch (Exception ex) { Toast.show("Could not open the browser."); }
        });
        Btn folder = new Btn("Open data folder", Btn.Kind.TINTED, Icons.Type.FOLDER);
        folder.addActionListener(e -> {
            try { Desktop.getDesktop().open(AppPaths.dataDir().toFile()); } catch (Exception ex) { Toast.show("Could not open the folder."); }
        });
        btns.add(insta);
        btns.add(folder);
        info.add(btns, 14);
        c.add(info, BorderLayout.CENTER);
        return c;
    }

    private JComponent detail(String label, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        Lbl l = new Lbl(label, Theme.font(13f), Lbl.Role.TEXT3);
        l.setPreferredSize(new Dimension(86, 20));
        p.add(l);
        p.add(new Lbl(value, Theme.semi(14f), Lbl.Role.TEXT));
        return p;
    }
}
