// Pre-draws a preview image for every park, model and manufacturer in the app's data, so the free
// Cloudflare plan only has to serve files. Runs in GitHub Actions whenever the data or the cards change.
import { readFileSync, writeFileSync, mkdirSync, rmSync, copyFileSync } from "fs";
import { Resvg } from "@resvg/resvg-js";
import sharp from "sharp";
import * as C from "./src/card.js";

const snap = JSON.parse(readFileSync(new URL("../app/src/main/assets/snapshot.json", import.meta.url)));
const fontFiles = ["plex_sans_regular", "plex_sans_semibold", "plex_mono_semibold"].map((f) => new URL(`fonts/${f}.ttf`, import.meta.url).pathname);
const OUT = new URL("public/img/", import.meta.url).pathname;
const only = process.argv[2] ? Number(process.argv[2]) : Infinity;   // limit for quick local checks

async function save(svg, path) {
  const png = new Resvg(svg, { font: { fontFiles, loadSystemFonts: false, defaultFontFamily: "IBM Plex Sans" } }).render().asPng();
  await sharp(png).jpeg({ quality: 80, mozjpeg: true }).toFile(OUT + path);
}

// Texture tiles: paper grain (brown specks) and brushed metal (white streaks along each row). Fixed seeds.
function rng(seed) { return () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296); }
async function tile(w, h, fill) { const px = Buffer.alloc(w * h * 4); fill(px, w, h); return sharp(px, { raw: { width: w, height: h, channels: 4 } }).png().toBuffer(); }
const r1 = rng(3), r2 = rng(7);
const paper = await tile(256, 256, (px) => { for (let i = 0; i < px.length; i += 4) { px[i] = 0x73; px[i + 1] = 0x61; px[i + 2] = 0x47; px[i + 3] = Math.floor(r1() * 120); } });
const brushed = await tile(1024, 256, (px, w, h) => {
  for (let y = 0; y < h; y++) { let v = r2(); for (let x = 0; x < w; x++) {
    v = Math.min(1, Math.max(0, v + (r2() - 0.5) * 0.04)); const i = (y * w + x) * 4;
    px[i] = px[i + 1] = px[i + 2] = 255; px[i + 3] = Math.floor(Math.min(1, v * 0.5 + r2() * 0.08) * 255); } }
});
C.setTextures({ paper: "data:image/png;base64," + paper.toString("base64"), brushed: "data:image/png;base64," + brushed.toString("base64") });

rmSync(OUT, { recursive: true, force: true });
mkdirSync(new URL("public/", import.meta.url).pathname, { recursive: true });
copyFileSync(new URL("src/card.js", import.meta.url), new URL("public/card.js", import.meta.url));  // the landing page draws your own card with it
mkdirSync(new URL("public/tex/", import.meta.url).pathname, { recursive: true });
writeFileSync(new URL("public/tex/paper.png", import.meta.url), paper);
writeFileSync(new URL("public/tex/brushed.png", import.meta.url), brushed);
for (const d of ["p", "m", "k"]) mkdirSync(OUT + d, { recursive: true });

const parks = new Map(snap.parks.map((p) => [p.id, p]));
const withCoasters = [...new Set(snap.coasters.map((c) => c.parkId).filter((id) => parks.has(id)))];
const models = new Map(), makers = new Map();
for (const c of snap.coasters) {
  const us = c.country === "US";
  if (c.make && c.model) {
    const m = models.get(`${c.make}|${c.model}`) ?? { make: c.make, model: c.model, all: 0, us: 0 };
    m.all++; if (us) m.us++; models.set(`${c.make}|${c.model}`, m);
  }
  if (c.make) {
    const k = makers.get(c.make) ?? { make: c.make, all: 0, us: 0 };
    k.all++; if (us) k.us++; makers.set(c.make, k);
  }
}

// Names for the share pages, so links carry only ids and numbers (chat apps mangle names in links).
const SHORT = { "Bolliger & Mabillard": "B&M", "Great Coasters International": "GCI", "Rocky Mountain Construction": "RMC",
  "Philadelphia Toboggan Coasters": "PTC", "Custom Coasters International, Inc.": "CCI" };  // same as shortMake() in the app
const names = { p: {}, m: {}, k: {} };
for (const id of withCoasters) names.p[id] = parks.get(id).name;
for (const m of models.values()) names.m[`${C.slug(m.make)}--${C.slug(m.model)}`] = [m.make, m.model, SHORT[m.make] ?? m.make];
for (const k of makers.values()) names.k[C.slug(k.make)] = k.make;
writeFileSync(new URL("public/names.json", import.meta.url), JSON.stringify(names));

const jobs = [];
for (const id of withCoasters) jobs.push([C.park({ name: parks.get(id).name, generic: true }), `p/${id}.jpg`]);
for (const m of models.values()) for (const world of [false, true]) {
  if ((world ? m.all : m.us) < 2) continue;
  jobs.push([C.model({ make: m.make, model: m.model, world, generic: true }), `m/${C.slug(m.make)}--${C.slug(m.model)}-${world ? "world" : "us"}.jpg`]);
}
for (const k of makers.values()) for (const world of [false, true]) {
  if ((world ? k.all : k.us) < 2) continue;
  jobs.push([C.maker({ make: k.make, world, generic: true }), `k/${C.slug(k.make)}-${world ? "world" : "us"}.jpg`]);
}
// Fallbacks for anything not in the data (coasters and parks people added themselves).
jobs.push([C.park({ name: "Every Coaster Ridden", generic: true, sub: false }), "fallback-park.jpg"]);
for (const world of [false, true]) {
  const sc = world ? "world" : "us";
  jobs.push([C.model({ make: "", model: "Model Complete", world, generic: true }), `fallback-model-${sc}.jpg`]);
  jobs.push([C.maker({ make: "Manufacturer Complete", world, generic: true }), `fallback-maker-${sc}.jpg`]);
}

const t0 = Date.now();
const todo = jobs.slice(-5).concat(jobs.slice(0, Math.min(only, jobs.length - 5)));
let n = 0;
const lanes = 4;
await Promise.all(Array.from({ length: lanes }, async (_, lane) => {
  for (let i = lane; i < todo.length; i += lanes) { await save(...todo[i]); n++; }
}));
console.log(`${n} images in ${((Date.now() - t0) / 1000).toFixed(0)} s (of ${jobs.length} total)`);
