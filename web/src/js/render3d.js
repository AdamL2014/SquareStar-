// Jednoduchý 3D renderer na canvas: modely z resource packu serveru + postava s animacemi emotů z BetterModel.
import { MODELS } from "./models3d.js";
import { RIG } from "./rig3d.js";
import { pixelHeadData } from "./util.js";


/* ---------- matice 4×4 (sloupcové pole 16 čísel) ---------- */
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
function mul(a, b) { const o = new Array(16); for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; }
const T = (x, y, z) => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1];
const S = (s) => [s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1];
function RX(a) { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]; }
function RY(a) { const c = Math.cos(a), s = Math.sin(a); return [c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]; }
function RZ(a) { const c = Math.cos(a), s = Math.sin(a); return [c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; }
const ap = (m, p) => [m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12], m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13], m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]];
const D = Math.PI / 180;

/* ---------- barvy ---------- */
const hex = (n) => [(n >> 16) & 255, (n >> 8) & 255, n & 255];
function parse(c) { if (typeof c === "number") return hex(c); if (c[0] === "#") return hex(parseInt(c.slice(1), 16)); const m = c.match(/\d+/g); return [+m[0], +m[1], +m[2]]; }
const lit = (rgb, k) => `rgb(${Math.min(255, rgb[0] * k) | 0},${Math.min(255, rgb[1] * k) | 0},${Math.min(255, rgb[2] * k) | 0})`;

/* ---------- scéna: seznam čtyřúhelníků ve světě ---------- */
function addModel(out, name, m, tint) {
  const q = MODELS[name].q;
  for (let i = 0; i < q.length; i += 13) {
    const pts = [ap(m, [q[i], q[i + 1], q[i + 2]]), ap(m, [q[i + 3], q[i + 4], q[i + 5]]), ap(m, [q[i + 6], q[i + 7], q[i + 8]]), ap(m, [q[i + 9], q[i + 10], q[i + 11]])];
    const cc = q[i + 12]; out.push({ p: pts, c: tint ? tint(cc & 0xffffff) : parse(cc & 0xffffff), a: cc > 0xffffff ? .4 : 1 });
  }
}
// kvádr; faces = funkce (jméno stěny, u, v) -> barva; div = dělení stěny (pro pixely hlavy)
function addBox(out, m, f, t, col, div = 1) {
  const L = [f[0], f[1], f[2]], H = [t[0], t[1], t[2]];
  const P = (x, y, z) => ap(m, [L[0] + (H[0] - L[0]) * x, L[1] + (H[1] - L[1]) * y, L[2] + (H[2] - L[2]) * z]);
  const faces = {
    north: (u, v) => [1 - u, 1 - v, 0], south: (u, v) => [u, 1 - v, 1], east: (u, v) => [1, 1 - v, 1 - u],
    west: (u, v) => [0, 1 - v, u], up: (u, v) => [u, 1, v], down: (u, v) => [u, 0, 1 - v],
  };
  for (const [fn, map] of Object.entries(faces)) {
    const n = fn === "north" || fn === "west" || fn === "east" || fn === "south" || fn === "up" ? div : 1;
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      const a = map(i / n, j / n), b = map((i + 1) / n, j / n), c = map((i + 1) / n, (j + 1) / n), d = map(i / n, (j + 1) / n);
      out.push({ p: [P(...a), P(...b), P(...c), P(...d)], c: parse(col(fn, i, j, n)) });
    }
  }
}

