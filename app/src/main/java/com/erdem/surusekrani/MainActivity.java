package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.SeekBar;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** Büyük butonlu sürüş ekranı: canlı harita + çalan şarkı. */
public class MainActivity extends Activity {
    private static final int REQ_LOC = 10;
    private static final int REQ_CALL = 11;
    private String pendingCall;

    private Prefs p;
    private boolean compact;
    private Status status;
    private float appliedScale = 1f;

    /** Ayarlar'daki "Ekran ölçeği"ni uygular: tüm ekranı küçültür ya da büyütür. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        float s = base.getSharedPreferences("surus", MODE_PRIVATE).getFloat("scale", 1f);
        appliedScale = s;
        if (s != 1f) {
            android.content.res.Configuration c =
                    new android.content.res.Configuration(base.getResources().getConfiguration());
            c.densityDpi = Math.round(c.densityDpi * s);
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    // Harita
    private MapView map;
    private MyLocationNewOverlay myLoc;
    private ImageView navIcon;
    private TextView navTitle, navSub;

    // Müzik
    private MediaSessionManager msm;
    private ComponentName listenerCn;
    private MediaController ctrl;
    private ImageButton playBtn;
    private TextView songTitle, songArtist, elapsed, durationTv;
    private ImageView musicArt;
    private SeekBar seek;
    private boolean seeking;

    private final MediaController.Callback mediaCb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { renderMusic(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { renderMusic(); }
        @Override public void onSessionDestroyed() { pickController(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            list -> pickController();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = new Prefs(this);
        Places.migrate(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupOsm();
        compact = isInMultiWindowMode();
        setContentView(compact ? R.layout.activity_main_compact : R.layout.activity_main);
        if (compact) ((TextView) findViewById(R.id.clock)).setTextSize(38);
        status = new Status(this);

        navIcon = findViewById(R.id.navIcon);
        navTitle = findViewById(R.id.navTitle);
        navSub = findViewById(R.id.navSub);
        playBtn = findViewById(R.id.playBtn);
        songTitle = findViewById(R.id.songTitle);
        songArtist = findViewById(R.id.songArtist);
        elapsed = findViewById(R.id.musicElapsed);
        durationTv = findViewById(R.id.musicDuration);
        seek = findViewById(R.id.musicSeek);

        findViewById(R.id.navFrame).setClipToOutline(true);
        setupMap();

        // İlerleme çubuğu sürüklenince şarkıda o noktaya atlar.
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int value, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar sb) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                seeking = false;
                if (ctrl == null || ctrl.getMetadata() == null) return;
                long dur = ctrl.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (dur > 0) ctrl.getTransportControls().seekTo(dur * sb.getProgress() / 1000);
            }
        });

        // Dokun: Google Haritalar yan yana açılır. Basılı tut: uygulama içi navigasyon.
        findViewById(R.id.navTouch).setOnClickListener(v -> Apps.openNavSplit(this, p, null));
        findViewById(R.id.navTouch).setOnLongClickListener(v -> {
            startActivity(new Intent(this, NavActivity.class));
            return true;
        });
        findViewById(R.id.musicOpen).setOnClickListener(v -> Apps.openMusic(this, p));
        findViewById(R.id.prevBtn).setOnClickListener(v -> {
            if (ctrl != null) ctrl.getTransportControls().skipToPrevious();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        });
        findViewById(R.id.nextBtn).setOnClickListener(v -> {
            if (ctrl != null) ctrl.getTransportControls().skipToNext();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        });
        playBtn.setOnClickListener(v -> togglePlay());
        musicArt = findViewById(R.id.musicArt);
        musicArt.setClipToOutline(true);
        musicArt.setOnClickListener(v -> Apps.openMusic(this, p));

        findViewById(R.id.assistBtn).setOnClickListener(v -> openAssistant());
        // Düğmeler uzayınca simgeler dev olmasın: simge en fazla 34dp
        findViewById(R.id.iconsGrid).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                v.post(() -> { squareQuick(); fitQuickIcons(); }));
        findViewById(R.id.parkBtn).setOnClickListener(v -> showPark());
        findViewById(R.id.parkBtn).setOnLongClickListener(v -> {
            if (Park.saveHere(this)) parkNoteDialog();
            else Toast.makeText(this, "Konum bulunamadı", Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                openOwn(new Intent(this, SettingsActivity.class)));

        msm = getSystemService(MediaSessionManager.class);
        listenerCn = new ComponentName(this, MediaListener.class);

        if (!hasLocation() || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT}, REQ_LOC);
        }
        handleCarIntent(getIntent());
        // Uygulama ilk açıldığında haritayı ekranın diğer yarısında başlat.
    }

    // ---------- Harita ----------

    private void setupOsm() {
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(this, getSharedPreferences("osm", MODE_PRIVATE));
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osm");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
    }

    private void setupMap() {
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(false);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getMapOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(31.0, -99.0));

        GpsMyLocationProvider provider = new GpsMyLocationProvider(this);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLoc = new MyLocationNewOverlay(provider, map);
        map.getOverlays().add(myLoc);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocation() {
        if (!hasLocation()) return;
        myLoc.enableMyLocation();
        myLoc.enableFollowLocation();
        try {
            LocationManager lm = getSystemService(LocationManager.class);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l != null) map.getController().setCenter(new GeoPoint(l.getLatitude(), l.getLongitude()));
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private void renderNav() {
        if (NavState.active) {
            navTitle.setText(NavState.title.isEmpty() ? NavState.text : NavState.title);
            navSub.setText(join(NavState.title.isEmpty() ? "" : NavState.text, NavState.eta));
            navSub.setVisibility(navSub.getText().length() == 0 ? View.GONE : View.VISIBLE);
            if (NavState.icon != null) {
                navIcon.setImageDrawable(NavState.icon);
                navIcon.setImageTintList(null);
                return;
            }
        } else {
            navTitle.setText("Navigasyon");
            navSub.setText(Apps.navName(p.nav()) + " · yan yana açmak için dokun");
            navSub.setVisibility(View.VISIBLE);
        }
        navIcon.setImageResource(R.drawable.ic_nav);
        navIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.amber_ink)));
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    // ---------- Müzik ----------

    private boolean hasListenerAccess() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(listenerCn);
    }

    private void pickController() {
        List<MediaController> list = Collections.emptyList();
        if (hasListenerAccess()) {
            try {
                list = msm.getActiveSessions(listenerCn);
            } catch (SecurityException ignored) {
            }
        }
        String want = Apps.musicPkg(p.music());
        MediaController best = null;
        for (MediaController c : list) {
            if (want.equals(c.getPackageName())) { best = c; break; }
        }
        if (best == null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
        }
        if (best == null && !list.isEmpty()) best = list.get(0);
        setController(best);
        renderMusic();
        autoPlayIfPending();
    }

    private void setController(MediaController c) {
        if (ctrl != null) ctrl.unregisterCallback(mediaCb);
        ctrl = c;
        if (ctrl != null) ctrl.registerCallback(mediaCb);
    }

    // ---------- Hızlı düğmeler (iki sıra) ----------

    /** Üst sıra: durum, hava, araba, mikrofon, ayarlar. */
    private static final int[] ROW1 = {
            R.id.statusTile, R.id.weatherTile, R.id.parkBtn, R.id.assistBtn, R.id.settingsBtn};
    /** Alt sıra: hızlı aramalar, sonra hızlı rotalar (koddan eklenir). */
    static final int[] CALLS = {R.id.call1Btn, R.id.call2Btn, R.id.call3Btn, R.id.call4Btn, R.id.call5Btn};
    private static final int[] QUICK = {
            R.id.statusTile, R.id.weatherTile, R.id.parkBtn, R.id.assistBtn, R.id.settingsBtn,
            R.id.call1Btn, R.id.call2Btn, R.id.call3Btn, R.id.call4Btn, R.id.call5Btn};
    private final java.util.List<View> placeViews = new java.util.ArrayList<>();

