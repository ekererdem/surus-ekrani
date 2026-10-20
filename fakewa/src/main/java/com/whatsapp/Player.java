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

/** Test: Spotify gibi çalan bir müzik oturumu (kapak, "• Akıllı Mix'ten", beğen / radyo / kaldır tuşları). */
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
            session.setActive(true);
        }
        moveTaskToBack(true);
    }
}
