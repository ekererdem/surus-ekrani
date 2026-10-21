package com.erdem.surusekrani;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ayarlar › Hakkında › İpuçları: ana ekrandan küçük görüntülerle kısa kısa ipuçları. */
public class TipsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        Accent.apply(this);
        super.onCreate(b);
        LinearLayout box = Ui.page(this, "İpuçları");
        tip(box, R.drawable.tip_music, "Şarkı değiştir",
                "Müzik kutusunu sola kaydır: sonraki şarkı. Sağa kaydır: önceki şarkı.");
        tip(box, R.drawable.tip_park, "Park yerini kaydet",
                "Arabadan inmeden araba tuşuna basılı tut. Köşede P belirir; sonra dokununca arabanın yerini ve yol tarifini görürsün.");
        tip(box, R.drawable.tip_mic, "Sesli not",
                "Mikrofon tuşuna basılı tut ve aklına geleni söyle. Arabadan inince sana hatırlatılır.");
        tip(box, R.drawable.tip_call, "Konumunu gönder",
                "Kişi tuşuna basılı tut: WhatsApp'tan anlık konumunu gönderirsin. Dokunursan 3 saniye sonra arar.");
        tip(box, R.drawable.tip_notif, "Mesajlar",
                "Bildirim kutusuna dokun: mesajları okursun. Basılı tut: doğrudan sohbete geçersin.");
        tip(box, R.drawable.tip_weather, "Saatlik hava",
                "Hava durumuna dokun: önümüzdeki 6 saatin havası görünür. Hava bozacaksa kutu renkli çerçeveyle uyarır.");
        tip(box, R.drawable.tip_place, "Tek dokunuşla rota",
                "Ev gibi kayıtlı yerlere dokun: haritada rota hemen başlar, adres yazmana gerek kalmaz.");
        tip(box, R.drawable.tip_status, "Araba bağlanmadıysa",
                "Gösterge paneline dokun: Kopilot arabana bağlanmayı dener.");
        tip(box, 0, "👋 El salla",
                "Elini ön kameranın üzerinden bir kez geçir: şarkı geçer. Ne yapacağını Ayarlar › Uzaktan kontrol'den seçebilirsin.");
    }

    private void tip(LinearLayout box, int img, String head, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.btn_outline);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 14));
        if (img != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(img);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Ui.dp(this, 190));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundResource(R.drawable.tip_img_bg);
            iv.setClipToOutline(true);
            iv.setContentDescription(head);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            il.bottomMargin = Ui.dp(this, 10);
            card.addView(iv, il);
        }
        TextView h = new TextView(this);
        h.setText(head);
        h.setTextSize(16);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(getColor(R.color.fg));
        card.addView(h);
        TextView t = new TextView(this);
        t.setText(body);
        t.setTextSize(14);
        t.setLineSpacing(0, 1.1f);
        t.setTextColor(getColor(R.color.muted));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 4);
        card.addView(t, tl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        box.addView(card, lp);
    }
}
