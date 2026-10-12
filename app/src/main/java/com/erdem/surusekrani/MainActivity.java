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
        findViewById(R.id.shuffleBtn).setOnClickListener(v -> toggleShuffle());
        findViewById(R.id.shuffleBtn).setOnLongClickListener(v -> { showMediaActions(); return true; });
        renderShuffle();

        findViewById(R.id.homeBtn).setOnClickListener(v -> goTo(p.homeTarget()));
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
    }

    private void setController(MediaController c) {
        if (ctrl != null) ctrl.unregisterCallback(mediaCb);
        ctrl = c;
        if (ctrl != null) ctrl.registerCallback(mediaCb);
    }

    /** Spotify'da karışık çalı açıp kapatır. */
    private void toggleShuffle() {
        if (ctrl == null) {
            Toast.makeText(this, "Karışık çal için müzik çalıyor olmalı ve bildirim erişimi açık olmalı",
                    Toast.LENGTH_LONG).show();
            return;
        }
        boolean next = !getSharedPreferences("surus", MODE_PRIVATE).getBoolean("shuffle", false);
        boolean sent = false;
        // 1) Uygulamanın kendi sunduğu "karışık çal" eylemi (Spotify bunu Android Auto için sunar)
        PlaybackState st = ctrl.getPlaybackState();
        if (st != null && st.getCustomActions() != null) {
            for (PlaybackState.CustomAction ca : st.getCustomActions()) {
                String id = (ca.getAction() + " " + ca.getName()).toLowerCase(new java.util.Locale("tr", "TR"));
                if (id.contains("shuffle") || id.contains("karış") || id.contains("karis")) {
                    ctrl.getTransportControls().sendCustomAction(ca, null);
                    sent = true;
                    break;
                }
            }
        }
        // 2) Genel Android müzik komutu
        if (!sent) {
            Bundle b = new Bundle();
            b.putInt("android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE", next ? 1 : 0);
            ctrl.getTransportControls().sendCustomAction(
                    "android.support.v4.media.session.action.SET_SHUFFLE_MODE", b);
        }
        getSharedPreferences("surus", MODE_PRIVATE).edit().putBoolean("shuffle", next).apply();
        renderShuffle();
        Toast.makeText(this, next ? "Karışık çal açık" : "Karışık çal kapalı", Toast.LENGTH_SHORT).show();
    }

    /** Test: çalan uygulamanın sunduğu ek komutları listeler; dokununca o komutu gönderir. */
    private void showMediaActions() {
        if (ctrl == null) {
            Toast.makeText(this, "Önce Spotify'da bir şarkı çal", Toast.LENGTH_LONG).show();
            return;
        }
        PlaybackState st = ctrl.getPlaybackState();
        java.util.List<PlaybackState.CustomAction> list = new java.util.ArrayList<>();
        if (st != null && st.getCustomActions() != null) list.addAll(st.getCustomActions());
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            items[i] = list.get(i).getName() + "\n" + list.get(i).getAction();
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(ctrl.getPackageName() + " · " + list.size() + " ek komut")
                .setItems(items.length == 0 ? new String[]{"(hiç ek komut yok)"} : items, (d, which) -> {
                    if (which < list.size()) {
                        ctrl.getTransportControls().sendCustomAction(list.get(which), null);
                        Toast.makeText(this, "Gönderildi: " + list.get(which).getName(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void renderShuffle() {
        boolean on = getSharedPreferences("surus", MODE_PRIVATE).getBoolean("shuffle", false);
        ImageButton b = findViewById(R.id.shuffleBtn);
        b.setImageTintList(ColorStateList.valueOf(getColor(on ? R.color.amber : R.color.muted)));
        b.setContentDescription(on ? "Karışık çal açık" : "Karışık çal kapalı");
    }

    private void togglePlay() {
        if (ctrl == null) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return;
        }
        PlaybackState s = ctrl.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
            ctrl.getTransportControls().pause();
        } else {
            ctrl.getTransportControls().play();
        }
    }

    private void renderMusic() {
        MediaMetadata m = ctrl == null ? null : ctrl.getMetadata();
        if (m == null) {
            seek.setProgress(0);
            elapsed.setText("0:00");
            durationTv.setText("0:00");
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

        PlaybackState s = ctrl.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        playBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
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
        String plate = p.plate();
        ((TextView) findViewById(R.id.plateText)).setText(plate);
        findViewById(R.id.plate).setVisibility(plate.isEmpty() ? View.GONE : View.VISIBLE);
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
