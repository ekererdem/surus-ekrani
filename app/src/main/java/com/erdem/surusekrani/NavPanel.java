package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uygulama içi navigasyon (OpenStreetMap haritası, OSRM rotası, Türkçe sesli yön tarifi).
 * Hem tam ekran NavActivity'de hem ana ekranın içinde (yatayda haritanın yanında) kullanılır.
 */
final class NavPanel {
    private enum Mode { BROWSE, PREVIEW, NAV }

    private static final Pattern COORD = Pattern.compile("\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*");

    private final Activity a;
    private final boolean embedded;
    private final Prefs p;
    private Mode mode = Mode.BROWSE;

    private final MapView map;
    private MyLocationNewOverlay myLoc;
    private final EditText searchBox;
    private final ListView results;
    private final LinearLayout quickRow;
    private final View searchBar, turnCard, bottomPanel, recenterBtn, backBtn;
    private final ImageView turnArrow;
    private final TextView turnDist, turnText, destName, summary;
    private final Button mainBtn;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private boolean ttsReady;
    private AudioFocusRequest duck;

    private final LocationManager lm;
    private Location last;
    private Route route;
    private Route.Place dest;
    private boolean autoStart, waitingForFix, rerouting, updating, destroyed;
    private Polyline line;
    private Marker destMarker;
    private final List<Route.Place> found = new ArrayList<>();

    private int cur;
    private int spokenFor = -1;
    private boolean saidFar, saidNear, saidNow;
    private int offCount;
    private long lastTouch, lastBack;

    private final LocationListener locListener = this::onLocation;

