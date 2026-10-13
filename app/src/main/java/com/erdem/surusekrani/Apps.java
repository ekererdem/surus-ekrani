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

    static final String YANDEX = "ru.yandex.yandexmaps";

    static String navName(String key) {
        if ("waze".equals(key)) return "Waze";
        if ("yandex".equals(key)) return "Yandex Haritalar";
        return "Google Maps";
    }
    static String musicName(String key) { return "ytm".equals(key) ? "YouTube Music" : "Spotify"; }
    static String musicPkg(String key) { return "ytm".equals(key) ? YTM : SPOTIFY; }

    static String navPkg(String key) {
        if ("waze".equals(key)) return WAZE;
        if ("yandex".equals(key)) return YANDEX;
        return MAPS;
    }

    /** Seçili navigasyon uygulaması için intent. dest boşsa uygulamayı açar; "enlem,boylam" ya da adres olabilir. */
    private static Intent navIntent(Activity a, Prefs p, String dest) {
        String key = p.nav();
        String pkg = navPkg(key);
        if (dest == null || dest.trim().isEmpty()) {
            return a.getPackageManager().getLaunchIntentForPackage(pkg);
        }
        String d = dest.trim();
        Intent i;
        if ("waze".equals(key)) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?navigate=yes&q=" + Uri.encode(d)));
        } else if ("yandex".equals(key)) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").matcher(d);
            // Yandex Haritalar: rtext=~hedef → bulunduğun yerden hedefe araç rotası
            Uri u = m.matches()
                    ? Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~" + m.group(1) + "," + m.group(2) + "&rtt=auto")
                    : Uri.parse("yandexmaps://maps.yandex.ru/?text=" + Uri.encode(d));
            i = new Intent(Intent.ACTION_VIEW, u);
        } else {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d) + "&mode=d"));
        }
        i.setPackage(pkg);
        return i;
    }

    /** dest boşsa uygulamayı açar, doluysa o adrese navigasyonu başlatır. */
    static void openNav(Activity a, Prefs p, String dest) {
        String pkg = navPkg(p.nav());
        Intent i = navIntent(a, p, dest);
        if (i == null) { openStore(a, pkg); return; }
        start(a, i, pkg);
    }

    /** Navigasyon uygulamasını ekranın diğer yarısında açar (bölünmüş ekran). */
    static void openNavSplit(Activity a, Prefs p, String dest) {
        String pkg = navPkg(p.nav());
        Intent i = navIntent(a, p, dest);
        if (i == null) { openStore(a, pkg); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
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
