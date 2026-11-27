// Připraví složku public/ pro Cloudflare Pages. Spuštění: npm run build:pages
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const R = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const out = path.join(R, "public");
fs.rmSync(out, { recursive: true, force: true });
fs.cpSync(path.join(R, "src"), out, { recursive: true });
// Pages nemá SSE (dlouhé spojení), web si data bere pollingem z /api/status (Pages Function).
const cfg = { serverIp: process.env.SERVER_IP || "", discord: process.env.DISCORD_URL || "", stream: false, pollMs: 4000 };
fs.writeFileSync(path.join(out, "config.json"), JSON.stringify(cfg, null, 2));
fs.writeFileSync(path.join(out, "_headers"), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src * data:; connect-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'
/js/*
  Cache-Control: public, max-age=300
`);
console.log("public/ hotovo:", fs.readdirSync(out).join(", "));
