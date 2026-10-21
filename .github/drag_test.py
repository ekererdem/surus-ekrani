# Emülatörde Ayarlar > Hızlı arama listesinde ≡ tutamağından sürükleyip sırayı kontrol eder.
import re, subprocess, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def dump():
    sh("uiautomator", "dump", "/sdcard/d.xml")
    return sh("cat", "/sdcard/d.xml")

def nodes(x):
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
        out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out

def order(x):
    return [t for t, _, _ in nodes(x) if "Ahmet" in t or "Ayşe" in t]

W, H = map(int, re.findall(r"(\d+)x(\d+)", sh("wm", "size"))[-1])
def tap_text(label):
    for t, cx, cy in nodes(dump()):
        if t == label:
            sh("input", "tap", str(cx), str(cy))
            time.sleep(2)
            return True
    return False

sh("am", "start", "-n", "com.erdem.surusekrani/.SettingsActivity")
time.sleep(5)
with open("shot-menu.png", "wb") as f:
    f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
tap_text("Kişiler ve yerler")
res = "tutamak bulunamadı"
for _ in range(10):
    x = dump()
    o = order(x)
    hs = [(cx, cy) for t, cx, cy in nodes(x) if t == "≡"]
    rows = [(t, cy) for t, _, cy in nodes(x) if "Ahmet" in t or "Ayşe" in t]
    if len(rows) == 2 and rows[1][1] < H * 0.85 and hs:
        # Ahmet satırının tutamağı (aynı yükseklikteki ≡)
        h = min(hs, key=lambda p: abs(p[1] - rows[0][1]))
        before = o
        sh("input", "swipe", str(h[0]), str(h[1]), str(h[0]), str(h[1] + (rows[1][1] - rows[0][1]) + 40), "1200")
        time.sleep(2)
        after = order(dump())
        res = f"önce={before} sonra={after} " + ("SÜRÜKLEME ÇALIŞTI" if after and after != before else "SIRA DEĞİŞMEDİ")
        break
    sh("input", "swipe", str(W // 2), str(int(H * 0.8)), str(W // 2), str(int(H * 0.35)), "400")
    res = f"bulunamadı ekran={W}x{H} son yazılar={[t for t, _, _ in nodes(x)][:12]}"
    time.sleep(1)
print(res)
open("drag.txt", "w").write(res)

# Ana ekran düğmeleri bölümünün ekran görüntüsü
sh("input", "keyevent", "4")  # başlıktan menüye dön
time.sleep(2)
tap_text("Ana ekran")
for _ in range(15):
    x = dump()
    if any(t in ("Gizle", "Göster") for t, _, _ in nodes(x)):
        sh("input", "swipe", str(W // 2), str(int(H * 0.6)), str(W // 2), str(int(H * 0.4)), "400")
        time.sleep(1)
        with open("shot-layout.png", "wb") as f:
            f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
        break
    sh("input", "swipe", str(W // 2), str(int(H * 0.8)), str(W // 2), str(int(H * 0.35)), "400")
    time.sleep(1)

# Bağlantı seçenekleri sayfası ve araba seçimi (emülatörde Bluetooth yoksa yalnız uyarı çıkar, çökmemeli)
def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
sh("am", "start", "-n", "com.erdem.surusekrani/.SettingsActivity")
time.sleep(4)
for _ in range(3):
    if tap_text("Bağlantı seçenekleri"):
        break
    sh("input", "keyevent", "4")
    time.sleep(2)
shot("shot-connection.png")
sh("input", "swipe", str(W // 2), str(int(H * 0.8)), str(W // 2), str(int(H * 0.3)), "400")
time.sleep(1)
shot("shot-connection2.png")
sh("input", "swipe", str(W // 2), str(int(H * 0.3)), str(W // 2), str(int(H * 0.9)), "400")
time.sleep(1)
tap_text("Arabayı seç") or tap_text("Değiştir")
time.sleep(2)
shot("shot-carpick.png")
sh("input", "keyevent", "4")
