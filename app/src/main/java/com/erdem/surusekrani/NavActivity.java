package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Tam ekran uygulama içi navigasyon (dikey ekranda ya da gömülü harita yokken). İşi NavPanel yapar. */
public class NavActivity extends Activity {
    static final String EXTRA_DEST = "dest";
    static final String EXTRA_NAME = "destName";

    private NavPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout host = new FrameLayout(this);
        setContentView(host);
        panel = new NavPanel(this, host, false);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        String d = getIntent().getStringExtra(EXTRA_DEST);
        if (d != null && !d.trim().isEmpty()) panel.goTo(d, getIntent().getStringExtra(EXTRA_NAME));
    }

    @Override
    public void onBackPressed() {
        if (!panel.onBack()) super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        panel.onResume();
        hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        panel.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        panel.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        panel.onPermission();
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
