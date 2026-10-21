package com.erdem.surusekrani;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Müzik çaların altında, seçilen uygulamaların kısayolu (en fazla 4) ve en sağda her zaman Ayarlar. */
final class Shortcuts {
    static final int MAX = 4;

    static void render(Activity a) {
        LinearLayout row = a.findViewById(R.id.shortcutRow);
        if (row == null) return;
        row.removeAllViews();
        PackageManager pm = a.getPackageManager();

        // Seçilen uygulamalar (en fazla 4) + Ayarlar; kaç tane seçildiyse o kadar kutu
        java.util.List<ComponentName> apps = new java.util.ArrayList<>();
        java.util.List<ActivityInfo> infos = new java.util.ArrayList<>();
        String saved = a.getSharedPreferences("surus", Context.MODE_PRIVATE).getString("favApps", "");
        if (!saved.isEmpty()) {
            for (String k : saved.split("\\|")) {
                if (apps.size() >= MAX) break;
                int slash = k.indexOf('/');
                if (slash <= 0) continue;
                ComponentName cn = new ComponentName(k.substring(0, slash), k.substring(slash + 1));
                try {
                    infos.add(pm.getActivityInfo(cn, 0));
                    apps.add(cn);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // kaldırılmış uygulama
                }
            }
        }
        // Kutular satırı eşit paylaşır: 2 uygulama = iki yarım, 5 uygulama = beşte bir
        if (apps.isEmpty()) {
            View tile = tile(a, row, 0, true);
            ImageView icon = tile.findViewWithTag("icon");
            icon.setImageResource(R.drawable.ic_plus);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(a.getColor(R.color.muted)));
            int pad = dp(a, 8);
            icon.setPadding(pad, pad, pad, pad);
            ((TextView) tile.findViewWithTag("label")).setText("Ekle");
            tile.setContentDescription("Kısayol ekle");
            tile.setOnClickListener(v -> openEditor(a));
        }
        java.util.List<Drawable> logos = new java.util.ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            ComponentName cn = apps.get(i);
            ActivityInfo info = infos.get(i);
            View tile = tile(a, row, i, false);
            Drawable logo = info.loadIcon(pm);
            logos.add(logo);
            ((ImageView) tile.findViewWithTag("icon")).setImageDrawable(logo);
            tile.setContentDescription(info.loadLabel(pm));
            tile.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
            tile.setOnLongClickListener(v -> { openEditor(a); return true; });
        }
        // En sağda her zaman Ayarlar; yanındaki logolarla aynı boyut ve şekilde.
        View st = tile(a, row, Math.max(1, apps.size()), false);
        ImageView gear = st.findViewWithTag("icon");
        Drawable gd = settingsIcon(a, logos);
        gear.setImageDrawable(gd);
        // Yeni sürüm varsa simgenin üst kenarında küçük kırmızı "Yeni sürüm var" etiketi (güncelleme Ayarlar'ın en altında)
        if (Update.pending(a) != null) addUpdateTag(a, row, st, gear, gd);
        st.setContentDescription(Update.pending(a) != null ? "Ayarlar, yeni sürüm var" : "Ayarlar");
        st.setOnClickListener(v -> a.startActivity(new Intent(a, SettingsActivity.class)));
        st.setOnLongClickListener(v -> { openEditor(a); return true; });
    }

    /**
     * Ayarlar kutusunun üstüne, simgenin görünen üst kenarına ortalı küçük kırmızı etiket.
     * Kutu bir FrameLayout'a alınır; etiket satırın dışına taşabilsin diye kırpma kapatılır.
     */
    private static void addUpdateTag(Activity a, LinearLayout row, View st, ImageView icon, Drawable gd) {
        int idx = row.indexOfChild(st);
        android.view.ViewGroup.LayoutParams lp = st.getLayoutParams();
        row.removeView(st);
        android.widget.FrameLayout fl = new android.widget.FrameLayout(a);
        fl.setClipChildren(false);
        fl.setClipToPadding(false);
        fl.addView(st, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tag = new TextView(a);
        tag.setText("Yeni sürüm var");
        tag.setTextSize(8.5f);
        tag.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tag.setTextColor(0xFFFFFFFF);
        tag.setSingleLine(true);
        tag.setIncludeFontPadding(false);
        tag.setPadding(dp(a, 7), dp(a, 3), dp(a, 7), dp(a, 3));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(a.getColor(R.color.low));
        bg.setCornerRadius(dp(a, 20));
        bg.setStroke(Math.max(2, dp(a, 1)), a.getColor(R.color.bg));
        tag.setBackground(bg);
        tag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fl.addView(tag, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        row.addView(fl, idx, lp);
        row.setClipChildren(false);
        if (row.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) row.getParent()).setClipChildren(false);

        // Simge şeklinin görünen üst kenarı (çizimdeki saydam boşluk hariç)
        float topFrac = 0f;
        if (gd instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) gd).getBitmap();
            int x = b.getWidth() / 2;
            for (int y = 0; y < b.getHeight(); y++) {
                if ((b.getPixel(x, y) >>> 24) > 40) { topFrac = y / (float) b.getHeight(); break; }
            }
        }
        float tf = topFrac;
        fl.post(() -> {
            Drawable d = icon.getDrawable();
            if (d == null) return;
            float[] m = new float[9];
            icon.getImageMatrix().getValues(m);
            float drawnTop = m[android.graphics.Matrix.MTRANS_Y] + tf * d.getIntrinsicHeight() * m[android.graphics.Matrix.MSCALE_Y];
            float edge = st.getTop() + icon.getTop() + icon.getPaddingTop() + drawnTop;
            tag.setTranslationY(edge - tag.getHeight() / 2f);
        });
    }

    /** Ayarlar simgesinin zemini (açık) ve çark rengi (canlı mavi). */
    private static final int GEAR_BG = 0xEEF2F7;

    /**
     * Ayarlar simgesi: yanındaki logolardan en dolu olanını kalıp alır (Xiaomi'de yuvarlatılmış kare ve
     * etrafındaki boşluk dahil), aynı şekli açık renkle doldurup ortasına mavi çarkı çizer. Böylece telefon
     * hangi simge şeklini kullanıyorsa Ayarlar da birebir o boyutta ve şekilde olur.
     */
    private static Drawable settingsIcon(Activity a, java.util.List<Drawable> logos) {
        final int s = 192;
        int[] best = null;
        int bestCover = 0;
        for (Drawable d : logos) {
            int[] px = pixels(a, d, s);
            if (px == null) continue;
            int cover = 0;
            for (int v : px) if ((v >>> 24) >= 128) cover++;
            if (cover > bestCover) {
                bestCover = cover;
                best = px;
            }
        }
        android.util.Log.i("Kopilot", "ayarlar simgesi: " + logos.size() + " logo, en dolu %"
                + (100 * bestCover / (s * s)));
        if (best == null || bestCover < s * s * 0.2f) best = roundedSquare(s); // kalıp yoksa dolu yuvarlak kare

        // Şekli doldur: her satırda ilk ve son dolu nokta arası tam dolu, kenarlar logodaki yumuşaklıkta
        int[] out = new int[s * s];
        int minX = s, maxX = -1, minY = s, maxY = -1;
        for (int y = 0; y < s; y++) {
            int l = -1, r = -1;
            for (int x = 0; x < s; x++) {
                if ((best[y * s + x] >>> 24) >= 128) {
                    if (l < 0) l = x;
                    r = x;
                }
            }
            for (int x = 0; x < s; x++) {
                int al = best[y * s + x] >>> 24;
                if (l >= 0 && x > l && x < r) al = 255;
                out[y * s + x] = (al << 24) | GEAR_BG;
            }
            if (l >= 0) {
                minX = Math.min(minX, l);
                maxX = Math.max(maxX, r);
                minY = Math.min(minY, y);
                maxY = y;
            }
        }
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        bm.setPixels(out, 0, s, 0, 0, s, s);
        Drawable g = a.getDrawable(R.drawable.ic_settings).mutate();
        g.setTint(a.getColor(R.color.rain));
        int size = Math.round(Math.min(maxX - minX + 1, maxY - minY + 1) * 0.72f);
        int cx = (minX + maxX + 1) / 2, cy = (minY + maxY + 1) / 2;
        g.setBounds(cx - size / 2, cy - size / 2, cx - size / 2 + size, cy - size / 2 + size);
        g.draw(new Canvas(bm));
        return new BitmapDrawable(a.getResources(), bm);
    }

    /**
     * Logoyu s x s piksele çiz. Telefon logoları ekran kartı belleğinde tutabiliyor (normal tuvale
     * çizilemez); bu yüzden önce kayıt alıp ekran kartında çizdiriyoruz, sonra piksellerini okuyoruz.
     */
    private static int[] pixels(Activity a, Drawable d, int s) {
        Drawable c = d.getConstantState() != null
                ? d.getConstantState().newDrawable(a.getResources()).mutate() : d;
        Rect old = new Rect(d.getBounds());
        c.setBounds(0, 0, s, s);
        Bitmap sw = null;
        try {
            android.graphics.Picture pic = new android.graphics.Picture();
            c.draw(pic.beginRecording(s, s));
            pic.endRecording();
            Bitmap hw = Bitmap.createBitmap(pic);
            sw = hw.copy(Bitmap.Config.ARGB_8888, false);
            hw.recycle();
        } catch (Throwable t) {
            try {
                sw = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                c.draw(new Canvas(sw));
            } catch (Throwable t2) {
                android.util.Log.w("Kopilot", "logo okunamadı: " + d.getClass().getName(), t2);
                sw = null;
            }
        } finally {
            if (c == d) d.setBounds(old);
        }
        if (sw == null) return null;
        int[] px = new int[s * s];
        sw.getPixels(px, 0, s, 0, 0, s, s);
        sw.recycle();
        return px;
    }

    private static int[] roundedSquare(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);
        new Canvas(b).drawRoundRect(0, 0, s, s, s * 0.23f, s * 0.23f, p);
        int[] px = new int[s * s];
        b.getPixels(px, 0, s, 0, 0, s, s);
        b.recycle();
        return px;
    }

    private static void openEditor(Activity a) {
        a.startActivity(new Intent(a, AppsActivity.class).putExtra(AppsActivity.EXTRA_EDIT, true));
    }

    /**
     * framed: "Ekle" kutusu (çerçeveli, yazılı). Uygulamalar ise çerçevesiz, yazısız, büyük logo;
     * dokunma alanı satırdaki payın tamamı, basınca yuvarlak dalga.
     */
    private static View tile(Activity a, LinearLayout row, int index, boolean framed) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);

        ImageView icon = new ImageView(a);
        icon.setTag("icon");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (framed) {
            t.setBackgroundResource(R.drawable.chip_bg);
            t.setPadding(dp(a, 4), dp(a, 8), dp(a, 4), dp(a, 6));
            t.addView(icon, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)));
            TextView label = new TextView(a);
            label.setTag("label");
            label.setTextSize(12);
            label.setTextColor(a.getColor(R.color.muted));
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(a, 4);
            t.addView(label, llp);
        } else {
            android.util.TypedValue tv = new android.util.TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
            t.setBackgroundResource(tv.resourceId);
            t.setPadding(0, dp(a, 6), 0, dp(a, 6));
            // Genişlik payı kadar, en fazla 58dp kare; dar ekranda logo kesilmeden küçülür
            t.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 58)));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) lp.setMarginStart(dp(a, 8));
        row.addView(t, lp);
        return t;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
