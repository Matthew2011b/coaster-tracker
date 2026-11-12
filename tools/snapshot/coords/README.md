# Park coordinates

Coasterpedia has no coordinates for about 670 parks. These scripts filled them (2026-11-12), in order:

1. `geocode.py`: OpenStreetMap Nominatim search by park name + town/state (US) or country; town center fallback.
2. `pass2.py`: name variants for parks still missing.
3. `verify.py`: re-query pass-1 hits to record OSM type and returned name.
4. `towns.py` + `pass3.py`: Coasterpedia park detail pages give the town for non-US parks; search name + town.
5. `pass4.py`: US parks still at a town center, searched by name + state.
6. `decide.py`: picks per park with checks (US/Canadian points must fall inside the park's state or province;
   Wikidata's point wins when it and OSM disagree by more than 2 km; generic-park matches need a name match),
   then `manual.json` for the last few placed by hand. Patches assets/snapshot.json.

Sources: OpenStreetMap contributors (ODbL), Wikidata (CC0), Natural Earth admin-1 polygons for the region check.
Nominatim policy: one request per second with a real User-Agent. Paths in the scripts point at the scratch
folders used at the time; adjust before rerunning.
