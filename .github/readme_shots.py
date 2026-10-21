# GitHub sayfası için ekran görüntüleri (Pixel boyutunda emülatör, sahte veri):
# Ayarlar menüsü, Neler yapabilirim?, yatayda yan yana ekran (Kopilot + harita).
import subprocess, time

def sh(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

# tips_shots.py verileri zaten yükledi (kişiler, Ev, park, hava, çalan şarkı, mesaj)
sh("am start -n com.erdem.surusekrani/.SettingsActivity")
time.sleep(5)
shot("readme-settings.png")
sh("input keyevent 4")
time.sleep(2)
sh("am start -n com.erdem.surusekrani/.TipsActivity")
time.sleep(4)
shot("readme-tips.png")
sh("input keyevent 4")
time.sleep(2)

# Yatay, yan yana: solda Kopilot, sağda harita
subprocess.run(["adb", "emu", "geo", "fix", "32.8597", "39.9334"])
sh("settings put system accelerometer_rotation 0")
sh("settings put system user_rotation 1")
time.sleep(3)
sh("am start --windowingMode 3 -n com.erdem.surusekrani/.MainActivity")
time.sleep(6)
sh("am start --windowingMode 4 -n com.google.android.apps.maps/com.google.android.maps.MapsActivity")
time.sleep(20)
shot("readme-split.png")
sh("am broadcast -n com.whatsapp/.Post --es who Ecem --esa texts 'Yola çıktın mı?'")
time.sleep(4)
shot("readme-split-msg.png")
