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

    /** Son sesleniş az önce mi oldu (o sırada kopan kısa bağlantı "arabadan indin" sayılmasın). */
    static boolean justKnocked(Context c) {
        long at = c.getSharedPreferences("surus", Context.MODE_PRIVATE).getLong("knockAt", 0);
        return System.currentTimeMillis() - at < 30000;
    }

    /** Kopilot açılınca (en fazla dakikada bir). */
    static void onAppOpen(Context c) {
        if (!new Prefs(c).carLink()) return;
        if (System.currentTimeMillis() - lastKnock < 60000) return;
        knock(c);
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
            try {
                a.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
            Context app = a.getApplicationContext();
            H.postDelayed(() -> knock(app), 4000);
            H.postDelayed(() -> knock(app), 12000);
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
