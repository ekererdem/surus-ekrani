package com.whatsapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Test: sahte müzik bildirimindeki tuşlara dokunulunca logcat'e yazar. */
public class Tap extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        android.util.Log.i("FakeWA", "bildirim tusu: " + i.getAction());
    }
}
