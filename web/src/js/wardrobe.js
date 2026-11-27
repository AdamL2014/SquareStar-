// Šatník / kosmetika: kategorie, mřížka věcí, 3D postava s nasazenými věcmi, mince.
// V náhledu vše jen v prohlížeči. API: GET /api/cosmetics, POST /api/cosmetics/buy {id}, POST /api/cosmetics/equip {slot,id,color}
import { $, esc, pixelHead } from "./util.js";
import { S, needLogin, onAccount, all } from "./account.js";
import { toast } from "./fx.js";

/* ---------- pixel sprity 12×12 (písmena = barvy z palety, "a" = barva varianty) ---------- */
const SP = {
  cap: ["............", "............", "............", "....aaaa....", "...aaaaaa...", "..aaaawaaa..", "..aaawwwaa..", "..aaaawaaa..", "..bbbbbbbbbb", "..bbbbbbbbbb", "............", "............"],
  halo: ["............", "............", "...yyyyyy...", "..y......y..", "..y......y..", "...yyyyyy...", "............", "............", "............", "............", "............", "............"],
  crown: ["............", "............", "..y..y..y...", "..yy.yy.yy..", "..yyyyyyyy..", "..yryyyryy..", "..yyyyyyyy..", "..oooooooo..", "............", "............", "............", "............"],
  bigcrown: ["............", ".y...y...y..", ".yy.yyy.yy..", ".yyyyyyyyy..", ".yrrrrrrry..", ".yrryyyrry..", ".yrrrrrrry..", ".yyyyyyyyy..", ".ooooooooo..", "............", "............", "............"],
  helmet: ["............", "...wwwwww...", "..wwwwwwww..", ".wwccccccww.", ".wcaaaaaacw.", ".wcaaaaaacw.", ".wcaaaaaacw.", ".wwccccccww.", "..wwwwwwww..", "...kkkkkk...", "............", "............"],
  beanie: ["............", ".....w......", "....aaa.....", "...aaaaa....", "..aaaaaaa...", "..aaaaaaa...", "..wwwwwww...", "..wwwwwww...", "............", "............", "............", "............"],
  wings: ["a..........a", "aa........aa", "aac......caa", "aaac....caaa", "aaaac..caaaa", "aaaaa..aaaaa", ".aaaa..aaaa.", ".aaa....aaa.", "..aa....aa..", "..a......a..", "............", "............"],
  dragon: ["r..........r", "rr........rr", "rbr......rbr", "rbbr....rbbr", "rbbbr..rbbbr", "rbbbb..bbbbr", ".rbbb..bbbr.", "..rbb..bbr..", "...r....r...", "............", "............", "............"],
  butterfly: ["............", ".aa......aa.", "aaaa....aaaa", "acca....acca", "aaaak..kaaaa", ".aaak..kaaa.", "..aak..kaa..", ".aaa....aaa.", ".aa......aa.", "............", "............", "............"],
  cape: ["............", "..aaaaaaaa..", "..aaaaaaaa..", "..aacccaaa..", "..acyyycaa..", "..acyyycaa..", "..aacccaaa..", "..aaaaaaaa..", "..aaaaaaaa..", "..bbbbbbbb..", "............", "............"],
  jetpack: ["............", "...kk..kk...", "..kaak.kaak.", "..kaak.kaak.", "..kaak.kaak.", "..kaak.kaak.", "..kkkk.kkkk.", "...yy...yy..", "...ry...ry..", "....r....r..", "............", "............"],
  flames: ["............", ".....r......", "....rr...r..", "...ryr..rr..", "...ryyr.ryr.", "..ryyyr.ryr.", "..ryyyyrryr.", "..ryywyyryr.", "...ryyyyyr..", "....rrrrr...", "............", "............"],
  stars: ["............", "....y.......", "...yyy......", "....y....y..", ".........y..", "..y.....yyy.", ".yyy.....y..", "..y...y.....", "......yyy...", ".......y....", "............", "............"],
  hearts: ["............", ".pp.pp......", "ppppppp.....", "ppppppp.pp.p", ".ppppp.ppppp", "..ppp..ppppp", "...p....ppp.", ".........p..", "....pp.pp...", "....ppppp...", ".....ppp....", "......p....."],
  ufo: ["............", "............", "....cccc....", "...cccccc...", "..kkkkkkkk..", ".kwwwwwwwwk.", "kkkkkkkkkkkk", ".kykykykyk..", "....g..g....", "...g....g...", "............", "............"],
  robot: ["............", ".....r......", ".....k......", "...wwwwww...", "...wcwwcw...", "...wwwwww...", "...wkkkkw...", "...wwwwww...", "....kkkk....", "...kk..kk...", "............", "............"],
  cat: ["............", "..o....o....", "..oo..oo....", "..oooooo....", "..ogoogo....", "..oooooo....", "..oowwoo....", "...oooo.....", "..oooooo..o.", "..oooooo.o..", "..o.o.o.oo..", "............"],
};
const PAL = { w: "#f4f4f4", k: "#2b2d36", y: "#ffd23a", o: "#e08a1c", r: "#e23b3b", c: "#9fe8ff", g: "#5cdc6a", p: "#ff6fae" };
const shade = (hex, k) => { const n = parseInt(hex.slice(1), 16); const f = (v) => Math.max(0, Math.min(255, Math.round(v * k))); return `rgb(${f(n >> 16)},${f((n >> 8) & 255)},${f(n & 255)})`; };
function sprite(name, col = "#3a8bff") {
  const rows = SP[name]; let s = "";
  rows.forEach((row, y) => [...row].forEach((ch, x) => {
    if (ch === ".") return;
    const c = ch === "a" ? col : ch === "b" ? shade(col, .7) : ch === "c" && name !== "ufo" && name !== "robot" && name !== "butterfly" ? shade(col, 1.35) : PAL[ch] || col;
    s += `<rect x="${x}" y="${y}" width="1.02" height="1.02" fill="${c}"/>`;
  }));
  return `<svg viewBox="0 0 12 12" shape-rendering="crispEdges" aria-hidden="true">${s}</svg>`;
}

