"""Build app/src/main/assets/basemap.bin from Natural Earth (public domain) GeoJSON.

Download (from github.com/nvkelso/natural-earth-vector/tree/master/geojson) into SRC:
  ne_10m_land, ne_10m_lakes, ne_50m_rivers_lake_centerlines, ne_10m_admin_0_boundary_lines_land,
  ne_10m_admin_1_states_provinces_lines, ne_10m_roads, ne_10m_populated_places_simple

Output format (little endian), coordinates in Web Mercator world units, 0..2^24 across the world:
  b"LHM1", u8 layer count, then per layer:
    u8 kind (0 polygons, 1 lines, 2 places), u8 id, varint feature count, then per feature:
      polygons: varint ring count, rings; lines: one ring;  ring = varint n, n zigzag-varint (dx, dy)
      places: varint name length, utf8 name, zigzag x, zigzag y, u8 min_zoom*10, u8 rank (0 = biggest)
Layers 10-15 are coarse copies of 0-5 for zoomed-out views. Lines and polygons carry a u8 min zoom (x10) before their geometry so the app can skip detail when zoomed out.
"""
import json, math, struct, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/claude-0/ne"
OUT = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/basemap.bin"
WORLD = 1 << 24
STATE_COUNTRIES = {"United States of America", "Canada", "Australia"}


def merc(lon, lat):
    lat = max(-85.0, min(85.0, lat))
    x = (lon + 180.0) / 360.0
    y = (1 - math.log(math.tan(math.radians(lat)) + 1 / math.cos(math.radians(lat))) / math.pi) / 2
    return x * WORLD, y * WORLD


def simplify(pts, tol):
    """Douglas-Peucker on a list of (x, y)."""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]; bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy) or 1e-9
        best, bi = -1.0, -1
        for i in range(a + 1, b):
            px, py = pts[i]
            d = abs(dy * px - dx * py + bx * ay - by * ax) / L
            if d > best:
                best, bi = d, i
        if best > tol:
            keep[bi] = True
            stack += [(a, bi), (bi, b)]
    return [p for p, k in zip(pts, keep) if k]


def varint(n, out):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def zz(n):
    return (n << 1) ^ (n >> 63)


def ring_bytes(coords, tol, out, closed):
    pts = [merc(lon, lat) for lon, lat in coords]
    if closed and len(pts) > 3:
        # A closed ring starts and ends on the same point, so split it at the far point and simplify each half.
        far = max(range(len(pts)), key=lambda i: (pts[i][0] - pts[0][0]) ** 2 + (pts[i][1] - pts[0][1]) ** 2)
        pts = simplify(pts[:far + 1], tol)[:-1] + simplify(pts[far:], tol)
    else:
        pts = simplify(pts, tol)
    pts = [(int(round(x)), int(round(y))) for x, y in pts]
    dedup = [pts[0]] + [p for i, p in enumerate(pts[1:], 1) if p != pts[i - 1]]
    if len(dedup) < (4 if closed else 2):
        return False
    varint(len(dedup), out)
    px = py = 0
    for x, y in dedup:
        varint(zz(x - px), out); varint(zz(y - py), out)
        px, py = x, y
    return True


def load(name):
    return json.load(open(f"{SRC}/{name}.geojson"))["features"]


def geoms(f):
    g = f["geometry"]
    if g is None:
        return []
    t, c = g["type"], g["coordinates"]
    if t in ("Polygon", "LineString"):
        return [c]
    if t in ("MultiPolygon", "MultiLineString"):
        return c
    return []


# tolerance in world units (2^24 per world: 1 unit ~ 2.4 m at the equator)
FINE, COARSE = 40, 700  # ~100 m for zoomed in; ~1.7 km copies (layer id + 10) for zoomed out
TOL = FINE


def polygon_layer(lid, feats, minz):
    out = bytearray(); count = 0
    for f in feats:
        mz = minz(f)
        for poly in geoms(f):
            body = bytearray(); rings = 0
            for ring in poly:
                rb = bytearray()
                if ring_bytes(ring, globals()['TOL'], rb, True):
                    body += rb; rings += 1
            if rings:
                out.append(min(255, int(mz * 10))); varint(rings, out); out += body; count += 1
    head = bytearray([0, lid]); varint(count, head)
    return head + out, count


def line_layer(lid, feats, minz):
    out = bytearray(); count = 0
    for f in feats:
        mz = minz(f)
        for line in geoms(f):
            rb = bytearray()
            if ring_bytes(line, globals()['TOL'], rb, False):
                out.append(min(255, int(mz * 10))); out += rb; count += 1
    head = bytearray([1, lid]); varint(count, head)
    return head + out, count


def place_layer(lid, feats):
    out = bytearray(); count = 0
    feats = sorted(feats, key=lambda f: -(f["properties"].get("pop_max") or 0))
    for rank, f in enumerate(feats):
        p = f["properties"]
        name = (p.get("nameascii") or p.get("name") or "").encode()
        if not name:
            continue
        x, y = merc(p["longitude"], p["latitude"])
        varint(len(name), out); out += name
        varint(zz(int(x)), out); varint(zz(int(y)), out)
        out.append(min(255, int((p.get("min_zoom") or 10) * 10)))
        out.append(min(255, rank // 40))
        count += 1
    head = bytearray([2, lid]); varint(count, head)
    return head + out, count


def mz(key, default):
    return lambda f: f["properties"].get(key) or f["properties"].get(key.upper()) or default


land = load("ne_10m_land")
lakes = [f for f in load("ne_10m_lakes") if (f["properties"].get("scalerank") or 9) <= 6]
rivers = [f for f in load("ne_50m_rivers_lake_centerlines") if (f["properties"].get("scalerank") or 9) <= 6]
countries = load("ne_10m_admin_0_boundary_lines_land")
states = [f for f in load("ne_10m_admin_1_states_provinces_lines") if f["properties"].get("ADM0_NAME") in STATE_COUNTRIES]
roads = [f for f in load("ne_10m_roads") if f["properties"].get("level") == "Interstate"
         or (f["properties"].get("expressway") == 1 and f["properties"].get("sov_a3") != "USA")]

layers = []
for tol, off in ((FINE, 0), (COARSE, 10)):
    TOL = tol
    layers.append(polygon_layer(0 + off, land, lambda f: 0))
    layers.append(polygon_layer(1 + off, lakes, mz("min_zoom", 3)))
    layers.append(line_layer(2 + off, rivers, mz("min_zoom", 4)))
    layers.append(line_layer(3 + off, countries, lambda f: 0))
    layers.append(line_layer(4 + off, states, lambda f: 2.5))
    layers.append(line_layer(5 + off, roads, mz("min_zoom", 5)))
layers.append(place_layer(6, [f for f in load("ne_10m_populated_places_simple") if (f["properties"].get("min_zoom") or 10) <= 9]))

blob = bytearray(b"LHM1") + bytes([len(layers)])
for b, n in layers:
    blob += b
    print("layer", b[1], "features", n, "bytes", len(b))
open(OUT, "wb").write(blob)
print("total", len(blob))
