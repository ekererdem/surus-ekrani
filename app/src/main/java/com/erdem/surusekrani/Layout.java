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

    static final String[] KEYS = {"weather", "notif", "status", "assist", "calls", "places", "park", "settings"};
    private static final int[] DEF_ROW = {1, 1, 1, 2, 2, 2, 2, 2};

    private Layout() {}

    static String name(String key) {
        switch (key) {
            case "weather": return "Hava durumu";
            case "notif": return "Bildirimler";
            case "status": return "Telefon durumu";
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
            case "weather": return "Solda şu anki hava, sağda 1 saat sonrası. Yoğun yağmur, kar, sis ya da fırtına "
                    + "bekleniyorsa sağ taraf kırmızı çerçeveli olur. Hızlı rotayla yola çıkınca sağda varış yerinin havası görünür.";
            case "notif": return "WhatsApp, SMS ve cevapsız aramalar. Yeni mesaj gelince ekranın altında birkaç saniye "
                    + "mesajın kendisi görünür; kutuda 5 dakika kimden geldiği yazar, sonra sadece uygulama simgesi kalır. "
                    + "Dokun: açar. Basılı tut: gizler.";
            case "status": return "Şebeke çekimi, Bluetooth ve pil. Arabaya bağlıyken şarj olmuyor ve pil %30'un "
                    + "altındaysa pil kırmızı yanıp söner.";
            case "assist": return "Telefonun sesli asistanını açar.";
            case "calls": return "Eklediğin kişiler. Dokun: 3 saniye sonra arar. Basılı tut: WhatsApp ile konumunu gönderir.";
            case "places": return "Eklediğin yerler. Dokun: o yere rota başlatır.";
            case "park": return "Basılı tut: o anki konumu park yeri olarak kaydeder. Dokun: arabana yürüyüş rotası açar.";
            case "settings": return "Bu ekranı açar. Kapatılamaz, sadece yeri değişir.";
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
        Entry st = find(l, "settings");
        if (st != null) st.on = true; // ayarlara her zaman ulaşılabilsin
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
