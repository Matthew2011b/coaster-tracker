import { readFileSync } from "fs";
import { initWasm, Resvg } from "@resvg/resvg-wasm";
import * as C from "./src/card.js";
await initWasm(readFileSync("node_modules/@resvg/resvg-wasm/index_bg.wasm"));
const fonts = ["plex_sans_regular", "plex_sans_semibold", "plex_mono_semibold"].map(f => new Uint8Array(readFileSync(`fonts/${f}.ttf`)));
const svgs = { park: C.park({ name: "Cedar Point", coasters: 17, models: 12, year: 2026, no: 42 }),
  maker: C.maker({ make: "Bolliger & Mabillard", world: true, coasters: 133, models: 11, places: 19, no: 42, date: "2026-11-12" }),
  model: C.model({ make: "Bolliger & Mabillard", model: "Wing Coaster", world: false, count: 3, no: 42, date: "2026-11-12" }) };
for (const [k, s] of Object.entries(svgs)) for (const tex of [true, false]) {
  const svg = tex ? s : s.replace(/<rect[^>]*filter="url\(#(paper|brush)\)"[^>]*\/>/g, "");
  let t0 = performance.now(); let png;
  for (let i = 0; i < 5; i++) png = new Resvg(svg, { font: { fontBuffers: fonts, loadSystemFonts: false } }).render().asPng();
  console.log(k, tex ? "tex" : "plain", ((performance.now() - t0) / 5).toFixed(0) + "ms", png.length);
}
