package com.focusflow.repositories;

import com.focusflow.database.Database;
import com.focusflow.utilities.Time;

import java.sql.*;

public class NotificationRepository {
    /** Returns true when the notification is new (the key had not fired before). */
    public boolean tryInsert(String key, Long taskId, String kind, String message) {
        return Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO notifications(dedupe_key,task_id,kind,message,fired_at,read) VALUES(?,?,?,?,?,0)")) {
                ps.setString(1, key);
                if (taskId == null) ps.setNull(2, Types.INTEGER); else ps.setLong(2, taskId);
                ps.setString(3, kind);
                ps.setString(4, message);
                ps.setLong(5, Time.now());
                return ps.executeUpdate() > 0;
            }
        });
    }

    public void pruneOlderThan(long ms) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM notifications WHERE fired_at<?")) {
                ps.setLong(1, ms);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
