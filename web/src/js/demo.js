// Ukázková data (náhled bez serveru): hráči se sami připojují a odpojují.
const NAMES = ["AdrrexStudio", "Kuba123", "MarsKing", "Terka_CZ", "PixelPavel", "NoName07", "Lenka", "RedFox"];
const RANKS = [["OWNER", "#ff5555"], ["VIP", "#55ff55"], ["HRAC", "#aaaaaa"], ["MVP", "#55ffff"], ["YOUTUBER", "#ff5555"], ["LEGENDA", "#ff55ff"], ["VIDRIFTER", "#55ffff"], ["HELPER", "#55ff55"]];
const GAMES = [["bed", "BedWars"], ["sky", "SkyWars"], ["duel", "1v1"], ["egg", "EggWars"], ["bridge", "The Bridge"], ["sumo", "Sumo"], ["parkour", "Parkour"], ["knife", "Vrah mezi námi"], ["build", "Stavitelská bitva"]];
const tiers = (i) => GAMES.map(([g, n], k) => { const s = (i * 7 + k * 3) % 11; return { g, n, t: (s % 3 ? "LT" : "HT") + Math.min(5, 1 + Math.floor(((s + (7 - i)) % 9) / 2)) }; }).sort((a, b) => a.t[2] - b.t[2] || (a.t < b.t ? -1 : 1));
const mk = (n, i) => ({ tiers: tiers(i), name: n, rank: RANKS[i % 8][0], rankColor: RANKS[i % 8][1], level: 3 + i * 4, xp: 40 + i * 9, xpNeed: 120, minutes: 90 + i * 130, playing: i % 2 ? "BedWars" : "Lobby", wins: i * 3, kills: i * 7, gamesPlayed: i * 5, xpTotal: 500 + i * 300, skin: "" });
export function startDemo(onData) {
  let online = ["AdrrexStudio", "Kuba123", "MarsKing", "Terka_CZ"];
  const tick = () => {
    const free = NAMES.filter((n) => !online.includes(n));
    if (free.length && (Math.random() < .55 || online.length < 3)) online.push(free[Math.floor(Math.random() * free.length)]);
    else if (online.length > 2) online.splice(Math.floor(Math.random() * online.length), 1);
    const top = (k) => NAMES.map(mk).sort((a, b) => b[k] - a[k]);
    onData({ max: 100, tps: 19.9, games: 2, online: online.length, players: online.map((n) => mk(n, NAMES.indexOf(n))), registered: 128,
      top: { xpTotal: top("xpTotal"), wins: top("wins"), kills: top("kills"), minutes: top("minutes"), gamesPlayed: top("gamesPlayed") } });
  };
  tick(); setInterval(tick, 4000);
}
