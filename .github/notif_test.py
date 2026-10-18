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
sh("am", "broadcast", "-n", "com.whatsapp/.Post", "--es", "who", "Ecem",
   "--esa", "texts", "Çıktın mı?,Ekmek alır mısın gelirken. Bir de yarın sabah için süt lazım")
time.sleep(1.5)
shot("shot-notif-banner.png")
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
print(res)
open("notif.txt", "w").write(res)
