#!/usr/bin/env node
// SquareStar web – bezzávislostní Node server (žádné npm balíčky).
// Dělá: statické soubory (src/), živý přenos dat ze serveru přes SSE (/api/stream), cache (/api/status), /config.json, /api/health.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const env = process.env;
const CFG = {
  port: +env.PORT || 3000,
  host: env.HOST || "0.0.0.0",
  plugin: (env.PLUGIN_URL || "http://127.0.0.1:8123").replace(/\/$/, ""),
  serverIp: env.SERVER_IP || "",
  pollMs: Math.max(1000, +env.POLL_MS || 2000),
  dir: path.resolve(ROOT, env.STATIC_DIR || "src"),
};

/* ---------- živá data z pluginu ---------- */
let latest = null;            // { body: string, hash: string, at: number }
let fails = 0, offlineSent = false;
const clients = new Set();

const hashOf = (o) => JSON.stringify({ ...o, time: 0 });
async function poll() {
  try {
    const r = await fetch(CFG.plugin + "/api/status", { signal: AbortSignal.timeout(4000) });
    if (!r.ok) throw new Error("HTTP " + r.status);
    const obj = await r.json();
    const h = hashOf(obj);
    const changed = !latest || latest.hash !== h;
    latest = { body: JSON.stringify(obj), hash: h, at: Date.now() };
    fails = 0; offlineSent = false;
    if (changed) broadcast("status", latest.body);
  } catch (e) {
    if (++fails >= 3) {
      latest = null;
      if (!offlineSent) { offlineSent = true; broadcast("offline", "{}"); console.warn("[web] plugin neodpovídá:", e.message); }
    }
  }
}
function broadcast(ev, data) { const msg = `event: ${ev}\ndata: ${data}\n\n`; for (const c of clients) c.write(msg); }
setInterval(poll, CFG.pollMs); poll();
setInterval(() => { for (const c of clients) c.write(": ping\n\n"); if (latest) broadcast("status", latest.body); }, 15000);

/* ---------- statické soubory ---------- */
const MIME = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".json": "application/json; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png", ".webp": "image/webp", ".ico": "image/x-icon", ".txt": "text/plain; charset=utf-8" };
const HEAD = {
  "X-Content-Type-Options": "nosniff", "X-Frame-Options": "DENY", "Referrer-Policy": "strict-origin-when-cross-origin",
  "Content-Security-Policy": "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src * data:; connect-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'",
};
function send(req, res, code, type, body, extra = {}) {
  const h = { "Content-Type": type, ...HEAD, ...extra };
  if (/^(text|application\/json|image\/svg)/.test(type) && body.length > 800 && /gzip/.test(req.headers["accept-encoding"] || "")) { body = zlib.gzipSync(body); h["Content-Encoding"] = "gzip"; h.Vary = "Accept-Encoding"; }
  h["Content-Length"] = Buffer.byteLength(body);
  res.writeHead(code, h); res.end(req.method === "HEAD" ? undefined : body);
}

http.createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  const p = url.pathname;
  if (p === "/api/stream") {
    res.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-store", Connection: "keep-alive", "X-Accel-Buffering": "no", ...HEAD });
    res.write("retry: 3000\n\n");
    res.write(latest ? `event: status\ndata: ${latest.body}\n\n` : "event: offline\ndata: {}\n\n");
    clients.add(res); req.on("close", () => clients.delete(res)); return;
  }
  if (p === "/api/status") return latest ? send(req, res, 200, MIME[".json"], latest.body, { "Cache-Control": "no-store" }) : send(req, res, 503, MIME[".json"], '{"error":"server offline"}', { "Cache-Control": "no-store" });
  if (p === "/api/health") return send(req, res, 200, MIME[".json"], JSON.stringify({ ok: true, pluginOnline: !!latest, ageMs: latest ? Date.now() - latest.at : null, sseClients: clients.size }), { "Cache-Control": "no-store" });
  if (p === "/config.json") return send(req, res, 200, MIME[".json"], JSON.stringify({ serverIp: CFG.serverIp, pollMs: 4000 }), { "Cache-Control": "no-store" });
  if (req.method !== "GET" && req.method !== "HEAD") return send(req, res, 405, "text/plain", "Method Not Allowed");

  let rel; try { rel = decodeURIComponent(p); } catch { return send(req, res, 400, "text/plain", "Bad Request"); }
  if (rel.endsWith("/")) rel += "index.html";
  const file = path.join(CFG.dir, path.normalize(rel));
  if (!file.startsWith(CFG.dir + path.sep) && file !== CFG.dir) return send(req, res, 403, "text/plain", "Forbidden");
  fs.readFile(file, (err, buf) => {
    if (err) return send(req, res, 404, "text/plain; charset=utf-8", "404 – tady nic není");
    const ext = path.extname(file);
    send(req, res, 200, MIME[ext] || "application/octet-stream", buf, { "Cache-Control": ext === ".html" ? "no-cache" : "public, max-age=300" });
  });
}).listen(CFG.port, CFG.host, () => console.log(`[web] SquareStar web běží na http://localhost:${CFG.port}  (plugin: ${CFG.plugin})`));
