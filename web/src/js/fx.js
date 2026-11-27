import { $ } from "./util.js";

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
  const c = $("stars"), x = c.getContext("2d"); let s = [];
  const rs = () => { c.width = c.offsetWidth * devicePixelRatio; c.height = c.offsetHeight * devicePixelRatio; s = Array.from({ length: 160 }, () => ({ x: Math.random() * c.width, y: Math.random() * c.height * .8, r: Math.random() * 1.3 * devicePixelRatio + .3, p: Math.random() * 6 })); };
  rs(); addEventListener("resize", rs);
  const still = matchMedia("(prefers-reduced-motion:reduce)").matches;
  (function f(t) { x.clearRect(0, 0, c.width, c.height); for (const a of s) { x.globalAlpha = .3 + .7 * Math.abs(Math.sin(a.p + (still ? 0 : t / 1400))); x.fillStyle = "#fff"; x.beginPath(); x.arc(a.x, a.y, a.r, 0, 7); x.fill(); } if (!still) requestAnimationFrame(f); })(0);
}

export function startReveal() {
  addEventListener("scroll", () => $("nav").classList.toggle("sc", scrollY > 20), { passive: true });
  const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { threshold: .1 });
  document.querySelectorAll(".reveal").forEach((el) => io.observe(el));
}

export function toast() { const x = $("toast"); x.classList.add("on"); setTimeout(() => x.classList.remove("on"), 1800); }
