package com.erdem.surusekrani;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

/** Google Maps / Waze / Yandex bildiriminden okunan güncel yön bilgisi ve varış saati. */
final class NavState {
    interface Listener { void onNavChanged(); }

    static boolean active;
    static String pkg = "";
    static String title = "";
    static String text = "";
    static String eta = "";
    static Drawable icon;
    static Listener listener;
    /** Navigasyonun söylediği varış zamanı (ms); bilinmiyorsa 0. */
    static volatile long etaAt;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NavState() {}

    private static final java.util.regex.Pattern CLOCK =
            java.util.regex.Pattern.compile("(?<![\\d:.])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d:])");
    private static final java.util.regex.Pattern DUR = java.util.regex.Pattern.compile(
            "(?:(\\d+)\\s*(?:sa|saat|h|hr|ч)\\.?\\s*)?(\\d+)\\s*(?:dk|dak|dakika|min|мин)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern HOURS = java.util.regex.Pattern.compile(
            "(\\d+)\\s*(?:sa|saat|h|ч)\\b", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern ARRIVE = java.util.regex.Pattern.compile(
            "varış|varis|arrival|eta|прибыт", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /**
     * Navigasyon bildiriminin yazılarından varış zamanı: "Varış 14:35" gibi bir saat varsa o
     * (varış kelimesine en yakın olanı), yoksa "18 dk" / "1 sa 20 dk" gibi kalan süre. Bulunamazsa 0.
     */
    static long parseEta(String s, long now) {
        if (s == null || s.isEmpty()) return 0;
        java.util.regex.Matcher kw = ARRIVE.matcher(s);
        int key = kw.find() ? kw.start() : -1;
        java.util.regex.Matcher m = CLOCK.matcher(s);
        int bestH = -1, bestM = -1, bestDist = Integer.MAX_VALUE;
        while (m.find()) {
            int d = key < 0 ? -m.start() : Math.abs(m.start() - key); // varış kelimesi yoksa sondaki saat
            if (d < bestDist) {
                bestDist = d;
                bestH = Integer.parseInt(m.group(1));
                bestM = Integer.parseInt(m.group(2));
            }
        }
        if (bestH >= 0) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(now);
            c.set(java.util.Calendar.HOUR_OF_DAY, bestH);
            c.set(java.util.Calendar.MINUTE, bestM);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            if (t < now - 5 * 60_000L) t += 24 * 3600_000L; // gece yarısını geçen yol
            return t;
        }
        java.util.regex.Matcher d = DUR.matcher(s);
        if (d.find()) {
            long h = d.group(1) == null ? 0 : Long.parseLong(d.group(1));
            long mi = Long.parseLong(d.group(2));
            return now + (h * 60 + mi) * 60_000L;
        }
        java.util.regex.Matcher h = HOURS.matcher(s);
        if (h.find()) return now + Long.parseLong(h.group(1)) * 3600_000L;
        return 0;
    }

    static void update(String p, String t, String x, String e, Drawable i, long at) {
        MAIN.post(() -> {
            active = true;
            pkg = p; title = t; text = x; eta = e; icon = i;
            if (at > 0) etaAt = at;
            if (listener != null) listener.onNavChanged();
        });
    }

    static void clear(String p) {
        MAIN.post(() -> {
            if (!active || !pkg.equals(p)) return;
            active = false;
            etaAt = 0;
            title = text = eta = "";
            icon = null;
            if (listener != null) listener.onNavChanged();
        });
    }
}
