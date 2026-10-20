package com.erdem.surusekrani;

import android.content.Context;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub'daki son sürüm: "Sürüm 15.4 (145) · Son derleme: …" satırından okunur. */
final class Update {
    private Update() {}

    static final String APK = "https://github.com/ekererdem/surus-ekrani/releases/download/latest/SurusEkrani.apk";
    private static final String API = "https://api.github.com/repos/ekererdem/surus-ekrani/releases/tags/latest";
    private static final Pattern VER = Pattern.compile("Sürüm\\s+([\\w.]+)\\s*\\((\\d+)\\)");

    interface Result { void done(String name, long code); }

    /** Arka planda bakar; sonuç ana iş parçacığında. Bulunamazsa name null. */
    static void check(android.app.Activity a, Result r) {
        new Thread(() -> {
            String name = null;
            long code = 0;
            try {
                JSONObject o = new JSONObject(Route.get(API));
                Matcher m = VER.matcher(o.optString("body"));
                if (m.find()) {
                    name = m.group(1);
                    code = Long.parseLong(m.group(2));
                }
            } catch (Exception ignored) {
            }
            String n = name;
            long c = code;
            a.runOnUiThread(() -> { if (!a.isFinishing()) r.done(n, c); });
        }, "guncelleme").start();
    }

    static long current(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }
}
