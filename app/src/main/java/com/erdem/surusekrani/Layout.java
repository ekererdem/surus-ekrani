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
    static final String[] KEYS = {"weather", "notif", "status", "assist", "calls", "places", "park"};
    private static final int[] DEF_ROW = {1, 1, 1, 2, 2, 2, 2};

    private Layout() {}

    static String name(String key) {
        switch (key) {
            case "weather": return "Hava durumu";
            case "notif": return "Bildirimler";
            case "status": return "Gösterge paneli";
            case "assist": return "Sesli komut";
            case "calls": return "Hızlı aramalar";
            case "places": return "Hızlı rotalar";
            case "park": return "Arabam nerede";
            case "settings": return "Ayarlar";
            default: return key;
        }
    }

    /** Ayarlar'da düğme adının altındaki kısa açıklama. */
    static String desc(String key) {
        switch (key) {
            case "weather": return "Şu anki havayı ve bir saat sonrasını yan yana görürsün. Hava bozacaksa sağ taraf "
                    + "renkli bir çerçeveyle seni uyarır: sarı hafif yağmur ya da sis, turuncu yağmur ya da kar, "
                    + "kırmızı sağanak, yoğun kar ya da fırtına demek. Hızlı rotayla yola çıktığında sağ tarafta "
                    + "gideceğin yerin, varacağın saatteki havası çıkar. Bilgiler 15 dakikada bir yenilenir.";
            case "notif": return "WhatsApp mesajları, SMS'ler ve cevapsız aramalar burada toplanır. Yeni bir mesaj "
                    + "geldiğinde ekranın altında birkaç saniyeliğine mesajın kendisi görünür; telefonun yukarıdan "
                    + "inen bildirimi önünü kapatmaz. Kutuya dokunursan mesajların tamamını okursun, basılı tutarsan "
                    + "doğrudan o sohbete geçersin. Okuduğun mesajlar birkaç saniye sonra kendiliğinden kapanır ve kutudan kalkar.";
            case "status": return "Çekimi, Bluetooth bağlantısını ve şarjı tek bakışta görürsün. Telefon şarj olurken "
                    + "şimşek doldukça turuncudan sarıya, sonra yeşile döner. Arabaya bağlıyken telefon şarj olmuyorsa "
                    + "ve şarj %30'un altına düştüyse şimşek kırmızı yanıp sönerek kabloyu hatırlatır. Araban bağlı değilse kutuya dokun, Kopilot bağlanmayı dener.";
            case "assist": return "Dokunduğunda telefonun sesli asistanı açılır; elini direksiyondan ayırmadan arama "
                    + "yapabilir, mesaj gönderebilirsin. Basılı tutarsan sesli not alırsın: aklına geleni söyle, "
                    + "arabadan indiğinde sana hatırlatalım.";
            case "calls": return "En sık aradığın kişiler. Dokunduğunda üç saniye içinde arama başlar; yanlışlıkla "
                    + "bastıysan iptal etmeye vaktin olur. Basılı tutarsan o kişiye WhatsApp'tan \"Yoldayım\" "
                    + "mesajıyla anlık konumunu gönderirsin.";
            case "places": return "Ev, iş gibi sık gittiğin yerler. Dokunduğunda seçtiğin harita uygulamasında rota "
                    + "hemen başlar, adres yazmana gerek kalmaz.";
            case "park": return "Arabadan inmeden düğmeye kısaca basılı tut, park ettiğin yer kaydedilsin; istersen "
                    + "kat ya da sıra numarası gibi kısa bir not da ekleyebilirsin. Araba simgesinin köşesindeki gri artı, "
                    + "kayıt yapınca sarı bir P olur; böylece kayıtlı bir park yerin olduğunu bir bakışta anlarsın. "
                    + "Dokunduğunda ne zaman park ettiğini, arabanın ne kadar uzakta olduğunu ve notunu gösterir; "
                    + "oradan yürüyüş yol tarifini açabilir, konumu paylaşabilir ya da silebilirsin.";
            case "settings": return "Bu sayfaya buradan gelirsin. Gizlenemez ama istediğin yere taşıyabilirsin.";
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
        // Eksik olanları varsayılan yerleriyle ekle (eski sürümden geçiş dahil)
        for (int i = 0; i < KEYS.length; i++) {
            if (find(l, KEYS[i]) != null) continue;
            boolean on = !"assist".equals(KEYS[i]) || sp(c).getBoolean("assistBtn", true);
            l.add(new Entry(KEYS[i], DEF_ROW[i], on));
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
