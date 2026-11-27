// Jazyk webu: na začátku se zeptá CZ / EN. Anglicky = překlad textů na stránce (i těch, které se dokreslí později).
import { $ } from "./util.js";
import { ICON } from "./icons.js";

const EN = [
  ["Zpět na hlavní stránku", "Back to home"], ["Přihlásit se", "Log in"], ["Přihlásit", "Log in"], ["Odhlášeno", "Logged out"],
  ["Online", "Online"], ["Obchod", "Shop"], ["Kosmetika", "Cosmetics"], ["Přátelé", "Friends"], ["Žebříčky", "Leaderboards"], ["Minihry", "Minigames"], ["Jak hrát", "How to play"], ["Funkce", "Features"],
  ["Hrát", "Play"], ["Kopírovat IP", "Copy IP"], ["Kdo hraje", "Who's playing"], ["Živě", "Live"], ["IP zkopírována ✓", "IP copied ✓"],
  ["hráčů právě hraje", "players online now"], ["Ranky, klíče, kosmetika →", "Ranks, keys, cosmetics →"], ["Přidej kamarády z webu →", "Add friends from the web →"], ["Klikni pro zobrazení →", "Click to view →"], ["Java 1.21.11 · zdarma →", "Java 1.21.11 · free →"],
  ["PRÁVĚ ONLINE", "ONLINE NOW"], ["Data přímo z pluginu. Klikni na hráče pro statistiky.", "Live data from the plugin. Click a player for stats."], ["· živě", "· live"],
  ["hráčů online", "players online"], ["běžících her", "running games"], ["hráčů celkem", "total players"], ["TPS (max 20)", "TPS (max 20)"],
  ["Server online", "Server online"], ["Server je offline", "Server is offline"], ["hráčů", "players"], ["Level", "Level"],
  ["ŽEBŘÍČKY", "LEADERBOARDS"], ["Nejlepší hráči SquareStaru. Dostaneš se mezi ně i ty?", "The best SquareStar players. Can you join them?"], ["Výhry", "Wins"], ["Zabití", "Kills"], ["Čas", "Time"], ["Hry", "Games"],
  ["hráčů v žebříčku", "ranked players"], ["Hráč", "Player"], ["Body", "Points"], ["Rank", "Rank"], ["LEVEL", "LEVEL"], ["VÝHER", "WINS"], ["ZABITÍ", "KILLS"], ["ODEHRÁNO", "PLAYED"], ["HER", "GAMES"],
  ["Legenda Marsu", "Mars Legend"], ["Bojový mistr", "Combat Master"], ["Bojový specialista", "Combat Specialist"], ["Nováček", "Rookie"],
  ["MINIHRY", "MINIGAMES"], ["Přes 20 miniher rozdělených do kategorií stejně jako ve hře.", "Over 20 minigames sorted into categories just like in game."],
  ["Bojové", "Combat"], ["Schovávací", "Hide & seek"], ["Závodní", "Racing"], ["Zábavné", "Fun"], ["Všichni proti všem", "Free for all"], ["Schovka", "Hide and Seek"], ["Vrah mezi námi", "Murder Mystery"], ["Stavitelská bitva", "Build Battle"], ["Rover závod", "Rover Race"], ["Útěk před TNT", "TNT Run"],
  ["FUNKCE", "FEATURES"], ["Všechno je napsané na míru od AdrrexStudio.", "Everything is custom-made by AdrrexStudio."],
  ["Nízká gravitace Marsu", "Low gravity of Mars"], ["Skáčeš dál a výš, padáš pomaleji. Každá minihra se hraje jinak než jinde.", "Jump further and higher, fall slower. Every minigame plays differently."],
  ["Denní odměny a úkoly", "Daily rewards and quests"], ["Každý den odměna se sérií, 3 nové úkoly a 7. den vzácný klíč.", "A daily reward streak, 3 new quests and a rare key on day 7."],
  ["Parta", "Party"], ["Pozvi kamarády přes /parta nebo menu. Vůdce vybere hru a jdete spolu.", "Invite friends with /parta or the menu. The leader picks a game and you go together."],
  ["Levely a 13 úspěchů", "Levels and 13 achievements"], ["XP za hry, výhry a zabití. Série zabití, první krev a žebříčky.", "XP for games, wins and kills. Kill streaks, first blood and leaderboards."],
  ["Věci, které jinde nenajdeš", "Things you won't find elsewhere"], ["3D modely, animované emoty, padající meteory, UFO a vlastní hudba.", "3D models, animated emotes, falling meteors, a UFO and custom music."],
  ["Vlastní AI pomocník přímo ve hře. Zeptej se na pravidla nebo hry.", "A custom AI helper right in the game. Ask about rules or games."],
  ["JAK SE PŘIPOJIT", "HOW TO JOIN"], ["Čtyři kroky a můžeš hrát.", "Four steps and you can play."], ["Spusť Minecraft", "Start Minecraft"], ["Java Edition 1.21.11 → Multiplayer → Přidat server.", "Java Edition 1.21.11 → Multiplayer → Add server."],
  ["Vlož IP adresu", "Paste the IP"], ["Zkopíruj IP tlačítkem Hrát nahoře a vlož ji do „Adresa serveru\".", "Copy the IP with the Play button and paste it into \"Server Address\"."],
  ["Potvrď „Ano\"", "Click \"Yes\""], ["Server nabídne svůj vzhled. Klikni Ano a uvidíš 3D modely a vlastní menu.", "The server offers its resource pack. Click Yes to see 3D models and custom menus."],
  ["Hraj!", "Play!"], ["Kompas v lobby otevře minihry. Nezapomeň na /odmena.", "The compass in the lobby opens minigames. Don't forget /odmena."],
  ["OBCHOD", "SHOP"], ["Kupuj za Mars Coiny ze hry. Věc se ti hned objeví na serveru.", "Buy with Mars Coins from the game. Items appear on the server right away."], ["Tvoje Mars Coiny", "Your Mars Coins"], ["Přihlas se", "Log in"],
  ["Vše", "All"], ["Ranky", "Ranks"], ["Klíče", "Keys"], ["Klíč", "Key"], ["Nelze za Mars Coiny", "Not for Mars Coins"], ["Do košíku", "Add to cart"], ["V košíku · zrušit", "In cart · remove"], ["Otevřít šatník →", "Open wardrobe →"],
  ["Košík je prázdný", "Cart is empty"], ["Košík je prázdný.", "Cart is empty."], ["Koupit", "Buy"], ["Košík:", "Cart:"], ["Nemáš dost Mars Coinů.", "Not enough Mars Coins."], ["Nemáš dost Mars Coinů", "Not enough Mars Coins"], ["Koupeno! Klíče máš ve hře.", "Bought! Your keys are in the game."],
  ["Obyčejný klíč", "Common key"], ["Vzácný klíč", "Rare key"], ["Legendární klíč", "Legendary key"], ["Otevře obyčejnou bednu na spawnu", "Opens a common crate at spawn"], ["Otevře vzácnou bednu na spawnu", "Opens a rare crate at spawn"], ["Otevře legendární bednu na spawnu", "Opens a legendary crate at spawn"],
  ["Barevné jméno, /fly v lobby a další výhody", "Colored name, /fly in the lobby and more"], ["Vše z VIP a ještě víc", "Everything from VIP and more"], ["Šatník", "Wardrobe"], ["Čepice, pety, balónky, stopy, aury a emoty", "Hats, pets, balloons, trails, auras and emotes"],
  ["PŘÁTELÉ", "FRIENDS"], ["Přidávej kamarády a hned uvidíš, kdo je online a co hraje.", "Add friends and see who's online and what they play."], ["Pro přátele se nejdřív přihlas přes svůj Minecraft účet.", "Log in with your Minecraft account to see friends."],
  ["Napiš jméno hráče…", "Type a player name…"], ["Poslat žádost", "Send request"], ["ti poslal žádost o přátelství", "sent you a friend request"], ["Přijmout", "Accept"], ["Odmítnout", "Decline"], ["Žádost odeslána…", "Request sent…"], ["Offline", "Offline"],
  ["PŘIHLÁŠENÍ", "LOG IN"], ["Napiš svoje jméno z Minecraftu.", "Type your Minecraft name."], ["Heslo nikdy nezadáváš.", "You never enter a password."], ["Minecraft jméno", "Minecraft name"], ["Pokračovat", "Continue"],
  ["Připoj se na server a napiš do hry:", "Join the server and type in game:"], ["Ve hře ti vyskočí 6místný kód (funguje i se skrytým chatem). Opiš ho sem:", "A 6-digit code pops up in game (works with hidden chat too). Type it here:"], ["Kód ze hry", "Code from the game"], ["Náhled: projde jakýchkoli 6 číslic.", "Preview: any 6 digits work."],
  ["ŠATNÍK", "WARDROBE"], ["Vyber si vzhled. Uvidíš ho hned na postavě i ve hře.", "Pick your look. You'll see it on the character and in game."], ["Tvůj vzhled", "Your look"], ["Nepřihlášen", "Not logged in"],
  ["Čepice a pety", "Hats & pets"], ["Balónky", "Balloons"], ["Stopy", "Trails"], ["Aury", "Auras"], ["Emoty", "Emotes"], ["Hledat věc…", "Search…"], ["Hlava", "Head"], ["Balónek", "Balloon"], ["Stopa", "Trail"], ["Aura", "Aura"],
  ["Běžné", "Common"], ["Vzácné", "Rare"], ["Epické", "Epic"], ["Legendární", "Legendary"], ["Advent", "Advent"], ["Nasazeno", "Equipped"], ["Vlastníš", "Owned"], ["Zdarma", "Free"], ["Nasadit", "Equip"], ["Sundat", "Unequip"], ["Zkoušíš", "Trying on"], ["Koupit za", "Buy for"],
  ["Adventní kalendář", "Advent calendar"], ["Jen z adventního kalendáře", "Advent calendar only"], ["věcí", "items"], ["věci", "items"], ["věc", "item"], ["Zahrát", "Play"],
  ["Nejsme spojeni s Mojang Studios ani Microsoft.", "Not affiliated with Mojang Studios or Microsoft."], ["Vytvořilo AdrrexStudio.", "Made by AdrrexStudio."], ["Načítám…", "Loading…"],
];
EN.sort((a, b) => b[0].length - a[0].length);
let lang = "cs";
const tr = (s) => { let o = s; for (const [c, e] of EN) if (o.includes(c)) o = o.split(c).join(e); return o; };
function walk(root) {
  const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let n; (n = w.nextNode());) { const v = n.nodeValue; if (v.trim() && !n.parentElement.closest("script,style")) { const t = tr(v); if (t !== v) n.nodeValue = t; } }
  root.querySelectorAll?.("[placeholder],[aria-label]").forEach((el) => { for (const a of ["placeholder", "aria-label"]) { const v = el.getAttribute(a); if (v) { const t = tr(v); if (t !== v) el.setAttribute(a, t); } } });
}
let obs;
function apply(l) {
  lang = l; document.documentElement.lang = l;
  try { localStorage.setItem("ss_lang", l); } catch {}
  if (l === "en") { walk(document.body); obs ??= new MutationObserver((ms) => { obs.disconnect(); for (const m of ms) for (const n of m.addedNodes) n.nodeType === 1 ? walk(n) : n.nodeType === 3 && walk(n.parentElement); if (ms.some((m) => m.type === "characterData")) walk(document.body); obs.observe(document.body, { childList: true, subtree: true, characterData: true }); }); obs.observe(document.body, { childList: true, subtree: true, characterData: true }); }
  else location.reload();
}
export function startLang() {
  let saved = null; try { saved = localStorage.getItem("ss_lang"); } catch {}
  const box = document.createElement("div"); box.className = "modal on"; box.id = "langPick";
  box.innerHTML = `<div class="glass mbox lang"><h3>Vyber jazyk · Choose language</h3><div class="langs">
    <button data-l="cs"><img src="${ICON.flag_cz}" alt=""><b>Čeština</b></button><button data-l="en"><img src="${ICON.flag_en}" alt=""><b>English</b></button></div></div>`;
  if (!saved) document.body.appendChild(box); else if (saved === "en") apply("en");
  box.addEventListener("click", (e) => { const b = e.target.closest("[data-l]"); if (!b) return; box.remove(); if (b.dataset.l === "en") { sw.innerHTML = `<img src="${ICON.flag_en}" alt="">`; apply("en"); } else { try { localStorage.setItem("ss_lang", "cs"); } catch {} } });
  // přepínač v liště
  const sw = document.createElement("button"); sw.className = "langsw"; sw.setAttribute("aria-label", "Jazyk / Language");
  sw.innerHTML = `<img src="${saved === "en" ? ICON.flag_en : ICON.flag_cz}" alt="">`;
  sw.onclick = () => { if (lang === "en") apply("cs"); else { sw.innerHTML = `<img src="${ICON.flag_en}" alt="">`; apply("en"); } };
  document.querySelector("#nav .nright")?.prepend(sw);
}
