package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Surface;

/** Önceki sürümün yatay kilidini geri alır; telefon kendi döndürme ayarına döner. */
final class Rotation {
    private Rotation() {}

    static void giveBack(Context c) {
        SharedPreferences s = c.getSharedPreferences("surus", Context.MODE_PRIVATE);
        if (!s.getBoolean("rotLocked", false)) return;
        try {
            if (Settings.System.canWrite(c)) {
                Settings.System.putInt(c.getContentResolver(), Settings.System.USER_ROTATION,
                        s.getInt("rotPrevUser", Surface.ROTATION_0));
                Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                        s.getInt("rotPrevAcc", 1));
            }
        } catch (Exception ignored) {
        }
        s.edit().putBoolean("rotLocked", false).putBoolean("lockRot", false).apply();
    }
}
