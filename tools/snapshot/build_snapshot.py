"""Build the bundled coaster snapshot from a Coasterpedia pull.

Inputs (paths relative to this file unless absolute):
  coasters.json  - /v1/attractions?type=coaster, all pages
  parks.json     - /v1/parks, all pages
  places.json    - optional {park_id: {"place": city, "state": ...}} from /v1/parks/{id} for US parks
  wikidata.csv   - Wikidata roller coasters with manufacturer labels
  aliases.json   - manufacturer alias table
Output:
  snapshot.json  - {"generated":..., "licence":..., "parks":[...], "coasters":[...]}
"""
import csv, json, re, sys, collections
from datetime import date

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/claude-0/cp"
WD = sys.argv[2] if len(sys.argv) > 2 else "/tmp/claude-0/wd/wd3.csv"
HERE = "/home/claude/data"

aliases = json.load(open(f"{HERE}/aliases.json"))
aliases.update({
    "Custom Coasters International": "Custom Coasters International, Inc.",
    "Philadelphia Toboggan Company": "Philadelphia Toboggan Coasters",
    "Maurer Söhne": "Maurer Rides",
    "Intamin Amusement Rides": "Intamin",
    "Bolliger and Mabillard": "Bolliger & Mabillard",
    "Rocky Mountain Coasters": "Rocky Mountain Construction",
    "Togo": "TOGO",
})

def make_name(n):
    return aliases.get(n, n) if n else None

# Model names: manufacturer-specific merges to family level.
MODEL_MAP = {
    "Vekoma": {
        "MK-1200 Tornado": "MK-1200", "Custom MK-1200": "MK-1200", "MK-1200 (Whirlwind)": "MK-1200",
        "Corkscrew with Bayerncurve": "MK-1200", "Corkscrew with bayerncurve": "MK-1200", "Whirlwind": "MK-1200",
        "Flying Dutchman": "Flying Coaster", "Flying Dutchman)": "Flying Coaster", "Stingray": "Flying Coaster",
        "LSM Coaster (Disney)": "LSM Coaster", "LSM Coaster (996m)": "LSM Coaster",
        "Custom Designed Family Coaster": "Family Coaster",
        "Custom MK-700": "MK-700", "Custom MK-900": "MK-900",
    },
    "Zamperla": {
        "Custom Powered Coaster": "Powered Coaster", "Family Gravity Coaster": "Family Coaster",
        "Air Force 5": "Air Force", "Custom Air Force": "Air Force",
        "Family Thrill Launch Coaster": "Family Thrill Launch",
    },
    "SBF Visa Group": {"Spinning Coaster": "Spinning Coasters"},
    "Intamin": {"Half Pipe Coaster": "Half Pipe", "Indoor/Family Roller Coaster": "Indoor / Family Roller Coaster",
                "Wood Coaster": "Wooden Coaster", "Zac Spin": "ZacSpin"},
    "Mack Rides": {"Wild Maus": "Wilde Maus", "Wild Mouse": "Wilde Maus"},
    "Zierer": {"Tivoli New": "Tivoli", "Tivoli Small": "Tivoli", "Elevated Seating Coaster": "ESC"},
    "Pinfari": {"Supersonic": "Zyklon", "Super Train ST40": "Super Train", "FC80": "FC80 Family Coaster",
                "XP48": "Xpress", "Xpress XP56": "Xpress"},
    "Schwarzkopf": {"Alpenblitz II": "Alpen Blitz", "Alpen Blitz II": "Alpen Blitz", "Jet Star II": "Jet Star",
                    "Jet Star III": "Jet Star", "Jet Star 2": "Jet Star", "City Jet": "Jet Star",
                    "City Jet / Jet 400": "Jet Star", "Jumbo V": "Jet Star"},
    "E&F Miler Industries": {"Childrens Coaster": "Children's Coaster"},
    "S&S Worldwide": {"Launched El Loco": "El Loco"},
}
PREFIX_MAP = {  # make: [(prefix or regex, family)]
    "Zamperla": [(r"^Powered Coaster", "Powered Coaster"), (r"^Twister Coaster", "Twister Coaster"),
                 (r"^Family Coaster", "Family Coaster")],
    "Zierer": [(r"^Tivoli", "Tivoli")],
    "Pinfari": [(r"^(Zyklon|Z\d|ZL\d)", "Zyklon"), (r"^Super Dragon", "Super Dragon")],
    "Mack Rides": [(r"^Wil(d|de) Ma?us", "Wilde Maus")],
    "E&F Miler Industries": [(r"^(Family Coaster|\d|Single Helix|Oval)", "Family Coaster"),
                             (r"^Children'?s Coaster", "Children's Coaster")],
    "Wisdom Rides": [(r"^Dragon Wagon", "Dragon Wagon"), (r"^Miner Mike", "Miner Mike")],
    "Jinma Rides": [(r"^Fruit Worm", "Fruit Worm Coaster"), (r"^Mine Coaster", "Mine Coaster"),
                    (r"^Suspended Roller Coaster", "Suspended Roller Coaster"), (r"^Spinning Coaster", "Spinning Coaster")],
    "SBF Visa Group": [(r"^Double Coaster", "Double Coaster")],
    "Vekoma": [(r"^MK-1200", "MK-1200")],
}

