package com.erdem.surusekrani;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.view.KeyEvent;
import android.widget.Toast;

/** Navigasyon ve müzik uygulamalarını açan yardımcılar. */
final class Apps {
    static final String MAPS = "com.google.android.apps.maps";
    static final String WAZE = "com.waze";
    static final String SPOTIFY = "com.spotify.music";
    static final String YTM = "com.google.android.apps.youtube.music";

    private Apps() {}

    static String navName(String key) { return "waze".equals(key) ? "Waze" : "Google Maps"; }
    static String musicName(String key) { return "ytm".equals(key) ? "YouTube Music" : "Spotify"; }
    static String musicPkg(String key) { return "ytm".equals(key) ? YTM : SPOTIFY; }

    /** dest boşsa uygulamayı açar, doluysa o adrese navigasyonu başlatır. */
    static void openNav(Activity a, Prefs p, String dest) {
        boolean waze = "waze".equals(p.nav());
        String pkg = waze ? WAZE : MAPS;
        Intent i;
        if (dest == null || dest.trim().isEmpty()) {
            i = a.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { openStore(a, pkg); return; }
        } else if (waze) {
            i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://waze.com/ul?navigate=yes&q=" + Uri.encode(dest.trim())));
            i.setPackage(WAZE);
        } else {
            i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=" + Uri.encode(dest.trim()) + "&mode=d"));
            i.setPackage(MAPS);
        }
        start(a, i, pkg);
    }

    static void openMusic(Activity a, Prefs p) {
        String pkg = musicPkg(p.music());
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { openStore(a, pkg); return; }
        start(a, i, pkg);
    }

    private static void start(Activity a, Intent i, String pkg) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            openStore(a, pkg);
        }
    }

    private static void openStore(Activity a, String pkg) {
        Toast.makeText(a, "Uygulama yüklü değil, Play Store açılıyor", Toast.LENGTH_SHORT).show();
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Son çalan müziği (ör. Spotify) devam ettirir. */
    static void playMedia(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
    }
}
