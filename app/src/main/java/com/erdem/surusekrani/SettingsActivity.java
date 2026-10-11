package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {
    private static final int REQ_BT = 1;
    private static final int REQ_NOTIF = 2;

    private Prefs p;
    private EditText home, work;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        p = new Prefs(this);

        RadioGroup nav = findViewById(R.id.radioNav);
        nav.check("waze".equals(p.nav()) ? R.id.rbWaze : R.id.rbMaps);
        nav.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("nav", id == R.id.rbWaze ? "waze" : "gmaps").apply());

        RadioGroup music = findViewById(R.id.radioMusic);
        music.check("ytm".equals(p.music()) ? R.id.rbYtm : R.id.rbSpotify);
        music.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("music", id == R.id.rbYtm ? "ytm" : "spotify").apply());

        RadioGroup units = findViewById(R.id.radioUnits);
        units.check("km".equals(p.units()) ? R.id.rbKm : R.id.rbMiles);
        units.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("units", id == R.id.rbKm ? "km" : "mi").apply());

        home = findViewById(R.id.editHome);
        work = findViewById(R.id.editWork);
        home.setText(p.home());
        work.setText(p.work());

        Switch autoOpen = findViewById(R.id.swAutoOpen);
        autoOpen.setChecked(p.autoOpen());
        autoOpen.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoOpen", on).apply());

        Switch autoPlay = findViewById(R.id.swAutoPlay);
        autoPlay.setChecked(p.autoPlay());
        autoPlay.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoPlay", on).apply());

        Switch launcher = findViewById(R.id.swLauncher);
        launcher.setChecked(isLauncherEnabled());
        launcher.setOnCheckedChangeListener((b, on) -> {
            setLauncherEnabled(on);
            if (on) {
                Toast.makeText(this, "Listeden Sürüş Ekranı'nı seç", Toast.LENGTH_LONG).show();
                safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
            }
        });

        findViewById(R.id.carBtn).setOnClickListener(v -> pickCar());
        findViewById(R.id.overlayBtn).setOnClickListener(v -> safeStart(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.notifBtn).setOnClickListener(v -> askNotifications());
        findViewById(R.id.xiaomiBtn).setOnClickListener(v -> openXiaomiPermissions());
        findViewById(R.id.homeAppBtn).setOnClickListener(v ->
                safeStart(new Intent(Settings.ACTION_HOME_SETTINGS)));
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.listenerBtn).setOnClickListener(v ->
                safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        findViewById(R.id.appInfoBtn).setOnClickListener(v -> safeStart(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        p.edit()
                .putString("home", home.getText().toString().trim())
                .putString("work", work.getText().toString().trim())
                .apply();
    }

    private void refreshStatus() {
        ((TextView) findViewById(R.id.carStatus)).setText(
                p.carName().isEmpty() ? "Henüz seçilmedi" : "Seçili: " + p.carName());
        ((TextView) findViewById(R.id.overlayStatus)).setText(
                Settings.canDrawOverlays(this) ? "Verildi ✓" : "Verilmedi. Otomatik açılma için gerekli.");
        NotificationManager lnm = getSystemService(NotificationManager.class);
        boolean listener = lnm != null && lnm.isNotificationListenerAccessGranted(
                new ComponentName(this, MediaListener.class));
        ((TextView) findViewById(R.id.listenerStatus)).setText(
                listener ? "Verildi ✓" : "Verilmedi. Çalan şarkı ve yön bilgisi için gerekli.");
        ((TextView) findViewById(R.id.notifStatus)).setText(
                notificationsReady() ? "Hazır ✓" : "Kapalı. Yedek bildirim için gerekli.");
    }

    // ---------- Araba seçimi ----------

    private void pickCar() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null) {
            Toast.makeText(this, "Bluetooth bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ad.isEnabled()) {
            Toast.makeText(this, "Önce Bluetooth'u aç", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<BluetoothDevice> bonded = ad.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "Eşleşmiş cihaz yok. Önce telefonu arabayla eşleştir.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<BluetoothDevice> list = new ArrayList<>(bonded);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i).getName();
            names[i] = (n == null || n.isEmpty()) ? list.get(i).getAddress() : n;
        }
        new AlertDialog.Builder(this)
                .setTitle("Araban hangisi?")
                .setItems(names, (dialog, which) -> {
                    p.edit()
                            .putString("carAddr", list.get(which).getAddress())
                            .putString("carName", names[which])
                            .apply();
                    refreshStatus();
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_BT) {
            if (granted) pickCar();
            else Toast.makeText(this, "Arabayı seçmek için Bluetooth izni gerekli", Toast.LENGTH_LONG).show();
        } else if (code == REQ_NOTIF) {
            askFullScreenIfNeeded();
            refreshStatus();
        }
    }

    // ---------- İzinler ----------

    private boolean notificationsReady() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true;
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            askFullScreenIfNeeded();
        }
    }

    private void askFullScreenIfNeeded() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                safeStart(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void openXiaomiPermissions() {
        Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", getPackageName());
        try {
            startActivity(miui);
        } catch (ActivityNotFoundException | SecurityException e) {
            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    // ---------- Ana ekran modu ----------

    private ComponentName homeAlias() {
        return new ComponentName(this, getPackageName() + ".HomeAlias");
    }

    private boolean isLauncherEnabled() {
        return getPackageManager().getComponentEnabledSetting(homeAlias())
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void setLauncherEnabled(boolean on) {
        getPackageManager().setComponentEnabledSetting(homeAlias(),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                   : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Bu ayar ekranı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }
}
