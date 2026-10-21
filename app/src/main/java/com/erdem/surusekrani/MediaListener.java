package com.erdem.surusekrani;

import android.app.Notification;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Bildirim erişimi. İki işe yarar:
 * 1) Ana ekranın çalan şarkıyı (MediaSession) görebilmesi için gereken izni sağlar.
 * 2) Google Maps / Waze navigasyon bildiriminden sıradaki dönüşü okur.
 */
public class MediaListener extends NotificationListenerService {

    /** Bağlı dinleyici; ana ekran açıldığında bildirimleri yeniden okumak için. */
    static MediaListener instance;

    /**
     * Müzik uygulamalarının bildirimindeki tuşlar (paket → tuşlar). Spotify bazı tuşları (ör. akıllı mix
     * şarkısında "beğenmedim") yalnız bildirimine koyuyor; ana ekran oradan alır.
     */
    static final java.util.Map<String, Notification.Action[]> MEDIA_ACTIONS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Müzik bildirimi değişince ana ekranın tuşları tazelemesi için. */
    static volatile Runnable onMedia;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private static void media(StatusBarNotification sbn, boolean removed) {
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null || !n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return;
        if (removed || n.actions == null) MEDIA_ACTIONS.remove(sbn.getPackageName());
        else MEDIA_ACTIONS.put(sbn.getPackageName(), n.actions);
        Runnable r = onMedia;
        if (r != null) MAIN.post(r);
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        listenPower();
        resync();
        LegacyDnd.restore(this);
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        try {
            unregisterReceiver(power);
        } catch (Exception ignored) {
        }
        // HyperOS dinleyiciyi kapatabiliyor: yeniden bağlanmayı iste
        try {
            requestRebind(new android.content.ComponentName(this, MediaListener.class));
        } catch (Exception ignored) {
        }
    }

    /** Telefon şarja takılınca (arabada) Kopilot arabaya bağlanmayı dener. */
    private final android.content.BroadcastReceiver power = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent in) {
            CarLink.onPower(c, android.content.Intent.ACTION_POWER_CONNECTED.equals(in.getAction()));
        }
    };

    private void listenPower() {
        android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_POWER_CONNECTED);
        f.addAction(android.content.Intent.ACTION_POWER_DISCONNECTED);
        try {
            unregisterReceiver(power);
        } catch (Exception ignored) {
        }
        try {
            registerReceiver(power, f, android.os.Build.VERSION.SDK_INT >= 33 ? android.content.Context.RECEIVER_NOT_EXPORTED : 0);
        } catch (Exception ignored) {
        }
    }

    /** Telefondaki mevcut bildirimleri baştan oku (alt şerit gösterilmez). */
    void resync() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            Notifs.clear();
            MEDIA_ACTIONS.clear();
            if (all != null) for (StatusBarNotification s : all) { handle(s); media(s, false); Notifs.posted(this, s, false); }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
        media(sbn, false);
        Notifs.posted(this, sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isNav(sbn)) NavState.clear(sbn.getPackageName());
        media(sbn, true);
        Notifs.removed(sbn);
    }

    private static boolean isNav(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!Apps.MAPS.equals(pkg) && !Apps.WAZE.equals(pkg) && !Apps.YANDEX.equals(pkg)
                && !"ru.yandex.yandexnavi".equals(pkg)) return false;
        Notification n = sbn.getNotification();
        return Notification.CATEGORY_NAVIGATION.equals(n.category)
                || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
    }

    private void handle(StatusBarNotification sbn) {
        if (!isNav(sbn)) return;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (title.isEmpty() && text.isEmpty()) return;

        Drawable icon = null;
        Icon large = n.getLargeIcon();
        if (large != null) {
            try {
                icon = large.loadDrawable(this);
            } catch (Exception ignored) {
            }
        }
        // Varış saati: bildirimdeki tüm yazılara bak (uygulamaya göre farklı yerde durur)
        StringBuilder all = new StringBuilder();
        for (String k : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
                Notification.EXTRA_TITLE_BIG}) {
            String v = str(e.getCharSequence(k));
            if (!v.isEmpty()) all.append(v).append(" | ");
        }
        CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence l : lines) all.append(str(l)).append(" | ");
        long eta = NavState.parseEta(all.toString(), System.currentTimeMillis());
        android.util.Log.i("Kopilot", "yol tarifi bildirimi (" + sbn.getPackageName() + "): " + all + " → varış " + eta);
        NavState.update(sbn.getPackageName(), title, text, sub, icon, eta);
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }
}
