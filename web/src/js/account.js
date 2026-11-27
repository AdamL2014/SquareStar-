// Účet (přihlášení kódem ze hry /web), obchod za herní mince a přátelé.
// V náhledu (cfg.demo) vše běží jen v prohlížeči. Napojení na server: viz komentáře "API:".
import { $, esc, pixelHead } from "./util.js";
import { startTilt, toast } from "./fx.js";
import { RANK_IMG } from "./ranks.js";

const SHOP = [
  { id: "vip", cat: "Rank", name: "VIP", desc: "Barevné jméno, /fly v lobby a 2 emoty navíc", price: 1500, c: "t-green", icon: "crown", rk: "vip" },
  { id: "mvp", cat: "Rank", name: "MVP", desc: "Vše z VIP + vlastní částice a 2× denní odměna", price: 4000, c: "t-pink", icon: "star", rk: "mvp" },
  { id: "key", cat: "Klíč", name: "Legendární klíč", desc: "Otevře legendární bednu v lobby", price: 600, c: "t-purple", icon: "gift" },
  { id: "keys5", cat: "Klíč", name: "5× Vzácný klíč", desc: "Balíček vzácných klíčů", price: 900, c: "t-red", icon: "pack" },
  { id: "emotes", cat: "Kosmetika", name: "Balíček emotů", desc: "6 animovaných emotů do lobby", price: 450, c: "t-green", icon: "smile" },
  { id: "trail", cat: "Kosmetika", name: "Raketová stopa", desc: "Ohnivá stopa za tebou při skoku", price: 350, c: "t-orange", icon: "rocket" },
];

export const S = { cat: "", user: null, coins: 0, cart: new Set(), owned: new Set(),
  friends: [{ name: "Kuba123", on: true, play: "BedWars" }, { name: "Terka_CZ", on: true, play: "Lobby" }, { name: "MarsKing", on: false, play: "" }],
  requests: ["PixelPavel"], pendingNick: "" };

const NICK = /^[A-Za-z0-9_]{3,16}$/;
const open = () => { $("lgStep1").hidden = false; $("lgStep2").hidden = true; $("login").classList.add("on"); setTimeout(() => $("lgN").focus(), 50); };
const close = () => $("login").classList.remove("on");
export const needLogin = () => { if (S.user) return false; open(); return true; };

function renderAcc() {
  const b = $("accBtn"), c = $("coins");
  if (S.user) { b.innerHTML = `<img class="av head" src="${pixelHead(S.user)}" alt="">${esc(S.user)}`; c.hidden = false; c.textContent = S.coins + " mincí"; }
  else { b.textContent = "Přihlásit"; c.hidden = true; }
  $("balance").textContent = S.user ? S.coins + " mincí" : "Přihlas se";
  $("frLocked").hidden = !!S.user; $("frBox").hidden = !S.user;
}

function renderShop() {
  $("shop").innerHTML = SHOP.filter((it) => !S.cat || it.cat === S.cat).map((it) => {
    const own = S.owned.has(it.id), inCart = S.cart.has(it.id);
    const lbl = own ? "Vlastníš" : inCart ? "V košíku · zrušit" : `${it.price} mincí · Do košíku`;
    return `<div class="tile item ${it.c}"><i class="shine"></i><svg class="art" viewBox="0 0 24 24"><use href="#i-${it.icon}"/></svg>
      <span class="tag">${it.cat}</span><div class="tx">${it.rk ? `<img class="rkbig" src="${RANK_IMG[it.rk]}" alt="Rank ${it.name}">` : ""}<h2>${it.name}</h2><p>${it.desc}</p>
      <button class="buy${own || inCart ? " on" : ""}" data-action="cart" data-id="${it.id}" ${own ? "disabled" : ""}>${lbl}</button></div></div>`;
  }).join("");
  const sel = SHOP.filter((i) => S.cart.has(i.id)), total = sel.reduce((a, i) => a + i.price, 0);
  $("cartInfo").textContent = sel.length ? `Košík: ${sel.map((i) => i.name).join(", ")} · ${total} mincí` : "Košík je prázdný";
  $("checkoutBtn").textContent = `Koupit (${total} mincí)`;
  startTilt("#shop .tile");
}

function renderFriends() {
  $("frReq").innerHTML = S.requests.map((n) => `<div class="req"><span>${esc(n)} ti poslal žádost o přátelství</span>
    <button class="gbtn green sm" data-action="fr-accept" data-n="${esc(n)}">Přijmout</button><button class="gbtn ghost sm" data-action="fr-deny" data-n="${esc(n)}">Odmítnout</button></div>`).join("");
  $("frList").innerHTML = S.friends.length ? S.friends.map((f) => `<div class="fr"><span class="fav${f.on ? " on" : ""}"><img class="head" src="${pixelHead(f.name.replace(" (čeká)", ""))}" alt=""></span>
    <div class="fi2"><b>${esc(f.name)}</b><span class="${f.on ? "on" : ""}">${f.wait ? "Žádost odeslána…" : f.on ? "Online · " + esc(f.play) : "Offline"}</span></div>
    <button class="rm" data-action="fr-rm" data-n="${esc(f.name)}" aria-label="Odebrat ${esc(f.name)}">×</button></div>`).join("")
    : '<div class="empty">Zatím nemáš žádné přátele. Přidej prvního!</div>';
}

