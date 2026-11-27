// Šatník / kosmetika: kategorie, mřížka věcí, 3D postava s nasazenými věcmi, mince.
// V náhledu vše jen v prohlížeči. API: GET /api/cosmetics, POST /api/cosmetics/buy {id}, POST /api/cosmetics/equip {slot,id,color}
import { $, esc, pixelHead } from "./util.js";
import { S, needLogin, onAccount, all, coin } from "./account.js";
import { toast } from "./fx.js";
import { startStage, modelThumb, bannerPixels } from "./render3d.js";

/* ---------- pixel sprity 12×12 (písmena = barvy z palety, "a" = barva varianty) ---------- */
const SP = {
  rocket: [".....w......", "....wrw.....", "....www.....", "...wwcww....", "...wcccw....", "...wwcww....", "...wwwww....", "..rwwwwwr...", "..rr.y.rr...", ".....y......", "....yry.....", ".....r......"],
  ufo: ["............", "............", "....cccc....", "...cccccc...", "..kkkkkkkk..", ".kwwwwwwwwk.", "kkkkkkkkkkkk", ".kykykykyk..", "....g..g....", "...g....g...", "............", "............"],
  planet: ["............", "....aaaa....", "..aacaaaaa..", ".aaccaabaaa.", ".acaaaabbaa.", "aaaaaaaaabba", "aabbaaaaaaba", "aabbbaaacaaa", ".aaabaaaaaa.", ".aaaaaaabba.", "..aaaaaaaa..", "....aaaa...."],
  saturn: ["............", "............", "....aaaa....", "...acaaaa...", "yy.aaaaaa.yy", ".yyyyyyyyyy.", "..ybbaaaby..", "...aaaaaa...", "....aaaa....", "............", "............", "............"],
  helmet: ["............", "...wwwwww...", "..wwwwwwww..", ".wwccccccww.", ".wcaaaaaacw.", ".wcaaaaaacw.", ".wcaaaaaacw.", ".wwccccccww.", "..wwwwwwww..", "...kkkkkk...", "............", "............"],
  star: ["............", ".....y......", ".....y......", "....yyy.....", "yyyyyyyyyyy.", ".yyyywyyyy..", "..yyyyyyy...", "...yyyyy....", "..yyy.yyy...", "..yy...yy...", ".y.......y..", "............"],
  crown: ["............", "............", "..y..y..y...", "..yy.yy.yy..", "..yyyyyyyy..", "..yryyyryy..", "..yyyyyyyy..", "..oooooooo..", "............", "............", "............", "............"],
  halo: ["............", "............", "...yyyyyy...", "..y......y..", "..y......y..", "...yyyyyy...", "............", "............", "............", "............", "............", "............"],
  alien: ["..g......g..", "..gg....gg..", "...g....g...", "...g....g...", "....g..g....", "....g..g....", "...aaaaaa...", "..aaaaaaaa..", "..akkaakka..", "..aaaaaaaa..", "...aaaaaa...", "............"],
  propeller: ["..rr....bb..", "....r..b....", ".....ky.....", ".....k......", "....aaaa....", "...ayaaya...", "..aaaaaaaa..", "..yaayaaya..", "..aaaaaaaa..", ".kkkkkkkkkk.", "............", "............"],
  satellite: ["............", "cc......cc..", "cck....kcc..", "cc.k..k.cc..", "....ww......", "...wwww.....", "....ww......", "cc.k..k.cc..", "cck....kcc..", "cc......cc..", "............", "............"],
  witch: [".....k......", ".....kk.....", "....kak.....", "....kaak....", "...kaaak....", "...kaaaak...", "..kyyyyyyk..", "..kaaaaaak..", "kkkkkkkkkkkk", "............", "............", "............"],
  santa: ["........ww..", ".......rww..", "......rrr...", ".....rrrr...", "....rrrrr...", "...rrrrrr...", "..rrrrrrrr..", "..wwwwwwww..", "..wwwwwwww..", "............", "............", "............"],
  pet: ["............", "..a......a..", "..aa....aa..", "..aaaaaaaa..", ".aaaaaaaaaa.", ".awkaaaawka.", ".aaaaaaaaaa.", ".aaaabbaaaa.", "..aaaaaaaa..", "..aa....aa..", "..b.....b...", "............"],
  slime: ["............", "............", "...aaaaaa...", "..acaaaaaa..", ".aakkaakkaa.", ".aakkaakkaa.", ".aaaaaaaaaa.", ".aaaabbaaaa.", ".aaaaaaaaaa.", "..bbbbbbbb..", "............", "............"],
  spider: ["............", "k.k......k.k", ".k.k....k.k.", "..kkkkkkkk..", ".kkkkkkkkkk.", "kkrrkkkkrrkk", ".kkkkkkkkkk.", "..kkkkkkkk..", ".k.k....k.k.", "k.k......k.k", "............", "............"],
  endy: ["............", "...kkkkkk...", "..kkkkkkkk..", "..kkkkkkkk..", "..kpppkppk..", "..kkkkkkkk..", "...kkkkkk...", "....kkkk....", "....k..k....", "....k..k....", "...kk..kk...", "............"],
  bird: ["............", "....aaa.....", "...aakaa....", "..oaaaaa....", "...aaaaaa...", "...aaaaaaa..", "...abbbbaa..", "....aaaaa...", ".....o.o....", "....oo.oo...", "............", "............"],
  balloon: ["....aaaa....", "...acaaaa...", "..accaaaaa..", "..acaaaaaa..", "..aaaaaaaa..", "..aaaaaaab..", "...aaaaab...", "....aaab....", ".....bb.....", "......k.....", ".....k......", "......k....."],
  cape: ["............", "..aaaaaaaa..", "..aaaaaaaa..", "..aacccaaa..", "..acyyycaa..", "..acyyycaa..", "..aacccaaa..", "..aaaaaaaa..", "..aaaaaaaa..", "..bbbbbbbb..", "............", "............"],
  flames: ["............", ".....a......", "....aa...a..", "...aya..aa..", "...ayya.aya.", "..ayyya.aya.", "..ayyyyaaya.", "..ayywyyaya.", "...ayyyyya..", "....aaaaa...", "............", "............"],
  hearts: ["............", ".aa.aa......", "aaaaaaa.....", "aaaaaaa.aa.a", ".aaaaa.aaaaa", "..aaa..aaaaa", "...a....aaa.", ".........a..", "....aa.aa...", "....aaaaa...", ".....aaa....", "......a....."],
  notes: ["............", "......aaaa..", "......a..a..", "......a..a..", "......a..a..", "......a..a..", "....aaa.aaa.", "...aaaa.aaaa", "...aaa..aaa.", "............", "............", "............"],
  sparkle: ["............", "....a.......", "...aca......", "....a....a..", ".........a..", "..a.....aca.", ".aca.....a..", "..a...a.....", "......aca...", ".......a....", "............", "............"],
  snow: ["............", ".....a......", "...a.a.a....", "....aaa.....", "..aaacaaa...", "....aaa.....", "...a.a.a....", ".....a......", ".........a..", "........aaa.", ".........a..", "............"],
  ring: ["............", "....aaaa....", "..aa....aa..", ".a..c..c..a.", ".a........a.", "a..c....c..a", "a..........a", ".a..c..c..a.", ".a........a.", "..aa....aa..", "....aaaa....", "............"],
};
const PAL = { w: "#f4f4f4", k: "#2b2d36", y: "#ffd23a", o: "#e08a1c", r: "#e23b3b", c: "#9fe8ff", g: "#5cdc6a", p: "#d66bff" };
const shade = (hex, k) => { const n = parseInt(hex.slice(1), 16); const f = (v) => Math.max(0, Math.min(255, Math.round(v * k))); return `rgb(${f(n >> 16)},${f((n >> 8) & 255)},${f(n & 255)})`; };
function sprite(name, col = "#3a8bff") {
  const rows = SP[name]; let s = "";
  rows.forEach((row, y) => [...row].forEach((ch, x) => {
    if (ch === ".") return;
    const c = ch === "a" ? col : ch === "b" ? shade(col, .7) : ch === "c" && !["ufo", "satellite", "rocket", "helmet"].includes(name) ? shade(col, 1.45) : PAL[ch] || col;
    s += `<rect x="${x}" y="${y}" width="1.02" height="1.02" fill="${c}"/>`;
  }));
  return `<svg viewBox="0 0 12 12" shape-rendering="crispEdges" aria-hidden="true">${s}</svg>`;
}

