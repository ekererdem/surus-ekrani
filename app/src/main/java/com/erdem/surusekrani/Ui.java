package com.erdem.surusekrani;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Kodla kurulan basit sayfalar için ortak parçalar (Neler yapabilirim, Notlarım, Tanıtım). */
final class Ui {
    private Ui() {}

    static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Koyu arka planlı, kaydırılabilir sayfa; başlığı ortalı. İçerik eklenecek kabı döndürür. */
    static LinearLayout page(Activity a, String title) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(a.getColor(R.color.bg));
        sv.setFillViewport(true);
        sv.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets ins = in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(Math.round(ins.left * 0.35f), ins.top, ins.right / 2, ins.bottom);
            return in;
        });
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 12), dp(a, 16), dp(a, 14), dp(a, 32));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(32);
        t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        t.setTextColor(a.getColor(R.color.fg));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.setContentView(sv);
        return box;
    }

    static TextView section(Activity a, LinearLayout box, String text) {
        TextView t = new TextView(a);
        t.setText(text.toUpperCase(new java.util.Locale("tr", "TR")));
        t.setTextSize(17);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(a.getColor(R.color.amber));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 26);
        lp.bottomMargin = dp(a, 6);
        box.addView(t, lp);
        return t;
    }

    /** Kalın başlık + altında gri açıklama. */
    static View item(Activity a, LinearLayout box, String head, String body) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.btn_outline);
        col.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 12));
        TextView h = new TextView(a);
        h.setText(head);
        h.setTextSize(16);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(a.getColor(R.color.fg));
        col.addView(h);
        if (body != null && !body.isEmpty()) {
            TextView b = new TextView(a);
            b.setText(body);
            b.setTextSize(14);
            b.setTextColor(a.getColor(R.color.muted));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = dp(a, 4);
            col.addView(b, bl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        box.addView(col, lp);
        return col;
    }

    static TextView note(Activity a, LinearLayout box, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(a.getColor(R.color.muted));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 6);
        box.addView(t, lp);
        return t;
    }

    static Button button(Activity a, LinearLayout box, String text, boolean primary) {
        Button b = new Button(a, null, 0, R.style.OutlineBtn);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); // yazı kutunun tam ortasında
        b.setPadding(dp(a, 12), 0, dp(a, 12), 0);
        b.setIncludeFontPadding(false);
        if (primary) {
            b.setBackgroundResource(R.drawable.tile_accent);
            b.setTextColor(a.getColor(R.color.accent_ink));
        } else {
            b.setBackgroundResource(R.drawable.btn_outline);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 56));
        lp.topMargin = dp(a, 10);
        box.addView(b, lp);
        return b;
    }
}
