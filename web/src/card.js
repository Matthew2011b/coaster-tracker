// Draws the wide (1200x630) share cards as SVG, for link previews. Mirrors the in-app cards:
// ticket = park, builder's plate = manufacturer (steel US / brass world), blueprint = model.

export const W = 1200, H = 630;
const E = 18;                       // the thin black edge around every card
const SANS = "IBM Plex Sans", MONO = "IBM Plex Mono";
const TRACK = "M0 50 L12 50 C18 50 20 53 25 53 C32 53 35 40 43 40 C51 40 54 50 62 50 C66 50 68 49 70 47 L89.1 25 C93.1 20 99.1 20 102.1 26 C107.1 36 110.1 50 121.1 50 L314 50 C332 50 344 50 344.01 50 C355.6 50 365 40.6 365 29 C365 17.4 355.6 8 344 8 C332.4 8 323 17.4 323 29 C323 40.6 332.4 52.5 348 52.5 C358 52.5 364 50 378 50 L390 50";

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/** Rough text width: IBM Plex Sans SemiBold averages about 0.56 em per character, Mono 0.6. */
const width = (s, size, mono = false) => [...String(s)].length * size * (mono ? 0.6 : 0.56);

/** Fit [s] into [maxW]: shrink from [size] toward [min], then wrap onto two lines. */
function fit(s, maxW, size, min) {
  let sz = size;
  while (width(s, sz) > maxW && sz > min) sz -= 2;
  if (width(s, sz) <= maxW) return { size: sz, lines: [s] };
  const words = s.split(" ");
  let best = null;
  for (let i = 1; i < words.length; i++) {
    const a = words.slice(0, i).join(" "), b = words.slice(i).join(" ");
    const w = Math.max(width(a, 1), width(b, 1));
    if (!best || w < best.w) best = { a, b, w };
  }
  if (!best) return { size: min, lines: [s] };
  let s2 = size;
  while (best.w * s2 > maxW && s2 > min * 0.8) s2 -= 2;
  return { size: s2, lines: [best.a, best.b] };
}

function textLines(t, x, y, opts) {
  const { size, lh = 1.08, fill, weight = 600, anchor = "start", family = SANS, extra = "" } = opts;
  return t.lines.map((l, i) =>
    `<text x="${x}" y="${y + i * size * lh}" font-family="${family}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}" ${extra}>${esc(l)}</text>`).join("");
}

function icon(x, y, s) {
  const k = s / 108;
  const car = (tx, ty) => `<rect x="-6" y="-5" width="12" height="9" rx="2" fill="#fff" transform="translate(${tx} ${ty}) rotate(-49)"/>`;
  return `<g transform="translate(${x} ${y}) scale(${k})"><rect width="108" height="108" rx="24" fill="#25282A"/>
    <g transform="translate(54 58) scale(.6) translate(-54 -58)"><path d="M30,88V62 M46,88V44 M62,88V30 M80,88V48" stroke="#F0A04B" stroke-opacity=".4" stroke-width="2.5"/>
    <path d="M8,88 L58,30 C64,23 70,24 74,32 L100,88" stroke="#F0A04B" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
    ${car(26, 64)}${car(36, 52)}${car(46, 40)}</g></g>`;
}

/** Icon plus "Lift Hill", right-aligned to [right] or left-aligned at [left]. */
function mark({ left, right, y, size, color }) {
  const tw = width("Lift Hill", size), is = size * 1.5, gap = size * 0.4;
  const x0 = left ?? right - tw - gap - is;
  return icon(x0, y - is * 0.78, is) +
    `<text x="${x0 + is + gap}" y="${y}" font-family="${SANS}" font-size="${size}" font-weight="600" fill="${color}">Lift Hill</text>`;
}

const defs = `<defs>
  <filter id="paper" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".65" numOctaves="4" seed="3" result="n"/>
    <feColorMatrix in="n" values="0 0 0 0 .45  0 0 0 0 .38  0 0 0 0 .28  0 0 0 .55 0"/></filter>
  <filter id="brush" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".002 .45" numOctaves="2" seed="7" result="n"/>
    <feColorMatrix in="n" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .6 -.05"/></filter>
</defs>`;

