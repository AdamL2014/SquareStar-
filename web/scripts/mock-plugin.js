// Falešný plugin pro vývoj: simuluje /api/status, hráči se připojují a odpojují. Spuštění: npm run mock
import http from "node:http";
const NAMES = ["AdrrexStudio", "Kuba123", "MarsKing", "Terka_CZ", "PixelPavel", "NoName07", "Lenka", "RedFox"];
const RANKS = [["OWNER", "#ff5555"], ["VIP", "#ffaa00"], ["HRAC", "#aaaaaa"]];
let online = ["AdrrexStudio", "Kuba123"];
const mk = (n, i) => ({ name: n, rank: RANKS[i % 3][0], rankColor: RANKS[i % 3][1], level: 3 + i * 4, xp: 40 + i * 9, xpNeed: 120, minutes: 90 + i * 130, playing: i % 2 ? "BedWars" : "Lobby", wins: i * 3, kills: i * 7, gamesPlayed: i * 5, xpTotal: 500 + i * 300, skin: "" });
setInterval(() => {
  const free = NAMES.filter((n) => !online.includes(n));
  if (free.length && (Math.random() < .55 || online.length < 2)) online.push(free[Math.floor(Math.random() * free.length)]);
  else if (online.length > 1) online.splice(Math.floor(Math.random() * online.length), 1);
}, +process.env.MOCK_MS || 4000);
http.createServer((req, res) => {
  if (req.url !== "/api/status") { res.writeHead(404); return res.end(); }
  const players = online.map((n) => mk(n, NAMES.indexOf(n)));
  const top = (k) => NAMES.map((n, i) => mk(n, i)).sort((a, b) => b[k] - a[k]).slice(0, 10);
  res.writeHead(200, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" });
  res.end(JSON.stringify({ name: "SquareStar", version: "mock", mc: "1.21.11", max: 100, tps: 19.9, games: 2, time: Date.now(), online: players.length, players, registered: 128, top: { xpTotal: top("xpTotal"), wins: top("wins"), kills: top("kills"), minutes: top("minutes"), gamesPlayed: top("gamesPlayed") } }));
}).listen(8123, () => console.log("[mock] falešný plugin na :8123"));
