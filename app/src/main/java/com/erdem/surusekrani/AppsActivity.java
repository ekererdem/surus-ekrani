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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sadece kullanıcının seçtiği uygulamaları gösterir.
 * "Düzenle" ile tüm uygulamalar listelenir; dokununca eklenir ya da çıkarılır.
 */
public class AppsActivity extends Activity {
    private static final String KEY = "favApps";
    static final String EXTRA_EDIT = "edit";
    /** Bu uygulamanın yerine seçileni koy (Ayarlar → Değiştir). */
    static final String EXTRA_REPLACE = "replace";

    private PackageManager pm;
    private final List<ResolveInfo> all = new ArrayList<>();
    private final Map<String, ResolveInfo> byKey = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final List<String> favs = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private boolean editing;

    private Adapter adapter;
    private Button editBtn;
    private TextView title, hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        pm = getPackageManager();

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        found.removeIf(r -> getPackageName().equals(r.activityInfo.packageName));
        for (ResolveInfo r : found) labels.put(key(r), r.loadLabel(pm).toString());
        Collator col = Collator.getInstance(new Locale("tr", "TR"));
        found.sort((a, b) -> col.compare(labels.get(key(a)), labels.get(key(b))));
        for (ResolveInfo r : found) {
            all.add(r);
            byKey.put(key(r), r);
        }

        String saved = getSharedPreferences("surus", MODE_PRIVATE).getString(KEY, "");
        if (!saved.isEmpty()) favs.addAll(Arrays.asList(saved.split("\\|")));

        title = findViewById(R.id.appsTitle);
        hint = findViewById(R.id.appsHint);
        editBtn = findViewById(R.id.appsEdit);
        GridView grid = findViewById(R.id.appsGrid);
        adapter = new Adapter();
        grid.setAdapter(adapter);

        findViewById(R.id.appsBack).setOnClickListener(v -> onBackPressed());
        editBtn.setOnClickListener(v -> {
            editing = !editing;
            refresh();
        });
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            String k = shown.get(pos);
            String replace = getIntent().getStringExtra(EXTRA_REPLACE);
            if (editing && replace != null) {
                int at = favs.indexOf(replace);
                if (!k.equals(replace)) {
                    favs.remove(k);
                    at = favs.indexOf(replace);
                    if (at >= 0) favs.set(at, k); else favs.add(k);
                }
                save();
                finish();
                return;
            }
            if (editing) {
                if (favs.contains(k)) favs.remove(k);
                else if (visibleFavs().size() >= Shortcuts.MAX) {
                    android.widget.Toast.makeText(this, "En fazla " + Shortcuts.MAX + " uygulama seçebilirsin",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                } else favs.add(k);
                save();
                adapter.notifyDataSetChanged();
            } else {
                launch(byKey.get(k));
            }
        });

        // Hiç uygulama seçilmemişse doğrudan seçim ekranıyla aç.
        editing = getIntent().getBooleanExtra(EXTRA_EDIT, false) || visibleFavs().isEmpty();
        refresh();
    }

    private static String key(ResolveInfo r) {
        return r.activityInfo.packageName + "/" + r.activityInfo.name;
    }

    private List<String> visibleFavs() {
        List<String> out = new ArrayList<>();
        for (String k : favs) if (byKey.containsKey(k)) out.add(k);
        return out;
    }

    private void refresh() {
        shown.clear();
        if (editing) {
            for (ResolveInfo r : all) shown.add(key(r));
            title.setText(getIntent().getStringExtra(EXTRA_REPLACE) != null ? "Yerine konacak uygulamayı seç" : "Uygulama seç");
            hint.setText("Ana ekranda müzik çaların altında görünmesini istediğin uygulamalara dokun (en fazla 5). Tekrar dokunursan çıkar.");
            hint.setVisibility(View.VISIBLE);
            editBtn.setText("Tamam");
        } else {
            shown.addAll(visibleFavs());
            title.setText("Uygulamalar");
            hint.setText("Henüz uygulama eklemedin. Düzenle'ye basıp istediklerini seç.");
            hint.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            editBtn.setText("Düzenle");
        }
        adapter.notifyDataSetChanged();
    }

    private void save() {
        getSharedPreferences("surus", MODE_PRIVATE).edit()
                .putString(KEY, TextUtils.join("|", favs)).apply();
    }

    private void launch(ResolveInfo r) {
        if (r == null) return;
        Intent l = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(l);
    }

    @Override
    public void onBackPressed() {
        if (editing && !visibleFavs().isEmpty()) {
            editing = false;
            refresh();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                icon = new ImageView(AppsActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
                label = new TextView(AppsActivity.this);
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
            String k = shown.get(i);
            ResolveInfo r = byKey.get(k);
            icon.setImageDrawable(r == null ? null : r.loadIcon(pm));
            label.setText(labels.get(k));

            boolean selected = favs.contains(k);
            float alpha = editing && !selected ? 0.35f : 1f;
            icon.setAlpha(alpha);
            label.setAlpha(alpha);
            label.setTextColor(getColor(editing && selected ? R.color.teal : R.color.fg));
            return cell;
        }
    }
}
