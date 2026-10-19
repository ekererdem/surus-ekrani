package com.erdem.surusekrani;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Google: harita anahtarı, yer arama (Places) ve trafiğe göre rota (Routes). */
final class Gapi {
    private Gapi() {}

    private static final String TAG = "Kopilot";
    private static final Pattern EXIT = Pattern.compile("(\\d+)\\.\\s*çıkış");

    private static String key, pkg, cert;
    private static Boolean hasKey, hasMap;

    /** APK'ya Google anahtarı konmuş mu. */
    static synchronized boolean ok(Context c) {
        if (hasKey == null) {
            try {
                Context app = c.getApplicationContext();
                pkg = app.getPackageName();
                ApplicationInfo ai = app.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_META_DATA);
                Object v = ai.metaData == null ? null : ai.metaData.get("com.google.android.geo.API_KEY");
                key = v == null ? null : v.toString().trim();
            } catch (Exception e) {
                key = null;
            }
            hasKey = key != null && key.startsWith("AIza");
        }
        return hasKey;
    }

    /** Google haritası gösterilebilir mi (anahtar + Google Play hizmetleri). */
    static synchronized boolean map(Context c) {
        if (hasMap == null) {
            boolean play;
            try {
                play = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS;
            } catch (Exception e) {
                play = false;
            }
            hasMap = ok(c) && play;
        }
        return hasMap;
    }

    /** Android'e kısıtlı anahtar için imza parmak izi (SHA-1, iki nokta olmadan). */
    private static String cert(Context c) {
        if (cert != null) return cert;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] s = pi.signingInfo.getApkContentsSigners();
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02X", b));
            cert = sb.toString();
        } catch (Exception e) {
            cert = "";
        }
        return cert;
    }

    private static String post(Context c, String url, String fields, JSONObject body) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setConnectTimeout(10000);
        h.setReadTimeout(15000);
        h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        h.setRequestProperty("X-Goog-Api-Key", key);
        h.setRequestProperty("X-Goog-FieldMask", fields);
        h.setRequestProperty("X-Android-Package", pkg);
        h.setRequestProperty("X-Android-Cert", cert(c));
        try {
            try (OutputStream o = h.getOutputStream()) {
                o.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = h.getResponseCode();
            InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
            String text = "";
            if (in != null) {
                try (InputStream i = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
                    text = out.toString(StandardCharsets.UTF_8.name());
                }
            }
            if (code >= 400) {
                Log.w(TAG, "Google " + url + " → " + code + " " + text);
                throw new IOException("HTTP " + code);
            }
            return text;
        } finally {
            h.disconnect();
        }
    }

    private static JSONObject latLng(GeoPoint p) throws Exception {
        return new JSONObject().put("latitude", p.getLatitude()).put("longitude", p.getLongitude());
    }

    private static GeoPoint point(JSONObject o) {
        if (o == null) return null;
        JSONObject l = o.has("latLng") ? o.optJSONObject("latLng") : o;
        if (l == null) return null;
        return new GeoPoint(l.optDouble("latitude"), l.optDouble("longitude"));
    }

    // ---------- Yer arama ----------

    static List<Route.Place> search(Context c, String q, GeoPoint near) throws Exception {
        JSONObject body = new JSONObject()
                .put("textQuery", q)
                .put("languageCode", "tr")
                .put("regionCode", "TR")
                .put("pageSize", 6);
        if (near != null) {
            body.put("locationBias", new JSONObject().put("circle", new JSONObject()
                    .put("center", latLng(near)).put("radius", 50000.0)));
        }
        JSONObject r = new JSONObject(post(c, "https://places.googleapis.com/v1/places:searchText",
                "places.displayName,places.formattedAddress,places.location", body));
        JSONArray arr = r.optJSONArray("places");
        List<Route.Place> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            GeoPoint p = point(o.optJSONObject("location"));
            if (p == null) continue;
            JSONObject dn = o.optJSONObject("displayName");
            String name = dn == null ? "" : dn.optString("text");
            String addr = shortAddr(o.optString("formattedAddress"));
            if (name.isEmpty()) name = addr;
            out.add(new Route.Place(name, p, addr.equals(name) ? null : addr));
        }
        return out;
    }

    /** "…, 06420 Çankaya/Ankara, Türkiye" → "…, Çankaya/Ankara" */
    private static String shortAddr(String a) {
        if (a == null) return "";
        String s = a.replaceAll(",\\s*(Türkiye|Turkey)$", "").replaceAll("\\b\\d{5}\\s+", "");
        return s.trim();
    }

    // ---------- Rota ----------

    static Route route(Context c, GeoPoint from, GeoPoint to, Location heading) throws Exception {
        JSONObject origin = new JSONObject().put("latLng", latLng(from));
        if (heading != null && heading.hasBearing() && heading.getSpeed() > 3) {
            origin.put("heading", Math.round(heading.getBearing()) % 360);
        }
        JSONObject body = new JSONObject()
                .put("origin", new JSONObject().put("location", origin))
                .put("destination", new JSONObject().put("location", new JSONObject().put("latLng", latLng(to))))
                .put("travelMode", "DRIVE")
                .put("routingPreference", "TRAFFIC_AWARE")
                .put("polylineQuality", "HIGH_QUALITY")
                .put("extraComputations", new JSONArray().put("TRAFFIC_ON_POLYLINE"))
                .put("languageCode", "tr");
        JSONObject root = new JSONObject(post(c, "https://routes.googleapis.com/directions/v2:computeRoutes",
                "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline,"
                        + "routes.legs.steps.distanceMeters,routes.legs.steps.startLocation,"
                        + "routes.legs.steps.endLocation,routes.legs.steps.navigationInstruction,"
                        + "routes.travelAdvisory.speedReadingIntervals", body));
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) throw new IOException("Rota bulunamadı");
        JSONObject r = routes.getJSONObject(0);

        Route route = new Route();
        route.distance = r.optDouble("distanceMeters", 0);
        route.duration = seconds(r.optString("duration"));
        route.points.addAll(decode(r.getJSONObject("polyline").getString("encodedPolyline")));

        JSONObject adv = r.optJSONObject("travelAdvisory");
        JSONArray sp = adv == null ? null : adv.optJSONArray("speedReadingIntervals");
        if (sp != null) {
            for (int i = 0; i < sp.length(); i++) {
                JSONObject s = sp.getJSONObject(i);
                String speed = s.optString("speed");
                int lv = "TRAFFIC_JAM".equals(speed) ? 2 : "SLOW".equals(speed) ? 1 : 0;
                route.traffic.add(new int[]{s.optInt("startPolylinePointIndex", 0),
                        s.optInt("endPolylinePointIndex", 0), lv});
            }
        }

        GeoPoint end = null;
        JSONArray legs = r.optJSONArray("legs");
        for (int l = 0; legs != null && l < legs.length(); l++) {
            JSONArray st = legs.getJSONObject(l).optJSONArray("steps");
            for (int i = 0; st != null && i < st.length(); i++) {
                JSONObject s = st.getJSONObject(i);
                Route.Step step = new Route.Step();
                step.at = point(s.optJSONObject("startLocation"));
                if (step.at == null) continue;
                step.distance = s.optDouble("distanceMeters", 0);
                JSONObject ni = s.optJSONObject("navigationInstruction");
                String text = ni == null ? "" : ni.optString("instructions");
                int nl = text.indexOf('\n');
                step.text = (nl > 0 ? text.substring(0, nl) : text).trim();
                maneuver(step, ni == null ? "" : ni.optString("maneuver"));
                if (route.steps.isEmpty()) step.type = "depart";
                route.steps.add(step);
                GeoPoint e = point(s.optJSONObject("endLocation"));
                if (e != null) end = e;
            }
        }
        if (end == null && !route.points.isEmpty()) end = route.points.get(route.points.size() - 1);
        if (route.steps.isEmpty() || end == null) throw new IOException("Rota adımları yok");
        Route.Step arrive = new Route.Step();
        arrive.at = end;
        arrive.type = "arrive";
        route.steps.add(arrive);
        return route;
    }

    /** Google manevrasını OSRM'deki tür/yön karşılığına çevir (ok ve sesli okuma ortak kalsın). */
    private static void maneuver(Route.Step s, String m) {
        switch (m) {
            case "DEPART": s.type = "depart"; break;
            case "TURN_LEFT": s.type = "turn"; s.modifier = "left"; break;
            case "TURN_RIGHT": s.type = "turn"; s.modifier = "right"; break;
            case "TURN_SLIGHT_LEFT": s.type = "turn"; s.modifier = "slight left"; break;
            case "TURN_SLIGHT_RIGHT": s.type = "turn"; s.modifier = "slight right"; break;
            case "TURN_SHARP_LEFT": s.type = "turn"; s.modifier = "sharp left"; break;
            case "TURN_SHARP_RIGHT": s.type = "turn"; s.modifier = "sharp right"; break;
            case "UTURN_LEFT":
            case "UTURN_RIGHT": s.type = "turn"; s.modifier = "uturn"; break;
            case "RAMP_LEFT": s.type = "on ramp"; s.modifier = "slight left"; break;
            case "RAMP_RIGHT": s.type = "on ramp"; s.modifier = "slight right"; break;
            case "FORK_LEFT": s.type = "fork"; s.modifier = "slight left"; break;
            case "FORK_RIGHT": s.type = "fork"; s.modifier = "slight right"; break;
            case "MERGE": s.type = "merge"; s.modifier = "straight"; break;
            case "ROUNDABOUT_LEFT":
            case "ROUNDABOUT_RIGHT": {
                s.type = "roundabout";
                Matcher x = EXIT.matcher(s.text == null ? "" : s.text);
                if (x.find()) s.exit = Integer.parseInt(x.group(1));
                break;
            }
            default: s.type = "continue"; s.modifier = "straight"; break;
        }
    }

    private static double seconds(String d) {
        if (d == null || d.isEmpty()) return 0;
        try {
            return Double.parseDouble(d.endsWith("s") ? d.substring(0, d.length() - 1) : d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Google'ın kodlanmış çizgisini noktalara çevir. */
    static List<GeoPoint> decode(String enc) {
        List<GeoPoint> out = new ArrayList<>();
        int i = 0, lat = 0, lng = 0, n = enc.length();
        while (i < n) {
            int[] v = new int[2];
            for (int k = 0; k < 2; k++) {
                int shift = 0, result = 0, b;
                do {
                    b = enc.charAt(i++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20 && i < n);
                v[k] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }
            lat += v[0];
            lng += v[1];
            out.add(new GeoPoint(lat / 1e5, lng / 1e5));
        }
        return out;
    }
}
