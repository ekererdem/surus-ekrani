package com.erdem.surusekrani;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/** Navigasyon uygulamasının üstünde duran, sürüklenebilir küçük müzik çubuğu. */
public class FloatService extends Service {
    private static final String CHANNEL = "float";
    private static final int NOTIF_ID = 11;

    static void show(Context c) {
        if (!Settings.canDrawOverlays(c)) return;
        c.startForegroundService(new Intent(c, FloatService.class));
    }

    static void hide(Context c) {
        c.stopService(new Intent(c, FloatService.class));
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private View root, bar, bubble;
    private TextView title, artist, weather;
    private ImageButton play;
    private SharedPreferences sp;

    private MediaSessionManager msm;
    private ComponentName listenerCn;
    private MediaController ctrl;

    private final MediaController.Callback mediaCb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { render(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { render(); }
        @Override public void onSessionDestroyed() { pickController(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pickController();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            renderWeather();
            if (ctrl == null) render();
            h.postDelayed(this, 30 * 1000L);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        sp = getSharedPreferences("surus", MODE_PRIVATE);
        wm = getSystemService(WindowManager.class);

        root = LayoutInflater.from(new ContextThemeWrapper(this, R.style.AppTheme))
                .inflate(R.layout.float_bar, null);
        bar = root.findViewById(R.id.fBar);
        bubble = root.findViewById(R.id.fBubble);
        title = root.findViewById(R.id.fTitle);
        artist = root.findViewById(R.id.fArtist);
        weather = root.findViewById(R.id.fWeather);
        play = root.findViewById(R.id.fPlay);

        root.findViewById(R.id.fPrev).setOnClickListener(v -> {
            if (ctrl != null) ctrl.getTransportControls().skipToPrevious();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        });
        root.findViewById(R.id.fNext).setOnClickListener(v -> {
            if (ctrl != null) ctrl.getTransportControls().skipToNext();
            else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        });
        play.setOnClickListener(v -> togglePlay());
        root.findViewById(R.id.fMin).setOnClickListener(v -> setCollapsed(true));
        root.findViewById(R.id.fClose).setOnClickListener(v -> stopSelf());

        // Çubuğun boş yerine dokun: Sürüş Ekranı'nı aç. Sürükle: taşı.
        bar.setOnClickListener(v -> openApp());
        bubble.setOnClickListener(v -> setCollapsed(false));
        dragWith(bar);
        dragWith(bubble);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        int defX = Math.max(0, (b.width() - dp(560)) / 2);
        int defY = Math.max(0, b.height() - dp(80));
        lp.x = clamp(sp.getInt("fX", defX), 0, b.width() - dp(60));
        lp.y = clamp(sp.getInt("fY", defY), 0, b.height() - dp(60));

        setCollapsedInternal(sp.getBoolean("fMin", false));
        wm.addView(root, lp);

        msm = getSystemService(MediaSessionManager.class);
        listenerCn = new ComponentName(this, MediaListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listenerCn, h);
        } catch (SecurityException ignored) {
        }
        pickController();
        h.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        try {
            msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
        }
        if (ctrl != null) ctrl.unregisterCallback(mediaCb);
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
    }

    // ---------- Ön plan bildirimi ----------

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Müzik çubuğu", NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle("Müzik çubuğu açık")
                .setContentText("Sürüş Ekranı'na dönmek için dokun")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ---------- Görünüm ----------

    private void setCollapsed(boolean min) {
        setCollapsedInternal(min);
        sp.edit().putBoolean("fMin", min).apply();
        try {
            wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
    }

    private void setCollapsedInternal(boolean min) {
        bar.setVisibility(min ? View.GONE : View.VISIBLE);
        bubble.setVisibility(min ? View.VISIBLE : View.GONE);
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void dragWith(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX();
                        sy = e.getRawY();
                        ox = lp.x;
                        oy = lp.y;
                        moved = false;
                        v.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (!moved && Math.hypot(dx, dy) > dp(8)) {
                            moved = true;
                            v.setPressed(false);
                        }
                        if (moved) {
                            lp.x = ox + Math.round(dx);
                            lp.y = oy + Math.round(dy);
                            wm.updateViewLayout(root, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (moved) sp.edit().putInt("fX", lp.x).putInt("fY", lp.y).apply();
                        else v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        return true;
                }
                return false;
            }
        });
    }

    private void renderWeather() {
        if (System.currentTimeMillis() - sp.getLong("wAt", 0) < 3 * 3600 * 1000L) {
            weather.setText(Status.icon(sp.getInt("wC", 0), sp.getBoolean("wD", true))
                    + " " + sp.getInt("wT", 0) + "°");
            weather.setVisibility(View.VISIBLE);
        } else {
            weather.setVisibility(View.GONE);
        }
    }

    // ---------- Müzik ----------

    private void pickController() {
        List<MediaController> list = Collections.emptyList();
        try {
            list = msm.getActiveSessions(listenerCn);
        } catch (SecurityException ignored) {
        }
        String want = Apps.musicPkg(new Prefs(this).music());
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
        if (ctrl != null) ctrl.unregisterCallback(mediaCb);
        ctrl = best;
        if (ctrl != null) ctrl.registerCallback(mediaCb, h);
        render();
    }

    private void render() {
        MediaMetadata m = ctrl == null ? null : ctrl.getMetadata();
        boolean playing;
        if (m == null) {
            title.setText("Müzik");
            artist.setText(Apps.musicName(new Prefs(this).music()));
            AudioManager am = getSystemService(AudioManager.class);
            playing = am != null && am.isMusicActive();
        } else {
            String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            title.setText(t == null ? "" : t);
            artist.setText(a == null ? "" : a);
            PlaybackState s = ctrl.getPlaybackState();
            playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        }
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void togglePlay() {
        if (ctrl == null) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return;
        }
        PlaybackState s = ctrl.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) ctrl.getTransportControls().pause();
        else ctrl.getTransportControls().play();
    }

    private void mediaKey(int code) {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        h.postDelayed(this::render, 600);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, Math.max(lo, hi)));
    }
}
