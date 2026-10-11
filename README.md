# Sürüş Ekranı

Araba için büyük butonlu Android ekranı: Navigasyon, Müzik, Eve git, İşe git.
Araba Bluetooth'una bağlanınca kendiliğinden açılır. İstersen ana ekran olarak da kullanılır.

## APK'yı alma

**Yol A: GitHub (bilgisayar gerekmez)**
1. GitHub'da boş bir depo aç ve bu klasörün içeriğini yükle.
2. Actions sekmesinde "APK derle" çalışır (5 dakika kadar sürer).
3. Releases → `latest` → `SurusEkrani.apk` dosyasını telefondan indirip kur.

**Yol B: Android Studio**
1. Bu klasörü Android Studio'da aç.
2. Build → Build APK(s). APK: `app/build/outputs/apk/debug/app-debug.apk`

Kurulumda "Bilinmeyen kaynaklardan yükleme" iznini vermen gerekir.

## İlk kurulum (telefonda)

Uygulamayı aç → Ayarlar:
1. Navigasyon ve müzik uygulamanı seç, ev ve iş adresini yaz.
2. **Arabayı seç** → listeden arabanın Bluetooth adını seç.
3. Otomatik açılma izinlerini sırayla ver (1, 2, 3).
4. Son uygulamalar ekranında Sürüş Ekranı'nı aşağı çekip kilitle.

Ana ekran modunu açarsan, diğer uygulamalara **Uygulamalar** butonundan ulaşırsın.
