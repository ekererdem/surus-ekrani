package com.erdem.surusekrani;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Ana ekran düğmelerinin düzeni: hangi düğme açık, hangi satırda (üst/alt) ve hangi sırada.
 * Ayarlar'daki düzenleyici yazar, ana ekran okur.
 * Kayıt biçimi: "weather:1,notif:1,!assist:2,..."  (başında ! olan kapalı)
 */
final class Layout {
    static final class Entry {
        final String key;
        int row;
        boolean on;
        Entry(String key, int row, boolean on) { this.key = key; this.row = row; this.on = on; }
    }

    // Ayarlar düğmesi artık en alttaki uygulama kısayollarının en sağında (Shortcuts); burada yok.
    static final String[] KEYS = {"weather", "notif", "status", "assist", "calls", "missed", "places", "park"};
    private static final int[] DEF_ROW = {1, 1, 1, 2, 2, 2, 2, 2};

    private Layout() {}

    static String name(String key) {
        switch (key) {
            case "weather": return "Hava durumu";
            case "notif": return "Bildirimler";
            case "status": return "Gösterge paneli";
            case "assist": return "Sesli komut";
            case "calls": return "Hızlı aramalar";
            case "missed": return "Cevapsız arama";
            case "places": return "Hızlı rotalar";
            case "park": return "Arabam nerede";
            case "settings": return "Ayarlar";
            default: return key;
        }
    }

    /**
     * Ayarlar'da düğme adının altındaki kısa açıklama: ne gösterdiği, sonra dokununca / basılı tutunca ne olduğu.
     * Daha fazlası Ayarlar › Neler yapabilirim? sayfasında.
     */
    static String desc(String key) {
        switch (key) {
            case "weather": return "Şimdi ve 1 saat sonrasının havasını gösterir. Hava bozacaksa renkli çerçeveyle "
                    + "önceden uyarır. Dokunursan önümüzdeki 6 saatin havasını görürsün.";
            case "notif": return "Seçtiğin uygulamaların mesajlarını ve cevapsız aramaları bir arada gösterir. "
                    + "Dokunursan mesajları okursun, basılı tutarsan doğrudan sohbete geçersin.";
            case "status": return "Çekimi, Bluetooth'u ve şarjı gösterir. Arabadayken telefon şarj olmuyorsa şimşek "
                    + "kırmızı yanıp söner. Araban bağlı değilse dokunursan Kopilot bağlamayı dener.";
            case "assist": return "Dokunursan sesli asistan açılır; elini direksiyondan ayırmadan arar, mesaj "
                    + "gönderirsin. Basılı tutarsan sesli not alırsın, arabadan inince sana hatırlatılır.";
            case "calls": return "En sık aradığın kişiler. Dokunursan 3 saniye sonra arama başlar, bu sürede iptal "
                    + "edebilirsin. Basılı tutarsan WhatsApp'tan konumunu gönderirsin.";
            case "missed": return "Cevapsız arama olunca arayanın adıyla görünür. Dokunursan geri ararsın, basılı "
                    + "tutarsan düğme kalkar.";
            case "places": return "Ev, iş gibi sık gittiğin yerler. Dokunursan haritada rota hemen başlar, adres "
                    + "yazmana gerek kalmaz.";
            case "park": return "Arabadan inmeden basılı tutarsan park ettiğin yer kaydedilir, simgenin köşesinde P "
                    + "belirir. Dokunursan arabanın ne kadar uzakta olduğunu görür, yürüyerek yol tarifi alır ya da "
                    + "konumu paylaşırsın.";
            case "settings": return "Gizlenemez ama istediğin yere taşıyabilirsin.";
            default: return "";
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("surus", Context.MODE_PRIVATE);
    }

    static List<Entry> load(Context c) {
        List<Entry> l = new ArrayList<>();
        String s = sp(c).getString("layout", "");
        for (String part : s.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            boolean on = !part.startsWith("!");
            if (!on) part = part.substring(1);
            int colon = part.indexOf(':');
            String key = colon < 0 ? part : part.substring(0, colon);
            int row = colon < 0 ? 2 : ("1".equals(part.substring(colon + 1)) ? 1 : 2);
            if (index(key) >= 0 && find(l, key) == null) l.add(new Entry(key, row, on));
        }
        // Eksik olanları varsayılan yerleriyle ekle (eski sürümden geçiş dahil); yeni düğme,
        // listede kendinden önce gelen düğmenin hemen arkasına girer
        for (int i = 0; i < KEYS.length; i++) {
            if (find(l, KEYS[i]) != null) continue;
            boolean on = !"assist".equals(KEYS[i]) || sp(c).getBoolean("assistBtn", true);
            Entry prev = i > 0 ? find(l, KEYS[i - 1]) : null;
            Entry e = new Entry(KEYS[i], DEF_ROW[i], on);
            if (prev != null) l.add(l.indexOf(prev) + 1, e); else l.add(e);
        }
        return l;
    }

    static void save(Context c, List<Entry> l) {
        StringBuilder b = new StringBuilder();
        for (Entry e : l) {
            if (b.length() > 0) b.append(',');
            if (!e.on) b.append('!');
            b.append(e.key).append(':').append(e.row);
        }
        sp(c).edit().putString("layout", b.toString()).apply();
    }

    static void reset(Context c) {
        sp(c).edit().remove("layout").putBoolean("assistBtn", true).apply();
    }

    static Entry find(List<Entry> l, String key) {
        for (Entry e : l) if (e.key.equals(key)) return e;
        return null;
    }

    private static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }
}
