// Sestaví jednosouborovou verzi webu (dist/index.html) – tu servíruje přímo plugin z JARu (src/main/resources/web/index.html).
// Spuštění: npm run build
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const R = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rd = (f) => fs.readFileSync(path.join(R, "src", f), "utf8");

const order = ["util", "config", "live", "render", "fx", "main"];
const code = order.map((n) => rd(`js/${n}.js`)
  .replace(/^import .*;\s*$/gm, "")
  .replace(/^export (const|function|async function) /gm, "$1 ")).join("\n");
const js = `(async()=>{\n${code}\n})();`;
const fav = "data:image/svg+xml;base64," + Buffer.from(rd("favicon.svg")).toString("base64");
let html = rd("index.html")
  .replace('<link rel="stylesheet" href="css/main.css">', `<style>\n${rd("css/main.css")}\n</style>`)
  .replace('href="favicon.svg"', `href="${fav}"`)
  .replace('<script type="module" src="js/main.js"></script>', `<script>\n${js}\n</script>`);
fs.mkdirSync(path.join(R, "dist"), { recursive: true });
fs.writeFileSync(path.join(R, "dist/index.html"), html);
console.log("dist/index.html", (html.length / 1024).toFixed(1), "kB");
