package com.erdem.surusekrani;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Uygulama ekrandayken telefonun yukarıdan inen bildirimleri gelmesin.
 * Bunun için kısa süreliğine "Rahatsız Etmeyin" açılır; ayarı sadece:
 * aramalar (herkesten), alarmlar ve medya sesi serbest; bildirimler panelde ve durum çubuğunda kalır,
 * sadece yukarıdan inme (peek) engellenir. Uygulamadan çıkınca kullanıcının eski ayarı aynen geri yüklenir.
 * Kullanıcı zaten Rahatsız Etmeyin'deyse hiçbir şeye dokunulmaz.
 */
final class Quiet {
    private Quiet() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return sp(c).getBoolean("quietNotifs", true); }

    static boolean hasAccess(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    static void on(Context c) {
        if (!enabled(c) || !hasAccess(c)) return;
        SharedPreferences sp = sp(c);
        if (sp.getBoolean("quietOn", false)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try {
            if (nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) return;
            NotificationManager.Policy old = nm.getNotificationPolicy();
            sp.edit().putBoolean("quietOn", true)
                    .putInt("qCat", old.priorityCategories)
                    .putInt("qCall", old.priorityCallSenders)
                    .putInt("qMsg", old.priorityMessageSenders)
                    .putInt("qFx", old.suppressedVisualEffects)
                    .putInt("qConv", old.priorityConversationSenders)
                    .commit();
            int cats = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
                    | NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM;
            nm.setNotificationPolicy(new NotificationManager.Policy(cats,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK));
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (Exception e) {
            off(c);
        }
    }

    /** Eski ayarları geri yükle (sadece biz açtıysak). */
    static void off(Context c) {
        SharedPreferences sp = sp(c);
        if (!sp.getBoolean("quietOn", false)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try {
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                if (nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                nm.setNotificationPolicy(new NotificationManager.Policy(sp.getInt("qCat", 0),
                        sp.getInt("qCall", 0), sp.getInt("qMsg", 0), sp.getInt("qFx", 0),
                        sp.getInt("qConv", NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT)));
            }
        } catch (Exception ignored) {
        }
        sp.edit().putBoolean("quietOn", false).commit();
    }
}