    /** host: haritanın yerleşeceği kap (içine activity_nav açılır). */
    NavPanel(Activity a, ViewGroup host, boolean embedded) {
        this.a = a;
        this.embedded = embedded;
        p = new Prefs(a);
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(a, a.getSharedPreferences("osm", Context.MODE_PRIVATE));
        cfg.setUserAgentValue(a.getPackageName());
        File base = new File(a.getCacheDir(), "osm");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
        View root = LayoutInflater.from(a).inflate(R.layout.activity_nav, host, false);
        host.addView(root);

        searchBox = root.findViewById(R.id.searchBox);
        results = root.findViewById(R.id.results);
        quickRow = root.findViewById(R.id.quickRow);
        searchBar = root.findViewById(R.id.searchBar);
        turnCard = root.findViewById(R.id.turnCard);
        bottomPanel = root.findViewById(R.id.bottomPanel);
        recenterBtn = root.findViewById(R.id.recenterBtn);
        backBtn = root.findViewById(R.id.backBtn);
        turnArrow = root.findViewById(R.id.turnArrow);
        turnDist = root.findViewById(R.id.turnDist);
        turnText = root.findViewById(R.id.turnText);
        destName = root.findViewById(R.id.destName);
        summary = root.findViewById(R.id.summary);
        mainBtn = root.findViewById(R.id.mainBtn);
        map = root.findViewById(R.id.navMap);

        lm = a.getSystemService(LocationManager.class);
        setupMap();
        buildQuickRow();

        tts = new TextToSpeech(a, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Route.TR);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { releaseDuck(); }
                    @Override public void onError(String id) { releaseDuck(); }
                });
                ttsReady = true;
            }
        });

        backBtn.setOnClickListener(v -> onBack());
        root.findViewById(R.id.searchBtn).setOnClickListener(v -> doSearch(false));
        searchBox.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(false); return true; }
            return false;
        });
        results.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < found.size()) choose(found.get(pos));
        });
        recenterBtn.setOnClickListener(v -> recenter());
        mainBtn.setOnClickListener(v -> {
            if (mode == Mode.PREVIEW) startNav();
            else if (mode == Mode.NAV) stopNav();
        });
        Button ext = root.findViewById(R.id.extBtn);
        ext.setText(embedded ? "Yandex'te aç" : Apps.navName(p.nav()));
        ext.setOnClickListener(v -> openExternal());
        setMode(Mode.BROWSE);
    }

    // ---------- Dışarıdan ----------

    /** Adres ya da "enlem,boylam" ile rota başlat (Hızlı rota düğmeleri). */
    void goTo(String addr, String label) {
        if (addr == null || addr.trim().isEmpty()) {
            toast("Önce Ayarlar'dan adres ekle");
            return;
        }
        autoStart = true;
        Matcher m = COORD.matcher(addr);
        if (m.matches()) {
            choose(new Route.Place(label == null || label.isEmpty() ? "Hedef" : label,
                    new GeoPoint(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)))));
            return;
        }
        searchBox.setText(addr.trim());
        doSearch(true);
    }

    boolean navigating() { return mode == Mode.NAV; }

    void onResume() {
        map.onResume();
        startUpdates();
        buildQuickRow();
    }

    void onPause() {
        map.onPause();
        // Navigasyon sürerken başka uygulamaya geçilse de sesli yönlendirme devam etsin.
        if (mode != Mode.NAV) stopUpdates();
    }

    void onDestroy() {
        destroyed = true;
        stopUpdates();
        if (tts != null) tts.shutdown();
        releaseDuck();
        io.shutdownNow();
    }

    void onPermission() { startUpdates(); }

    /** Geri tuşu: sonuç listesi / rota önizleme / navigasyon. Kullanıldıysa true. */
    boolean onBack() {
        if (results.getVisibility() == View.VISIBLE) {
            results.setVisibility(View.GONE);
            quickRow.setVisibility(View.VISIBLE);
            refreshBack();
            return true;
        }
        if (mode == Mode.PREVIEW) {
            stopNav();
            return true;
        }
        if (mode == Mode.NAV) {
            long now = System.currentTimeMillis();
            if (now - lastBack < 2500) {
                stopNav();
            } else {
                lastBack = now;
                toast("Navigasyonu bitirmek için tekrar bas");
            }
            return true;
        }
        return false;
    }

    // ---------- Harita ----------

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getMapOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(39.92, 32.85));
        map.setOnTouchListener((v, e) -> {
            lastTouch = System.currentTimeMillis();
            return false;
        });

        GpsMyLocationProvider provider = new GpsMyLocationProvider(a);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLoc = new MyLocationNewOverlay(provider, map);
        myLoc.enableFollowLocation();
        map.getOverlays().add(myLoc);

        Location l = lastKnown();
        if (l != null) {
            last = l;
            map.getController().setCenter(new GeoPoint(l.getLatitude(), l.getLongitude()));
        }
    }

    /** Arama çubuğunun altında hızlı rotalar (Ayarlar'daki yerler). */
    private void buildQuickRow() {
        quickRow.removeAllViews();
        int n = Math.min(Places.count(a), 4);
        float d = a.getResources().getDisplayMetrics().density;
        for (int i = 1; i <= n; i++) {
            String name = Places.name(a, i), coord = Places.coord(a, i);
            Button b = new Button(a, null, 0, R.style.OutlineBtn);
            b.setText(name);
            b.setTextSize(16);
            b.setBackgroundResource(R.drawable.chip_bg);
            b.setPadding(Math.round(16 * d), 0, Math.round(16 * d), 0);
            android.graphics.drawable.Drawable ic = a.getDrawable(Places.iconRes(Places.icon(a, i))).mutate();
            int sz = Math.round(20 * d);
            ic.setBounds(0, 0, sz, sz);
            ic.setTint(a.getColor(R.color.amber));
            b.setCompoundDrawablesRelative(ic, null, null, null);
            b.setCompoundDrawablePadding(Math.round(8 * d));
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> goTo(coord, name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(52 * d));
            if (i > 1) lp.setMarginStart(Math.round(10 * d));
            quickRow.addView(b, lp);
        }
    }

    private boolean hasLocation() {
        return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Location lastKnown() {
        if (!hasLocation()) return null;
        try {
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private void startUpdates() {
        if (!hasLocation() || updating) return;
        updating = true;
        myLoc.enableMyLocation();
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locListener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, locListener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private void stopUpdates() {
        updating = false;
        myLoc.disableMyLocation();
        lm.removeUpdates(locListener);
    }

    private void onLocation(Location loc) {
        // GPS varken ağ konumunu yok say
        if (LocationManager.NETWORK_PROVIDER.equals(loc.getProvider()) && last != null
                && LocationManager.GPS_PROVIDER.equals(last.getProvider())
                && loc.getTime() - last.getTime() < 10000) {
            return;
        }
        last = loc;
        if (waitingForFix && dest != null) {
            waitingForFix = false;
            requestRoute();
        }
        if (mode == Mode.NAV) navUpdate(loc);
    }

    private GeoPoint here() {
        if (last != null) return new GeoPoint(last.getLatitude(), last.getLongitude());
        return myLoc.getMyLocation();
    }

    private void recenter() {
        lastTouch = 0;
        GeoPoint h = here();
        if (h != null) map.getController().animateTo(h);
        if (mode != Mode.NAV) myLoc.enableFollowLocation();
    }

    // ---------- Arama ----------

    private void doSearch(boolean pickFirst) {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) return;
        hideKeyboard();
        toast("Aranıyor…");
        GeoPoint near = here();
        io.execute(() -> {
            try {
                List<Route.Place> r = Route.search(q, near);
                a.runOnUiThread(() -> {
                    if (destroyed) return;
                    if (r.isEmpty()) { toast("Sonuç bulunamadı"); return; }
                    if (pickFirst) { choose(r.get(0)); return; }
                    showResults(r);
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> toast("Arama yapılamadı. İnternet bağlantını kontrol et."));
            }
        });
    }

    private void showResults(List<Route.Place> r) {
        found.clear();
        found.addAll(r);
        List<String> names = new ArrayList<>();
        for (Route.Place pl : r) names.add(pl.name);
        results.setAdapter(new ArrayAdapter<>(a, R.layout.item_result, names));
        results.setVisibility(View.VISIBLE);
        quickRow.setVisibility(View.GONE);
        refreshBack();
    }

    private void choose(Route.Place place) {
        results.setVisibility(View.GONE);
        hideKeyboard();
        dest = place;
        requestRoute();
    }

    // ---------- Rota ----------

    private void requestRoute() {
        GeoPoint from = here();
        if (from == null) {
            waitingForFix = true;
            toast("Konum bekleniyor…");
            return;
        }
        Route.Place target = dest;
        io.execute(() -> {
            try {
                Route r = Route.fetch(from, target.point);
                a.runOnUiThread(() -> onRoute(r));
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    rerouting = false;
                    toast("Rota hesaplanamadı. İnternet bağlantını kontrol et.");
                });
            }
        });
    }

    private void onRoute(Route r) {
        if (destroyed || dest == null) return;
        route = r;
        cur = 0;
        spokenFor = -1;
        drawRoute();
        // Hava kutusu varış yerinin havasını göstersin
        a.getSharedPreferences("surus", Context.MODE_PRIVATE).edit()
                .putString("destCoord", String.format(Locale.US, "%.6f,%.6f",
                        dest.point.getLatitude(), dest.point.getLongitude()))
                .putLong("destAt", System.currentTimeMillis()).apply();
        if (mode == Mode.NAV) {
            rerouting = false;
            if (last != null) navUpdate(last);
            return;
        }
        destName.setText(dest.name);
        setSummary(r.distance, r.duration);
        setMode(Mode.PREVIEW);
        myLoc.disableFollowLocation();
        map.post(() -> {
            try {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(r.points).increaseByScale(1.3f), true);
            } catch (Exception ignored) {
            }
        });
        if (autoStart) {
            autoStart = false;
            startNav();
        }
    }

    private void drawRoute() {
        clearRoute();
        line = new Polyline();
        line.setPoints(route.points);
        Paint paint = line.getOutlinePaint();
        paint.setColor(a.getColor(R.color.accent));
        paint.setStrokeWidth(dp(8));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        int idx = map.getOverlays().indexOf(myLoc);
        map.getOverlays().add(Math.max(0, idx), line);

        destMarker = new Marker(map);
        destMarker.setPosition(dest.point);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destMarker.setInfoWindow(null);
        map.getOverlays().add(destMarker);
        map.invalidate();
    }

    private void clearRoute() {
        if (line != null) map.getOverlays().remove(line);
        if (destMarker != null) map.getOverlays().remove(destMarker);
        line = null;
        destMarker = null;
        map.invalidate();
    }

    // ---------- Navigasyon ----------

    private boolean miles() {
        return "mi".equals(p.units());
    }

    private void startNav() {
        if (route == null) return;
        setMode(Mode.NAV);
        lastTouch = 0;
        offCount = 0;
        spokenFor = -1;
        myLoc.disableFollowLocation();
        map.getController().setZoom(17.5);
        speak("Rota başlıyor");
        if (last != null) navUpdate(last);
    }

    private void stopNav() {
        if (tts != null) tts.stop();
        releaseDuck();
        route = null;
        dest = null;
        clearRoute();
        map.setMapOrientation(0);
        searchBox.setText("");
        setMode(Mode.BROWSE);
        recenter();
    }

    private void navUpdate(Location loc) {
        if (route == null || loc == null || rerouting) return;
        GeoPoint h = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (System.currentTimeMillis() - lastTouch > 8000) {
            map.getController().animateTo(h);
            if (loc.hasBearing() && loc.getSpeed() > 2) map.setMapOrientation(-loc.getBearing());
        }

        // Rotadan çıkıldıysa yeniden hesapla
        if (loc.getAccuracy() < 40 && route.distanceToLine(h) > 60) {
            if (++offCount >= 3) {
                offCount = 0;
                rerouting = true;
                speak("Rota yeniden hesaplanıyor");
                requestRoute();
                return;
            }
        } else {
            offCount = 0;
        }

        int n = route.steps.size();
        while (cur + 1 < n) {
            Route.Step next = route.steps.get(cur + 1);
            if (!"arrive".equals(next.type) && Route.meters(h, next.at) < 25) cur++;
            else break;
        }
        if (cur + 1 >= n) { arrived(); return; }

        Route.Step next = route.steps.get(cur + 1);
        double d = Route.meters(h, next.at);
        boolean arrive = "arrive".equals(next.type);
        if (arrive && d < 35) { arrived(); return; }

        String what = Route.instruction(next);
        turnDist.setText(Route.shortDist(d, miles()));
        turnText.setText(next.name.isEmpty() || arrive ? what : what + " · " + next.name);
        if (arrive) {
            turnArrow.setImageResource(R.drawable.ic_nav);
            turnArrow.setRotation(0);
        } else {
            turnArrow.setImageResource(R.drawable.ic_arrow);
            turnArrow.setRotation(Route.arrowAngle(next));
        }

        double remaining = d;
        for (int j = cur + 1; j < n; j++) remaining += route.steps.get(j).distance;
        double remSec = route.distance > 0 ? route.duration * remaining / route.distance : 0;
        setSummary(remaining, remSec);

        // Sesli yönlendirme
        if (spokenFor != cur + 1) {
            spokenFor = cur + 1;
            saidFar = d < 450;
            saidNear = false;
            saidNow = false;
        }
        String lower = what.substring(0, 1).toLowerCase(Route.TR) + what.substring(1);
        if (!saidFar && d < 900) {
            saidFar = true;
            speak(Route.spokenDist(d, miles()) + " sonra " + lower);
        } else if (!saidNear && d <= 250 && d > 60) {
            saidNear = true;
            saidFar = true;
            speak(Route.spokenDist(d, miles()) + " sonra " + lower);
        } else if (!saidNow && d <= 50) {
            saidNow = saidNear = saidFar = true;
            speak(arrive ? "Varış noktasına yaklaşıyorsunuz" : what);
        }
    }

    private void arrived() {
        speak("Varış noktasına ulaştınız");
        toast("Varış noktasına ulaştınız");
        stopNav();
    }

    private void setSummary(double meters, double seconds) {
        long mins = Math.max(1, Math.round(seconds / 60));
        String dur = mins < 60 ? mins + " dk" : (mins / 60) + " sa " + (mins % 60) + " dk";
        String eta = new SimpleDateFormat("HH:mm", Route.TR)
                .format(new Date(System.currentTimeMillis() + (long) (seconds * 1000)));
        summary.setText(Route.shortDist(meters, miles()) + " · " + dur + " · varış " + eta);
    }

    /** Konuşurken müzik kısılsın, bitince geri gelsin. */
    private void speak(String text) {
        if (!ttsReady) return;
        AudioManager am = a.getSystemService(AudioManager.class);
        if (am != null && duck == null) {
            duck = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .build();
            am.requestAudioFocus(duck);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav" + System.nanoTime());
    }

    private void releaseDuck() {
        AudioManager am = a.getSystemService(AudioManager.class);
        if (am != null && duck != null) am.abandonAudioFocusRequest(duck);
        duck = null;
    }

    private void openExternal() {
        if (dest == null) {
            Apps.openNav(a, p, null);
        } else {
            Apps.openNav(a, p, String.format(Locale.US, "%.6f,%.6f",
                    dest.point.getLatitude(), dest.point.getLongitude()));
        }
    }

    // ---------- Ekran durumu ----------

    private void setMode(Mode m) {
        mode = m;
        searchBar.setVisibility(m == Mode.NAV ? View.GONE : View.VISIBLE);
        quickRow.setVisibility(m == Mode.BROWSE ? View.VISIBLE : View.GONE);
        if (m != Mode.BROWSE) results.setVisibility(View.GONE);
        turnCard.setVisibility(m == Mode.NAV ? View.VISIBLE : View.GONE);
        bottomPanel.setVisibility(m == Mode.BROWSE ? View.GONE : View.VISIBLE);
        destName.setVisibility(m == Mode.PREVIEW ? View.VISIBLE : View.GONE);
        mainBtn.setText(m == Mode.NAV ? "Bitir" : "Başlat");
        refreshBack();
        bottomPanel.post(() -> recenterBtn.setTranslationY(
                bottomPanel.getVisibility() == View.VISIBLE ? -(bottomPanel.getHeight() + dp(12)) : 0));
    }

    /** Gömülüyken geri oku sadece geri dönülecek bir şey varken görünür. */
    private void refreshBack() {
        if (!embedded) return;
        boolean need = results.getVisibility() == View.VISIBLE || mode == Mode.PREVIEW;
        backBtn.setVisibility(need ? View.VISIBLE : View.GONE);
        searchBox.setPadding(need ? 0 : dp(16), 0, 0, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        searchBox.clearFocus();
    }

    private void toast(String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
