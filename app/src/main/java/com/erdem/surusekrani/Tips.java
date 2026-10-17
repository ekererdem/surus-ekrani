package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;

/** Doğru anda, sadece bir kez gösterilen ipuçları. Bir oturumda en fazla bir ipucu. */
final class Tips {
    interface Show { void show(String text); }

    static Show shower;
    private static boolean shownThisSession;

    private Tips() {}

    static void maybe(Context c, String key, String text) {
        if (shownThisSession || shower == null) return;
        SharedPreferences sp = c.getSharedPreferences("surus", Context.MODE_PRIVATE);
        if (sp.getBoolean("tip_" + key, false)) return;
        sp.edit().putBoolean("tip_" + key, true).apply();
        shownThisSession = true;
        shower.show("💡 " + text);
    }

    /** Ayarlar'dan "ipuçlarını yeniden göster". */
    static void reset(Context c) {
        SharedPreferences sp = c.getSharedPreferences("surus", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith("tip_")) e.remove(k);
        e.apply();
        shownThisSession = false;
    }
}
