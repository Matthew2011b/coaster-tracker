import json, time, urllib.request, urllib.parse, os
d={str(p['id']):p for p in json.load(open('/tmp/claude-0/geo/snapshot.before.json'))['parks']}
dec=json.load(open('/tmp/claude-0/geo/decisions.json'))
OUT='/tmp/claude-0/geo/geo4.json'
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
OKCAT={'tourism','leisure','amenity'}
BAD={'city','town','village','administrative','country','state','county','postcode','suburb','road','residential'}
for k,v in dec.items():
    p=d[k]
    if v['how']!='town center' or p['country']!='US' or k in out: continue
    q=f"{p['name']}, {p.get('state') or ''}"
    u="https://nominatim.openstreetmap.org/search?"+urllib.parse.urlencode({'q':q,'format':'jsonv2','limit':5,'countrycodes':'us'})
    req=urllib.request.Request(u,headers={'User-Agent':'LiftHill-dev/0.1 (personal project, one-time data fill)'})
    try:
        with urllib.request.urlopen(req,timeout=40) as r: res=json.load(r)
    except Exception: res=[]
    hit=next((r for r in res if r.get('category') in OKCAT and r.get('type') not in BAD), None)
    out[k]={'lat':float(hit['lat']),'lon':float(hit['lon']),'how':'osm4','name':hit.get('name'),'type':hit.get('type')} if hit else {'how':'none'}
    json.dump(out,open(OUT,'w')); time.sleep(1.1)
import collections; print('done',len(out),collections.Counter(v['how'] for v in out.values()))
