package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.UiSettings;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Google haritası: koyu tema, canlı trafik. */
final class GLayer implements MapLayer {
    private static final float NAV_ZOOM = 17.5f, NAV_TILT = 50f;

    private final Activity a;
    private final MapView mv;
    private GoogleMap gm;
    private final List<Consumer<GoogleMap>> pending = new ArrayList<>();
    private final List<Polyline> lines = new ArrayList<>();
    private Marker destMarker;
    private Runnable onGesture;
    private boolean follow = true, nav, showMe;
    private Location lastLoc;
    private int padTop, padBottom;

    GLayer(Activity a) {
        this.a = a;
        mv = new MapView(a, new GoogleMapOptions()
                .compassEnabled(false)
                .mapToolbarEnabled(false)
                .zoomControlsEnabled(false)
                .camera(new CameraPosition(new LatLng(39.92, 32.85), 15f, 0f, 0f)));
        mv.onCreate(null);
        mv.onStart();
        mv.getMapAsync(this::ready);
    }

    private void ready(GoogleMap g) {
        gm = g;
        try {
            g.setMapStyle(MapStyleOptions.loadRawResourceStyle(a, R.raw.map_night));
        } catch (Exception ignored) {
        }
        g.setTrafficEnabled(true);
        g.setBuildingsEnabled(true);
        g.setIndoorEnabled(false);
        UiSettings u = g.getUiSettings();
        u.setMyLocationButtonEnabled(false);
        u.setCompassEnabled(false);
        u.setMapToolbarEnabled(false);
        u.setZoomControlsEnabled(false);
        u.setIndoorLevelPickerEnabled(false);
        g.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                follow = false;
                if (onGesture != null) onGesture.run();
            }
        });
        g.setPadding(0, padTop, 0, padBottom);
        applyMyLocation();
        for (Consumer<GoogleMap> c : pending) c.accept(g);
        pending.clear();
    }

    private void run(Consumer<GoogleMap> c) {
        if (gm != null) c.accept(gm);
        else pending.add(c);
    }

    private static LatLng ll(GeoPoint p) { return new LatLng(p.getLatitude(), p.getLongitude()); }

    @Override public View view() { return mv; }
    @Override public void onResume() { mv.onResume(); }
    @Override public void onPause() { mv.onPause(); }

    @Override public void onDestroy() {
        try {
            mv.onStop();
            mv.onDestroy();
        } catch (Exception ignored) {
        }
    }

    @Override public void setOnGesture(Runnable r) { onGesture = r; }

    @Override public void show(GeoPoint p, double zoom) {
        run(g -> g.moveCamera(CameraUpdateFactory.newLatLngZoom(ll(p), (float) zoom)));
    }

    @Override public void animateTo(GeoPoint p) {
        run(g -> g.animateCamera(CameraUpdateFactory.newLatLng(ll(p))));
    }

    @Override public void follow(boolean on) {
        follow = on;
        if (on && lastLoc != null && !nav) {
            run(g -> g.animateCamera(CameraUpdateFactory.newLatLng(
                    new LatLng(lastLoc.getLatitude(), lastLoc.getLongitude()))));
        }
    }

    @Override public void myLocation(boolean on) {
        showMe = on;
        applyMyLocation();
    }

    private void applyMyLocation() {
        if (gm == null) return;
        boolean perm = a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        try {
            gm.setMyLocationEnabled(showMe && perm);
        } catch (SecurityException ignored) {
        }
    }

    @Override public GeoPoint myLocationPoint() {
        return lastLoc == null ? null : new GeoPoint(lastLoc.getLatitude(), lastLoc.getLongitude());
    }

    @Override public void onLocation(Location l) {
        boolean first = lastLoc == null;
        lastLoc = l;
        if (follow && !nav) {
            LatLng t = new LatLng(l.getLatitude(), l.getLongitude());
            run(g -> {
                if (first) g.moveCamera(CameraUpdateFactory.newLatLng(t));
                else g.animateCamera(CameraUpdateFactory.newLatLng(t));
            });
        }
    }

    @Override public void navMode(boolean on) {
        nav = on;
        run(g -> {
            CameraPosition c = g.getCameraPosition();
            CameraPosition.Builder b = CameraPosition.builder(c);
            if (on) b.zoom(NAV_ZOOM).tilt(NAV_TILT);
            else b.zoom(Math.min(c.zoom, 16f)).tilt(0).bearing(0);
            if (on && lastLoc != null) b.target(new LatLng(lastLoc.getLatitude(), lastLoc.getLongitude()));
            g.moveCamera(CameraUpdateFactory.newCameraPosition(b.build()));
        });
    }

    @Override public void drive(GeoPoint p, float bearing) {
        run(g -> {
            CameraPosition c = g.getCameraPosition();
            CameraPosition.Builder b = CameraPosition.builder(c).target(ll(p));
            if (!Float.isNaN(bearing)) b.bearing(bearing);
            g.animateCamera(CameraUpdateFactory.newCameraPosition(b.build()), 900, null);
        });
    }

    @Override public void showRoute(Route r, GeoPoint dest) {
        List<LatLng> all = new ArrayList<>(r.points.size());
        for (GeoPoint p : r.points) all.add(ll(p));
        List<int[]> spans = r.spans();
        run(g -> {
            removeRoute();
            // Koyu kenar, üstüne trafik rengine göre parçalar
            lines.add(g.addPolyline(new PolylineOptions().addAll(all)
                    .color(0xFF0B1730).width(dp(11)).zIndex(1)
                    .startCap(new RoundCap()).endCap(new RoundCap()).jointType(JointType.ROUND)));
            for (int[] s : spans) {
                lines.add(g.addPolyline(new PolylineOptions().addAll(all.subList(s[0], s[1] + 1))
                        .color(color(s[2])).width(dp(7)).zIndex(2)
                        .startCap(new RoundCap()).endCap(new RoundCap()).jointType(JointType.ROUND)));
            }
            destMarker = g.addMarker(new MarkerOptions().position(ll(dest)).zIndex(3));
        });
    }

    private int color(int level) {
        if (level == 2) return a.getColor(R.color.low);
        if (level == 1) return a.getColor(R.color.chg_low);
        return a.getColor(R.color.rain);
    }

    private void removeRoute() {
        for (Polyline l : lines) l.remove();
        lines.clear();
        if (destMarker != null) destMarker.remove();
        destMarker = null;
    }

    @Override public void clearRoute() { run(g -> removeRoute()); }

    @Override public void fitRoute(List<GeoPoint> pts) {
        if (pts.isEmpty()) return;
        LatLngBounds.Builder bb = LatLngBounds.builder();
        for (GeoPoint p : pts) bb.include(ll(p));
        LatLngBounds bounds = bb.build();
        mv.post(() -> run(g -> {
            try {
                g.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(40)));
            } catch (Exception ignored) {
            }
        }));
    }

    @Override public void setPadding(int top, int bottom) {
        if (top == padTop && bottom == padBottom) return;
        padTop = top;
        padBottom = bottom;
        if (gm != null) gm.setPadding(0, top, 0, bottom);
    }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
