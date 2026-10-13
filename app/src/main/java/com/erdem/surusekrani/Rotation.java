package com.erdem.surusekrani;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Surface;

/**
 * Bölünmüş ekranda Android uygulamaların yön isteğini yok sayıp telefonun döndürme kilidine uyar.
 * Bu yüzden sürüş sırasında telefonun döndürme kilidini yatayda tutarız, sonra eski haline döndürürüz.
 * "Sistem ayarlarını değiştirme" izni gerekir.
 */
final class Rotation {
    private Rotation() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("lockRot", false);
    }

    static boolean canWrite(Context c) {
        return Settings.System.canWrite(c);
    }

    /** Döndürmeyi yatay kilitle (kamera çentiği solda). Eski ayarları bir kez saklar. */
    static void lockLandscape(Context c) {
        if (!enabled(c) || !canWrite(c)) return;
        ContentResolver cr = c.getContentResolver();
        try {
            SharedPreferences s = sp(c);
            if (!s.getBoolean("rotLocked", false)) {
                s.edit()
                        .putInt("rotPrevAcc", Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1))
                        .putInt("rotPrevUser", Settings.System.getInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0))
                        .putBoolean("rotLocked", true)
                        .apply();
            }
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_90);
        } catch (Exception ignored) {
        }
    }

    /** Telefonun döndürme ayarını sürüş öncesindeki haline getir. */
    static void restore(Context c) {
        SharedPreferences s = sp(c);
        if (!s.getBoolean("rotLocked", false) || !canWrite(c)) return;
        ContentResolver cr = c.getContentResolver();
        try {
            Settings.System.putInt(cr, Settings.System.USER_ROTATION, s.getInt("rotPrevUser", Surface.ROTATION_0));
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, s.getInt("rotPrevAcc", 1));
        } catch (Exception ignored) {
        }
        s.edit().putBoolean("rotLocked", false).apply();
    }
}
