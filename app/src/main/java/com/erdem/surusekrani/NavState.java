package com.erdem.surusekrani;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

/** Google Maps / Waze bildiriminden okunan güncel yön bilgisi. */
final class NavState {
    interface Listener { void onNavChanged(); }

    static boolean active;
    static String pkg = "";
    static String title = "";
    static String text = "";
    static String eta = "";
    static Drawable icon;
    static Listener listener;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NavState() {}

    static void update(String p, String t, String x, String e, Drawable i) {
        MAIN.post(() -> {
            active = true;
            pkg = p; title = t; text = x; eta = e; icon = i;
            if (listener != null) listener.onNavChanged();
        });
    }

    static void clear(String p) {
        MAIN.post(() -> {
            if (!active || !pkg.equals(p)) return;
            active = false;
            title = text = eta = "";
            icon = null;
            if (listener != null) listener.onNavChanged();
        });
    }
}
