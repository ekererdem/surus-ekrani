package com.erdem.surusekrani;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/** Büyük butonlu sürüş ekranı. */
public class MainActivity extends Activity {
    private Prefs p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = new Prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        findViewById(R.id.navTile).setOnClickListener(v -> Apps.openNav(this, p, null));
        findViewById(R.id.musicTile).setOnClickListener(v -> Apps.openMusic(this, p));
        findViewById(R.id.homeChip).setOnClickListener(v -> goTo(p.home()));
        findViewById(R.id.workChip).setOnClickListener(v -> goTo(p.work()));
        findViewById(R.id.appsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, AppsActivity.class)));
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        handleCarIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCarIntent(intent);
    }

    private void handleCarIntent(Intent i) {
        if (i != null && i.getBooleanExtra(BtReceiver.EXTRA_FROM_CAR, false)) {
            i.removeExtra(BtReceiver.EXTRA_FROM_CAR);
            if (p.autoPlay()) Apps.playMedia(this);
        }
    }

    private void goTo(String addr) {
        if (addr.trim().isEmpty()) {
            Toast.makeText(this, "Önce Ayarlar'dan adres ekle", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            Apps.openNav(this, p, addr);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(BtReceiver.NOTIF_ID);

        ((TextView) findViewById(R.id.navSub)).setText(Apps.navName(p.nav()));
        ((TextView) findViewById(R.id.musicSub)).setText(Apps.musicName(p.music()));
        ((TextView) findViewById(R.id.homeSub)).setText(
                p.home().isEmpty() ? "Adres eklemek için Ayarlar" : p.home());
        ((TextView) findViewById(R.id.workSub)).setText(
                p.work().isEmpty() ? "Adres eklemek için Ayarlar" : p.work());
        hideBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
