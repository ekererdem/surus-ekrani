package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Haritayı kaydırarak ev konumunu işaretleme ekranı. */
public class MapPickActivity extends Activity {
    private MapView map;
    private MyLocationNewOverlay myLoc;
    private SharedPreferences sp;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(this, getSharedPreferences("osm", MODE_PRIVATE));
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osm");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
        setContentView(R.layout.activity_map_pick);
        sp = getSharedPreferences("surus", MODE_PRIVATE);

        map = findViewById(R.id.pickMap);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getMapOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);

        GpsMyLocationProvider provider = new GpsMyLocationProvider(this);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLoc = new MyLocationNewOverlay(provider, map);
        map.getOverlays().add(myLoc);

        // Başlangıç: kayıtlı ev > mevcut konum > Ankara
        GeoPoint start = null;
        double zoom = 17.5;
        String coord = sp.getString("homeCoord", "");
        if (!coord.isEmpty()) {
            try {
                String[] p = coord.split(",");
                start = new GeoPoint(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
            } catch (Exception ignored) {
            }
        }
        if (start == null) {
            Location l = lastLocation();
            if (l != null) {
                start = new GeoPoint(l.getLatitude(), l.getLongitude());
                zoom = 16.0;
            } else {
                start = new GeoPoint(39.92, 32.85);
                zoom = 12.0;
            }
        }
        map.getController().setZoom(zoom);
        map.getController().setCenter(start);

        findViewById(R.id.pickBack).setOnClickListener(v -> finish());
        findViewById(R.id.pickLocate).setOnClickListener(v -> {
            Location l = lastLocation();
            if (l == null) {
                Toast.makeText(this, "Konum bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
            map.getController().animateTo(new GeoPoint(l.getLatitude(), l.getLongitude()), 17.5, 600L);
        });
        findViewById(R.id.pickSave).setOnClickListener(v -> save());
    }

    private Location lastLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        try {
            Location g = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location n = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (g == null || (n != null && n.getTime() > g.getTime() + 60000)) return n;
            return g;
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private void save() {
        IGeoPoint c = map.getMapCenter();
        double lat = c.getLatitude(), lon = c.getLongitude();
        findViewById(R.id.pickSave).setEnabled(false);
        io.execute(() -> {
            String label = null;
            try {
                List<Address> r = new Geocoder(this, new Locale("tr", "TR")).getFromLocation(lat, lon, 1);
                if (r != null && !r.isEmpty() && r.get(0).getAddressLine(0) != null) {
                    label = r.get(0).getAddressLine(0);
                }
            } catch (Exception ignored) {
            }
            String finalLabel = label != null ? label : "Ev (haritada işaretlendi)";
            sp.edit()
                    .putString("home", finalLabel)
                    .putString("homeCoord", String.format(Locale.US, "%.6f,%.6f", lat, lon))
                    .apply();
            runOnUiThread(() -> {
                Toast.makeText(this, "Ev kaydedildi", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        myLoc.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        myLoc.disableMyLocation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
