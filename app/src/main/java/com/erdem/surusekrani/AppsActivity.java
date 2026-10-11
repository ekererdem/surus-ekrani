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
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ana ekran modunda diğer uygulamalara ulaşmak için basit uygulama listesi. */
public class AppsActivity extends Activity {
    private final List<ResolveInfo> apps = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PackageManager pm = getPackageManager();

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        Collator col = Collator.getInstance(new Locale("tr", "TR"));
        found.removeIf(r -> getPackageName().equals(r.activityInfo.packageName));
        found.sort((a, b) -> col.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
        for (ResolveInfo r : found) {
            apps.add(r);
            labels.add(r.loadLabel(pm).toString());
        }

        int pad = dp(16);
        GridView grid = new GridView(this);
        grid.setBackgroundColor(getColor(R.color.bg));
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(96));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(18));
        grid.setAdapter(new Adapter(pm));
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            ResolveInfo r = apps.get(pos);
            Intent l = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(l);
        });
        setContentView(grid);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class Adapter extends BaseAdapter {
        private final PackageManager pm;

        Adapter(PackageManager pm) { this.pm = pm; }

        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
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
                cell.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
                label = new TextView(AppsActivity.this);
                label.setTextColor(getColor(R.color.fg));
                label.setTextSize(14);
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
            icon.setImageDrawable(apps.get(i).loadIcon(pm));
            label.setText(labels.get(i));
            return cell;
        }
    }
}
