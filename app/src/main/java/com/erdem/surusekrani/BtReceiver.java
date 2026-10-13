package com.erdem.surusekrani;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Araba Bluetooth'una bağlanınca sürüş ekranını açar. */
public class BtReceiver extends BroadcastReceiver {
    static final String EXTRA_FROM_CAR = "fromCar";
    static final int NOTIF_ID = 7;
    private static final String CHANNEL = "car";

    @Override
    public void onReceive(Context c, Intent in) {
        String action = in.getAction();
        boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
        boolean disconnected = BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
        if (!connected && !disconnected) return;

        Prefs p = new Prefs(c);
        if (p.carAddr().isEmpty()) return;

        BluetoothDevice d;
        if (Build.VERSION.SDK_INT >= 33) {
            d = in.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            d = in.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (d == null || !p.carAddr().equalsIgnoreCase(d.getAddress())) return;

        // Arabadan ayrılınca park yerini kaydet
        if (disconnected) {
            Park.saveHere(c);
            Rotation.restore(c);
            return;
        }
        if (!p.autoOpen()) return;

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_FROM_CAR, true);

        // Yedek: ekran doğrudan açılamazsa bildirim üzerinden açılır.
        postFallback(c, open);
        try {
            c.startActivity(open);
        } catch (Exception ignored) {
        }
    }

    private static void postFallback(Context c, Intent open) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Araba bağlantısı", NotificationManager.IMPORTANCE_HIGH));

        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_nav)
                .setContentTitle("Arabaya bağlandın")
                .setContentText("Sürüş Ekranı'nı açmak için dokun")
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
        }
    }
}
