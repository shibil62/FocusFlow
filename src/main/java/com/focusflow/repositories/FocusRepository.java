package com.focusflow.repositories;

import com.focusflow.database.Database;
import com.focusflow.models.FocusSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FocusRepository {
    public void insert(FocusSession s) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO focus_sessions(task_id,started_at,ended_at,planned_seconds,actual_seconds,completed) VALUES(?,?,?,?,?,?)")) {
                if (s.taskId == null) ps.setNull(1, Types.INTEGER); else ps.setLong(1, s.taskId);
                ps.setLong(2, s.startedAt);
                ps.setLong(3, s.endedAt);
                ps.setInt(4, s.plannedSeconds);
                ps.setInt(5, s.actualSeconds);
                ps.setInt(6, s.completed ? 1 : 0);
                ps.executeUpdate();
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                s.id = rs.getLong(1);
            }
            return null;
        });
    }

    public List<FocusSession> findAll() {
        return Database.run(c -> {
            List<FocusSession> list = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id,task_id,started_at,ended_at,planned_seconds,actual_seconds,completed "
                         + "FROM focus_sessions ORDER BY started_at DESC")) {
                while (rs.next()) {
                    FocusSession s = new FocusSession();
                    s.id = rs.getLong(1);
                    long tid = rs.getLong(2);
                    s.taskId = rs.wasNull() ? null : tid;
                    s.startedAt = rs.getLong(3);
                    s.endedAt = rs.getLong(4);
                    s.plannedSeconds = rs.getInt(5);
                    s.actualSeconds = rs.getInt(6);
                    s.completed = rs.getInt(7) != 0;
                    list.add(s);
                }
            }
            return list;
        });
    }

    public long secondsBetween(long fromMs, long toMs) {
        return Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(actual_seconds),0) FROM focus_sessions WHERE started_at>=? AND started_at<?")) {
                ps.setLong(1, fromMs);
                ps.setLong(2, toMs);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }
}
