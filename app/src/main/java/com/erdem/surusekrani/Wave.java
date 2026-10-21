package com.erdem.surusekrani;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * El sallama: elini telefonun üst kısmının (yakınlık sensörü) üzerinden bir kez geçir, seçilen iş hemen yapılır.
 * (İki kez geçirme kaldırıldı: güvenilir ayırt edilemiyordu.) Hemen ardından gelen ikinci geçiş yok sayılır.
 * Sensör kapalıyken neredeyse hiç pil harcamaz; sadece uygulama ekrandayken dinlenir.
 */
final class Wave implements SensorEventListener {
    interface Action { void run(); }

    private static final long MAX_COVER = 900;   // daha uzun kapalı kalırsa el sallama değil (cep, tutucu vb.)
    private static final long QUIET = 1500;      // bir el sallamadan sonra bu kadar süre yeni geçiş sayılmaz

    private final SensorManager sm;
    private final Sensor prox;
    private final Action onWave;
    private boolean near, running;
    private long nearAt, firedAt;

    Wave(Context c, Action onWave) {
        sm = c.getSystemService(SensorManager.class);
        prox = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.onWave = onWave;
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE).getBoolean("wave", true);
    }

    boolean available() { return prox != null; }

    void start(Context c) {
        if (running || prox == null || !enabled(c)) return;
        running = true;
        near = false;
        sm.registerListener(this, prox, SensorManager.SENSOR_DELAY_UI);
    }

    void stop() {
        if (!running) return;
        running = false;
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        boolean isNear = e.values[0] < Math.min(prox.getMaximumRange(), 5f);
        long now = System.currentTimeMillis();
        if (isNear && !near) {
            nearAt = now;
        } else if (!isNear && near) {
            // kısa bir geçiş = el sallama
            if (now - nearAt <= MAX_COVER && now - firedAt > QUIET) {
                firedAt = now;
                onWave.run();
            }
        }
        near = isNear;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
