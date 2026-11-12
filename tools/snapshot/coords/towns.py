import json, time, urllib.request, re, os
K=re.search(r'cpk_[A-Za-z0-9]+', open('/tmp/claude-0/cp/pull.py').read()).group(0)
g2=json.load(open('/tmp/claude-0/geo/geo2.json'))
ids=[k for k,v in g2.items() if v.get('how')=='none'] + [str(x['id']) for x in json.load(open('/tmp/claude-0/geo/left.json'))]
OUT='/tmp/claude-0/geo/towns.json'
out=json.load(open(OUT)) if os.path.exists(OUT) else {}
for i in ids:
    if i in out: continue
    for a in range(5):
        try:
            req=urllib.request.Request(f"https://api.coasterpedia.net/v1/parks/{i}", headers={"Authorization":"Bearer "+K,"User-Agent":"lift-hill-dev/0.2"})
            with urllib.request.urlopen(req,timeout=60) as r: dd=json.load(r)["data"]
            out[i]={"place":dd.get("place"),"state":dd.get("state"),"website":dd.get("website"),"url":dd.get("url")}; break
        except urllib.error.HTTPError as e:
            if e.code==429: time.sleep(int(e.headers.get("Retry-After") or 30)); continue
            out[i]={"err":e.code}; break
        except Exception: time.sleep(5)
    json.dump(out,open(OUT,'w')); time.sleep(1.05)
print('done',len(out),sum(1 for v in out.values() if v.get('place')))
