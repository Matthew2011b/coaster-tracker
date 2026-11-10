"""Render basemap.bin to a PNG for checking: python3 preview.py lon lat zoom out.png"""
import sys, struct, math
from PIL import Image, ImageDraw
data = open("app/src/main/assets/basemap.bin", "rb").read()
pos = 5
def vi():
    global pos
    n = s = 0
    while True:
        b = data[pos]; pos += 1
        n |= (b & 0x7F) << s; s += 7
        if not b & 0x80: return n
def zz(n): return (n >> 1) ^ -(n & 1)
def ring():
    n = vi(); x = y = 0; pts = []
    for _ in range(n):
        x += zz(vi()); y += zz(vi()); pts.append((x, y))
    return pts
layers = {}
for _ in range(data[4]):
    kind, lid = data[pos], data[pos + 1]; pos += 2
    cnt = vi(); feats = []
    for _ in range(cnt):
        if kind == 2:
            L = vi(); name = data[pos:pos + L].decode(); pos += L
            x = zz(vi()); y = zz(vi()); mz = data[pos] / 10; rank = data[pos + 1]; pos += 2
            feats.append((name, x, y, mz))
        else:
            mz = data[pos] / 10; pos += 1
            if kind == 0: feats.append((mz, [ring() for _ in range(vi())]))
            else: feats.append((mz, [ring()]))
    layers[lid] = feats
lon, lat, z, out = float(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
W, H = 800, 1000
WORLD = 1 << 24
cx = (lon + 180) / 360 * WORLD
cy = (1 - math.log(math.tan(math.radians(lat)) + 1 / math.cos(math.radians(lat))) / math.pi) / 2 * WORLD
scale = 256 * 2 ** z / WORLD * 2  # px per unit at 2x density
def P(p): return ((p[0] - cx) * scale + W / 2, (p[1] - cy) * scale + H / 2)
im = Image.new("RGB", (W, H), (196, 216, 232)); d = ImageDraw.Draw(im)
for mz, rings in layers[0]:
    d.polygon([P(p) for p in rings[0]], fill=(243, 243, 243))
    for h in rings[1:]: d.polygon([P(p) for p in h], fill=(196, 216, 232))
for mz, rings in layers[1]:
    if z >= mz: d.polygon([P(p) for p in rings[0]], fill=(196, 216, 232))
for lid, col, w in ((2, (196, 216, 232), 3), (5, (190, 194, 197), 3), (4, (110, 114, 117), 2), (3, (110, 114, 117), 4)):
    for mz, rings in layers[lid]:
        if z + 0.5 >= mz: d.line([P(p) for p in rings[0]], fill=col, width=w)
for name, x, y, mz in layers[6]:
    if z + 1 >= mz:
        X, Y = P((x, y))
        if 0 < X < W and 0 < Y < H: d.text((X + 4, Y - 6), name, fill=(80, 84, 87)); d.ellipse((X - 2, Y - 2, X + 2, Y + 2), fill=(80, 84, 87))
im.save(out)
