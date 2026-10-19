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
        /** Telefonun bildirimini biz kaldırdık (ekrandayken yukarıdan kaymasın diye); kutuda kalır. */
        boolean swallowed;
        PendingIntent open;
        Drawable icon;
    }

    static Listener listener;
    static Banner banner;
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Bizim kaldırdığımız bildirimler: "kaldırıldı" haberi gelince kutudan silinmesin. */
    private static final java.util.Set<String> SWALLOWED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    /** Kutuda okunup kapatılanlar (anahtar → bildirim zamanı): telefonda dursa da kutuya geri gelmesin. */
    private static final Map<String, Long> SEEN = java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    private Notifs() {}

    /** Ayarlar'da her zaman listelenen mesajlaşma uygulamaları (telefonda yüklüyse). */
    private static final String[] MESSENGERS = {
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging",
            "com.android.mms", "com.facebook.orca", "com.instagram.android", "org.thoughtcrime.securesms",
            "com.viber.voip", "com.google.android.dialer", "com.android.incallui"};

    private static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    /** Bu uygulamanın mesajları kutuda görünsün mü (Ayarlar > Bildirim kutusu). */
    static boolean allowed(Context c, String pkg) {
        return !sp(c).getStringSet("notifOff", java.util.Collections.emptySet()).contains(pkg);
    }

    static void allow(Context c, String pkg, boolean on) {
        java.util.Set<String> off = new java.util.HashSet<>(
                sp(c).getStringSet("notifOff", java.util.Collections.emptySet()));
        if (on) off.remove(pkg); else off.add(pkg);
        sp(c).edit().putStringSet("notifOff", off).apply();
        if (!on) MAIN.post(() -> {
            if (ITEMS.values().removeIf(it -> pkg.equals(it.pkg)) && listener != null) listener.onNotifsChanged();
        });
    }

    /** Ayarlar'da gösterilecek uygulamalar: bilinen mesajlaşma uygulamaları + mesaj göndermiş diğerleri. */
    static List<String> candidates(Context c) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>(java.util.Arrays.asList(MESSENGERS));
        s.addAll(sp(c).getStringSet("notifSeen", java.util.Collections.emptySet()));
        return new ArrayList<>(s);
    }

    /** Mesaj bildirimi gönderen uygulamayı adıyla hatırla (Ayarlar'daki listede çıksın). */
    private static void remember(Context c, StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        java.util.Set<String> seen = sp(c).getStringSet("notifSeen", java.util.Collections.emptySet());
        if (seen.contains(pkg)) return;
        java.util.Set<String> n = new java.util.HashSet<>(seen);
        n.add(pkg);
        android.content.SharedPreferences.Editor e = sp(c).edit().putStringSet("notifSeen", n);
        // Adı bildirimin içindeki uygulama bilgisinden (telefon bu uygulamayı bize göstermese de)
        Object o = sbn.getNotification().extras.get("android.appInfo");
        if (o instanceof android.content.pm.ApplicationInfo) {
            try {
                e.putString("notifLabel." + pkg, c.getPackageManager()
                        .getApplicationLabel((android.content.pm.ApplicationInfo) o).toString());
            } catch (Exception ignored) {
            }
        }
        e.apply();
    }

    /** Ayarlar için: hatırlanan uygulama adı (yoksa null). */
    static String label(Context c, String pkg) {
        return sp(c).getString("notifLabel." + pkg, null);
    }

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

    /** Telefondan yeniden okumadan önce: bizim kaldırdıklarımız (telefonda artık yoklar) kutuda kalsın. */
    static void clear() {
        MAIN.post(() -> {
            ITEMS.values().removeIf(it -> !it.swallowed);
            if (listener != null) listener.onNotifsChanged();
        });
    }

    /** "Temizle": kutuyu tamamen boşalt. */
    static void clearAll() {
        MAIN.post(() -> {
            for (Item it : ITEMS.values()) SEEN.put(it.key, it.when);
            ITEMS.clear();
            if (listener != null) listener.onNotifsChanged();
        });
    }

    static void posted(Context c, StatusBarNotification sbn, boolean allowBanner) {
        if (!relevant(c, sbn)) return;
        remember(c, sbn);
        if (!allowed(c, sbn.getPackageName())) return; // Ayarlar'da kapatılmış: telefonda normal bildirim olarak kalır
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
        Long seenAt = SEEN.get(it.key);
        if (seenAt != null) {
            if (it.when <= seenAt) return; // okunmuş; yeni mesaj gelince yine gösterilir
            SEEN.remove(it.key);
        }
        it.missedCall = Notification.CATEGORY_MISSED_CALL.equals(n.category);
        // Son mesajın metni (alttaki kısa bilgi şeridi için)
        CharSequence txt = null;
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof android.os.Bundle)
            txt = ((android.os.Bundle) msgs[msgs.length - 1]).getCharSequence("text");
        if (txt == null) txt = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        it.text = txt == null ? "" : txt.toString().trim();
        it.full = fullText(n, msgs, it.text);
        it.open = n.contentIntent;
        // Uygulamanın adı ve simgesi: önce bildirimin içindeki uygulama bilgisinden (paket görünürlüğüne takılmaz),
        // olmazsa paket yöneticisinden, o da olmazsa bildirimin küçük simgesinden.
        android.content.pm.PackageManager pm = c.getPackageManager();
        android.content.pm.ApplicationInfo ai = null;
        Object o = n.extras.get("android.appInfo"); // (sınıflı getParcelable Android 13+; uygulama 11'den başlıyor)
        if (o instanceof android.content.pm.ApplicationInfo) ai = (android.content.pm.ApplicationInfo) o;
        try {
            if (ai == null) ai = pm.getApplicationInfo(it.pkg, 0);
        } catch (Exception ignored) {
        }
        it.app = "";
        if (ai != null) {
            try { it.app = pm.getApplicationLabel(ai).toString(); } catch (Exception ignored) { }
            try { it.icon = pm.getApplicationIcon(ai); } catch (Exception ignored) { }
        }
        if (it.icon == null && n.getSmallIcon() != null) {
            try { it.icon = n.getSmallIcon().loadDrawable(c); } catch (Exception ignored) { }
        }
        // Yeni gelen (birkaç saniye içinde gönderilmiş) bildirimde alt şeridi göster
        boolean fresh = allowBanner && System.currentTimeMillis() - it.when < 15000;
        // Ana ekran açıkken telefonun yukarıdan kayan bildirimi görünmesin: hemen kaldır, kutuda tut.
        // (Gelen arama bu listede yok; ona dokunulmaz.)
        if (fresh && MainActivity.sResumed
                && c instanceof android.service.notification.NotificationListenerService) {
            it.swallowed = true;
            SWALLOWED.add(it.key);
            try {
                ((android.service.notification.NotificationListenerService) c).cancelNotification(it.key);
                android.util.Log.i("Kopilot", "kayan bildirim kaldırıldı: " + it.pkg);
            } catch (Exception e) {
                android.util.Log.w("Kopilot", "kayan bildirim kaldırılamadı: " + e);
                SWALLOWED.remove(it.key);
                it.swallowed = false;
            }
        } else if (fresh) {
            android.util.Log.i("Kopilot", "kaldırılmadı: ekranda=" + MainActivity.sResumed);
        }
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
        if (SWALLOWED.remove(key)) return; // biz kaldırdık, kutuda kalsın
        MAIN.post(() -> {
            if (ITEMS.remove(key) != null && listener != null) listener.onNotifsChanged();
        });
    }

    /** Ana ekranda gizle (bildirim telefonda kalır). */
    static void hide(String key) {
        Item it = ITEMS.remove(key);
        if (it != null) SEEN.put(key, it.when);
        if (it != null && listener != null) listener.onNotifsChanged();
    }

    /** Okundu: kutudan kaldır (bildirim telefonda kalır, yeni mesaj gelince yine görünür). */
    static void seen(List<Item> items) {
        boolean changed = false;
        for (Item it : items) {
            SEEN.put(it.key, it.when);
            Item cur = ITEMS.get(it.key);
            if (cur != null && cur.when <= it.when) {
                ITEMS.remove(it.key);
                changed = true;
            }
        }
        if (changed && listener != null) listener.onNotifsChanged();
    }

    /** En yeniden eskiye. */
    static List<Item> list() {
        List<Item> l = new ArrayList<>(ITEMS.values());
        java.util.Collections.reverse(l);
        return l;
    }
}
