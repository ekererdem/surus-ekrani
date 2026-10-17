package com.erdem.surusekrani;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;

/** İlk açılışta kısa kurulum: hoş geldin → izinler → araba → yerler. Her adım atlanabilir. */
public class OnboardingActivity extends Activity {
    static final String DONE = "onboarded";
    private int step;

    static boolean needed(Activity a) {
        return !a.getSharedPreferences("surus", MODE_PRIVATE).getBoolean(DONE, false);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        MainActivity.sOwnScreen = true;
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // izin ekranlarından dönünce ✓ işaretleri güncellensin
    }

    private void render() {
        switch (step) {
            case 0: welcome(); break;
            case 1: permissions(); break;
            case 2: car(); break;
            default: places(); break;
        }
    }

    private void next() { step++; render(); }

    private void finishSetup() {
        getSharedPreferences("surus", MODE_PRIVATE).edit().putBoolean(DONE, true).apply();
        finish();
    }

    private void footer(LinearLayout box, String nextText) {
        Ui.button(this, box, nextText, true).setOnClickListener(v -> { if (step >= 3) finishSetup(); else next(); });
        Ui.note(this, box, "Adım " + (step + 1) + " / 4").setGravity(android.view.Gravity.CENTER);
        if (step < 3) {
            Button skip = Ui.button(this, box, "Tanıtımı atla", false);
            skip.setOnClickListener(v -> finishSetup());
        }
    }

    private void welcome() {
        LinearLayout box = Ui.page(this, "Hoş geldin 👋");
        Ui.note(this, box, "Arabadayken telefonu tek ekrandan kullanman için: harita yanda, müzik, aramalar ve "
                + "rotalar büyük tuşlarla bu tarafta.");
        Ui.section(this, box, "En çok işine yarayacaklar");
        Ui.item(this, box, "🔵 Arabaya binince kendiliğinden açılır", "Araç Bluetooth'unu bir kez seçmen yeterli.");
        Ui.item(this, box, "👋 El sallayarak şarkı geç", "Elini telefonun üst kısmının üzerinden geçir.");
        Ui.item(this, box, "🚗 Arabam nerede", "Arabadan inmeden park tuşuna basılı tut, sonra kolayca bul.");
        footer(box, "Başlayalım");
    }

    private void permissions() {
        LinearLayout box = Ui.page(this, "İzinler");
        Ui.note(this, box, "Sırayla dokun. Hepsi isteğe bağlı, sonra Ayarlar'dan da verebilirsin.");
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Ui.button(this, box, (loc ? "✓ " : "") + "Konum (hava, park yeri, rotalar)", false).setOnClickListener(v ->
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT}, 1));
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean listener = nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(this, MediaListener.class));
        Ui.button(this, box, (listener ? "✓ " : "") + "Bildirim erişimi (şarkı bilgisi, mesajlar)", false).setOnClickListener(v ->
                safe(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        boolean overlay = Settings.canDrawOverlays(this);
        Ui.button(this, box, (overlay ? "✓ " : "") + "Arabaya binince otomatik açılma", false).setOnClickListener(v ->
                safe(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        if (Build.VERSION.SDK_INT >= 33) {
            boolean post = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            Ui.button(this, box, (post ? "✓ " : "") + "Hatırlatmalar (sesli notlar)", false).setOnClickListener(v ->
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2));
        }
        Ui.note(this, box, "Bildirim erişimi listesinde uygulama gri görünüyorsa: Ayarlar → İzinler bölümündeki adımları izle.");
        footer(box, "Devam");
    }

    private void car() {
        LinearLayout box = Ui.page(this, "Araban");
        String name = new Prefs(this).carName();
        Ui.note(this, box, name.isEmpty()
                ? "Arabanın Bluetooth'unu seç; telefon arabaya bağlanınca uygulama kendiliğinden açılsın, müzik devam etsin."
                : "Seçili araç: " + name);
        Ui.button(this, box, name.isEmpty() ? "Arabayı seç" : "Başka araç seç", false).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_DO, "car")));
        footer(box, "Devam");
    }

    private void places() {
        LinearLayout box = Ui.page(this, "Sık gittiğin yerler");
        Ui.note(this, box, "Ev ve işini ekle, ana ekranda tek dokunuşla rota başlasın. Hızlı aramaya da annen, eşin gibi kişileri ekleyebilirsin.");
        int n = Places.count(this);
        Ui.button(this, box, n > 0 ? "✓ " + n + " yer eklendi · bir tane daha ekle" : "Hızlı rota ekle", false).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_DO, "place")));
        Ui.button(this, box, "Hızlı arama ekle", false).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_DO, "call")));
        footer(box, "Bitir");
    }

    private void safe(Intent i) {
        try { startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        render();
    }

    @Override
    public void onBackPressed() {
        if (step > 0) { step--; render(); } else super.onBackPressed();
    }
}
