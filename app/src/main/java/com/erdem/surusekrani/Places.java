package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;

/** En fazla 5 hızlı rota: ad, simge, konum (enlem,boylam) ve adres yazısı. Sıra 1..5. */
final class Places {
    static final int MAX = 5;

    static final String[] ICON_KEYS = {"home", "work", "school", "cart", "heart", "star", "gym", "pin"};
    static final String[] ICON_NAMES = {"Ev", "İş", "Okul", "Market", "Aile", "Favori", "Spor", "Konum"};
    static final int[] ICON_RES = {
            R.drawable.ic_home, R.drawable.ic_work, R.drawable.ic_school, R.drawable.ic_cart,
            R.drawable.ic_heart, R.drawable.ic_star, R.drawable.ic_gym, R.drawable.ic_pin};

    private Places() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    static String name(Context c, int i) { return sp(c).getString("place" + i + "Name", ""); }
    static String coord(Context c, int i) { return sp(c).getString("place" + i + "Coord", ""); }
    static String label(Context c, int i) { return sp(c).getString("place" + i + "Label", ""); }
    static String icon(Context c, int i) { return sp(c).getString("place" + i + "Icon", "pin"); }

    static int iconRes(String key) {
        for (int k = 0; k < ICON_KEYS.length; k++) if (ICON_KEYS[k].equals(key)) return ICON_RES[k];
        return R.drawable.ic_pin;
    }

    static int iconIndex(String key) {
        for (int k = 0; k < ICON_KEYS.length; k++) if (ICON_KEYS[k].equals(key)) return k;
        return ICON_KEYS.length - 1;
    }

    static void setName(Context c, int i, String v) { sp(c).edit().putString("place" + i + "Name", v).apply(); }
    static void setIcon(Context c, int i, String v) { sp(c).edit().putString("place" + i + "Icon", v).apply(); }

    static void setLocation(Context c, int i, String coord, String label) {
        sp(c).edit().putString("place" + i + "Coord", coord).putString("place" + i + "Label", label).apply();
    }

    /** Konumu olan rota sayısı (sıra boşluksuz tutulur). */
    static int count(Context c) {
        int n = 0;
        for (int i = 1; i <= MAX; i++) if (!coord(c, i).isEmpty()) n = i; else break;
        return n;
    }

    /** i. rotayı siler, sonrakileri bir yukarı kaydırır. */
    static void remove(Context c, int i) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int k = i; k < MAX; k++) {
            e.putString("place" + k + "Name", name(c, k + 1))
                    .putString("place" + k + "Coord", coord(c, k + 1))
                    .putString("place" + k + "Label", label(c, k + 1))
                    .putString("place" + k + "Icon", icon(c, k + 1));
        }
        e.remove("place" + MAX + "Name").remove("place" + MAX + "Coord")
                .remove("place" + MAX + "Label").remove("place" + MAX + "Icon");
        e.apply();
    }

    /** Konumu seçilmemiş yarım kayıtları temizler. */
    static void cleanup(Context c) {
        for (int i = MAX; i >= 1; i--) {
            if (coord(c, i).isEmpty() && (!name(c, i).isEmpty() || sp(c).contains("place" + i + "Icon"))) {
                remove(c, i);
            }
        }
    }

    /** Eski tek "ev adresi" ayarını 1. hızlı rotaya taşır (bir kez). */
    static void migrate(Context c) {
        SharedPreferences s = sp(c);
        if (s.getBoolean("placesMigrated", false)) return;
        String home = s.getString("homeCoord", "");
        if (!home.isEmpty() && coord(c, 1).isEmpty()) {
            s.edit().putString("place1Name", "Ev").putString("place1Icon", "home")
                    .putString("place1Coord", home).putString("place1Label", s.getString("home", ""))
                    .apply();
        }
        s.edit().putBoolean("placesMigrated", true).apply();
    }
}