function frame(inner, clipR) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${defs}
  <clipPath id="card"><rect x="${E}" y="${E}" width="${W - 2 * E}" height="${H - 2 * E}" rx="${clipR}"/></clipPath>
  <rect width="${W}" height="${H}" fill="#141617"/><g clip-path="url(#card)">${inner}</g></svg>`;
}

const stat = (x, y, v, label, ink, sub, size = 60, shadow = "") =>
  (shadow ? `<text x="${x}" y="${y + 2}" font-family="${MONO}" font-size="${size}" font-weight="600" fill="${shadow}" text-anchor="middle">${esc(v)}</text>` : "") +
  `<text x="${x}" y="${y}" font-family="${MONO}" font-size="${size}" font-weight="600" fill="${ink}" text-anchor="middle">${esc(v)}</text>
   <text x="${x}" y="${y + size * 0.45}" font-family="${SANS}" font-size="${size * 0.33}" fill="${sub}" text-anchor="middle" letter-spacing="${size * 0.04}">${esc(label)}</text>`;

/** Park: ticket stub. */
export function park({ name, coasters, models, year, no }) {
  const R = W - E, band = 112, stub = 340, div = R - stub, top = E + band;
  const nm = fit(name, div - E - 100, 100, 54);
  const nameH = nm.size * 1.08 * nm.lines.length;
  const cy = (top + H - E) / 2;
  const nameY = cy - (nameH + 46) / 2 + nm.size * 0.82;
  const subY = nameY + (nm.lines.length - 1) * nm.size * 1.08 + 54;
  const sy = [cy - 150, cy - 10, cy + 130];
  const inner = `<rect x="0" y="0" width="${W}" height="${H}" fill="#fff"/>
    <rect x="0" y="0" width="${W}" height="${top}" fill="#F0A04B"/>
    <text x="${E + 48}" y="${E + 67}" font-family="${SANS}" font-size="32" font-weight="600" fill="#2A1A08" letter-spacing="5.8">PARK COMPLETE</text>
    ${mark({ right: R - 48, y: E + 67, size: 28, color: "#2A1A08" })}
    <text x="${R - 48 - width("Lift Hill", 28) - 11 - 42 - 28}" y="${E + 67}" font-family="${MONO}" font-size="32" font-weight="600" fill="#2A1A08" text-anchor="end">No. ${esc(String(no).padStart(4, "0"))}</text>
    ${textLines(nm, E + 56, nameY, { size: nm.size, fill: "#25282A" })}
    <text x="${E + 56}" y="${subY}" font-family="${SANS}" font-size="38" fill="#6E7174">Every coaster ridden</text>
    <line x1="${div}" y1="${top + 28}" x2="${div}" y2="${H - E - 28}" stroke="#C9C9CA" stroke-width="5" stroke-dasharray="14 9"/>
    ${stat(div + stub / 2, sy[0] + 20, coasters, "COASTERS", "#25282A", "#6E7174")}
    ${stat(div + stub / 2, sy[1] + 20, models, "MODELS", "#25282A", "#6E7174")}
    ${stat(div + stub / 2, sy[2] + 20, year, "FINISHED", "#25282A", "#6E7174")}
    <rect x="0" y="0" width="${W}" height="${H}" filter="url(#paper)" opacity=".45" style="mix-blend-mode:multiply"/>`;
  const notch = (y) => `<circle cx="${div}" cy="${y}" r="22" fill="#141617"/>`;
  return frame(inner, 28).replace("</svg>", `${notch(E)}${notch(H - E)}</svg>`);
}

/** Manufacturer: riveted builder's plate. */
export function maker({ make, world, coasters, models, places, no, date }) {
  const stops = world ? ["#F1D89A", "#C9A24E", "#E6C77E", "#A88434"] : ["#E4E7EA", "#AEB4BA", "#D3D7DB", "#8E959C"];
  const ink = world ? "#4A3510" : "#2B3036", hi = world ? "rgba(255,248,220,.6)" : "rgba(255,255,255,.6)";
  const R = W - E, B = H - E, col = R - 80 - 280, lx = E + 80;
  const nm = fit(make, col - lx - 40, 84, 50);
  const blockH = 34 + 20 + nm.size * 1.08 * nm.lines.length + 40 + 60;
  let y = (E + B) / 2 - blockH / 2 + 28;
  const engr = (t, x, yy, size, opts = "") => `<text x="${x}" y="${yy + 2}" font-family="${SANS}" font-size="${size}" font-weight="600" fill="${hi}" ${opts}>${esc(t)}</text>
    <text x="${x}" y="${yy}" font-family="${SANS}" font-size="${size}" font-weight="600" fill="${ink}" ${opts}>${esc(t)}</text>`;
  const kicker = engr(`MANUFACTURER COMPLETE · ${world ? "WORLDWIDE" : "UNITED STATES"}`, lx, y, 26, 'letter-spacing="5"');
  y += 26 + 20 + nm.size * 0.82;
  const nameT = nm.lines.map((l, i) => engr(l, lx, y + i * nm.size * 1.08, nm.size)).join("");
  y += (nm.lines.length - 1) * nm.size * 1.08 + 46;
  const month = new Date(date + "T12:00:00Z").toLocaleString("en-US", { month: "short", year: "numeric", timeZone: "UTC" }).toUpperCase();
  const serial = `<text x="${lx}" y="${y}" font-family="${MONO}" font-size="24" font-weight="600" fill="${ink}" opacity=".8" letter-spacing="2.5">SERIAL LH-${esc(String(no).padStart(4, "0"))} · ${esc(month)}</text>`;
  const m = mark({ left: lx, y: y + 58, size: 28, color: ink });
  const rivet = (cx, cy) => `<circle cx="${cx}" cy="${cy}" r="12" fill="url(#rv)"/>`;
  const cyc = (E + B) / 2, sx = R - 80 - 140;
  const inner = `<defs><linearGradient id="g" x1="0" y1="0" x2=".75" y2="1.4">${stops.map((c, i) => `<stop offset="${[0, .45, .7, 1][i]}" stop-color="${c}"/>`).join("")}</linearGradient>
      <radialGradient id="rv" cx=".35" cy=".35" r=".9"><stop offset="0" stop-color="${hi}"/><stop offset="1" stop-color="${ink}"/></radialGradient></defs>
    <rect x="0" y="0" width="${W}" height="${H}" fill="url(#g)"/>
    <rect x="0" y="0" width="${W}" height="${H}" filter="url(#brush)" opacity=".9" style="mix-blend-mode:overlay"/>
    <rect x="${E + 5}" y="${E + 5}" width="${W - 2 * E - 10}" height="${H - 2 * E - 10}" rx="12" fill="none" stroke="${hi}" stroke-width="10"/>
    ${rivet(E + 36, E + 36)}${rivet(R - 36, E + 36)}${rivet(E + 36, B - 36)}${rivet(R - 36, B - 36)}
    ${kicker}${nameT}${serial}${m}
    <line x1="${col}" y1="${cyc - 170}" x2="${col}" y2="${cyc + 170}" stroke="${ink}" stroke-opacity=".25" stroke-width="3"/>
    ${stat(sx, cyc - 110, coasters, "COASTERS", ink, ink, 60, hi)}
    ${stat(sx, cyc + 20, models, "MODELS", ink, ink, 60, hi)}
    ${stat(sx, cyc + 150, places, world ? "COUNTRIES" : "PARKS", ink, ink, 60, hi)}`;
  return frame(inner, 16);
}

/** Model: blueprint with title block and a COMPLETE stamp. */
export function model({ make, model: md, world, count, no, date }) {
  const paper = world ? "#0F2E55" : "#163F73", stamp = world ? "#FFC93C" : "#F0A04B";
  const L = E + 28, T = E + 28, R = W - E - 28, B = H - E - 28, iw = R - L;
  let grid = "";
  for (let x = E; x < W; x += 36) grid += `<line x1="${x}" y1="0" x2="${x}" y2="${H}" stroke="#fff" stroke-opacity=".07" stroke-width="2"/>`;
  for (let y = E; y < H; y += 36) grid += `<line x1="0" y1="${y}" x2="${W}" y2="${y}" stroke="#fff" stroke-opacity=".07" stroke-width="2"/>`;
  const st = `${world ? "WORLD" : "US"} COMPLETE`, sw = width(st, 34) + 14 * 2.2 + 40;
  const nm = fit(md, iw - 64 - sw - 40, 80, 48);
  const titleY = T + 24 + nm.size * 0.82;
  const byY = titleY + (nm.lines.length - 1) * nm.size * 1.06 + 46;
  const k = iw / 390, trackTop = 248;
  const cellsTop = B - 84, cw = iw / 4;
  const cells = [["SCOPE", world ? "WORLDWIDE" : "UNITED STATES"], ["QTY RIDDEN", `${count} OF ${count}`], ["DATE", date], ["DWG NO.", `LH-${String(no).padStart(4, "0")}`]]
    .map(([a, b], i) => `<rect x="${L + i * cw}" y="${cellsTop}" width="${cw}" height="84" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="2"/>
      <text x="${L + i * cw + 16}" y="${cellsTop + 28}" font-family="${SANS}" font-size="18" fill="#fff" fill-opacity=".7" letter-spacing="2.2">${a}</text>
      <text x="${L + i * cw + 16}" y="${cellsTop + 66}" font-family="${MONO}" font-size="30" font-weight="600" fill="#fff">${esc(b)}</text>`).join("");
  const sx = R - 32 - sw, sy = T + 34;
  const inner = `<rect x="0" y="0" width="${W}" height="${H}" fill="${paper}"/>${grid}
    <rect x="0" y="0" width="${W}" height="${H}" filter="url(#paper)" opacity=".3" style="mix-blend-mode:multiply"/>
    <rect x="${L}" y="${T}" width="${iw}" height="${B - T}" fill="none" stroke="#fff" stroke-opacity=".7" stroke-width="3"/>
    ${textLines(nm, L + 32, titleY, { size: nm.size, fill: "#fff", lh: 1.06 })}
    <text x="${L + 32}" y="${byY}" font-family="${SANS}" font-size="34" fill="#fff">By ${esc(make)}</text>
    <g transform="rotate(-8 ${sx + sw / 2} ${sy + 30})"><rect x="${sx}" y="${sy}" width="${sw}" height="60" rx="10" fill="none" stroke="${stamp}" stroke-width="6"/>
      <text x="${sx + sw / 2}" y="${sy + 42}" font-family="${SANS}" font-size="34" font-weight="600" fill="${stamp}" text-anchor="middle" letter-spacing="4">${st}</text></g>
    <path d="${TRACK}" transform="translate(${L} ${trackTop}) scale(${k})" fill="none" stroke="#fff" stroke-width="${3 / k}" stroke-linecap="round" stroke-linejoin="round"/>
    ${cells}`;
  return frame(inner, 12);
}