/* ---------- katalog ---------- */
/* Katalog = stejné věci jako v pluginu (Cosmetics.java): ID, názvy i ceny. */
const T = (id, n, sp, c) => ({ id: "trail:" + id, n, slot: "stopa", sp, colors: [c] });
const HM = { HELMET: "oxygen_helmet", ROCKET: "hat_rocket", UFO: "hat_ufo", EARTH: "earth", STAR: "star_shard", CROWN: "hat_crown", HALO: "hat_halo", ALIEN: "hat_alien", SATURN: "hat_saturn", PROPELLER: "hat_propeller", SATELLITE: "hat_satellite", MARS: "hat_mars", WITCH: "hat_witch", SANTA: "hat_santa" };
const H = (id, n, sp, c) => ({ id: "hat:" + id, n, slot: "hlava", sp, model: HM[id], colors: [c] });
const P = (id, n, sp, c) => ({ id: "hat:" + id, n, slot: "hlava", pet: 1, sp, model: id.toLowerCase(), colors: [c] });
const B = (id, n, c) => ({ id: "balloon:" + id, n, slot: "balonek", sp: "balloon", model: "balloon_" + id.toLowerCase(), colors: [c] });
const CAPE = { RED: ["RED"], BLUE: ["BLUE"], BLACK: ["BLACK", "BORDER", "RED"], MARS: ["ORANGE", "GRADIENT", "RED"], GALAXY: ["PURPLE", "GRADIENT", "BLACK"], ROYAL: ["PURPLE", "BORDER", "YELLOW"], CREEPER: ["LIME", "CREEPER", "BLACK"], SKULL: ["BLACK", "SKULL", "WHITE"], FLOWER: ["PINK", "FLOWER", "MAGENTA"], ICE: ["LIGHT_BLUE", "GRADIENT", "WHITE"] };
const C = (id, n, c) => ({ id: "cape:" + id, n, slot: "plast", sp: "cape", banner: bannerPixels(...CAPE[id]), colors: [c] });
function bannerSvg(bn) { let s = ""; for (let y = 0; y < bn.H; y++) for (let x = 0; x < bn.W; x++) s += `<rect x="${x}" y="${y}" width="1.05" height="1.05" fill="#${bn.px[y * bn.W + x].toString(16).padStart(6, "0")}"/>`; return `<svg viewBox="-6 -2 32 44" shape-rendering="crispEdges" aria-hidden="true"><rect x="-1" y="-1.5" width="22" height="1.5" fill="#6b4a2a"/>${s}</svg>`; }
const A = (id, n, c) => ({ id: "aura:" + id, n, slot: "aura", sp: "ring", colors: [c] });
const ITEMS = [
  H("HELMET", "Kyslíková helma", "helmet", "#5bc8ff"), H("ROCKET", "Mini raketa", "rocket", "#e23b3b"), H("UFO", "UFO", "ufo", "#9aa3b5"), H("EARTH", "Planeta Země", "planet", "#2f86ff"),
  H("STAR", "Hvězda", "star", "#ffd23a"), H("CROWN", "Koruna", "crown", "#ffd23a"), H("HALO", "Svatozář", "halo", "#ffd23a"), H("ALIEN", "Mimozemská tykadla", "alien", "#5cdc6a"),
  H("SATURN", "Saturn", "saturn", "#d9a65a"), H("PROPELLER", "Čepice s vrtulkou", "propeller", "#2f86ff"), H("SATELLITE", "Satelit", "satellite", "#9aa3b5"), H("MARS", "Planeta Mars", "planet", "#d4501c"),
  H("WITCH", "Čarodějný klobouk", "witch", "#6b3fa0"), H("SANTA", "Čepice Santy", "santa", "#e23b3b"),
  P("PET_SPIDER", "Malý pavouk", "spider", "#2b2d36"), P("PET_CHICKEN", "Kuřátko", "bird", "#f4f4f4"), P("PET_SLIME", "Slime", "slime", "#6fdc5a"), P("PET_FOX", "Liška", "pet", "#e8812a"),
  P("PET_ENDY", "Little Endy", "endy", "#2b2d36"), P("PET_CAT", "Kočka", "pet", "#c9a06a"), P("PET_RACCOON", "Mýval", "pet", "#7a7f8c"), P("PET_CTHULHU", "Mini Cthulhu", "pet", "#3f9e7a"), P("PET_PIGEON", "Holub", "bird", "#8a93a8"),
  B("RED", "Červený balónek", "#e23b3b"), B("BLUE", "Modrý balónek", "#2f86ff"), B("PURPLE", "Fialový balónek", "#a05cff"), B("GOLD", "Zlatý balónek", "#ffc83d"), B("GREEN", "Zelený balónek", "#2fd24a"), B("WHITE", "Bílý balónek", "#eef0f4"),
  T("FLAME", "Ohnivá stopa", "flames", "#ff7a1f"), T("HEARTS", "Srdíčka", "hearts", "#ff4b6a"), T("NOTES", "Noty", "notes", "#3ddc84"), T("STARS", "Hvězdný prach", "sparkle", "#fff3b0"),
  T("SOUL", "Duše", "flames", "#4fd8ff"), T("CHERRY", "Sakura", "sparkle", "#ffb0d0"), T("COSMIC", "Vesmírná magie", "sparkle", "#b06bff"), T("RAINBOW", "Duha", "sparkle", "#ff5a5a"),
  T("SNOW", "Sněžení", "snow", "#eef6ff"), T("ELECTRIC", "Elektřina", "sparkle", "#7ad8ff"), T("CLOUD", "Obláček", "snow", "#e8ecf2"), T("SCULK", "Sculk duše", "flames", "#1fb0a8"),
  T("ENCHANT", "Kouzla", "sparkle", "#c88bff"), T("GLOW", "Záře", "sparkle", "#5ef0c8"), T("PORTAL", "Portál", "sparkle", "#8a3cff"), T("TOTEM", "Totem", "sparkle", "#ffd23a"), T("HAPPY", "Jiskřičky", "sparkle", "#5cdc6a"),
  A("ORBIT", "Kroužící orby", "#4fd8ff"), A("HALO", "Zářivý prstenec", "#ffd23a"), A("FLAMES", "Ohnivá aura", "#ff7a1f"), A("STORM", "Bouřková aura", "#9ab8ff"),
  A("GALAXY", "Galaktická aura", "#a05cff"), A("LOVE", "Aura lásky", "#ff7ac8"), A("SAKURA", "Sakurová aura", "#ffb0d0"), A("FROST", "Ledová aura", "#bfeaff"),
];
/* stejné ceny jako Cosmetics.price() v pluginu; -1 = jen z adventního kalendáře */
function price(id) {
  if (id === "hat:HELMET") return 0;
  if (id === "hat:SANTA" || id === "hat:PET_PIGEON") return -1;
  if (["hat:CROWN", "hat:HALO"].includes(id) || id.startsWith("hat:PET_")) return 300;
  if (["hat:EARTH", "hat:SATURN", "hat:MARS"].includes(id)) return 200;
  if (["trail:RAINBOW", "trail:TOTEM", "trail:COSMIC"].includes(id)) return 150;
  return id.startsWith("hat:") ? 150 : id.startsWith("balloon:") ? 100 : id.startsWith("cape:") ? 120 : id.startsWith("aura:") ? 150 : 80;
}
ITEMS.forEach((i) => { i.price = price(i.id); i.r = i.price < 0 ? "a" : i.price >= 300 ? "l" : i.price >= 150 ? "e" : i.price >= 100 ? "v" : "b"; });
/* emoty ze serveru (/emote) */
const EMO_SYM = { cheer: "★", hi: "HI", dance: "♪", twerk: "♫", spin: "↻", sleep: "z", swim: "≈", fly: "➶", sit: "⌐", dead: "☠", bow: "✦", gg: "GG", love: "❤", clap: "✦✦", rage: "⚡", rocket: "▲" };
const EMOTES = [["Jásání", "cheer"], ["Mávání", "hi"], ["Tanec", "dance"], ["Twerk", "twerk"], ["Tornádo", "spin"], ["Spaní", "sleep"], ["Plavání vzduchem", "swim"], ["Letadlo", "fly"], ["Sednout", "sit"], ["Mrtvý (naoko)", "dead"], ["Poklona", "bow"], ["GG", "gg"], ["Láska", "love"], ["Tleskání", "clap"], ["Vztek", "rage"], ["Odlet na Mars", "rocket"]];

