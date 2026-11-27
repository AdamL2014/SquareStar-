// Netlify Function: /api/status -> proxy na plugin. V Netlify nastav proměnnou PLUGIN_URL = http://IP_SERVERU:8123
export default async () => {
  const base = Netlify.env.get("PLUGIN_URL");
  const json = (o, s) => new Response(JSON.stringify(o), { status: s, headers: { "content-type": "application/json", "cache-control": "no-store" } });
  if (!base) return json({ error: "PLUGIN_URL není nastaveno" }, 503);
  try {
    const r = await fetch(base.replace(/\/$/, "") + "/api/status", { signal: AbortSignal.timeout(4000) });
    if (!r.ok) return json({ error: "plugin HTTP " + r.status }, 503);
    return new Response(await r.text(), { headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=0, s-maxage=3" } });
  } catch { return json({ error: "server offline" }, 503); }
};
export const config = { path: "/api/status" };
