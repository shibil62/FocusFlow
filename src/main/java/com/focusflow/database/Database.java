package com.focusflow.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;

/**
 * Single shared SQLite connection (WAL mode) with schema versioning.
 * All access goes through run()/tx() so it is serialised and safe from any thread.
 */
public final class Database {
    private Database() {}

    public interface SqlWork<T> { T run(Connection c) throws SQLException; }

    public static final int SCHEMA_VERSION = 1;
    private static Connection conn;
    private static Path path;

    public static synchronized void open(Path file) {
        try {
            try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) { }
            path = file;
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("PRAGMA synchronous = NORMAL");
                s.execute("PRAGMA busy_timeout = 5000");
            }
            migrate();
        } catch (SQLException e) {
            throw new DataException("Could not open the database: " + e.getMessage(), e);
        }
    }

    public static synchronized void close() {
        if (conn == null) return;
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException ignored) { }
        try { conn.close(); } catch (SQLException ignored) { }
        conn = null;
    }

    public static synchronized <T> T run(SqlWork<T> work) {
        try {
            return work.run(conn);
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        }
    }

    public static synchronized <T> T tx(SqlWork<T> work) {
        try {
            conn.setAutoCommit(false);
            try {
                T r = work.run(conn);
                conn.commit();
                return r;
            } catch (Throwable ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        }
    }

    /** Writes a consistent copy of the live database. The target must not already exist. */
    public static synchronized void backupTo(Path target) {
        if (Files.exists(target)) throw new DataException("Backup target already exists: " + target);
        run(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            return null;
        });
    }

    public static boolean isValidDatabase(Path file) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('tasks','categories')")) {
            return rs.next() && rs.getInt(1) == 2;
        } catch (SQLException e) {
            return false;
        }
    }

    public static synchronized void restoreFrom(Path source) throws IOException {
        close();
        Files.deleteIfExists(Path.of(path.toString() + "-wal"));
        Files.deleteIfExists(Path.of(path.toString() + "-shm"));
        Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
        open(path);
    }

    // ------------------------------------------------------------------ schema

    private static void migrate() throws SQLException {
        int v;
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("PRAGMA user_version")) {
            v = rs.next() ? rs.getInt(1) : 0;
        }
        if (v > SCHEMA_VERSION) {
            throw new SQLException("This database was created by a newer version of FocusFlow.");
        }
        if (v < 1) applyV1();
        // Future versions: if (v < 2) applyV2(); ...
    }

    private static void applyV1() throws SQLException {
        conn.setAutoCommit(false);
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS categories(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL UNIQUE,
                  color TEXT NOT NULL,
                  sort_order INTEGER NOT NULL DEFAULT 0)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS tasks(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  notes TEXT NOT NULL DEFAULT '',
                  category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
                  priority INTEGER NOT NULL DEFAULT 1,
                  status INTEGER NOT NULL DEFAULT 0,
                  scheduled_start INTEGER,
                  scheduled_end INTEGER,
                  all_day INTEGER NOT NULL DEFAULT 0,
                  deadline INTEGER,
                  estimated_minutes INTEGER NOT NULL DEFAULT 0,
                  completed_at INTEGER,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  archived INTEGER NOT NULL DEFAULT 0,
                  recurrence TEXT NOT NULL DEFAULT 'NONE',
                  recurrence_parent INTEGER)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS subtasks(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                  title TEXT NOT NULL,
                  done INTEGER NOT NULL DEFAULT 0,
                  done_at INTEGER,
                  sort_order INTEGER NOT NULL DEFAULT 0)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS focus_sessions(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  task_id INTEGER REFERENCES tasks(id) ON DELETE SET NULL,
                  started_at INTEGER NOT NULL,
                  ended_at INTEGER NOT NULL,
                  planned_seconds INTEGER NOT NULL,
                  actual_seconds INTEGER NOT NULL,
                  completed INTEGER NOT NULL DEFAULT 0)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS settings(
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS task_history(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                  event TEXT NOT NULL,
                  detail TEXT,
                  at INTEGER NOT NULL)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS notifications(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  dedupe_key TEXT NOT NULL UNIQUE,
                  task_id INTEGER REFERENCES tasks(id) ON DELETE SET NULL,
                  kind TEXT NOT NULL,
                  message TEXT NOT NULL,
                  fired_at INTEGER NOT NULL,
                  read INTEGER NOT NULL DEFAULT 0)""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_tasks_start ON tasks(scheduled_start)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_subtasks_task ON subtasks(task_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_focus_started ON focus_sessions(started_at)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_history_task ON task_history(task_id)");

            String[][] cats = {
                    {"Study", "#5E5CE6"}, {"College", "#0A84FF"}, {"Work", "#FF9F0A"},
                    {"Personal", "#30D158"}, {"Exercise", "#FF375F"}, {"Projects", "#40C8E0"}, {"Other", "#8E8E93"}};
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO categories(name,color,sort_order) VALUES(?,?,?)")) {
                int i = 0;
                for (String[] c : cats) {
                    ps.setString(1, c[0]);
                    ps.setString(2, c[1]);
                    ps.setInt(3, i++);
                    ps.executeUpdate();
                }
            }
            s.execute("PRAGMA user_version = 1");
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
