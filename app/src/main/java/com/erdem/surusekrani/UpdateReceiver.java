package com.erdem.surusekrani;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Kopilot'un içinden güncelleme: telefon onay isteyince onay ekranını açar, hata olursa söyler. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = in.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(confirm);
            } catch (Exception e) {
                Toast.makeText(c, "Güncelleme onayı açılamadı", Toast.LENGTH_LONG).show();
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS && st != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = in.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            android.util.Log.w("Kopilot", "güncelleme kurulamadı: " + st + " " + msg);
            Toast.makeText(c, "Güncelleme kurulamadı" + (msg == null ? "" : ": " + msg), Toast.LENGTH_LONG).show();
        }
    }
}
