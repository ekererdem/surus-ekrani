package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {
    private static final int REQ_BT = 1;
    static final String EXTRA_DO = "do";
    private static final int REQ_NOTIF = 2;
    private static final int PICK_CALL = 21; // 21..25


    private Prefs p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // Kenar boşluğu: çentik/kamera payının sadece yarısı kadar bırak, ekranı daha iyi kullan
        View root = findViewById(R.id.settingsRoot);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets ins = in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(Math.round(ins.left * 0.35f), ins.top, ins.right / 2, ins.bottom); // sol pay %30 daha az
            return in;
        });
        p = new Prefs(this);

        RadioGroup nav = findViewById(R.id.radioNav);
        nav.check("yandex".equals(p.nav()) ? R.id.rbYandex : R.id.rbMaps);
        nav.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("nav", id == R.id.rbYandex ? "yandex" : "gmaps").apply());

        RadioGroup music = findViewById(R.id.radioMusic);
        music.check("ytm".equals(p.music()) ? R.id.rbYtm : R.id.rbSpotify);
        music.setOnCheckedChangeListener((g, id) ->
                p.edit().putString("music", id == R.id.rbYtm ? "ytm" : "spotify").apply());

        try {
            ((TextView) findViewById(R.id.versionText)).setText("Sürüm " +
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
        }



        // Ekran ölçeği: %70 – %120, 5'er adım
        android.widget.SeekBar scaleSeek = findViewById(R.id.scaleSeek);
        TextView scaleText = findViewById(R.id.scaleText);
        float sc = getSharedPreferences("surus", MODE_PRIVATE).getFloat("scale", 1f);
        int prog = Math.max(0, Math.min(10, Math.round((sc - 0.7f) / 0.05f)));
        scaleSeek.setProgress(prog);
        scaleText.setText("%" + (70 + prog * 5));
        scaleSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int v, boolean user) {
                scaleText.setText("%" + (70 + v * 5));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {
                p.edit().putFloat("scale", 0.7f + sb.getProgress() * 0.05f).apply();
            }
        });


        Places.migrate(this);
        setupCalls();

        findViewById(R.id.featuresBtn).setOnClickListener(v ->
                startActivity(new Intent(this, FeaturesActivity.class)));
        Switch quietSw = findViewById(R.id.swQuiet);
        quietSw.setChecked(Quiet.enabled(this));
        quietSw.setOnCheckedChangeListener((b, on) -> {
            p.edit().putBoolean("quietNotifs", on).apply();
            if (on && !Quiet.hasAccess(this)) askQuietAccess();
            refreshQuiet();
        });
        findViewById(R.id.quietStatus).setOnClickListener(v -> { if (!Quiet.hasAccess(this)) askQuietAccess(); });
        Switch waveSw = findViewById(R.id.swWave);
        waveSw.setChecked(Wave.enabled(this));
        waveSw.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("wave", on).apply());
        findViewById(R.id.tipsReset).setOnClickListener(v -> {
            Tips.reset(this);
            Toast.makeText(this, "İpuçları ana ekranda yeniden gösterilecek", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.notesBtn).setOnClickListener(v -> {
            java.util.List<String> l = Notes.list(this);
            if (l.isEmpty()) { Toast.makeText(this, "Henüz sesli not yok", Toast.LENGTH_SHORT).show(); return; }
            new AlertDialog.Builder(this).setTitle("Sesli notların")
                    .setItems(l.toArray(new String[0]), null)
                    .setPositiveButton("Kapat", null)
                    .setNegativeButton("Hepsini sil", (d, w) -> Notes.clear(this))
                    .show();
        });

        renderLayout();
        findViewById(R.id.layoutReset).setOnClickListener(v -> {
            Layout.reset(this);
            renderLayout();
            Toast.makeText(this, "Varsayılan düzen", Toast.LENGTH_SHORT).show();
        });

        Switch autoOpen = findViewById(R.id.swAutoOpen);
        autoOpen.setChecked(p.autoOpen());
        autoOpen.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoOpen", on).apply());

        Switch autoSplit = findViewById(R.id.swAutoSplit);
        autoSplit.setChecked(p.autoSplit());
        autoSplit.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoSplit", on).apply());

        Switch autoPlay = findViewById(R.id.swAutoPlay);
        autoPlay.setChecked(p.autoPlay());
        autoPlay.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean("autoPlay", on).apply());

        // Arabaya bağlanınca müzik sesi
        Switch carVol = findViewById(R.id.swCarVol);
        View volRow = findViewById(R.id.carVolRow);
        android.widget.SeekBar volSeek = findViewById(R.id.carVolSeek);
        TextView volText = findViewById(R.id.carVolText);
        carVol.setChecked(p.carVolOn());
        volRow.setVisibility(p.carVolOn() ? View.VISIBLE : View.GONE);
        volSeek.setProgress(p.carVol());
        volText.setText("%" + p.carVol());
        carVol.setOnCheckedChangeListener((b, on) -> {
            p.edit().putBoolean("carVolOn", on).apply();
            volRow.setVisibility(on ? View.VISIBLE : View.GONE);
        });
        volSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int v, boolean user) {
                volText.setText("%" + v);
                if (user) p.edit().putInt("carVol", v).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });

        findViewById(R.id.carBtn).setOnClickListener(v -> pickCar());
        findViewById(R.id.overlayBtn).setOnClickListener(v -> safeStart(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.notifBtn).setOnClickListener(v -> askNotifications());
        findViewById(R.id.xiaomiBtn).setOnClickListener(v -> openXiaomiPermissions());
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Tanıtım ekranından doğrudan bir işe gelindiyse onu başlat
        String todo = getIntent().getStringExtra(EXTRA_DO);
        if ("car".equals(todo)) pickCar();
        else if ("place".equals(todo) && Places.count(this) < Places.MAX) addPlace(Places.count(this) + 1);
        else if ("call".equals(todo)) {
            int n = compactCalls();
            if (n < MAX_CALLS) pickContact(n + 1);
        }
        findViewById(R.id.listenerBtn).setOnClickListener(v ->
                safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        findViewById(R.id.appInfoBtn).setOnClickListener(v -> safeStart(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshQuiet();
        renderApps();
        Places.cleanup(this);
        renderPlaces();
    }

    private void askQuietAccess() {
        Toast.makeText(this, "Listede Sürüş Ekranı'nı bul ve izin ver", Toast.LENGTH_LONG).show();
        safeStart(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void refreshQuiet() {
        TextView t = findViewById(R.id.quietStatus);
        boolean need = Quiet.enabled(this) && !Quiet.hasAccess(this);
        t.setText(need ? "⚠ İzin gerekli (Rahatsız Etmeyin erişimi). Vermek için dokun."
                : "Uygulama ekrandayken telefonun yukarıdan inen bildirimleri çıkmaz, mesajlar bildirim kutusuna ve alttaki şeride gelir. "
                + "Aramalar, alarmlar ve müzik etkilenmez. Uygulamadan çıkınca her şey eskisi gibi olur.");
        t.setTextColor(getColor(need ? R.color.low : R.color.muted));
    }

    // ---------- Hızlı rotalar ----------

    private final java.util.concurrent.ExecutorService io =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void renderPlaces() {
        android.widget.LinearLayout list = findViewById(R.id.placesList);
        list.removeAllViews();
        int n = Places.count(this);
        for (int i = 1; i <= n; i++) {
            final int slot = i;
            String label = Places.label(this, i);
            list.addView(itemRow(Places.iconRes(Places.icon(this, i)), R.color.amber,
                    Places.name(this, i) + (label.isEmpty() ? "" : "  ·  " + shortLabel(label)),
                    // simgeye dokun: simge seç
                    v -> chooseIcon(Places.iconIndex(Places.icon(this, slot)), key -> {
                        Places.setIcon(this, slot, key);
                        renderPlaces();
                    }),
                    v -> askName(Places.name(this, slot), name -> {
                        Places.setName(this, slot, name);
                        renderPlaces();
                    }),
                    v -> {
                        Places.remove(this, slot);
                        renderPlaces();
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    }));
        }
        if (n < Places.MAX) {
            android.widget.Button add = placeButton();
            add.setText("Hızlı rota ekle");
            setIconOn(add, R.drawable.ic_plus, R.color.muted);
            add.setOnClickListener(v -> addPlace(n + 1));
            list.addView(add);
        }
    }

    /** Liste satırı: [simge] ad ............ İsim ver   Sil  — satırın kendisine dokunmak bir şey yapmaz. */
    private View itemRow(int iconRes, int iconColor, String text, View.OnClickListener onIcon,
                         View.OnClickListener onRename, View.OnClickListener onDelete) {
        android.graphics.drawable.Drawable dr = getDrawable(iconRes).mutate();
        dr.setTint(getColor(iconColor));
        return itemRow(dr, text, onIcon, "İsim ver", onRename, "Sil", onDelete);
    }

    private View itemRow(android.graphics.drawable.Drawable icon, String text, View.OnClickListener onIcon,
                         String aText, View.OnClickListener onA, String bText, View.OnClickListener onB) {
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.btn_outline);
        row.setPadding(Math.round(8 * d), 0, Math.round(4 * d), 0);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(60 * d));
        lp.topMargin = Math.round(10 * d);
        row.setLayoutParams(lp);

        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageDrawable(icon);
        int pad = Math.round(9 * d);
        ic.setPadding(pad, pad, pad, pad);
        if (onIcon != null) {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
            ic.setBackgroundResource(tv.resourceId);
            ic.setOnClickListener(onIcon);
            ic.setContentDescription("Simgeyi değiştir");
        }
        row.addView(ic, new android.widget.LinearLayout.LayoutParams(Math.round(44 * d), Math.round(44 * d)));

        TextView name = new TextView(this);
        name.setText(text);
        name.setTextSize(15);
        name.setTextColor(getColor(R.color.fg));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        android.widget.LinearLayout.LayoutParams nlp = new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.setMarginStart(Math.round(6 * d));
        row.addView(name, nlp);

        TextView rename = smallBtn(aText, Math.round(76 * d));
        rename.setTextSize(14);
        rename.setTextColor(getColor(R.color.amber));
        rename.setOnClickListener(onA);
        row.addView(rename);

        TextView del = smallBtn(bText, Math.round(56 * d));
        del.setTextSize(14);
        del.setTextColor(getColor(R.color.low));
        del.setOnClickListener(onB);
        row.addView(del);
        return row;
    }

    private android.widget.Button placeButton() {
        android.widget.Button b = new android.widget.Button(this, null, 0, R.style.OutlineBtn);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(60 * getResources().getDisplayMetrics().density));
        lp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(lp);
        b.setBackgroundResource(R.drawable.btn_outline);
        b.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        b.setPadding(pad, 0, pad, 0);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextSize(15);
        return b;
    }

    private void setIconOn(TextView b, int res, int color) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        int sz = Math.round(26 * getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, sz, sz);
        d.setTint(getColor(color));
        b.setCompoundDrawablesRelative(d, null, null, null);
        b.setCompoundDrawablePadding(Math.round(14 * getResources().getDisplayMetrics().density));
    }

    private static String shortLabel(String l) {
        return l.length() > 40 ? l.substring(0, 40) + "…" : l;
    }

    /** Yeni rota: önce simge, sonra ad, sonra haritada konum. */
    private void addPlace(int slot) {
        chooseIcon(-1, key -> {
            String def = Places.ICON_NAMES[Places.iconIndex(key)];
            askName(def, name -> {
                Places.setIcon(this, slot, key);
                Places.setName(this, slot, name);
                chooseLocation(slot);
            });
        });
    }

    private void editPlace(int slot) {
        String[] opts = {"Konumunu değiştir", "Adını değiştir", "Simgesini değiştir", "Sil"};
        new AlertDialog.Builder(this)
                .setTitle(Places.name(this, slot))
                .setItems(opts, (d, w) -> {
                    if (w == 0) chooseLocation(slot);
                    else if (w == 1) askName(Places.name(this, slot), name -> {
                        Places.setName(this, slot, name);
                        renderPlaces();
                    });
                    else if (w == 2) chooseIcon(Places.iconIndex(Places.icon(this, slot)), key -> {
                        Places.setIcon(this, slot, key);
                        renderPlaces();
                    });
                    else {
                        Places.remove(this, slot);
                        renderPlaces();
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void chooseIcon(int current, java.util.function.Consumer<String> done) {
        String[] emoji = {"🏠", "💼", "🎓", "🛒", "❤️", "⭐", "🏋️", "📍"};
        String[] items = new String[Places.ICON_NAMES.length];
        for (int k = 0; k < items.length; k++) items[k] = emoji[k] + "   " + Places.ICON_NAMES[k];
        new AlertDialog.Builder(this)
                .setTitle("Simge seç")
                .setSingleChoiceItems(items, current, (d, w) -> {
                    d.dismiss();
                    done.accept(Places.ICON_KEYS[w]);
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void askName(String def, java.util.function.Consumer<String> done) {
        askName("Rotanın adı", def, done);
    }

    private void askName(String title, String def, java.util.function.Consumer<String> done) {
        EditText e = new EditText(this);
        e.setText(def);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Tamam", (d, w) -> {
                    String v = e.getText().toString().trim();
                    done.accept(v.isEmpty() ? def : v);
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        e.requestFocus();
    }

    /** Konumu haritada işaretle ya da bulunduğun yeri kullan. */
    private void chooseLocation(int slot) {
        new AlertDialog.Builder(this)
                .setTitle("Konum")
                .setItems(new String[]{"Haritada işaretle", "Şu anki konumum"}, (d, w) -> {
                    if (w == 0) {
                        startActivity(new Intent(this, MapPickActivity.class).putExtra(MapPickActivity.EXTRA_SLOT, slot));
                    } else {
                        placeFromHere(slot);
                    }
                })
                .setOnCancelListener(d -> { Places.cleanup(this); renderPlaces(); })
                .show();
    }

    private android.location.Location lastLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
        try {
            android.location.Location g = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            android.location.Location n = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (g == null || (n != null && n.getTime() > g.getTime() + 60000)) return n;
            return g;
        } catch (SecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private void placeFromHere(int slot) {
        android.location.Location l = lastLocation();
        if (l == null) {
            Toast.makeText(this, "Konum bulunamadı. Haritada işaretle'yi kullan.", Toast.LENGTH_LONG).show();
            Places.cleanup(this);
            renderPlaces();
            return;
        }
        double lat = l.getLatitude(), lon = l.getLongitude();
        io.execute(() -> {
            String label = "";
            try {
                List<android.location.Address> r = new android.location.Geocoder(this, new java.util.Locale("tr", "TR"))
                        .getFromLocation(lat, lon, 1);
                if (r != null && !r.isEmpty() && r.get(0).getAddressLine(0) != null) label = r.get(0).getAddressLine(0);
            } catch (Exception ignored) {
            }
            String c = String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon);
            String fl = label;
            runOnUiThread(() -> {
                Places.setLocation(this, slot, c, fl);
                renderPlaces();
                Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ---------- Uygulama kısayolları ----------

    private void renderApps() {
        android.widget.LinearLayout list = findViewById(R.id.appsList);
        list.removeAllViews();
        android.content.SharedPreferences sp = getSharedPreferences("surus", MODE_PRIVATE);
        String saved = sp.getString("favApps", "");
        java.util.List<String> keys = new java.util.ArrayList<>();
        PackageManager pm = getPackageManager();
        if (!saved.isEmpty()) for (String k : saved.split("\\|")) {
            int slash = k.indexOf('/');
            if (slash <= 0) continue;
            try {
                android.content.pm.ActivityInfo info = pm.getActivityInfo(
                        new ComponentName(k.substring(0, slash), k.substring(slash + 1)), 0);
                if (keys.size() >= Shortcuts.MAX) break;
                keys.add(k);
                list.addView(itemRow(info.loadIcon(pm), info.loadLabel(pm).toString(), null,
                        "Değiştir", v -> startActivity(new Intent(this, AppsActivity.class)
                                .putExtra(AppsActivity.EXTRA_EDIT, true)
                                .putExtra(AppsActivity.EXTRA_REPLACE, k)),
                        "Kaldır", v -> {
                            java.util.List<String> all = new java.util.ArrayList<>(
                                    java.util.Arrays.asList(sp.getString("favApps", "").split("\\|")));
                            all.remove(k);
                            sp.edit().putString("favApps", android.text.TextUtils.join("|", all)).apply();
                            renderApps();
                        }));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (keys.size() < Shortcuts.MAX) {
            android.widget.Button add = placeButton();
            add.setText("Uygulama ekle");
            setIconOn(add, R.drawable.ic_plus, R.color.muted);
            add.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)
                    .putExtra(AppsActivity.EXTRA_EDIT, true)));
            list.addView(add);
        }
    }

    // ---------- Ana ekran düğme düzeni ----------

    private void renderLayout() {
        android.widget.LinearLayout list = findViewById(R.id.layoutList);
        list.removeAllViews();
        java.util.List<Layout.Entry> l = Layout.load(this);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            Layout.Entry e = l.get(i);
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.widget.LinearLayout.LayoutParams rlp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Math.round(10 * d);
            row.setLayoutParams(rlp);

            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(Layout.name(e.key));
            cb.setTextSize(15);
            cb.setTextColor(getColor(R.color.fg));
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.amber)));
            cb.setChecked(e.on);
            if ("settings".equals(e.key)) cb.setEnabled(false); // ayarlar hep açık
            cb.setOnCheckedChangeListener((b, on) -> { e.on = on; Layout.save(this, l); });
            row.addView(cb, new android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            android.widget.TextView rowBtn = smallBtn(e.row == 1 ? "Üst\nsıra" : "Alt\nsıra", Math.round(56 * d));
            rowBtn.setTextSize(15);
            rowBtn.setLineSpacing(0, 0.9f);
            rowBtn.setOnClickListener(v -> {
                e.row = e.row == 1 ? 2 : 1;
                Layout.save(this, l);
                renderLayout();
            });
            rowBtn.setTextColor(getColor(R.color.amber));
            rowBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            row.addView(rowBtn);

            android.widget.TextView up = smallBtn("▲", Math.round(40 * d));
            up.setEnabled(i > 0);
            up.setAlpha(i > 0 ? 1f : 0.3f);
            up.setOnClickListener(v -> { java.util.Collections.swap(l, idx, idx - 1); Layout.save(this, l); renderLayout(); });
            row.addView(up);

            android.widget.TextView down = smallBtn("▼", Math.round(40 * d));
            down.setEnabled(i < l.size() - 1);
            down.setAlpha(i < l.size() - 1 ? 1f : 0.3f);
            down.setOnClickListener(v -> { java.util.Collections.swap(l, idx, idx + 1); Layout.save(this, l); renderLayout(); });
            row.addView(down);

            list.addView(row);
            // Açıklama: satırın altında, tüm genişliği kullanır
            TextView desc = new TextView(this);
            desc.setText(Layout.desc(e.key));
            desc.setTextSize(12);
            desc.setTextColor(getColor(R.color.muted));
            desc.setPadding(Math.round(4 * d), 0, Math.round(4 * d), Math.round(4 * d));
            list.addView(desc);
        }
    }

    /** Çerçevesiz, yazısı ortalı küçük tuş (Üst/Alt, ▲, ▼). */
    private android.widget.TextView smallBtn(String text, int width) {
        android.widget.TextView b = new android.widget.TextView(this);
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(width, Math.round(44 * d));
        lp.setMarginStart(Math.round(4 * d));
        b.setLayoutParams(lp);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId); // dokununca hafif dalga, çerçeve yok
        b.setGravity(android.view.Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setTextSize(17);
        b.setTextColor(getColor(R.color.fg));
        b.setClickable(true);
        b.setFocusable(true);
        b.setText(text);
        return b;
    }

    // ---------- Hızlı arama ----------

    private static final int MAX_CALLS = 5;

    private android.content.SharedPreferences calls() {
        return getSharedPreferences("surus", MODE_PRIVATE);
    }

    /** Boş kalan yerleri kaydır: 1..n dolu olsun. */
    private int compactCalls() {
        android.content.SharedPreferences sp = calls();
        java.util.List<String[]> l = new java.util.ArrayList<>();
        for (int i = 1; i <= MAX_CALLS; i++) {
            String num = sp.getString("call" + i + "Num", "");
            if (!num.isEmpty()) l.add(new String[]{sp.getString("call" + i + "Name", ""), num,
                    sp.getString("call" + i + "Label", "")});
        }
        android.content.SharedPreferences.Editor e = sp.edit();
        for (int i = 1; i <= MAX_CALLS; i++) {
            if (i <= l.size()) e.putString("call" + i + "Name", l.get(i - 1)[0]).putString("call" + i + "Num", l.get(i - 1)[1])
                    .putString("call" + i + "Label", l.get(i - 1)[2]);
            else e.remove("call" + i + "Name").remove("call" + i + "Num").remove("call" + i + "Label");
        }
        e.apply();
        return l.size();
    }

    private void setupCalls() {
        android.widget.LinearLayout list = findViewById(R.id.callsList);
        list.removeAllViews();
        int n = compactCalls();
        android.content.SharedPreferences sp = calls();
        for (int i = 1; i <= n; i++) {
            final int slot = i;
            String name = sp.getString("call" + i + "Name", ""), num = sp.getString("call" + i + "Num", "");
            String lbl = sp.getString("call" + i + "Label", "");
            list.addView(itemRow(R.drawable.ic_phone, R.color.teal,
                    (lbl.isEmpty() ? "" : lbl + "  ·  ") + (name.isEmpty() ? num : name),
                    null,
                    v -> askCallLabel(slot),
                    v -> {
                        sp.edit().remove("call" + slot + "Name").remove("call" + slot + "Num")
                                .remove("call" + slot + "Label").apply();
                        setupCalls();
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    }));
        }
        if (n < MAX_CALLS) {
            android.widget.Button add = placeButton();
            add.setText("Hızlı arama ekle");
            setIconOn(add, R.drawable.ic_plus, R.color.muted);
            add.setOnClickListener(v -> pickContact(n + 1));
            list.addView(add);
        }
    }

    private void pickContact(int slot) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_PICK,
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_CALL + slot - 1);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Rehber açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    /** Ana ekranda görünecek kısa ad, ör. "Annem", "Eşim". */
    private void askCallLabel(int slot) {
        android.content.SharedPreferences sp = calls();
        String cur = sp.getString("call" + slot + "Label", "");
        if (cur.isEmpty()) {
            String n = sp.getString("call" + slot + "Name", "").trim();
            cur = n.isEmpty() ? "" : n.split("\\s+")[0];
        }
        askName("Ekranda görünecek ad (ör. Annem)", cur, v -> {
            sp.edit().putString("call" + slot + "Label", v).apply();
            setupCalls();
        });
    }

    private void editCall(int slot) {
        android.content.SharedPreferences sp = calls();
        String name = sp.getString("call" + slot + "Name", "");
        new AlertDialog.Builder(this)
                .setTitle(name.isEmpty() ? sp.getString("call" + slot + "Num", "") : name)
                .setItems(new String[]{"Ekranda görünen adı değiştir", "Başka kişi seç", "Sil"}, (d, w) -> {
                    if (w == 0) askCallLabel(slot);
                    else if (w == 1) pickContact(slot);
                    else {
                        sp.edit().remove("call" + slot + "Name").remove("call" + slot + "Num")
                                .remove("call" + slot + "Label").apply();
                        setupCalls();
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req < PICK_CALL || req >= PICK_CALL + MAX_CALLS
                || res != RESULT_OK || data == null || data.getData() == null) return;
        String key = "call" + (req - PICK_CALL + 1);
        String[] cols = {android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (android.database.Cursor c = getContentResolver().query(data.getData(), cols, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0) == null ? "" : c.getString(0);
                String num = c.getString(1) == null ? "" : c.getString(1).replaceAll("[^0-9+]", "");
                getSharedPreferences("surus", MODE_PRIVATE).edit()
                        .putString(key + "Name", name).putString(key + "Num", num)
                        .remove(key + "Label").apply();
                int slot = req - PICK_CALL + 1;
                getWindow().getDecorView().post(() -> askCallLabel(slot));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Kişi okunamadı", Toast.LENGTH_SHORT).show();
        }
        setupCalls();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void refreshStatus() {
        ((TextView) findViewById(R.id.carStatus)).setText(
                p.carName().isEmpty() ? "Henüz seçilmedi" : "Seçili: " + p.carName());
        ((TextView) findViewById(R.id.overlayStatus)).setText(
                Settings.canDrawOverlays(this) ? "Verildi ✓" : "Verilmedi. Otomatik açılma için gerekli.");
        NotificationManager lnm = getSystemService(NotificationManager.class);
        boolean listener = lnm != null && lnm.isNotificationListenerAccessGranted(
                new ComponentName(this, MediaListener.class));
        ((TextView) findViewById(R.id.listenerStatus)).setText(
                listener ? "Verildi ✓" : "Verilmedi. Çalan şarkı ve yön bilgisi için gerekli.");
        ((TextView) findViewById(R.id.notifStatus)).setText(
                notificationsReady() ? "Hazır ✓" : "Kapalı. Yedek bildirim için gerekli.");
    }

    // ---------- Araba seçimi ----------

    private void pickCar() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null) {
            Toast.makeText(this, "Bluetooth bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ad.isEnabled()) {
            Toast.makeText(this, "Önce Bluetooth'u aç", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<BluetoothDevice> bonded = ad.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "Eşleşmiş cihaz yok. Önce telefonu arabayla eşleştir.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<BluetoothDevice> list = new ArrayList<>(bonded);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i).getName();
            names[i] = (n == null || n.isEmpty()) ? list.get(i).getAddress() : n;
        }
        new AlertDialog.Builder(this)
                .setTitle("Araban hangisi?")
                .setItems(names, (dialog, which) -> {
                    p.edit()
                            .putString("carAddr", list.get(which).getAddress())
                            .putString("carName", names[which])
                            .apply();
                    refreshStatus();
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_BT) {
            if (granted) pickCar();
            else Toast.makeText(this, "Arabayı seçmek için Bluetooth izni gerekli", Toast.LENGTH_LONG).show();
        } else if (code == REQ_NOTIF) {
            askFullScreenIfNeeded();
            refreshStatus();
        }
    }

    // ---------- İzinler ----------

    private boolean notificationsReady() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true;
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            askFullScreenIfNeeded();
        }
    }

    private void askFullScreenIfNeeded() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                safeStart(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void openXiaomiPermissions() {
        Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", getPackageName());
        try {
            startActivity(miui);
        } catch (ActivityNotFoundException | SecurityException e) {
            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }


    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Bu ayar ekranı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }
}
