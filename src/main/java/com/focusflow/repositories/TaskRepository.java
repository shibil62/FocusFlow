package com.focusflow.repositories;

import com.focusflow.database.Database;
import com.focusflow.models.*;
import com.focusflow.utilities.Time;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskRepository {
    private static final String COLS = "id,title,description,notes,category_id,priority,status,scheduled_start,"
            + "scheduled_end,all_day,deadline,estimated_minutes,completed_at,created_at,updated_at,archived,"
            + "recurrence,recurrence_parent";
    private static final String INSERT = "INSERT INTO tasks(title,description,notes,category_id,priority,status,"
            + "scheduled_start,scheduled_end,all_day,deadline,estimated_minutes,completed_at,created_at,updated_at,"
            + "archived,recurrence,recurrence_parent) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE = "UPDATE tasks SET title=?,description=?,notes=?,category_id=?,priority=?,"
            + "status=?,scheduled_start=?,scheduled_end=?,all_day=?,deadline=?,estimated_minutes=?,completed_at=?,"
            + "created_at=?,updated_at=?,archived=?,recurrence=?,recurrence_parent=? WHERE id=?";

    public List<Task> findAll() {
        return Database.run(c -> {
            Map<Long, Task> map = new LinkedHashMap<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT " + COLS + " FROM tasks ORDER BY id")) {
                while (rs.next()) {
                    Task t = read(rs);
                    map.put(t.id, t);
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT task_id,title,done,done_at FROM subtasks ORDER BY task_id, sort_order, id")) {
                while (rs.next()) {
                    Task t = map.get(rs.getLong(1));
                    if (t == null) continue;
                    Subtask st = new Subtask(rs.getString(2), rs.getInt(3) != 0);
                    st.doneAt = nl(rs, 4);
                    t.subtasks.add(st);
                }
            }
            return new ArrayList<>(map.values());
        });
    }

    public void insert(Task t) {
        Database.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(INSERT)) {
                bind(ps, t);
                ps.executeUpdate();
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                t.id = rs.getLong(1);
            }
            saveSubtasks(c, t);
            return null;
        });
    }

    public void update(Task t) {
        Database.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
                bind(ps, t);
                ps.setLong(18, t.id);
                ps.executeUpdate();
            }
            saveSubtasks(c, t);
            return null;
        });
    }

    public void delete(long id) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM tasks WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void history(long taskId, String event, String detail) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO task_history(task_id,event,detail,at) VALUES(?,?,?,?)")) {
                ps.setLong(1, taskId);
                ps.setString(2, event);
                ps.setString(3, detail);
                ps.setLong(4, Time.now());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private static void saveSubtasks(Connection c, Task t) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM subtasks WHERE task_id=?")) {
            del.setLong(1, t.id);
            del.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO subtasks(task_id,title,done,done_at,sort_order) VALUES(?,?,?,?,?)")) {
            int i = 0;
            for (Subtask s : t.subtasks) {
                ps.setLong(1, t.id);
                ps.setString(2, s.title);
                ps.setInt(3, s.done ? 1 : 0);
                setNull(ps, 4, s.doneAt);
                ps.setInt(5, i++);
                ps.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement ps, Task t) throws SQLException {
        ps.setString(1, t.title);
        ps.setString(2, t.description == null ? "" : t.description);
        ps.setString(3, t.notes == null ? "" : t.notes);
        setNull(ps, 4, t.categoryId);
        ps.setInt(5, t.priority.ordinal());
        ps.setInt(6, t.status.ordinal());
        setNull(ps, 7, t.start);
        setNull(ps, 8, t.end);
        ps.setInt(9, t.allDay ? 1 : 0);
        setNull(ps, 10, t.deadline);
        ps.setInt(11, t.estimatedMinutes);
        setNull(ps, 12, t.completedAt);
        ps.setLong(13, t.createdAt);
        ps.setLong(14, t.updatedAt);
        ps.setInt(15, t.archived ? 1 : 0);
        ps.setString(16, t.recurrence.name());
        setNull(ps, 17, t.recurrenceParent);
    }

    private static Task read(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.id = rs.getLong(1);
        t.title = rs.getString(2);
        t.description = nz(rs.getString(3));
        t.notes = nz(rs.getString(4));
        t.categoryId = nl(rs, 5);
        t.priority = Priority.of(rs.getInt(6));
        t.status = Status.of(rs.getInt(7));
        t.start = nl(rs, 8);
        t.end = nl(rs, 9);
        t.allDay = rs.getInt(10) != 0;
        t.deadline = nl(rs, 11);
        t.estimatedMinutes = rs.getInt(12);
        t.completedAt = nl(rs, 13);
        t.createdAt = rs.getLong(14);
        t.updatedAt = rs.getLong(15);
        t.archived = rs.getInt(16) != 0;
        t.recurrence = Recurrence.parse(rs.getString(17));
        t.recurrenceParent = nl(rs, 18);
        return t;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static Long nl(ResultSet rs, int i) throws SQLException {
        long v = rs.getLong(i);
        return rs.wasNull() ? null : v;
    }

    private static void setNull(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setLong(i, v);
    }
}
