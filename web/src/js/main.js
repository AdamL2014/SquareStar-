import { $ } from "./util.js";
import { loadConfig } from "./config.js";
import { startLive } from "./live.js";
import { state, renderPlayers, renderTop, renderStats, renderPill, showModal, closeModal } from "./render.js";
import { renderCategories, startStars, startReveal, toast } from "./fx.js";

const cfg = await loadConfig();
$("y").textContent = new Date().getFullYear();
if (cfg.serverIp) $("ip").textContent = cfg.serverIp;
renderCategories(); startStars(); startReveal();

let mode = "offline";
startLive(cfg, {
  onData(d) { state.data = d; renderStats(); renderPlayers(); renderTop(); renderPill(mode); },
  onState(m) {
    if (m === "offline") state.data = null;
    const changed = m !== mode; mode = m;
    if (changed || !state.data) { renderPill(m); if (!state.data) { renderStats(); renderPlayers(); renderTop(); } }
  },
});

document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-action]")?.dataset.action;
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
