package com.erdem.surusekrani;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ana ekrandaki bildirim kutusu için: sadece mesaj (WhatsApp, SMS) ve cevapsız arama bildirimleri.
 * Sürüş sırasında dikkat dağıtmasın diye mesaj metni tutulmaz; kimden ve kaç tane.
 */
final class Notifs {
    interface Listener { void onNotifsChanged(); }

    static final class Item {
        String key, pkg, who, app;
        int count;
        long when;
        boolean missedCall;
        PendingIntent open;
        Drawable icon;
    }

    static Listener listener;
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Notifs() {}

    private static boolean relevant(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (pkg.equals(c.getPackageName())) return false;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        if (pkg.startsWith("com.whatsapp")) return true;
        return Notification.CATEGORY_MESSAGE.equals(n.category)
                || Notification.CATEGORY_MISSED_CALL.equals(n.category);
    }

    static void posted(Context c, StatusBarNotification sbn) {
        if (!relevant(c, sbn)) return;
        Notification n = sbn.getNotification();
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (title == null) title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null || title.toString().trim().isEmpty()) return;
        Item it = new Item();
        it.key = sbn.getKey();
        it.pkg = sbn.getPackageName();
        it.who = title.toString().trim();
        Parcelable[] msgs = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        it.count = msgs != null && msgs.length > 0 ? msgs.length : Math.max(1, n.number);
        it.when = sbn.getPostTime();
        it.missedCall = Notification.CATEGORY_MISSED_CALL.equals(n.category);
        it.open = n.contentIntent;
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(it.pkg, 0);
            it.app = pm.getApplicationLabel(ai).toString();
            it.icon = pm.getApplicationIcon(ai);
        } catch (Exception e) {
            it.app = "";
        }
        MAIN.post(() -> {
            ITEMS.remove(it.key);
            ITEMS.put(it.key, it); // en yeni en sonda
            if (listener != null) listener.onNotifsChanged();
        });
    }

    static void removed(StatusBarNotification sbn) {
        String key = sbn.getKey();
        MAIN.post(() -> {
            if (ITEMS.remove(key) != null && listener != null) listener.onNotifsChanged();
        });
    }

    /** Ana ekranda gizle (bildirim telefonda kalır). */
    static void hide(String key) {
        if (ITEMS.remove(key) != null && listener != null) listener.onNotifsChanged();
    }

    /** En yeniden eskiye. */
    static List<Item> list() {
        List<Item> l = new ArrayList<>(ITEMS.values());
        java.util.Collections.reverse(l);
        return l;
    }
}
