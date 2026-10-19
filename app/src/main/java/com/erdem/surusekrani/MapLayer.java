package com.erdem.surusekrani;

import android.location.Location;
import android.view.View;

import org.osmdroid.util.GeoPoint;

import java.util.List;

/** NavPanel'in kullandığı harita: Google haritası (anahtar varsa) ya da OpenStreetMap. */
interface MapLayer {
    View view();

    void onResume();
    void onPause();
    void onDestroy();

    /** Kullanıcı haritayı elle kaydırınca çağrılır. */
    void setOnGesture(Runnable r);

    /** Haritayı bir noktaya ve yakınlığa taşı (animasyonsuz). */
    void show(GeoPoint p, double zoom);
    void animateTo(GeoPoint p);

    /** Gezinirken konumu ortada tut. */
    void follow(boolean on);
    /** Mavi konum noktası. */
    void myLocation(boolean on);
    GeoPoint myLocationPoint();
    void onLocation(Location l);

    /** Sürüş görünümü: yakın, araç yönüne dönük. */
    void navMode(boolean on);
    /** Navigasyonda kamerayı araçla götür. bearing NaN ise yön değişmez. */
    void drive(GeoPoint p, float bearing);

    void showRoute(Route r, GeoPoint dest);
    void clearRoute();
    void fitRoute(List<GeoPoint> pts);

    /** Üstteki kart ve alttaki özetin kapladığı yer (harita merkezi ve logo bunun dışında kalır). */
    void setPadding(int top, int bottom);
}
