"""Pick coordinates for every park missing them, with checks, and patch snapshot.json.

Sources per park: OSM pass 1 (geo.json + types.json), OSM pass 2 (geo2.json), Wikidata unique match, town center.
Writes the patched snapshot, decisions.json (how each park was placed) and left.json (still missing).
"""
import json, re, math, collections, sys

BEFORE = '/tmp/claude-0/geo/snapshot.before.json'
SNAP = '/home/claude/coaster-tracker/app/src/main/assets/snapshot.json'
d = json.load(open(BEFORE))
geo = json.load(open('/tmp/claude-0/geo/geo.json'))
geo2 = json.load(open('/tmp/claude-0/geo/geo2.json'))
types = json.load(open('/tmp/claude-0/geo/types.json'))
geo3 = json.load(open('/tmp/claude-0/geo/geo3.json'))
geo4 = json.load(open('/tmp/claude-0/geo/geo4.json'))
extra = json.load(open('/tmp/claude-0/geo/manual.json')) if len(sys.argv) > 1 else {}

STRONG = {'theme_park', 'amusement_park', 'water_park', 'zoo', 'aquarium', 'fairground', 'resort', 'attraction'}

def norm(s):
    s = s.lower(); s = re.sub(r"^the ", "", s); return re.sub(r"[^a-z0-9]+", "", s.replace('&', 'and'))
SUF = ['amusementpark', 'themepark', 'park', 'freizeitpark', 'parc', 'parque', 'waterpark', 'resort']
def core(s):
    n = norm(s)
    for x in SUF:
        if n.endswith(x) and len(n) > len(x) + 3: n = n[:-len(x)]
    return n
def words(s):
    return {w for w in re.findall(r"[a-z0-9]+", s.lower().replace('&', 'and')) if w not in {'the', 'and', 'park', 'amusement', 'theme', 'of', 'at', 'family', 'fun'}}
def similar(a, b):
    if not a or not b: return False
    ca, cb = core(a), core(b)
    if ca and cb and (ca in norm(b) or cb in norm(a)): return True
    wa, wb = words(a), words(b)
    return bool(wa) and len(wa & wb) / len(wa) >= 0.5

def km(a, b, c, e):
    r = math.radians
    return 6371 * 2 * math.asin(math.sqrt(math.sin(r(c - a) / 2) ** 2 + math.cos(r(a)) * math.cos(r(c)) * math.sin(r(e - b) / 2) ** 2))

# Wikidata index
wd = collections.defaultdict(dict)
for b in json.load(open('/tmp/claude-0/wd/parks_wd.json'))['results']['bindings']:
    m = re.match(r'Point\(([-\d.eE]+) ([-\d.eE]+)\)', b['coord']['value'])
    if not m: continue
    cc = b.get('cc', {}).get('value')
    for k in {norm(b['label']['value']), core(b['label']['value'])}:
        wd[(k, cc)][b['item']['value']] = (float(m[2]), float(m[1]))

# US states / Canadian provinces for the region check
regions = {}
for f in json.load(open('/tmp/claude-0/ne/ne_10m_admin_1_states_provinces.geojson'))['features']:
    pr = f['properties']
    if pr.get('adm0_a3') not in ('USA', 'CAN'): continue
    g = f['geometry']; polys = g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
    regions[(pr['adm0_a3'], pr['name'])] = polys
def inside(lat, lon, polys):
    for poly in polys:
        ring = poly[0]; c = False; j = len(ring) - 1
        for i in range(len(ring)):
            xi, yi = ring[i]; xj, yj = ring[j]
            if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi: c = not c
            j = i
        if c: return True
    return False
def region_ok(p, lat, lon):
    a3 = {'US': 'USA', 'CA': 'CAN'}.get(p['country'])
    if not a3 or not p.get('state'): return True
    polys = regions.get((a3, p['state']))
    if not polys: return True
    # small tolerance for points on a shoreline or border: also accept within ~3 km of the region
    if inside(lat, lon, polys): return True
    return any(inside(lat + dy, lon + dx, polys) for dy in (-0.03, 0, 0.03) for dx in (-0.03, 0, 0.03))

