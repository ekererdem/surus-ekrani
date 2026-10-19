package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.util.GeoPoint;

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
 * Uygulama içi navigasyon: Google haritası ve trafiğe göre rota (anahtar varsa), yoksa OpenStreetMap + OSRM.
 * Yön tarifi her iki durumda da Türkçe sesli okunur.
 * Hem tam ekran NavActivity'de hem ana ekranın içinde (yatayda haritanın yanında) kullanılır.
 */
final class NavPanel {
    private enum Mode { BROWSE, PREVIEW, NAV }

    private static final Pattern COORD = Pattern.compile("\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*");

    private final Activity a;
    private final boolean embedded;
    private final Prefs p;
    private Mode mode = Mode.BROWSE;

    private final MapLayer map;
    private final boolean google;
    private final View root;
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
        google = Gapi.ok(a);
        root = LayoutInflater.from(a).inflate(R.layout.activity_nav, host, false);
        host.addView(root);
        MapLayer m;
        try {
            m = Gapi.map(a) ? new GLayer(a) : new OsmLayer(a);
        } catch (Throwable t) {
            Log.w("Kopilot", "Google haritası açılamadı", t);
            m = new OsmLayer(a);
        }
        map = m;
        ((FrameLayout) root.findViewById(R.id.mapBox)).addView(map.view(),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
        ext.setText(embedded ? "Google Maps'te aç" : Apps.navName(p.nav()));
        ext.setOnClickListener(v -> openExternal());
        if (embedded) compactCards();
        setMode(Mode.BROWSE);
    }

    /** Gömülü harita küçük: yön kartı ve alt özet daha ince olsun ki harita görünsün. */
    private void compactCards() {
        turnCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        ViewGroup.LayoutParams al = turnArrow.getLayoutParams();
        al.width = al.height = dp(48);
        turnArrow.setLayoutParams(al);
        turnDist.setTextSize(28);
        turnText.setTextSize(15);
        turnText.setMaxLines(1);
        bottomPanel.setPadding(dp(12), dp(8), dp(8), dp(8));
        summary.setTextSize(19);
        ((ViewGroup.MarginLayoutParams) turnCard.getLayoutParams()).setMargins(dp(8), dp(8), dp(8), dp(8));
        ((ViewGroup.MarginLayoutParams) bottomPanel.getLayoutParams()).setMargins(dp(8), dp(8), dp(8), dp(8));
    }

    /** Gömülüyken navigasyonda alt özet tek satır: "9,9 km · 11 dk · varış 09:59   [Bitir]". */
    private void layoutBottom(Mode m) {
        if (!embedded) return;
        LinearLayout panel = (LinearLayout) bottomPanel;
        View row = (View) mainBtn.getParent();
        View ext = ((ViewGroup) row).findViewById(R.id.extBtn);
        boolean nav = m == Mode.NAV;
        panel.setOrientation(nav ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        panel.setGravity(nav ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        LinearLayout.LayoutParams sl = (LinearLayout.LayoutParams) summary.getLayoutParams();
        sl.width = nav ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        sl.weight = nav ? 1f : 0f;
        summary.setLayoutParams(sl);
        LinearLayout.LayoutParams rl = (LinearLayout.LayoutParams) row.getLayoutParams();
        rl.width = nav ? dp(108) : ViewGroup.LayoutParams.MATCH_PARENT;
        rl.height = dp(nav ? 46 : 52);
        rl.topMargin = nav ? 0 : dp(8);
        rl.setMarginStart(nav ? dp(8) : 0);
        row.setLayoutParams(rl);
        ext.setVisibility(nav ? View.GONE : View.VISIBLE);
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
        map.onDestroy();
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
        map.setOnGesture(() -> lastTouch = System.currentTimeMillis());
        Location l = lastKnown();
        if (l != null) {
            last = l;
            map.show(new GeoPoint(l.getLatitude(), l.getLongitude()), 16.0);
            map.onLocation(l);
        } else {
            map.show(new GeoPoint(39.92, 32.85), 16.0);
        }
        root.addOnLayoutChangeListener((v, l1, t1, r1, b1, l2, t2, r2, b2) -> updatePadding());
    }

    /** Haritanın görünen kısmı: üstteki arama/yön kartı ile alttaki özetin arası. */
    private void updatePadding() {
        // Gezinirken sadece arama kutusu sayılır; hızlı rota düğmeleri ve liste haritayı itmesin.
        int t = mode == Mode.NAV
                ? (turnCard.getVisibility() == View.VISIBLE ? turnCard.getBottom() : 0)
                : searchBar.getTop() + dp(64);
        int b = bottomPanel.getVisibility() == View.VISIBLE ? root.getHeight() - bottomPanel.getTop() : 0;
        map.setPadding(t, b);
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
        map.myLocation(true);
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
        map.myLocation(false);
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
        map.onLocation(loc);
        if (waitingForFix && dest != null) {
            waitingForFix = false;
            requestRoute();
        }
        if (mode == Mode.NAV) navUpdate(loc);
    }

    private GeoPoint here() {
        if (last != null) return new GeoPoint(last.getLatitude(), last.getLongitude());
        return map.myLocationPoint();
    }

    private void recenter() {
        lastTouch = 0;
        GeoPoint h = here();
        if (h != null) map.animateTo(h);
        if (mode != Mode.NAV) map.follow(true);
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
                List<Route.Place> r = null;
                if (google) {
                    try {
                        r = Gapi.search(a, q, near);
                    } catch (Exception e) {
                        Log.w("Kopilot", "Google arama olmadı, OSM'e geçiliyor", e);
                    }
                }
                if (r == null || r.isEmpty()) r = Route.search(q, near);
                List<Route.Place> res = r;
                a.runOnUiThread(() -> {
                    if (destroyed) return;
                    if (res.isEmpty()) { toast("Sonuç bulunamadı"); return; }
                    if (pickFirst) { choose(res.get(0)); return; }
                    showResults(res);
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> toast("Arama yapılamadı. İnternet bağlantını kontrol et."));
            }
        });
    }