const RAR = { b: ["Běžné", "#9aa3b5"], v: ["Vzácné", "#3ddc84"], e: ["Epické", "#b06bff"], l: ["Legendární", "#ffc83d"], a: ["Advent", "#5ee7ff"] };
const TABS = [["hlava", "Čepice a pety", "rocket"], ["balonek", "Balónky", "balloon"], ["stopa", "Stopy", "flames"], ["aura", "Aury", "ring"], ["emoty", "Emoty", null]];
const SLOTS = [["hlava", "Hlava"], ["balonek", "Balónek"], ["stopa", "Stopa"], ["aura", "Aura"]];
const W = { tab: "hlava", q: "", sel: "hat:HELMET", owned: new Set(["hat:HELMET"]), color: {}, outfits: [{ name: "Můj vzhled", eq: { hlava: "hat:HELMET" } }], oi: 0, angle: 20, ownEm: new Set(EMOTES.map(([n]) => n)) };
const eq = () => W.outfits[W.oi].eq;
const item = (id) => ITEMS.find((i) => i.id === id);
const colOf = (it) => W.color[it.id] || (it.colors ? it.colors[0] : "#3a8bff");

/* ---------- UI ---------- */
function renderSide() {
  $("wdSide").innerHTML = TABS.map(([id, n, sp]) => `<button class="wt${W.tab === id ? " on" : ""}" data-wd="tab" data-v="${id}">${sp ? sprite(sp, "#ff8a1f") : id === "emoty" ? `<img class="head" src="${pixelHead("Steve")}" alt="">` : '<svg viewBox="0 0 24 24" class="ic"><use href="#i-users"/></svg>'}<span>${n}</span></button>`).join("");
}
function card(it) {
  const own = W.owned.has(it.id), on = eq()[it.slot] === it.id, sel = W.sel === it.id, [rn, rc] = RAR[it.r || "b"];
  return `<button class="wc${sel ? " sel" : ""}${on ? " on" : ""}" style="--rc:${rc}" data-wd="item" data-v="${it.id}" aria-label="${esc(it.n)}">
    <span class="wc-r">${rn}</span>${it.isNew && !own ? '<span class="wc-new">Novinka</span>' : ""}
    <div class="wc-art"><i class="wc-glow"></i>${it.model ? `<img class="mdl" src="${modelThumb(it.model)}" alt="">` : it.banner ? bannerSvg(it.banner) : sprite(it.sp, colOf(it))}</div>
    ${it.colors && it.colors.length > 1 ? `<div class="wc-dots">${it.colors.map((c) => `<i style="background:${c}"></i>`).join("")}</div>` : ""}
    <b>${esc(it.n)}</b>
    <span class="wc-foot">${own ? (on ? '<em class="ok">Nasazeno</em>' : '<em>Vlastníš</em>') : `${it.price < 0 ? '<em class="adv">Adventní kalendář</em>' : `<span class="price">${it.price ? coin(it.price) : "Zdarma"}</span>`}`}</span></button>`;
}
function renderGrid() {
  const q = W.q.toLowerCase();
  let html = "", n = 0;
  if (W.tab === "emoty") {
    const l = EMOTES.filter(([x]) => !q || x.toLowerCase().includes(q)); n = l.length;
    html = l.map(([x, p]) => { const own = W.ownEm.has(x); return `<button class="wc emote" style="--rc:#ff8a1f" data-wd="emote" data-v="${esc(x)}"><span class="wc-r">Emote</span><div class="wc-art"><i class="wc-glow"></i><span class="emo">${EMO_SYM[p] || "✦"}</span></div><b>${esc(x)}</b><span class="wc-foot"><em>Zahrát · /emote ${esc(p)}</em></span></button>`; }).join("");
  } else if (W.tab === "outfity") {
    n = W.outfits.length;
    html = W.outfits.map((o, i) => `<button class="wc outfit${i === W.oi ? " sel" : ""}" style="--rc:#5ee7ff" data-wd="outfit" data-v="${i}"><span class="wc-r">Outfit</span><div class="wc-art mini"><i class="wc-glow"></i>${Object.values(o.eq).slice(0, 4).map((id) => sprite(item(id).sp, colOf(item(id)))).join("") || "<em>prázdný</em>"}</div><b>${esc(o.name)}</b><span class="wc-foot"><em>${Object.keys(o.eq).length} věci</em></span></button>`).join("");
  } else {
    const l = ITEMS.filter((i) => (q ? i.n.toLowerCase().includes(q) : i.slot === W.tab)); n = l.length; html = l.map(card).join("");
  }
  $("wdGrid").innerHTML = html || '<div class="empty">Nic jsme nenašli.</div>';
  $("wdTitle").textContent = n + (n === 1 ? " věc" : n < 5 ? " věci" : " věcí");
  $("wdCoins").innerHTML = S.user ? coin(S.coins) : "Přihlas se";
}
function renderPanel() {
  const it = item(W.sel);
  $("wdOutfit").textContent = S.user || "Nepřihlášen";
  $("wdColors").innerHTML = it && it.colors && it.colors.length > 1 ? it.colors.map((c) => `<button class="sw${colOf(it) === c ? " on" : ""}" style="background:${c}" data-wd="color" data-v="${c}" aria-label="Barva ${c}"></button>`).join("") : "";
  let act = "";
  if (it && W.tab !== "emoty" && W.tab !== "outfity") {
    const own = W.owned.has(it.id), on = eq()[it.slot] === it.id;
    act = own ? `<button class="gbtn ${on ? "ghost" : "green"} sm" data-wd="equip">${on ? "Sundat" : "Nasadit"}</button>`
      : on && it.price >= 0 ? `<span class="try">Zkoušíš</span><button class="gbtn sm" data-wd="buy">Koupit za ${coin(it.price)}</button>`
      : it.price < 0 ? `<span class="gbtn ghost sm">Jen z adventního kalendáře</span>` : `<button class="gbtn sm" data-wd="buy">Koupit za ${coin(it.price)}</button>`;
    act = `<span class="wd-sel" style="--rc:${RAR[it.r || "b"][1]}">${esc(it.n)}<small>${RAR[it.r || "b"][0]}</small></span>` + act;
  }
  $("wdAct").innerHTML = act;
  const e = eq();
  $("wdEq").innerHTML = SLOTS.map(([slot, n]) => { const id = e[slot]; return `<button class="slot${id ? " full" : ""}" data-wd="${id ? "unequip" : "tab"}" data-v="${slot}" title="${id ? "Sundat: " + esc(item(id).n) : n}">${id ? (item(id).model ? `<img class="mdl" src="${modelThumb(item(id).model, 96)}" alt="">` : item(id).banner ? bannerSvg(item(id).banner) : sprite(item(id).sp, colOf(item(id)))) : ""}<span>${n}</span></button>`; }).join("");
  renderRig();
}

