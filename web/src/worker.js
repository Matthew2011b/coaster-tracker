// Share links: lift-hill.lifthill.workers.dev/p/<park id>, /m/<make>--<model>-<us|world>, /k/<make>-<us|world>,
// with only numbers in the query (c coasters, m models, p places, r ridden, y year, d date, n credit number).
// Names come from public/names.json, written with the images, because chat apps mangle names inside links.
// Each page gives chat apps a preview (title, line, pre-drawn card from public/img) and shows people who
// open it their own card, drawn in the browser.

const PLAY = null;  // the Play Store link, once the app is published
const esc = (s) => String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const num = (v, max = 99999) => Math.max(0, Math.min(max, parseInt(v, 10) || 0));
const text = (v, max = 80) => String(v ?? "").slice(0, max);
const date = (v) => (/^\d{4}-\d{2}-\d{2}$/.test(v ?? "") ? v : new Date().toISOString().slice(0, 10));

let NAMES = null;  // kept between requests while the worker stays warm
async function names(env, origin) {
  if (!NAMES) { const r = await env.ASSETS.fetch(new Request(origin + "/names.json")); NAMES = r.ok ? await r.json() : { p: {}, m: {}, k: {} }; }
  return NAMES;
}

/** Splits "<slug>-us" / "<slug>-world" into the slug and scope. Links from 0.17 put the scope in w=. */
function scoped(key, q) {
  const m = key.match(/^(.*)-(us|world)$/);
  return m ? [m[1], m[2] === "world"] : [key, q.get("w") === "1"];
}

function card(url, N) {
  const [, kind, raw = ""] = url.pathname.split("/");
  const q = url.searchParams;
  const old = q.has("no");                          // 0.17 links: n = name, no = credit
  const credit = num(old ? q.get("no") : q.get("n"));
  if (kind === "p") {
    const id = num(raw, 1e9);
    const d = { name: N.p[id] ?? (old ? text(q.get("n")) : "") ?? "", coasters: num(q.get("c")), models: num(q.get("m")),
      year: num(q.get("y"), 9999) || new Date().getFullYear(), no: credit };
    return { kind: "park", data: { ...d, name: d.name || "Park Complete" }, title: d.name || "Park Complete",
      line: `${d.coasters} coasters, ${d.models} models. Credit #${credit}.`, img: [`/img/p/${id}.jpg`, "/img/fallback-park.jpg"] };
  }
  const [key, world] = scoped(raw.replace(/[^a-z0-9-]/g, ""), q);
  const scope = world ? "Worldwide" : "US", sc = world ? "world" : "us";
  if (kind === "m") {
    const [make, model, short] = N.m[key] ?? (old ? [text(q.get("mk")), text(q.get("md")), text(q.get("sm")) || text(q.get("mk"))] : ["", "", ""]);
    const d = { make, model: model || "Model Complete", world, count: num(q.get("r")), no: credit, date: date(q.get("d")) };
    return { kind: "model", data: d, title: model ? `${short} ${model} (${scope})` : `Model Complete (${scope})`,
      line: `${d.count} of ${d.count} ridden. Credit #${credit}.`, img: [`/img/m/${key}-${sc}.jpg`, `/img/fallback-model-${sc}.jpg`] };
  }
  if (kind === "k") {
    const make = N.k[key] ?? (old ? text(q.get("mk")) : "");
    const d = { make: make || "Manufacturer Complete", world, coasters: num(q.get("c")), models: num(q.get("m")), places: num(q.get("p")), no: credit, date: date(q.get("d")) };
    return { kind: "maker", data: d, title: make ? `${make} (${scope})` : `Manufacturer Complete (${scope})`,
      line: `${d.coasters} coasters, ${d.models} models, ${d.places} ${world ? "countries" : "parks"}. Credit #${credit}.`,
      img: [`/img/k/${key}-${sc}.jpg`, `/img/fallback-maker-${sc}.jpg`] };
  }
  return null;
}

const page = (c, img, url) => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(c ? c.title : "Lift Hill")}</title>
<meta property="og:type" content="website"><meta property="og:site_name" content="Lift Hill">
<meta property="og:title" content="${esc(c ? c.title : "Lift Hill")}">
<meta property="og:description" content="${esc(c ? c.line + " Track your coaster credits with Lift Hill." : "Track every coaster you ride by manufacturer and model.")}">
<meta property="og:url" content="${esc(url.href)}">
${img ? `<meta property="og:image" content="${esc(url.origin + img)}"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:image" content="${esc(url.origin + img)}">` : ""}
<meta name="theme-color" content="#F0A04B">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@600&family=IBM+Plex+Sans:wght@400;600&display=swap">
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#141617;color:#F3F3F3;font-family:'IBM Plex Sans',system-ui,sans-serif;display:flex;justify-content:center}
main{width:100%;max-width:720px;padding:28px 16px 40px;display:flex;flex-direction:column;align-items:center;gap:22px;text-align:center}
.brand{display:flex;align-items:center;gap:10px;font-weight:600;font-size:22px}
#card svg,#card img{width:100%;height:auto;display:block;border-radius:10px}#card{width:100%}
p{margin:0;color:#B9BCBE;font-size:16px;line-height:1.5;max-width:520px}
.btn{display:block;width:100%;max-width:420px;padding:15px;border-radius:10px;background:#F0A04B;color:#2A1A08;font-weight:600;font-size:17px;text-decoration:none}
.btn.off{opacity:.75}
</style></head><body><main>
<div class="brand"><svg width="34" height="34" viewBox="0 0 108 108" aria-hidden="true"><rect width="108" height="108" rx="24" fill="#25282A"/><g transform="translate(54 58) scale(.6) translate(-54 -58)"><path d="M8,88 L58,30 C64,23 70,24 74,32 L100,88" stroke="#F0A04B" stroke-width="5" fill="none" stroke-linecap="round"/></g></svg>Lift Hill</div>
<div id="card">${img ? `<img src="${esc(img)}" alt="${esc(c ? c.title : "")}">` : ""}</div>
${c ? `<h1 style="margin:0;font-size:22px">${esc(c.title)}</h1>` : ""}
<p>Track every coaster you ride by manufacturer and model, see what you've completed, and find the nearest models you've never ridden.</p>
${PLAY ? `<a class="btn" href="${PLAY}">Get it on Google Play</a>` : `<span class="btn off">Coming soon to Google Play</span>`}
</main>
${c ? `<script type="module">
import * as C from "/card.js";
C.setFramed(false);
const svg = C[${JSON.stringify(c.kind)}](${JSON.stringify(c.data).replace(/</g, "\\u003c")});
document.fonts.ready.then(() => { document.getElementById("card").innerHTML = svg.replace("<svg ", '<svg role="img" aria-label="Your card" '); });
</script>` : ""}
</body></html>`;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const c = card(url, await names(env, url.origin));
    let img = null;
    if (c) for (const path of c.img) {
      const r = await env.ASSETS.fetch(new Request(url.origin + path, { method: "HEAD" }));
      if (r.ok) { img = path; break; }
    }
    return new Response(page(c, img, url), {
      status: c || url.pathname === "/" ? 200 : 404,
      headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" },
    });
  },
};
