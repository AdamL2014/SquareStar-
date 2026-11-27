// Šatník / kosmetika: kategorie, mřížka věcí, 3D postava s nasazenými věcmi, mince.
// V náhledu vše jen v prohlížeči. API: GET /api/cosmetics, POST /api/cosmetics/buy {id}, POST /api/cosmetics/equip {slot,id,color}
import { $, esc, pixelHead } from "./util.js";
import { S, needLogin, onAccount, all, coin } from "./account.js";
import { toast } from "./fx.js";

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
const H = (id, n, sp, c) => ({ id: "hat:" + id, n, slot: "hlava", sp, colors: [c] });
const P = (id, n, sp, c) => ({ id: "hat:" + id, n, slot: "hlava", pet: 1, sp, colors: [c] });
const B = (id, n, c) => ({ id: "balloon:" + id, n, slot: "balonek", sp: "balloon", colors: [c] });
const C = (id, n, c) => ({ id: "cape:" + id, n, slot: "plast", sp: "cape", colors: [c] });
const A = (id, n, c) => ({ id: "aura:" + id, n, slot: "aura", sp: "ring", colors: [c] });
const ITEMS = [
  H("HELMET", "Kyslíková helma", "helmet", "#5bc8ff"), H("ROCKET", "Mini raketa", "rocket", "#e23b3b"), H("UFO", "UFO", "ufo", "#9aa3b5"), H("EARTH", "Planeta Země", "planet", "#2f86ff"),
  H("STAR", "Hvězda", "star", "#ffd23a"), H("CROWN", "Koruna", "crown", "#ffd23a"), H("HALO", "Svatozář", "halo", "#ffd23a"), H("ALIEN", "Mimozemská tykadla", "alien", "#5cdc6a"),
  H("SATURN", "Saturn", "saturn", "#d9a65a"), H("PROPELLER", "Čepice s vrtulkou", "propeller", "#2f86ff"), H("SATELLITE", "Satelit", "satellite", "#9aa3b5"), H("MARS", "Planeta Mars", "planet", "#d4501c"),
  H("WITCH", "Čarodějný klobouk", "witch", "#6b3fa0"), H("SANTA", "Čepice Santy", "santa", "#e23b3b"),
  P("PET_SPIDER", "Malý pavouk", "spider", "#2b2d36"), P("PET_CHICKEN", "Kuřátko", "bird", "#f4f4f4"), P("PET_SLIME", "Slime", "slime", "#6fdc5a"), P("PET_FOX", "Liška", "pet", "#e8812a"),
  P("PET_ENDY", "Little Endy", "endy", "#2b2d36"), P("PET_CAT", "Kočka", "pet", "#c9a06a"), P("PET_RACCOON", "Mýval", "pet", "#7a7f8c"), P("PET_CTHULHU", "Mini Cthulhu", "pet", "#3f9e7a"), P("PET_PIGEON", "Holub", "bird", "#8a93a8"),
  B("RED", "Červený balónek", "#e23b3b"), B("BLUE", "Modrý balónek", "#2f86ff"), B("PURPLE", "Fialový balónek", "#a05cff"), B("GOLD", "Zlatý balónek", "#ffc83d"), B("GREEN", "Zelený balónek", "#2fd24a"), B("WHITE", "Bílý balónek", "#eef0f4"),
  C("RED", "Rudý plášť", "#c8282e"), C("BLUE", "Modrý plášť", "#2f5fd8"), C("BLACK", "Černý plášť", "#24242c"), C("MARS", "Marsovský plášť", "#d4501c"), C("GALAXY", "Galaxie", "#5b2a9e"),
  C("ROYAL", "Královský plášť", "#7b32d6"), C("CREEPER", "Creeper plášť", "#4fc04a"), C("SKULL", "Pirátský plášť", "#1c1c22"), C("FLOWER", "Květinový plášť", "#ff7ac8"), C("ICE", "Ledový plášť", "#8fd8ff"),
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
const EMOTES = [["Jásání", "cheer"], ["Mávání", "hi"], ["Tanec", "dance"], ["Twerk", "twerk"], ["Tornádo", "spin"], ["Spaní", "sleep"], ["Plavání vzduchem", "swim"], ["Letadlo", "fly"], ["Sednout", "sit"], ["Mrtvý (naoko)", "dead"], ["Poklona", "bow"], ["GG", "gg"], ["Láska", "love"], ["Tleskání", "clap"], ["Vztek", "rage"], ["Odlet na Mars", "rocket"]];

const RAR = { b: ["Běžné", "#9aa3b5"], v: ["Vzácné", "#3ddc84"], e: ["Epické", "#b06bff"], l: ["Legendární", "#ffc83d"], a: ["Advent", "#5ee7ff"] };
const TABS = [["hlava", "Čepice a pety", "rocket"], ["plast", "Pláště", "cape"], ["balonek", "Balónky", "balloon"], ["stopa", "Stopy", "flames"], ["aura", "Aury", "ring"], ["emoty", "Emoty", null]];
const SLOTS = [["hlava", "Hlava"], ["plast", "Plášť"], ["balonek", "Balónek"], ["stopa", "Stopa"], ["aura", "Aura"]];
const W = { tab: "hlava", q: "", sel: "hat:HELMET", owned: new Set(["hat:HELMET"]), color: {}, outfits: [{ name: "Můj vzhled", eq: { hlava: "hat:HELMET" } }], oi: 0, angle: -25, ownEm: new Set(EMOTES.map(([n]) => n)) };
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
    <div class="wc-art"><i class="wc-glow"></i>${sprite(it.sp, colOf(it))}</div>
    ${it.colors && it.colors.length > 1 ? `<div class="wc-dots">${it.colors.map((c) => `<i style="background:${c}"></i>`).join("")}</div>` : ""}
    <b>${esc(it.n)}</b>
    <span class="wc-foot">${own ? (on ? '<em class="ok">Nasazeno</em>' : '<em>Vlastníš</em>') : `${it.price < 0 ? '<em class="adv">Adventní kalendář</em>' : `<span class="price">${it.price ? coin(it.price) : "Zdarma"}</span>`}`}</span></button>`;
}
function renderGrid() {
  const q = W.q.toLowerCase();
  let html = "", n = 0;
  if (W.tab === "emoty") {
    const l = EMOTES.filter(([x]) => !q || x.toLowerCase().includes(q)); n = l.length;
    html = l.map(([x, p]) => { const own = W.ownEm.has(x); return `<button class="wc emote" style="--rc:#ff8a1f" data-wd="emote" data-v="${esc(x)}"><span class="wc-r">Emote</span><div class="wc-art"><i class="wc-glow"></i><img class="head big" src="${pixelHead(S.user || "Steve")}" alt=""></div><b>${esc(x)}</b><span class="wc-foot"><em>Zahrát · /emote ${esc(p)}</em></span></button>`; }).join("");
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
      : it.price < 0 ? `<span class="gbtn ghost sm">Jen z adventního kalendáře</span>` : `<button class="gbtn sm" data-wd="buy">Koupit za ${coin(it.price)}</button>`;
    act = `<span class="wd-sel" style="--rc:${RAR[it.r || "b"][1]}">${esc(it.n)}<small>${RAR[it.r || "b"][0]}</small></span>` + act;
  }
  $("wdAct").innerHTML = act;
  const e = eq();
  $("wdEq").innerHTML = SLOTS.map(([slot, n]) => { const id = e[slot]; return `<button class="slot${id ? " full" : ""}" data-wd="${id ? "unequip" : "tab"}" data-v="${slot}" title="${id ? "Sundat: " + esc(item(id).n) : n}">${id ? sprite(item(id).sp, colOf(item(id))) : ""}<span>${n}</span></button>`; }).join("");
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
  // kosmetika (sloty jako na serveru: hlava, plášť, balónek, stopa, aura)
  const hat = e.hlava && item(e.hlava);
  if (hat && hat.pet) h += `<div class="ax pet" style="transform:translate3d(${11 * U}px,${-12 * U}px,${2 * U}px)">${sprite(hat.sp, colOf(hat))}</div>`;
  else if (hat && hat.sp === "halo") h += `<div class="ax halo" style="transform:translate3d(0,${-16 * U}px,0) rotateX(90deg)"></div>`;
  else if (hat && hat.sp === "helmet") { const c = "#e8eef8"; h += box(9.6 * U, 9.6 * U, 9.6 * U, 0, -10 * U, 0, ["background:#9fe8ff55;box-shadow:inset 0 0 0 4px " + c, sh(c, .9), sh(c, .85), sh(c, .85), sh(c, 1.05), sh(c, .6)], "glassbox"); }
  else if (hat) { const s = sprite(hat.sp, colOf(hat)); h += `<div class="ax hatsp" style="transform:translate3d(0,${-17.5 * U}px,0)">${s}</div><div class="ax hatsp" style="transform:translate3d(0,${-17.5 * U}px,0) rotateY(90deg)">${s}</div>`; }
  if (e.plast) { const c = colOf(item(e.plast)); h += `<div class="ax cape" style="transform:translate3d(0,${.8 * U}px,${-2.6 * U}px) rotateX(8deg);background:linear-gradient(${shade(c, 1.15)},${shade(c, .7)})"></div>`; }
  if (e.balonek) { const bl = item(e.balonek); h += `<div class="ax balloon" style="transform:translate3d(${-10 * U}px,${-22 * U}px,0)">${sprite("balloon", colOf(bl))}<i></i></div>`; }
  if (e.aura) { const c = colOf(item(e.aura)); h += `<div class="ax aura" style="--c:${c};transform:translate3d(0,${4 * U}px,0) rotateX(90deg)">${"<i></i>".repeat(8)}</div>`; }
  $("rig").innerHTML = `<div class="rot" id="rot" style="transform:rotateX(-8deg) rotateY(${W.angle}deg)">${h}</div>`;
  const tr = e.stopa && item(e.stopa), pt = tr ? tr.sp : "";
  $("pfx").className = "pfx " + pt; $("pfx").style.setProperty("--c", tr ? colOf(tr) : "#fff");
  $("pfx").innerHTML = pt ? Array.from({ length: 14 }, (_, i) => `<i style="--x:${(i * 37) % 100}%;--d:${(i % 7) * .45}s;--s:${.7 + (i % 3) * .25}"></i>`).join("") : "";
}
const setAngle = (a) => { W.angle = a; const r = $("rot"); if (r) r.style.transform = `rotateX(-8deg) rotateY(${a}deg)`; };

const renderAll = () => { renderSide(); renderGrid(); renderPanel(); };

export function startWardrobe() {
  onAccount.push(() => { renderGrid(); renderPanel(); });
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
    if (a === "tab") { W.tab = v; W.q = ""; $("wdSearch").value = ""; const cur = eq()[v], first = ITEMS.find((i) => i.slot === v); if (cur || first) W.sel = cur || first.id; renderAll(); }
    else if (a === "item") { W.sel = v; const x = item(v); if (W.owned.has(v)) eq()[x.slot] = v; renderGrid(); renderPanel(); }
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
      const r = $("rig"); r.classList.remove("dance"); void r.offsetWidth; r.classList.add("dance");
    }
    else if (a === "outfit") { W.oi = +v; renderAll(); }
    else if (a === "prev" || a === "next") { W.oi = (W.oi + (a === "next" ? 1 : -1) + W.outfits.length) % W.outfits.length; renderAll(); }
    else if (a === "new") { W.outfits.push({ name: `Outfit ${W.outfits.length + 1}`, eq: {} }); W.oi = W.outfits.length - 1; renderAll(); toast("Nový outfit vytvořen"); }
    else if (a === "front" || a === "back") { document.querySelectorAll(".wd-rot button").forEach((x) => x.classList.toggle("on", x === b)); setAngle(a === "front" ? -25 : 155); }
    else if (a === "spin") { let t0 = null; const s = W.angle; const f = (t) => { t0 ??= t; const k = Math.min(1, (t - t0) / 1200); setAngle(s + 360 * (1 - (1 - k) ** 3)); if (k < 1) requestAnimationFrame(f); }; requestAnimationFrame(f); }
  });
}
