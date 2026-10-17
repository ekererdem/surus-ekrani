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
 * "Tın – tıııın" zil sesi: iki çan notası (Sol → Do, yükselen), kodla üretilir.
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

    /** İlk nota kısa (tın), ikincisi uzun ve yavaşça sönen (tıııın). */
    private static synchronized short[] sound() {
        if (pcm != null) return pcm;
        float[] a = new float[(int) (RATE * 1.45)];
        bell(a, 0, 784f, 0.55f, 9f);          // Sol5 — tın
        bell(a, (int) (RATE * 0.17), 1047f, 0.75f, 2.6f); // Do6 — tıııın
        short[] s = new short[a.length];
        for (int i = 0; i < a.length; i++) {
            float v = Math.max(-1f, Math.min(1f, a[i]));
            s[i] = (short) (v * 32000);
        }
        return pcm = s;
    }

    /** Çan sesi: temel ton + iki üst ton, hızlı vuruş, üstel sönme. */
    private static void bell(float[] out, int start, float f, float amp, float decay) {
        float[][] partials = {{1f, 1f}, {2.0f, 0.35f}, {3.01f, 0.12f}};
        for (int i = start; i < out.length; i++) {
            float t = (i - start) / (float) RATE;
            float env = (float) Math.exp(-decay * t) * Math.min(1f, t / 0.004f); // 4 ms vuruş, tıkırtı olmasın
            float v = 0;
            for (float[] p : partials)
                v += p[1] * (float) Math.sin(2 * Math.PI * f * p[0] * t) * (float) Math.exp(-decay * (p[0] - 1) * 0.6f * t);
            out[i] += amp * env * v / 1.47f;
        }
    }
}
