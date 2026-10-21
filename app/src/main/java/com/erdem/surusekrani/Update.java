package com.erdem.surusekrani;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

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

    /** İndirme ilerlemesi (ana iş parçacığında): 0–100, boyut bilinmiyorsa -1, olmadıysa -2. */
    interface Progress { void on(int pct); }

    private static volatile boolean busy;

    /**
     * Yeni sürümü tarayıcı açmadan, Kopilot'un içinden indirip kurar; telefon yalnız "Güncelle" onayı ister.
     * İlk seferde Android, Kopilot'un uygulama kurmasına izin vermeni ister (ayar ekranı açılır).
     * Olmazsa eski yol: APK tarayıcıdan iner. Başladıysa true.
     */
    static boolean install(android.app.Activity a, Progress pr) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Kopilot'un güncelleme kurmasına izin ver, sonra geri gelip tekrar dokun.",
                    Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                browser(a);
            }
            return false;
        }
        if (busy) return true;
        busy = true;
        Context app = a.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            PackageInstaller.Session s = null;
            try {
                PackageInstaller inst = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(app.getPackageName());
                s = inst.openSession(inst.createSession(sp));
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(APK).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                long len = c.getContentLengthLong();
                try (java.io.InputStream in = c.getInputStream();
                     java.io.OutputStream out = s.openWrite("kopilot.apk", 0, len > 0 ? len : -1)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int last = -9, n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        int pct = len > 0 ? (int) (done * 100 / len) : -1;
                        if (pct != last) {
                            last = pct;
                            a.runOnUiThread(() -> { if (!a.isFinishing()) pr.on(pct); });
                        }
                    }
                    s.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent cb = PendingIntent.getBroadcast(app, 41, new Intent(app, UpdateReceiver.class), flags);
                s.commit(cb.getIntentSender());
                s.close();
                s = null;
                ok = true;
            } catch (Exception e) {
                android.util.Log.w("Kopilot", "güncelleme indirilemedi", e);
                if (s != null) try { s.abandon(); } catch (Exception ignored) { }
            }
            busy = false;
            if (!ok) a.runOnUiThread(() -> {
                if (!a.isFinishing()) pr.on(-2);
                Toast.makeText(app, "Güncelleme indirilemedi, tarayıcıdan indiriliyor", Toast.LENGTH_LONG).show();
                browser(a);
            });
        }, "guncelle").start();
        return true;
    }

    static void browser(android.app.Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK)));
        } catch (Exception ignored) {
        }
    }

    static long current(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }
}
