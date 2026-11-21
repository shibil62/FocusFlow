package com.focusflow.widget;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.Timer;
import java.awt.Window;

/**
 * Makes the widget live on the Windows desktop, like a real widget: it sits just above the wallpaper and
 * desktop icons and below every app window, so it is only seen when you look at the desktop. It never covers
 * your apps, does not take focus when clicked, and does not appear in the taskbar or Alt+Tab.
 *
 * It works by keeping the widget's window directly above the desktop window in Windows' window order.
 * If anything goes wrong (not Windows, native library missing) the widget simply stays a normal window.
 */
final class DesktopPin {
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TOOLWINDOW = 0x00000080, WS_EX_NOACTIVATE = 0x08000000, WS_EX_APPWINDOW = 0x00040000;
    private static final int SWP_NOSIZE = 0x0001, SWP_NOMOVE = 0x0002, SWP_NOACTIVATE = 0x0010;
    private static final HWND HWND_TOP = new HWND(com.sun.jna.Pointer.createConstant(0));

    private final Window window;
    private final Timer keeper = new Timer(500, e -> place());
    private HWND self, host;

    private DesktopPin(Window window) { this.window = window; }

    /** Starts keeping the (already visible) window on the desktop. Returns null when that is not possible. */
    static DesktopPin attach(Window window) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        try {
            DesktopPin p = new DesktopPin(window);
            p.self = new HWND(Native.getComponentPointer(window));
            User32 u = User32.INSTANCE;
            int ex = u.GetWindowLong(p.self, GWL_EXSTYLE);
            ex = (ex | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE) & ~WS_EX_APPWINDOW;
            u.SetWindowLong(p.self, GWL_EXSTYLE, ex);
            p.place();
            p.keeper.start();
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    void stop() { keeper.stop(); }

    /** Puts the widget directly above the desktop window if it is not there already. */
    private void place() {
        try {
            if (!window.isShowing()) return;
            User32 u = User32.INSTANCE;
            if (host == null || !u.IsWindow(host)) host = findDesktopHost();
            if (host == null) return;
            HWND above = u.GetWindow(host, new DWORD(WinUser.GW_HWNDPREV));
            if (above != null && above.equals(self)) return;          // already right above the desktop
            int flags = SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE;
            // "insert after" means: be placed directly behind that window. The window just above the desktop is
            // the lowest app window, so we end up between the apps and the desktop.
            u.SetWindowPos(self, above != null ? above : HWND_TOP, 0, 0, 0, 0, flags);
        } catch (Throwable ignored) { }
    }

    /** The desktop window that holds the icons: Progman, or the WorkerW that Windows moves them into. */
    private static HWND findDesktopHost() {
        User32 u = User32.INSTANCE;
        HWND progman = u.FindWindow("Progman", null);
        if (progman != null && u.FindWindowEx(progman, null, "SHELLDLL_DefView", null) != null) return progman;
        HWND[] found = new HWND[1];
        u.EnumWindows((hwnd, data) -> {
            if (u.FindWindowEx(hwnd, null, "SHELLDLL_DefView", null) != null) {
                found[0] = hwnd;
                return false;
            }
            return true;
        }, null);
        return found[0] != null ? found[0] : progman;
    }
}
