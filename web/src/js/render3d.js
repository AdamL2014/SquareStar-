// Jednoduchý 3D renderer na canvas: modely z resource packu serveru + postava s animacemi emotů z BetterModel.
import { MODELS } from "./models3d.js";
import { RIG } from "./rig3d.js";
import { pixelHeadData } from "./util.js";
import { SKIN } from "./skin.js";


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
    const cc = q[i + 12], rgb = hex(cc & 0xffffff), glass = name === "oxygen_helmet" && rgb[2] > 150 && rgb[2] > rgb[0] + 40;
    if (glass) { out.push({ p: pts, c: rgb, a: .28 }); continue; }
    out.push({ p: pts, c: tint ? tint(cc & 0xffffff) : parse(cc & 0xffffff), a: cc > 0xffffff ? .4 : 1 });
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
      const c = skin[(rv + j) * 64 + ru + i]; if (c == null) continue;
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

/* ---------- aury: přesný port Cosmetics.tickAuras (částice, 10× za sekundu, 1 blok = 16 jednotek) ---------- */
const B16 = 16;
function emitAura(id, tk, P, out) {
  const t = tk * .18, M = Math.random;
  const add = (type, x, y, z, o = {}) => out.push({ type, p: [P[0] + x * B16, y * B16, P[2] + z * B16], v: o.v || [0, 0, 0], l: 1, d: 1 / (o.life || 20), col: o.col, sz: o.sz || 1 });
  const dust = (x, y, z, col, sc, life = 22, sp = 0) => add("dust", x + (M() - .5) * sp, y, z + (M() - .5) * sp, { col, sz: sc, life: life * (.6 + M() * .8) });
  if (id === "ORBIT") for (let k = 0; k < 3; k++) { const an = t + k * Math.PI * 2 / 3; add("rod", Math.cos(an) * 1.1, 1 + Math.sin(t * .7 + k) * .5, Math.sin(an) * 1.1, { life: 34, v: [0, -.002, 0] }); }
  else if (id === "HALO") for (let k = 0; k < 10; k++) { const an = t + k * Math.PI / 5; dust(Math.cos(an) * .45, 2.25, Math.sin(an) * .45, "#ffe15a", .8, 12); }
  else if (id === "FLAMES") for (let k = 0; k < 2; k++) { const an = t * 1.4 + k * Math.PI, y = (tk * .04 + k) % 2; add("flame", Math.cos(an) * .8, y, Math.sin(an) * .8, { life: 14, v: [0, .004, 0] }); }
  else if (id === "STORM") {
    for (let n = 0; n < 2; n++) dust(Math.cos(t) * .9, 2.6, Math.sin(t) * .9, "#5a5a6e", 1.8, 30, .5);
    if (tk % 4 === 0) for (let n = 0; n < 2; n++) {
      let x = Math.cos(M() * 6.283) * .5, y = .2 + M() * 1.6, z = Math.sin(M() * 6.283) * .5; const ang = M() * 6.283, dx = Math.cos(ang) * .18, dz = Math.sin(ang) * .18; x = Math.cos(ang) * .5; z = Math.sin(ang) * .5;
      for (let seg = 0; seg < 4; seg++) {
        const qx = x + dx + (M() - .5) * .25, qy = y - .22 - M() * .12, qz = z + dz + (M() - .5) * .25;
        for (let i = 0; i <= 4; i++) add("spark", x + (qx - x) * i / 4, y + (qy - y) * i / 4, z + (qz - z) * i / 4, { life: 6 });
        x = qx; y = qy; z = qz;
      }
    }
  }
  else if (id === "GALAXY") for (let k = 0; k < 3; k++) { const an = t * 1.2 + k * 2.1, y = (tk * .03 + k * .7) % 2.2, rr = .5 + y * .35; dust(Math.cos(an) * rr, y, Math.sin(an) * rr, k % 2 ? "#50a0ff" : "#9650ff", 1.1, 26); }
  else if (id === "LOVE") {
    for (let k = 0; k < 2; k++) { const an = t * 1.3 + k * Math.PI; add("heart", Math.cos(an) * .8, .6 + (tk * .04 + k) % 1.6, Math.sin(an) * .8, { life: 22, v: [0, .006, 0] }); }
    if (tk % 8 === 0) for (let n = 0; n < 4; n++) dust(0, 2.3, 0, "#ff5a96", 1.2, 22, .6);
  }
  else if (id === "SAKURA") add("leaf", (M() - .5) * 1.4, 2.3, (M() - .5) * 1.4, { life: 60, v: [(M() - .5) * .004, -.016, (M() - .5) * .004] });
  else if (id === "FROST") for (let k = 0; k < 2; k++) { const an = -t + k * Math.PI, y = .2 + (tk * .03 + k) % 2; add("snow", Math.cos(an) * .9, y, Math.sin(an) * .9, { life: 30, v: [0, -.004, 0] }); }
}
/* originální textury částic z Minecraftu 1.21 (assets/minecraft/textures/particle) */
const PTX_SRC = {
  glitter: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAE0lEQVR4XmNgGITg/////+nsLADFUQP9uE9MVgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAH0lEQVR4XmNgoD/4////f2RbmZA5MEl0RSgAryRZAAATYQ/zy/2AgQAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIklEQVR4XmNgoDlgRLbh/////2F8RkZGsBwTIScQVEA5AABdaQQG3QX1mAAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAL0lEQVR4XmNgoDr4////f2RDmZA5MElkRXAF6DphfLgCRkZGRmTT0PlwgG4SQQAAp50X9/Bb8AMAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALklEQVR4XmNgoBQwIhvw/////zA+IyMjWI4JJoAsCQIwPlwBLkC8Apid6G4gCAB0iwwKAYBAPAAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAOElEQVR4XmNgIBX8////P7IeJmQOTBJZEVwBuk4YnxFkArokhhWMjIxghegAJA63Al0RLk0Y1gEArNsb/nNDDL8AAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALklEQVR4XmNgoBQwIhvw/////zA+IyMjWI4JJoAsCQIwPlwBLkC8Apid6G4gCAB0iwwKAYBAPAAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAOElEQVR4XmNgIBX8////P7IeJmQOTBJZEVwBuk4YnxFkArokhhWMjIxghegAJA63Al0RLk0Y1gEArNsb/nNDDL8AAAAASUVORK5CYII="],
  generic: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAE0lEQVR4XmNgGITg/////+nsLADFUQP9uE9MVgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAF0lEQVR4XmNgoC/4////f4I2EqWIugAAHSQH+QJ/ttEAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAG0lEQVR4XmNgoC/4////f4I2ghQRpRAnIFk3AI7OD/GjaMWnAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJElEQVR4XmNgoAn4////f6wG45RABiBFyAqZ0CUJupkoa5ABAJvAF+v68gERAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAKElEQVR4XmNgoCr4////f5CBMBoDgCRwSsIAuiImZGOQdeO1hiSPAQDf0CPfOQEjJwAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALElEQVR4XmNgoCr4////f5CBMBoDgCRwKkKWQDaBEdkYdKMZGRkR8gTtxwUAqO8r2v+33F0AAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAANElEQVR4XmNgIBX8////P1Y9IAmYJLIiRmTVMAlGRkZGFJPQdRM0AQZAJsE56PZjdSw2QQB5YzvP21LuugAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAPElEQVR4Xm2OMQ4AMAgCpf//M40DDWKZFOW0ykSS3j/5oGtpLLs5SL90Jwcl0x46ugMASVs/CL1OpOGkC1+SZ58Z6iKqAAAAAElFTkSuQmCC"],
  cherry: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIklEQVR4XmNkYGBg+L7zzv8f7z4yMIIYIAEQYIIxON1VGAEaYgpn7kaiqQAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIElEQVR4XmNkYGBgeDHz8H+GTz8ZGMEMKGCCMSTSbRkB73EJMxQS3oAAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIklEQVR4XmNkYGBgeFC27v+vrz8YGEEMkAAIMMEYCl1BjAD/hQnvv0n+EAAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIElEQVR4XmNkYGBg+L7zzn8QzQRjgDk/3n0EMzjdVRgB508KaBHFtXcAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIElEQVR4XmNkYGBgeDHz8H8QzQRjgDkMn36CGRLptowAzQUJNJt11JQAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAIElEQVR4XmNkYGBgeFC27j+IZoIxwJxfX3+AGQpdQYwAy2EJ8LiXjUgAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAJElEQVR4XmP4vvPOfxBmYGBgYAIRIAASYOJ0V2EEcX68+8gAADkDDfKMH3DoAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAI0lEQVR4XmN4MfPwfxBmYGBgYAIRIAASYJJIt2UE8z79ZAAAIDsMdvP3s+AAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAJElEQVR4XmN4ULbuPwgzMDAwMIEIEAAJMCl0BTGCOL++/mAAABGEDPJfa3qLAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAJElEQVR4XmN4v/zs/+877/xnYGBgYIQxQICJ012FEcxiYGAAAEbPCvHPGzYIAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAJElEQVR4XmN40b3n/4uZh/8zMDAwMMIYIMAkkW7LCGYxMDAAADJvCnWzQuvYAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAI0lEQVR4XmO4lb3s/4Oydf8ZGBgYGGEMEGBS6ApiBLMYGBgAHTAJ8QJe7/kAAAAASUVORK5CYII="],
  note: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAATUlEQVR4XmNgQAObN2/+P2nSpP8wYSZ0BSBw7do1BmRFKABkAkggMDAQbgoKQNeJ1QpkQLyCz58//wfZD3IgMmCBcUpLSxlevnyJ4SkAA0kgXDhRebAAAAAASUVORK5CYII="], soul: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAARElEQVR4XmNgQAKMfZP/I/NBgAlFQFISqyI4SPj66z/j8jUopqCagA+AdIJMAGGs6kASIIDNGjCAKYApwjAF2QpkhwIAxdk5ykZxXE0AAAAASUVORK5CYII="], glint: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAATklEQVR4XmNgQAbTBP+j8BkYGJhQBGQl0eWRFIB0iykzoJvCAhYA6QRJynpCTNh89z/D4+cMDFnvGRFGghSe9P2PzR0IsFmLgCNBxpIKAGI4FqGbFJg3AAAAAElFTkSuQmCC"],
  spell: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHElEQVR4XmNgoC/4////f5CNMBorIEoSryKSAQDWohPt8MUMYgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAI0lEQVR4XmNgoC/4/////3PnzoGo/1hthkmCaKwKCJpAFgAA4gwhkadeC+kAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALUlEQVR4XmNgoBr4////f2TD0PlgABI8d+4ciEJRDAcwSRCN1Wl4TSDKDdgAAPNdMYFSs0fZAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALElEQVR4XmNgoBr4/////3Pnzv0HGQiiQQDFcJggMo1hO9GSyNbBAUE34AIACkBMyZVICzEAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAMElEQVR4XmNgIAb8////PzZ1KOIgzrlz58AKQTSGJpggMo1hKtGSyNbBAV434PMFABuRXLlnjZrhAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAMElEQVR4XmNgIAb8////PzZ1KOIgzrlz58AKQTSGJpggMo1hKtGSyNbBAV434PMFABuRXLlnjZrhAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAMElEQVR4XmNgIAb8////PzZ1KOIgzrlz58AKQTSGJpggMo1hKtGSyNbBAV434PMFABuRXLlnjZrhAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAMElEQVR4XmNgIAb8////PzZ1KOIgzrlz58AKQTSGJpggMo1hKtGSyNbBAV434PMFABuRXLlnjZrhAAAAAElFTkSuQmCC"],
  sculk: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAh0lEQVR4XmNgGAXDADDi8gOvkPh/Fk4usPSf798YPr97iVUtEzYDYJq/8AuCMcggkBg2tVgNAGn4+vk9XD2IDXMNOsBqAAj8l1GCq0VmE2UAyM8gwPjkHhjjA1hdgB5gPB/fM7x/ep/4QAQBkCYYgLkIG2DBKgqNOm5eQXg0kgVAUYcr+mAAAPEhLzxkOaBNAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAApUlEQVR4XmNgGPKAEZcPBKUV/4Pk/nz/xvD53Uuc6piwGQDS/PXze4Yv/IIMLJxcDLxC4mDDsAEMk0GaQRphgOfjezATl0swXADSzPjkHtwAkEvwAaxeAAGYIf9llMB8kFewAQwDYE7G5gWiDAD5lZsXEQaEYgJr9IBCHdnJ75/eJy0aYbYi0yQDNj6B//jiHwZwxgII/Pzzk2SLUQDIFZSZQAQAAIWGQBwU/NhAAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAl0lEQVR4XmNgGPKAEZcPWLWM/oPkeD6+Z/jz/RvD53cvsaplwmYATDPjk3sMX/gFGVg4uRh4hcTBBqIDDAOQFf6XUSIYxFhdALIZHYBcQZQL0AEoDPABrC6AOR1ZMyggsQEWbILoGn/++cnw69MHrLGA1QAYwBd9eAEoJgSlFbFGG0bgYjMJ5GSG7/gtIQjY+ASIcsHAAwAvbDLCN+JfWwAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAlUlEQVR4XmNgGAWMuIKAV0j8P0iOhZMLrOT90/tY1TJhMwCk+eefnwwg/IVfkOHr5/cMgtKKYAPRAYapIIUgDf9llDAU83x8j+ESrC7g5hUkOnIxDPjz/RtYM8g2EIYBEBsmhwxYcFkFUvz53UtG3u/f/oMCEsQHhQk6wHABSBOyTcj8X58+4Iw1DMDGJ4A11EcBJgAAM5hEGeIJWzQAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAp0lEQVR4XmNgGAWM2IKAjU/gPzsLO4rU53cvsaplQjcAWTMLJxcYgwCvkPh/bJZhGACzGdnGHxLSDD///MRqCIYBIIUgzYLSimAbv/ALgi3+L6OENcIxDICBP9+/YWgAuQQdYBjw69MHrIHF+OQeVhewYBVlYAB7heE7A8Ofnm6wEpbmNgaej+8Z3qNpwGkA2CV8Av+5HtxjYJsyHazt/dP7WF030gEAyro0aOxFgwkAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAkElEQVR4XmNgGAWM2IKAjU/gP0ycnYUdzPz87iVWtUzYDPj16QNYMUwzCPAKicMNRQZYDQABmCEsnFxgjMsQFlyxCLIRphEEkNnIAKcBMA1f+AXh6jlePMVlHyoA2S4orfifcfma/6AAhdHYwgGnC0Ag3s+PYSHDHOJsRQcg20A2s2oZgW0GuYhkk8jWOLQAAOlQJ52/atxEAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAqklEQVR4XmNgGAWM+IKAjU/gP7L8r08f8KrHCUAG8QqJoxgGA0zYdCV8/fWfcfkasAZBacX/7CzsYGXoLsIKQJphAGQIyAAQBrkAlytQAEgTDMBcgmwIuq1YvcDOL8iQ+O03wzJJMbD6L/yC2J3LwIBpgEBJKcN/GSWGhZs2gWlkwMLJhWEQ1mgB+fXnn59gxSBDeD6+B7PfP72PoR5nvCIHGMhmbJqHCQAAvb5MyQ5jh14AAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAlElEQVR4XmNgGLQg4euv/yAAokl2JBufAFgzDOAzhAnddJDinx/fg4XZ+QXh0oLSilhdwoLNeTCNUc9fMTCtWAtWwgIy7Ol9bMpRAePyNf9BXgBhEJtVywhM43IBVgBSjGwASZpBAGQryAAQDdPMKySONQwYcXkK2dY/37+BlX1+9xJDPUYswMD7p/fBivFpHgVUAgDokVFXUPCx0gAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAaklEQVR4XmNgoBVI+PrrPwiAaJLtAGli4xMAGwCiSTaEcfkasGYYwGcAEy7nsfMLgjEILNy0CacvsBrwPzKE8ffMOWBNid9+kxwEYCAorfgf5BUQJs8EBgZwWIAMItsAijTDAFUMGQU0BgBhMT27T4HQsgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAS0lEQVR4XmNkwAESvv76D5NawM3GiEsdVgDSzMYn8B8EQDSyYUQBxuVrwJpgAMQnSiMyAGkiy3ZkADKALNthgBjNTCT7bRSMAloAAM6/LVuGBPc4AAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAANUlEQVR4XmNgwAESvv76D8K45GGACZ+CZZJiYIPwqcEL/v//T1AzXhckfvtNtuWjYBSMNAAAehMREm5FJnMAAAAASUVORK5CYII="],
  sga: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJ0lEQVR4XmNggIL/UADjowCQHLJCDEXICtABE1YjkQAVFOCznygAAJ8GF/EBaiSRAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJElEQVR4XmNggIL/////h7GRARM2QeoqQAG43IFXEQtRuvABAAsgD/sgn7U7AAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIUlEQVR4XmP4////fwaKACETmAiZDlZAyBS8CghaQRAAAIXoD/eV+M9vAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJElEQVR4XmP8////fwY0wMjIyIguhhuATMBmCgYgWhFRCqkLAB6eF+5hLDc5AAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAKUlEQVR4XmP4////fwYkgM7HFEADTOgCA6CAEcPVDAwMjIyMjITcBgcAdtQP/vwqocsAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHklEQVR4XmP8////fwY0wMjIyIguhhvATECnhxQAANmqD/YB02bGAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIklEQVR4XmNggIL/////h7GRARM2QZIUsOAyGgPQzg0EAQBmUgwBU6DQcQAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJklEQVR4XmP8////fwY0wMjIyIguhhMwEm0CNoUgwETILsoVEAQAkKUMBZmnjEIAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHElEQVR4XmP4////fwY8gAmfJAgQVEAYDAI3AAAb6ggBpEAITQAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHklEQVR4XmP4////fwY8gAmfJHGAkBWEASETKHckAB2wC/kEAnrDAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIklEQVR4XmNggIL/////h7GRARM2QdIUoBuNyyoauoEgAACs3A/7ZP26ewAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIklEQVR4XmP4////fwY8gAkmh1Mh0SbgAnArCCog2w0EAQDvrBP1IlF4yQAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIklEQVR4XmP4////fwYkgM7HAOgKmDCVoAI6KGAh6GpCAAA3LA/9E9gKzwAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJUlEQVR4XmP4////fwYoQGbDABOcRQzAZgIc4JIkzgqyjCYaAACpCxftl8/XMwAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAALElEQVR4XmP8////fwYkwMjIyIjMRwHoikGACadqdIBNN/Em4NINBngliQEATtwT9qGdXbAAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAKklEQVR4XmP4////fwYGBgYYjRPgUsBESCcTbjMhgKACgo6Dm0DILTgBAOFuF/H7GWZDAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAKklEQVR4XmNggIL/////h7FJAozYdDIyMjLiNAVdAxMh+wgqYCHb9TAAAEmsD/7I2L8wAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFklEQVR4XmP4////fwYoQGYPLUCxLwC/xw/xZy6CGgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIElEQVR4XmP4////fwY8gAmfJAgQp4CQNXgVEGcFRQAAhOgL/3T+3g0AAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJ0lEQVR4XmP8////fwY0wMjIyIguBgfoGphwqoQCyhWQDrD5Ci8AACgGDACSG3ajAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAIUlEQVR4XmNgoAr4////f5BBMJokwIhNFyMjIyNJplAGAN3JC/qZDqmNAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJklEQVR4XmNggIL/////h7GRARM2QeoqYMRmNyMjIyMhq+GAchMA/DYMBeyTK4IAAAAASUVORK5CYII=","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAGUlEQVR4XmNgoBr4////f6oZhgrQjaa+VQCtoQv1CMw/MAAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJUlEQVR4XmP4////fwYkgM5HAdgkmVCV4AEkGw0DhK3AazQxAACfnhftZayEGgAAAABJRU5ErkJggg==","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHklEQVR4XmP4////fwYGBgYYjQ6YMETQwJBQQBAAAAxqCAWwK89vAAAAAElFTkSuQmCC","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAJElEQVR4XmNggIL/////h7ExAEwSqyJ0QXQ+E05joYAOCggCAKreE/Ul/MDOAAAAAElFTkSuQmCC"],
  flame: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAARUlEQVR4XmNgQAL/GUAIFTChcLPAKjAUwcH/Gwz//2ehKkA1AR8A6QSbcAOHFWDJr8cgipCswbRC9hgDQx4Wu5CtQDYBACNIKVn+pWopAAAAAElFTkSuQmCC"], heart: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAX0lEQVR4XmNgIAZc1tL6D8Iwtch8RhBHJyqK4eOLFwyP9+0Dqzn94QOYfv3rFwMLiAGThEkgA7AJyLpgAKQbBJh0r11jRHcnTLL8zRtGuGSniAjckSAAksTwIEgRukIAWZw0No3q2mIAAAAASUVORK5CYII="], glow: ["data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAx0lEQVR4XjVPuQ2FUAzz4z4aGgZgA6gYAomKaViBhhFYgAkYgYolOAsaQIBA+cpD31ISRbGdRKRpinmecV0XmqahJEmEqqoQQuB9X2iczvMEw/M8SWSCYRiyV/q+h67rqKqKeFgUBbHovm9M0wSM40jrutIfwzDQsizUdR2FYQgRxzGe50FZlhQEAdq2RZ7nwrIsaJoGEUURbNvGcRyo65qyLBNEBEVRvvB9H/u+y6u5Mljpui4cx/lWsMO2bZLEatM05Yvs8AONDWrqiDtPSAAAAABJRU5ErkJggg=="],
};
const PTX = typeof Image === "undefined" ? {} : Object.fromEntries(Object.entries(PTX_SRC).map(([k, a]) => [k, a.map((s) => { const i = new Image(); i.src = s; return i; })]));
const TINT = new Map();
function tinted(img, col) {
  const k = img.src.length + img.src.slice(-24) + col; let c = TINT.get(k); if (c) return c;
  c = document.createElement("canvas"); c.width = img.width; c.height = img.height; const x = c.getContext("2d");
  x.drawImage(img, 0, 0); x.globalCompositeOperation = "multiply"; x.fillStyle = col; x.fillRect(0, 0, c.width, c.height);
  x.globalCompositeOperation = "destination-in"; x.drawImage(img, 0, 0); TINT.set(k, c); return c;
}
/* typ částice → [sada textur, velikost v 1/16 bloku, barva (tint), snímky podle stáří] */
const TRAILP = { FLAME: "flame", HEARTS: "heart", NOTES: "note", STARS: "rod", SOUL: "soul", CHERRY: "leaf", COSMIC: "witch", RAINBOW: "rainbow", SNOW: "snow", ELECTRIC: "spark", CLOUD: "cloud", SCULK: "sculk", ENCHANT: "sga", GLOW: "glowp", PORTAL: "portal", TOTEM: "totem", HAPPY: "happy" };
const PK = { note: ["note", 2.4, "tint"], soul: ["soul", 2.4], witch: ["spell", 2.2, "#a35ad8", 1], rainbow: ["generic", 2, "tint", 1], cloud: ["generic", 3.4, "#ffffff", 1], sculk: ["sculk", 3, null, 1], sga: ["sga", 1.8, "#e8e0ff"], glowp: ["glow", 2.2, "#5ef0c8"], portal: ["generic", 1.8, "#9a4cff", 1], totem: ["glitter", 2.4, "tint", 1], happy: ["glint", 2, "#68e85a"], rod: ["glitter", 2.8, null, 1], flame: ["flame", 2.4], heart: ["heart", 3.2], snow: ["generic", 2.2, "#f2f8ff", 1], leaf: ["cherry", 1.7], spark: ["glow", 2, "#fff6c8"], dust: ["generic", 1.9, null, 1] };
function drawAuraParticle(cx, x, y, sc, a) {
  const [set, U, tint, aged] = PK[a.type], arr = PTX[set]; if (!arr) return;
  const img = aged ? arr[Math.min(arr.length - 1, Math.floor((1 - a.l) * arr.length))] : arr[a.f ?? (a.f = Math.random() * arr.length | 0)];
  if (!img.complete || !img.width) return;
  const s = Math.max(2, Math.round(U * a.sz * sc)), col = a.type === "dust" || tint === "tint" ? a.col : tint;
  cx.imageSmoothingEnabled = false; cx.globalAlpha = a.type === "leaf" ? Math.min(1, a.l * 3) : 1;
  cx.drawImage(col ? tinted(img, col) : img, Math.round(x - s / 2), Math.round(y - s / 2), s, s);
  cx.globalAlpha = 1;
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
/* Z-buffer rasterizér: žádné prosvítání modelů přes postavu */
let ZB = null, IMG = null;
function draw(ctx, quads, cam, extra) {
  const { w, h, yaw, pitch, scale, cy, oy = 0 } = cam, dpr = ctx.getTransform().a || 1;
  const PW = Math.round(w * dpr), PH = Math.round(h * dpr);
  const V = mul(RX(pitch), RY(yaw)), dist = 140;
  const proj = (p) => { const v = ap(V, [p[0], p[1] - oy, p[2]]); const k = dist / (dist + v[2]); return [w / 2 - v[0] * scale * k, cy - v[1] * scale * k, v[2]]; };
  if (!ZB || ZB.length !== PW * PH) { ZB = new Float32Array(PW * PH); IMG = new ImageData(PW, PH); }
  ZB.fill(1e9); const D8 = IMG.data; D8.fill(0);
  const tri = (A, B, C, R, G, Bc, al) => {
    const minX = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), maxX = Math.min(PW - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
    const minY = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), maxY = Math.min(PH - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
    const ar = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]); if (Math.abs(ar) < 1e-6) return; const ia = 1 / ar;
    for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
      const px = x + .5, py = y + .5;
      let w0 = ((B[0] - px) * (C[1] - py) - (B[1] - py) * (C[0] - px)) * ia, w1 = ((C[0] - px) * (A[1] - py) - (C[1] - py) * (A[0] - px)) * ia, w2 = 1 - w0 - w1;
      if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) continue;
      const z = w0 * A[2] + w1 * B[2] + w2 * C[2], i = y * PW + x;
      if (z >= ZB[i]) continue;
      const o = i * 4;
      if (al < 1) { D8[o] = D8[o] * (1 - al) + R * al; D8[o + 1] = D8[o + 1] * (1 - al) + G * al; D8[o + 2] = D8[o + 2] * (1 - al) + Bc * al; D8[o + 3] = Math.max(D8[o + 3], 255 * al + D8[o + 3] * (1 - al)); }
      else { ZB[i] = z; D8[o] = R; D8[o + 1] = G; D8[o + 2] = Bc; D8[o + 3] = 255; }
    }
  };
  const glass = [];
  for (const q of quads) {
    const s = q.p.map((p) => { const t = proj(p); return [t[0] * dpr, t[1] * dpr, t[2]]; });
    const a = ap(V, q.p[0]), b = ap(V, q.p[1]), c = ap(V, q.p[2]);
    const ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
    let nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx; const l = Math.hypot(nx, ny, nz) || 1; nx /= l; ny /= l; nz /= l;
    if (nz > 0) { nx = -nx; ny = -ny; nz = -nz; }
    const k = .62 + .38 * Math.max(0, -nz * .7 + ny * .5 - nx * .25);
    const R = Math.min(255, q.c[0] * k), G = Math.min(255, q.c[1] * k), B = Math.min(255, q.c[2] * k), al = q.a || 1;
    if (al < 1) glass.push([s, R, G, B, al]); else { tri(s[0], s[1], s[2], R, G, B, 1); tri(s[0], s[2], s[3], R, G, B, 1); }
  }
  for (const [s, R, G, B, al] of glass) { tri(s[0], s[1], s[2], R, G, B, al); tri(s[0], s[2], s[3], R, G, B, al); }
  const tmp = draw._c || (draw._c = document.createElement("canvas")); tmp.width = PW; tmp.height = PH; tmp.getContext("2d").putImageData(IMG, 0, 0);
  ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.drawImage(tmp, 0, 0); ctx.restore();
  if (extra) {
    const list = extra.map((e) => ({ e, s: proj(e.p) })).sort((x, y) => y.s[2] - x.s[2]);
    for (const { e, s } of list) { const i = Math.round(s[1] * dpr) * PW + Math.round(s[0] * dpr); if (ZB[i] < s[2] - 1) continue; e.draw(ctx, s[0], s[1], scale * dist / (dist + s[2])); }
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
  let pitch = 14 * D, walkW = 0, st = {}, emote = null, t0 = 0, yaw = -20 * D, run = true, parts = [], syms = [], aps = [], auraT = 0, auraId = null;
  const api = {
    set(s) { st = s; }, get yaw() { return yaw; }, set yaw(v) { yaw = v; }, get pitch() { return pitch; }, set pitch(v) { pitch = Math.max(-35 * D, Math.min(60 * D, v)); },
    play(id) { emote = RIG.anims[id] ? id : null; t0 = performance.now(); api.emoteId = id; },
    stop() { run = false; }, resume() { if (!run) { run = true; requestAnimationFrame(frame); } },
  };
  const SYM = { dance: "♪", twerk: "♫", love: "❤", sleep: "z", gg: "GG", clap: "✦", rage: "⚡", hi: "✌", cheer: "★", bow: "✦", rocket: "▲", spin: "★" };
  function frame(now) {
    if (!run) return;
    const W = canvas.clientWidth, H = canvas.clientHeight, dpr = Math.min(1.5, devicePixelRatio || 1);
    if (!W || !H) { requestAnimationFrame(frame); return; }   // šatník je skrytý (jiná stránka) – počkej, až bude vidět
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
    const q = [], SK = SKIN;
    for (const [n, b] of Object.entries(BONE)) if (SKINMAP[n]) for (const [f, to] of b.b) addSkinBox(q, mats[n], f, to, SKINMAP[n], SK);
    // čepice: model z resource packu na hlavě
    if (st.hat?.model) {
      const mm = MODELS[st.hat.model], s = .625 * mm.h[1] * .9375;
      const m = mul(mats.h_ph_head, mul(T(0, 26.25 + mm.h[0] * .625 * .9375, 0), mul(RY(0), mul(S(s), T(-8, -8, -8)))));
      const n0 = q.length; addModel(q, st.hat.model, m); for (let i = n0; i < q.length; i++) q[i].L = 1;
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
    // aura: stejné částice jako na serveru (Cosmetics.tickAuras běží každé 2 ticky = 10× za sekundu)
    const glyph = (g, c, s) => (cx, x, y, sc) => { cx.fillStyle = c; cx.shadowColor = c; cx.shadowBlur = 8; cx.font = `${Math.max(6, sc * 2.2 * s)}px sans-serif`; cx.textAlign = "center"; cx.textBaseline = "middle"; cx.fillText(g, x, y); cx.shadowBlur = 0; cx.textAlign = "start"; cx.textBaseline = "alphabetic"; };
    const aid = st.aura ? st.aura.id : null;
    if (aid !== auraId) { auraId = aid; aps = []; auraT = now; }
    if (aid) {
      if (now - auraT > 600) auraT = now;
      while (auraT + 100 <= now) { auraT += 100; emitAura(aid, Math.round(auraT / 100) * 2, P, aps); }
      const dtk = Math.min(4, (now - (api._ln || now)) / 50); aps = aps.filter((a) => (a.l -= a.d * dtk) > 0);
    }
    for (const a of aps) { const dk = Math.min(4, (now - (api._ln || now)) / 50); a.p[0] += a.v[0] * B16 * dk; a.p[1] += a.v[1] * B16 * dk; a.p[2] += a.v[2] * B16 * dk; ex.push({ p: a.p, draw: (cx, x, y, sc) => drawAuraParticle(cx, x, y, sc, a) }); }
    // stopa: částice zůstávají na zemi tam, kudy postava šla
    if (st.trail) {
      const tp = TRAILP[st.trail.id] || "rod";
      /* jako Cosmetics.tickTrails: každé 2 ticky 2 částice u nohou (offset 0.2/0.05/0.2, rychlost 0.01), chování a život podle vanilla částic (ticky) */
      const PH = { flame: [8, 20, 0, -.04], soul: [8, 20, 0, -.04], heart: [16, 16, .1, 0], note: [6, 6, 0, 0], rod: [60, 72, -.0003, .02], leaf: [100, 140, -.006, 0], witch: [8, 16, .02, 0], rainbow: [10, 20, 0, 0],
        snow: [80, 120, -.002, 0], spark: [7, 11, 0, 0], cloud: [8, 20, .004, 0], sculk: [40, 40, .002, 0], sga: [30, 40, -.02, 0], glowp: [12, 40, 0, 0], portal: [40, 50, .005, 0], totem: [60, 72, .01, 0], happy: [20, 30, 0, 0] }[tp] || [20, 20, 0, 0];
      api._tt = api._tt || now;
      if (now - api._tt > 400) api._tt = now;
      while (api._tt + 100 <= now) {
        api._tt += 100;
        if (!walk) continue;
        const hue = (now / 15) % 360;
        for (let k = 0; k < 2; k++) {
          const g = () => { let s = 0; for (let i = 0; i < 4; i++) s += Math.random(); return (s - 2) * .5; };   // gaussovský rozptyl jako ve hře
          const col = tp === "note" ? `hsl(${(Math.random() * 360) | 0},90%,55%)` : tp === "totem" ? (Math.random() < .5 ? "#8ee84a" : "#ffe05a") : `hsl(${hue | 0},100%,55%)`;
          const life = PH[0] + Math.random() * (PH[1] - PH[0]), sp = .01;
          parts.push({ type: tp, p: [P[0] + g() * .2 * 2 * B16, 2.4 + g() * .05 * 2 * B16, P[2] + g() * .2 * 2 * B16], l: 1, d: 1 / life, sz: tp === "rod" || tp === "totem" ? 1 - Math.random() * .3 : 1, col,
            v: [g() * sp, (PH[3] ? 0 : g() * sp) + Math.abs(PH[3]) * .25, g() * sp], gy: PH[2] });
        }
      }
      const dk = Math.min(4, (now - (api._ln || now)) / 50);
      parts = parts.filter((p) => (p.l -= p.d * dk) > 0);
      for (const p of parts) {
        p.v[1] -= p.gy * .05 * dk; if (p.type === "leaf") { p.v[0] += Math.sin(now / 300 + p.p[2]) * .0004; }
        p.p[0] += p.v[0] * B16 * dk; p.p[1] = Math.max(.3, p.p[1] + p.v[1] * B16 * dk); p.p[2] += p.v[2] * B16 * dk;
        if (p.p[1] <= .3) { p.v[0] *= .5; p.v[2] *= .5; }
        if (p.type === "flame" || p.type === "soul") p.sz = 1 - (1 - p.l) * (1 - p.l) * .5;   // plamen se zmenšuje jako ve hře
        ex.push({ p: p.p, draw: ((pp) => (cx, x, y, sc) => drawAuraParticle(cx, x, y, sc, pp))(p) });
      }
    } else parts = [];
    const proj = draw(ctx, q, { w: W, h: H, cy: H * .86 - 16 * H / (st.trail ? 64 : 52), oy: 16, yaw, pitch, scale: H / (st.trail ? 64 : 52) }, ex);
    // provázek balónku
    for (const e of ex) if (e.line) { const a = proj(e.line.slice(0, 3)), b = proj(e.line.slice(3)); ctx.strokeStyle = "#ddd9"; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.quadraticCurveTo((a[0] + b[0]) / 2 + 6, (a[1] + b[1]) / 2, b[0], b[1]); ctx.stroke(); }
    // symboly emotu nad hlavou (jako ve hře)
    if (emote && SYM[emote] && Math.random() < .05) { const h = proj(ap(mats.h_ph_head, [0, 32, 0])); syms.push({ x: h[0] + (Math.random() - .5) * 30, y: h[1], l: 1, s: SYM[emote] }); }
    syms = syms.filter((s) => (s.l -= .01) > 0);
    for (const s of syms) { s.y -= .6; ctx.globalAlpha = s.l; ctx.fillStyle = "#ffd23a"; ctx.font = "bold 18px sans-serif"; ctx.textAlign = "center"; ctx.fillText(s.s, s.x, s.y); ctx.textAlign = "start"; }
    ctx.globalAlpha = 1; api._ln = now;
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  return api;
}

/* ---------- Mars jako čistý minecraftí blok: 16×16, pár sytých odstínů, ostré pixely, stínování stěn jako ve hře ---------- */
export function startMarsCube(canvas) {
  const ctx = canvas.getContext("2d"), N = 16;
  let s0 = 77; const rnd = () => ((s0 = (s0 * 1103515245 + 12345) >>> 0) / 4294967296);
  const SH = ["#f0a05a", "#e8873f", "#dc7634", "#cc662c", "#b85724"];
  function tex(light) {
    const c = document.createElement("canvas"); c.width = c.height = N; const x = c.getContext("2d");
    const lv = Array.from({ length: N * N }, () => 1 + (rnd() * 2 | 0));
    for (let k = 0; k < 7; k++) { const cx = rnd() * N | 0, cy = rnd() * N | 0, d = rnd() < .5 ? 0 : 3; for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) if (rnd() < .8) lv[((cy + dy) % N) * N + (cx + dx) % N] = d; }
    for (let k = 0; k < 10; k++) lv[(rnd() * 256) | 0] = 4;
    for (let i = 0; i < N * N; i++) { x.fillStyle = SH[lv[i]]; x.fillRect(i % N, i / N | 0, 1, 1); }
    if (light < 1) { x.fillStyle = `rgba(0,0,0,${1 - light})`; x.fillRect(0, 0, N, N); }
    return c;
  }
  const T = [tex(1), tex(1), tex(1), tex(1), tex(1), tex(1)];
  // stěny krychle: [roh, osa u, osa v, normála]
  const F = [[[-1, 1, -1], [2, 0, 0], [0, -2, 0], [0, 0, -1]], [[1, 1, 1], [-2, 0, 0], [0, -2, 0], [0, 0, 1]], [[-1, 1, 1], [0, 0, -2], [0, -2, 0], [-1, 0, 0]],
             [[1, 1, -1], [0, 0, 2], [0, -2, 0], [1, 0, 0]], [[-1, 1, 1], [2, 0, 0], [0, 0, -2], [0, 1, 0]], [[-1, -1, -1], [2, 0, 0], [0, 0, 2], [0, -1, 0]]];
  const render = () => {
    const W = canvas.clientWidth, Hh = canvas.clientHeight, dpr = Math.min(2, devicePixelRatio || 1); if (!W) return;
    canvas.width = W * dpr; canvas.height = Hh * dpr; ctx.clearRect(0, 0, canvas.width, canvas.height);
    const m = mul(RZ(-8 * D), mul(RX(-24 * D), RY(-32 * D))), s = W * .22, cx = W / 2, cy = Hh * .5;
    const P = (p) => { const v = ap(m, p); return [cx - v[0] * s, cy - v[1] * s, v[2]]; };
    const vis = F.map((f, i) => ({ f, i, n: ap(m, f[3].concat()).map((x, k) => x - ap(m, [0, 0, 0])[k]) })).filter((o) => o.n[2] < 0);
    for (const { f, i, n } of vis) {
      const o = P(f[0]), u = P(f[0].map((x, k) => x + f[1][k])), v = P(f[0].map((x, k) => x + f[2][k]));
      const light = Math.max(.45, Math.min(1, .62 + n[1] * .38 - n[0] * .12));
      ctx.setTransform(dpr * (u[0] - o[0]) / N, dpr * (u[1] - o[1]) / N, dpr * (v[0] - o[0]) / N, dpr * (v[1] - o[1]) / N, dpr * o[0], dpr * o[1]);
      ctx.imageSmoothingEnabled = false; ctx.drawImage(T[i], 0, 0);
      ctx.fillStyle = `rgba(0,0,0,${1 - light})`; ctx.fillRect(0, 0, N, N);
    }
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    // kopule lobby na vršku: skleněná polokoule z voxelů + rám
    const R = 10, vs = 1.4 / 2 / R, dq = [];
    for (let x = -R; x < R; x++) for (let z = -R; z < R; z++) for (let y = 0; y < R; y++) {
      const d = Math.hypot(x + .5, y + .5, z + .5); if (d > R || d < R - 1.2) continue;
      const c = [(x + .5) * vs * 2 / 2, 1 + (y + .5) * vs, (z + .5) * vs];
      const frame = (Math.abs(x + .5) < .6 || Math.abs(z + .5) < .6 || y === 4);
      const tp = ap(m, c); dq.push({ p: [cx - tp[0] * s, cy - tp[1] * s], z: tp[2], f: frame, y });
    }
    dq.sort((a, b) => b.z - a.z);
    const px = vs * s * 1.15;
    for (const q of dq) { ctx.fillStyle = q.f ? "rgba(235,245,255,.95)" : `rgba(150,225,255,${.16 + q.y * .012})`; ctx.fillRect((q.p[0] - px / 2) * dpr, (q.p[1] - px / 2) * dpr, px * dpr, px * dpr); }
    // vnitřní záře lobby
    const top = ap(m, [0, 1.15, 0]), gx = (cx - top[0] * s) * dpr, gy = (cy - top[1] * s) * dpr, gr = ctx.createRadialGradient(gx, gy, 2, gx, gy, s * .6 * dpr);
    gr.addColorStop(0, "#ffd27a66"); gr.addColorStop(1, "#ffd27a00"); ctx.fillStyle = gr; ctx.fillRect(gx - s * dpr, gy - s * dpr, 2 * s * dpr, 2 * s * dpr);
  };
  render(); new ResizeObserver(render).observe(canvas);
}
