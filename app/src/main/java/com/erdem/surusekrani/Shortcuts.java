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
        gear.setImageDrawable(settingsIcon(a, logos));
        st.setContentDescription("Ayarlar");
        st.setOnClickListener(v -> {
            MainActivity.sOwnScreen = true;
            a.startActivity(new Intent(a, SettingsActivity.class));
        });
        st.setOnLongClickListener(v -> { openEditor(a); return true; });
    }

    private static final int GEAR_BG = 0x2A313A;

    /**
     * Ayarlar simgesi: yanındaki logolardan en dolu olanını kalıp alır (Xiaomi'de yuvarlatılmış kare ve
     * etrafındaki boşluk dahil), aynı şekli koyu gri doldurup ortasına mavi çarkı çizer. Böylece telefon
     * hangi simge şeklini kullanıyorsa Ayarlar da birebir o boyutta ve şekilde olur.
     */
    private static Drawable settingsIcon(Activity a, java.util.List<Drawable> logos) {
        final int s = 192;
        int[] best = null;
        int bestCover = 0;
        for (Drawable d : logos) {
            try {
                Drawable c = d.getConstantState() != null
                        ? d.getConstantState().newDrawable(a.getResources()).mutate() : d;
                Rect old = new Rect(d.getBounds());
                Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                c.setBounds(0, 0, s, s);
                c.draw(new Canvas(b));
                if (c == d) d.setBounds(old);
                int[] px = new int[s * s];
                b.getPixels(px, 0, s, 0, 0, s, s);
                b.recycle();
                int cover = 0;
                for (int v : px) if ((v >>> 24) >= 128) cover++;
                if (cover > bestCover) {
                    bestCover = cover;
                    best = px;
                }
            } catch (Exception ignored) {
            }
        }
        Drawable g = a.getDrawable(R.drawable.ic_settings).mutate();
        g.setTint(a.getColor(R.color.rain));
        if (best == null || bestCover < s * s * 0.35f) {
            // Kalıp alınacak logo yok: telefonun kendi simge şablonu
            return new android.graphics.drawable.AdaptiveIconDrawable(
                    new android.graphics.drawable.ColorDrawable(0xFF000000 | GEAR_BG),
                    new android.graphics.drawable.InsetDrawable(g, 0.29f));
        }
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
        int size = Math.round(Math.min(maxX - minX + 1, maxY - minY + 1) * 0.62f);
        int cx = (minX + maxX + 1) / 2, cy = (minY + maxY + 1) / 2;
        g.setBounds(cx - size / 2, cy - size / 2, cx - size / 2 + size, cy - size / 2 + size);
        g.draw(new Canvas(bm));
        return new BitmapDrawable(a.getResources(), bm);
    }

    private static void openEditor(Activity a) {
        MainActivity.sOwnScreen = true;
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