export const onAccount = [];
export const all = () => { renderAcc(); renderShop(); renderFriends(); onAccount.forEach((f) => f()); };

/* samostatné stránky: #obchod a #pratele */
const PAGES = ["obchod", "pratele", "kosmetika"];
function route() {
  const p = location.hash.slice(1), page = PAGES.includes(p) ? p : "home";
  if (document.body.dataset.page === page && page === "home") return;
  const was = document.body.dataset.page; document.body.dataset.page = page;
  document.querySelectorAll("#nlinks a").forEach((a) => a.classList.toggle("act", a.getAttribute("href") === "#" + page));
  if (page !== "home") { document.querySelectorAll(".page").forEach((s) => s.classList.toggle("show", s.id === page)); scrollTo({ top: 0, behavior: "instant" }); }
  else if (was && was !== "home") { const t = p && document.getElementById(p); if (t) requestAnimationFrame(() => t.scrollIntoView({ behavior: "instant" })); else scrollTo({ top: 0, behavior: "instant" }); }
}

export function startAccount(cfg) {
  all(); route(); addEventListener("hashchange", route);
  $("shopTabs").addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; S.cat = b.dataset.cat; $("shopTabs").querySelectorAll("button").forEach((x) => x.classList.toggle("act", x === b)); renderShop(); });
  $("lgNick").addEventListener("submit", (e) => {
    e.preventDefault();
    const n = $("lgN").value.trim();
    if (!NICK.test(n)) { $("lgN").classList.add("bad"); return; }
    $("lgN").classList.remove("bad"); S.pendingNick = n;
    // API: POST /api/auth/start {nick} -> server čeká, až hráč napíše /web, a ve hře mu ukáže kód
    $("lgStep1").hidden = true; $("lgStep2").hidden = false; $("lgHint").textContent = cfg.demo ? "Náhled: projde jakýchkoli 6 číslic." : "";
    setTimeout(() => $("lgC").focus(), 50);
  });
  $("lgCode").addEventListener("submit", (e) => {
    e.preventDefault();
    const c = $("lgC").value.trim();
    if (!/^\d{6}$/.test(c)) { $("lgC").classList.add("bad"); return; }
    // API: POST /api/auth/verify {nick, code} -> přihlašovací token (cookie)
    $("lgC").classList.remove("bad"); $("lgC").value = "";
    S.user = S.pendingNick; S.coins = 2500; close(); all(); toast("Vítej, " + S.user + "!");
  });
  $("addFr").addEventListener("submit", (e) => {
    e.preventDefault();
    const n = $("frName").value.trim();
    if (!NICK.test(n) || n === S.user || S.friends.some((f) => f.name.toLowerCase() === n.toLowerCase())) { $("frName").classList.add("bad"); return; }
    // API: POST /api/friends/request {to} -> hráč dostane žádost ve hře (titulek)
    $("frName").classList.remove("bad"); $("frName").value = "";
    S.friends.push({ name: n, on: false, play: "", wait: true }); renderFriends(); toast("Žádost odeslána hráči " + n);
  });
  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-action]"); if (!el) return;
    const a = el.dataset.action, n = el.dataset.n;
    if (a === "account") { if (S.user) { S.user = null; S.cart.clear(); all(); toast("Odhlášeno"); } else open(); }
    else if (a === "close-login") close();
    else if (a === "cart") { if (needLogin()) return; const id = el.dataset.id; S.cart.has(id) ? S.cart.delete(id) : S.cart.add(id); $("buyMsg").textContent = ""; renderShop(); }
    else if (a === "checkout") {
      if (needLogin()) return;
      const sel = SHOP.filter((i) => S.cart.has(i.id)), total = sel.reduce((x, i) => x + i.price, 0);
      if (!sel.length) return void ($("buyMsg").textContent = "Košík je prázdný.");
      if (total > S.coins) return void ($("buyMsg").textContent = "Nemáš dost mincí.");
      // API: POST /api/shop/buy {items} -> server odečte mince a dá věci hráči ve hře
      S.coins -= total; sel.forEach((i) => S.owned.add(i.id)); S.cart.clear(); $("buyMsg").textContent = "Koupeno! Věci máš ve hře."; all();
    }
    else if (a === "fr-accept") { S.requests = S.requests.filter((x) => x !== n); S.friends.push({ name: n, on: true, play: "Lobby" }); renderFriends(); }
    else if (a === "fr-deny") { S.requests = S.requests.filter((x) => x !== n); renderFriends(); }
    else if (a === "fr-rm") { S.friends = S.friends.filter((x) => x.name !== n); renderFriends(); }
  });
  $("login").addEventListener("click", (e) => { if (e.target === $("login")) close(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
}