/* kvádr potažený skinem 64×64: texel po texelu (u,v = počátek dílu, w,h,d v pixelech skinu, r0..r1 = řádky výšky) */
function addSkinBox(out, m, f, t, P0, skin) {
  const [u, v, w, h, d, r0, r1] = P0, rows = r1 - r0;
  const X = (a) => f[0] + (t[0] - f[0]) * a, Y = (a) => f[1] + (t[1] - f[1]) * a, Z = (a) => f[2] + (t[2] - f[2]) * a;
  const face = (ru, rv, C, R, map) => {
    for (let i = 0; i < C; i++) for (let j = 0; j < R; j++) {
      const c = skin[(rv + j) * 64 + ru + i]; if (c === undefined) continue;
      const q = [map(i / C, j / R), map((i + 1) / C, j / R), map((i + 1) / C, (j + 1) / R), map(i / C, (j + 1) / R)].map((p) => ap(m, p));
      out.push({ p: q, c: hex(c) });
    }
  };
  face(u + d, v + d + r0, w, rows, (a, b) => [X(1 - a), Y(1 - b), Z(0)]);            // přední (sever)
  face(u + d + w + d, v + d + r0, w, rows, (a, b) => [X(a), Y(1 - b), Z(1)]);        // zadní
  face(u, v + d + r0, d, rows, (a, b) => [X(1), Y(1 - b), Z(1 - a)]);                 // pravý bok (+x)
  face(u + d + w, v + d + r0, d, rows, (a, b) => [X(0), Y(1 - b), Z(a)]);             // levý bok
  if (r0 === 0) face(u + d, v, w, d, (a, b) => [X(1 - a), Y(1), Z(1 - b)]);           // vršek
  if (r1 === h) face(u + d + w, v, w, d, (a, b) => [X(1 - a), Y(0), Z(b)]);           // spodek
}
const SKINMAP = {
  h_ph_head: [0, 0, 8, 8, 8, 0, 8], pc_chest: [16, 16, 8, 12, 4, 0, 4], pw_waist: [16, 16, 8, 12, 4, 4, 8], phip_hip: [16, 16, 8, 12, 4, 8, 12],
  pra_right_arm: [40, 16, 4, 12, 4, 0, 6], prfa_right_forearm: [40, 16, 4, 12, 4, 6, 12], pla_left_arm: [32, 48, 4, 12, 4, 0, 6], plfa_left_forearm: [32, 48, 4, 12, 4, 6, 12],
  prl_right_leg: [0, 16, 4, 12, 4, 0, 6], prfl_right_foreleg: [0, 16, 4, 12, 4, 6, 12], pll_left_leg: [16, 48, 4, 12, 4, 0, 6], plfl_left_foreleg: [16, 48, 4, 12, 4, 6, 12],
};

/* ---------- skin 64×64 vygenerovaný z nicku (čistý, bez cizích textur) ---------- */
const SKINS = new Map();
export function makeSkin(nick) {
  if (SKINS.has(nick)) return SKINS.get(nick);
  const hp = pixelHeadData(nick), px = new Array(4096), H2 = (c) => (typeof c === "number" ? c : c[0] === "#" ? parseInt(c.slice(1), 16) : (() => { const m = c.match(/\d+/g); return (+m[0] << 16) | (+m[1] << 8) | +m[2]; })());
  let h = 7; for (const ch of nick) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const SH = [0x2f9fb6, 0xd84a3a, 0x3a7bd8, 0x3aa65a, 0x8a4ad8, 0xe0a020, 0x2a2a34], PA = [0x3b4aa8, 0x2a2a34, 0x5a4030, 0x30507a, 0x4a4a52];
  const shirt = SH[h % SH.length], pants = PA[(h >> 4) % PA.length], skin = H2(hp.skin), hair = H2(hp.hair), shoe = 0x3a3a40;
  const n = (c, k) => { const v = hex(c), f = (x) => Math.max(0, Math.min(255, Math.round(x * k))); return (f(v[0]) << 16) | (f(v[1]) << 8) | f(v[2]); };
  const rnd = () => ((h = (h * 1103515245 + 12345) >>> 0) / 4294967296);
  const fill = (x, y, w, hh, fn) => { for (let j = 0; j < hh; j++) for (let i = 0; i < w; i++) px[(y + j) * 64 + x + i] = fn(i, j); };
  // hlava: předek = obličej, vršek vlasy, boky a záda vlasy nahoře
  fill(8, 8, 8, 8, (i, j) => H2(hp.px[j * 8 + i]));
  fill(8, 0, 8, 8, () => n(hair, .9 + rnd() * .2)); fill(16, 0, 8, 8, () => n(skin, .85));
  fill(0, 8, 8, 8, (i, j) => j < 3 ? n(hair, .9 + rnd() * .2) : n(skin, .9 + rnd() * .1));
  fill(16, 8, 8, 8, (i, j) => j < 3 ? n(hair, .9 + rnd() * .2) : n(skin, .9 + rnd() * .1));
  fill(24, 8, 8, 8, (i, j) => j < 6 ? n(hair, .9 + rnd() * .2) : n(skin, .9));
  // tělo, ruce, nohy (box u,v,w,h,d)
  const part = (u, v, w, hh, d, col) => { fill(u + d, v, w, d, (i, j) => col(i, 0, true)); fill(u + d + w, v, w, d, (i, j) => col(i, hh - 1, true)); fill(u, v + d, d + w + d + w, hh, (i, j) => col(i, j, false)); };
  part(16, 16, 8, 12, 4, (i, j) => j > 10 ? n(pants, .9) : n(shirt, .88 + rnd() * .16));
  const arm = (i, j) => j < 4 ? n(shirt, .88 + rnd() * .16) : n(skin, .9 + rnd() * .1);
  part(40, 16, 4, 12, 4, arm); part(32, 48, 4, 12, 4, arm);
  const leg = (i, j) => j > 9 ? n(shoe, .9 + rnd() * .2) : n(pants, .85 + rnd() * .2);
  part(0, 16, 4, 12, 4, leg); part(16, 48, 4, 12, 4, leg);
  SKINS.set(nick, px); return px;
}

