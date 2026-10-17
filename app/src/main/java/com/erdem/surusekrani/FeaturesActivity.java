package com.erdem.surusekrani;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** "Neler yapabilirim?": bütün özellikler kısa açıklamalarıyla, tek sayfada. */
public class FeaturesActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = Ui.page(this, "Neler yapabilirim?");

        Ui.section(this, box, "Dokunmadan");
        Ui.item(this, box, "👋 El sallayarak şarkı geç",
                "Elini telefonun üst kısmının (ön kameranın) üzerinden bir kez geçir: sonraki şarkı. "
                        + "Art arda iki kez geçir: durdur / devam et.");
        Ui.item(this, box, "↔️ Kaydırarak şarkı değiştir",
                "Müzik kutusunu sola kaydır: sonraki şarkı. Sağa kaydır: önceki şarkı.");
        Ui.item(this, box, "🎙️ Sesli not",
                "Sesli komut tuşuna basılı tut ve söyle (ör. \"yarın faturayı öde\"). Yazıya çevrilip kaydedilir, "
                        + "arabadan inince hatırlatılır. Notların: bu sayfanın altında.");

        Ui.section(this, box, "Yolda");
        Ui.item(this, box, "🗺️ Hızlı rotalar", "Ev, iş gibi yerlere tek dokunuşla rota. Harita yandaki Yandex/Google'da açılır.");
        Ui.item(this, box, "📞 Hızlı arama", "Dokun: 3 saniye sonra arar (iptal edebilirsin). Basılı tut: WhatsApp ile konumunu gönderir.");
        Ui.item(this, box, "🚗 Arabam nerede", "Kısaca basılı tut: park yerin kaydedilir, simgede turkuaz P belirir. Dokun: ne zaman park ettiğin, ne kadar uzakta olduğu, notun; yürüyerek git, paylaş ya da sil.");

        Ui.section(this, box, "Bilgi");
        Ui.item(this, box, "🌦️ Hava durumu", "Şimdi ve 1 saat sonrası. Hava kötüleşecekse sağ taraf sarı, turuncu ya da kırmızı çerçeveli. "
                + "Hızlı rotayla gidince varış yerinin havası.");
        Ui.item(this, box, "💬 Bildirimler", "WhatsApp, SMS ve cevapsız aramalar. Yeni mesaj ekranın altında birkaç saniye görünür. Kutuya dokun: mesajların tamamı. Basılı tut: uygulamaya geç.");
        Ui.item(this, box, "🔋 Telefon durumu", "Çekim, Bluetooth, şarj. Şarjdayken şimşek turuncu → sarı → yeşil dolar. Arabadayken şarj olmuyor ve şarj azsa şimşek kırmızı yanıp söner.");

        Ui.section(this, box, "Kendiliğinden");
        Ui.item(this, box, "🔵 Arabaya binince", "Araç Bluetooth'una bağlanınca uygulama açılır, müzik devam eder, ses seviyen ayarlanır.");
        Ui.item(this, box, "🌙 Gece modu", "Güneş batınca ekran kendiliğinden kısılır.");

        Ui.button(this, box, "📝 Sesli notlarım", false).setOnClickListener(v -> {
            java.util.List<String> l = Notes.list(this);
            if (l.isEmpty()) {
                android.widget.Toast.makeText(this, "Henüz sesli not yok", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            new android.app.AlertDialog.Builder(this).setTitle("Sesli notların")
                    .setItems(l.toArray(new String[0]), null)
                    .setPositiveButton("Kapat", null)
                    .setNegativeButton("Hepsini sil", (d, w) -> Notes.clear(this))
                    .show();
        });
        Ui.button(this, box, "Tanıtımı yeniden göster", false).setOnClickListener(v ->
                startActivity(new Intent(this, OnboardingActivity.class)));
        Ui.button(this, box, "Geri dön", true).setOnClickListener(v -> finish());
    }
}
