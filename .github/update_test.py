# Emülatörde Kopilot içinden güncelleme: eski sürüm (versionCode 1) kurulu iken Ayarlar'daki "Güncelle"ye dokun,
# indirilen son sürüm için telefonun kurulum onayı çıkıyor mu, onaylayınca sürüm yükseliyor mu.
import re, subprocess, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def nodes():
    sh("uiautomator", "dump", "/sdcard/up.xml")
    x = sh("cat", "/sdcard/up.xml")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
        out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out

def ver():
    m = re.search(r"versionCode=(\d+)", sh("dumpsys", "package", "com.erdem.surusekrani"))
    return m.group(1) if m else "?"

W, H = map(int, re.findall(r"(\d+)x(\d+)", sh("wm", "size"))[-1])
res = "önce sürüm " + ver()
sh("am", "start", "-n", "com.erdem.surusekrani/.MainActivity")
time.sleep(18)  # ipucu şeridi (9 sn) kapansın
shot("shot-update-dot.png")  # Ayarlar simgesinde kırmızı nokta, altta "Yeni sürüm var" şeridi
sh("am", "start", "-n", "com.erdem.surusekrani/.SettingsActivity")
time.sleep(8)
found = False
for _ in range(4):
    for t, cx, cy in nodes():
        if t == "Güncelle":
            sh("input", "tap", str(cx), str(cy))
            found = True
            break
    if found:
        break
    sh("input", "swipe", str(W // 2), str(int(H * 0.8)), str(W // 2), str(int(H * 0.3)), "400")
    time.sleep(2)
res += " | Güncelle " + ("bulundu" if found else "yok")
confirm = ""
if found:
    for _ in range(20):
        time.sleep(3)
        ts = nodes()
        texts = [t for t, _, _ in ts]
        btn = [(t, cx, cy) for t, cx, cy in ts if t.upper() in ("INSTALL", "UPDATE")
               and "surusekrani" not in t]
        if btn and any("update" in t.lower() or "install" in t.lower() for t in texts if len(t) > 20):
            confirm = " / ".join(t for t in texts if t)[:200]
            shot("shot-update-confirm.png")
            t, cx, cy = btn[-1]
            sh("input", "tap", str(cx), str(cy))
            break
    res += " | onay: " + (confirm or "çıkmadı")
    time.sleep(20)
    res += " | sonra sürüm " + ver()
log = subprocess.run(["adb", "logcat", "-d", "-s", "Kopilot:W"], capture_output=True, text=True).stdout
res += " | log: " + " ; ".join(l.split("Kopilot :")[-1].strip() for l in log.splitlines() if "güncelleme" in l)[-300:]
print(res)
open("update.txt", "w").write(res)