/* ---------- aury (podle Cosmetics.Aura na serveru) ---------- */
function auraPts(id, c, now, P) {
  const o = [], t = now / 1000, at = (x, y, z, g, s = 1) => o.push({ p: [P[0] + x, y, P[2] + z], g, c, s });
  if (id === "ORBIT") for (let i = 0; i < 3; i++) { const a = t * 2.2 + i * 2.09; at(Math.cos(a) * 8, 15 + Math.sin(t * 3 + i) * 2, Math.sin(a) * 8, "●", 1.4); }
  else if (id === "HALO") for (let i = 0; i < 16; i++) { const a = t * 1.5 + i * Math.PI / 8; at(Math.cos(a) * 4.5, 34, Math.sin(a) * 4.5, "●", .7); }
  else if (id === "FLAMES") for (let i = 0; i < 12; i++) { const k = (t * .6 + i / 12) % 1, a = i * 2.4 + t * 2; at(Math.cos(a) * 6, k * 30, Math.sin(a) * 6, "▲", 1 - k * .6); }
  else if (id === "STORM") { for (let i = 0; i < 10; i++) { const a = t * 4 + i * .63; at(Math.cos(a) * 9, 33 + Math.sin(i) * 1.5, Math.sin(a) * 9, "☁", 1); } if ((now / 120 | 0) % 9 === 0) for (let k = 0; k < 6; k++) at(Math.sin(k * 3) * 2, 32 - k * 5, 0, "⚡", 1.2); }
  else if (id === "GALAXY") for (let i = 0; i < 24; i++) { const r = 2 + (i % 12) * .8, a = t * 1.4 + (i % 12) * .45 + (i < 12 ? 0 : Math.PI); at(Math.cos(a) * r, 1, Math.sin(a) * r, "✦", .9); }
  else if (id === "LOVE") for (let i = 0; i < 6; i++) { const a = t * 1.6 + i * 1.047; at(Math.cos(a) * 9, 16 + Math.sin(t * 2 + i) * 4, Math.sin(a) * 9, "❤", 1.2); }
  else if (id === "SAKURA") for (let i = 0; i < 14; i++) { const k = (t * .25 + i / 14) % 1; at(Math.sin(i * 7.3 + t) * 10, 36 - k * 36, Math.cos(i * 3.1) * 10, "✿", 1); }
  else if (id === "FROST") for (let i = 0; i < 10; i++) { const a = -t * 1.2 + i * .628; at(Math.cos(a) * 10, 6 + (i % 5) * 6, Math.sin(a) * 10, "❄", 1.1); }
  return o;
}

