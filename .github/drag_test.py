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

sh("am", "start", "-n", "com.erdem.surusekrani/.SettingsActivity")
time.sleep(5)
res = "tutamak bulunamadı"
for _ in range(10):
    x = dump()
    o = order(x)
    hs = [(cx, cy) for t, cx, cy in nodes(x) if t == "≡"]
    rows = [(t, cy) for t, _, cy in nodes(x) if "Ahmet" in t or "Ayşe" in t]
    if len(rows) == 2 and rows[1][1] < 1700:
        # Ahmet satırının tutamağı (aynı yükseklikteki ≡)
        h = min(hs, key=lambda p: abs(p[1] - rows[0][1]))
        before = o
        sh("input", "swipe", str(h[0]), str(h[1]), str(h[0]), str(h[1] + (rows[1][1] - rows[0][1]) + 40), "1200")
        time.sleep(2)
        after = order(dump())
        res = f"önce={before} sonra={after} " + ("SÜRÜKLEME ÇALIŞTI" if after and after != before else "SIRA DEĞİŞMEDİ")
        break
    sh("input", "swipe", "500", "1500", "500", "700", "400")
    time.sleep(1)
print(res)
open("drag.txt", "w").write(res)
