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
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

/** Uygulama içi navigasyon: adres arama, rota, sesli yön tarifi. */
public class NavActivity extends Activity {
    static final String EXTRA_DEST = "dest";

    private enum Mode { BROWSE, PREVIEW, NAV }

    private Mode mode = Mode.BROWSE;
    private Prefs p;

    private MapView map;
    private MyLocationNewOverlay myLoc;
    private EditText searchBox;
    private ListView results;
    private View quickRow, searchBar, turnCard, bottomPanel, recenterBtn;
    private ImageView turnArrow;
    private TextView turnDist, turnText, destName, summary;
    private Button mainBtn;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private boolean ttsReady;

    private LocationManager lm;
    private Location last;
    private Route route;
    private Route.Place dest;
    private boolean autoStart, waitingForFix, rerouting;
    private Polyline line;
    private Marker destMarker;
    private final List<Route.Place> found = new ArrayList<>();

    private int cur;
    private int spokenFor = -1;
    private boolean saidFar, saidNear, saidNow;
    private int offCount;
    private long lastTouch, lastBack;

    private final LocationListener locListener = this::onLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = new Prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(this, getSharedPreferences("osm", MODE_PRIVATE));
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osm");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
        setContentView(R.layout.activity_nav);

        searchBox = findViewById(R.id.searchBox);
        results = findViewById(R.id.results);
        quickRow = findViewById(R.id.quickRow);
        searchBar = findViewById(R.id.searchBar);
        turnCard = findViewById(R.id.turnCard);
        bottomPanel = findViewById(R.id.bottomPanel);
        recenterBtn = findViewById(R.id.recenterBtn);
        turnArrow = findViewById(R.id.turnArrow);
        turnDist = findViewById(R.id.turnDist);
        turnText = findViewById(R.id.turnText);
        destName = findViewById(R.id.destName);
        summary = findViewById(R.id.summary);
        mainBtn = findViewById(R.id.mainBtn);

        lm = getSystemService(LocationManager.class);
        setupMap();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Route.TR);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                ttsReady = true;
            }
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.searchBtn).setOnClickListener(v -> doSearch(false));
        searchBox.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(false); return true; }
            return false;
        });
        findViewById(R.id.quickHome).setOnClickListener(v -> goAddress(p.home()));
        findViewById(R.id.quickWork).setOnClickListener(v -> goAddress(p.work()));
        results.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < found.size()) choose(found.get(pos));
        });
        recenterBtn.setOnClickListener(v -> recenter());
        mainBtn.setOnClickListener(v -> {
            if (mode == Mode.PREVIEW) startNav();
            else if (mode == Mode.NAV) stopNav();
        });
        findViewById(R.id.extBtn).setOnClickListener(v -> openExternal());
        ((Button) findViewById(R.id.extBtn)).setText(Apps.navName(p.nav()));

        if (!hasLocation()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        setMode(Mode.BROWSE);
        String d = getIntent().getStringExtra(EXTRA_DEST);
        if (d != null && !d.trim().isEmpty()) {
            autoStart = true;
            goAddress(d);
        }
    }

    // ---------- Harita ----------

    private void setupMap() {
        map = findViewById(R.id.navMap);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getMapOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(31.0, -99.0));
        map.setOnTouchListener((v, e) -> {
            lastTouch = System.currentTimeMillis();
            return false;
        });

        GpsMyLocationProvider provider = new GpsMyLocationProvider(this);
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

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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
        if (!hasLocation()) return;
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

    private void goAddress(String addr) {
        if (addr == null || addr.trim().isEmpty()) {
            toast("Önce Ayarlar'dan adres ekle");
            return;
        }
        searchBox.setText(addr.trim());
        doSearch(true);
    }

    private void doSearch(boolean pickFirst) {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) return;
        hideKeyboard();
        toast("Aranıyor…");
        GeoPoint near = here();
        io.execute(() -> {
            try {
                List<Route.Place> r = Route.search(q, near);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (r.isEmpty()) { toast("Sonuç bulunamadı"); return; }
                    if (pickFirst) { choose(r.get(0)); return; }
                    showResults(r);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Arama yapılamadı. İnternet bağlantını kontrol et."));
            }
        });
    }

    private void showResults(List<Route.Place> r) {
        found.clear();
        found.addAll(r);
        List<String> names = new ArrayList<>();
        for (Route.Place pl : r) names.add(pl.name);
        results.setAdapter(new ArrayAdapter<>(this, R.layout.item_result, names));
        results.setVisibility(View.VISIBLE);
        quickRow.setVisibility(View.GONE);
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
                runOnUiThread(() -> onRoute(r));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    rerouting = false;
                    toast("Rota hesaplanamadı. İnternet bağlantını kontrol et.");
                });
            }
        });
    }

    private void onRoute(Route r) {
        if (isDestroyed() || dest == null) return;
        route = r;
        cur = 0;
        spokenFor = -1;
        drawRoute();
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
        paint.setColor(getColor(R.color.accent));
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

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav" + System.nanoTime());
    }

    private void openExternal() {
        if (dest == null) {
            Apps.openNav(this, p, null);
        } else {
            Apps.openNav(this, p, String.format(Locale.US, "%.6f,%.6f",
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
        bottomPanel.post(() -> recenterBtn.setTranslationY(
                bottomPanel.getVisibility() == View.VISIBLE ? -(bottomPanel.getHeight() + dp(12)) : 0));
    }

    @Override
    public void onBackPressed() {
        if (results.getVisibility() == View.VISIBLE) {
            results.setVisibility(View.GONE);
            quickRow.setVisibility(View.VISIBLE);
        } else if (mode == Mode.PREVIEW) {
            stopNav();
        } else if (mode == Mode.NAV) {
            long now = System.currentTimeMillis();
            if (now - lastBack < 2500) {
                stopNav();
                finish();
            } else {
                lastBack = now;
                toast("Navigasyonu bitirip çıkmak için tekrar bas");
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        startUpdates();
        hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        // Navigasyon sürerken başka uygulamaya geçilse de sesli yönlendirme devam etsin.
        if (mode != Mode.NAV) {
            myLoc.disableMyLocation();
            lm.removeUpdates(locListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lm.removeUpdates(locListener);
        if (tts != null) tts.shutdown();
        io.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        startUpdates();
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        searchBox.clearFocus();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