/* ---------- banner (plášť ze serveru: barva + vzor) 20×40 px ---------- */
const DYE = { WHITE: 0xf9fffe, ORANGE: 0xf9801d, MAGENTA: 0xc74ebd, LIGHT_BLUE: 0x3ab3da, YELLOW: 0xfed83d, LIME: 0x80c71f, PINK: 0xf38baa, GRAY: 0x474f52, BLACK: 0x1d1d21, RED: 0xb02e26, PURPLE: 0x8932b8, BLUE: 0x3c44aa };
export function bannerPixels(base, pat, pc) {
  const W = 20, H = 40, b = DYE[base] ?? 0x888888, p = DYE[pc] ?? 0xffffff, px = new Array(W * H).fill(b);
  const mix = (a, c, k) => { const A = hex(a), C = hex(c); return ((A[0] + (C[0] - A[0]) * k) << 16) | ((A[1] + (C[1] - A[1]) * k) << 8) | (A[2] + (C[2] - A[2]) * k); };
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = b;
    if (pat === "GRADIENT") c = mix(p, b, Math.min(1, y / (H * .85)));
    else if (pat === "BORDER" && (x < 2 || x > W - 3 || y < 2 || y > H - 3)) c = p;
    px[y * W + x] = c;
  }
  const put = (rows, ox, oy) => rows.forEach((r, j) => [...r].forEach((ch, i) => { if (ch === "#") for (let a = 0; a < 2; a++) for (let bb = 0; bb < 2; bb++) px[(oy + j * 2 + bb) * W + ox + i * 2 + a] = p; }));
  if (pat === "CREEPER") put(["##..##", "##..##", "..##..", ".####.", ".#..#."], 4, 8);
  if (pat === "SKULL") put(["..##..", ".####.", "#.##.#", ".####.", ".#..#.", "#....#"], 4, 7);
  if (pat === "FLOWER") put(["..#...", ".###..", "#####.", ".###..", "..#..."], 4, 9);
  // lehké stínování látky
  for (let i = 0; i < px.length; i++) { const x = i % W; if (x % 5 === 0) px[i] = mix(px[i], 0, .08); }
  return { W, H, px };
}
function addBanner(out, m, bn) {
  const { W, H, px } = bn, w = 9, h = 19;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const x0 = -w / 2 + w * x / W, x1 = -w / 2 + w * (x + 1) / W, y0 = -h * y / H, y1 = -h * (y + 1) / H;
    out.push({ p: [[x0, y0, 0], [x1, y0, 0], [x1, y1, 0], [x0, y1, 0]].map((p) => ap(m, p)), c: hex(px[y * W + x]) });
  }
}

