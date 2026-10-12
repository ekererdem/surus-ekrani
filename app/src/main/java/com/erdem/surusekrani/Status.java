package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Üst satırdaki hava durumu, Bluetooth ve pil göstergeleri. */
final class Status {
    private static final long WEATHER_EVERY = 15 * 60 * 1000L;

    private final Activity a;
    private final SharedPreferences sp;
    private final TextView weatherTemp, weatherDesc, battText;
    private final ImageView btIcon, battIcon, signalIcon;
    private final TextView speedText;
    private long lastSpeedAt;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long lastWeather;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                renderBattery(i);
            } else {
                renderBt();
                // Profil bağlantısı ACL'den biraz sonra tamamlanır.
                btIcon.postDelayed(Status.this::renderBt, 2000);
            }
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            loadWeather();
            weatherTemp.postDelayed(this, 60 * 1000L);
        }
    };

    Status(Activity a) {
        this.a = a;
        sp = a.getSharedPreferences("surus", Context.MODE_PRIVATE);
        weatherTemp = a.findViewById(R.id.weatherTemp);
        weatherDesc = a.findViewById(R.id.weatherDesc);
        battText = a.findViewById(R.id.battText);
        btIcon = a.findViewById(R.id.btIcon);
        battIcon = a.findViewById(R.id.battIcon);
        signalIcon = a.findViewById(R.id.signalIcon);
        speedText = a.findViewById(R.id.speedText);

        // Son bilinen hava durumunu hemen göster (3 saatten eskiyse gösterme).
        if (System.currentTimeMillis() - sp.getLong("wAt", 0) < 3 * 3600 * 1000L) {
            showWeather(sp.getInt("wT", 0), sp.getInt("wC", 0), sp.getBoolean("wD", true));
        }
    }

    void resume() {
        startSpeed();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        f.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        Intent sticky = Build.VERSION.SDK_INT >= 33
                ? a.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
                : a.registerReceiver(receiver, f);
        if (sticky != null) renderBattery(sticky);
        renderBt();
        weatherTemp.removeCallbacks(weatherTick);
        weatherTemp.post(weatherTick);
        signalIcon.removeCallbacks(signalTick);
        signalIcon.post(signalTick);
    }

    void pause() {
        stopSpeed();
        weatherTemp.removeCallbacks(weatherTick);
        signalIcon.removeCallbacks(signalTick);
        btIcon.removeCallbacks(this::renderBt);
        try {
            a.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    void destroy() {
        io.shutdownNow();
    }

    // ---------- Hız (GPS) ----------

    private final android.location.LocationListener speedListener = this::onLocation;

    private final Runnable speedStale = new Runnable() {
        @Override public void run() {
            // 4 saniyedir yeni konum yoksa duruyoruz say
            if (System.currentTimeMillis() - lastSpeedAt > 4000) speedText.setText("0");
            speedText.postDelayed(this, 2000);
        }
    };

    private void startSpeed() {
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            speedText.setText("–");
            return;
        }
        try {
            LocationManager lm = a.getSystemService(LocationManager.class);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speedListener,
                    android.os.Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        speedText.removeCallbacks(speedStale);
        speedText.postDelayed(speedStale, 2000);
    }

    private void stopSpeed() {
        speedText.removeCallbacks(speedStale);
        try {
            a.getSystemService(LocationManager.class).removeUpdates(speedListener);
        } catch (Exception ignored) {
        }
    }

    private void onLocation(Location l) {
        lastSpeedAt = System.currentTimeMillis();
        int kmh = l.hasSpeed() ? Math.round(l.getSpeed() * 3.6f) : 0;
        if (kmh < 3) kmh = 0; // durağanken GPS titremesi
        speedText.setText(String.valueOf(kmh));
        Park.rememberLocation(a, l);
    }

    // ---------- Şebeke çekimi ----------

    private static final int[] SIGNAL = {
            R.drawable.ic_signal_0, R.drawable.ic_signal_1, R.drawable.ic_signal_2,
            R.drawable.ic_signal_3, R.drawable.ic_signal_4};

    private final Runnable signalTick = new Runnable() {
        @Override public void run() {
            renderSignal();
            signalIcon.postDelayed(this, 10 * 1000L);
        }
    };

    private void renderSignal() {
        int level = 0;
        try {
            android.telephony.TelephonyManager tm = a.getSystemService(android.telephony.TelephonyManager.class);
            android.telephony.SignalStrength ss = tm == null ? null : tm.getSignalStrength();
            if (ss != null) level = Math.max(0, Math.min(4, ss.getLevel()));
        } catch (Exception ignored) {
        }
        signalIcon.setImageResource(SIGNAL[level]);
        int color = a.getColor(level <= 1 ? R.color.low : R.color.muted);
        signalIcon.setImageTintList(ColorStateList.valueOf(color));
        signalIcon.setContentDescription("Şebeke çekimi " + level + "/4");
    }

    // ---------- Pil ----------

    private void renderBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int pct = Math.round(level * 100f / scale);

        battText.setText((charging ? "⚡%" : "%") + pct);
        if (battIcon.getTag() == null) {
            battIcon.setImageResource(R.drawable.battery_level);
            battIcon.setTag("level");
        }
        // Az doluyken de bir çizgi görünsün
        battIcon.getDrawable().setLevel(Math.max(pct, 6) * 100);
        int color = a.getColor(charging ? R.color.teal : pct <= 15 ? R.color.low : R.color.muted);
        battIcon.setImageTintList(ColorStateList.valueOf(color));
        battText.setTextColor(color);
    }

    // ---------- Bluetooth ----------

    private void renderBt() {
        boolean on = false, connected = false;
        try {
            BluetoothManager bm = a.getSystemService(BluetoothManager.class);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad != null) {
                on = ad.isEnabled();
                if (on && a.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    connected = ad.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                            || ad.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
                }
            }
        } catch (SecurityException ignored) {
        }
        btIcon.setVisibility(on ? View.VISIBLE : View.GONE);
        btIcon.setImageTintList(ColorStateList.valueOf(a.getColor(connected ? R.color.teal : R.color.muted)));
        btIcon.setAlpha(connected ? 1f : 0.55f);
        btIcon.setContentDescription(connected ? "Bluetooth bağlı" : "Bluetooth açık");
    }

    // ---------- Hava durumu (Open-Meteo, anahtar gerektirmez) ----------

    private Location lastLocation() {
        LocationManager lm = a.getSystemService(LocationManager.class);
        if (lm == null) return null;
        try {
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location n = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l == null || (n != null && n.getTime() > l.getTime())) l = n;
            return l;
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private void loadWeather() {
        if (System.currentTimeMillis() - lastWeather < WEATHER_EVERY) return;
        Location l = lastLocation();
        if (l == null) return;
        lastWeather = System.currentTimeMillis();
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto",
                l.getLatitude(), l.getLongitude());
        io.execute(() -> {
            try {
                JSONObject cur = new JSONObject(Route.get(url)).getJSONObject("current");
                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                boolean day = cur.optInt("is_day", 1) == 1;
                sp.edit().putInt("wT", t).putInt("wC", code).putBoolean("wD", day)
                        .putLong("wAt", System.currentTimeMillis()).apply();
                a.runOnUiThread(() -> showWeather(t, code, day));
            } catch (Exception e) {
                lastWeather = 0; // bir dakika sonra tekrar dene
            }
        });
    }

    private void showWeather(int temp, int code, boolean day) {
        weatherTemp.setText(icon(code, day) + " " + temp + "°");
        weatherDesc.setText(describe(code));
        weatherTemp.setVisibility(View.VISIBLE);
        weatherDesc.setVisibility(View.VISIBLE);
    }

    private static String icon(int c, boolean day) {
        if (c == 0) return day ? "☀️" : "🌙";
        if (c <= 2) return day ? "⛅" : "☁️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if (c >= 61 && c <= 67) return "🌧️";
        if (c >= 71 && c <= 77) return "❄️";
        if (c >= 80 && c <= 82) return "🌧️";
        if (c == 85 || c == 86) return "🌨️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }

    private static String describe(int c) {
        if (c == 0) return "Açık";
        if (c == 1) return "Az bulutlu";
        if (c == 2) return "Parçalı bulutlu";
        if (c == 3) return "Bulutlu";
        if (c == 45 || c == 48) return "Sisli";
        if (c >= 51 && c <= 57) return "Çiseleme";
        if (c == 66 || c == 67) return "Dondurucu yağmur";
        if (c >= 61 && c <= 65) return "Yağmurlu";
        if (c >= 71 && c <= 77) return "Karlı";
        if (c >= 80 && c <= 82) return "Sağanak";
        if (c == 85 || c == 86) return "Kar sağanağı";
        if (c >= 95) return "Gök gürültülü";
        return "";
    }
}
