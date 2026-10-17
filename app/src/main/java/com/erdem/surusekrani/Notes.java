package com.erdem.surusekrani;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Yolda sesle alınan notlar. Arabadan inince bildirim olarak hatırlatılır. */
final class Notes {
    static final String EXTRA_SHOW = "showNotes";
    private static final String CHANNEL = "notes";
    private static final int NOTIF_ID = 9;

    private Notes() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    static void add(Context c, String text) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("notes", "[]"));
            a.put(new JSONObject().put("t", text).put("at", System.currentTimeMillis()));
            sp(c).edit().putString("notes", a.toString()).putBoolean("notesPending", true).apply();
        } catch (Exception ignored) {
        }
    }

    static List<String> list(Context c) {
        List<String> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("notes", "[]"));
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM HH:mm", new java.util.Locale("tr"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.add(f.format(new java.util.Date(o.getLong("at"))) + "  ·  " + o.getString("t"));
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    static void clear(Context c) {
        sp(c).edit().remove("notes").remove("notesPending").apply();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    /** Arabadan inince: yolda alınmış yeni not varsa bildir. */
    static void remindIfPending(Context c) {
        if (!sp(c).getBoolean("notesPending", false)) return;
        sp(c).edit().putBoolean("notesPending", false).apply();
        List<String> l = list(c);
        if (l.isEmpty()) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Sesli notlar",
                NotificationManager.IMPORTANCE_DEFAULT));
        StringBuilder body = new StringBuilder();
        for (String s : l) body.append("• ").append(s.substring(s.indexOf("·") + 2)).append('\n');
        PendingIntent pi = PendingIntent.getActivity(c, 3,
                new Intent(c, MainActivity.class).putExtra(EXTRA_SHOW, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Yolda " + l.size() + " not aldın")
                .setContentText(body.toString().trim())
                .setStyle(new Notification.BigTextStyle().bigText(body.toString().trim()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
        }
    }
}
