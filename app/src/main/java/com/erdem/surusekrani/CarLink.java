package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.function.Consumer;

/**
 * Araba telefonu kendisi bulmuyorsa Kopilot bağlanmayı dener.
 * Android uygulamaların Bluetooth cihazını doğrudan bağlamasına izin vermiyor; bu yüzden telefon arabaya
 * seslenir (cihaz bilgisini sorar, bu sırada araba telefonu görür). Olmazsa Bluetooth ekranı açılır.
 */
final class CarLink {
    private CarLink() {}

    private static final String TAG = "Kopilot";
    private static final Object POWER = new Object();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static long lastKnock;

    private static BluetoothAdapter adapter(Context c) {
        BluetoothManager bm = c.getSystemService(BluetoothManager.class);
        return bm == null ? null : bm.getAdapter();
    }

    private static boolean perm(Context c) {
        return c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Arabanın ses bağlantısı (müzik ya da telefon) kurulu mu. */
    static boolean connected(Context c) {
        BluetoothAdapter ad = adapter(c);
        if (ad == null || !perm(c)) return false;
        try {
            if (!ad.isEnabled()) return false;
            return ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                    || ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Arabaya bir kez seslen. */
    static void knock(Context c) {
        Prefs p = new Prefs(c);
        if (p.carAddr().isEmpty() || !perm(c) || connected(c)) return;
        BluetoothAdapter ad = adapter(c);
        if (ad == null) return;
        try {
            if (!ad.isEnabled()) return;
            BluetoothDevice d = ad.getRemoteDevice(p.carAddr());
            if (d.getBondState() != BluetoothDevice.BOND_BONDED) return;
            lastKnock = System.currentTimeMillis();
            c.getSharedPreferences("surus", Context.MODE_PRIVATE).edit().putLong("knockAt", lastKnock).apply();
            boolean ok = d.fetchUuidsWithSdp();
            Log.i(TAG, "arabaya seslenildi: " + ok);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "arabaya seslenilemedi", e);
        }
    }

    static final String ACTION_OFF = "com.erdem.surusekrani.BT_OFF";

    private static android.app.PendingIntent offIntent(Context c) {
        Intent i = new Intent(c, BtReceiver.class).setAction(ACTION_OFF);
        return android.app.PendingIntent.getBroadcast(c, 41, i,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Arabadan inince: bir dakika sonra Bluetooth'u kapat (araba o arada yeniden bağlanırsa iptal). */
    static void scheduleOff(Context c) {
        if (!new Prefs(c).btOffOnLeave()) return;
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        if (am == null) return;
        am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60_000, offIntent(c));
        Log.i(TAG, "araba koptu: 1 dk sonra Bluetooth kapanacak");
    }

    static void cancelOff(Context c) {
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        if (am != null) am.cancel(offIntent(c));
    }

    // ---------- Kopilot alta alınınca ----------

    static final String ACTION_OFF_HIDE = "com.erdem.surusekrani.BT_OFF_HIDE";
    private static final long HIDE_DELAY = 30_000;
    /** Ekranda (başlamış) Kopilot ekranı sayısı: ana ekran, Ayarlar, uygulama seçimi… */
    private static int started;
    private static boolean watching;

    private static android.app.PendingIntent hideIntent(Context c) {
        Intent i = new Intent(c, BtReceiver.class).setAction(ACTION_OFF_HIDE);
        return android.app.PendingIntent.getBroadcast(c, 42, i,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Kopilot'un hiçbir ekranı görünmez olunca (alta alındı ya da kapatıldı) yarım dakika sonra Bluetooth'u kapat;
     * o arada Kopilot'a dönülürse iptal. Ana ekran açılırken bir kez kurulur.
     */
    static void watchApp(android.app.Application app) {
        if (watching) return;
        watching = true;
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                if (started++ == 0) {
                    android.app.AlarmManager am = app.getSystemService(android.app.AlarmManager.class);
                    if (am != null) am.cancel(hideIntent(app));
                }
            }
            @Override public void onActivityStopped(Activity a) {
                if (started > 0) started--;
                if (started == 0 && !a.isChangingConfigurations()) scheduleHideOff(app);
            }
            @Override public void onActivityCreated(Activity a, android.os.Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    private static void scheduleHideOff(Context c) {
        if (!new Prefs(c).btOffOnHide()) return;
        BluetoothAdapter ad = adapter(c);
        try {
            if (ad == null || !ad.isEnabled()) return;
        } catch (Exception e) {
            return;
        }
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        if (am == null) return;
        long at = SystemClock.elapsedRealtime() + HIDE_DELAY;
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, hideIntent(c));
        } else {
            am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, hideIntent(c));
        }
        Log.i(TAG, "Kopilot alta alındı: yarım dakika sonra Bluetooth kapanacak");
    }

    /** Zamanı gelince: Kopilot hâlâ alttaysa ve hiçbir cihaz bağlı değilse kapat. */
    static void offAfterHide(Context c) {
        if (!new Prefs(c).btOffOnHide() || started > 0) return;
        disableIfIdle(c, "Kopilot alta alındı");
    }

    /** Zamanı gelince: araba ya da başka bir cihaz (kulaklık, saat) bağlı değilse Bluetooth'u kapat. */
    static void offNow(Context c) {
        if (!new Prefs(c).btOffOnLeave()) return;
        disableIfIdle(c, "arabadan inildi");
    }

    private static void disableIfIdle(Context c, String why) {
        BluetoothAdapter ad = adapter(c);
        if (ad == null || !perm(c)) return;
        try {
            if (!ad.isEnabled()) return;
            if (ad.getProfileConnectionState(BluetoothProfile.A2DP) != BluetoothProfile.STATE_DISCONNECTED
                    || ad.getProfileConnectionState(BluetoothProfile.HEADSET) != BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Bluetooth kapatılmadı: bir ses cihazı bağlı");
                return;
            }
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            if (bm != null && !bm.getConnectedDevices(BluetoothProfile.GATT).isEmpty()) {
                Log.i(TAG, "Bluetooth kapatılmadı: saat/bileklik gibi bir cihaz bağlı");
                return;
            }
            //noinspection deprecation
            boolean ok = ad.disable();
            Log.i(TAG, why + ", Bluetooth kapatıldı: " + ok);
        } catch (SecurityException e) {
            Log.w(TAG, "Bluetooth kapatılamadı", e);
        }
    }

    /** Son sesleniş az önce mi oldu (o sırada kopan kısa bağlantı "arabadan indin" sayılmasın). */
    static boolean justKnocked(Context c) {
        long at = c.getSharedPreferences("surus", Context.MODE_PRIVATE).getLong("knockAt", 0);
        return System.currentTimeMillis() - at < 30000;
    }

    private static long lastEnable;

    /**
     * Bluetooth'u aç. Önce sessizce (uygulama Android 12 kurallarıyla derlendiği için izinli);
     * telefon izin vermezse Android'in "Bluetooth açılsın mı?" sorusu çıkar. Açılınca arabaya seslenilir.
     */
    static void turnOn(Activity a) {
        BluetoothAdapter ad = adapter(a);
        if (ad == null || !perm(a)) return;
        boolean ok = false;
        try {
            //noinspection deprecation
            ok = ad.enable();
        } catch (SecurityException e) {
            Log.w(TAG, "Bluetooth sessiz açılamadı", e);
        }
        Log.i(TAG, "Bluetooth açılıyor: " + ok);
        if (!ok) {
            try {
                a.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        }
        if (new Prefs(a).carAddr().isEmpty()) return;
        Context app = a.getApplicationContext();
        H.postDelayed(() -> knock(app), 4000);
        H.postDelayed(() -> knock(app), 12000);
    }

    /** Kopilot açılınca: Bluetooth kapalıysa aç (ayar açıksa); açıksa ve araba bağlı değilse seslen. */
    static void onAppOpen(Activity a) {
        Prefs p = new Prefs(a);
        BluetoothAdapter ad = adapter(a);
        boolean off;
        try {
            off = ad != null && !ad.isEnabled();
        } catch (Exception e) {
            off = false;
        }
        if (off) {
            // Sen kapattıysan her dönüşte yeniden açmasın: en fazla 10 dakikada bir
            if (p.btOnOpen() && System.currentTimeMillis() - lastEnable > 10 * 60 * 1000L) {
                lastEnable = System.currentTimeMillis();
                turnOn(a);
            }
            return;
        }
        if (!p.carLink()) return;
        if (System.currentTimeMillis() - lastKnock < 60000) return;
        knock(a);
    }

    /** Telefon şarja takılınca: araba ekranı açılana kadar birkaç kez dene. */
    static void onPower(Context c, boolean plugged) {
        H.removeCallbacksAndMessages(POWER);
        if (!plugged || !new Prefs(c).carLink()) return;
        Context app = c.getApplicationContext();
        long now = SystemClock.uptimeMillis();
        for (long t : new long[]{2000, 20000, 45000, 90000}) {
            H.postAtTime(() -> knock(app), POWER, now + t);
        }
    }

    /** Bluetooth simgesine dokununca: bağlanmayı dene, olmazsa Bluetooth ekranını aç. */
    static void tap(Activity a, Consumer<String> say) {
        Prefs p = new Prefs(a);
        if (p.carAddr().isEmpty()) {
            say.accept("Önce Ayarlar'dan arabanı seç.");
            return;
        }
        if (!perm(a)) {
            a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 60);
            return;
        }
        BluetoothAdapter ad = adapter(a);
        if (ad == null) return;
        String car = p.carName().isEmpty() ? "Araban" : p.carName();
        if (!ad.isEnabled()) {
            // Bluetooth açılınca telefon son bağlandığı cihaza kendisi bağlanır; biz de seslenelim.
            say.accept("Bluetooth açılıyor…");
            turnOn(a);
            return;
        }
        if (connected(a)) {
            say.accept(car + " bağlı.");
            return;
        }
        knock(a);
        say.accept(car + " aranıyor…");
        long until = SystemClock.uptimeMillis() + 7000;
        Runnable check = new Runnable() {
            @Override public void run() {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (connected(a)) {
                    say.accept(car + " bağlandı.");
                    return;
                }
                if (SystemClock.uptimeMillis() < until) {
                    H.postDelayed(this, 500);
                    return;
                }
                // Bluetooth ekranı Kopilot'un üstüne açılacağı için not sistem baloncuğuyla
                android.widget.Toast.makeText(a, "Listede " + car + " adına dokun, bağlansın.",
                        android.widget.Toast.LENGTH_LONG).show();
                try {
                    a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        };
        H.postDelayed(check, 1000);
    }
}
