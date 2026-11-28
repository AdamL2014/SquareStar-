// Konfigurace webu. Pořadí: /config.json ze serveru (server.js) -> window.SS_CONFIG -> výchozí hodnoty.
const defaults = { serverIp: "squarestar.minekeep.gg", apiBase: "", pollMs: 4000, discord: "", stream: true, demo: false };

export async function loadConfig() {
  let cfg = { ...defaults, ...(window.SS_CONFIG || {}) };
  if (!cfg.demo) try {
    const r = await fetch("config.json", { cache: "no-store" });
    if (r.ok && (r.headers.get("content-type") || "").includes("json")) cfg = { ...cfg, ...(await r.json()) };
  } catch { /* běží bez serveru (např. přímo z pluginu) – nevadí */ }
  if (false && location.protocol.startsWith("http") && location.hostname) cfg.serverIp = location.hostname;
  return cfg;
}
