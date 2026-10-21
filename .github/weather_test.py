# Hava kutusuna dokununca saatlik şerit; Ayarlar'da "Bildirim kutusu" listesi. Ekran görüntüleri.
import re, subprocess, time

def sh(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def nodes():
    sh("uiautomator dump /sdcard/w.xml")
    return sh("cat /sdcard/w.xml")

def tap_menu(label):
    x = nodes()
    m = re.search(r'text="' + label + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        sh("input tap %d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))
        time.sleep(2)

res = []
now = int(time.time())
codes = [3, 61, 80, 63, 2, 0]
hr = ";".join("%d,%d,%d,1,%d,-1" % (now + 600 + i * 3600, 9 - i, codes[i], [0, 40, 70, 90, 10, 0][i]) for i in range(6))
P = "/data/data/com.erdem.surusekrani/shared_prefs/surus.xml"
sh("am force-stop com.erdem.surusekrani")
time.sleep(1)
sh("rm -f " + P + ".bak")  # yarım kalmış yazma yedeği tohumu ezmesin  # yarım kalmış yazma yedeği tohumu ezmesin
sh("sed -i 's|<string name=\"wHr\">[^<]*</string>||; s|<long name=\"wAt\" value=\"[0-9]*\" />||' " + P)
sh("sed -i 's|</map>|<string name=\"wHr\">" + hr + "</string><long name=\"wAt\" value=\"" + str(now * 1000) + "\" /></map>|' " + P)
sh("am start -n com.erdem.surusekrani/.MainActivity")
time.sleep(5)
x = nodes()
m = re.search(r'resource-id="com\.erdem\.surusekrani:id/weatherTile"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    sh("input tap %d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))
    time.sleep(1.2)
    shot("shot-hourly.png")
    res.append("şerit: " + " / ".join(t for t in re.findall(r'text="([^"]+)"', nodes()) if re.match(r"^\d\d:\d\d$|°$|^%", t))[:200])
    time.sleep(9)
    res.append("9 sn sonra: " + ("açık" if re.search(r'text="%40"', nodes()) else "kapandı"))
else:
    res.append("hava kutusu yok")

sh("am start -n com.erdem.surusekrani/.SettingsActivity")
time.sleep(4)
tap_menu("Ana ekran")
w, h = 320, 640
mm = re.search(r"(\d+)x(\d+)", sh("wm size"))
if mm:
    w, h = int(mm.group(1)), int(mm.group(2))
found = False
for i in range(45):
    x = nodes()
    m = re.search(r'text="B[İI]LD[İI]R[İI]M KUTUSU"[^>]*bounds="\[\d+,(\d+)\]', x, re.I)
    if m:
        found = True
        if m and int(m.group(1)) > h * 0.3:
            sh("input swipe %d %d %d %d 400" % (w // 2, int(m.group(1)), w // 2, int(h * 0.15)))
            time.sleep(1)
        break
    sh("input swipe %d %d %d %d 900" % (w // 2, int(h * 0.75), w // 2, int(h * 0.4)))
    time.sleep(0.8)
shot("shot-notifapps.png")
x = nodes()
res.append("ayarlar: " + ("bulundu" if found else "bulunamadı"))
m = re.search(r'resource-id="com\.erdem\.surusekrani:id/notifAppsBtn"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    sh("input tap %d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))
    time.sleep(3)
    shot("shot-notifpicker.png")
    res.append("seçim: " + " / ".join(t for t in re.findall(r'text="([^"]+)"', nodes()) if t.startswith("✓"))[:150])
print(" | ".join(res))
open("weather.txt", "w").write(" | ".join(res))

# Ayarlar'ın en altı: İzinler bölümü
sh("am start -n com.erdem.surusekrani/.SettingsActivity")
time.sleep(3)
tap_menu("İzinler")
time.sleep(1.5)
x = nodes()
m = re.search(r'text="[İI]Z[İI]NLER"[^>]*bounds="\[\d+,(\d+)\]', x, re.I)
if m and int(m.group(1)) > h * 0.2:
    sh("input swipe %d %d %d %d 600" % (w // 2, int(m.group(1)), w // 2, int(h * 0.08)))
    time.sleep(1.5)
shot("shot-perms.png")

# Vurgu rengi: Ayarlar'da Turkuaz'a dokun, ana ekranın görüntüsünü al
sh("am start -n com.erdem.surusekrani/.SettingsActivity")
time.sleep(3)
tap_menu("Ana ekran")
tapped = False
for i in range(40):
    x = nodes()
    m = re.search(r'content-desc="Turkuaz[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        if y2 < h * 0.9:
            sh("input tap %d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))
            tapped = True
            time.sleep(3)
            shot("shot-accent-settings.png")
            break
    sh("input swipe %d %d %d %d 900" % (w // 2, int(h * 0.75), w // 2, int(h * 0.45)))
    time.sleep(0.8)
sh("am start -n com.erdem.surusekrani/.MainActivity")
time.sleep(4)
shot("shot-accent.png")
open("weather.txt", "a").write(" | renk: " + ("turkuaz seçildi" if tapped else "yuvarlak bulunamadı"))