/* ---------- vykreslení ---------- */
function draw(ctx, quads, cam, extra) {
  const { w, h, yaw, pitch, scale, cy } = cam;
  const V = mul(RX(pitch), RY(yaw)), dist = 140;
  const proj = (p) => { const v = ap(V, p); const k = dist / (dist + v[2]); return [w / 2 - v[0] * scale * k, cy - v[1] * scale * k, v[2]]; };
  const list = [];
  for (const q of quads) {
    const s = q.p.map(proj);
    const z = (s[0][2] + s[1][2] + s[2][2] + s[3][2]) / 4;
    // normála ve světě kamery -> světlo
    const a = ap(V, q.p[0]), b = ap(V, q.p[1]), c = ap(V, q.p[2]);
    const ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
    let nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx; const l = Math.hypot(nx, ny, nz) || 1; nx /= l; ny /= l; nz /= l;
    if (nz > 0) { nx = -nx; ny = -ny; nz = -nz; }
    const k = .62 + .38 * Math.max(0, -nz * .7 + ny * .5 - nx * .25);
    list.push({ s, z, col: lit(q.c, k), a: q.a || 1 });
  }
  if (extra) for (const e of extra) { const s = proj(e.p); list.push({ pt: e, s, z: s[2] }); }
  list.sort((x, y) => y.z - x.z);
  for (const it of list) {
    if (it.pt) { it.pt.draw(ctx, it.s[0], it.s[1], scale * dist / (dist + it.s[2])); continue; }
    const s = it.s; ctx.fillStyle = it.col; ctx.strokeStyle = it.col; ctx.lineWidth = it.a < 1 ? 0 : .6; ctx.globalAlpha = it.a;
    ctx.beginPath(); ctx.moveTo(s[0][0], s[0][1]); ctx.lineTo(s[1][0], s[1][1]); ctx.lineTo(s[2][0], s[2][1]); ctx.lineTo(s[3][0], s[3][1]); ctx.closePath(); ctx.fill(); if (it.a === 1) ctx.stroke(); ctx.globalAlpha = 1;
  }
  return proj;
}

/* ---------- náhled jednoho modelu (karty v šatníku, obchod) ---------- */
const THUMBS = new Map();
export function modelThumb(name, size = 160) {
  const key = name + size; if (THUMBS.has(key)) return THUMBS.get(key);
  const c = document.createElement("canvas"); c.width = c.height = size; const x = c.getContext("2d");
  const q = MODELS[name].q; let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
  for (let i = 0; i < q.length; i += 13) for (let k = 0; k < 4; k++) for (let a = 0; a < 3; a++) { const v = q[i + k * 3 + a]; mn[a] = Math.min(mn[a], v); mx[a] = Math.max(mx[a], v); }
  const ctr = mn.map((v, i) => (v + mx[i]) / 2), ext = Math.max(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]);
  const out = []; addModel(out, name, T(-ctr[0], -ctr[1], -ctr[2]));
  draw(x, out, { w: size, h: size, cy: size / 2, yaw: -35 * D, pitch: 22 * D, scale: size * .62 / ext });
  const u = c.toDataURL(); THUMBS.set(key, u); return u;
}

/* ---------- postava ---------- */
const BONE = RIG.bones;
function sample(keys, t) {
  if (!keys || !keys.length) return null;
  if (t <= keys[0][0]) return keys[0].slice(1);
  for (let i = 1; i < keys.length; i++) if (t <= keys[i][0]) {
    const a = keys[i - 1], b = keys[i], k = (t - a[0]) / (b[0] - a[0] || 1), e = k * k * k * (k * (k * 6 - 15) + 10);
    return [a[1] + (b[1] - a[1]) * e, a[2] + (b[2] - a[2]) * e, a[3] + (b[3] - a[3]) * e];
  }
  return keys[keys.length - 1].slice(1);
}
function boneMats(anim, t, extra = {}, wgt = 1) {
  const tr = anim ? RIG.anims[anim]?.t || {} : {}, mats = {};
  const get = (n) => {
    if (mats[n]) return mats[n];
    const b = BONE[n], parent = b.p ? get(b.p) : I(), o = b.o, ch = tr[n] || {};
    const r = (sample(ch.rotation, t) || [0, 0, 0]).map((v) => v * wgt), p = (sample(ch.position, t) || [0, 0, 0]).map((v) => v * wgt);
    if (extra[n]) { r[0] += extra[n][0]; r[1] += extra[n][1]; r[2] += extra[n][2]; }
    let m = mul(parent, T(o[0] - p[0], o[1] + p[1], o[2] + p[2]));
    m = mul(m, mul(RZ(r[2] * D), mul(RY(-r[1] * D), RX(-r[0] * D))));
    return (mats[n] = mul(m, T(-o[0], -o[1], -o[2])));
  };
  for (const n in BONE) get(n);
  return mats;
}
export const EMOTE_LEN = Object.fromEntries(Object.entries(RIG.anims).map(([k, v]) => [k, v.len]));