    private void arrangeQuick() {
        ViewGroup r1 = findViewById(R.id.iconsRow1), r2 = findViewById(R.id.iconsRow2);
        for (View v : placeViews) if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        placeViews.clear();
        findViewById(R.id.assistBtn).setVisibility(
                getSharedPreferences("surus", MODE_PRIVATE).getBoolean("assistBtn", true) ? View.VISIBLE : View.GONE);
        // Görünümleri önce topla: ağaçtan çıkınca findViewById onları bulamaz
        java.util.Map<Integer, View> views = new java.util.HashMap<>();
        for (int id : QUICK) {
            View v = findViewById(id);
            views.put(id, v);
            ((ViewGroup) v.getParent()).removeView(v);
        }
        for (int id : ROW1) addQuick(r1, views.get(id));
        for (int id : CALLS) addQuick(r2, views.get(id));
        for (View v : buildPlaceButtons()) addQuick(r2, v);
        findViewById(R.id.iconsGrid).post(() -> { squareQuick(); fitQuickIcons(); });
    }

    private void addQuick(ViewGroup row, View v) {
        row.addView(v, new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weightOf(v)));
    }

    /** Ayarlar'da eklenen hızlı rotalar için düğmeler: simge + kısa ad. */
    private java.util.List<View> buildPlaceButtons() {
        int n = Places.count(this);
        float d = getResources().getDisplayMetrics().density;
        // Saate göre sırala: sabah İş, akşam Ev öne geçer
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String first = hour >= 5 && hour < 12 ? "work" : hour >= 15 ? "home" : "";
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) if (Places.icon(this, i).equals(first)) order.add(i);
        for (int i = 1; i <= n; i++) if (!order.contains(i)) order.add(i);
        for (int i : order) {
            String coord = Places.coord(this, i), name = Places.name(this, i);
            TextView b = new TextView(this, null, 0, R.style.QuickText);
            b.setTextSize(13);
            b.setTextColor(getColor(R.color.fg));
            b.setText(name);
            android.graphics.drawable.Drawable ic = getDrawable(Places.iconRes(Places.icon(this, i))).mutate();
            int sz = Math.round(18 * d);
            ic.setBounds(0, 0, sz, sz);
            ic.setTint(getColor(R.color.amber));
            b.setCompoundDrawablesRelative(ic, null, null, null);
            b.setCompoundDrawablePadding(Math.round(4 * d));
            b.setPadding(Math.round(6 * d), 0, Math.round(6 * d), 0);
            b.setContentDescription(name + " için rota başlat");
            b.setOnClickListener(v -> goTo(coord));
            b.setOnLongClickListener(v -> { openOwn(new Intent(this, SettingsActivity.class)); return true; });
            placeViews.add(b);
        }
        return placeViews;
    }

    /** Düğmelere sabit, kareye yakın genişlik ver; sıralar ortalanır (az düğmeli sıra uzamaz). */
    private void squareQuick() {
        View g = findViewById(R.id.iconsGrid);
        ViewGroup r1 = findViewById(R.id.iconsRow1), r2 = findViewById(R.id.iconsRow2);
        float d = getResources().getDisplayMetrics().density;
        if (g.getWidth() == 0) return;
        int gap = Math.round(8 * d);
        int h = (g.getHeight() - gap) / 2;
        if (g.getPaddingLeft() != 0) g.setPadding(0, 0, 0, 0);
        for (ViewGroup r : new ViewGroup[]{r1, r2}) {
            ((android.widget.LinearLayout) r).setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            int cols = visibleCount(r);
            // Alt sıra boşsa yeri yine dursun (üst sıra uzamasın)
            r.setVisibility(cols == 0 ? View.INVISIBLE : View.VISIBLE);
            int w = Math.round(65 * d); // sabit genişlik (yükseklikten bağımsız)
            if (cols > 0) w = Math.min(w, (g.getWidth() - (cols - 1) * gap) / cols); // sığmazsa daralt
            boolean first = true;
            for (int i = 0; i < r.getChildCount(); i++) {
                View c = r.getChildAt(i);
                if (c.getVisibility() == View.GONE) continue;
                int k = weightOf(c), cw = w * k + gap * (k - 1), ms = first ? 0 : gap;
                first = false;
                android.widget.LinearLayout.LayoutParams lp =
                        (android.widget.LinearLayout.LayoutParams) c.getLayoutParams();
                if (lp.width != cw || lp.weight != 0 || lp.getMarginStart() != ms) {
                    lp.width = cw;
                    lp.weight = 0;
                    lp.setMarginStart(ms);
                    c.setLayoutParams(lp);
                }
            }
        }
    }

    private static int visibleCount(ViewGroup r) {
        int n = 0;
        for (int i = 0; i < r.getChildCount(); i++)
            if (r.getChildAt(i).getVisibility() != View.GONE) n += weightOf(r.getChildAt(i));
        return n;
    }

    private static int weightOf(View v) {
        int id = v.getId();
        if (id == R.id.statusTile) return 2;
        for (int c : CALLS) if (c == id) return 2;
        return 1;
    }

    /** Simge boyutunu düğmenin küçük kenarına göre sınırla. */
    private void fitQuickIcons() {
        float d = getResources().getDisplayMetrics().density;
        int max = Math.round(24 * d), min = Math.round(6 * d);
        for (int id : QUICK) {
            View c = findViewById(id);
            if (!(c instanceof ImageButton) || c.getWidth() == 0) continue;
            int px = Math.max(min, (c.getWidth() - max) / 2);
            int py = Math.max(min, (c.getHeight() - max) / 2);
            if (c.getPaddingLeft() != px || c.getPaddingTop() != py) c.setPadding(px, py, px, py);
        }
    }

    // ---------- Sesli asistan ----------

    private void openAssistant() {
        Intent[] tries = {
                new Intent(Intent.ACTION_VOICE_COMMAND),
                new Intent("android.intent.action.ASSIST"),
                new Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH)};
        for (Intent i : tries) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, "Sesli asistan bulunamadı", Toast.LENGTH_SHORT).show();
    }

    // ---------- Arabam nerede ----------

    private void showPark() {
        String c = Park.coord(this);
        if (c.isEmpty()) {
            Toast.makeText(this, "Henüz park yeri yok. Arabadan inince kendiliğinden kaydedilir; "
                    + "elle kaydetmek için bu düğmeye basılı tut.", Toast.LENGTH_LONG).show();
            return;
        }
        long mins = (System.currentTimeMillis() - Park.savedAt(this)) / 60000;
        String ago = mins < 1 ? "az önce" : mins < 60 ? mins + " dk önce"
                : mins < 1440 ? (mins / 60) + " saat önce" : (mins / 1440) + " gün önce";
        String note = Park.note(this), photo = Park.photo(this);
        if (note.isEmpty() && photo.isEmpty()) {
            Toast.makeText(this, "Park yeri " + ago + " kaydedildi", Toast.LENGTH_SHORT).show();
            Apps.openWalk(this, p, c);
            return;
        }
        // Not ya da fotoğraf varsa önce onları göster
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(20 * d);
        box.setPadding(pad, Math.round(8 * d), pad, 0);
        if (!note.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(note);
            t.setTextSize(22);
            t.setTextColor(getColor(R.color.fg));
            box.addView(t);
        }
        if (!photo.isEmpty()) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Math.round(260 * d));
            try {
                iv.setImageURI(android.net.Uri.parse(photo));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Math.round(10 * d);
                box.addView(iv, lp);
            } catch (Exception ignored) {
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Park yeri (" + ago + ")")
                .setView(box)
                .setPositiveButton("Yol tarifi", (x, y) -> Apps.openWalk(this, p, c))
                .setNegativeButton("Kapat", null)
                .show();
    }

    // ---------- Park notu ve fotoğrafı ----------

    private static final int REQ_PHOTO = 31;
    private android.net.Uri pendingPhoto;

    private void parkNoteDialog() {
        float d = getResources().getDisplayMetrics().density;
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Not (ör. B2 katı, 47 numara)");
        et.setSingleLine(true);
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        int pad = Math.round(20 * d);
        fl.setPadding(pad, Math.round(8 * d), pad, 0);
        fl.addView(et);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Park yeri kaydedildi")
                .setView(fl)
                .setPositiveButton("Kaydet", (x, y) -> Park.setNote(this, et.getText().toString().trim()))
                .setNeutralButton("📷 Fotoğraf", (x, y) -> {
                    Park.setNote(this, et.getText().toString().trim());
                    takeParkPhoto();
                })
                .setNegativeButton("Geç", null)
                .show();
    }

    private void takeParkPhoto() {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "park_" + System.currentTimeMillis() + ".jpg");
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SurusEkrani");
            pendingPhoto = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (pendingPhoto == null) throw new IllegalStateException();
            sOwnScreen = true;
            startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingPhoto)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION), REQ_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "Kamera açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PHOTO || pendingPhoto == null) return;
        if (res == RESULT_OK) {
            Park.setPhoto(this, pendingPhoto.toString());
            Toast.makeText(this, "Fotoğraf park yerine eklendi", Toast.LENGTH_SHORT).show();
        } else {
            try { getContentResolver().delete(pendingPhoto, null, null); } catch (Exception ignored) { }
        }
        pendingPhoto = null;
    }

    // ---------- Hızlı arama ----------

    private void renderCalls() {
        for (int i = 0; i < CALLS.length; i++) bindCall(CALLS[i], "call" + (i + 1));
    }

    private void bindCall(int id, String key) {
        TextView b = findViewById(id);
        android.content.SharedPreferences sp = getSharedPreferences("surus", MODE_PRIVATE);
        String name = sp.getString(key + "Name", "");
        String num = sp.getString(key + "Num", "");
        if (num.isEmpty()) {
            b.setVisibility(View.GONE);
            return;
        }
        b.setVisibility(View.VISIBLE);
        String first = name.trim().isEmpty() ? num : name.trim().split("\\s+")[0];
        b.setText(first);
        // Küçük telefon simgesi ismin üstünde (hızlı rota düğmeleriyle aynı düzen)
        float dd = getResources().getDisplayMetrics().density;
        android.graphics.drawable.Drawable ph = getDrawable(R.drawable.ic_phone).mutate();
        int psz = Math.round(22 * dd);
        ph.setBounds(0, 0, psz, psz);
        ph.setTint(getColor(R.color.teal));
        b.setCompoundDrawablesRelative(ph, null, null, null);
        b.setCompoundDrawablePadding(Math.round(6 * dd));
        b.setTextSize(17);
        b.setPadding(Math.round(6 * dd), 0, Math.round(6 * dd), 0);
        b.setContentDescription(name + " ara");
        b.setOnClickListener(v -> confirmCall(first, num));
        b.setOnLongClickListener(v -> { sendLocation(num); return true; });
    }

    /** Yanlışlıkla aramayı önlemek için 3 saniyelik geri sayım; dokununca iptal. */
    private void confirmCall(String name, String num) {
        final int[] left = {3};
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("📞 " + name + " aranıyor…")
                .setMessage("3")
                .setPositiveButton("Şimdi ara", null)
                .setNegativeButton("İptal", null)
                .create();
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!dlg.isShowing()) return;
                left[0]--;
                if (left[0] <= 0) {
                    dlg.dismiss();
                    call(num);
                } else {
                    dlg.setMessage(String.valueOf(left[0]));
                    musicArt.postDelayed(this, 1000);
                }
            }
        };
        dlg.setOnShowListener(x -> {
            TextView msg = dlg.findViewById(android.R.id.message);
            if (msg != null) {
                msg.setTextSize(44);
                msg.setGravity(android.view.Gravity.CENTER);
            }
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                dlg.dismiss();
                call(num);
            });
        });
        dlg.setOnDismissListener(x -> musicArt.removeCallbacks(tick));
        dlg.show();
        musicArt.postDelayed(tick, 1000);
    }

    /** Hızlı arama tuşuna basılı tutunca: WhatsApp ile anlık konumu gönder. */
    private void sendLocation(String num) {
        String c = Park.current(this);
        if (c == null) {
            Toast.makeText(this, "Konum bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = "Yoldayım. Konumum: https://maps.google.com/?q=" + c;
        String d = num.replaceAll("[^0-9]", "");
        if (d.startsWith("00")) d = d.substring(2);
        else if (d.startsWith("0")) d = "90" + d.substring(1);
        else if (d.length() == 10 && d.startsWith("5")) d = "90" + d;
        android.net.Uri u = android.net.Uri.parse("https://wa.me/" + d + "?text=" + android.net.Uri.encode(text));
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b", null}) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg != null) i.setPackage(pkg);
                startActivity(i);
                return;
            } catch (android.content.ActivityNotFoundException ignored) {
            }
        }
        Toast.makeText(this, "WhatsApp bulunamadı", Toast.LENGTH_SHORT).show();
    }

    private void call(String num) {
        android.net.Uri tel = android.net.Uri.parse("tel:" + num);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_CALL, tel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (pendingCall == null && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)
                && !getSharedPreferences("surus", MODE_PRIVATE).getBoolean("callAsked", false)) {
            getSharedPreferences("surus", MODE_PRIVATE).edit().putBoolean("callAsked", true).apply();
            pendingCall = num;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
        } else {
            // İzin yoksa numara çevirici açılır, yeşil tuşa basman yeterli
            startActivity(new Intent(Intent.ACTION_DIAL, tel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void togglePlay() {
        PlaybackState s = ctrl == null ? null : ctrl.getPlaybackState();
        boolean playing = (s != null && s.getState() == PlaybackState.STATE_PLAYING)
                || (ctrl == null && musicActive());
        if (playing) {
            if (ctrl != null) ctrl.getTransportControls().pause();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            return;
        }
        String want = Apps.musicPkg(p.music());
        // Spotify'ın oturumu açıksa doğrudan ona "çal" de
        if (ctrl != null && want.equals(ctrl.getPackageName())) {
            ctrl.getTransportControls().play();
            playBtn.postDelayed(() -> { if (!musicActive()) startMusicApp(); }, 1500);
            return;
        }
        // Spotify kapalı (ya da başka bir uygulamanın oturumu var): Spotify'ı kendisi başlat
        startMusicApp();
    }

    private android.media.browse.MediaBrowser browser;
    private boolean pendingAutoPlay;

    /** Kapalı müzik uygulamasını arka planda başlatıp çaldır; olmazsa uygulamayı açıp çaldır. */
    private void startMusicApp() {
        String pkg = Apps.musicPkg(p.music());
        android.content.ComponentName svc = null;
        try {
            List<android.content.pm.ResolveInfo> r = getPackageManager().queryIntentServices(
                    new Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0);
            if (!r.isEmpty()) svc = new android.content.ComponentName(pkg, r.get(0).serviceInfo.name);
        } catch (Exception ignored) {
        }
        if (svc != null) {
            if (browser != null) try { browser.disconnect(); } catch (Exception ignored) { }
            browser = new android.media.browse.MediaBrowser(this, svc,
                    new android.media.browse.MediaBrowser.ConnectionCallback() {
                        @Override public void onConnected() {
                            try {
                                new MediaController(MainActivity.this, browser.getSessionToken())
                                        .getTransportControls().play();
                            } catch (Exception ignored) {
                            }
                        }
                    }, null);
            try { browser.connect(); } catch (Exception ignored) { }
        }
        // Yedek: uygulamanın medya tuşu alıcısına doğrudan "çal"
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)));
        }
        playBtn.postDelayed(() -> {
            if (musicActive()) { pickController(); return; }
            // Son çare: uygulamayı aç, oturumu görününce çaldır
            pendingAutoPlay = true;
            Apps.openMusic(this, p);
            playBtn.postDelayed(() -> pendingAutoPlay = false, 15000);
        }, 2500);
    }

    /** Uygulama açılınca oturumu gelirse bekleyen "çal"ı gönder. */
    private void autoPlayIfPending() {
        if (!pendingAutoPlay || ctrl == null) return;
        if (!Apps.musicPkg(p.music()).equals(ctrl.getPackageName())) return;
        pendingAutoPlay = false;
        ctrl.getTransportControls().play();
    }

    private boolean musicActive() {
        AudioManager am = getSystemService(AudioManager.class);
        return am != null && am.isMusicActive();
    }


    private void renderMusic() {
        MediaMetadata m = ctrl == null ? null : ctrl.getMetadata();
        if (m == null) {
            seek.setProgress(0);
            elapsed.setText("0:00");
            durationTv.setText("0:00");
            showArtPlaceholder();
            songTitle.setText("Müzik");
            songArtist.setText(hasListenerAccess()
                    ? Apps.musicName(p.music()) + " · açmak için dokun"
                    : "Şarkıyı görmek için Ayarlar → 4. Bildirim erişimi");
            AudioManager am = getSystemService(AudioManager.class);
            boolean active = am != null && am.isMusicActive();
            playBtn.setImageResource(active ? R.drawable.ic_pause : R.drawable.ic_play);
            return;
        }
        String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        songTitle.setText(title == null ? "" : title);
        songArtist.setText(artist == null ? "" : artist);

        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (art != null) {
            musicArt.setPadding(0, 0, 0, 0);
            musicArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            musicArt.setImageTintList(null);
            musicArt.setImageBitmap(art);
            tintMusic(art);
        } else {
            showArtPlaceholder();
            tintMusic(null);
        }

        PlaybackState s = ctrl.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        playBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
    }

    private Bitmap lastTintArt;

    /** Müzik kutusunu albüm kapağının baskın rengiyle hafifçe renklendir. */
    private void tintMusic(Bitmap art) {
        View tile = findViewById(R.id.musicTile);
        if (tile == null || art == lastTintArt) return;
        lastTintArt = art;
        if (art == null) { tile.setBackgroundTintList(null); return; }
        try {
            Bitmap s = Bitmap.createScaledBitmap(art, 12, 12, true);
            long r = 0, g = 0, b = 0; int n = 0;
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                int px = s.getPixel(x, y);
                int pr = (px >> 16) & 255, pg = (px >> 8) & 255, pb = px & 255;
                int mx = Math.max(pr, Math.max(pg, pb)), mn = Math.min(pr, Math.min(pg, pb));
                int w = 1 + (mx - mn) / 16; // renkli pikseller daha çok sayılsın
                r += pr * w; g += pg * w; b += pb * w; n += w;
            }
            int base = getColor(R.color.panel2);
            float k = 0.28f; // albüm renginin payı: hafif
            int cr = Math.round(((base >> 16) & 255) * (1 - k) + (r / n) * k);
            int cg = Math.round(((base >> 8) & 255) * (1 - k) + (g / n) * k);
            int cb = Math.round((base & 255) * (1 - k) + (b / n) * k);
            tile.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.rgb(cr, cg, cb)));
        } catch (Exception e) {
            tile.setBackgroundTintList(null);
        }
    }

    private void showArtPlaceholder() {
        int pad = Math.round(32 * getResources().getDisplayMetrics().density);
        musicArt.setPadding(pad, pad, pad, pad);
        musicArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        musicArt.setImageResource(R.drawable.ic_music);
        musicArt.setImageTintList(ColorStateList.valueOf(getColor(R.color.muted)));
    }

    /** Şarkının ilerleme çubuğunu ve sürelerini günceller. */
    private void updateProgress() {
        if (ctrl == null || seeking) return;
        MediaMetadata m = ctrl.getMetadata();
        PlaybackState s = ctrl.getPlaybackState();
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (s == null || dur <= 0) {
            seek.setProgress(0);
            elapsed.setText("0:00");
            durationTv.setText("0:00");
            return;
        }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        pos = Math.max(0, Math.min(pos, dur));
        seek.setProgress((int) (pos * 1000 / dur));
        elapsed.setText(fmt(pos));
        durationTv.setText(fmt(dur));
    }

    private static String fmt(long ms) {
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format(java.util.Locale.US, "%02d", sec % 60);
    }

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            updateProgress();
            seek.postDelayed(this, 500);
        }
    };

    // ---------- Yaşam döngüsü ----------

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

    void goTo(String addr) {
        if (addr.trim().isEmpty()) {
            Toast.makeText(this, "Önce Ayarlar'dan adres ekle", Toast.LENGTH_SHORT).show();
            openOwn(new Intent(this, SettingsActivity.class));
        } else {
            Apps.openNavSplit(this, p, addr);
        }
    }

    /** Uygulamanın kendi ekranlarına (Ayarlar vb.) gidip dönerken haritayı tekrar açma. */
    static boolean sOwnScreen;

    void openOwn(Intent i) {
        sOwnScreen = true;
        startActivity(i);
    }

    private final Runnable autoSplitRun = () -> {
        if (!isFinishing() && !isDestroyed() && !isInMultiWindowMode()) {
            Apps.openNavSplit(this, p, null);
        }
    };

    /** Ekran bölünmüş değilse haritayı yanına aç. */
    private void autoSplit() {
        playBtn.removeCallbacks(autoSplitRun);
        if (!p.autoSplit() || isInMultiWindowMode()) return;
        playBtn.postDelayed(autoSplitRun, 700);
    }

    /** Bildirim erişimi olmadan da çalışan medya tuşu: son çalan uygulamaya gider. */
    private void mediaKey(int code) {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        playBtn.postDelayed(this::renderMusic, 600);
    }

    /** Bölünmüş ekranda harita kutusunu gizle; haritanın kendisi zaten yanda. */
    private void updateSplitUi() {
        View nav = findViewById(R.id.navFrame);
        nav.setVisibility(isInMultiWindowMode() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onMultiWindowModeChanged(boolean inMultiWindow, android.content.res.Configuration cfg) {
        super.onMultiWindowModeChanged(inMultiWindow, cfg);
        if (!inMultiWindow) sOwnScreen = true; // elle tam ekrana geçildi, haritayı geri açma
        if (inMultiWindow != compact) {
            recreate();
            return;
        }
        updateSplitUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        float wantScale = getSharedPreferences("surus", MODE_PRIVATE).getFloat("scale", 1f);
        if (wantScale != appliedScale || isInMultiWindowMode() != compact) {
            recreate();
            return;
        }
        updateSplitUi();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(BtReceiver.NOTIF_ID);

        map.onResume();
        seek.removeCallbacks(progressTick);
        seek.post(progressTick);
        status.resume();
        Shortcuts.render(this);
        renderCalls();
        arrangeQuick();
        startLocation();

        NavState.listener = this::renderNav;
        renderNav();

        if (hasListenerAccess()) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerCn);
            } catch (SecurityException ignored) {
            }
        }
        pickController();
        hideBars();

        Rotation.giveBack(this); // daha önce kilitlendiyse telefonun kendi ayarına dön
        if (sOwnScreen) sOwnScreen = false;
        else autoSplit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        playBtn.removeCallbacks(autoSplitRun);
        status.pause();
        seek.removeCallbacks(progressTick);
        map.onPause();
        myLoc.disableMyLocation();
        NavState.listener = null;
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (Exception ignored) {
        }
        setController(null);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CALL) {
            if (pendingCall != null) call(pendingCall);
            pendingCall = null;
            return;
        }
        if (code == REQ_LOC) {
            startLocation();
            status.pause();
            status.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (status != null) status.destroy();
        if (browser != null) try { browser.disconnect(); } catch (Exception ignored) { }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        getWindow().setDecorFitsSystemWindows(false);
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
