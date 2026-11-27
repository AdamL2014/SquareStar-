import { $, pixelHead } from "./util.js";

// Ikony miniher (24×24, tahy)
const GI = {
  duel: '<path d="M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4"/><path d="M9.5 17.5 21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4"/>',
  ffa: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/><path d="M12 1v5M12 18v5M1 12h5M18 12h5"/>',
  team: '<circle cx="7" cy="8" r="3"/><circle cx="17" cy="8" r="3"/><path d="M1 20c0-3 3-5 6-5s6 2 6 5M11 20c0-3 3-5 6-5s6 2 6 5"/>',
  army: '<path d="M12 2 4 5v6c0 5 3.5 9 8 11 4.5-2 8-6 8-11V5z"/><path d="m8 12 3 3 5-6"/>',
  sumo: '<circle cx="12" cy="12" r="10"/><circle cx="9" cy="12" r="2.5"/><circle cx="15" cy="12" r="2.5"/><path d="M5 17h14"/>',
  bed: '<path d="M2 18V7M2 13h20v5M22 13a4 4 0 0 0-4-4h-7v4"/><circle cx="6.5" cy="10" r="1.8"/>',
  egg: '<path d="M12 3c4 0 7 6.5 7 11a7 7 0 0 1-14 0c0-4.5 3-11 7-11z"/><path d="m8 13 2 2 2-2 2 2 2-2"/>',
  bridge: '<path d="M2 17h20M4 17V9M20 17V9M4 9q8 6 16 0M8 17v-4M12 17v-3M16 17v-4"/>',
  anni: '<path d="M12 2v6M9 5h6"/><path d="M4 22l3-9h10l3 9z"/><path d="M9 13V9h6v4"/>',
  swords: '<path d="M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2"/>',
  sky: '<path d="M3 15h7l-2 5h8l-2-5h7"/><path d="M7 15V9l5-5 5 5v6"/>',
  apple: '<path d="M12 7c-2-2-7-1.5-7 4 0 5 3 10 5 10 1 0 1.5-.6 2-.6s1 .6 2 .6c2 0 5-5 5-10 0-5.5-5-6-7-4z"/><path d="M12 7c0-2 1-4 3-4"/>',
  zombie: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 10h2M14 10h2M8 15h8M10 15v2M14 15v2"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  knife: '<path d="M3 21 14 10M14 10l6-6c1 3 0 6-3 8z"/><path d="m5 17 2 2"/>',
  build: '<path d="m3 21 4-1 11-11-3-3L4 17z"/><path d="m14 6 3 3M17 3l4 4"/>',
  parkour: '<path d="M3 20h4v-4h4v-4h4V8h4V4h2"/><circle cx="18" cy="3" r="1"/>',
  up: '<path d="M12 21V5M5 12l7-7 7 7"/><path d="M5 21h14"/>',
  drop: '<path d="M12 3v14M6 11l6 6 6-6"/><path d="M4 21h16"/>',
  rover: '<rect x="3" y="8" width="18" height="7" rx="2"/><circle cx="7" cy="18" r="2.5"/><circle cx="17" cy="18" r="2.5"/><path d="M12 8V4h4"/>',
  clock: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l3 2M10 2h4"/>',
  beat: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
  shovel: '<path d="M14 10 21 3M17 7l-3-3M5 13l6 6-3 3c-3 0-6-3-6-6z"/>',
  tnt: '<rect x="4" y="7" width="16" height="13" rx="1"/><path d="M4 12h16M8 7V4M8 4c2 0 2-2 4-2"/><path d="M8 16h2M12 16v-2M15 16h2"/>',
  lucky: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M10 9.5a2 2 0 1 1 3 1.7c-.7.4-1 1-1 1.8M12 16h.01"/>',
};
export const CATS = [
  { t: "PvP", c: ["#ff4b3a", "#a3160c"], g: [["1v1", "duel"], ["Všichni proti všem", "ffa"], ["5v5", "team"], ["10v10", "army"], ["Sumo", "sumo"]] },
  { t: "Bojové", c: ["#ff9a2a", "#b4500e"], g: [["BedWars", "bed"], ["EggWars", "egg"], ["The Bridge", "bridge"], ["AnniRun", "anni"], ["Duels", "swords"], ["SkyWars", "sky"], ["UHC Run", "apple"], ["Zombies", "zombie"]] },
  { t: "Schovávací", c: ["#b06bff", "#5a22b0"], g: [["Schovka", "eye"], ["Vrah mezi námi", "knife"], ["Stavitelská bitva", "build"]] },
  { t: "Závodní", c: ["#3ddc84", "#138a4a"], g: [["Parkour", "parkour"], ["Only Up", "up"], ["Dropper", "drop"], ["Rover závod", "rover"], ["Speedrun", "clock"], ["Beat", "beat"]] },
  { t: "Zábavné", c: ["#45d4ff", "#1576b8"], g: [["Spleef", "shovel"], ["Útěk před TNT", "tnt"], ["Lucky Tower", "lucky"]] },
];
const word = (n) => (n === 1 ? "hra" : n < 5 ? "hry" : "her");