/**
 * Pódium s postavou: start(canvas) vrátí ovladač { set(stav), play(emote), yaw }.
 * stav = { nick, hat: {model|sprite|helmet}, cape: barva, balloon: model, trail: {shape,color}, aura: barva, pet: Image }
 */
export function startStage(canvas) {
  const ctx = canvas.getContext("2d");
  let walkW = 0, st = {}, emote = null, t0 = 0, yaw = -20 * D, run = true, parts = [], syms = [];
  const api = {
    set(s) { st = s; }, get yaw() { return yaw; }, set yaw(v) { yaw = v; },
    play(id) { emote = RIG.anims[id] ? id : null; t0 = performance.now(); api.emoteId = id; },
    stop() { run = false; }, resume() { if (!run) { run = true; requestAnimationFrame(frame); } },
  };
  const SYM = { dance: "♪", twerk: "♫", love: "❤", sleep: "z", gg: "GG", clap: "✦", rage: "⚡", hi: "✌", cheer: "★", bow: "✦", rocket: "▲", spin: "★" };
  function frame(now) {
    if (!run) return;
    const W = canvas.clientWidth, H = canvas.clientHeight, dpr = Math.min(2, devicePixelRatio || 1);
    if (canvas.width !== W * dpr || canvas.height !== H * dpr) { canvas.width = W * dpr; canvas.height = H * dpr; }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, W, H);
    let t = 0;
    if (emote) { t = (now - t0) / 1000; if (t > RIG.anims[emote].len) { emote = null; api.emoteId = null; } }
    // chůze: postava chodí po plošině, když má stopu (ať je stopa vidět jako ve hře)
    const wantWalk = !!st.trail && !emote, wt = now / 1000;
    walkW += ((wantWalk ? 1 : 0) - walkW) * .06; const walk = walkW > .01;
    const ew = emote ? Math.max(0, Math.min(1, t / .22, (RIG.anims[emote].len - t) / .22)) : 0;
    const ang = wt * .45, P = walk ? [Math.sin(ang) * 9, 0, Math.cos(ang) * 6] : [0, 0, 0];
    const head = Math.atan2(-Math.cos(ang) * 9, Math.sin(ang) * 6) * walkW;
    const sw = Math.sin(wt * 7) * 32 * walkW, bob = Math.abs(Math.cos(wt * 7)) * .6 * walkW, br = Math.sin(wt * 2.2);
    const idle = { pra_right_arm: [0, 0, 2.5 + br * 2], pla_left_arm: [0, 0, -2.5 - br * 2], h_ph_head: [br * 2, Math.sin(wt * .7) * 6, 0], pc_chest: [br * 1.2, 0, 0] };
    const extra = walk ? { prl_right_leg: [sw, 0, 0], pll_left_leg: [-sw, 0, 0], pra_right_arm: [-sw * .8, 0, 0], pla_left_arm: [sw * .8, 0, 0], prfl_right_foreleg: [Math.max(0, -sw) * .5, 0, 0], plfl_left_foreleg: [Math.max(0, sw) * .5, 0, 0] } : {};
    for (const k in idle) { const a = extra[k] || [0, 0, 0]; extra[k] = a.map((v, i) => v + idle[k][i] * (1 - ew)); }
    const Wm = mul(T(P[0], bob, P[2]), RY(head));
    const mats0 = boneMats(emote, t, extra, ew || 1), mats = {};
    for (const k in mats0) mats[k] = mul(Wm, mats0[k]);
    const q = [], SK = makeSkin(st.nick || "Steve");
    for (const [n, b] of Object.entries(BONE)) if (SKINMAP[n]) for (const [f, to] of b.b) addSkinBox(q, mats[n], f, to, SKINMAP[n], SK);
    // čepice: model z resource packu na hlavě
    if (st.hat?.model) {
      const mm = MODELS[st.hat.model], s = .625 * mm.h[1] * .9375;
      const m = mul(mats.h_ph_head, mul(T(0, 26.25 + mm.h[0] * .625 * .9375, 0), mul(RY(0), mul(S(s), T(-8, -8, -8)))));
      addModel(q, st.hat.model, m);
    }
    const ex = [];
    // balónek
    if (st.balloon) {
      const bb2 = Math.sin(now / 700) * 1.2, bp = ap(Wm, [-13, 34 + bb2, 3]);
      addModel(q, st.balloon, mul(T(bp[0], bp[1], bp[2]), mul(S(.45), T(-8, 0, -8))));
      const hand = ap(mats.plfa_left_forearm, [-5.6, 11.5, 0]);
      ex.push({ p: [(bp[0] + hand[0]) / 2, (bp[1] - 7 + hand[1]) / 2, (bp[2] + hand[2]) / 2], draw: () => {} , line: [bp[0], bp[1] - 7.2, bp[2], ...hand] });
    }
    // pet (obrázek) u hlavy
    if (st.pet) { const hpnt = ap(mats.h_ph_head, [9, 30 + Math.sin(now / 500) * 1.5, -1]); ex.push({ p: hpnt, draw: (c, x, y, sc) => { const z = 9 * sc; c.imageSmoothingEnabled = false; c.drawImage(st.pet, x - z / 2, y - z / 2, z, z); } }); }
    // aura podle typu
    const glyph = (g, c, s) => (cx, x, y, sc) => { cx.fillStyle = c; cx.shadowColor = c; cx.shadowBlur = 8; cx.font = `${Math.max(6, sc * 2.2 * s)}px sans-serif`; cx.textAlign = "center"; cx.textBaseline = "middle"; cx.fillText(g, x, y); cx.shadowBlur = 0; cx.textAlign = "start"; cx.textBaseline = "alphabetic"; };
    if (st.aura) for (const a of auraPts(st.aura.id, st.aura.color, now, P)) ex.push({ p: a.p, draw: glyph(a.g, a.c, a.s) });
    // stopa: částice zůstávají na zemi tam, kudy postava šla
    if (st.trail) {
      if (walk && Math.random() < .7) parts.push({ p: [P[0] + (Math.random() - .5) * 4, .5, P[2] + (Math.random() - .5) * 3], l: 1, vy: .02 + Math.random() * .05 });
      parts = parts.filter((p) => (p.l -= .006) > 0);
      for (const p of parts) { p.p[1] += p.vy; ex.push({ p: p.p, draw: ((pp) => (cx, x, y, sc) => { cx.globalAlpha = pp.l; glyph(st.trail.shape, st.trail.color, .6 + pp.l * .5)(cx, x, y, sc); cx.globalAlpha = 1; })(p) }); }
    } else parts = [];
    const proj = draw(ctx, q, { w: W, h: H, cy: H * .86, yaw, pitch: 14 * D, scale: H / (st.trail ? 64 : 52) }, ex);
    // provázek balónku
    for (const e of ex) if (e.line) { const a = proj(e.line.slice(0, 3)), b = proj(e.line.slice(3)); ctx.strokeStyle = "#ddd9"; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.quadraticCurveTo((a[0] + b[0]) / 2 + 6, (a[1] + b[1]) / 2, b[0], b[1]); ctx.stroke(); }
    // symboly emotu nad hlavou (jako ve hře)
    if (emote && SYM[emote] && Math.random() < .05) { const h = proj(ap(mats.h_ph_head, [0, 32, 0])); syms.push({ x: h[0] + (Math.random() - .5) * 30, y: h[1], l: 1, s: SYM[emote] }); }
    syms = syms.filter((s) => (s.l -= .01) > 0);
    for (const s of syms) { s.y -= .6; ctx.globalAlpha = s.l; ctx.fillStyle = "#ffd23a"; ctx.font = "bold 18px sans-serif"; ctx.textAlign = "center"; ctx.fillText(s.s, s.x, s.y); ctx.textAlign = "start"; }
    ctx.globalAlpha = 1;
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  return api;
}
