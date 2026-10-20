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
    private Wave wave;
    private static final int REQ_NOTE = 51;
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
        @Override public void onPlaybackStateChanged(PlaybackState s) {
            // Başka bir uygulamanın oturumu durduysa kutudan kaldır (Spotify'ınki kalır)
            if (ctrl != null && !Apps.musicPkg(p.music()).equals(ctrl.getPackageName())
                    && (s == null || (s.getState() != PlaybackState.STATE_PLAYING
                        && s.getState() != PlaybackState.STATE_BUFFERING))) {
                pickController();
                return;
            }
            renderMusic();
            seek.removeCallbacks(progressTick);
            seek.post(progressTick);
        }
        @Override public void onSessionDestroyed() { pickController(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            list -> pickController();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CarLink.watchApp(getApplication()); // alta alınınca Bluetooth'u kapatmak için
        p = new Prefs(this);
        Places.migrate(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        compact = isInMultiWindowMode();
        setContentView(compact ? R.layout.activity_main_compact : R.layout.activity_main);
        if (compact) ((TextView) findViewById(R.id.clock)).setTextSize(38);
        status = new Status(this);
        // El sallama: 1 kez / 2 kez ne yapacağı Ayarlar'dan seçilir (varsayılan: sonraki şarkı / durdur-başlat)
        wave = new Wave(this, () -> runWave(WaveActions.get(this, 1)), () -> runWave(WaveActions.get(this, 2)));

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
        // Kapak, yanındaki ilerleme çubuğu + tuşlar kadar yüksek ve kare olsun (altı üstü boş kalmasın)
        View controls = findViewById(R.id.musicControls);
        if (controls != null) controls.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            View body = (View) v.getParent();
            int h = Math.min(b - t, Math.round(body.getWidth() * 0.42f));
            ViewGroup.LayoutParams lp = musicArt.getLayoutParams();
            if (h > 0 && (lp.width != h || lp.height != h)) {
                lp.width = h;
                lp.height = h;
                v.post(() -> musicArt.setLayoutParams(lp));
            }
        });
        musicArt.setClipToOutline(true);
        musicArt.setOnClickListener(v -> Apps.openMusic(this, p));

        findViewById(R.id.assistBtn).setOnClickListener(v -> openAssistant());
        findViewById(R.id.assistBtn).setOnLongClickListener(v -> { takeVoiceNote(); return true; });
        // Düğmeler uzayınca simgeler dev olmasın: simge en fazla 34dp
        findViewById(R.id.iconsGrid).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                v.post(() -> { squareQuick(); fitQuickIcons(); }));
        // Bas: kayıtlı park yerine rota. Basılı tut: o anki konumu kaydet.
        findViewById(R.id.parkBtn).setOnClickListener(v -> showPark()); // erişilebilirlik için
        findViewById(R.id.parkBtn).setOnLongClickListener(v -> { saveParkNow(); return true; });
        // Telefonun uzun basma süresi yerine kısa (0,35 sn) basılı tutma
        QuickHold.attach(findViewById(R.id.parkBtn), 350, this::showPark, this::saveParkNow);
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                openOwn(new Intent(this, SettingsActivity.class)));
        // Pil/Bluetooth kutusuna dokun: araba bağlı değilse bağlanmayı dener, olmazsa Bluetooth ekranını açar
        findViewById(R.id.statusTile).setOnClickListener(v -> CarLink.tap(this, this::showTip));
        // Hava kutusuna dokun: önümüzdeki 6 saat altta birkaç saniye görünür
        findViewById(R.id.weatherTile).setOnClickListener(v -> showHourly());

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

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void renderNav() {
        status.onNavEta(NavState.active ? NavState.etaAt : 0);
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
        navIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.accent_ink)));
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
        // Durmuş başka uygulama oturumlarını (Instagram/TikTok videosu vb.) gösterme:
        // seçili müzik uygulaması yoksa yalnızca o an gerçekten çalan oturum gösterilir.
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

    /** Üst sıra: hava, bildirimler, durum. Alt sıra: mikrofon, aramalar, rotalar, araba, ayarlar. */
    private static final int[] ROW1 = {
            R.id.weatherTile, R.id.notifTile, R.id.statusTile};
    /** Alt sıra: hızlı aramalar, sonra hızlı rotalar (koddan eklenir). */
    static final int[] CALLS = {R.id.call1Btn, R.id.call2Btn, R.id.call3Btn, R.id.call4Btn, R.id.call5Btn};
    private static final int[] QUICK = {
            R.id.statusTile, R.id.weatherTile, R.id.notifTile, R.id.parkBtn, R.id.assistBtn, R.id.settingsBtn,
            R.id.call1Btn, R.id.call2Btn, R.id.call3Btn, R.id.call4Btn, R.id.call5Btn, R.id.missedBtn};
    private final java.util.List<View> placeViews = new java.util.ArrayList<>();

    /** Düğmeleri Ayarlar'daki düzene göre iki sıraya dizer; kapalı olanlar gizli bir kapta bekler. */
    private void arrangeQuick() {
        ViewGroup r1 = findViewById(R.id.iconsRow1), r2 = findViewById(R.id.iconsRow2);
        ViewGroup grid = findViewById(R.id.iconsGrid);
        ViewGroup hidden = grid.findViewWithTag("hiddenQuick");
        if (hidden == null) {
            hidden = new android.widget.FrameLayout(this);
            hidden.setTag("hiddenQuick");
            hidden.setVisibility(View.GONE);
            grid.addView(hidden, new ViewGroup.LayoutParams(0, 0));
        }
        for (View v : placeViews) if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        placeViews.clear();
        // Görünümleri önce topla: ağaçtan çıkınca findViewById onları bulamaz
        java.util.Map<Integer, View> views = new java.util.HashMap<>();
        for (int id : QUICK) {
            View v = findViewById(id);
            views.put(id, v);
            ((ViewGroup) v.getParent()).removeView(v);
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Layout.Entry e : Layout.load(this)) {
            if (!e.on) continue;
            ViewGroup row = e.row == 1 ? r1 : r2;
            switch (e.key) {
                case "weather": addQuick(row, views.get(R.id.weatherTile)); used.add(R.id.weatherTile); break;
                case "notif": addQuick(row, views.get(R.id.notifTile)); used.add(R.id.notifTile); break;
                case "status": addQuick(row, views.get(R.id.statusTile)); used.add(R.id.statusTile); break;
                case "assist": addQuick(row, views.get(R.id.assistBtn)); used.add(R.id.assistBtn); break;
                case "park": addQuick(row, views.get(R.id.parkBtn)); used.add(R.id.parkBtn); break;
                case "settings": addQuick(row, views.get(R.id.settingsBtn)); used.add(R.id.settingsBtn); break;
                case "missed": addQuick(row, views.get(R.id.missedBtn)); used.add(R.id.missedBtn); break;
                case "calls":
                    for (int id : CALLS) { addQuick(row, views.get(id)); used.add(id); }
                    break;
                case "places":
                    for (View v : buildPlaceButtons()) addQuick(row, v);
                    break;
            }
        }
        // Kapalı düğmeler görünmez kapta kalır (bilgileri güncellenmeye devam eder)
        for (int id : QUICK) if (!used.contains(id)) hidden.addView(views.get(id));
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
            // Büyük simge üstte, adı altında (arama düğmeleri gibi)
            android.graphics.drawable.Drawable ic = getDrawable(Places.iconRes(Places.icon(this, i))).mutate();
            int sz = Math.round(26 * d);
            ic.setBounds(0, 0, sz, sz);
            ic.setTint(getColor(R.color.amber));
            b.setCompoundDrawablesRelative(null, ic, null, null);
            b.setCompoundDrawablePadding(Math.round(1 * d));
            b.setIncludeFontPadding(false);
            b.setGravity(android.view.Gravity.CENTER);
            b.setPadding(Math.round(4 * d), Math.round(4 * d), Math.round(4 * d), Math.round(2 * d));
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
            float cols = visibleCount(r);
            // Alt sıra boşsa yeri yine dursun (üst sıra uzamasın)
            r.setVisibility(cols == 0 ? View.INVISIBLE : View.VISIBLE);
            float w = Math.round(65 * d); // sabit genişlik (yükseklikten bağımsız)
            if (cols > 0) w = Math.min(w, (g.getWidth() - (cols - 1) * gap) / cols); // sığmazsa daralt
            boolean first = true;
            for (int i = 0; i < r.getChildCount(); i++) {
                View c = r.getChildAt(i);
                if (c.getVisibility() == View.GONE) continue;
                float k = weightOf(c);
                int cw = Math.round(w * k + gap * (k - 1)), ms = first ? 0 : gap;
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

    private static float visibleCount(ViewGroup r) {
        float n = 0;
        for (int i = 0; i < r.getChildCount(); i++)
            if (r.getChildAt(i).getVisibility() != View.GONE) n += weightOf(r.getChildAt(i));
        return n;
    }

    private static float weightOf(View v) {
        int id = v.getId();
        if (id == R.id.statusTile) return 2.4f; // simgeler ve yüzde sığacak kadar
        if (id == R.id.notifTile) return 2.6f;  // bildirim metnine biraz daha yer
        if (id == R.id.weatherTile) return 2;
        return 1;
    }

    /** Simge boyutunu düğmenin küçük kenarına göre sınırla. */
    private void fitQuickIcons() {
        float d = getResources().getDisplayMetrics().density;
        int max = Math.round(32 * d), min = Math.round(5 * d); // simgeler düğmede daha dolgun dursun
        for (int id : QUICK) {
            View c = findViewById(id);
            if (!(c instanceof ImageButton) || c.getWidth() == 0) continue;
            int px = Math.max(min, (c.getWidth() - max) / 2);
            int py = Math.max(min, (c.getHeight() - max) / 2);
            if (c.getPaddingLeft() != px || c.getPaddingTop() != py) c.setPadding(px, py, px, py);
        }
    }

    // ---------- El sallama işleri ----------

    /** Müzik işleri tuşların kendi işlevini kullanır (Play davranışı CLAUDE.md'deki gibi kalır). */
    private void runWave(String key) {
        switch (key) {
            case "next": findViewById(R.id.nextBtn).performClick(); break;
            case "prev": findViewById(R.id.prevBtn).performClick(); break;
            case "play": playBtn.performClick(); break;
            case "notif":
                if (Notifs.list().isEmpty()) Toast.makeText(this, "Bekleyen bildirim yok", Toast.LENGTH_SHORT).show();
                else showFullNotifs();
                break;
            case "read": readLatestNotif(); break;
            case "mute": toggleMute(); break;
            case "assist": openAssistant(); break;
            case "home": goHome(); break;
            case "park": saveParkNow(); break;
            default: break; // "none"
        }
    }

    private android.speech.tts.TextToSpeech waveTts;
    private boolean waveTtsReady;
    private String waveTtsPending;

    /** En yeni bildirimi sesli okur: "WhatsApp'tan Annem: yoldayım". Ekranda da bildirim listesi açılır. */
    private void readLatestNotif() {
        List<Notifs.Item> l = Notifs.list();
        String say;
        if (l.isEmpty()) {
            say = "Bekleyen bildirim yok";
        } else {
            Notifs.Item it = l.get(0);
            StringBuilder sb = new StringBuilder();
            if (it.app != null && !it.app.isEmpty()) sb.append(it.app).append(". ");
            if (it.who != null && !it.who.isEmpty()) sb.append(it.who).append(": ");
            String body = it.text != null && !it.text.isEmpty() ? it.text : it.full;
            if (body != null) sb.append(body.length() > 220 ? body.substring(0, 220) : body);
            if (l.size() > 1) sb.append(". ").append(l.size() - 1).append(" bildirim daha var.");
            say = sb.toString();
            showFullNotifs();
        }
        speakWave(say);
    }

    private void speakWave(String text) {
        if (waveTts == null) {
            waveTtsPending = text;
            waveTts = new android.speech.tts.TextToSpeech(this, st -> {
                if (st != android.speech.tts.TextToSpeech.SUCCESS) return;
                waveTts.setLanguage(new java.util.Locale("tr", "TR"));
                waveTts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
                waveTtsReady = true;
                if (waveTtsPending != null) speakWave(waveTtsPending);
                waveTtsPending = null;
            });
            return;
        }
        if (!waveTtsReady) { waveTtsPending = text; return; }
        waveTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "wave");
    }

    private void toggleMute() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
    }

    /** Ayarlar'daki hızlı rotalardan "Ev" simgeli olana rota başlatır. */
    private void goHome() {
        int n = Places.count(this);
        for (int i = 1; i <= n; i++) {
            if ("home".equals(Places.icon(this, i))) {
                String coord = Places.coord(this, i);
                if (coord != null && !coord.isEmpty()) { goTo(coord); return; }
            }
        }
        if (!p.home().isEmpty()) { goTo(p.home()); return; }
        Toast.makeText(this, "Ev adresi kayıtlı değil (Ayarlar → hızlı rotalar)", Toast.LENGTH_SHORT).show();
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

    /** Kayıtlı park yeri varsa araba simgesinin köşesinde sarı "P". */
    private void refreshParkBadge() {
        ImageButton pb = findViewById(R.id.parkBtn);
        if (pb == null) return;
        if (Park.coord(this).isEmpty()) {
            pb.setImageResource(R.drawable.ic_car_add); // araba + gri artı: "basılı tut, kaydet"
            pb.setImageTintList(null);
            pb.setContentDescription("Arabam nerede: kayıtlı park yeri yok, kaydetmek için basılı tut");
        } else {
            pb.setImageResource(R.drawable.ic_car_parked);
            pb.setImageTintList(null);
            pb.setContentDescription("Arabam nerede: park yeri kayıtlı, " + parkAgo(Park.savedAt(this)));
        }
    }

    private static String parkAgo(long at) {
        long mins = (System.currentTimeMillis() - at) / 60000;
        return mins < 1 ? "az önce" : mins < 60 ? mins + " dk önce"
                : mins < 1440 ? (mins / 60) + " saat önce" : (mins / 1440) + " gün önce";
    }

    private static String parkWhen(long at) {
        java.util.Calendar now = java.util.Calendar.getInstance(), t = java.util.Calendar.getInstance();
        t.setTimeInMillis(at);
        String hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date(at));
        int dd = now.get(java.util.Calendar.DAY_OF_YEAR) - t.get(java.util.Calendar.DAY_OF_YEAR)
                + 365 * (now.get(java.util.Calendar.YEAR) - t.get(java.util.Calendar.YEAR));
        if (dd == 0) return "Bugün " + hm;
        if (dd == 1) return "Dün " + hm;
        return new java.text.SimpleDateFormat("d MMM HH:mm", new java.util.Locale("tr", "TR")).format(new java.util.Date(at));
    }

    /**
     * Dokununca: ekranın altında park paneli. Ne zaman park edildiği, ne kadar uzakta olduğu, adres ve not;
     * Yürüyerek git, Not, Paylaş, Sil.
     */
    private void showPark() {
        String c = Park.coord(this);
        if (c.isEmpty()) {
            Toast.makeText(this, "Henüz park yeri yok. Kaydetmek için bu düğmeye kısaca basılı tut.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }

        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.tile_dark);
        panel.setElevation(12 * d);
        int pad = Math.round(16 * d);
        panel.setPadding(pad, pad, pad, pad);

        // Başlık: simge + "Park yerin" + ne zaman
        android.widget.LinearLayout head = new android.widget.LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_car_parked);
        head.addView(ic, new android.widget.LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d)));
        TextView title = new TextView(this);
        long at = Park.savedAt(this);
        android.text.SpannableStringBuilder tb = new android.text.SpannableStringBuilder("Park yerin");
        tb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, tb.length(), 0);
        int st = tb.length();
        tb.append("\n").append(parkWhen(at)).append(" · ").append(parkAgo(at));
        tb.setSpan(new android.text.style.RelativeSizeSpan(0.78f), st, tb.length(), 0);
        tb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), st, tb.length(), 0);
        title.setText(tb);
        title.setTextSize(18);
        title.setTextColor(getColor(R.color.fg));
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Math.round(12 * d));
        head.addView(title, tlp);
        panel.addView(head);

        // Uzaklık ve adres (adres arka planda bulunur)
        TextView where = new TextView(this);
        where.setTextSize(15);
        where.setTextColor(getColor(R.color.muted));
        where.setPadding(0, Math.round(10 * d), 0, 0);
        String dist = parkDistance(c);
        where.setText(dist == null ? "" : dist);
        where.setVisibility(dist == null ? View.GONE : View.VISIBLE);
        panel.addView(where);
        new Thread(() -> {
            String addr = parkAddress(c);
            if (addr == null) return;
            runOnUiThread(() -> {
                where.setText(dist == null ? addr : dist + " · " + addr);
                where.setVisibility(View.VISIBLE);
            });
        }).start();

        String note = Park.note(this);
        if (!note.isEmpty()) {
            TextView nt = new TextView(this);
            nt.setText(note);
            nt.setTextSize(20);
            nt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            nt.setTextColor(getColor(R.color.fg));
            nt.setPadding(0, Math.round(10 * d), 0, 0);
            panel.addView(nt);
        }

        // Düğmeler
        android.widget.LinearLayout btns = new android.widget.LinearLayout(this);
        btns.setPadding(0, Math.round(14 * d), 0, 0);
        TextView go = parkBtn("Yürüyerek git", true);
        go.setOnClickListener(v -> { hideBanner(); Apps.openWalk(this, p, c); });
        btns.addView(go, new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1.6f));
        TextView noteBtn = parkBtn(note.isEmpty() ? "Not ekle" : "Not", false);
        noteBtn.setOnClickListener(v -> parkSheet(3));
        btns.addView(noteBtn, parkBtnLp(d));
        TextView share = parkBtn("Paylaş", false);
        share.setOnClickListener(v -> {
            hideBanner();
            String txt = "Arabam burada: https://maps.google.com/?q=" + c + (note.isEmpty() ? "" : " (" + note + ")");
            try {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, txt), "Park yerini paylaş"));
            } catch (Exception ignored) {
            }
        });
        btns.addView(share, parkBtnLp(d));
        TextView del = parkBtn("Sil", false);
        del.setTextColor(getColor(R.color.low));
        del.setOnClickListener(v -> {
            if (!"sure".equals(v.getTag())) { // yanlışlıkla silinmesin: ikinci dokunuşta sil
                v.setTag("sure");
                del.setText("Emin misin?");
                return;
            }
            Park.clear(this);
            refreshParkBadge();
            hideBanner();
            Toast.makeText(this, "Park yeri silindi", Toast.LENGTH_SHORT).show();
        });
        btns.addView(del, parkBtnLp(d));
        panel.addView(btns);

        panel.setOnClickListener(v -> hideBanner());
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        content.addView(panel, lp);
        bannerView = panel;
        panel.setAlpha(0f);
        panel.setTranslationY(40 * d);
        panel.animate().alpha(1f).translationY(0).setDuration(200).start();
        panel.postDelayed(bannerHide, 30000);
    }

    private TextView parkBtn(String text, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(android.view.Gravity.CENTER);
        b.setTextSize(15);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setMaxLines(1); // (setSingleLine otomatik küçültmeyi bozuyor)
        b.setPadding(Math.round(4 * getResources().getDisplayMetrics().density), 0,
                Math.round(4 * getResources().getDisplayMetrics().density), 0);
        // dar ekranda yazı kesilmesin, sığacak kadar küçülsün
        b.setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        b.setClickable(true);
        b.setFocusable(true);
        b.setBackgroundResource(primary ? R.drawable.tile_accent : R.drawable.btn_outline);
        b.setTextColor(getColor(primary ? R.color.accent_ink : R.color.fg));
        return b;
    }

    private static android.widget.LinearLayout.LayoutParams parkBtnLp(float d) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1f);
        lp.setMarginStart(Math.round(8 * d));
        return lp;
    }

    /** "Buradan yaklaşık 350 m" — o anki konum bilinmiyorsa null. */
    private String parkDistance(String park) {
        String here = Park.current(this);
        if (here == null) return null;
        try {
            String[] a = park.split(","), b = here.split(",");
            float[] r = new float[1];
            Location.distanceBetween(Double.parseDouble(a[0]), Double.parseDouble(a[1]),
                    Double.parseDouble(b[0]), Double.parseDouble(b[1]), r);
            if (r[0] < 30) return "Arabanın yanındasın";
            if (r[0] < 1000) return "Buradan yaklaşık " + (Math.round(r[0] / 10f) * 10) + " m";
            return String.format(new java.util.Locale("tr", "TR"), "Buradan yaklaşık %.1f km", r[0] / 1000f);
        } catch (Exception e) {
            return null;
        }
    }

    /** Park yerinin sokak adı (telefonun adres servisiyle); bulunamazsa null. */
    private String parkAddress(String park) {
        try {
            String[] a = park.split(",");
            List<android.location.Address> l = new android.location.Geocoder(this, new java.util.Locale("tr", "TR"))
                    .getFromLocation(Double.parseDouble(a[0]), Double.parseDouble(a[1]), 1);
            if (l == null || l.isEmpty()) return null;
            android.location.Address ad = l.get(0);
            String street = ad.getThoroughfare(), no = ad.getSubThoroughfare(), area = ad.getSubLocality();
            StringBuilder b = new StringBuilder();
            if (street != null) b.append(street).append(no != null ? " " + no : "");
            if (area != null) b.append(b.length() > 0 ? ", " : "").append(area);
            return b.length() > 0 ? b.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Yanlışlıkla basılı tutulursa "Geri al" için önceki park yeri
    private String prevParkCoord, prevParkNote;
    private long prevParkAt;
    private static final int REQ_PARK_VOICE = 52;

    private void saveParkNow() {
        prevParkCoord = Park.coord(this);
        prevParkAt = Park.savedAt(this);
        prevParkNote = Park.note(this);
        parkSheet(0);
        Park.saveNow(this, ok -> {
            if (ok) {
                refreshParkBadge();
                parkSheet(1);
            } else {
                parkSheet(2);
            }
        });
    }

    /**
     * Park kaydı paneli (ekranın altında, diğer panellerle aynı görünüm).
     * 0: kaydediliyor, 1: kaydedildi + not alanı, 2: konum bulunamadı, 3: notu düzenle.
     */
    private void parkSheet(int mode) {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }

        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.tile_dark);
        panel.setElevation(12 * d);
        int pad = Math.round(16 * d);
        panel.setPadding(pad, pad, pad, pad);
        panel.setClickable(true); // alttaki düğmelere dokunuş geçmesin

        // Başlık satırı
        android.widget.LinearLayout head = new android.widget.LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        View lead;
        if (mode == 0) {
            android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(getColor(R.color.accent)));
            lead = pb;
        } else {
            ImageView ic = new ImageView(this);
            ic.setImageResource(mode == 2 ? R.drawable.ic_car : R.drawable.ic_car_parked);
            if (mode == 2) ic.setImageTintList(ColorStateList.valueOf(getColor(R.color.low)));
            lead = ic;
        }
        head.addView(lead, new android.widget.LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d)));
        long at = Park.savedAt(this);
        String t1, t2;
        switch (mode) {
            case 0: t1 = "Park yeri kaydediliyor…"; t2 = "Konumun alınıyor, birkaç saniye sürebilir"; break;
            case 1: t1 = "✓ Park yerin kaydedildi"; t2 = parkWhen(at); break;
            case 2: t1 = "Konum bulunamadı"; t2 = "Konumun açık olduğundan emin ol, gökyüzünü gören bir yerde tekrar dene"; break;
            default: t1 = "Park notu"; t2 = parkWhen(at); break;
        }
        TextView title = new TextView(this);
        android.text.SpannableStringBuilder tb = new android.text.SpannableStringBuilder(t1);
        tb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, t1.length(), 0);
        if (t1.startsWith("✓")) tb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.accent)), 0, 1, 0);
        int st = tb.length();
        tb.append("\n").append(t2);
        tb.setSpan(new android.text.style.RelativeSizeSpan(0.78f), st, tb.length(), 0);
        tb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), st, tb.length(), 0);
        title.setText(tb);
        title.setTextSize(18);
        title.setTextColor(getColor(R.color.fg));
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Math.round(12 * d));
        head.addView(title, tlp);
        panel.addView(head);
        if (mode == 1) { // adres gelince alt satıra ekle
            String c = Park.coord(this);
            new Thread(() -> {
                String addr = parkAddress(c);
                if (addr != null) runOnUiThread(() -> {
                    int s2 = tb.length();
                    tb.append(" · ").append(addr);
                    tb.setSpan(new android.text.style.RelativeSizeSpan(0.78f), s2, tb.length(), 0);
                    tb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), s2, tb.length(), 0);
                    title.setText(tb);
                });
            }).start();
        }

        // Not alanı (+ sesle yazdırma)
        android.widget.EditText et = null;
        if (mode == 1 || mode == 3) {
            android.widget.LinearLayout nr = new android.widget.LinearLayout(this);
            nr.setGravity(android.view.Gravity.CENTER_VERTICAL);
            nr.setPadding(0, Math.round(14 * d), 0, 0);
            et = new android.widget.EditText(this);
            et.setBackgroundResource(R.drawable.field_bg);
            et.setPadding(Math.round(14 * d), 0, Math.round(14 * d), 0);
            et.setTextSize(16);
            et.setTextColor(getColor(R.color.fg));
            et.setHintTextColor(getColor(R.color.muted));
            et.setHint("Not ekle: B2 katı, 47 numara");
            et.setSingleLine(true);
            et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            et.setText(Park.note(this));
            et.setSelection(et.getText().length());
            nr.addView(et, new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1f));
            android.widget.ImageButton mic = new android.widget.ImageButton(this);
            mic.setImageResource(R.drawable.ic_mic);
            mic.setImageTintList(ColorStateList.valueOf(getColor(R.color.accent)));
            mic.setBackgroundResource(R.drawable.btn_outline);
            mic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mic.setPadding(Math.round(13 * d), Math.round(13 * d), Math.round(13 * d), Math.round(13 * d));
            mic.setContentDescription("Notu sesle söyle");
            mic.setOnClickListener(v -> parkVoiceNote());
            android.widget.LinearLayout.LayoutParams mlp = new android.widget.LinearLayout.LayoutParams(Math.round(52 * d), Math.round(52 * d));
            mlp.setMarginStart(Math.round(8 * d));
            nr.addView(mic, mlp);
            panel.addView(nr);
            et.setOnFocusChangeListener((v, f) -> { if (f) panel.removeCallbacks(bannerHide); });
        }
        final android.widget.EditText field = et;

        // Düğmeler
        android.widget.LinearLayout btns = new android.widget.LinearLayout(this);
        btns.setPadding(0, Math.round(12 * d), 0, 0);
        Runnable saveNote = () -> {
            if (field != null) {
                Park.setNote(this, field.getText().toString().trim());
                hideKeyboard(field);
            }
            hideBanner();
        };
        if (mode == 1) {
            TextView ok = parkBtn("Tamam", true);
            ok.setOnClickListener(v -> saveNote.run());
            btns.addView(ok, new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1.4f));
            TextView undo = parkBtn("Geri al", false);
            undo.setOnClickListener(v -> {
                if (field != null) hideKeyboard(field);
                if (prevParkCoord == null || prevParkCoord.isEmpty()) Park.clear(this);
                else Park.restore(this, prevParkCoord, prevParkAt, prevParkNote);
                refreshParkBadge();
                hideBanner();
                Toast.makeText(this, "Park kaydı geri alındı", Toast.LENGTH_SHORT).show();
            });
            btns.addView(undo, parkBtnLp(d));
        } else if (mode == 2) {
            TextView again = parkBtn("Tekrar dene", true);
            again.setOnClickListener(v -> saveParkNow());
            btns.addView(again, new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1.4f));
            TextView close = parkBtn("Kapat", false);
            close.setOnClickListener(v -> hideBanner());
            btns.addView(close, parkBtnLp(d));
        } else if (mode == 3) {
            TextView ok = parkBtn("Kaydet", true);
            ok.setOnClickListener(v -> saveNote.run());
            btns.addView(ok, new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * d), 1.4f));
            TextView cancel = parkBtn("Vazgeç", false);
            cancel.setOnClickListener(v -> { if (field != null) hideKeyboard(field); hideBanner(); });
            btns.addView(cancel, parkBtnLp(d));
        }
        if (btns.getChildCount() > 0) panel.addView(btns);
        if (field != null) field.setOnEditorActionListener((v, a, e) -> { saveNote.run(); return true; });

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        // Klavye açılınca panel klavyenin üstüne çıksın
        panel.setOnApplyWindowInsetsListener((v, in) -> {
            int ime = in.getInsets(WindowInsets.Type.ime()).bottom;
            int bars = in.getInsets(WindowInsets.Type.systemBars()).bottom;
            v.setTranslationY(-Math.max(0, ime - bars));
            return in;
        });
        content.addView(panel, lp);
        bannerView = panel;
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(200).start();
        if (mode == 1) panel.postDelayed(bannerHide, 25000);
        else if (mode == 2) panel.postDelayed(bannerHide, 20000);
        else if (mode == 3) panel.postDelayed(bannerHide, 60000);
    }

    private void hideKeyboard(View v) {
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        v.clearFocus();
    }

    /** Park notunu konuşarak yazdır. */
    private void parkVoiceNote() {
        try {
            sOwnScreen = true;
            startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Park notunu söyle (ör. B2 katı, 47 numara)"),
                    REQ_PARK_VOICE);
        } catch (Exception e) {
            sOwnScreen = false;
            Toast.makeText(this, "Ses tanıma bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Hızlı arama ----------

    private void renderCalls() {
        for (int i = 0; i < CALLS.length; i++) bindCall(CALLS[i], "call" + (i + 1));
        renderMissed();
    }

    /**
     * Cevapsız arama varsa: kırmızı telefon ve arayanın adıyla bir düğme. Dokun: geri ara (3 sn iptal süresi).
     * Basılı tut: düğmeyi kaldır. Cevapsız arama yokken görünmez.
     */
    private void renderMissed() {
        TextView b = findViewById(R.id.missedBtn);
        if (b == null) return;
        Notifs.Missed m = Notifs.missed(this);
        int was = b.getVisibility();
        if (m == null) {
            b.setVisibility(View.GONE);
        } else {
            b.setVisibility(View.VISIBLE);
            String who = m.who.trim();
            String first = who.matches("[+0-9 ()\\-]{5,}") ? who : who.split("\\s+")[0];
            float pd = getResources().getDisplayMetrics().density;
            android.graphics.drawable.Drawable ph = getDrawable(R.drawable.ic_phone).mutate();
            int psz = Math.round(16 * pd);
            ph.setBounds(0, 0, psz, psz);
            ph.setTint(getColor(R.color.low));
            android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder(" \n" + first);
            t.setSpan(new android.text.style.ImageSpan(ph, android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM),
                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setText(t);
            b.setIncludeFontPadding(false);
            b.setGravity(android.view.Gravity.CENTER);
            b.setLineSpacing(2 * pd, 1f);
            b.setTextSize(15);
            b.setTextColor(getColor(R.color.fg));
            b.setPadding(Math.round(4 * pd), 0, Math.round(4 * pd), 0);
            b.setContentDescription(first + " cevapsız arama, geri ara");
            b.setOnClickListener(v -> {
                if (m.number != null && !m.number.isEmpty()) {
                    confirmCall(first, m.number);
                } else if (m.callBack != null) {
                    sendIntent(m.callBack);
                    Notifs.dismissMissed(this);
                    renderMissed();
                } else {
                    Toast.makeText(this, "Numara bulunamadı", Toast.LENGTH_SHORT).show();
                }
            });
            b.setOnLongClickListener(v -> { Notifs.dismissMissed(this); renderMissed(); return true; });
        }
        if (was != b.getVisibility()) findViewById(R.id.iconsGrid).post(() -> { squareQuick(); fitQuickIcons(); });
    }

    private void sendIntent(android.app.PendingIntent pi) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                o.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(this, 0, null, null, null, null, o.toBundle());
            } else {
                pi.send();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Aranamadı", Toast.LENGTH_SHORT).show();
        }
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
        // Kısa ad (ör. "Annem"); yoksa rehberdeki adın ilk kelimesi
        String label = sp.getString(key + "Label", "").trim();
        String first = !label.isEmpty() ? label : name.trim().isEmpty() ? num : name.trim().split("\\s+")[0];
        // Küçük sarı telefon simgesi ve altında ad; ikisi birlikte tuşun tam ortasında
        float pd = getResources().getDisplayMetrics().density;
        android.graphics.drawable.Drawable ph = getDrawable(R.drawable.ic_phone).mutate();
        int psz = Math.round(16 * pd);
        ph.setBounds(0, 0, psz, psz);
        ph.setTint(getColor(R.color.accent));
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder(" \n" + first);
        t.setSpan(new android.text.style.ImageSpan(ph, android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setText(t);
        b.setIncludeFontPadding(false);
        b.setGravity(android.view.Gravity.CENTER);
        b.setCompoundDrawablesRelative(null, null, null, null);
        b.setLineSpacing(2 * pd, 1f);
        b.setTextSize(15);
        float dd = getResources().getDisplayMetrics().density;
        b.setPadding(Math.round(4 * dd), 0, Math.round(4 * dd), 0);
        b.setContentDescription(first + " ara");
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
        String text = "Yoldayım. İşte konumum: https://maps.google.com/?q=" + c;
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
        // Cevapsız aramadaki kişi geri arandıysa düğmesi kalksın
        Notifs.Missed mc = Notifs.missed(this);
        if (mc != null && !mc.number.isEmpty()) {
            String a = mc.number.replaceAll("[^0-9]", ""), b = num.replaceAll("[^0-9]", "");
            int k = Math.min(9, Math.min(a.length(), b.length()));
            if (k >= 7 && a.endsWith(b.substring(b.length() - k))) { Notifs.dismissMissed(this); renderMissed(); }
        }
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

    /*
     * DİKKAT: Bu davranış kullanıcı tarafından onaylandı (v6.6), değiştirmeden önce CLAUDE.md'ye bak.
     * Spotify kapalıyken Play → medya tuşu ile arka planda çalar; Spotify ekranı ASLA açılmaz.
     */
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
        // Spotify kapalı: sistemin medya tuşu, son çalan uygulamayı (Spotify) arka planda
        // uyandırıp kaldığı yerden çaldırır. Eski sürümlerdeki yöntem buydu.
        mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        playBtn.postDelayed(() -> { if (!musicActive()) startMusicApp(); }, 1500);
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
            pickController();
            // Hâlâ çalmıyorsa son bir kez medya tuşu (uygulama bu arada uyanmış olabilir)
            if (!musicActive()) {
                if (ctrl != null && Apps.musicPkg(p.music()).equals(ctrl.getPackageName()))
                    ctrl.getTransportControls().play();
                else mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
            }
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
        renderActions();
        MediaMetadata m = ctrl == null ? null : ctrl.getMetadata();
        if (m == null) {
            seek.setProgress(0);
            elapsed.setText("0:00");
            durationTv.setText("0:00");
            showArtPlaceholder();
            songTitle.setText("Müzik");
            songArtist.setVisibility(View.VISIBLE);
            songArtist.setText(hasListenerAccess()
                    ? Apps.musicName(p.music()) + " · açmak için dokun"
                    : "Şarkıyı görmek için Ayarlar → İzinler → Bildirim erişimi");
            AudioManager am = getSystemService(AudioManager.class);
            boolean active = am != null && am.isMusicActive();
            playBtn.setImageResource(active ? R.drawable.ic_pause : R.drawable.ic_play);
            return;
        }
        String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        // Nereden çalıyor: Spotify bazen şarkıcının yanına yazar ("… • Akıllı Mix'ten"), yoksa sıranın adı
        String from = null;
        if (artist != null) {
            int dot = artist.indexOf(" • ");
            if (dot > 0) {
                from = artist.substring(dot + 3).trim();
                artist = artist.substring(0, dot).trim();
            }
        }
        if (from == null || from.isEmpty()) {
            CharSequence q = ctrl.getQueueTitle();
            if (q != null && q.toString().trim().length() > 0) from = fromSuffix(q.toString().trim());
        }
        // Şarkı ve şarkıcı yan yana tek satırda (altta uygulamanın tuşlarına yer kalsın)
        android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder(title == null ? "" : title);
        if (artist != null && !artist.isEmpty()) {
            int st = line.length();
            line.append(line.length() > 0 ? "  ·  " : "").append(artist);
            line.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), st, line.length(), 0);
            line.setSpan(new android.text.style.RelativeSizeSpan(0.8f), st, line.length(), 0);
            line.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), st, line.length(), 0);
        }
        songTitle.setText(line);
        // Altında küçük: "Beğenilen Şarkılar'dan", "Akıllı Mix'ten"
        songArtist.setText(from == null ? "" : from);
        songArtist.setVisibility(from == null || from.isEmpty() ? View.GONE : View.VISIBLE);

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
        if (playing && wave.available() && Wave.enabled(this))
            Tips.maybe(this, "wave", "Elini telefonun üst kısmının üzerinden geçir: "
                    + WaveActions.label(WaveActions.get(this, 1)).toLowerCase(new java.util.Locale("tr", "TR"))
                    + ". İki kez geçir: "
                    + WaveActions.label(WaveActions.get(this, 2)).toLowerCase(new java.util.Locale("tr", "TR"))
                    + ". Ayarlar'dan değiştirebilirsin.");
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

    private String actionsKey = "";

    /**
     * Müzik uygulamasının telefona açtığı kendi tuşları (Spotify: beğen, öneriyi kaldır, karıştır…).
     * Uygulama hangi tuşları veriyorsa onlar görünür; dokununca uygulama açılmadan işini yapar.
     * (Oynat tuşuna dokunmaz; sadece o anki oturumun kendi tuşlarını gönderir.)
     */
    private void renderActions() {
        android.widget.LinearLayout box = findViewById(R.id.musicActions);
        if (box == null) return;
        MediaController c = ctrl;
        PlaybackState s = c == null ? null : c.getPlaybackState();
        List<PlaybackState.CustomAction> acts = s == null ? null : s.getCustomActions();
        StringBuilder key = new StringBuilder(c == null ? "" : c.getPackageName());
        if (acts != null) for (PlaybackState.CustomAction a : acts) key.append('|').append(a.getAction()).append(a.getIcon());
        if (key.toString().equals(actionsKey)) return;
        actionsKey = key.toString();
        box.removeAllViews();
        if (c == null || acts == null || acts.isEmpty()) {
            box.setVisibility(View.GONE);
            return;
        }
        android.content.res.Resources res;
        try {
            res = getPackageManager().getResourcesForApplication(c.getPackageName());
        } catch (Exception e) {
            box.setVisibility(View.GONE);
            return;
        }
        StringBuilder names = new StringBuilder();
        float d = getResources().getDisplayMetrics().density;
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        for (PlaybackState.CustomAction a : acts) {
            names.append(a.getName()).append(" (").append(a.getAction()).append("); ");
            if (box.getChildCount() >= 4) continue;
            // Radyo tuşu istenmedi
            String id = (a.getAction() + " " + a.getName()).toLowerCase(new java.util.Locale("tr", "TR"));
            if (id.contains("radio") || id.contains("radyo")) continue;
            android.graphics.drawable.Drawable icon;
            try {
                icon = res.getDrawable(a.getIcon(), null).mutate();
            } catch (Exception e) {
                continue;
            }
            ImageButton b = new ImageButton(this);
            b.setImageDrawable(icon);
            b.setImageTintList(ColorStateList.valueOf(getColor(R.color.fg)));
            b.setBackgroundResource(tv.resourceId);
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = Math.round(7 * d);
            b.setPadding(pad, pad, pad, pad);
            b.setContentDescription(a.getName());
            String action = a.getAction();
            Bundle extras = a.getExtras();
            CharSequence name = a.getName();
            b.setOnClickListener(v -> {
                MediaController now = ctrl;
                if (now != null && now.getPackageName().equals(c.getPackageName()))
                    now.getTransportControls().sendCustomAction(action, extras);
            });
            b.setOnLongClickListener(v -> { showTip(String.valueOf(name)); return true; });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    Math.round(52 * d), Math.round(40 * d));
            lp.setMarginStart(Math.round(3 * d));
            lp.setMarginEnd(Math.round(3 * d));
            box.addView(b, lp);
        }
        fitActions();
        android.util.Log.i("Kopilot", "müzik uygulamasının tuşları (" + c.getPackageName() + "): " + names);
        box.setVisibility(box.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** "Beğenilen Şarkılar" → "Beğenilen Şarkılar'dan" (Türkçe ses uyumuyla); rakamla bitiyorsa olduğu gibi. */
    static String fromSuffix(String name) {
        String n = name.trim();
        if (n.isEmpty()) return n;
        String low = n.toLowerCase(new java.util.Locale("tr", "TR"));
        char last = low.charAt(low.length() - 1);
        if (!Character.isLetter(last)) return n;
        char v = 'e';
        for (int i = low.length() - 1; i >= 0; i--) {
            if ("aıoueiöü".indexOf(low.charAt(i)) >= 0) { v = low.charAt(i); break; }
        }
        boolean back = "aıou".indexOf(v) >= 0;
        boolean hard = "çfhkpsştx".indexOf(last) >= 0;
        return n + "'" + (hard ? "t" : "d") + (back ? "an" : "en");
    }

    /** Dar ekranda (dikey) tuşlar sürelerin yerini kapatmasın: genişliği satıra göre ayarla. */
    private void fitActions() {
        android.widget.LinearLayout box = findViewById(R.id.musicActions);
        if (box == null || box.getChildCount() == 0) return;
        View row = (View) box.getParent();
        row.post(() -> {
            float d = getResources().getDisplayMetrics().density;
            int n = box.getChildCount();
            int room = row.getWidth() - Math.round(84 * d); // iki taraftaki süreler
            if (row.getWidth() == 0 || n == 0) return;
            int w = Math.max(Math.round(36 * d), Math.min(Math.round(52 * d), room / n - Math.round(6 * d)));
            for (int i = 0; i < n; i++) {
                android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) box.getChildAt(i).getLayoutParams();
                if (lp.width != w) { lp.width = w; box.getChildAt(i).setLayoutParams(lp); }
            }
        });
    }

    private void showArtPlaceholder() {
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
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
            // Sadece müzik çalarken saniyede bir güncelle
            PlaybackState st = ctrl == null ? null : ctrl.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) seek.postDelayed(this, 1000);
        }
    };

    // ---------- Bildirim kutusu ----------

    /** Dokun: bildirimlerin tamamı ekranın altında. Basılı tut: bildirimin uygulamasına geç. */
    private void setNotifClicks(View tile, Notifs.Item it) {
        tile.setOnClickListener(v -> showFullNotifs());
        tile.setOnLongClickListener(v -> { hideBanner(); openNotif(it); Notifs.hide(it.key); return true; });
    }

    /**
     * Bildirimlerin tam hali, ekranın altında (en yeni üstte). 8 saniye sonra ya da dokununca kapanır ve
     * okunanlar kutudan silinir. Bir bildirime basılı tut: o uygulamaya geçer.
     */
    private void showFullNotifs() {
        List<Notifs.Item> l = Notifs.list();
        ViewGroup content = findViewById(android.R.id.content);
        if (l.isEmpty() || content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }
        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.tile_dark);
        panel.setElevation(12 * d);
        int pad = Math.round(14 * d);
        panel.setPadding(pad, Math.round(10 * d), pad, Math.round(6 * d));

        int maxH = Math.round(content.getHeight() * 0.6f);
        android.widget.ScrollView sv = new android.widget.ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.max(maxH, 1), MeasureSpec.AT_MOST));
            }
        };
        android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        sv.addView(list);
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
        for (int i = 0; i < l.size(); i++) {
            Notifs.Item it = l.get(i);
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(0, Math.round(8 * d), 0, Math.round(8 * d));
            if (it.icon != null) {
                ImageView ic = new ImageView(this);
                ic.setImageDrawable(it.icon);
                android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(
                        Math.round(30 * d), Math.round(30 * d));
                ilp.topMargin = Math.round(2 * d);
                row.addView(ic, ilp);
            }
            TextView t = new TextView(this);
            String head = it.who;
            String meta = "  " + (it.app == null || it.app.isEmpty() ? "" : it.app + " · ") + hm.format(new java.util.Date(it.when));
            String body = it.missedCall ? "Cevapsız arama"
                    : (it.full != null && !it.full.isEmpty() ? it.full : it.text);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(head);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, head.length(), 0);
            int ms = sb.length();
            sb.append(meta);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.8f), ms, sb.length(), 0);
            sb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), ms, sb.length(), 0);
            if (body != null && !body.isEmpty()) sb.append("\n").append(body);
            t.setText(sb);
            t.setTextSize(17);
            t.setTextColor(getColor(R.color.fg));
            t.setLineSpacing(0, 1.1f);
            android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(Math.round(12 * d));
            row.addView(t, tlp);
            row.setOnClickListener(v -> panel.performClick());
            row.setOnLongClickListener(v -> { hideBanner(); openNotif(it); Notifs.hide(it.key); return true; });
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0x22FFFFFF);
                list.addView(line, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(d))));
            }
            list.addView(row);
        }
        panel.addView(sv, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Alt satır: ipucu solda, Temizle sağda
        android.widget.LinearLayout foot = new android.widget.LinearLayout(this);
        foot.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView hint = new TextView(this);
        hint.setText("Basılı tut: uygulamada aç · Dokun: kapat");
        hint.setTextSize(13);
        hint.setTextColor(getColor(R.color.muted));
        foot.addView(hint, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = new TextView(this);
        clear.setText("Temizle");
        clear.setTextSize(15);
        clear.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        clear.setTextColor(getColor(R.color.amber));
        clear.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(4 * d), Math.round(10 * d));
        clear.setOnClickListener(v -> { hideBanner(); Notifs.clearAll(); });
        foot.addView(clear);
        panel.addView(foot);

        // Kapanınca okunanlar kutudan silinir (telefonda kalırlar)
        Runnable done = () -> {
            if (bannerView == panel) hideBanner();
            Notifs.seen(l);
        };
        panel.setOnClickListener(v -> { panel.removeCallbacks(done); done.run(); });
        // Listeyi kaydırırken kapanmasın: her dokunuşta süre baştan başlar
        sv.setOnTouchListener((v, e) -> {
            panel.removeCallbacks(done);
            panel.postDelayed(done, 8000);
            return false;
        });
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        content.addView(panel, lp);
        bannerView = panel;
        panel.setAlpha(0f);
        panel.setTranslationY(40 * d);
        panel.animate().alpha(1f).translationY(0).setDuration(200).start();
        panel.postDelayed(done, 8000);
    }

    private void openNotif(Notifs.Item it) {
        try {
            if (it.open != null) {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                    o.setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    it.open.send(this, 0, null, null, null, null, o.toBundle());
                } else {
                    it.open.send();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private View bannerView;

    /** Yeni bildirimde ekranın altında birkaç saniyelik bilgi şeridi: "WhatsApp · Annem — mesaj…" */
    private void showBanner(Notifs.Item it) {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }
        android.widget.LinearLayout b = new android.widget.LinearLayout(this);
        b.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        b.setGravity(android.view.Gravity.CENTER_VERTICAL);
        b.setBackgroundResource(R.drawable.tile_dark);
        b.setElevation(12 * d);
        int pad = Math.round(14 * d);
        b.setPadding(pad, Math.round(10 * d), pad, Math.round(10 * d));
        if (it.icon != null) {
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(it.icon);
            b.addView(ic, new android.widget.LinearLayout.LayoutParams(Math.round(32 * d), Math.round(32 * d)));
        }
        TextView t = new TextView(this);
        String head = it.app == null || it.app.isEmpty() ? it.who : it.app + " · " + it.who;
        String body = it.missedCall ? "Cevapsız arama" : it.text;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(head);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, head.length(), 0);
        if (body != null && !body.isEmpty()) {
            int st = sb.length() + 1;
            sb.append("\n").append(body);
            sb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), st, sb.length(), 0);
        }
        t.setText(sb);
        t.setTextSize(16);
        t.setTextColor(getColor(R.color.fg));
        t.setMaxLines(3);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Math.round(12 * d));
        b.addView(t, tlp);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        // Dokun: tam halini göster. Basılı tut: uygulamaya geç.
        b.setOnClickListener(v -> showFullNotifs());
        b.setOnLongClickListener(v -> { hideBanner(); openNotif(it); Notifs.hide(it.key); return true; });
        content.addView(b, lp);
        bannerView = b;
        b.setAlpha(0f);
        b.setTranslationY(40 * d);
        b.animate().alpha(1f).translationY(0).setDuration(200).start();
        b.removeCallbacks(bannerHide);
        b.postDelayed(bannerHide, 7000);
    }

    private final Runnable bannerHide = this::hideBanner;

    /** Önümüzdeki 6 saatin havası: saat, simge, derece, yağış olasılığı. 8 saniye sonra ya da dokununca kapanır. */
    private void showHourly() {
        android.content.SharedPreferences sp = getSharedPreferences("surus", MODE_PRIVATE);
        String raw = sp.getString("wHr", "");
        long now = System.currentTimeMillis() / 1000;
        java.util.List<String[]> hours = new java.util.ArrayList<>();
        if (System.currentTimeMillis() - sp.getLong("wAt", 0) < 3 * 3600 * 1000L) {
            for (String h : raw.split(";")) {
                String[] f = h.split(",");
                if (f.length < 6) continue;
                try {
                    if (Long.parseLong(f[0]) > now) hours.add(f);
                } catch (NumberFormatException ignored) {
                }
                if (hours.size() == 6) break;
            }
        }
        if (hours.isEmpty()) {
            showTip("Saatlik tahmin henüz gelmedi. Biraz sonra tekrar dokun.");
            return;
        }
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }
        android.widget.LinearLayout strip = new android.widget.LinearLayout(this);
        strip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        strip.setBackgroundResource(R.drawable.tile_dark);
        strip.setElevation(12 * d);
        strip.setPadding(Math.round(8 * d), Math.round(10 * d), Math.round(8 * d), Math.round(10 * d));
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
        for (String[] f : hours) {
            android.widget.LinearLayout col = new android.widget.LinearLayout(this);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            TextView time = new TextView(this);
            time.setText(hm.format(new java.util.Date(Long.parseLong(f[0]) * 1000)));
            time.setTextSize(13);
            time.setTextColor(getColor(R.color.muted));
            col.addView(time);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(new WeatherIcon(Integer.parseInt(f[2]), "1".equals(f[3]), Integer.parseInt(f[5])));
            android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(
                    Math.round(34 * d), Math.round(34 * d));
            ilp.topMargin = ilp.bottomMargin = Math.round(4 * d);
            col.addView(ic, ilp);
            TextView temp = new TextView(this);
            temp.setText(f[1] + "°");
            temp.setTextSize(19);
            temp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            temp.setTextColor(getColor(R.color.fg));
            col.addView(temp);
            // Yağış olasılığı: %20 ve üstüyse mavi, yoksa boş (sütunlar hizalı kalsın)
            TextView rain = new TextView(this);
            int pop = Integer.parseInt(f[4]);
            rain.setText(pop >= 20 ? "%" + pop : " ");
            rain.setTextSize(13);
            rain.setTextColor(getColor(R.color.rain));
            col.addView(rain);
            strip.addView(col, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        strip.setOnClickListener(v -> hideBanner());
        strip.setContentDescription("Önümüzdeki saatlerin hava durumu");
        content.addView(strip, lp);
        bannerView = strip;
        strip.setAlpha(0f);
        strip.setTranslationY(40 * d);
        strip.animate().alpha(1f).translationY(0).setDuration(200).start();
        strip.postDelayed(bannerHide, 8000);
    }

    /** İpucu şeridi: ekranın altında 9 saniye, dokununca kapanır. */
    private void showTip(String text) {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (bannerView != null) { bannerView.removeCallbacks(bannerHide); content.removeView(bannerView); }
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(getColor(R.color.fg));
        t.setBackgroundResource(R.drawable.tile_dark);
        t.setElevation(12 * d);
        int pad = Math.round(14 * d);
        t.setPadding(pad, Math.round(12 * d), pad, Math.round(12 * d));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = Math.round(16 * d);
        lp.setMargins(m, m, m, m);
        t.setOnClickListener(v -> hideBanner());
        content.addView(t, lp);
        bannerView = t;
        t.setAlpha(0f);
        t.animate().alpha(1f).setDuration(200).start();
        t.postDelayed(bannerHide, 9000);
    }

    private void hideBanner() {
        View b = bannerView;
        if (b == null) return;
        bannerView = null;
        b.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            if (b.getParent() != null) ((ViewGroup) b.getParent()).removeView(b);
        }).start();
    }

    /** Bildirim kutusunu yarım dakikada bir yenile. */
    private final Runnable notifTick = new Runnable() {
        @Override public void run() {
            renderNotifs();
            playBtn.postDelayed(this, 30 * 1000L);
        }
    };

    private void renderNotifs() {
        ImageView icon = findViewById(R.id.notifIcon);
        TextView text = findViewById(R.id.notifText);
        View tile = findViewById(R.id.notifTile);
        if (icon == null || text == null) return;
        List<Notifs.Item> l = Notifs.list();
        if (l.isEmpty()) {
            icon.setVisibility(View.GONE); // boşken simge yok, sadece ortalı yazı
            ((android.widget.LinearLayout) tile).setGravity(android.view.Gravity.CENTER_VERTICAL);
            text.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            text.setGravity(android.view.Gravity.CENTER);
            ((android.widget.LinearLayout.LayoutParams) text.getLayoutParams()).setMarginStart(0);
            text.setText(hasListenerAccess() ? "Bildirim yok" : "Bildirim erişimi kapalı");
            text.setTextColor(getColor(R.color.muted));
            tile.setOnClickListener(null);
            tile.setOnLongClickListener(null);
            tile.setContentDescription("Bildirim yok");
            return;
        }
        Notifs.Item it = l.get(0);
        ((android.widget.LinearLayout) tile).setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (text.getLayoutParams().width != 0) {
            android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            text.setLayoutParams(tl);
        }
        // Sadece mesajın geldiği uygulamanın simgesi (WhatsApp vb.)
        icon.setVisibility(it.icon != null ? View.VISIBLE : View.GONE);
        text.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        ((android.widget.LinearLayout.LayoutParams) text.getLayoutParams()).setMarginStart(
                Math.round(8 * getResources().getDisplayMetrics().density));
        if (it.icon != null) {
            icon.setImageDrawable(it.icon);
            icon.setImageTintList(null);
        }
        String unit = it.msg ? "mesaj" : "bildirim";
        String what = it.missedCall ? "cevapsız arama" : it.count > 1 ? it.count + " " + unit : unit;
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date(it.when));
        String sub = (it.app == null || it.app.isEmpty() ? "" : it.app + " · ") + what + " · " + time
                + (l.size() > 1 ? "  +" + (l.size() - 1) : "");
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(it.who);
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, it.who.length(), 0);
        int st = b.length() + 1;
        b.append("\n").append(sub);
        b.setSpan(new android.text.style.RelativeSizeSpan(0.75f), st, b.length(), 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.muted)), st, b.length(), 0);
        text.setText(b);
        text.setTextColor(getColor(R.color.fg));
        tile.setContentDescription(it.who + ", " + it.app + " " + what);
        setNotifClicks(tile, it);
    }

    // ---------- Müzik kutusunda kaydırarak şarkı değiştirme ----------

    private android.view.GestureDetector swipe;
    private boolean swipeHandled, swipeTracking;

    /** Müzik kutusu üzerindeki dokunuşları izle: yatay hızlı kaydırma = önceki/sonraki şarkı. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        View tile = findViewById(R.id.musicTile);
        if (tile != null && seek != null) {
            if (swipe == null) {
                float dp = getResources().getDisplayMetrics().density;
                swipe = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent a, android.view.MotionEvent b, float vx, float vy) {
                        if (a == null) return false;
                        float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                        if (Math.abs(dx) < 60 * dp || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                        swipeHandled = true;
                        View t = findViewById(R.id.musicTile);
                        t.animate().translationX(dx > 0 ? 40 * dp : -40 * dp).setDuration(90)
                                .withEndAction(() -> t.animate().translationX(0).setDuration(140).start()).start();
                        // Sola kaydır = sonraki, sağa kaydır = önceki (tuşların kendi işlevi çalışır)
                        findViewById(dx < 0 ? R.id.nextBtn : R.id.prevBtn).performClick();
                        return true;
                    }
                });
            }
            android.graphics.Rect r = new android.graphics.Rect(), sr = new android.graphics.Rect();
            int x = (int) ev.getRawX(), y = (int) ev.getRawY();
            boolean onTile = tile.getGlobalVisibleRect(r) && r.contains(x, y);
            boolean onSeek = seek.getGlobalVisibleRect(sr) && sr.contains(x, y);
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                swipeHandled = false;
                swipeTracking = onTile && !onSeek; // ilerleme çubuğunda kaydırma şarkıyı sarar, karışmasın
            }
            if (swipeTracking) swipe.onTouchEvent(ev);
            if (swipeHandled && ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                // Kaydırma tuşa dokunma sayılmasın
                android.view.MotionEvent c = android.view.MotionEvent.obtain(ev);
                c.setAction(android.view.MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(c);
                c.recycle();
                swipeHandled = false;
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---------- Yaşam döngüsü ----------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCarIntent(intent);
    }

    // ---------- Sesli not ----------

    private void takeVoiceNote() {
        try {
            sOwnScreen = true;
            startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Notunu söyle"), REQ_NOTE);
        } catch (Exception e) {
            Toast.makeText(this, "Ses tanıma bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PARK_VOICE) {
            java.util.ArrayList<String> r = data == null || res != RESULT_OK ? null
                    : data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty() && !r.get(0).trim().isEmpty()) Park.setNote(this, r.get(0).trim());
            playBtn.post(this::showPark); // notuyla birlikte park bilgisini göster
            return;
        }
        if (req != REQ_NOTE || res != RESULT_OK || data == null) return;
        java.util.ArrayList<String> r = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (r == null || r.isEmpty() || r.get(0).trim().isEmpty()) return;
        Notes.add(this, r.get(0).trim());
        Toast.makeText(this, "📝 Not kaydedildi: " + r.get(0).trim(), Toast.LENGTH_LONG).show();
    }

    private void showNotesDialog() {
        java.util.List<String> l = Notes.list(this);
        if (l.isEmpty()) {
            Toast.makeText(this, "Henüz sesli not yok", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Sesli notların")
                .setItems(l.toArray(new String[0]), null)
                .setPositiveButton("Kapat", null)
                .setNegativeButton("Hepsini sil", (d, w) -> Notes.clear(this))
                .show();
    }

    private void handleCarIntent(Intent i) {
        if (i != null && i.getBooleanExtra(Notes.EXTRA_SHOW, false)) {
            i.removeExtra(Notes.EXTRA_SHOW);
            playBtn.post(this::showNotesDialog);
        }
        if (i != null && i.getBooleanExtra(BtReceiver.EXTRA_FROM_CAR, false)) {
            i.removeExtra(BtReceiver.EXTRA_FROM_CAR);
            if (p.autoPlay()) playWhenCarAudioReady(System.currentTimeMillis());
        }
    }

    /**
     * Bluetooth önce "bağlandı" der ama arabanın ses kanalı (A2DP) birkaç saniye sonra açılır.
     * Hemen çalarsak müzik telefonun hoparlöründen başlar. Bu yüzden ses çıkışı gerçekten araba
     * olana kadar bekle (en fazla 25 sn), oturması için 1,5 sn daha bekle, sonra çal.
     * Araba ses kanalı hiç gelmezse çalma (telefondan çalmasın).
     */
    private void playWhenCarAudioReady(long since) {
        if (carAudioOut()) {
            playBtn.postDelayed(() -> {
                if (carAudioOut() && !musicActive()) Apps.playMedia(this);
            }, 1500);
        } else if (System.currentTimeMillis() - since < 25000) {
            playBtn.postDelayed(() -> playWhenCarAudioReady(since), 500);
        }
    }

    private boolean carAudioOut() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return false;
        for (android.media.AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if (d.getType() == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true;
        return false;
    }

    void goTo(String addr) {
        if (addr.trim().isEmpty()) {
            Toast.makeText(this, "Önce Ayarlar'dan adres ekle", Toast.LENGTH_SHORT).show();
            openOwn(new Intent(this, SettingsActivity.class));
        } else {
            Apps.openNavSplit(this, p, addr);
            // Hava tuşu varış yerinin havasını göstersin
            getSharedPreferences("surus", MODE_PRIVATE).edit()
                    .putString("destCoord", addr).putLong("destAt", System.currentTimeMillis()).apply();
            status.refreshWeather();
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

        seek.removeCallbacks(progressTick);
        seek.post(progressTick);
        status.resume();
        wave.start(this);
        Tips.shower = this::showTip;
        // İlk kurulum yapılmadıysa kısa tanıtım; yapıldıysa ipuçları
        if (OnboardingActivity.needed(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
        } else playBtn.postDelayed(() -> {
            if (!hasWindowFocus() && !isInMultiWindowMode()) return;
            Tips.maybe(this, "note", "Sesli komut tuşuna basılı tut: yolda aklına geleni söyle, not olarak kaydedilsin.");
            Tips.maybe(this, "park", "Arabadan inmeden araba tuşuna basılı tut: park yerin kaydedilir.");
            Tips.maybe(this, "swipe", "Müzik kutusunu sola kaydır: sonraki şarkı, sağa kaydır: önceki şarkı.");
        }, 5000);
        Shortcuts.render(this);
        CarLink.onAppOpen(this);
        refreshParkBadge();
        renderCalls();
        arrangeQuick();

        NavState.listener = this::renderNav;
        renderNav();
        Notifs.listener = () -> { renderNotifs(); renderMissed(); };
        // Bildirim dinleyicisi kapanmışsa yeniden bağla, açıksa mevcut bildirimleri tazele
        if (hasListenerAccess()) {
            if (MediaListener.instance != null) MediaListener.instance.resync();
            else try {
                android.service.notification.NotificationListenerService.requestRebind(listenerCn);
            } catch (Exception ignored) {
            }
        }
        Notifs.banner = this::showBanner;
        renderNotifs();
        playBtn.removeCallbacks(notifTick);
        playBtn.postDelayed(notifTick, 30 * 1000L);

        if (hasListenerAccess()) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerCn);
            } catch (SecurityException ignored) {
            }
        }
        pickController();
        hideBars();

        sResumed = true;
        LegacyDnd.restore(this);

        Rotation.giveBack(this); // daha önce kilitlendiyse telefonun kendi ayarına dön
        if (sOwnScreen) sOwnScreen = false;
        else autoSplit();
    }

    /** Ana ekran şu an önde mi (Rahatsız Etmeyin'i yanlışlıkla açık bırakmamak için). */
    static volatile boolean sResumed;

    @Override
    protected void onPause() {
        super.onPause();
        sResumed = false;
        playBtn.removeCallbacks(autoSplitRun);
        status.pause();
        wave.stop();
        Tips.shower = null;
        seek.removeCallbacks(progressTick);
        NavState.listener = null;
        Notifs.listener = null;
        Notifs.banner = null;
        playBtn.removeCallbacks(notifTick);
        hideBanner();
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
            status.pause();
            status.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (status != null) status.destroy();
        if (browser != null) try { browser.disconnect(); } catch (Exception ignored) { }
        if (waveTts != null) waveTts.shutdown();
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
