"""İpuçları: el sallama görseli — telefonun üst kısmı (ön kamera) ve üzerinden geçen el."""
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
W, H, S = 900, 292, 3
BG = (11, 23, 48, 255)
img = Image.new("RGBA", (W * S, H * S), BG)
d = ImageDraw.Draw(img)
# Telefonun üst kısmı: çerçeve, ekran, ön kamera
px0, px1, py0 = 250 * S, 650 * S, 150 * S
d.rounded_rectangle((px0, py0, px1, (H + 80) * S), radius=60 * S, fill=(32, 37, 44, 255), outline=(120, 132, 148, 255), width=6 * S)
d.rounded_rectangle((px0 + 18 * S, py0 + 18 * S, px1 - 18 * S, (H + 80) * S), radius=44 * S, fill=(18, 30, 56, 255))
cx = (px0 + px1) // 2
d.ellipse((cx - 13 * S, py0 + 34 * S, cx + 13 * S, py0 + 60 * S), fill=(5, 8, 14, 255), outline=(70, 80, 95, 255), width=3 * S)
# El hareket izi (yay) ve oklar
arc = (cx - 230 * S, 20 * S, cx + 230 * S, 260 * S)
d.arc(arc, start=200, end=340, fill=(255, 255, 255, 150), width=7 * S)
for sx, ang in ((-1, 200), (1, 340)):
    import math
    a = math.radians(ang)
    rx, ry = 230 * S, 120 * S
    x = cx + rx * math.cos(a)
    y = 140 * S + ry * math.sin(a)
    h = 22 * S
    d.polygon([(x + sx * 4 * S, y + h), (x - sx * h, y - 2 * S), (x + sx * h * 0.6, y - h * 0.9)], fill=(255, 255, 255, 150))
img = img.resize((W, H), Image.LANCZOS)
# El (emoji)
try:
    f = ImageFont.truetype("/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf", 109)
    e = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    ImageDraw.Draw(e).text((10, 10), "👋", font=f, embedded_color=True)
    e = e.crop(e.getchannel("A").getbbox()).resize((96, 96), Image.LANCZOS)
    img.alpha_composite(e, (W // 2 - 48, 22))
except Exception as ex:
    print("emoji yok", ex)
img.convert("RGB").save(OUT, "WEBP", quality=88, method=6)
img.convert("RGB").save(OUT.replace(".webp", ".png"))
print("ok")
