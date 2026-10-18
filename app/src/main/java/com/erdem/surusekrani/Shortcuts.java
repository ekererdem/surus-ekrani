package com.erdem.surusekrani;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Müzik çaların altında, Ayarlar'dan seçilen uygulamaların kısayolu (en fazla 5). */
final class Shortcuts {
    static final int MAX = 5;

    static void render(Activity a) {
        LinearLayout row = a.findViewById(R.id.shortcutRow);
        if (row == null) return;
        row.removeAllViews();
        PackageManager pm = a.getPackageManager();

        // Seçilen uygulamalar (en fazla 5); kaç tane seçildiyse o kadar kutu
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
        for (int i = 0; i < apps.size(); i++) {
            ComponentName cn = apps.get(i);
            ActivityInfo info = infos.get(i);
            View tile = tile(a, row, i, false);
            ((ImageView) tile.findViewWithTag("icon")).setImageDrawable(info.loadIcon(pm));
            tile.setContentDescription(info.loadLabel(pm));
            tile.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
            tile.setOnLongClickListener(v -> { openEditor(a); return true; });
        }
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
            t.addView(icon, new LinearLayout.LayoutParams(dp(a, 58), dp(a, 58)));
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
