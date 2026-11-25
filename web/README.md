# SquareStar web

Web serveru se **živým zobrazením hráčů** (hráči, level, skin, čas, žebříčky).

## Struktura
- `src/` – web (index.html, css/main.css, js/*.js jako ES moduly, favicon.svg)
- `server.js` – Node server bez závislostí: servíruje web a posílá živá data přes SSE (`/api/stream`)
- `scripts/mock-plugin.js` – falešný plugin pro vývoj (hráči se připojují a odpojují)
- `scripts/build.js` – sestaví jednosouborovou verzi `dist/index.html` (ta jde do JARu pluginu)
- `Dockerfile`, `.env.example`

## Rychlý start (potřebuje Node 22.9+)
1. `cp .env.example .env` a nastav `PLUGIN_URL` (adresa pluginu, např. `http://IP:8123`) a `SERVER_IP`
2. `npm start` → http://localhost:3000
3. Vývoj bez serveru: `npm run dev` (spustí falešný plugin a web)

## Jak funguje živé zobrazení
Plugin vystavuje `/api/status` (port `web.port`, výchozí 8123). `server.js` ho dotazuje každé 2 s a když se
něco změní, hned to pošle všem prohlížečům přes SSE. Když SSE nejde, web sám přepne na dotazování
každé 4 s a po výpadku se znovu připojí. Prohlížeč nikdy nemluví s pluginem napřímo, takže port 8123
stačí otevřít jen pro server s webem.

## Nasazení
- Node hosting / VPS: `npm start` (za reverse proxy s HTTPS, u nginx vypni `proxy_buffering` pro `/api/stream`)
- Docker: `docker build -t squarestar-web . && docker run -p 3000:3000 -e PLUGIN_URL=http://IP:8123 -e SERVER_IP=mc.tvoje.cz squarestar-web`
- Čistě statický hosting (bez Node): `npm run build` a nahraj `dist/index.html`; pak musí prohlížeč na plugin dosáhnout sám.