    private void showResults(List<Route.Place> r) {
        found.clear();
        found.addAll(r);
        List<CharSequence> names = new ArrayList<>();
        for (Route.Place pl : r) {
            SpannableStringBuilder sb = new SpannableStringBuilder(pl.name);
            if (pl.addr != null && !pl.addr.isEmpty()) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int st = sb.length();
                sb.append("\n").append(pl.addr);
                sb.setSpan(new ForegroundColorSpan(a.getColor(R.color.muted)), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.85f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            names.add(sb);
        }
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
        Location head = last;
        io.execute(() -> {
            try {
                Route r = null;
                if (google) {
                    try {
                        r = Gapi.route(a, from, target.point, head);
                    } catch (Exception e) {
                        Log.w("Kopilot", "Google rota olmadı, OSRM'e geçiliyor", e);
                    }
                }
                if (r == null) r = Route.fetch(from, target.point);
                Route done = r;
                a.runOnUiThread(() -> onRoute(done));
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
        map.follow(false);
        if (autoStart) { // hızlı rota: önizlemeyi atla, hemen sür
            autoStart = false;
            startNav();
            return;
        }
        map.fitRoute(r.points);
    }

    private void drawRoute() {
        map.showRoute(route, dest.point);
    }

    private void clearRoute() {
        map.clearRoute();
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
        map.follow(false);
        map.navMode(true);
        speak("Rota başlıyor");
        if (last != null) navUpdate(last);
    }

    private void stopNav() {
        if (tts != null) tts.stop();
        releaseDuck();
        route = null;
        dest = null;
        clearRoute();
        map.navMode(false);
        searchBox.setText("");
        setMode(Mode.BROWSE);
        recenter();
    }

    private void navUpdate(Location loc) {
        if (route == null || loc == null || rerouting) return;
        GeoPoint h = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (System.currentTimeMillis() - lastTouch > 8000) {
            map.drive(h, loc.hasBearing() && loc.getSpeed() > 2 ? loc.getBearing() : Float.NaN);
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
        if (!arrive && next.text != null && !next.text.isEmpty()) turnText.setText(next.text);
        else turnText.setText(next.name.isEmpty() || arrive ? what : what + " · " + next.name);
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
        layoutBottom(m);
        refreshBack();
        bottomPanel.post(() -> {
            recenterBtn.setTranslationY(
                    bottomPanel.getVisibility() == View.VISIBLE ? -(bottomPanel.getHeight() + dp(12)) : 0);
            updatePadding();
        });
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
