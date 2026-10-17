package com.erdem.surusekrani;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Telefonun kendi "basılı tutma" süresinden bağımsız, kısa basılı tutma.
 * Kısa dokunuş: tap. ms kadar basılı tutunca: titreşim + hold (parmağı kaldırmayı beklemeden).
 */
final class QuickHold {
    private QuickHold() {}

    static void attach(View v, long ms, Runnable tap, Runnable hold) {
        boolean[] fired = {false};
        float[] down = new float[2];
        int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop() * 2;
        Runnable r = () -> {
            fired[0] = true;
            v.setPressed(false);
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            hold.run();
        };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fired[0] = false;
                    down[0] = e.getX();
                    down[1] = e.getY();
                    view.drawableHotspotChanged(e.getX(), e.getY());
                    view.setPressed(true);
                    view.postDelayed(r, ms);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - down[0]) > slop || Math.abs(e.getY() - down[1]) > slop) {
                        view.removeCallbacks(r);
                        view.setPressed(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    view.removeCallbacks(r);
                    boolean pressed = view.isPressed();
                    view.setPressed(false);
                    if (!fired[0] && pressed) tap.run();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.removeCallbacks(r);
                    view.setPressed(false);
                    return true;
            }
            return true;
        });
    }
}
