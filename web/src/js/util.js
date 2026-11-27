export const $ = (id) => document.getElementById(id);
export const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
export const time = (m) => { m = +m || 0; const h = Math.floor(m / 60); return h > 0 ? `${h} h ${m % 60} min` : `${m % 60} min`; };
export const safeColor = (c) => (/^#[0-9a-fA-F]{6}$/.test(c) ? c : "#a88d83");

/* Pixelová hlava 8×8 vygenerovaná ze jména (když nemáme skin ze serveru). */
const HEADS = new Map(), HEADPX = new Map();
/** barvy pixelů hlavy (8×8) pro 3D postavu */
export function pixelHeadData(name) { if (!HEADPX.has(name)) pixelHead(name); return HEADPX.get(name); }
export function pixelHead(name) {
  if (HEADS.has(name)) return HEADS.get(name);
  let h = 2166136261; for (const ch of String(name)) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); }
  const r = () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) >>> 0) / 4294967296);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const skin = pick(["#f2c39b", "#e0a878", "#c68a5a", "#8d5a3b", "#ffd9b8", "#b07a52"]);
  const hair = pick(["#3b2416", "#1e1b1b", "#c9a227", "#7a3a1c", "#e8e2d8", "#b0302a", "#2f4f9e", "#6b3fa0"]);
  const eye = pick(["#3a6fd8", "#3ba55c", "#7a4a2a", "#5b5b6b", "#b03ad8"]);
  const c = document.createElement("canvas"); c.width = c.height = 8; const x = c.getContext("2d");
  const px = (cx, cy, col) => { x.fillStyle = col; x.fillRect(cx, cy, 1, 1); };
  const shade = (col, k) => { const n = parseInt(col.slice(1), 16); const f = (v) => Math.max(0, Math.min(255, Math.round(v * k))); return `rgb(${f(n >> 16)},${f((n >> 8) & 255)},${f(n & 255)})`; };
  for (let y = 0; y < 8; y++) for (let i = 0; i < 8; i++) px(i, y, shade(skin, .92 + r() * .14));
  for (let i = 0; i < 8; i++) { px(i, 0, shade(hair, .9 + r() * .2)); px(i, 1, shade(hair, .85 + r() * .2)); }
  px(0, 2, hair); px(7, 2, hair); if (r() < .6) { px(1, 2, hair); px(6, 2, hair); } if (r() < .4) { px(0, 3, hair); px(7, 3, hair); }
  px(1, 4, "#fff"); px(2, 4, eye); px(5, 4, eye); px(6, 4, "#fff");
  px(3, 5, shade(skin, .75)); px(4, 5, shade(skin, .75));
  const m = shade(skin, .55); px(2, 6, m); px(3, 6, m); px(4, 6, m); px(5, 6, m);
  if (r() < .35) for (let i = 1; i < 7; i++) px(i, 7, shade(hair, .9));
  const u = c.toDataURL(); HEADS.set(name, u);
  const d = x.getImageData(0, 0, 8, 8).data, pxs = [];
  for (let i = 0; i < 64; i++) pxs.push(`rgb(${d[i * 4]},${d[i * 4 + 1]},${d[i * 4 + 2]})`);
  HEADPX.set(name, { px: pxs, skin, hair }); return u;
}
