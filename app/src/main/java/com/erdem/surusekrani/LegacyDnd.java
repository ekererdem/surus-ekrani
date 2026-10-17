package com.erdem.surusekrani;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * 10.8 sürümü uygulama ekrandayken "Rahatsız Etmeyin"i açıyordu (kaldırıldı).
 * O sürümden açık kalmışsa bir kere kapat ve kullanıcının eski ayarını geri yükle.
 */
final class LegacyDnd {
    private LegacyDnd() {}

    static void restore(Context c) {
        SharedPreferences sp = c.getSharedPreferences("surus", Context.MODE_PRIVATE);
        if (!sp.getBoolean("quietOn", false)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try {
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                if (nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                nm.setNotificationPolicy(new NotificationManager.Policy(sp.getInt("qCat", 0),
                        sp.getInt("qCall", 0), sp.getInt("qMsg", 0), sp.getInt("qFx", 0),
                        sp.getInt("qConv", NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT)));
            } else if (MediaListener.instance != null) {
                MediaListener.instance.requestInterruptionFilter(
                        android.service.notification.NotificationListenerService.INTERRUPTION_FILTER_ALL);
            } else {
                return; // şimdi yapamıyoruz, sonra tekrar dene
            }
        } catch (Exception ignored) {
        }
        sp.edit().putBoolean("quietOn", false).apply();
    }
}
