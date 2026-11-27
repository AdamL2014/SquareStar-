import { $, esc, time, safeColor, pixelHead } from "./util.js";
import { RANK_IMG } from "./ranks.js";
import { GI } from "./fx.js";

export const state = { data: null, tab: "xpTotal" };

export function face(p, size) {
  if (!p.skin) return `<img class="face head" src="${pixelHead(p.name || "?")}" width="${size}" height="${size}" alt="">`;
  const u = esc(p.skin), bs = size * 8;
  return `<div class="face" style="width:${size}px;height:${size}px;background-image:url('${u}');background-size:${bs}px ${bs}px;background-position:-${size}px -${size}px"><i style="background-image:url('${u}');background-size:${bs}px ${bs}px;background-position:-${size * 5}px -${size}px"></i></div>`;
}
export function rank(p) {
  const k = String(p.rank || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  if (RANK_IMG[k]) return `<img class="rkimg" src="${RANK_IMG[k]}" alt="${esc(p.rank)}">`;
  const c = safeColor(p.rankColor); return `<span class="rk" style="color:${c};background:${c}1f">${esc(p.rank)}</span>`;
}

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
let lastTop = "", lastTab = "";
const TAB_UNIT = { xpTotal: "LEVEL", wins: "VÝHER", kills: "ZABITÍ", minutes: "ODEHRÁNO", gamesPlayed: "HER" };
const title = (lv) => lv >= 30 ? ["Legenda Marsu", "t-leg"] : lv >= 20 ? ["Bojový mistr", "t-ace"] : lv >= 10 ? ["Bojový specialista", "t-spec"] : ["Nováček", "t-new"];
const MEDAL = ['<path d="m2 8 5 4 5-8 5 8 5-4-2 12H4z"/>', '<circle cx="12" cy="14" r="6"/><path d="M8 3h8l-2 5h-4z"/>', '<circle cx="12" cy="14" r="6"/><path d="M8 3h8l-2 5h-4z"/>'];
const tierHtml = (t) => `<div class="tier tr${t.t[2]}" title="${esc(t.n)} · ${t.t}"><i><svg viewBox="0 0 24 24" aria-hidden="true">${GI[t.g] || ""}</svg></i><span>${t.t}</span></div>`;
export function renderTop() {
  const l = state.data?.top?.[state.tab];
  if (!l?.length) { $("lb").innerHTML = '<div class="empty">Zatím tu nikdo není.</div>'; lastTop = ""; return; }
  $("lbCount").textContent = `${state.data.registered ?? l.length} hráčů v žebříčku`;
  const rows = l.slice(0, 10).map((p, i) => {
    const [tt, tc] = title(p.level), v = val(p, state.tab);
    const pos = i < 3 ? `<div class="pos m${i + 1}"><svg viewBox="0 0 24 24" aria-hidden="true">${MEDAL[i]}</svg>${i + 1}</div>` : `<div class="pos">${i + 1}</div>`;
    return `<div class="lr${i < 3 ? " top" + (i + 1) : ""}" data-top="${i}" style="--d:${i * 55}ms">${pos}${face(p, 44)}
      <div class="who"><b>${esc(p.name)}</b><span class="ttl ${tc}"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 2 3.1 6.3 6.9 1-5 4.8 1.2 6.9-6.2-3.3-6.2 3.3L7 14.1 2 9.3l6.9-1z"/></svg>${tt}</span></div>
      <div class="pts"><b>${String(v).replace(/^Lv /, "")}</b><span>${TAB_UNIT[state.tab]}</span></div>
      <div class="lrk">${rank(p)}</div>
      <div class="tiers">${(p.tiers || []).map(tierHtml).join("")}</div></div>`;
  }).join("");
  const key = state.tab + rows;
  if (key === lastTop) return;
  const anim = state.tab !== lastTab; lastTop = key; lastTab = state.tab;
  const el = $("lb"); el.innerHTML = rows; el.classList.remove("anim"); if (anim) { void el.offsetWidth; el.classList.add("anim"); }
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
  if (el.textContent !== s) {
    const from = parseFloat(prev[id]), to = parseFloat(s);
    if (!isNaN(from) && !isNaN(to) && /^\d+$/.test(s) && !matchMedia("(prefers-reduced-motion:reduce)").matches) {
      const t0 = performance.now(), d = 600;
      const step = (t) => { const k = Math.min(1, (t - t0) / d), e = 1 - (1 - k) ** 3; el.textContent = Math.round(from + (to - from) * e); if (k < 1) requestAnimationFrame(step); };
      requestAnimationFrame(step);
    } else el.textContent = s;
    if (prev[id] !== undefined) { el.classList.remove("bump"); void el.offsetWidth; el.classList.add("bump"); }
  }
  prev[id] = s;
}
export function renderStats() {
  const d = state.data;
  setStat("sOnline", d?.online); setStat("tOnline", d?.online); setStat("sGames", d?.games); setStat("sReg", d?.registered); setStat("sTps", d?.tps);
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