/* ---------- katalog ---------- */
const COLORS = ["#2f86ff", "#2fd24a", "#ff9a1f", "#ff7ac8", "#a05cff", "#ff4b4b"];
const WCATS = [
  { id: "outfity", name: "Outfity", tint: "#4ade80" },
  { id: "emoty", name: "Emoty", tint: "#fb923c" },
  { id: "kosmetika", name: "Kosmetika", tint: "#facc15", sub: [["plast", "Plášť"], ["kridla", "Křídla"], ["zada", "Záda"], ["castice", "Částice"], ["mazlicci", "Mazlíčci"], ["klobouk", "Klobouk"]] },
];
const ITEMS = [
  { id: "cap", n: "SquareStar kšiltovka", slot: "klobouk", sp: "cap", price: 0, colors: COLORS.slice(0, 4) },
  { id: "halo", n: "Svatozář", slot: "klobouk", sp: "halo", price: 400, isNew: 1 },
  { id: "crown", n: "Koruna", slot: "klobouk", sp: "crown", price: 900 },
  { id: "bigcrown", n: "Velká koruna", slot: "klobouk", sp: "bigcrown", price: 1800 },
  { id: "helmet", n: "Helma astronauta", slot: "klobouk", sp: "helmet", price: 700, isNew: 1, colors: ["#5bc8ff", "#ffb84a", "#9a7bff"] },
  { id: "beanie", n: "Kulich", slot: "klobouk", sp: "beanie", price: 250, colors: ["#e23b3b", "#2f86ff", "#2fd24a"] },
  { id: "wings", n: "Andělská křídla", slot: "kridla", sp: "wings", price: 1500, colors: ["#8ec5ff", "#ffffff", "#ffd23a"] },
  { id: "dragon", n: "Dračí křídla", slot: "kridla", sp: "dragon", price: 2200, isNew: 1 },
  { id: "butterfly", n: "Motýlí křídla", slot: "kridla", sp: "butterfly", price: 1200, colors: ["#ff7ac8", "#a05cff", "#5bc8ff"] },
  { id: "cape", n: "SquareStar plášť", slot: "plast", sp: "cape", price: 0, colors: ["#d4501c", "#2f3a8f", "#1f1f2a"] },
  { id: "capemars", n: "Marsovský plášť", slot: "plast", sp: "cape", price: 800, colors: ["#b3200e"] },
  { id: "jetpack", n: "Raketový batoh", slot: "zada", sp: "jetpack", price: 1300, isNew: 1, colors: ["#9aa3b5", "#ff9a1f"] },
  { id: "flames", n: "Plamínky", slot: "castice", sp: "flames", price: 600 },
  { id: "stars", n: "Hvězdičky", slot: "castice", sp: "stars", price: 500 },
  { id: "hearts", n: "Srdíčka", slot: "castice", sp: "hearts", price: 500 },
  { id: "ufo", n: "Mini UFO", slot: "mazlicci", sp: "ufo", price: 1600, isNew: 1 },
  { id: "robot", n: "Robůtek", slot: "mazlicci", sp: "robot", price: 1100 },
  { id: "cat", n: "Kočička", slot: "mazlicci", sp: "cat", price: 900 },
];
const EMOTES = [["Mávání", 0], ["Tanec", 300], ["Facepalm", 200], ["Salto", 450], ["Zívání", 150], ["Tleskání", 0]];

