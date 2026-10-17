package com.erdem.surusekrani;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Renkli hava simgesi, 48x48 birimlik bir alana kodla çizilir (resim dosyası yok).
 * Kodlar Open-Meteo / WMO. Sisin yoğunluğu görüş mesafesine göre 1–4 çizgi.
 */
final class WeatherIcon extends Drawable {
    private static final int SUN = 0xFFF2A900, MOON = 0xFFE8D58A, CLOUD = 0xFFC9D3DA, DARK = 0xFF7F8E99,
            RAIN = 0xFF4FA3FF, SNOW = 0xFFFFFFFF, ICE = 0xFF9FE3FF, BOLT = 0xFFF2A900, FOG = 0xFF9AA8B3,
            HAIL = 0xFFDDEBF5, SHOWER = 0xFFA78BFA;

    private final int code, fogLines;
    private final boolean day, fogMode;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Canvas cv;

    /** visibility: metre, bilinmiyorsa -1. */
    WeatherIcon(int code, boolean day, int visibility) {
        this.code = code;
        this.day = day;
        this.fogLines = fogLines(visibility);
        this.fogMode = isFog(code, visibility);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Görüş: 1 km üstü 1 çizgi, 1 km altı 2, 500 m altı 3, 200 m altı 4. Bilinmiyorsa 2. */
    static int fogLines(int vis) {
        if (vis < 0) return 2;
        if (vis < 200) return 4;
        if (vis < 500) return 3;
        if (vis < 1000) return 2;
        return 1;
    }

    /** Sis simgesi mi gösterilecek: sis kodu ya da açık/bulutlu ama görüş 1 km altı. */
    static boolean isFog(int code, int vis) {
        return code == 45 || code == 48 || (code <= 3 && vis >= 0 && vis < 1000);
    }

    @Override
    public void draw(Canvas c) {
        cv = c;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(getBounds().width() / 48f, getBounds().height() / 48f);
        paint();
        c.restore();
    }

    private void paint() {
        int c = code;
        if (fogMode) {
            fog(fogLines);
            if (c == 48) flake(40, 10, 3.4f, ICE);
            return;
        }
        switch (c) {
            case 0: sky(24, 24, 9); return;
            case 1: sky(19, 19, 9); cloud(14, 19, 0.62f, CLOUD); return;
            case 2: sky(15, 15, 6.5f); cloud(1, 8, 1.1f, CLOUD); return;
            case 3: cloud(-1, 3, 0.85f, DARK); cloud(4, 12, 1, CLOUD); return;
            // Çiseleme
            case 51: cloud(1, 0, 1, CLOUD); dots(1, RAIN); return;
            case 53: cloud(1, 0, 1, CLOUD); dots(2, RAIN); return;
            case 55: cloud(1, 0, 1, DARK); dots(3, RAIN); return;
            case 56: cloud(1, 0, 1, CLOUD); dots(1, ICE); flake(36, 41, 3.4f, ICE); return;
            case 57: cloud(1, 0, 1, DARK); dots(2, ICE); flake(36, 43, 3.4f, ICE); return;
            // Yağmur
            case 61: cloud(1, 0, 1, CLOUD); rain(2, 6, 1.6f, 2.2f, RAIN); return;
            case 63: cloud(1, 0, 1, CLOUD); rain(3, 9, 2, 2.4f, RAIN); return;
            case 65: cloud(1, 0, 1, DARK); rain2(4, 6.5f, 2, 2.6f, RAIN); return;
            case 66: cloud(1, 0, 1, CLOUD); rain(2, 8, 1.6f, 2.2f, ICE); flake(40, 42, 3.4f, ICE); return;
            case 67: cloud(1, 0, 1, DARK); rain(3, 8, 2, 2.6f, ICE); flake(41, 43, 3.4f, ICE); return;
            // Sağanak: güneş yok, mor ve çok eğik kalın çizgiler
            case 80: cloud(1, 0, 1, CLOUD); rain(2, 8, 6, 2.6f, SHOWER); return;
            case 81: cloud(1, 0, 1, CLOUD); rain(3, 10, 7, 2.8f, SHOWER); return;
            case 82: cloud(1, 0, 1, DARK); rain2(4, 6.5f, 5, 2.8f, SHOWER); return;
            // Kar
            case 71: cloud(1, 0, 1, CLOUD); snow(2); return;
            case 73: cloud(1, 0, 1, CLOUD); snow(3); return;
            case 75: cloud(1, 0, 1, DARK); snow(5); return;
            case 77: cloud(1, 0, 1, CLOUD); grains(); return;
            case 85:
                cloud(1, 0, 1, CLOUD);
                flake(13, 35, 3.8f, SNOW); flake(33, 38, 3.8f, SNOW);
                seg(27, 31, 20, 43, 2.6f, SHOWER);
                return;
            case 86:
                cloud(1, 0, 1, DARK);
                flake(11, 35, 3.2f, SNOW); flake(26, 36, 3.2f, SNOW); flake(16, 44, 3.2f, SNOW); flake(35, 44, 3.2f, SNOW);
                seg(20, 31, 15, 39, 2.6f, SHOWER); seg(38, 31, 33, 39, 2.6f, SHOWER);
                return;
            // Fırtına
            case 95:
                cloud(1, 0, 1, DARK); bolt(0);
                seg(13, 32, 11, 39, 2.3f, RAIN); seg(36, 32, 34, 39, 2.3f, RAIN);
                return;
            case 96:
                cloud(1, 0, 1, DARK); bolt(-4);
                dot(31, 36, 2.4f, HAIL); dot(36, 38, 2.4f, HAIL);
                return;
            case 99:
                cloud(1, 0, 1, DARK); bolt(-5);
                dot(31, 36, 2.4f, HAIL); dot(37, 41, 2.4f, HAIL); dot(11, 37, 2.4f, HAIL); dot(31, 44, 2.4f, HAIL);
                return;
            default:
                cloud(4, 12, 1, CLOUD);
        }
    }

    // ---- parçalar ----

    private void sky(float cx, float cy, float r) {
        if (day) sun(cx, cy, r); else moon(cx, cy, r + 1);
    }

    private void sun(float cx, float cy, float r) {
        dot(cx, cy, r, SUN);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float r1 = r + 3, r2 = r + 6.5f;
            seg(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, 2.6f, SUN);
        }
    }

