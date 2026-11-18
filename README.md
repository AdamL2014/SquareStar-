# ✦ SquareStar

Minecraft minihry na **Marsu** s **nízkou gravitací**. Plugin pro **Paper 1.21.11** od **AdrrexStudio**.

## 🎮 Minihry (11)
Mars Parkour · Spleef · TNT Run · SkyWars · Bed Wars · Murder Mystery · Schovávaná · Lucky Tower · **PvP Aréna** · **Sumo** · **Build Battle**

- Mapy se stavějí **automaticky**, většina her má **2 mapy a hlasování**.
- Bed Wars: diamantové ostrovy, smaragdy uprostřed, obchodník astronaut.

## 🌌 3D lobby
Obří létající 3D nápis, kolem kterého obíhá raketa, NPC pro každou minihru, točící se ikony nad bednami a **dvojskok**.

## 👑 Ranky
OWNER, ADMIN, MODERATOR, HELPER, BUILDER, YOUTUBER, STREAMER, **VIDRIFTER** (klikací odkaz na [VidRift](https://vidriftvideo.netlify.app)), LEGENDA, MVP, VIP. S resource packem mají v chatu pixel-art odznaky.

## 🛰️ Vesmírné itemy
Laserová pistole, jetpack, meteoritový granát, gravitační boty, kyslíková helma, plazmový meč, měsíční kámen, vesmírná porce, hvězdný úlomek. Textury jsou ve složce `resourcepack/`.

## 🎁 Bedny
Obyčejná, vzácná a legendární. Zatím bez placení, klíče se získávají za výhry nebo příkazem `/ss key`.

## 📥 Instalace
1. Stáhni `SquareStar.jar` ze složky [download/](download/).
2. Nahraj ho do složky `plugins/` na serveru (Paper 1.21.11).
3. Zapni server.

## ⌨️ Příkazy
| Příkaz | Co dělá |
|---|---|
| `/ss hry` | menu miniher |
| `/ss join <hra>` | připojit se ke hře |
| `/ss leave` | opustit hru |
| `/ss give <item>` | (admin) vesmírný item |
| `/ss key <hráč> <typ>` | (admin) klíč od bedny |
| `/ss rank <hráč> <rank>` | (admin) nastaví rank |
| `/ss rebuild` | (admin) přestaví lobby + 3D efekty |
| `/ss fx` | (admin) obnoví jen 3D nápis, raketu a NPC |
| `/ss lag` | výkon serveru (TPS, MSPT, RAM) |

## 🔧 Sestavení
```
mvn package
```

## 🖼️ Resource pack
Do `server.properties` dej:
```
resource-pack=https://raw.githubusercontent.com/AdamL2014/SquareStar-/main/download/SquareStar-ResourcePack.zip
resource-pack-sha1=98d766d40de252a0c72d29f07a8af9a37fbf1ce0
```

## ✨ Novinky 2.2
- Obří **3D Země**, která se točí, s vrstvou mraků
- Raketa má **oheň** a je rychlejší
- **3D modely všech itemů** (boty, hvězda, helma, pistole, meč, jetpack, granát...)
- **Kosmetika**: 8 stop za hráčem a 5 3D čepic (raketa, UFO, Země...)
- NPC se **dívají na hráče** a mávají, ohňostroj při výhře, uvítací animace
- Nové **ranky ve 2x rozlišení** i v tabu, hezký tab s logem
