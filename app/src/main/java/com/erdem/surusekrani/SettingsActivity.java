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
    private EditText home, plate;

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

        RadioGroup scale = findViewById(R.id.radioScale);
        float sc = getSharedPreferences("surus", MODE_PRIVATE).getFloat("scale", 1f);
        scale.check(sc <= 0.71f ? R.id.rbScaleXS : sc <= 0.86f ? R.id.rbScaleS : sc >= 1.1f ? R.id.rbScaleL : R.id.rbScaleM);
        scale.setOnCheckedChangeListener((g, id) -> p.edit().putFloat("scale",
                id == R.id.rbScaleXS ? 0.7f : id == R.id.rbScaleS ? 0.85f : id == R.id.rbScaleL ? 1.15f : 1f).apply());

        RadioGroup units = findViewById(R.id.radioUnits);
        units.check("km".equals(p.units()) ? R.id.rbKm : R.id.rbMiles);
        units.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("units", id == R.id.rbKm ? "km" : "mi").apply());

        plate = findViewById(R.id.editPlate);
        plate.setText(p.plate());
        home = findViewById(R.id.editHome);
        home.setText(p.home());
        setupHome();

        Switch autoOpen = findViewById(R.id.swAutoOpen);
        autoOpen.setChecked(p.autoOpen());
        autoOpen.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoOpen", on).apply());

        Switch autoSplit = findViewById(R.id.swAutoSplit);
        autoSplit.setChecked(p.autoSplit());
        autoSplit.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoSplit", on).apply());

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
        savedHomeLabel = p.home();
        home.setText(savedHomeLabel);
        renderHomeStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        String typed = home.getText().toString().trim();
        android.content.SharedPreferences.Editor e = p.edit()
                .putString("plate", plate.getText().toString().trim().toUpperCase(new java.util.Locale("tr", "TR")))
                .putString("home", typed);
        // Yazı elle değiştirildiyse kayıtlı tam konum artık geçersiz.
        if (!typed.equals(savedHomeLabel)) e.remove("homeCoord");
        e.apply();
    }

    // ---------- Ev adresi ----------

    private String savedHomeLabel = "";
    private final java.util.concurrent.ExecutorService io =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void setupHome() {
        savedHomeLabel = p.home();
        findViewById(R.id.homeSearchBtn).setOnClickListener(v -> openPicker());
        findViewById(R.id.homeHereBtn).setOnClickListener(v -> homeFromHere());
        home.setOnClickListener(v -> openPicker());
        renderHomeStatus();
    }

    private void renderHomeStatus() {
        TextView t = findViewById(R.id.homeStatus);
        String coord = getSharedPreferences("surus", MODE_PRIVATE).getString("homeCoord", "");
        if (!coord.isEmpty()) {
            t.setText("Kaydedildi ✓ Ev tuşu tam bu konuma rota başlatır.");
            t.setTextColor(getColor(R.color.teal));
        } else if (!p.home().isEmpty()) {
            t.setText("Tam konum kayıtlı değil. Haritada işaretle'ye basıp evini seç.");
            t.setTextColor(getColor(R.color.muted));
        } else {
            t.setText("");
        }
    }

    private void saveHome(String label, double lat, double lon) {
        savedHomeLabel = label;
        home.setText(label);
        p.edit()
                .putString("home", label)
                .putString("homeCoord", String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon))
                .apply();
        renderHomeStatus();
        Toast.makeText(this, "Ev adresi kaydedildi", Toast.LENGTH_SHORT).show();
    }

    private android.location.Location lastLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
        try {
            android.location.Location g = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            android.location.Location n = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (g == null || (n != null && n.getTime() > g.getTime() + 60000)) return n;
            return g;
        } catch (SecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private void homeFromHere() {
        android.location.Location l = lastLocation();
        if (l == null) {
            Toast.makeText(this, "Konum bulunamadı. Konumu açıp ana ekranda haritanın yüklenmesini bekle, sonra tekrar dene.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        double lat = l.getLatitude(), lon = l.getLongitude();
        Toast.makeText(this, "Adres bulunuyor…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            String label = null;
            try {
                List<android.location.Address> r = new android.location.Geocoder(this, new java.util.Locale("tr", "TR"))
                        .getFromLocation(lat, lon, 1);
                if (r != null && !r.isEmpty() && r.get(0).getAddressLine(0) != null) {
                    label = r.get(0).getAddressLine(0);
                }
            } catch (Exception ignored) {
            }
            String finalLabel = label != null ? label : "Ev (kayıtlı konum)";
            runOnUiThread(() -> saveHome(finalLabel, lat, lon));
        });
    }

    private void openPicker() {
        startActivity(new Intent(this, MapPickActivity.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
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
