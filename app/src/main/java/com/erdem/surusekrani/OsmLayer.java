package com.erdem.surusekrani;

import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.view.View;

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
import java.util.ArrayList;
import java.util.List;

/** OpenStreetMap haritası (Google anahtarı yoksa ya da Google Play hizmetleri yoksa). */
final class OsmLayer implements MapLayer {
    private final Activity a;
    private final MapView map;
    private final MyLocationNewOverlay myLoc;
    private final List<Polyline> lines = new ArrayList<>();
    private Marker destMarker;
    private Runnable onGesture;

    OsmLayer(Activity a) {
        this.a = a;
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(a, a.getSharedPreferences("osm", Context.MODE_PRIVATE));
        cfg.setUserAgentValue(a.getPackageName());
        File base = new File(a.getCacheDir(), "osm");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));

        map = new MapView(a);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getMapOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        map.setOnTouchListener((v, e) -> {
            if (onGesture != null) onGesture.run();
            return false;
        });

        GpsMyLocationProvider provider = new GpsMyLocationProvider(a);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLoc = new MyLocationNewOverlay(provider, map);
        myLoc.enableFollowLocation();
        map.getOverlays().add(myLoc);
    }

    @Override public View view() { return map; }
    @Override public void onResume() { map.onResume(); }
    @Override public void onPause() { map.onPause(); }
    @Override public void onDestroy() { map.onDetach(); }
    @Override public void setOnGesture(Runnable r) { onGesture = r; }

    @Override public void show(GeoPoint p, double zoom) {
        map.getController().setZoom(zoom);
        map.getController().setCenter(p);
    }

    @Override public void animateTo(GeoPoint p) { map.getController().animateTo(p); }

    @Override public void follow(boolean on) {
        if (on) myLoc.enableFollowLocation();
        else myLoc.disableFollowLocation();
    }

    @Override public void myLocation(boolean on) {
        if (on) myLoc.enableMyLocation();
        else myLoc.disableMyLocation();
    }

    @Override public GeoPoint myLocationPoint() { return myLoc.getMyLocation(); }
    @Override public void onLocation(Location l) { }

    @Override public void navMode(boolean on) {
        if (on) map.getController().setZoom(17.5);
        else map.setMapOrientation(0);
    }

    @Override public void drive(GeoPoint p, float bearing) {
        map.getController().animateTo(p);
        if (!Float.isNaN(bearing)) map.setMapOrientation(-bearing);
    }

    @Override public void showRoute(Route r, GeoPoint dest) {
        clearRoute();
        int idx = Math.max(0, map.getOverlays().indexOf(myLoc));
        for (int[] s : r.spans()) {
            Polyline line = new Polyline();
            line.setPoints(new ArrayList<>(r.points.subList(s[0], s[1] + 1)));
            Paint paint = line.getOutlinePaint();
            paint.setColor(color(s[2]));
            paint.setStrokeWidth(dp(8));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            map.getOverlays().add(idx++, line);
            lines.add(line);
        }
        destMarker = new Marker(map);
        destMarker.setPosition(dest);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destMarker.setInfoWindow(null);
        map.getOverlays().add(destMarker);
        map.invalidate();
    }

    private int color(int level) {
        if (level == 2) return a.getColor(R.color.low);
        if (level == 1) return a.getColor(R.color.chg_low);
        if (level == 0) return a.getColor(R.color.rain);
        return a.getColor(R.color.accent);
    }

    @Override public void clearRoute() {
        map.getOverlays().removeAll(lines);
        lines.clear();
        if (destMarker != null) map.getOverlays().remove(destMarker);
        destMarker = null;
        map.invalidate();
    }

    @Override public void fitRoute(List<GeoPoint> pts) {
        map.post(() -> {
            try {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.3f), true);
            } catch (Exception ignored) {
            }
        });
    }

    @Override public void setPadding(int top, int bottom) { }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
