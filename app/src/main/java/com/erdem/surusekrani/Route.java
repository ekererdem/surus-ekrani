package com.erdem.surusekrani;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adres arama (OpenStreetMap Nominatim), rota (OSRM) ve mesafe hesapları. */
final class Route {
    static final Locale TR = new Locale("tr", "TR");

    static final class Place {
        final String name;
        final GeoPoint point;
        /** Listede adın altında gösterilen kısa adres (yoksa null). */
        final String addr;
        Place(String name, GeoPoint point) { this(name, point, null); }
        Place(String name, GeoPoint point, String addr) { this.name = name; this.point = point; this.addr = addr; }
    }

    static final class Step {
        GeoPoint at;
        String type = "", modifier = "", name = "";
        /** Google'ın hazır yön cümlesi (OSRM'de boş). */
        String text = "";
        int exit;
        double distance;
    }

    final List<GeoPoint> points = new ArrayList<>();
    final List<Step> steps = new ArrayList<>();
    /** Google trafik bilgisi: {ilk nokta, son nokta, seviye 0 akıcı / 1 yavaş / 2 sıkışık}. */
    final List<int[]> traffic = new ArrayList<>();
    double distance, duration;

    /** Çizgiyi trafik durumuna göre parçalara böler: {ilk, son, seviye}; bilgi olmayan yerde seviye -1. */
    List<int[]> spans() {
        List<int[]> out = new ArrayList<>();
        int n = points.size();
        if (n < 2) return out;
        int at = 0;
        for (int[] t : traffic) {
            int s = Math.max(t[0], at), e = Math.min(t[1], n - 1);
            if (e <= s) continue;
            if (s > at) out.add(new int[]{at, s, -1});
            out.add(new int[]{s, e, t[2]});
            at = e;
        }
        if (at < n - 1) out.add(new int[]{at, n - 1, -1});
        return out;
    }

