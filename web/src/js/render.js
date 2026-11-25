import { $, esc, time, safeColor } from "./util.js";

export const state = { data: null, tab: "xpTotal" };

export function face(p, size) {
  if (!p.skin) return `<div class="face ph" style="width:${size}px;height:${size}px;font-size:${Math.round(size / 2.4)}px">${esc((p.name || "?")[0].toUpperCase())}</div>`;
  const u = esc(p.skin), bs = size * 8;
  return `<div class="face" style="width:${size}px;height:${size}px;background-image:url('${u}');background-size:${bs}px ${bs}px;background-position:-${size}px -${size}px"><i style="background-image:url('${u}');background-size:${bs}px ${bs}px;background-position:-${size * 5}px -${size}px"></i></div>`;
}
export function rank(p) { const c = safeColor(p.rankColor); return `<span class="rk" style="color:${c};background:${c}1f">${esc(p.rank)}</span>`; }

const cardHtml = (p) => `${face(p, 54)}<div style="flex:1;min-width:0"><div class="nm"><em>${esc(p.name)}</em>${rank(p)}</div>
  <div class="sm"><span>Level ${p.level}</span><span>${time(p.minutes)}</span><span>${esc(p.playing)}</span></div>
  <div class="xpbar"><div style="width:${Math.min(100, (p.xp / (p.xpNeed || 1)) * 100)}%"></div></div></div>`;

/* Živý seznam hráčů: porovnává se podle jména, takže přibyvší/odcházející hráči se animují a ostatní karty se nepřekreslují. */
export function renderPlayers() {
  const box = $("players"), d = state.data;
  if (!d) { box.innerHTML = '<div class="empty">Server je teď offline nebo web není napojený na plugin.</div>'; return; }
  if (!d.players.length) { box.innerHTML = '<div class="empty">Zrovna nikdo nehraje. Buď první, kdo přistane!</div>'; return; }
  box.querySelector(".empty")?.remove();
  const have = new Map([...box.querySelectorAll(".pl[data-name]")].map((el) => [el.dataset.name, el]));
  const want = new Set(d.players.map((p) => p.name));
  for (const [n, el] of have) if (!want.has(n) && !el.classList.contains("leave")) { el.classList.add("leave"); setTimeout(() => el.remove(), 350); }
  d.players.forEach((p, i) => {
    let el = have.get(p.name);
    if (el && el.classList.contains("leave")) el = null;
    const html = cardHtml(p);
    if (!el) { el = document.createElement("div"); el.className = "pl join"; el.dataset.name = p.name; el.innerHTML = html; el._h = html; box.appendChild(el); }
    else if (el._h !== html) { el.innerHTML = html; el._h = html; }
    el.dataset.i = i;
    el.style.order = i;
  });
}

const val = (p, k) => (k === "xpTotal" ? "Lv " + p.level : k === "minutes" ? time(p.minutes) : p[k]);
export function renderTop() {
  const l = state.data?.top?.[state.tab];
  if (!l?.length) { $("podium").innerHTML = ""; $("lb").innerHTML = '<div class="empty">Zatím tu nikdo není.</div>'; return; }
  $("podium").innerHTML = [1, 0, 2].filter((i) => l[i]).map((i) => `<div class="pod p${i + 1}" data-top="${i}"><div class="medal">${i + 1}</div>${face(l[i], i === 0 ? 72 : 56)}<div class="n">${esc(l[i].name)}</div><div class="v">${val(l[i], state.tab)}</div></div>`).join("");
  const rest = l.slice(3);
  $("lb").innerHTML = rest.length ? rest.map((p, j) => `<div class="row" data-top="${j + 3}"><div class="pos">${j + 4}</div>${face(p, 34)}<div class="nm"><em>${esc(p.name)}</em>${rank(p)}</div><div class="v">${val(p, state.tab)}</div></div>`).join("") : '<div class="empty">Další místa jsou volná – můžou být tvoje!</div>';
}

export function showModal(p) {
  if (!p) return;
  $("mbox").innerHTML = `<button class="x" data-action="close" aria-label="Zavřít"><svg class="ic" style="width:18px;height:18px"><use href="#i-x"/></svg></button>${face(p, 96)}
   <h3>${esc(p.name)}</h3><div style="margin-top:8px">${rank(p)}</div>
   <div class="mgrid"><div><b>${p.level}</b><span>level</span></div><div><b>${time(p.minutes)}</b><span>odehraný čas</span></div>
   <div><b>${p.wins ?? 0}</b><span>výhry</span></div><div><b>${p.kills ?? 0}</b><span>zabití</span></div>
   <div><b>${p.gamesPlayed ?? 0}</b><span>odehrané hry</span></div><div><b style="font-size:14px">${p.playing ? esc(p.playing) : "offline"}</b><span>právě</span></div></div>`;
  $("modal").classList.add("on");
}
export const closeModal = () => $("modal").classList.remove("on");

const prev = {};
function setStat(id, v) {
  const el = $(id), s = String(v ?? "–");
  if (el.textContent !== s) { el.textContent = s; if (prev[id] !== undefined) { el.classList.remove("bump"); void el.offsetWidth; el.classList.add("bump"); } }
  prev[id] = s;
}
export function renderStats() {
  const d = state.data;
  setStat("sOnline", d?.online); setStat("sGames", d?.games); setStat("sReg", d?.registered); setStat("sTps", d?.tps);
}
export function renderPill(mode) {
  const d = state.data, dot = $("dot"), t = $("livetxt"), u = $("upd");
  dot.classList.toggle("on", mode === "live");
  if (mode === "offline" || !d) t.textContent = "Server je offline";
  else t.textContent = `Server online · ${d.online}/${d.max} hráčů`;
  u.classList.toggle("stale", mode !== "live");
  u.textContent = mode === "live" ? "· živě" : mode === "stale" ? "· spojení se obnovuje…" : "";
  document.title = d && mode !== "offline" ? `(${d.online}) SquareStar – Minecraft minihry na Marsu` : "SquareStar – Minecraft minihry na Marsu";
}
