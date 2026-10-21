"""İpuçları sayfası görüntüleri: ana ekran görüntüsünden şerit, hedef dışı karartılır (spot ışığı)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SRC, OUT, PREVIEW = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(SRC).convert("RGBA")
W = im.width
os.makedirs(OUT, exist_ok=True)

STRIP = (0, 30, W, 380)  # üst iki sıra
TILES = {
    "weather": (50, 61, 306, 193), "notif": (340, 61, 683, 193), "status": (717, 61, 1031, 193),
    "mic": (51, 215, 224, 347), "call": (252, 215, 425, 347), "place": (654, 215, 827, 347),
    "park": (855, 215, 1028, 347),
}


def spotlight(crop_box, target, radius=34):
    c = im.crop(crop_box).copy()
    ox, oy = crop_box[0], crop_box[1]
    t = (target[0] - ox - 6, target[1] - oy - 6, target[2] - ox + 6, target[3] - oy + 6)
    S = 3  # yumuşak kenar için büyük maske
    mask = Image.new("L", (c.width * S, c.height * S), 165)
    ImageDraw.Draw(mask).rounded_rectangle(tuple(v * S for v in t), radius=radius * S, fill=0)
    mask = mask.resize(c.size, Image.LANCZOS)
    dark = Image.new("RGBA", c.size, (4, 8, 18, 255))
    dark.putalpha(mask)
    c.alpha_composite(dark)
    ring = Image.new("RGBA", (c.width * S, c.height * S), (0, 0, 0, 0))
    ImageDraw.Draw(ring).rounded_rectangle(tuple(v * S for v in t), radius=radius * S,
                                           outline=(255, 255, 255, 235), width=4 * S)
    c.alpha_composite(ring.resize(c.size, Image.LANCZOS))
    return c


def arrows(c):
    """Müzik kutusu hafif karartılır, üstüne sola / sağa kaydır okları."""
    dim = Image.new("RGBA", c.size, (4, 8, 18, 120))
    c.alpha_composite(dim)
    S = 3
    lay = Image.new("RGBA", (c.width * S, c.height * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    cy = int(c.height * 0.50) * S
    for direction in (-1, 1):
        x0 = c.width * S // 2 + direction * 60 * S
        x1 = c.width * S // 2 + direction * 260 * S
        d.line((x0, cy, x1, cy), fill=(255, 255, 255, 240), width=14 * S)
        h = 38 * S
        d.polygon([(x1 + direction * 26 * S, cy), (x1 - direction * h * 0.4, cy - h), (x1 - direction * h * 0.4, cy + h)],
                  fill=(255, 255, 255, 240))
    shadow = lay.filter(ImageFilter.GaussianBlur(8 * S)).point(lambda v: v)
    sh = Image.new("RGBA", lay.size, (0, 0, 0, 0))
    sh.putalpha(shadow.getchannel("A").point(lambda v: int(v * 0.7)))
    c.alpha_composite(sh.resize(c.size, Image.LANCZOS))
    c.alpha_composite(lay.resize(c.size, Image.LANCZOS))
    return c


outs = {}
for k, box in TILES.items():
    outs[k] = spotlight(STRIP, box)
music = im.crop((30, 1510, W - 30, 2025)).copy()
outs["music"] = arrows(music)

for k, c in outs.items():
    w = 900
    c = c.resize((w, round(c.height * w / c.width)), Image.LANCZOS).convert("RGB")
    c.save(os.path.join(OUT, "tip_%s.webp" % k), "WEBP", quality=86, method=6)
    outs[k] = c

# önizleme
ims = list(outs.values())
h = sum(i.height for i in ims) + 20 * len(ims)
sheet = Image.new("RGB", (900, h), (40, 40, 40))
y = 0
for i in ims:
    sheet.paste(i, (0, y))
    y += i.height + 20
sheet.save(PREVIEW)
print({k: v.size for k, v in outs.items()})
