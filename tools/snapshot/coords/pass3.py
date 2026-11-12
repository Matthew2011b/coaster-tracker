import json, time, urllib.request, urllib.parse, os
SNAP='/tmp/claude-0/geo/snapshot.before.json'
d={str(p['id']):p for p in json.load(open(SNAP))['parks']}
towns=json.load(open('/tmp/claude-0/geo/towns.json'))
LEFT=json.load(open('/tmp/claude-0/geo/left.json'))
OUT='/tmp/claude-0/geo/geo3.json'
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
COUNTRY={}
for p in json.load(open('/tmp/claude-0/cp/parks.json')):
    c=p.get('country') or {}
    if c.get('code'): COUNTRY[c['code']]=c.get('name')
OKCAT={'tourism','leisure','amenity'}
BAD={'city','town','village','administrative','country','state','county','postcode','suburb','road','residential'}
def search(q, cc):
    u="https://nominatim.openstreetmap.org/search?"+urllib.parse.urlencode({'q':q,'format':'jsonv2','limit':5,'countrycodes':(cc or '').lower(),'accept-language':'en'})
    req=urllib.request.Request(u,headers={'User-Agent':'LiftHill-dev/0.1 (personal project, one-time data fill)'})
    for a in range(3):
        try:
            with urllib.request.urlopen(req,timeout=40) as r: return json.load(r)
        except Exception: time.sleep(5)
    return []
for k,t in towns.items():
    if k in out and out[k].get('how')!='none' or (k in out and k not in [str(x['id']) for x in LEFT]): continue
    p=d[k]; cc=p['country']; place=t.get('place'); state=t.get('state')
    out[k]={'how':'none'}
    if place:
        loc=', '.join(x for x in (place,state,COUNTRY.get(cc)) if x)
        res=search(f"{p['name']}, {loc}", cc); time.sleep(1.1)
        hit=next((r for r in res if r.get('category') in OKCAT and r.get('type') not in BAD), None)
        if hit: out[k]={'lat':float(hit['lat']),'lon':float(hit['lon']),'how':'osm3','name':hit.get('name'),'type':hit.get('type'),'town':place}
        else:
            res=search(loc, cc); time.sleep(1.1)
            if res: out[k]={'lat':float(res[0]['lat']),'lon':float(res[0]['lon']),'how':'town3','town':place,'match':res[0].get('display_name')}
    json.dump(out,open(OUT,'w'))
import collections; print('done',len(out),collections.Counter(v['how'] for v in out.values()))
