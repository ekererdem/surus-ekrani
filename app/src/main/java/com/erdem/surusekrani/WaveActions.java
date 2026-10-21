package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;

/** El sallama hareketine atanabilen işler (Ayarlar › Uzaktan kontrol). */
final class WaveActions {
    static final String[] KEYS = {
            "next", "prev", "play", "notif", "read", "mute", "assist", "home", "park", "none"};
    static final String[] LABELS = {
            "Sonraki şarkı",
            "Önceki şarkı",
            "Şarkıyı durdur / başlat",
            "Bekleyen bildirimleri göster",
            "Son bildirimi sesli oku",
            "Sesi kapat / aç",
            "Sesli komut (asistan)",
            "Eve rota başlat",
            "Park yerini kaydet",
            "Hiçbir şey yapma"};

    private WaveActions() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    /** times: 1 = el sallama (2 = eski iki kez sallama, artık kullanılmıyor). Varsayılan: sonraki şarkı. */
    static String get(Context c, int times) {
        return sp(c).getString(times == 2 ? "wave2" : "wave1", times == 2 ? "play" : "next");
    }

    static void set(Context c, int times, String key) {
        sp(c).edit().putString(times == 2 ? "wave2" : "wave1", key).apply();
    }

    static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    static String label(String key) { return LABELS[index(key)]; }
}
