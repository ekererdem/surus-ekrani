package com.erdem.surusekrani;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ev, İş ve bir favori yer için tek dokunuşla rota başlatan küçük tuşlar. */
final class QuickNav {
    private QuickNav() {}

    static void render(MainActivity a, Prefs p) {
        LinearLayout row = a.findViewById(R.id.quickNavRow);
        if (row == null) return;
        row.removeAllViews();

        add(a, row, R.drawable.ic_home, "Ev", p.home());
        add(a, row, R.drawable.ic_work, "İş", p.work());
        String n3 = p.fav3Name();
        add(a, row, R.drawable.ic_pin, n3.isEmpty() ? "Favori" : n3, p.fav3Addr());

        if (row.getChildCount() == 0) {
            LinearLayout chip = chip(a, row, R.drawable.ic_plus, "Hızlı rota ekle");
            ((ImageView) chip.getChildAt(0)).setImageTintList(ColorStateList.valueOf(a.getColor(R.color.muted)));
            ((TextView) chip.getChildAt(1)).setTextColor(a.getColor(R.color.muted));
            chip.setOnClickListener(v -> a.startActivity(new Intent(a, SettingsActivity.class)));
        }
    }

    private static void add(MainActivity a, LinearLayout row, int icon, String label, String addr) {
        if (addr == null || addr.trim().isEmpty()) return;
        LinearLayout chip = chip(a, row, icon, label);
        chip.setContentDescription(label + " için rota başlat");
        chip.setOnClickListener(v -> a.goTo(addr));
    }

    private static LinearLayout chip(MainActivity a, LinearLayout row, int icon, String label) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackgroundResource(R.drawable.chip_bg);
        c.setPadding(dp(a, 14), 0, dp(a, 16), 0);
        c.setClickable(true);
        c.setFocusable(true);

        ImageView iv = new ImageView(a);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(a.getColor(R.color.amber)));
        c.addView(iv, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));

        TextView tv = new TextView(a);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(a.getColor(R.color.fg));
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(dp(a, 8));
        c.addView(tv, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dp(a, 44), 1f);
        if (row.getChildCount() > 0) lp.setMarginStart(dp(a, 8));
        row.addView(c, lp);
        return c;
    }

    private static int dp(MainActivity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
