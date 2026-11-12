import json, time, urllib.request, urllib.parse, os, re, collections
SNAP='/home/claude/coaster-tracker/app/src/main/assets/snapshot.json'
OUT='/tmp/claude-0/geo/geo.json'
d=json.load(open(SNAP))
COUNTRY={}  # ISO2 -> name for queries
try:
    for p in json.load(open('/tmp/claude-0/cp/parks.json')):
        c=p.get('country') or {}
        if c.get('code'): COUNTRY[c['code']]=c.get('name')
except Exception: pass
missing=[p for p in d['parks'] if p.get('lat') is None or p.get('lon') is None]
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
GOOD={'theme_park','amusement_park','water_park','zoo','attraction','park','resort','fairground','leisure','aquarium','farm','entertainment','amusement_arcade','miniature_golf','sports_centre','events_venue','hotel','casino'}
def search(q, cc):
    u="https://nominatim.openstreetmap.org/search?"+urllib.parse.urlencode({'q':q,'format':'jsonv2','limit':5,'countrycodes':cc.lower() if cc else ''})
    req=urllib.request.Request(u,headers={'User-Agent':'LiftHill-dev/0.1 (personal project, one-time data fill)'})
    for a in range(3):
        try:
            with urllib.request.urlopen(req,timeout=40) as r: return json.load(r)
        except Exception as e: time.sleep(5)
    return []
for p in missing:
    k=str(p['id'])
    if k in out: continue
    cc=p.get('country'); city=p.get('city'); state=p.get('state')
    loc=', '.join(x for x in (city, state) if x) if cc=='US' else (COUNTRY.get(cc) or '')
    res=search(f"{p['name']}, {loc}" if loc else p['name'], cc); time.sleep(1.1)
    hit=next((r for r in res if r.get('type') in GOOD or r.get('category') in ('tourism','leisure')), None)
    if hit:
        out[k]={'lat':float(hit['lat']),'lon':float(hit['lon']),'how':'osm','name':hit.get('name')}
    else:
        out[k]={'how':'none'}
        if city:
            res=search(', '.join(x for x in (city, state, COUNTRY.get(cc) or cc) if x), cc); time.sleep(1.1)
            if res: out[k]={'lat':float(res[0]['lat']),'lon':float(res[0]['lon']),'how':'town'}
    json.dump(out,open(OUT,'w'))
print('done',len(out),collections.Counter(v['how'] for v in out.values()))
