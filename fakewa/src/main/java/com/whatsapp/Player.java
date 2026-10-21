package com.whatsapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;

/**
 * Test: Spotify gibi çalan bir müzik oturumu (kapak, "• Akıllı Mix'ten", beğen / radyo / kaldır tuşları).
 * Bildiriminde ayrıca yalnız orada duran "Beğenmedim" tuşu var (Spotify'ın akıllı mix şarkıları gibi).
 * Dokunulan tuşlar logcat'e "FakeWA" etiketiyle yazılır.
 */
public class Player extends Activity {
    static MediaSession session;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (session == null) {
            session = new MediaSession(this, "test");
            Bitmap art = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 300, 300, 0xFFE0563B, 0xFF2B2D6E, Shader.TileMode.CLAMP));
            new Canvas(art).drawRect(0, 0, 300, 300, p);
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Yalnızlar Treni")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Tarkan, Nazan Öncel • Akıllı Mix'ten")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 248000)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .build());
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 61000, 1f)
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                            | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .addCustomAction("like", "Beğenilenlere ekle", android.R.drawable.btn_star_big_off)
                    .addCustomAction("radio", "Radyoya git", android.R.drawable.ic_menu_rotate)
                    .addCustomAction("hide", "Öneriyi kaldır", android.R.drawable.ic_menu_close_clear_cancel)
                    .build());
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onCustomAction(String action, Bundle extras) {
                    android.util.Log.i("FakeWA", "ozel tus: " + action);
                }
            });
            session.setActive(true);
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel("music", "Müzik",
                    android.app.NotificationManager.IMPORTANCE_LOW));
            android.app.PendingIntent dislike = android.app.PendingIntent.getBroadcast(this, 7,
                    new android.content.Intent(this, Tap.class).setAction("dislike"),
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.PendingIntent none = android.app.PendingIntent.getBroadcast(this, 8,
                    new android.content.Intent(this, Tap.class).setAction("transport"),
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification n = new android.app.Notification.Builder(this, "music")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Yalnızlar Treni")
                    .setContentText("Tarkan, Nazan Öncel")
                    .addAction(new android.app.Notification.Action.Builder(null, "Beğenilenlere ekle", none).build())
                    .addAction(new android.app.Notification.Action.Builder(null, "Önceki", none).build())
                    .addAction(new android.app.Notification.Action.Builder(null, "Duraklat", none).build())
                    .addAction(new android.app.Notification.Action.Builder(null, "Sonraki", none).build())
                    .addAction(new android.app.Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_delete),
                            "Beğenmedim", dislike).build())
                    .setStyle(new android.app.Notification.MediaStyle().setMediaSession(session.getSessionToken())
                            .setShowActionsInCompactView(1, 2, 3))
                    .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
                    .setOngoing(true)
                    .build();
            nm.notify(77, n);
        }
        moveTaskToBack(true);
    }
}
