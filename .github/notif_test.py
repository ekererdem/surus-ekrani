# Emülatörde test "WhatsApp"ından mesaj gönderip bildirim kutusunu, alt şeridi ve tam paneli çeker.
import re, subprocess, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def dump():
    sh("uiautomator", "dump", "/sdcard/n.xml")
    return sh("cat", "/sdcard/n.xml")

sh("am", "start", "-n", "com.erdem.surusekrani/.MainActivity")
time.sleep(3)
sh("am broadcast -n com.whatsapp/.Post --es who Ecem "
   "--esa texts 'Çıktın mı?,Ekmek alır mısın gelirken. Bir de yarın sabah için süt lazım'")
time.sleep(1.5)
shot("shot-notif-banner.png")
log = subprocess.run(["adb", "logcat", "-d", "-s", "Kopilot"], capture_output=True, text=True).stdout.strip().splitlines()[-3:]
still = sh("dumpsys notification --noredact | grep -c 'pkg=com.whatsapp'").strip()
time.sleep(8)  # alt şerit kaybolsun
shot("shot-notif-tile.png")
x = dump()
m = re.search(r'resource-id="com\.erdem\.surusekrani:id/notifTile"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
res = "kutu: " + " / ".join(t.replace("&#10;", " | ") for t in re.findall(r'text="([^"]+)"', x) if "Ecem" in t)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    sh("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.5)
    shot("shot-notif-panel.png")
    res += " | panel: " + " / ".join(t.replace("&#10;", " | ")[:80] for t in re.findall(r'text="([^"]+)"', dump()) if "Ecem" in t or "Ekmek" in t)
    time.sleep(9)  # panel kendiliğinden kapansın, okunan kutudan silinsin
    x3 = dump()
    shot("shot-notif-after.png")
    res += " | 9 sn sonra: panel " + ("açık" if "Ekmek" in x3 else "kapandı") + ", kutu: " + \
        " / ".join(t for t in re.findall(r'text="([^"]+)"', x3) if "Bildirim" in t or "Ecem" in t)
res += " | log: " + " ; ".join(l[-80:] for l in log) + " | telefonda kalan: " + still
print(res)
open("notif.txt", "w").write(res)

# Cevapsız arama düğmesi
sh("am", "start", "-n", "com.erdem.surusekrani/.MainActivity")
time.sleep(2)
sh("am broadcast -n com.whatsapp/.Post --es missed 'Ahmet Yılmaz'")
time.sleep(3)
x = dump()
m = re.search(r'resource-id="com\.erdem\.surusekrani:id/missedBtn"[^>]*', x)
mt = re.search(r'text="([^"]*)"[^>]*resource-id="com\.erdem\.surusekrani:id/missedBtn"', x)
shot("shot-missed.png")
res2 = "cevapsız düğme: " + ((mt.group(1).replace("&#10;", " | ")) if mt else ("var" if m else "yok"))
print(res2)
open("notif.txt", "a").write(" | " + res2)

# Müzik kutusu: sahte "Spotify" oturumuyla (kapak, şarkı satırı, nereden çaldığı, uygulama tuşları)
sh("am start -n com.whatsapp/.Player")
time.sleep(3)
sh("am start -n com.erdem.surusekrani/.MainActivity")
time.sleep(4)
x = dump()
shot("shot-music.png")
def tx(i):
    m = re.search(r'text="([^"]*)"[^>]*resource-id="com\.erdem\.surusekrani:id/' + i + '"', x)
    return m.group(1) if m else "-"
acts = re.search(r'resource-id="com\.erdem\.surusekrani:id/musicActions".*?</node>', x, re.S)
nact = len(re.findall(r'ImageButton', acts.group(0))) if acts else 0
res3 = "müzik: " + tx("songTitle") + " / " + tx("songArtist") + " / tuş sayısı " + str(nact)
ms = sh("dumpsys media_session").splitlines()
res3 += " / oturumlar: " + " ; ".join(l.strip() for l in ms if "package=" in l or "active=" in l or "state=PlaybackState" in l)[:400]
res3 += " / player: " + sh("am start -W -n com.whatsapp/.Player").replace("\n", " ")[:200]
print(res3)
open("notif.txt", "a").write(" | " + res3)

# Müzik tuşları: oturumdaki "Beğenilenlere ekle" ve yalnız bildirimde duran "Beğenmedim" dokununca uygulamaya ulaşıyor mu
sh("logcat -c")
descs = re.findall(r'content-desc="([^"]*)"[^>]*class="android.widget.ImageButton"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                   acts.group(0)) if acts else []
if not descs and acts:
    descs = re.findall(r'class="android.widget.ImageButton"[^>]*content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                       acts.group(0))
names = [d[0] for d in descs]
for want in ("Beğenilenlere ekle", "Beğenmedim"):
    for d in descs:
        if d[0] == want:
            sh("input tap %d %d" % ((int(d[1]) + int(d[3])) // 2, (int(d[2]) + int(d[4])) // 2))
            time.sleep(1.5)
log = subprocess.run(["adb", "logcat", "-d", "-s", "FakeWA:I"], capture_output=True, text=True).stdout
got = [l.split("FakeWA:")[-1].strip() for l in log.splitlines() if "FakeWA" in l and "tus" in l]
res4 = "müzik tuşları: " + " / ".join(names) + " | dokununca: " + (" ; ".join(got) if got else "hiçbiri ulaşmadı")
print(res4)
open("notif.txt", "a").write(" | " + res4)
