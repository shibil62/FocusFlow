package com.focusflow.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locations of user data. Everything lives under %APPDATA%/FocusFlow, never in the install folder. */
public final class AppPaths {
    private AppPaths() {}

    public static Path dataDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return ensure(base.resolve("FocusFlow"));
    }

    public static Path dbFile() { return dataDir().resolve("focusflow.db"); }
    public static Path backupDir() { return ensure(dataDir().resolve("backups")); }
    public static Path autoBackupDir() { return ensure(backupDir().resolve("auto")); }
    public static Path exportDir() { return ensure(dataDir().resolve("exports")); }
    public static Path logFile() { return dataDir().resolve("error.log"); }

    private static Path ensure(Path p) {
        try { Files.createDirectories(p); } catch (IOException ignored) { }
        return p;
    }
}
