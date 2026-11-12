import { readFileSync, writeFileSync, mkdirSync } from "fs";
import { initWasm, Resvg } from "@resvg/resvg-wasm";
import * as C from "./src/card.js";
await initWasm(readFileSync("node_modules/@resvg/resvg-wasm/index_bg.wasm"));
const fonts = ["plex_sans_regular", "plex_sans_semibold", "plex_mono_semibold"].map(f => new Uint8Array(readFileSync(`fonts/${f}.ttf`)));
mkdirSync("out", { recursive: true });
const cards = {
  park: C.park({ name: "Cedar Point", coasters: 17, models: 12, year: 2026, no: 42 }),
  park_long: C.park({ name: "Six Flags Great Adventure & Safari", coasters: 14, models: 13, year: 2026, no: 142 }),
  maker_us: C.maker({ make: "Bolliger & Mabillard", world: false, coasters: 28, models: 9, places: 14, no: 42, date: "2026-11-12" }),
  maker_world: C.maker({ make: "Bolliger & Mabillard", world: true, coasters: 133, models: 11, places: 19, no: 42, date: "2026-11-12" }),
  model_us: C.model({ make: "Bolliger & Mabillard", model: "Wing Coaster", world: false, count: 3, no: 42, date: "2026-11-12" }),
  model_world: C.model({ make: "Vekoma", model: "Suspended Looping Coaster (689m Standard)", world: true, count: 7, no: 42, date: "2026-11-12" }),
};
for (const [k, svg] of Object.entries(cards)) {
  const png = new Resvg(svg, { font: { fontBuffers: fonts, loadSystemFonts: false, defaultFontFamily: "IBM Plex Sans" } }).render().asPng();
  writeFileSync(`out/${k}.png`, png); console.log(k, png.length);
}
