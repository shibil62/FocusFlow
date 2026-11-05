package com.focusflow.utilities;

import com.focusflow.app.Main;

import java.nio.file.Path;

/** "Launch on Windows startup" through the per-user Run registry key. No admin rights needed. */
public final class StartupRegistry {
    private StartupRegistry() {}

    private static final String KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    public static boolean supported() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    public static void set(boolean enabled) throws Exception {
        if (!supported()) return;
        ProcessBuilder pb = enabled
                ? new ProcessBuilder("reg", "add", KEY, "/v", "FocusFlow", "/t", "REG_SZ", "/d", command(), "/f")
                : new ProcessBuilder("reg", "delete", KEY, "/v", "FocusFlow", "/f");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().readAllBytes();
        int code = p.waitFor();
        if (code != 0 && enabled) throw new IllegalStateException("Windows refused the startup entry (code " + code + ").");
    }

    private static String command() throws Exception {
        String app = System.getProperty("jpackage.app-path");
        if (app != null && !app.isBlank()) return "\"" + app + "\"";
        Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path java = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
        return "\"" + java + "\" -jar \"" + jar + "\"";
    }
}
