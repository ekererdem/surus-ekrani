package com.erdem.surusekrani;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ayarlar > Bildirim kutusu: telefondaki tüm uygulamalar. Dokun: bildirimleri ana ekrandaki kutuya gelsin;
 * tekrar dokun: gelmesin. Seçililer üstte, parlak; seçili olmayanlar soluk.
 */
public class NotifAppsActivity extends Activity {
    private PackageManager pm;
    private final List<String> pkgs = new ArrayList<>();
    private final Map<String, ResolveInfo> info = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private Set<String> chosen;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Accent.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        pm = getPackageManager();
        chosen = new HashSet<>(Notifs.selected(this));

        // Her uygulama bir kez (bazı uygulamaların birden çok simgesi olur)
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (p.equals(getPackageName()) || info.containsKey(p)) continue;
            info.put(p, r);
            labels.put(p, r.loadLabel(pm).toString());
            pkgs.add(p);
        }
        Collator col = Collator.getInstance(new Locale("tr", "TR"));
        // Açılışta seçililer başta, sonra alfabetik (dokundukça yer değiştirmesin)
        pkgs.sort((a, b) -> {
            boolean sa = chosen.contains(a), sb = chosen.contains(b);
            if (sa != sb) return sa ? -1 : 1;
            return col.compare(labels.get(a), labels.get(b));
        });

        ((TextView) findViewById(R.id.appsTitle)).setText("Bildirim kutusu");
        TextView hint = findViewById(R.id.appsHint);
        hint.setText("Bildirimleri ana ekrandaki kutuda görünsün istediğin uygulamalara dokun. Tekrar dokunursan çıkar.");
        Button done = findViewById(R.id.appsEdit);
        done.setText("Tamam");
        done.setOnClickListener(v -> finish());
        findViewById(R.id.appsBack).setOnClickListener(v -> finish());

        GridView grid = findViewById(R.id.appsGrid);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            String p = pkgs.get(pos);
            if (!chosen.remove(p)) chosen.add(p);
            Notifs.setSelected(this, chosen);
            adapter.notifyDataSetChanged();
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return pkgs.size(); }
        @Override public Object getItem(int i) { return pkgs.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                cell = new LinearLayout(NotifAppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                icon = new ImageView(NotifAppsActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
                label = new TextView(NotifAppsActivity.this);
                label.setTextSize(13);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                cell.addView(label, lp);
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            String p = pkgs.get(i);
            ResolveInfo r = info.get(p);
            icon.setImageDrawable(r == null ? null : r.loadIcon(pm));
            boolean on = chosen.contains(p);
            label.setText(on ? "✓ " + labels.get(p) : labels.get(p));
            float alpha = on ? 1f : 0.35f;
            icon.setAlpha(alpha);
            label.setAlpha(alpha);
            label.setTextColor(Accent.color(NotifAppsActivity.this, on ? R.color.accent : R.color.fg));
            cell.setContentDescription(labels.get(p) + (on ? ", seçili" : ", seçili değil"));
            return cell;
        }
    }
}
