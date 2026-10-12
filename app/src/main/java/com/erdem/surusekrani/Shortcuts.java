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

/** Müzik çaların altında, Uygulamalar listesindeki ilk dört uygulamanın kısayolu. */
final class Shortcuts {
    private static final int MAX = 4;

    private Shortcuts() {}

    static void render(Activity a) {
        LinearLayout row = a.findViewById(R.id.shortcutRow);
        if (row == null) return;
        row.removeAllViews();
        PackageManager pm = a.getPackageManager();

        String saved = a.getSharedPreferences("surus", Context.MODE_PRIVATE).getString("favApps", "");
        int count = 0;
        if (!saved.isEmpty()) {
            for (String k : saved.split("\\|")) {
                if (count >= MAX) break;
                int slash = k.indexOf('/');
                if (slash <= 0) continue;
                ComponentName cn = new ComponentName(k.substring(0, slash), k.substring(slash + 1));
                ActivityInfo info;
                try {
                    info = pm.getActivityInfo(cn, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    continue; // kaldırılmış uygulama
                }
                View tile = tile(a, row, count);
                ((ImageView) tile.findViewWithTag("icon")).setImageDrawable(info.loadIcon(pm));
                ((TextView) tile.findViewWithTag("label")).setText(info.loadLabel(pm));
                tile.setContentDescription(info.loadLabel(pm));
                tile.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
                tile.setOnLongClickListener(v -> { openEditor(a); return true; });
                count++;
            }
        }

        // Boş yer varsa "Ekle" kutusu
        if (count < MAX) {
            View tile = tile(a, row, count);
            ImageView icon = tile.findViewWithTag("icon");
            icon.setImageResource(R.drawable.ic_plus);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(a.getColor(R.color.muted)));
            int pad = dp(a, 8);
            icon.setPadding(pad, pad, pad, pad);
            ((TextView) tile.findViewWithTag("label")).setText("Ekle");
            tile.setContentDescription("Kısayol ekle");
            tile.setOnClickListener(v -> openEditor(a));
            count++;
        }
        // Satırı eşit kutularla doldur
        for (; count < MAX; count++) {
            View spacer = new View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMarginStart(dp(a, 8));
            row.addView(spacer, lp);
        }
    }

    private static void openEditor(Activity a) {
        MainActivity.sOwnScreen = true;
        a.startActivity(new Intent(a, AppsActivity.class).putExtra(AppsActivity.EXTRA_EDIT, true));
    }

    private static View tile(Activity a, LinearLayout row, int index) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.chip_bg);
        t.setPadding(dp(a, 4), dp(a, 8), dp(a, 4), dp(a, 6));
        t.setClickable(true);
        t.setFocusable(true);

        ImageView icon = new ImageView(a);
        icon.setTag("icon");
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) lp.setMarginStart(dp(a, 8));
        row.addView(t, lp);
        return t;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
