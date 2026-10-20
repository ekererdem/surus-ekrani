package com.erdem.surusekrani;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;

/** Vurgu rengi (Ayarlar > Görünüm): Oynat tuşu, kartlar, çubuklar, anahtarlar, başlıklar. */
final class Accent {
    private Accent() {}

    static final String[] KEYS = {"gold", "teal", "blue", "green", "coral", "purple"};
    static final String[] NAMES = {"Altın", "Turkuaz", "Mavi", "Yeşil", "Mercan", "Mor"};
    static final int[] COLORS = {0xFFF5A623, 0xFF22C3B5, 0xFF3B82F6, 0xFF22C55E, 0xFFF2545B, 0xFF8B5CF6};
    private static final int[] THEMES = {R.style.AppTheme, R.style.AppTheme_Teal, R.style.AppTheme_Blue,
            R.style.AppTheme_Green, R.style.AppTheme_Coral, R.style.AppTheme_Purple};

    static int index(Context c) {
        String k = c.getSharedPreferences("surus", Context.MODE_PRIVATE).getString("accent", "gold");
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return i;
        return 0;
    }

    static void set(Context c, int i) {
        c.getSharedPreferences("surus", Context.MODE_PRIVATE).edit().putString("accent", KEYS[i]).apply();
    }

    /** Her ekran açılırken (super.onCreate'ten önce) çağrılır. */
    static void apply(Activity a) {
        a.setTheme(THEMES[index(a)]);
    }

    /** Vurgu rengi kaynakları temadan; diğer renkler olduğu gibi. */
    static int color(Context c, int res) {
        if (res == R.color.accent || res == R.color.amber) return attr(c, R.attr.kAccent, res);
        if (res == R.color.accent_ink) return attr(c, R.attr.kAccentInk, res);
        return c.getColor(res);
    }

    private static int attr(Context c, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return c.getColor(fallback);
    }
}
