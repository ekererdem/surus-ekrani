package com.erdem.surusekrani;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/**
 * El sallama: elini telefonun üst kısmının (yakınlık sensörü) üzerinden geçir.
 * 1 kez = sonraki şarkı, 1 saniye içinde 2 kez = oynat/duraklat.
 * Sensör kapalıyken neredeyse hiç pil harcamaz; sadece uygulama ekrandayken dinlenir.
 */
final class Wave implements SensorEventListener {
    interface Action { void run(); }

    private static final long MAX_COVER = 900;   // daha uzun kapalı kalırsa el sallama değil (cep, tutucu vb.)
    private static final long DOUBLE_GAP = 1000;

    private final SensorManager sm;
    private final Sensor prox;
    private final Action onSingle, onDouble;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean near, running;
    private long nearAt;
    private int waves;
    private final Runnable fire = this::fire;

    Wave(Context c, Action onSingle, Action onDouble) {
        sm = c.getSystemService(SensorManager.class);
        prox = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.onSingle = onSingle;
        this.onDouble = onDouble;
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
        main.removeCallbacks(fire);
        waves = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        boolean isNear = e.values[0] < Math.min(prox.getMaximumRange(), 5f);
        long now = System.currentTimeMillis();
        if (isNear && !near) {
            nearAt = now;
        } else if (!isNear && near) {
            if (now - nearAt <= MAX_COVER) { // kısa bir geçiş = el sallama
                waves++;
                main.removeCallbacks(fire);
                main.postDelayed(fire, DOUBLE_GAP);
            }
        }
        near = isNear;
    }

    private void fire() {
        int n = waves;
        waves = 0;
        if (n >= 2) onDouble.run(); else if (n == 1) onSingle.run();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
