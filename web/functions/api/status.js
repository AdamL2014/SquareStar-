// Cloudflare Pages Function: /api/status -> proxy na plugin (HTTP port pluginu, např. http://IP:8123).
// Nastav v Cloudflare: Settings -> Variables -> PLUGIN_URL = http://IP_SERVERU:8123
export async function onRequestGet({ env }) {
  if (!env.PLUGIN_URL) return json({ error: "PLUGIN_URL není nastaveno" }, 500);
  try {
    const r = await fetch(env.PLUGIN_URL.replace(/\/$/, "") + "/api/status", { cf: { cacheTtl: 3, cacheEverything: true }, signal: AbortSignal.timeout(4000) });
    if (!r.ok) return json({ error: "plugin HTTP " + r.status }, 503);
    return new Response(await r.text(), { headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*" } });
  } catch { return json({ error: "server offline" }, 503); }
}
const json = (o, s) => new Response(JSON.stringify(o), { status: s, headers: { "content-type": "application/json", "cache-control": "no-store" } });
