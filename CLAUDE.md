# Sürüş Ekranı — notlar

Kullanıcıyla Türkçe konuş.

## Müzik çalar Play tuşu (DOKUNMA — defalarca bozuldu)

`MainActivity.togglePlay()` ve `startMusicApp()` şu davranışı korumalı ve kullanıcı bunu onayladı (v6.6):

- Spotify kapalıyken Play'e basınca şarkı **arka planda, Spotify ekrana gelmeden** başlamalı.
- Spotify oturumu açıksa → `ctrl.getTransportControls().play()`.
- Spotify kapalıysa (ya da `ctrl` başka bir uygulamanın oturumu ise) → önce
  `AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PLAY_PAUSE)` (sistem son çalan uygulamayı uyandırır).
  Bu, işe yarayan asıl yöntem.
- Ses gelmezse yedekler: MediaBrowser bağlantısı + `ACTION_MEDIA_BUTTON` yayını (setPackage), sonra tekrar medya tuşu.
- **Asla** Spotify uygulamasını açma (`Apps.openMusic` / launch intent) — kullanıcı istemiyor.
- `ctrl` başka bir uygulamaya aitse ona `play()` gönderme.

Müzik koduna dokunan her değişiklikten sonra bu davranışın aynı kaldığını kontrol et.

## Diğer

- Derleme GitHub Actions'ta (`build.yml`), APK `latest` sürümüne yüklenir.
- `smoke.yml`: emülatörde ana ekran + Ayarlar açılır, çökme annotation olarak raporlanır.
  Elle tetiklenir: `gh api -X POST repos/ekererdem/surus-ekrani/actions/workflows/smoke.yml/dispatches -f ref=main`.
- Her sürümde `app/build.gradle` versionCode/versionName artır.
