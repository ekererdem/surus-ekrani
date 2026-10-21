# İpuçları sayfası için ana ekranın büyük ekran görüntüleri (Pixel boyutunda emülatör).
# Sahte veri: iki kişi, Ev, kayıtlı park yeri, çalan şarkı, WhatsApp mesajı, hava durumu.
import subprocess, time

def sh(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout

def shot(name):
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)

def dump(name):
    sh("uiautomator dump /sdcard/t.xml")
    open(name, "w").write(sh("cat /sdcard/t.xml"))

P = "/data/data/com.erdem.surusekrani/shared_prefs/surus.xml"
now = int(time.time())
codes = [1, 2, 61, 3, 1, 0]
hr = ";".join("%d,%d,%d,1,%d,-1" % (now + 600 + i * 3600, 16 - i, codes[i], [0, 10, 60, 20, 0, 0][i]) for i in range(6))
xml = ('<?xml version="1.0" encoding="utf-8"?><map>'
       '<boolean name="autoSplit" value="false" /><boolean name="onboarded" value="true" />'
       '<boolean name="accentLogo" value="true" /><string name="accent">teal</string>'
       '<string name="call1Name">Ahmet</string><string name="call1Num">05551112233</string>'
       '<string name="call2Name">Ayşe</string><string name="call2Num">05552223344</string>'
       '<string name="place1Name">Ev</string><string name="place1Coord">39.9,32.8</string><string name="place1Icon">home</string>'
       '<string name="parkCoord">39.9208,32.8541</string><long name="parkAt" value="%d" />'
       '<int name="wT" value="16" /><int name="wC" value="1" /><boolean name="wD" value="true" /><string name="wH"></string>'
       '<int name="lT" value="15" /><int name="lC" value="2" /><boolean name="lD" value="true" /><string name="lL">%s</string>'
       '<string name="wHr">%s</string><long name="wAt" value="%d" />'
       '<string name="tip_note">x</string><boolean name="tip_note" value="true" /><boolean name="tip_park" value="true" />'
       '<boolean name="tip_swipe" value="true" /><boolean name="tip_wave" value="true" />'
       '</map>') % ((now - 3 * 3600) * 1000, time.strftime("%H:%M", time.localtime(now + 3600)), hr, now * 1000)
xml = xml.replace('<string name="tip_note">x</string>', '')
sh("am force-stop com.erdem.surusekrani")
time.sleep(1)
sh("mkdir -p /data/data/com.erdem.surusekrani/shared_prefs")
sh("rm -f " + P + ".bak")
open("seed.xml", "w").write(xml)
subprocess.run(["adb", "push", "seed.xml", P])
sh("chmod 666 " + P)
sh("cmd notification allow_listener com.erdem.surusekrani/com.erdem.surusekrani.MediaListener")
sh("am start -n com.whatsapp/.Player")
time.sleep(3)
sh("am start -n com.erdem.surusekrani/.MainActivity")
time.sleep(8)
sh("am broadcast -n com.whatsapp/.Post --es who Ecem --esa texts 'Yola çıktın mı?'")
time.sleep(12)  # alttaki şerit kapansın, kutu kalsın
shot("tips-main.png")
dump("tips-main.xml")
