import { $, pixelHead } from "./util.js";

export const CATS = [
  { t: "PvP", i: "sword", c: ["#ff3b2f", "#5a0c08"], g: ["1v1", "Všichni proti všem", "5v5", "10v10", "Sumo"] },
  { t: "Bojové", i: "shield", c: ["#ff8a1f", "#5e2706"], g: ["BedWars", "EggWars", "The Bridge", "AnniRun", "Duels", "SkyWars", "UHC Run", "Zombies"] },
  { t: "Schovávací", i: "eye", c: ["#a66bff", "#2e1258"], g: ["Schovka", "Vrah mezi námi", "Stavitelská bitva"] },
  { t: "Závodní", i: "flag", c: ["#3ddc84", "#0b4a2a"], g: ["Parkour", "Only Up", "Dropper", "Rover závod", "Speedrun", "Beat"] },
  { t: "Zábavné", i: "smile", c: ["#5ee7ff", "#0b3f52"], g: ["Spleef", "Útěk před TNT", "Lucky Tower"] },
];
const word = (n) => (n === 1 ? "hra" : n < 5 ? "hry" : "her");

export function renderCategories() {
  $("gcat").innerHTML = CATS.map((k) => `<div class="gc" style="--c1:${k.c[0]};--c2:${k.c[1]}"><div class="gh"><svg class="ic"><use href="#i-${k.i}"/></svg><h3>${k.t}</h3><span>${k.g.length} ${word(k.g.length)}</span></div><ul>${k.g.map((g) => `<li>${g}</li>`).join("")}</ul></div>`).join("");
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
  const so = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) links.forEach((a) => a.classList.toggle("act", a.getAttribute("href") === "#" + e.target.id)); }), { rootMargin: "-45% 0px -50% 0px" });
  secs.forEach((s) => so.observe(s));
  const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { threshold: .1 });
  document.querySelectorAll(".reveal").forEach((el) => io.observe(el));
}

export function toast(t = "IP zkopírována ✓") { const x = $("toast"); x.textContent = t; clearTimeout(x._t); x.classList.add("on"); x._t = setTimeout(() => x.classList.remove("on"), 2000); }
