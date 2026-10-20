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
        /** Sohbet mesajı mı (değilse kutuda "bildirim" yazar). */
        boolean msg;
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

    private static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    /** Telefonun mesaj (SMS) uygulaması. */
    private static String smsApp(Context c) {
        try {
            return android.provider.Telephony.Sms.getDefaultSmsPackage(c);
        } catch (Exception e) {
            return null;
        }
    }

    /** Telefonun arama uygulaması. */
    private static String phoneApp(Context c) {
        try {
            android.telecom.TelecomManager tm = c.getSystemService(android.telecom.TelecomManager.class);
            return tm == null ? null : tm.getDefaultDialerPackage();
        } catch (Exception e) {
            return null;
        }
    }

    /** Hazır seçim: WhatsApp, Mesajlar ve Telefon (aramalar). */
    private static java.util.Set<String> defaults(Context c) {
        java.util.Set<String> d = new java.util.HashSet<>(java.util.Arrays.asList(
                "com.whatsapp", "com.whatsapp.w4b",
                "com.google.android.apps.messaging", "com.android.mms",
                "com.google.android.dialer", "com.android.dialer"));
        String sms = smsApp(c), phone = phoneApp(c);
        if (sms != null) d.add(sms);
        if (phone != null) d.add(phone);
        return d;
    }

    /** Bildirimleri kutuya gelen uygulamalar (Ayarlar > Bildirim kutusu > Değiştir). */
    static java.util.Set<String> selected(Context c) {
        java.util.Set<String> s = sp(c).getStringSet("notifApps", null);
        if (s == null) return defaults(c);
        // Bir kereliğine: önceden seçim yapıldıysa WhatsApp, Mesajlar ve Telefon da eklensin (diğerleri kalır)
        if (!sp(c).getBoolean("notifDefaults2", false)) {
            java.util.Set<String> n = new java.util.HashSet<>(s);
            n.addAll(defaults(c));
            sp(c).edit().putStringSet("notifApps", n).putBoolean("notifDefaults2", true).apply();
            return n;
        }
        return s;
    }

    static void setSelected(Context c, java.util.Set<String> apps) {
        sp(c).edit().putStringSet("notifApps", new java.util.HashSet<>(apps)).putBoolean("notifDefaults2", true).apply();
        boolean calls = callsOn(c, apps);
        MAIN.post(() -> {
            if (ITEMS.values().removeIf(it -> it.missedCall ? !calls : !apps.contains(it.pkg)) && listener != null)
                listener.onNotifsChanged();
        });
    }

    /** Cevapsız aramalar: listede telefonun arama uygulaması seçiliyse gelir. */
    private static boolean callsOn(Context c, java.util.Set<String> sel) {
        String phone = phoneApp(c);
        return (phone != null && sel.contains(phone))
                || sel.contains("com.google.android.dialer") || sel.contains("com.android.dialer");
    }

    private static boolean relevant(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (pkg.equals(c.getPackageName())) return false;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        if (Notification.CATEGORY_MISSED_CALL.equals(n.category)) {
            java.util.Set<String> sel = selected(c);
            return sel.contains(pkg) || callsOn(c, sel);
        }
        // Müzik, indirme, sistem, yol tarifi gibi bildirimler kutuya gelmez
        String cat = n.category == null ? "" : n.category;
        switch (cat) {
            case Notification.CATEGORY_TRANSPORT:
            case Notification.CATEGORY_PROGRESS:
            case Notification.CATEGORY_SERVICE:
            case Notification.CATEGORY_SYSTEM:
            case Notification.CATEGORY_NAVIGATION:
            case Notification.CATEGORY_CALL:
            case Notification.CATEGORY_ALARM:
            case Notification.CATEGORY_STOPWATCH:
            case Notification.CATEGORY_LOCATION_SHARING:
                return false;
            default:
                return selected(c).contains(pkg);
        }
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
        it.msg = Notification.CATEGORY_MESSAGE.equals(n.category) || it.pkg.startsWith("com.whatsapp")
                || (msgs != null && msgs.length > 0);
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