    private void moon(float cx, float cy, float r) {
        Path a = new Path(), b = new Path();
        a.addCircle(cx, cy, r, Path.Direction.CW);
        b.addCircle(cx + r * 0.45f, cy - r * 0.35f, r * 0.8f, Path.Direction.CW);
        a.op(b, Path.Op.DIFFERENCE);
        fill.setColor(MOON);
        cv.drawPath(a, fill);
    }

    private void cloud(float tx, float ty, float s, int color) {
        Path p = new Path();
        p.moveTo(13, 27);
        p.cubicTo(9.2f, 27, 6, 24, 6, 20.2f);
        p.cubicTo(6, 16.8f, 8.6f, 14, 11.8f, 13.6f);
        p.cubicTo(13.2f, 9.8f, 16.8f, 7, 21, 7);
        p.cubicTo(25.8f, 7, 29.8f, 10.6f, 30.4f, 15.2f);
        p.cubicTo(31, 15, 31.4f, 15, 32, 15);
        p.cubicTo(36.4f, 15, 40, 18.6f, 40, 23);
        p.cubicTo(40, 25.2f, 38.2f, 27, 36, 27);
        p.close();
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(s, s);
        m.postTranslate(tx, ty);
        p.transform(m);
        fill.setColor(color);
        cv.drawPath(p, fill);
    }

    private static float[] xs(int n) {
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = n == 1 ? 23 : 12 + i * (22f / (n - 1));
        return r;
    }

    private void rain(int n, float len, float sl, float w, int color) {
        for (float x : xs(n)) seg(x + sl / 2, 31, x - sl / 2, 31 + len, w, color);
    }

    /** İki sıra: ikinci sıra birincinin aralarına düşer. */
    private void rain2(int n, float len, float sl, float w, int color) {
        rain(n, len, sl, w, color);
        float step = 22f / (n - 1), y = 31 + len + 3;
        float[] x = xs(n);
        for (int i = 0; i < n - 1; i++) {
            float xx = x[i] + step / 2;
            seg(xx + sl / 2, y, xx - sl / 2, y + len, w, color);
        }
    }

    private void dots(int rows, int color) {
        float[][] r = {{13, 21, 29}, {17, 25, 33}, {13, 21, 29}};
        for (int i = 0; i < rows; i++)
            for (float x : r[i]) dot(x + (i % 2 == 1 ? 0 : 2), 33 + i * 5, 1.6f, color);
    }

    private void flake(float x, float y, float s, int color) {
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI / 3;
            float dx = (float) Math.cos(a) * s, dy = (float) Math.sin(a) * s;
            seg(x - dx, y - dy, x + dx, y + dy, 1.9f, color);
        }
    }

    private void snow(int n) {
        float[][] p;
        if (n == 2) p = new float[][]{{16, 35}, {30, 39}};
        else if (n == 3) p = new float[][]{{13, 35}, {23, 39}, {33, 35}};
        else p = new float[][]{{12, 34}, {23, 34}, {34, 34}, {17.5f, 42}, {28.5f, 42}};
        for (float[] q : p) flake(q[0], q[1], n >= 5 ? 3.2f : 3.8f, SNOW);
    }

    private void grains() {
        float[][] p = {{14, 35}, {22, 38}, {30, 35}, {18, 42}, {26, 43}, {34, 41}};
        fill.setColor(SNOW);
        for (float[] q : p) {
            Path d = new Path();
            d.moveTo(q[0], q[1] - 2); d.lineTo(q[0] + 2, q[1]); d.lineTo(q[0], q[1] + 2); d.lineTo(q[0] - 2, q[1]);
            d.close();
            cv.drawPath(d, fill);
        }
    }

    private void bolt(float tx) {
        Path p = new Path();
        p.moveTo(25 + tx, 28); p.lineTo(19 + tx, 38); p.lineTo(24 + tx, 38); p.lineTo(21 + tx, 46);
        p.lineTo(31 + tx, 34); p.lineTo(25.6f + tx, 34); p.lineTo(29 + tx, 28); p.close();
        fill.setColor(BOLT);
        cv.drawPath(p, fill);
    }

    /** Bulutsuz yatay çizgiler; sayı arttıkça sis yoğun. */
    private void fog(int n) {
        float[][] len = {{8, 40}, {5, 37}, {11, 43}, {7, 35}};
        float y0 = 24 - (n - 1) * 3.5f;
        for (int i = 0; i < n; i++) seg(len[i][0], y0 + i * 7, len[i][1], y0 + i * 7, 2.8f, FOG);
    }

    private void seg(float x1, float y1, float x2, float y2, float w, int color) {
        line.setStrokeWidth(w);
        line.setColor(color);
        cv.drawLine(x1, y1, x2, y2, line);
    }

    private void dot(float x, float y, float r, int color) {
        fill.setColor(color);
        cv.drawCircle(x, y, r, fill);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); line.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); line.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