how = collections.Counter(); decisions = {}; left = []
for p in d['parks']:
    if p.get('lat') is not None and p.get('lon') is not None: continue
    k = str(p['id'])
    if k in extra:
        e = extra[k]; p['lat'], p['lon'] = round(e['lat'], 5), round(e['lon'], 5)
        how['looked up by hand'] += 1; decisions[k] = {'name': p['name'], 'how': 'manual', 'note': e.get('note')}; continue
    items = {}
    for kk in (norm(p['name']), core(p['name'])): items.update(wd.get((kk, p['country']), {}))
    cands = [v for v in items.values() if region_ok(p, *v)]  # several same-named parks: keep the one in the right state
    wdp = cands[0] if len(cands) == 1 and (len(items) == 1 or p['country'] in ('US', 'CA')) else None

    osm = []
    g = geo.get(k, {})
    if g.get('how') == 'osm':
        t = types.get(k, {})
        osm.append((g['lat'], g['lon'], t.get('type'), t.get('name') or g.get('name')))
    g2 = geo2.get(k, {})
    if g2.get('how') == 'osm2':
        osm.append((g2['lat'], g2['lon'], g2.get('type'), g2.get('name')))
    g3 = geo3.get(k, {})
    if g3.get('how') == 'osm3':
        osm.append((g3['lat'], g3['lon'], g3.get('type'), g3.get('name')))
    pick = None
    for la, lo, ty, nm in osm:
        if not region_ok(p, la, lo): continue
        strong = ty in STRONG and similar(p['name'], nm or '')
        if wdp and km(la, lo, *wdp) > 2:
            continue  # two sources disagree: Wikidata's point is for the park itself, OSM may have found a neighbor
        if strong or (wdp and km(la, lo, *wdp) <= 25): pick = (la, lo, 'park (OpenStreetMap)'); break
    if not pick and wdp: pick = (*wdp, 'park (Wikidata)')
    if not pick:
        for la, lo, ty, nm in osm:  # weaker: name matches and the region checks out (US queries include the town)
            if p['country'] == 'US' and region_ok(p, la, lo) and similar(p['name'], nm or ''):
                pick = (la, lo, 'park (OpenStreetMap, weaker match)'); break
    if not pick:
        for la, lo, ty, nm in osm:  # pass 3 searched with the park's own town, so a name match there is trustworthy
            if g3.get('how') == 'osm3' and (la, lo) == (g3['lat'], g3['lon']) and similar(p['name'], nm or ''):
                pick = (la, lo, 'park (OpenStreetMap, with town)'); break
    g4 = geo4.get(k, {})
    if not pick and g4.get('how') == 'osm4' and region_ok(p, g4['lat'], g4['lon']) and similar(p['name'], g4.get('name') or ''):
        pick = (g4['lat'], g4['lon'], 'park (OpenStreetMap, state search)')
    if not pick and g.get('how') == 'town' and region_ok(p, g['lat'], g['lon']):
        pick = (g['lat'], g['lon'], 'town center')
    if not pick and g3.get('how') == 'town3':
        pick = (g3['lat'], g3['lon'], 'town center')
    if pick:
        p['lat'], p['lon'] = round(pick[0], 5), round(pick[1], 5); how[pick[2]] += 1
        decisions[k] = {'name': p['name'], 'how': pick[2]}
    else:
        how['not found'] += 1; left.append(p)

d['generated'] = '2026-11-12'
if 'OpenStreetMap' not in d['source']:
    d['source'] += '; park coordinates partly from OpenStreetMap contributors (ODbL) and Wikidata (CC0)'
json.dump(d, open(SNAP, 'w'), ensure_ascii=False, separators=(',', ':'))
json.dump(decisions, open('/tmp/claude-0/geo/decisions.json', 'w'), indent=0)
json.dump([{'id': p['id'], 'name': p['name'], 'country': p['country'], 'city': p.get('city'), 'state': p.get('state')} for p in left],
          open('/tmp/claude-0/geo/left.json', 'w'), indent=0)
print(dict(how))
