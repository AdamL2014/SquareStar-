// Ukázková data (náhled bez serveru): hráči se sami připojují a odpojují.
const NAMES = ["AdrrexStudio", "Kuba123", "MarsKing", "Terka_CZ", "PixelPavel", "NoName07", "Lenka", "RedFox"];
const RANKS = [["OWNER", "#ff5555"], ["VIP", "#ffaa00"], ["HRÁČ", "#aaaaaa"]];
const mk = (n, i) => ({ name: n, rank: RANKS[i % 3][0], rankColor: RANKS[i % 3][1], level: 3 + i * 4, xp: 40 + i * 9, xpNeed: 120, minutes: 90 + i * 130, playing: i % 2 ? "BedWars" : "Lobby", wins: i * 3, kills: i * 7, gamesPlayed: i * 5, xpTotal: 500 + i * 300, skin: "" });
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