export function renderCategories() {
  $("gcat").innerHTML = CATS.map((k) => `<div class="gsec" style="--c1:${k.c[0]};--c2:${k.c[1]}"><h3><span class="dot"></span>${k.t}<small>${k.g.length} ${word(k.g.length)}</small></h3>
    <div class="ggrid">${k.g.map(([n, i]) => `<div class="game"><div class="gi"><svg viewBox="0 0 24 24" aria-hidden="true">${GI[i]}</svg></div><b>${n}</b><span>${k.t}</span></div>`).join("")}</div></div>`).join("");
}

export function startStars() {
  const c = $("stars"), x = c.getContext("2d"); let s = [], m = [];
  const dpr = Math.min(devicePixelRatio || 1, 2);
  const rs = () => { c.width = innerWidth * dpr; c.height = innerHeight * dpr; s = Array.from({ length: 170 }, () => ({ x: Math.random() * c.width, y: Math.random() * c.height, r: Math.random() * 1.2 * dpr + .4, p: Math.random() * 6, z: Math.random() * .5 + .2 })); };
  rs(); addEventListener("resize", rs);
  if (matchMedia("(prefers-reduced-motion:reduce)").matches) { for (const a of s) { x.globalAlpha = .6; x.fillStyle = "#fff"; x.fillRect(a.x, a.y, a.r, a.r); } return; }
  (function f(t) {
    x.clearRect(0, 0, c.width, c.height);
    const sy = scrollY * dpr;
    for (const a of s) { x.globalAlpha = .25 + .75 * Math.abs(Math.sin(a.p + t / 1400)); x.fillStyle = "#fff"; const y = ((a.y - sy * a.z) % c.height + c.height) % c.height; x.fillRect(a.x, y, a.r * 1.4, a.r * 1.4); }
    if (Math.random() < .012 && m.length < 3) m.push({ x: Math.random() * c.width * .9 + c.width * .2, y: -20, v: (6 + Math.random() * 5) * dpr, l: 1 });
    for (const k of m) {
      k.x -= k.v * .8; k.y += k.v * .55; k.l -= .009;
      const g = x.createLinearGradient(k.x, k.y, k.x + 120 * dpr, k.y - 80 * dpr);
      g.addColorStop(0, "#ffd84a"); g.addColorStop(.3, "#ff7a2a88"); g.addColorStop(1, "#ff3b2f00");
      x.globalAlpha = Math.max(k.l, 0); x.strokeStyle = g; x.lineWidth = 3 * dpr; x.beginPath(); x.moveTo(k.x, k.y); x.lineTo(k.x + 120 * dpr, k.y - 80 * dpr); x.stroke();
      x.fillStyle = "#fff5c0"; x.fillRect(k.x - 2 * dpr, k.y - 2 * dpr, 5 * dpr, 5 * dpr);
    }
    m = m.filter((k) => k.l > 0 && k.y < c.height + 50);
    requestAnimationFrame(f);
  })(0);
}

/* 3D náklon dlaždic + světlo za myší */
export function startTilt(sel = ".tile,.gc") {
  if (matchMedia("(hover:none),(prefers-reduced-motion:reduce)").matches) return;
  document.querySelectorAll(sel).forEach((el) => {
    el.addEventListener("pointermove", (e) => {
      const r = el.getBoundingClientRect(), px = (e.clientX - r.left) / r.width, py = (e.clientY - r.top) / r.height;
      el.style.setProperty("--mx", px * 100 + "%"); el.style.setProperty("--my", py * 100 + "%");
      el.style.transform = `perspective(900px) rotateX(${(.5 - py) * 7}deg) rotateY(${(px - .5) * 9}deg) translateY(-4px)`;
    });
    el.addEventListener("pointerleave", () => { el.style.transform = ""; });
  });
}

/* běžící pruh s hráči online */
export function renderTicker(d) {
  const t = $("ticker");
  const items = !d ? ["● Server je offline – přistaň, až bude zpátky!"] : !d.players.length ? ["● Zrovna nikdo nehraje – buď první na Marsu!"]
    : d.players.map((p) => ({ h: pixelHead(p.name), t: `${p.name} · Lv ${p.level} · ${p.playing || "Lobby"}` }));
  const e = (s) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const html = items.map((s) => typeof s === "string" ? `<span>${e(s)}</span>` : `<span><img class="head" src="${s.h}" alt="">${e(s.t)}</span>`).join("");
  if (t._h === html) return; t._h = html;
  t.innerHTML = html + html;
  t.style.animationDuration = Math.max(18, items.length * 7) + "s";
}

export function startReveal() {
  addEventListener("scroll", () => $("nav").classList.toggle("sc", scrollY > 20), { passive: true });
  const secs = [...document.querySelectorAll("main section[id]")], links = [...document.querySelectorAll("#nlinks a")];
  const so = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting && document.body.dataset.page === "home") links.forEach((a) => a.classList.toggle("act", a.getAttribute("href") === "#" + e.target.id)); }), { rootMargin: "-45% 0px -50% 0px" });
  secs.forEach((s) => so.observe(s));
  const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { threshold: .1 });
  document.querySelectorAll(".reveal").forEach((el) => io.observe(el));
}

export function toast(t = "IP zkopírována ✓") { const x = $("toast"); x.textContent = t; clearTimeout(x._t); x.classList.add("on"); x._t = setTimeout(() => x.classList.remove("on"), 2000); }
