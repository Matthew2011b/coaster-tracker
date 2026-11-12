"""Re-query each pass-1 OSM hit to record its OSM type and returned name, so weak matches can be judged."""
import json, time, urllib.request, urllib.parse, os
SNAP='/tmp/claude-0/geo/snapshot.before.json'
OUT='/tmp/claude-0/geo/types.json'
d=json.load(open(SNAP)); geo=json.load(open('/tmp/claude-0/geo/geo.json'))
COUNTRY={}
for p in json.load(open('/tmp/claude-0/cp/parks.json')):
    c=p.get('country') or {}
    if c.get('code'): COUNTRY[c['code']]=c.get('name')
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
GOOD={'theme_park','amusement_park','water_park','zoo','attraction','park','resort','fairground','leisure','aquarium','farm','entertainment','amusement_arcade','miniature_golf','sports_centre','events_venue','hotel','casino'}
for p in d['parks']:
    k=str(p['id']); g=geo.get(k,{})
    if g.get('how')!='osm' or k in out: continue
    cc=p.get('country'); city=p.get('city'); state=p.get('state')
    loc=', '.join(x for x in (city, state) if x) if cc=='US' else (COUNTRY.get(cc) or '')
    q=f"{p['name']}, {loc}" if loc else p['name']
    u="https://nominatim.openstreetmap.org/search?"+urllib.parse.urlencode({'q':q,'format':'jsonv2','limit':5,'countrycodes':(cc or '').lower(),'accept-language':'en'})
    req=urllib.request.Request(u,headers={'User-Agent':'LiftHill-dev/0.1 (personal project, one-time data fill)'})
    try:
        with urllib.request.urlopen(req,timeout=40) as r: res=json.load(r)
    except Exception: res=[]
    hit=next((r for r in res if r.get('type') in GOOD or r.get('category') in ('tourism','leisure')), None)
    out[k]={'type':hit.get('type') if hit else None,'name':hit.get('name') if hit else None,'display':hit.get('display_name') if hit else None}
    json.dump(out,open(OUT,'w')); time.sleep(1.1)
print('done',len(out))
