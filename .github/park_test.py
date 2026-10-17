# Emülatörde park düğmesi: rozet görünüyor mu, dokununca panel, kısa basılı tutunca kayıt.
import re, subprocess, time

def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def dump():
    sh("uiautomator", "dump", "/sdcard/p.xml")
    return sh("cat", "/sdcard/p.xml")

x = dump()
m = re.search(r'resource-id="com\.erdem\.surusekrani:id/parkBtn"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x) \
    or re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="com\.erdem\.surusekrani:id/parkBtn"', x)
res = []
if not m:
    res.append("parkBtn bulunamadı")
else:
    x1, y1, x2, y2 = map(int, m.groups())
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    desc = re.search(r'content-desc="([^"]*)"[^>]*resource-id="com\.erdem\.surusekrani:id/parkBtn"', x)
    res.append("simge: " + (desc.group(1) if desc else "?"))
    sh("input", "tap", str(cx), str(cy))
    time.sleep(2)
    shot("shot-park.png")
    texts = re.findall(r'text="([^"]+)"', dump())
    res.append("panel: " + " / ".join(t for t in texts if t in ("Yürüyerek git", "Paylaş", "Sil", "Not", "Not ekle") or "Park yerin" in t or "B2" in t))
    # paneli kapat: başlığına dokun
    t = re.search(r'text="Park yerin[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump())
    if t:
        a1, b1, a2, b2 = map(int, t.groups())
        sh("input", "tap", str((a1 + a2) // 2), str((b1 + b2) // 2))
    time.sleep(1)
    # kısa basılı tutma (0,5 sn)
    sh("input", "swipe", str(cx), str(cy), str(cx), str(cy), "500")
    time.sleep(4)
    shot("shot-hold.png")
    texts = re.findall(r'text="([^"]+)"', dump())
    hit = [t for t in texts if "kaydedildi" in t or "kaydediliyor" in t or "bulunamadı" in t]
    res.append("basılı tut: " + (hit[0].replace("&#10;", " / ") if hit else "panel yok " + str(texts[:8])))
print(" | ".join(res))
open("park.txt", "w").write(" | ".join(res))
