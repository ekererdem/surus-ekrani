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
    private boolean btConnected, charging, warnedCharge;
    private int battPct = 100;
    private Boolean night;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                renderBattery(i);
            } else {
                renderBt();
                // Profil bağlantısı ACL'den biraz sonra tamamlanır.
                btIcon.removeCallbacks(btRefresh);
                btIcon.postDelayed(btRefresh, 2000);
            }
        }
    };

    private final Runnable btRefresh = this::renderBt;

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            loadWeather();
            applyNight();
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
            showWeather(sp.getInt("wT", 0), sp.getInt("wC", 0), sp.getBoolean("wD", true),
                    sp.getBoolean("wR", false));
        }
    }

    boolean carConnected() { return btConnected; }

    void resume() {
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
        weatherTemp.removeCallbacks(weatherTick);
        signalIcon.removeCallbacks(signalTick);
        btIcon.removeCallbacks(btRefresh);
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
        this.charging = charging;
        battPct = pct;
        checkCharge();

        battText.setText((charging ? "⚡%" : "%") + pct);
        if (battIcon.getTag() == null) {
            battIcon.setImageResource(R.drawable.battery_level);
            battIcon.setTag("level");
        }
        // Az doluyken de bir çizgi görünsün
        battIcon.getDrawable().setLevel(Math.max(pct, 6) * 100);
        int color = a.getColor(charging ? R.color.teal : pct <= 15 || warnedCharge ? R.color.low : R.color.muted);
        battIcon.setImageTintList(ColorStateList.valueOf(color));
        battText.setTextColor(color);
    }

    private android.animation.ObjectAnimator blinkIcon, blinkText;

    /** Araba (Bluetooth) bağlıyken telefon şarj olmuyor ve pil düşükse pil simgesi kırmızı yanıp söner. */
    private void checkCharge() {
        boolean warn = btConnected && !charging && battPct <= 30;
        if (warn == warnedCharge) return;
        warnedCharge = warn;
        if (warn) {
            int red = a.getColor(R.color.low);
            battIcon.setImageTintList(ColorStateList.valueOf(red));
            battText.setTextColor(red);
            blinkIcon = blink(battIcon);
            blinkText = blink(battText);
        } else {
            if (blinkIcon != null) blinkIcon.cancel();
            if (blinkText != null) blinkText.cancel();
            blinkIcon = blinkText = null;
            battIcon.setAlpha(1f);
            battText.setAlpha(1f);
            int c = a.getColor(charging ? R.color.teal : battPct <= 15 ? R.color.low : R.color.muted);
            battIcon.setImageTintList(ColorStateList.valueOf(c));
            battText.setTextColor(c);
        }
    }

    private static android.animation.ObjectAnimator blink(View v) {
        android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(v, "alpha", 1f, 0.15f);
        an.setDuration(600);
        an.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        an.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        an.start();
        return an;
    }

    // ---------- Gece modu ----------

    /** Güneş batınca ekranı kıs ve renkleri yumuşat; gündüz eski haline dön. */
    private void applyNight() {
        boolean n;
        if (System.currentTimeMillis() - sp.getLong("wAt", 0) < 3 * 3600 * 1000L) {
            n = !sp.getBoolean("wD", true);
        } else {
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            n = h >= 20 || h < 6;
        }
        if (night != null && night == n) return;
        night = n;
        android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        lp.screenBrightness = n ? 0.2f : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        a.getWindow().setAttributes(lp);
        View root = a.findViewById(android.R.id.content);
        if (root != null) root.setAlpha(n ? 0.8f : 1f);
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
        btConnected = connected;
        checkCharge();
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
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&hourly=precipitation_probability,precipitation&forecast_hours=3&timezone=auto",
                l.getLatitude(), l.getLongitude());
        io.execute(() -> {
            try {
                JSONObject all = new JSONObject(Route.get(url));
                JSONObject cur = all.getJSONObject("current");
                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                boolean day = cur.optInt("is_day", 1) == 1;
                // Önümüzdeki 3 saatte yağmur bekleniyor mu (ya da şu an yağıyor mu)
                boolean rain = isWet(code);
                JSONObject hr = all.optJSONObject("hourly");
                if (hr != null) {
                    org.json.JSONArray pp = hr.optJSONArray("precipitation_probability");
                    org.json.JSONArray pr = hr.optJSONArray("precipitation");
                    for (int i = 0; pp != null && i < pp.length(); i++)
                        if (pp.optInt(i, 0) >= 50) rain = true;
                    for (int i = 0; pr != null && i < pr.length(); i++)
                        if (pr.optDouble(i, 0) >= 0.3) rain = true;
                }
                boolean r = rain;
                sp.edit().putInt("wT", t).putInt("wC", code).putBoolean("wD", day).putBoolean("wR", r)
                        .putLong("wAt", System.currentTimeMillis()).apply();
                a.runOnUiThread(() -> { showWeather(t, code, day, r); night = null; applyNight(); });
            } catch (Exception e) {
                lastWeather = 0; // bir dakika sonra tekrar dene
            }
        });
    }

    private static boolean isWet(int c) {
        return (c >= 51 && c <= 67) || (c >= 80 && c <= 82) || c >= 95;
    }

    private void showWeather(int temp, int code, boolean day, boolean rain) {
        weatherTemp.setText(icon(code, day) + " " + temp + "°");
        TextView tile = a.findViewById(R.id.weatherTile);
        if (tile != null) {
            tile.setText(icon(code, day) + " " + temp + "°");
            // Yağmur geliyorsa düğme maviye döner
            int col = a.getColor(R.color.rain);
            tile.setBackgroundTintList(rain ? ColorStateList.valueOf(col) : null);
            tile.setTextColor(a.getColor(rain ? R.color.rain : R.color.fg));
            tile.setContentDescription("Hava " + temp + " derece, " + describe(code)
                    + (rain ? ", yağmur bekleniyor" : ""));
        }
        weatherDesc.setText(describe(code));
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
