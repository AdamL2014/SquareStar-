// Sestaví jednosouborovou verzi webu (dist/index.html) – tu servíruje přímo plugin z JARu (src/main/resources/web/index.html).
// Spuštění: npm run build
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const R = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rd = (f) => fs.readFileSync(path.join(R, "src", f), "utf8");

const order = ["util", "config", "live", "demo", "ranks", "icons", "models3d", "rig3d", "fx", "render", "render3d", "account", "wardrobe", "main"];
// každý modul ve vlastním rozsahu (žádné kolize jmen), importy -> const { … } = __modul
const mod = (n) => {
  let src = rd(`js/${n}.js`);
  const ex = [...src.matchAll(/^export (?:const|function|async function) ([A-Za-z0-9_$]+)/gm)].map((m) => m[1]);
  src = src.replace(/^import \{([^}]*)\} from "\.\/([\w-]+)\.js";\s*$/gm, (_, names, from) => `const {${names}} = __${from.replace(/-/g, "_")};`)
    .replace(/^export (const|function|async function) /gm, "$1 ");
  const isMain = n === "main";
  return isMain ? `await (async () => {\n${src}\n})();` : `const __${n} = (() => {\n${src}\nreturn { ${ex.join(", ")} };\n})();`;
};
const code = order.map(mod).join("\n");
const js = `(async()=>{\n${code}\n})();`;
const fav = "data:image/svg+xml;base64," + Buffer.from(rd("favicon.svg")).toString("base64");
let html = rd("index.html")
  .replace(/<link rel="preload"[^>]*>\n?/g, "")
  .replace('<link rel="stylesheet" href="css/main.css">', `<style>\n${rd("css/main.css").replace(/url\(\.\.\/fonts\/([\w-]+\.woff2)\)/g, (_, f) => "url(data:font/woff2;base64," + fs.readFileSync(path.join(R, "src/fonts", f)).toString("base64") + ")")}\n</style>`)
  .replace('href="favicon.svg"', `href="${fav}"`)
  .replace('<script type="module" src="js/main.js"></script>', `<script>\n${js}\n</script>`);
fs.mkdirSync(path.join(R, "dist"), { recursive: true });
fs.writeFileSync(path.join(R, "dist/index.html"), html);
console.log("dist/index.html", (html.length / 1024).toFixed(1), "kB");