/* ---------- 3D postava (canvas renderer, modely ze serveru, animace emotů z BetterModel) ---------- */
let stage = null;
const SHAPE = { flames: "●", hearts: "❤", notes: "♪", sparkle: "✦", snow: "❄" };
const spriteImg = new Map();
function petImg(it) {
  const k = it.id; if (spriteImg.has(k)) return spriteImg.get(k);
  const im = new Image(); im.src = "data:image/svg+xml;utf8," + encodeURIComponent(sprite(it.sp, colOf(it)).replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" '));
  spriteImg.set(k, im); return im;
}
function renderRig() {
  if (!stage) return;
  const e = eq(), hat = e.hlava && item(e.hlava), tr = e.stopa && item(e.stopa);
  stage.set({
    nick: S.user || "Steve",
    hat: hat ? { model: hat.model } : null,
    
    balloon: e.balonek ? item(e.balonek).model : null,
    trail: tr ? { shape: SHAPE[tr.sp] || "✦", color: colOf(tr) } : null,
    aura: e.aura ? { id: e.aura.split(":")[1], color: colOf(item(e.aura)) } : null,
  });
}
function turnTo(target) { const s = W.angle, t0 = performance.now(); const f = (t) => { const k = Math.min(1, (t - t0) / 500), e = 1 - (1 - k) ** 3; setAngle(s + (target - s) * e); if (k < 1) requestAnimationFrame(f); }; requestAnimationFrame(f); }
const setAngle = (a) => { W.angle = a; if (stage) stage.yaw = -a * Math.PI / 180; };

const renderAll = () => { renderSide(); renderGrid(); renderPanel(); };

export function startWardrobe() {
  onAccount.push(() => { renderGrid(); renderPanel(); });
  stage = startStage($("stageCv")); setAngle(W.angle);
  renderAll();
  $("wdSearch").addEventListener("input", (e) => { W.q = e.target.value.trim(); renderGrid(); });
  // otáčení tažením
  const st = $("stage"); let drag = null;
  st.addEventListener("pointerdown", (e) => { if (e.target.closest("button")) return; drag = { x: e.clientX, y: e.clientY, a: W.angle, p: stage ? stage.pitch : 0 }; st.setPointerCapture(e.pointerId); st.classList.add("drag"); });
  st.addEventListener("pointermove", (e) => { if (drag) { setAngle(drag.a + (e.clientX - drag.x) * .8); if (stage) stage.pitch = drag.p + (e.clientY - drag.y) * .01; } });
  const end = () => { drag = null; st.classList.remove("drag"); };
  st.addEventListener("pointerup", end); st.addEventListener("pointercancel", end);
  $("kosmetika").addEventListener("click", (e) => {
    const b = e.target.closest("[data-wd]"); if (!b) return;
    const a = b.dataset.wd, v = b.dataset.v, it = item(W.sel);
    if (a === "tab") { W.tab = v; W.q = ""; $("wdSearch").value = ""; const cur = eq()[v], first = ITEMS.find((i) => i.slot === v); if (cur || first) W.sel = cur || first.id; renderAll(); }
    else if (a === "item") { const x = item(v), s = eq(); if (W.sel === v && s[x.slot] === v) delete s[x.slot]; else s[x.slot] = v; W.sel = v; renderGrid(); renderPanel(); }
    else if (a === "color") { W.color[it.id] = v; renderGrid(); renderPanel(); }
    else if (a === "equip") { const s = eq(); if (s[it.slot] === it.id) delete s[it.slot]; else s[it.slot] = it.id; renderGrid(); renderPanel(); }
    else if (a === "unequip") { delete eq()[v]; renderGrid(); renderPanel(); }
    else if (a === "buy") {
      if (needLogin()) return;
      if (S.coins < it.price) return toast("Nemáš dost Mars Coinů");
      S.coins -= it.price; W.owned.add(it.id); eq()[it.slot] = it.id; all(); renderPanel(); toast(`Koupeno: ${it.n}`);
    }
    else if (a === "emote") {
      const p = EMOTES.find(([n]) => n === v)[1];
      if (!W.ownEm.has(v)) { if (needLogin()) return; if (S.coins < p) return toast("Nemáš dost Mars Coinů"); S.coins -= p; W.ownEm.add(v); all(); toast(`Emote ${v} je tvůj!`); }
      const id = EMOTES.find(([n]) => n === v)[1]; if (stage) stage.play(id);
    }
    else if (a === "outfit") { W.oi = +v; renderAll(); }
    else if (a === "prev" || a === "next") { W.oi = (W.oi + (a === "next" ? 1 : -1) + W.outfits.length) % W.outfits.length; renderAll(); }
    else if (a === "new") { W.outfits.push({ name: `Outfit ${W.outfits.length + 1}`, eq: {} }); W.oi = W.outfits.length - 1; renderAll(); toast("Nový outfit vytvořen"); }
    else if (a === "front" || a === "back") { document.querySelectorAll(".wd-rot button").forEach((x) => x.classList.toggle("on", x === b)); turnTo(a === "front" ? 20 : 200); }
    else if (a === "bg") { const s = $("stage"), ow = s.classList.toggle("ow"); b.textContent = ow ? "🔴" : "🌍"; toast(ow ? "Pozadí: Overworld" : "Pozadí: Mars"); }
    else if (a === "spin") { let t0 = null; const s = W.angle; const f = (t) => { t0 ??= t; const k = Math.min(1, (t - t0) / 1200); setAngle(s + 360 * (1 - (1 - k) ** 3)); if (k < 1) requestAnimationFrame(f); }; requestAnimationFrame(f); }
  });
}