const W = { cat: "kosmetika", sub: "klobouk", q: "", sel: "cap", owned: new Set(["cap", "cape"]), color: { cap: COLORS[0] }, outfits: [{ name: "Outfit 1", eq: { klobouk: "cap", plast: "cape" } }, { name: "Vesmírák", eq: { klobouk: "helmet", zada: "jetpack", mazlicci: "ufo" } }], oi: 0, angle: -25, ownEm: new Set(["Mávání", "Tleskání"]) };
const eq = () => W.outfits[W.oi].eq;
const item = (id) => ITEMS.find((i) => i.id === id);
const colOf = (it) => W.color[it.id] || (it.colors ? it.colors[0] : "#3a8bff");

/* ---------- UI ---------- */
function renderSide() {
  $("wdSide").innerHTML = `<div class="wd-h">Šatník</div>` + WCATS.map((c) => `<button class="wd-cat${W.cat === c.id ? " on" : ""}" data-wd="cat" data-v="${c.id}" style="--t:${c.tint}">${c.name}</button>
    ${c.sub && W.cat === c.id ? `<div class="wd-subs">${c.sub.map(([id, n]) => `<button class="wd-sub${W.sub === id ? " on" : ""}" data-wd="sub" data-v="${id}">${n}</button>`).join("")}</div>` : ""}`).join("");
}
function card(it) {
  const own = W.owned.has(it.id), on = eq()[it.slot] === it.id, sel = W.sel === it.id;
  return `<button class="wd-item${sel ? " sel" : ""}${own ? " own" : ""}" data-wd="item" data-v="${it.id}" aria-label="${esc(it.n)}">
    ${it.isNew && !own ? '<span class="wd-new">NOVÉ</span>' : ""}
    <div class="wd-art"><div class="wd-ped"></div>${sprite(it.sp, colOf(it))}</div>
    ${it.colors && it.colors.length > 1 ? `<div class="wd-dots">${it.colors.map((c) => `<i style="background:${c}"></i>`).join("")}</div>` : ""}
    ${own ? (on ? '<span class="wd-ok" title="Nasazeno">✓</span>' : "") : `<span class="wd-price">${it.price}<i class="coin"></i></span>`}
    <span class="wd-name">${esc(it.n)}</span></button>`;
}
function renderGrid() {
  const q = W.q.toLowerCase();
  let html = "";
  if (W.cat === "kosmetika") { $("wdTitle").textContent = "Kosmetika"; html = ITEMS.filter((i) => (q ? i.n.toLowerCase().includes(q) : i.slot === W.sub)).map(card).join(""); }
  else if (W.cat === "emoty") {
    $("wdTitle").textContent = "Emoty";
    html = EMOTES.filter(([n]) => !q || n.toLowerCase().includes(q)).map(([n, p]) => { const own = W.ownEm.has(n); return `<button class="wd-item emote${own ? " own" : ""}" data-wd="emote" data-v="${esc(n)}"><div class="wd-art"><img class="head big" src="${pixelHead(S.user || "Steve")}" alt=""></div>${own ? "" : `<span class="wd-price">${p}<i class="coin"></i></span>`}<span class="wd-name">${esc(n)}</span></button>`; }).join("");
  } else {
    $("wdTitle").textContent = "Outfity";
    html = W.outfits.map((o, i) => `<button class="wd-item outfit${i === W.oi ? " sel" : ""}" data-wd="outfit" data-v="${i}"><div class="wd-art mini">${Object.values(o.eq).slice(0, 3).map((id) => sprite(item(id).sp, colOf(item(id)))).join("")}</div><span class="wd-name">${esc(o.name)}</span></button>`).join("");
  }
  $("wdGrid").innerHTML = html || '<div class="empty">Nic jsme nenašli.</div>';
  $("wdCoins").innerHTML = (S.user ? S.coins.toLocaleString("cs-CZ") : "—") + '<i class="coin"></i>';
}
function renderPanel() {
  const it = item(W.sel);
  $("wdOutfit").textContent = W.outfits[W.oi].name;
  $("wdColors").innerHTML = it && it.colors && it.colors.length > 1 ? it.colors.map((c) => `<button class="sw${colOf(it) === c ? " on" : ""}" style="background:${c}" data-wd="color" data-v="${c}" aria-label="Barva ${c}"></button>`).join("") : "";
  let act = "";
  if (it && W.cat === "kosmetika") {
    const own = W.owned.has(it.id), on = eq()[it.slot] === it.id;
    act = own ? `<button class="gbtn ${on ? "ghost" : "green"} sm" data-wd="equip">${on ? "Sundat" : "Nasadit"}</button>`
      : `<button class="gbtn sm" data-wd="buy">Koupit za ${it.price} <i class="coin"></i></button>`;
    act = `<span class="wd-sel">${esc(it.n)}</span>` + act;
  }
  $("wdAct").innerHTML = act;
  const e = eq();
  $("wdEq").innerHTML = `<span>Máš nasazeno:</span>` + (Object.keys(e).length ? Object.entries(e).map(([slot, id]) => `<button class="chip" data-wd="unequip" data-v="${slot}" title="Sundat">${sprite(item(id).sp, colOf(item(id)))}${esc(item(id).n)} ×</button>`).join("") : "<em>nic</em>");
  renderRig();
}

