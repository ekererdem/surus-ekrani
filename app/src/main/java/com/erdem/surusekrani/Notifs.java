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
    interface Banner { void show(Item it); }

    static final class Item {
        String key, pkg, who, app, text, full;
        int count;
        long when;
        boolean missedCall;
        PendingIntent open;
        Drawable icon;
    }

    static Listener listener;
    static Banner banner;
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
        posted(c, sbn, true);
    }

    static void clear() {
        MAIN.post(() -> {
            ITEMS.clear();
            if (listener != null) listener.onNotifsChanged();
        });
    }

    static void posted(Context c, StatusBarNotification sbn, boolean allowBanner) {
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
        // Son mesajın metni (alttaki kısa bilgi şeridi için)
        CharSequence txt = null;
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof android.os.Bundle)
            txt = ((android.os.Bundle) msgs[msgs.length - 1]).getCharSequence("text");
        if (txt == null) txt = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        it.text = txt == null ? "" : txt.toString().trim();
        it.full = fullText(n, msgs, it.text);
        it.open = n.contentIntent;
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(it.pkg, 0);
            it.app = pm.getApplicationLabel(ai).toString();
            it.icon = pm.getApplicationIcon(ai);
        } catch (Exception e) {
            it.app = "";
        }
        // Yeni gelen (birkaç saniye içinde gönderilmiş) bildirimde alt şeridi göster
        boolean fresh = allowBanner && System.currentTimeMillis() - it.when < 15000;
        MAIN.post(() -> {
            ITEMS.remove(it.key);
            ITEMS.put(it.key, it); // en yeni en sonda
            if (listener != null) listener.onNotifsChanged();
            if (fresh && banner != null) banner.show(it);
        });
    }

    /** Bildirimin tamamı: sohbet bildirimlerinde son 10 mesaj (grupta gönderenle), yoksa uzun metin. */
    private static String fullText(Notification n, Parcelable[] msgs, String last) {
        boolean group = n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
        if (msgs != null && msgs.length > 0) {
            StringBuilder b = new StringBuilder();
            for (int i = Math.max(0, msgs.length - 10); i < msgs.length; i++) {
                if (!(msgs[i] instanceof android.os.Bundle)) continue;
                android.os.Bundle m = (android.os.Bundle) msgs[i];
                CharSequence t = m.getCharSequence("text");
                if (t == null) continue;
                CharSequence who = m.getCharSequence("sender");
                if (b.length() > 0) b.append("\n");
                if (group && who != null) b.append(who).append(": ");
                b.append(t.toString().trim());
            }
            if (b.length() > 0) return b.toString();
        }
        CharSequence big = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (big != null && big.length() > 0) return big.toString().trim();
        CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            StringBuilder b = new StringBuilder();
            for (CharSequence l : lines) { if (b.length() > 0) b.append("\n"); b.append(l); }
            return b.toString();
        }
        return last;
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