    // ---------- Ağ ----------

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "SurusEkrani/1.0 (kisisel kullanim)");
        c.setRequestProperty("Accept-Language", "tr,en");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    /** Adres ara. near null değilse sonuçlar o çevreye öncelik verir. */
    static List<Place> search(String query, GeoPoint near) throws Exception {
        StringBuilder u = new StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=")
                .append(Uri.encode(query));
        if (near != null) {
            double la = near.getLatitude(), lo = near.getLongitude();
            u.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f", lo - 0.6, la + 0.6, lo + 0.6, la - 0.6));
        }
        JSONArray arr = new JSONArray(get(u.toString()));
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Place(o.optString("display_name"),
                    new GeoPoint(o.getDouble("lat"), o.getDouble("lon"))));
        }
        return out;
    }

    static Route fetch(GeoPoint from, GeoPoint to) throws Exception {
        String url = String.format(Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude());
        JSONObject root = new JSONObject(get(url));
        if (!"Ok".equals(root.optString("code"))) throw new IOException("Rota bulunamadı");
        JSONObject r = root.getJSONArray("routes").getJSONObject(0);

        Route route = new Route();
        route.distance = r.getDouble("distance");
        route.duration = r.getDouble("duration");
        JSONArray coords = r.getJSONObject("geometry").getJSONArray("coordinates");
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            route.points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        JSONArray legs = r.getJSONArray("legs");
        for (int l = 0; l < legs.length(); l++) {
            JSONArray st = legs.getJSONObject(l).getJSONArray("steps");
            for (int i = 0; i < st.length(); i++) {
                JSONObject s = st.getJSONObject(i);
                JSONObject m = s.getJSONObject("maneuver");
                JSONArray loc = m.getJSONArray("location");
                Step step = new Step();
                step.at = new GeoPoint(loc.getDouble(1), loc.getDouble(0));
                step.type = m.optString("type");
                step.modifier = m.optString("modifier");
                step.exit = m.optInt("exit", 0);
                step.name = s.optString("name");
                step.distance = s.optDouble("distance", 0);
                route.steps.add(step);
            }
        }
        return route;
    }

    // ---------- Geometri ----------

    static double meters(GeoPoint a, GeoPoint b) {
        double r = 6371000;
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Konumun rota çizgisine en kısa uzaklığı (metre). */
    double distanceToLine(GeoPoint p) {
        double kx = Math.cos(Math.toRadians(p.getLatitude())) * 111320.0, ky = 110540.0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i++) {
            GeoPoint a = points.get(i), b = points.get(i + 1);
            double ax = (a.getLongitude() - p.getLongitude()) * kx, ay = (a.getLatitude() - p.getLatitude()) * ky;
            double bx = (b.getLongitude() - p.getLongitude()) * kx, by = (b.getLatitude() - p.getLatitude()) * ky;
            double dx = bx - ax, dy = by - ay;
            double len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len));
            double x = ax + t * dx, y = ay + t * dy;
            best = Math.min(best, Math.sqrt(x * x + y * y));
        }
        return best;
    }

    // ---------- Metinler ----------

    static String instruction(Step s) {
        String m = s.modifier == null ? "" : s.modifier;
        String side = m.contains("right") ? "sağdan " : m.contains("left") ? "soldan " : "";
        switch (s.type) {
            case "arrive": return "Varış noktası";
            case "depart": return "Yola çıkın";
            case "roundabout":
            case "rotary":
            case "roundabout turn":
                return s.exit > 0 ? "Göbekte " + s.exit + ". çıkıştan çıkın" : "Göbeğe girin";
            case "on ramp": return "Bağlantı yoluna " + side + "girin";
            case "off ramp": return "Çıkışa " + side + "girin";
            case "fork": return "Ayrımda " + side + "devam edin";
            case "merge": return "Yola katılın";
            default: return turn(m);
        }
    }

    private static String turn(String m) {
        switch (m) {
            case "right": return "Sağa dönün";
            case "left": return "Sola dönün";
            case "slight right": return "Hafif sağa dönün";
            case "slight left": return "Hafif sola dönün";
            case "sharp right": return "Keskin sağa dönün";
            case "sharp left": return "Keskin sola dönün";
            case "uturn": return "U dönüşü yapın";
            default: return "Düz devam edin";
        }
    }

    /** Ok simgesinin döndürme açısı. */
    static float arrowAngle(Step s) {
        switch (s.modifier == null ? "" : s.modifier) {
            case "slight right": return 45;
            case "right": return 90;
            case "sharp right": return 135;
            case "uturn": return 180;
            case "slight left": return -45;
            case "left": return -90;
            case "sharp left": return -135;
            default: return 0;
        }
    }

    /** Ekranda gösterilecek mesafe. */
    static String shortDist(double m, boolean miles) {
        if (miles) {
            double ft = m * 3.28084;
            if (ft < 1000) return Math.max(50, Math.round(ft / 50) * 50) + " ft";
            double mi = m / 1609.344;
            return String.format(TR, mi < 10 ? "%.1f mi" : "%.0f mi", mi);
        }
        if (m < 1000) return Math.max(10, Math.round(m / 10) * 10) + " m";
        double km = m / 1000;
        return String.format(TR, km < 10 ? "%.1f km" : "%.0f km", km);
    }

    /** Sesli okunacak mesafe. */
    static String spokenDist(double m, boolean miles) {
        if (miles) {
            double ft = m * 3.28084;
            if (ft < 1000) return Math.max(100, Math.round(ft / 100) * 100) + " fit";
            double mi = m / 1609.344;
            return (mi < 10 ? String.format(TR, "%.1f", mi) : String.valueOf(Math.round(mi))) + " mil";
        }
        if (m < 1000) return Math.max(50, Math.round(m / 50) * 50) + " metre";
        double km = m / 1000;
        return (km < 10 ? String.format(TR, "%.1f", km) : String.valueOf(Math.round(km))) + " kilometre";
    }
}