/* ---------- 3D postava ---------- */
const U = 7; // 1 pixel skinu = 7 px
function box(w, h, d, x, y, z, faces, cls = "") {
  const f = (n, W2, H2, tr, c) => `<i class="f ${n}" style="width:${W2}px;height:${H2}px;transform:${tr};${c}"></i>`;
  const [fr, bk, l, r, t, b] = faces;
  return `<div class="bx ${cls}" style="transform:translate3d(${x}px,${y}px,${z}px)">${f("q1", w, h, `translateZ(${d / 2}px)`, fr)}${f("q2", w, h, `rotateY(180deg) translateZ(${d / 2}px)`, bk)}${f("q3", d, h, `rotateY(-90deg) translateZ(${w / 2}px)`, l)}${f("q4", d, h, `rotateY(90deg) translateZ(${w / 2}px)`, r)}${f("q5", w, d, `rotateX(90deg) translateZ(${h / 2}px)`, t)}${f("q6", w, d, `rotateX(-90deg) translateZ(${h / 2}px)`, b)}</div>`;
}
const solid = (c) => `background:${c}`;
function renderRig() {
  const nick = S.user || "Steve", face = pixelHead(nick), e = eq(), skin = "#d9a06a", hair = "#3b2416";
  const shirt = "#3aa6b9", pants = "#3b4aa8", shoe = "#4a4a4a";
  const sh = (c, k) => solid(shade(c, k));
  let h = "";
  // hlava (8×8×8)
  h += box(8 * U, 8 * U, 8 * U, 0, -10 * U, 0, [`background:url(${face}) 0 0/100% 100%`, sh(hair, .9), sh(skin, .85), sh(skin, .85), solid(hair), sh(skin, .8)], "head");
  // tělo, ruce, nohy
  h += box(8 * U, 12 * U, 4 * U, 0, 0, 0, [solid(shirt), sh(shirt, .8), sh(shirt, .75), sh(shirt, .75), sh(shirt, 1.1), sh(shirt, .7)]);
  h += box(4 * U, 12 * U, 4 * U, -6 * U, 0, 0, [`background:linear-gradient(${skin} 0 33%,${shirt} 33%)`, sh(skin, .8), sh(skin, .75), sh(skin, .85), sh(shirt, 1.1), sh(skin, .7)], "arm");
  h += box(4 * U, 12 * U, 4 * U, 6 * U, 0, 0, [`background:linear-gradient(${shirt} 0 67%,${skin} 67%)`, sh(skin, .8), sh(skin, .85), sh(skin, .75), sh(shirt, 1.1), sh(skin, .7)], "arm");
  h += box(4 * U, 12 * U, 4 * U, -2 * U, 12 * U, 0, [`background:linear-gradient(${pants} 0 80%,${shoe} 80%)`, sh(pants, .8), sh(pants, .75), sh(pants, .85), sh(pants, 1), solid(shoe)], "leg");
  h += box(4 * U, 12 * U, 4 * U, 2 * U, 12 * U, 0, [`background:linear-gradient(${pants} 0 80%,${shoe} 80%)`, sh(pants, .8), sh(pants, .85), sh(pants, .75), sh(pants, 1), solid(shoe)], "leg");
  // kosmetika
  const hat = e.klobouk && item(e.klobouk);
  if (hat) {
    if (hat.id === "halo") h += `<div class="ax halo" style="transform:translate3d(0,${-16 * U}px,0) rotateX(90deg)"></div>`;
    else { const c = colOf(hat), tall = hat.id === "bigcrown" || hat.id === "helmet" ? 4 : 2.5, ww = hat.id === "helmet" ? 9.4 : 8.8;
      const gold = hat.id.includes("crown") ? "#ffd23a" : c;
      h += box(ww * U, tall * U, ww * U, 0, (-14 - tall / 2 - (hat.id === "helmet" ? -2 : 0)) * U + (hat.id === "helmet" ? 0 : 0), hat.id === "cap" ? .4 * U : 0, [sh(gold, 1), sh(gold, .8), sh(gold, .85), sh(gold, .85), sh(gold, 1.15), sh(gold, .6)], "hat");
      if (hat.id === "cap") h += box(8.8 * U, .8 * U, 4 * U, 0, -14.6 * U, 6 * U, [sh(c, .7), sh(c, .7), sh(c, .7), sh(c, .7), sh(c, .9), sh(c, .6)]);
      if (hat.id === "helmet") h += `<div class="ax visor" style="transform:translate3d(0,${-10 * U}px,${4.75 * U}px)"></div>`; }
  }
  if (e.plast) { const c = colOf(item(e.plast)); h += `<div class="ax cape" style="transform:translate3d(0,${.8 * U}px,${-2.6 * U}px) rotateX(8deg);background:linear-gradient(${shade(c, 1.1)},${shade(c, .75)})"><span>${e.plast === "cape" ? "★" : ""}</span></div>`; }
  if (e.kridla) { const w = item(e.kridla), s = sprite(w.sp, colOf(w)); h += `<div class="ax wing l" style="transform:translate3d(${-7 * U}px,${-4 * U}px,${-3 * U}px) rotateY(30deg)">${s}</div><div class="ax wing r" style="transform:translate3d(${7 * U}px,${-4 * U}px,${-3 * U}px) rotateY(-30deg)">${s}</div>`; }
  if (e.zada) { const c = colOf(item(e.zada)); h += box(7 * U, 9 * U, 3 * U, 0, -.5 * U, -3.6 * U, [sh(c, .85), sh(c, 1), sh(c, .8), sh(c, .8), sh(c, 1.1), sh(c, .6)]) + `<div class="ax flame" style="transform:translate3d(${-2 * U}px,${6 * U}px,${-4 * U}px)"></div><div class="ax flame" style="transform:translate3d(${2 * U}px,${6 * U}px,${-4 * U}px)"></div>`; }
  if (e.mazlicci) { const p = item(e.mazlicci); h += `<div class="ax pet" style="transform:translate3d(${11 * U}px,${-12 * U}px,${2 * U}px)">${sprite(p.sp, colOf(p))}</div>`; }
  $("rig").innerHTML = `<div class="rot" id="rot" style="transform:rotateX(-8deg) rotateY(${W.angle}deg)">${h}</div>`;
  const pt = e.castice ? item(e.castice).sp : "";
  $("pfx").className = "pfx " + pt;
  $("pfx").innerHTML = pt ? Array.from({ length: 14 }, (_, i) => `<i style="--x:${(i * 37) % 100}%;--d:${(i % 7) * .45}s;--s:${.7 + (i % 3) * .25}"></i>`).join("") : "";
}
const setAngle = (a) => { W.angle = a; const r = $("rot"); if (r) r.style.transform = `rotateX(-8deg) rotateY(${a}deg)`; };

