import json, time, urllib.request, urllib.parse, os, re
SNAP='/home/claude/coaster-tracker/app/src/main/assets/snapshot.json'
OUT='/tmp/claude-0/geo/geo2.json'
d=json.load(open(SNAP))
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
OKCAT={'tourism','leisure','amenity','building','landuse','historic'}
BAD={'city','town','village','administrative','country','state','county','postcode','suburb','road','residential'}
def search(q, cc):
    u="https://nominatim.openstreetmap.org/search?"+urllib.parse.urlencode({'q':q,'format':'jsonv2','limit':5,'countrycodes':(cc or '').lower(),'accept-language':'en'})
    req=urllib.request.Request(u,headers={'User-Agent':'LiftHill-dev/0.1 (personal project, one-time data fill)'})
    for a in range(3):
        try:
            with urllib.request.urlopen(req,timeout=40) as r: return json.load(r)
        except Exception: time.sleep(5)
    return []
def variants(n):
    v=[n]
    s=re.sub(r"\s*\(.*?\)","",n).strip()
    if s!=n: v.append(s)
    for suf in [' Theme Park',' Amusement Park',' Fun Park',' Family Park',' Adventure Park',' Resort',' Park']:
        if s.endswith(suf): v.append(s[:-len(suf)])
    if ' - ' in s: v.append(s.split(' - ')[0]); v.append(s.split(' - ')[-1])
    if not any(w in s.lower() for w in ('park','land','world','pier')): v.append(s+' amusement park')
    seen=[]; [seen.append(x) for x in v if x and x not in seen]; return seen
for p in d['parks']:
    if p.get('lat') is not None: continue
    k=str(p['id'])
    if k in out: continue
    out[k]={'how':'none'}
    for q in variants(p['name']):
        res=search(q, p['country']); time.sleep(1.1)
        hit=next((r for r in res if r.get('category') in OKCAT and r.get('type') not in BAD), None)
        if hit:
            out[k]={'lat':float(hit['lat']),'lon':float(hit['lon']),'how':'osm2','q':q,'name':hit.get('name'),'type':hit.get('type')}; break
    json.dump(out,open(OUT,'w'))
import collections; print('done',len(out),collections.Counter(v['how'] for v in out.values()))