def model_name(make, raw):
    if not raw:
        return None
    if make == "Philadelphia Toboggan Coasters" and raw.strip() == "Wooden Coaster":
        return None  # PTC woodies are custom builds
    m = raw.strip()
    m = MODEL_MAP.get(make, {}).get(m, m)
    for pat, fam in PREFIX_MAP.get(make, []):
        if re.match(pat, m):
            return fam
    m = re.sub(r"\s*\((product)\)$", "", m)
    m = re.sub(r"\s*\([A-Z]{1,4}[- ]?\d[\w/.-]*\)$", "", m)   # trailing model codes like (MX610)
    m = MODEL_MAP.get(make, {}).get(m, m)
    return m.strip() or None

# Per-coaster fixes (Coasterpedia id -> fields), mostly from the original 42 credits.
OVERRIDES = {
    2458: {"make": "Great Coasters International", "model": "Wooden Coaster"},   # Thunderhead
    10543: {"model": "Topper Track"},                                              # Lightning Rod, built as Topper
    187: {"model": "Special Coaster Systems"},
    205: {"make": "Intamin", "model": "Accelerator Coaster"},                      # Top Thrill 2 (Zamperla rebuild)                                     # Gemini
    759: {"make": "In-house"},                                                     # The Beast
    3405: {"make": "In-house"},                                                    # Blazing Fury
}

def norm(s):
    return re.sub(r"[^a-z0-9]", "", (s or "").lower().replace("the ", ""))

# Wikidata manufacturer fills, matched by coaster name + park.
wd = collections.defaultdict(lambda: {"mfr": set(), "park": set(), "name": ""})
for r in csv.DictReader(open(WD)):
    e = wd[r["c"]]
    e["name"] = r["cLabel"]
    if r["mfrLabel"]:
        e["mfr"].add(r["mfrLabel"])
    if r["parkLabel"]:
        e["park"].add(r["parkLabel"])
wd_by_name = collections.defaultdict(list)
for e in wd.values():
    wd_by_name[norm(e["name"])].append(e)

def wikidata_make(a):
    cands = wd_by_name.get(norm(a["name"]), [])
    pk = norm((a.get("park") or {}).get("name"))
    good = [e for e in cands if any(norm(p) == pk for p in e["park"])]
    pick = good[0] if good else (cands[0] if len(cands) == 1 else None)
    if pick and len(pick["mfr"]) == 1:
        return next(iter(pick["mfr"]))
    return None

STATUS = {"operating": "operating", "under_refurbishment": "operating", "standing_but_not_operating": "sbno",
          "defunct": "defunct", "in_storage": "defunct", "under_construction": "upcoming", "planned": "upcoming",
          "unknown": "unknown"}
TODAY = date.today().isoformat()

coasters_raw = json.load(open(f"{SRC}/coasters.json"))
parks_raw = {p["id"]: p for p in json.load(open(f"{SRC}/parks.json"))}

stats = collections.Counter()
coasters = []
for a in coasters_raw:
    st = STATUS.get(a["status"])
    if st is None:  # cancelled etc.
        stats["dropped_status"] += 1
        continue
    mk = make_name(a["manufacturers"][0]["name"]) if a.get("manufacturers") else None
    if not mk:
        w = wikidata_make(a)
        if w:
            mk = make_name(w)
            stats["wikidata_make"] += 1
    prods = a.get("products") or []
    mo = model_name(mk, prods[0]["product"]["name"]) if prods else None
    o = OVERRIDES.get(a["id"], {})
    mk = o.get("make", mk)
    mo = o.get("model", mo)
    sbno_since = None
    if st == "sbno":
        closed = (a.get("closed") or {}).get("latest")
        sbno_since = closed or TODAY
        # Initial rule: B&M hypers/gigas are treated as running; restart their clock today.
        if mk == "Bolliger & Mabillard" and mo in ("Hyper Coaster", "Giga Coaster"):
            sbno_since = TODAY
            stats["bm_hyper_reset"] += 1
    s = a.get("stats") or {}
    def val(k):
        v = s.get(k)
        return round(v["value"], 2) if v and v.get("value") is not None else None
    park = a.get("park") or {}
    coasters.append({
        "id": a["id"], "name": a["name"], "parkId": park.get("id"), "parkName": park.get("name"),
        "country": (a.get("country") or {}).get("code"), "locationKind": a.get("location_kind"),
        "status": st, "sbnoSince": sbno_since,
        "opened": (a.get("opened") or {}).get("earliest"), "closed": (a.get("closed") or {}).get("latest"),
        "make": mk, "model": mo, "heightM": val("height"), "speedKmh": val("speed"),
    })
    stats["kept"] += 1
    stats["no_make"] += mk is None
    stats["no_model"] += mo is None

used_parks = {c["parkId"] for c in coasters if c["parkId"]}
try:
    places = json.load(open(f"{SRC}/places.json"))
except FileNotFoundError:
    places = {}
parks = []
for pid in sorted(used_parks):
    p = parks_raw.get(pid)
    if not p:
        stats["park_missing"] += 1
        continue
    co = p.get("coordinates") or {}
    parks.append({"id": pid, "name": p["name"], "country": (p.get("country") or {}).get("code"),
                  "lat": co.get("lat"), "lon": co.get("lon"), "status": p.get("status"),
                  "city": (places.get(str(pid)) or {}).get("place"),
                  "state": p.get("state") or (places.get(str(pid)) or {}).get("state")})

out = {"generated": TODAY, "source": "Coasterpedia (coasterpedia.net), CC BY-SA 4.0; manufacturer gaps partly from Wikidata (CC0)",
       "parks": parks, "coasters": coasters}
json.dump(out, open(f"{HERE}/snapshot.json", "w"), ensure_ascii=False, separators=(",", ":"))
models = collections.Counter((c["make"], c["model"]) for c in coasters if c["make"] and c["model"])
print(dict(stats), "parks", len(parks), "make+model", len(models))
