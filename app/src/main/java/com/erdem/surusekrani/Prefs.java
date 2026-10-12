package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;

/** Uygulama ayarları. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    String nav() { return sp.getString("nav", "gmaps"); }
    String music() { return sp.getString("music", "spotify"); }
    String home() { return sp.getString("home", ""); }
    String work() { return sp.getString("work", ""); }
    String carAddr() { return sp.getString("carAddr", ""); }
    String carName() { return sp.getString("carName", ""); }
    boolean autoOpen() { return sp.getBoolean("autoOpen", true); }
    String units() { return sp.getString("units", "km"); }
    boolean autoSplit() { return sp.getBoolean("autoSplit", true); }
    String navMode() { return sp.getString("navMode", "split"); }
    boolean autoPlay() { return sp.getBoolean("autoPlay", false); }

    SharedPreferences.Editor edit() { return sp.edit(); }
}
