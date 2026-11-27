import { $ } from "./util.js";
import { loadConfig } from "./config.js";
import { startLive } from "./live.js";
import { startDemo } from "./demo.js";
import { startAccount } from "./account.js";
import { startWardrobe } from "./wardrobe.js";
import { startLang } from "./i18n.js";
import { state, renderPlayers, renderTop, renderStats, renderPill, showModal, closeModal } from "./render.js";
import { renderCategories, startStars, startReveal, startTilt, renderTicker, toast } from "./fx.js";

const cfg = await loadConfig();
$("y").textContent = new Date().getFullYear();
if (cfg.serverIp) $("ip").textContent = cfg.serverIp;
if (cfg.discord) { const a = $("discord"); a.href = cfg.discord; a.hidden = false; }
renderCategories(); startStars(); startReveal(); startTilt();

let mode = "offline";
const live = {
  onData(d) { state.data = d; renderTicker(d); renderStats(); renderPlayers(); renderTop(); renderPill(mode); },
  onState(m) {
    if (m === "offline") state.data = null;
    const changed = m !== mode; mode = m;
    if (changed || !state.data) { renderPill(m); if (!state.data) { renderTicker(null); renderStats(); renderPlayers(); renderTop(); } }
  },
};
if (cfg.demo) { mode = "live"; startDemo((d) => live.onData(d)); } else startLive(cfg, live);
startAccount(cfg); startWardrobe(); startLang();

document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-action]")?.dataset.action;
  if (a === "menu") return $("nav").classList.toggle("open");
  if (e.target.closest("#nlinks a")) $("nav").classList.remove("open");
  if (a === "copy-ip") { const done = () => toast(); try { navigator.clipboard.writeText($("ip").textContent).then(done, done); } catch { done(); } return; }
  if (a === "close" || e.target === $("modal")) return closeModal();
  const pl = e.target.closest(".pl[data-name]");
  if (pl) return showModal(state.data?.players.find((p) => p.name === pl.dataset.name));
  const top = e.target.closest("[data-top]");
  if (top) return showModal(state.data?.top?.[state.tab]?.[+top.dataset.top]);
});
$("tabs").addEventListener("click", (e) => {
  const b = e.target.closest("button"); if (!b) return;
  state.tab = b.dataset.k;
  document.querySelectorAll("#tabs button").forEach((x) => x.classList.toggle("act", x === b));
  renderTop();
});
document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeModal(); });
