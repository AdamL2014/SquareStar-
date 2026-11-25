// Konfigurace webu. Pořadí: /config.json ze serveru (server.js) -> window.SS_CONFIG -> výchozí hodnoty.
const defaults = { serverIp: "", apiBase: "", pollMs: 4000 };

export async function loadConfig() {
  let cfg = { ...defaults, ...(window.SS_CONFIG || {}) };
  try {
    const r = await fetch("config.json", { cache: "no-store" });
    if (r.ok && (r.headers.get("content-type") || "").includes("json")) cfg = { ...cfg, ...(await r.json()) };
  } catch { /* běží bez serveru (např. přímo z pluginu) – nevadí */ }
  if (!cfg.serverIp && location.protocol.startsWith("http") && location.hostname) cfg.serverIp = location.hostname;
  return cfg;
}