const renderAll = () => { renderSide(); renderGrid(); renderPanel(); };

export function startWardrobe() {
  onAccount.push(() => { renderGrid(); renderRig(); });
  renderAll();
  $("wdSearch").addEventListener("input", (e) => { W.q = e.target.value.trim(); renderGrid(); });
  // otáčení tažením
  const st = $("stage"); let drag = null;
  st.addEventListener("pointerdown", (e) => { drag = { x: e.clientX, a: W.angle }; st.setPointerCapture(e.pointerId); st.classList.add("drag"); });
  st.addEventListener("pointermove", (e) => { if (drag) setAngle(drag.a + (e.clientX - drag.x) * .8); });
  const end = () => { drag = null; st.classList.remove("drag"); };
  st.addEventListener("pointerup", end); st.addEventListener("pointercancel", end);
  $("kosmetika").addEventListener("click", (e) => {
    const b = e.target.closest("[data-wd]"); if (!b) return;
    const a = b.dataset.wd, v = b.dataset.v, it = item(W.sel);
    if (a === "cat") { W.cat = v; W.q = ""; $("wdSearch").value = ""; renderAll(); }
    else if (a === "sub") { W.sub = v; const first = ITEMS.find((i) => i.slot === v); if (first) W.sel = first.id; renderAll(); }
    else if (a === "item") { W.sel = v; const x = item(v); if (W.owned.has(v)) eq()[x.slot] = v; renderGrid(); renderPanel(); }
    else if (a === "color") { W.color[it.id] = v; renderGrid(); renderPanel(); }
    else if (a === "equip") { const s = eq(); if (s[it.slot] === it.id) delete s[it.slot]; else s[it.slot] = it.id; renderGrid(); renderPanel(); }
    else if (a === "unequip") { delete eq()[v]; renderGrid(); renderPanel(); }
    else if (a === "buy") {
      if (needLogin()) return;
      if (S.coins < it.price) return toast("Nemáš dost mincí");
      S.coins -= it.price; W.owned.add(it.id); eq()[it.slot] = it.id; all(); renderPanel(); toast(`Koupeno: ${it.n}`);
    }
    else if (a === "emote") {
      const p = EMOTES.find(([n]) => n === v)[1];
      if (!W.ownEm.has(v)) { if (needLogin()) return; if (S.coins < p) return toast("Nemáš dost mincí"); S.coins -= p; W.ownEm.add(v); all(); toast(`Emote ${v} je tvůj!`); }
      const r = $("rig"); r.classList.remove("dance"); void r.offsetWidth; r.classList.add("dance");
    }
    else if (a === "outfit") { W.oi = +v; renderAll(); }
    else if (a === "prev" || a === "next") { W.oi = (W.oi + (a === "next" ? 1 : -1) + W.outfits.length) % W.outfits.length; renderAll(); }
    else if (a === "new") { W.outfits.push({ name: `Outfit ${W.outfits.length + 1}`, eq: {} }); W.oi = W.outfits.length - 1; renderAll(); toast("Nový outfit vytvořen"); }
    else if (a === "front" || a === "back") { document.querySelectorAll(".wd-fb button").forEach((x) => x.classList.toggle("on", x === b)); setAngle(a === "front" ? -25 : 155); }
    else if (a === "spin") { let t0 = null; const s = W.angle; const f = (t) => { t0 ??= t; const k = Math.min(1, (t - t0) / 1200); setAngle(s + 360 * (1 - (1 - k) ** 3)); if (k < 1) requestAnimationFrame(f); }; requestAnimationFrame(f); }
  });
}
