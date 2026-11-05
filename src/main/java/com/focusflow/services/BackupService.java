package com.focusflow.services;

import com.focusflow.app.AppContext;
import com.focusflow.config.AppPaths;
import com.focusflow.database.Database;
import com.focusflow.models.*;
import com.focusflow.utilities.Json;
import com.focusflow.utilities.Time;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/** Manual and automatic backups, restore with a safety copy, and CSV/JSON export. Existing files are never overwritten. */
public class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static Path unique(Path dir, String base, String ext) {
        Path p = dir.resolve(base + ext);
        int n = 2;
        while (Files.exists(p)) p = dir.resolve(base + "-" + n++ + ext);
        return p;
    }

    public Path backupNow() {
        Path target = unique(AppPaths.backupDir(), "focusflow-backup-" + LocalDateTime.now().format(STAMP), ".db");
        Database.backupTo(target);
        return target;
    }

    public Path backupTo(Path target) {
        Path t = Files.exists(target) ? unique(target.getParent(), stripExt(target.getFileName().toString()), ".db") : target;
        Database.backupTo(t);
        return t;
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    /** One automatic backup per day, keeping the newest seven. */
    public void autoBackupIfNeeded() {
        try {
            Path dir = AppPaths.autoBackupDir();
            Path today = dir.resolve("auto-" + LocalDate.now().toString().replace("-", "") + ".db");
            if (!Files.exists(today)) Database.backupTo(today);
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) { s.filter(p -> p.getFileName().toString().startsWith("auto-")).forEach(files::add); }
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            while (files.size() > 7) Files.deleteIfExists(files.remove(0));
        } catch (Exception ignored) { }
    }

    /** Restores a backup. The current data is first copied to a pre-restore file so nothing is lost. */
    public Path restore(Path source) throws IOException {
        if (!Database.isValidDatabase(source)) throw new IOException("That file is not a FocusFlow database.");
        Path safety = unique(AppPaths.backupDir(), "pre-restore-" + LocalDateTime.now().format(STAMP), ".db");
        Database.backupTo(safety);
        Database.restoreFrom(source);
        AppContext.reloadAll();
        return safety;
    }

    public Path exportCsv(Path file) throws IOException {
        Path out = Files.exists(file) ? unique(file.getParent(), stripExt(file.getFileName().toString()), ".csv") : file;
        StringBuilder b = new StringBuilder("\uFEFF");
        b.append("id,title,description,category,priority,status,scheduled_start,scheduled_end,all_day,deadline,")
                .append("estimated_minutes,completed_at,subtasks_done,subtasks_total,recurrence,archived,notes\n");
        for (Task t : AppContext.tasks.allIncludingArchived()) {
            Category c = AppContext.categories.byId(t.categoryId);
            b.append(t.id).append(',').append(Json.csv(t.title)).append(',').append(Json.csv(t.description)).append(',')
                    .append(Json.csv(c == null ? "" : c.name)).append(',').append(t.priority.label).append(',')
                    .append(t.status.label).append(',').append(iso(t.start)).append(',').append(iso(t.end)).append(',')
                    .append(t.allDay).append(',').append(iso(t.deadline)).append(',').append(t.estimatedMinutes).append(',')
                    .append(iso(t.completedAt)).append(',').append(t.subtasksDone()).append(',').append(t.subtasks.size())
                    .append(',').append(t.recurrence.name()).append(',').append(t.archived).append(',')
                    .append(Json.csv(t.notes)).append('\n');
        }
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        return out;
    }

    public Path exportJson(Path file) throws IOException {
        Path out = Files.exists(file) ? unique(file.getParent(), stripExt(file.getFileName().toString()), ".json") : file;
        StringBuilder b = new StringBuilder("{\n  \"exportedAt\": ").append(Json.quote(LocalDateTime.now().toString()));
        b.append(",\n  \"categories\": [");
        boolean first = true;
        for (Category c : AppContext.categories.all()) {
            b.append(first ? "\n    " : ",\n    ").append("{\"name\": ").append(Json.quote(c.name))
                    .append(", \"color\": ").append(Json.quote(c.color)).append('}');
            first = false;
        }
        b.append("\n  ],\n  \"tasks\": [");
        first = true;
        for (Task t : AppContext.tasks.allIncludingArchived()) {
            Category c = AppContext.categories.byId(t.categoryId);
            b.append(first ? "\n    " : ",\n    ").append("{\"id\": ").append(t.id)
                    .append(", \"title\": ").append(Json.quote(t.title))
                    .append(", \"description\": ").append(Json.quote(t.description))
                    .append(", \"notes\": ").append(Json.quote(t.notes))
                    .append(", \"category\": ").append(Json.quote(c == null ? null : c.name))
                    .append(", \"priority\": ").append(Json.quote(t.priority.label))
                    .append(", \"status\": ").append(Json.quote(t.status.label))
                    .append(", \"scheduledStart\": ").append(Json.quote(iso(t.start).isEmpty() ? null : iso(t.start)))
                    .append(", \"scheduledEnd\": ").append(Json.quote(iso(t.end).isEmpty() ? null : iso(t.end)))
                    .append(", \"allDay\": ").append(t.allDay)
                    .append(", \"deadline\": ").append(Json.quote(iso(t.deadline).isEmpty() ? null : iso(t.deadline)))
                    .append(", \"estimatedMinutes\": ").append(t.estimatedMinutes)
                    .append(", \"completedAt\": ").append(Json.quote(iso(t.completedAt).isEmpty() ? null : iso(t.completedAt)))
                    .append(", \"archived\": ").append(t.archived)
                    .append(", \"subtasks\": [");
            for (int i = 0; i < t.subtasks.size(); i++) {
                Subtask s = t.subtasks.get(i);
                if (i > 0) b.append(", ");
                b.append("{\"title\": ").append(Json.quote(s.title)).append(", \"done\": ").append(s.done).append('}');
            }
            b.append("]}");
            first = false;
        }
        b.append("\n  ],\n  \"focusSessions\": [");
        first = true;
        for (FocusSession s : AppContext.focus.sessions()) {
            b.append(first ? "\n    " : ",\n    ").append("{\"taskId\": ").append(s.taskId)
                    .append(", \"startedAt\": ").append(Json.quote(iso(s.startedAt)))
                    .append(", \"plannedSeconds\": ").append(s.plannedSeconds)
                    .append(", \"actualSeconds\": ").append(s.actualSeconds)
                    .append(", \"completed\": ").append(s.completed).append('}');
            first = false;
        }
        b.append("\n  ]\n}\n");
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        return out;
    }

    private static String iso(Long ms) { return ms == null ? "" : Time.dt(ms).toString().replace('T', ' '); }
}
