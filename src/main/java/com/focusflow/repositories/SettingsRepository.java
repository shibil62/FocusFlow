package com.focusflow.repositories;

import com.focusflow.database.Database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SettingsRepository {
    public Map<String, String> loadAll() {
        return Database.run(c -> {
            Map<String, String> m = new HashMap<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT key,value FROM settings")) {
                while (rs.next()) m.put(rs.getString(1), rs.getString(2));
            }
            return m;
        });
    }

    public void put(String key, String value) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void remove(String key) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM settings WHERE key=?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
