package com.whatsapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Test: WhatsApp gibi sohbet bildirimi gönderir (MessagingStyle, mesaj kategorisi). */
public class Post extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        String who = in.getStringExtra("who");
        if (who == null) who = "Annem";
        String[] texts = in.getStringArrayExtra("texts");
        if (texts == null) {
            String t = in.getStringExtra("text");
            texts = new String[]{t == null ? "Merhaba" : t};
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("msg", "Mesajlar", NotificationManager.IMPORTANCE_HIGH));
        // Test: telefon uygulaması gibi cevapsız arama bildirimi (kişi + tel: numarası + Geri ara tuşu)
        String missed = in.getStringExtra("missed");
        if (missed != null) {
            Person caller = new Person.Builder().setName(missed).setUri("tel:+905551112233").build();
            PendingIntent cb = PendingIntent.getBroadcast(c, 1, new Intent(c, Post.class), PendingIntent.FLAG_IMMUTABLE);
            Notification mn = new Notification.Builder(c, "msg")
                    .setSmallIcon(android.R.drawable.sym_call_missed)
                    .setContentTitle(missed)
                    .setContentText("Cevapsız arama")
                    .setCategory(Notification.CATEGORY_MISSED_CALL)
                    .addPerson(caller)
                    .addAction(new Notification.Action.Builder(null, "Geri ara", cb).build())
                    .build();
            nm.notify(777, mn);
            return;
        }
        Person me = new Person.Builder().setName("Ben").build();
        Person p = new Person.Builder().setName(who).build();
        Notification.MessagingStyle st = new Notification.MessagingStyle(me);
        long now = System.currentTimeMillis();
        for (int i = 0; i < texts.length; i++) st.addMessage(texts[i], now - (texts.length - i) * 20000L, p);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, Post.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "msg")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(who)
                .setContentText(texts[texts.length - 1])
                .setStyle(st)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(pi)
                .build();
        nm.notify(who.hashCode(), n);
    }
}
