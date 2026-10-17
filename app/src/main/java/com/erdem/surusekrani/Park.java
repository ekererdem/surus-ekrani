package com.erdem.surusekrani;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import java.util.Locale;

/** Son bilinen konumu ve park yerini saklar. */
final class Park {
    private Park() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    /** Uygulama açıkken gelen her iyi konumu kaydeder; park yeri için yedek. */
    static void rememberLocation(Context c, Location l) {
        if (l == null || (l.hasAccuracy() && l.getAccuracy() > 80)) return;
        sp(c).edit()
                .putString("lastLoc", String.format(Locale.US, "%.6f,%.6f", l.getLatitude(), l.getLongitude()))
                .putLong("lastLocAt", l.getTime())
                .apply();
    }

    /** Park yerini kaydeder: en yeni konum hangisiyse (sistem ya da uygulamanın son kaydı). */
    static boolean saveHere(Context c) {
        String coord = current(c);
        if (coord == null) return false;
        // Yeni park yeri: eski not ve fotoğraf geçersiz
        sp(c).edit().putString("parkCoord", coord).putLong("parkAt", System.currentTimeMillis())
                .remove("parkNote").remove("parkPhoto").apply();
        return true;
    }

    /** Düğmeye basınca: GPS'ten o anki konumu bir kez al (en fazla 10 sn), olmazsa son bilineni kullan. */
    static void saveNow(android.app.Activity a, java.util.function.Consumer<Boolean> done) {
        LocationManager lm = a.getSystemService(LocationManager.class);
        if (lm == null || a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            done.accept(saveHere(a));
            return;
        }
        android.os.CancellationSignal cancel = new android.os.CancellationSignal();
        boolean[] finished = {false};
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable fallback = () -> {
            if (finished[0]) return;
            finished[0] = true;
            cancel.cancel();
            done.accept(saveHere(a));
        };
        h.postDelayed(fallback, 10000);
        try {
            lm.getCurrentLocation(LocationManager.GPS_PROVIDER, cancel, a.getMainExecutor(), l -> {
                if (finished[0]) return;
                finished[0] = true;
                h.removeCallbacks(fallback);
                if (l == null) { done.accept(saveHere(a)); return; }
                sp(a).edit().putString("parkCoord",
                                String.format(Locale.US, "%.6f,%.6f", l.getLatitude(), l.getLongitude()))
                        .putLong("parkAt", System.currentTimeMillis())
                        .remove("parkNote").remove("parkPhoto").apply();
                done.accept(true);
            });
        } catch (Exception e) {
            h.removeCallbacks(fallback);
            fallback.run();
        }
    }

    static String note(Context c) { return sp(c).getString("parkNote", ""); }
    static String photo(Context c) { return sp(c).getString("parkPhoto", ""); }
    static void setNote(Context c, String n) { sp(c).edit().putString("parkNote", n).apply(); }
    static void setPhoto(Context c, String uri) { sp(c).edit().putString("parkPhoto", uri).apply(); }

    /** En yeni bilinen konum "enlem,boylam" ya da null. */
    static String current(Context c) {
        String coord = null;
        long at = 0;
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager lm = c.getSystemService(LocationManager.class);
                Location g = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location n = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                Location best = g;
                if (best == null || (n != null && n.getTime() > best.getTime() + 120000)) best = n;
                if (best != null) {
                    coord = String.format(Locale.US, "%.6f,%.6f", best.getLatitude(), best.getLongitude());
                    at = best.getTime();
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        long mine = sp(c).getLong("lastLocAt", 0);
        if (mine > at) {
            coord = sp(c).getString("lastLoc", null);
            at = mine;
        }
        return coord;
    }

    /** Park yerini sil (araba simgesindeki P işareti de kalkar). */
    static void clear(Context c) {
        sp(c).edit().remove("parkCoord").remove("parkAt").remove("parkNote").remove("parkPhoto").apply();
    }

    static String coord(Context c) {
        return sp(c).getString("parkCoord", "");
    }

    static long savedAt(Context c) {
        return sp(c).getLong("parkAt", 0);
    }
}
