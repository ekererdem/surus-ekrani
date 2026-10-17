package com.erdem.surusekrani;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * Kristal zil sesi: iki ince nota (Do7 → Mi7, yükselen), kodla üretilir.
 * Navigasyon sesi gibi çalar: arabanın hoparlöründen çıkar, çalarken müzik kısılır.
 */
final class Chime {
    private static final int RATE = 44100;
    private static short[] pcm;

    private Chime() {}

    static void play(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build();
        try {
            if (am != null) am.requestAudioFocus(focus);
            short[] data = sound();
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.length * 2)
                    .build();
            t.write(data, 0, data.length);
            t.play();
            long ms = data.length * 1000L / RATE + 200;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { t.release(); } catch (Exception ignored) { }
                if (am != null) am.abandonAudioFocusRequest(focus);
            }, ms);
        } catch (Exception e) {
            if (am != null) am.abandonAudioFocusRequest(focus);
        }
    }

    /** Kristal: ince bir "tın" (Do7) ve ardından yükselen, uzun sönen "tınnnn" (Mi7). Cam bardağa dokunmak gibi. */
    private static synchronized short[] sound() {
        if (pcm != null) return pcm;
        float[] a = new float[(int) (RATE * 2.8)];
        hit(a, 0, 2093f, 10f, 0.5f);
        hit(a, (int) (RATE * 0.17), 2637f, 2.0f, 2.6f);
        float max = 0;
        for (float v : a) max = Math.max(max, Math.abs(v));
        int fade = (int) (RATE * 0.15);
        short[] s = new short[a.length];
        for (int i = 0; i < a.length; i++) {
            float v = a[i] / max * 0.6f;
            if (i > a.length - fade) v *= (a.length - i) / (float) fade;
            s[i] = (short) (v * 32767);
        }
        return pcm = s;
    }

    /** Tek vuruş: temel ton + hafif oktav, 2 ms yumuşak başlangıç, üstel sönme. */
    private static void hit(float[] out, int start, float f, float decay, float dur) {
        int n = Math.min(out.length - start, (int) (RATE * dur));
        for (int k = 0; k < n; k++) {
            float t = k / (float) RATE;
            double v = Math.sin(2 * Math.PI * f * t) * Math.exp(-decay * t)
                    + 0.12 * Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-decay * 1.5 * t);
            out[start + k] += (float) (v * Math.min(1f, t / 0.002f));
        }
    }
}
