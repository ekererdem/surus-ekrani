# Emülatörde yatay ekran + Kopilot haritası: ana ekran ve "Ev" rotası ekran görüntüleri.
import re, subprocess, time

def sh(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def nodes():
    sh("uiautomator dump /sdcard/m.xml")
    x = sh("cat /sdcard/m.xml")
    return [(m.group(1), (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2)
            for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]

res = []
sh("am force-stop com.erdem.surusekrani")
sh("sed -i 's|</map>|<string name=\"nav\">inapp</string></map>|' /data/data/com.erdem.surusekrani/shared_prefs/surus.xml")
sh("settings put system accelerometer_rotation 0")
sh("settings put system user_rotation 1")
subprocess.run(["adb", "emu", "geo", "fix", "32.866", "39.925"], capture_output=True)
time.sleep(2)
sh("am start -n com.erdem.surusekrani/.MainActivity")
time.sleep(12)
subprocess.run(["adb", "emu", "geo", "fix", "32.8661", "39.9251"], capture_output=True)
time.sleep(3)
shot("shot-map.png")
n = nodes()
res.append("ekran: " + " / ".join(t for t, _, _ in n if t)[:300])
ev = [(x, y) for t, x, y in n if t.strip() == "Ev"]
if ev:
    x, y = ev[-1]  # haritadaki "Ev" çipi
    sh(f"input tap {x} {y}")
    time.sleep(10)
    shot("shot-map-route.png")
    n2 = nodes()
    res.append("rota: " + " / ".join(t for t, _, _ in n2 if t)[:300])
else:
    res.append("Ev düğmesi bulunamadı")
sh("settings put system user_rotation 0")
print(" | ".join(res))
open("map.txt", "w").write(" | ".join(res))
