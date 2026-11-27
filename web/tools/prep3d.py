# Připraví data pro webový 3D renderer z resource packu a z BetterModel emotes.bbmodel
import json, math, sys, base64, os
from PIL import Image
RP = "/home/claude/squarestar-/resourcepack/assets/squarestar"
OUT = "/home/claude/squarestar-/web/src/js/"
tex_cache = {}
def tex(name):
    n = name.split(":")[-1].replace("item/", "")
    if n not in tex_cache:
        p = f"{RP}/textures/item/{n}.png"
        if not os.path.exists(p): p = f"/tmp/rp/assets/squarestar/textures/item/{n}.png"
        if not os.path.exists(p):
            print("chybí textura", name)
            base = (240, 240, 236) if "chicken" in n else (110, 200, 90) if "slime" in n else (22, 20, 28) if "enderman" in n else (236, 220, 190) if "cat" in n else (52, 44, 40)
            im = Image.new("RGBA", (16, 16), base + (255,))
            for x in range(16):
                for y in range(16):
                    if (x * 7 + y * 3) % 5 == 0: im.putpixel((x, y), tuple(int(c * .88) for c in base) + (255,))
            if "enderman_eyes" in n:
                im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
                for x, y in [(4, 7), (5, 7), (10, 7), (11, 7)]: im.putpixel((x, y), (210, 90, 255, 255))
            if "spider" in n:
                for x, y in [(5, 6), (10, 6), (4, 8), (11, 8)]: im.putpixel((x, y), (200, 30, 30, 255))
            tex_cache[n] = im
        else: tex_cache[n] = Image.open(p).convert("RGBA")
    return tex_cache[n]
def avg(img, uv, ts):
    W, H = img.size; sx, sy = W / 16, H / 16
    x0, y0, x1, y1 = uv; x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1))
    a, b, c, d = int(x0 * sx), int(y0 * sy), max(int(x0 * sx) + 1, int(math.ceil(x1 * sx))), max(int(y0 * sy) + 1, int(math.ceil(y1 * sy)))
    px = [img.getpixel((min(W - 1, x), min(H - 1, y))) for x in range(a, c) for y in range(b, d)]
    px = [p for p in px if p[3] > 20] or px
    allpx = [img.getpixel((min(W - 1, x), min(H - 1, y))) for x in range(a, c) for y in range(b, d)]
    al = sum(p[3] for p in allpx) / len(allpx)
    r = sum(p[0] for p in px) // len(px); g = sum(p[1] for p in px) // len(px); bb = sum(p[2] for p in px) // len(px)
    return (r << 16) | (g << 8) | bb | ((1 << 24) if al < 235 else 0)
def rot(p, r):
    if not r: return p
    a = math.radians(r["angle"]); o = r["origin"]; x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]; c, s = math.cos(a), math.sin(a)
    if r["axis"] == "x": y, z = y * c - z * s, y * s + z * c
    elif r["axis"] == "y": x, z = x * c + z * s, -x * s + z * c
    else: x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])
FACES = {"north": [(1,1,0),(0,1,0),(0,0,0),(1,0,0)], "south": [(0,1,1),(1,1,1),(1,0,1),(0,0,1)], "east": [(1,1,1),(1,1,0),(1,0,0),(1,0,1)],
         "west": [(0,1,0),(0,1,1),(0,0,1),(0,0,0)], "up": [(0,1,0),(1,1,0),(1,1,1),(0,1,1)], "down": [(0,0,1),(1,0,1),(1,0,0),(0,0,0)]}
def voxel_model(name, res=1.0):
    d = json.load(open(f"{RP}/models/item/{name}.json")); ts = d.get("texture_size", [16, 16]); texs = d["textures"]
    cells = {}
    for e in d["elements"]:
        f, t = e["from"], e["to"]; face = next(iter(e["faces"].values())); col = avg(tex(texs["0"]), face["uv"], ts)
        x = f[0]
        while x < t[0] - 1e-6:
            y = f[1]
            while y < t[1] - 1e-6:
                z = f[2]
                while z < t[2] - 1e-6:
                    k = (int(x // res), int(y // res), int(z // res)); cells.setdefault(k, []).append(col); z += .5
                y += .5
            x += .5
    cell = {k: max(set(v), key=v.count) for k, v in cells.items()}
    flat = []
    dirs = {"north": (0,0,-1), "south": (0,0,1), "east": (1,0,0), "west": (-1,0,0), "up": (0,1,0), "down": (0,-1,0)}
    for (i, j, k), col in cell.items():
        for fn, dv in dirs.items():
            if (i + dv[0], j + dv[1], k + dv[2]) in cell: continue
            for c in FACES[fn]: flat += [round((i + c[0]) * res, 2), round((j + c[1]) * res, 2), round((k + c[2]) * res, 2)]
            flat.append(col)
    disp = d.get("display", {}).get("head", {})
    return {"q": flat, "h": [disp.get("translation", [0, 0, 0])[1], disp.get("scale", [1, 1, 1])[0]]}
def texel_model(name):
    d = json.load(open(f"/tmp/rp/assets/squarestar/models/item/{name}.json")); texs = d["textures"]; flat = []
    for e in d["elements"]:
        f, t = e["from"], e["to"]
        for fn, face in e["faces"].items():
            img = tex(texs.get(face.get("texture", "#0")[1:], texs.get("0"))); W, H = img.size; sx, sy = W / 16, H / 16
            u0, v0, u1, v1 = face.get("uv", [0, 0, 16, 16])
            pts = [rot((f[0] + (t[0] - f[0]) * c[0], f[1] + (t[1] - f[1]) * c[1], f[2] + (t[2] - f[2]) * c[2]), e.get("rotation")) for c in FACES[fn]]
            ext = sorted([abs(t[0] - f[0]), abs(t[1] - f[1]), abs(t[2] - f[2])], reverse=True)
            nu = max(1, min(round(abs(u1 - u0) * sx), round(ext[0] * 1.5) or 1)); nv = max(1, min(round(abs(v1 - v0) * sy), round(ext[1] * 1.5) or 1))
            fx, fy = abs(u1 - u0) * sx / nu, abs(v1 - v0) * sy / nv
            def at(a, b):
                top = [pts[0][k] + (pts[1][k] - pts[0][k]) * a for k in range(3)]; bot = [pts[3][k] + (pts[2][k] - pts[3][k]) * a for k in range(3)]
                return [top[k] + (bot[k] - top[k]) * b for k in range(3)]
            for i in range(nu):
                for j in range(nv):
                    px = int(min(u0, u1) * sx + ((i if u1 >= u0 else nu - 1 - i) + .5) * fx); py = int(min(v0, v1) * sy + ((j if v1 >= v0 else nv - 1 - j) + .5) * fy)
                    c = img.getpixel((min(W - 1, px), min(H - 1, py)))
                    if c[3] < 40: continue
                    q = [at(i / nu, j / nv), at((i + 1) / nu, j / nv), at((i + 1) / nu, (j + 1) / nv), at(i / nu, (j + 1) / nv)]
                    flat += [round(v, 2) for p in q for v in p] + [(c[0] << 16) | (c[1] << 8) | c[2]]
    disp = d.get("display", {}).get("head", {})
    return {"q": flat, "h": [disp.get("translation", [0, 0, 0])[1], disp.get("scale", [1, 1, 1])[0]]}
def model(name):
    if name == "earth": return voxel_model(name)
    if name.startswith("pet_"): return texel_model(name)
    d = json.load(open(f"{RP}/models/item/{name}.json")); ts = d.get("texture_size", [16, 16]); texs = d["textures"]
    quads = []
    for e in d["elements"]:
        f, t = e["from"], e["to"]
        for fn, face in e["faces"].items():
            tid = face.get("texture", "#0")[1:]; img = tex(texs.get(tid, texs.get("0")))
            uv = face.get("uv", [0, 0, 16, 16]); col = avg(img, uv, ts)
            pts = [rot((f[0] + (t[0] - f[0]) * c[0], f[1] + (t[1] - f[1]) * c[1], f[2] + (t[2] - f[2]) * c[2]), e.get("rotation")) for c in FACES[fn]]
            quads.append((pts, col))
    # vyhodit dvojice překrývajících se protilehlých stěn (vnitřek)
    key = lambda pts: tuple(sorted((round(p[0], 3), round(p[1], 3), round(p[2], 3)) for p in pts))
    cnt = {}
    for pts, c in quads: k = key(pts); cnt[k] = cnt.get(k, 0) + 1
    quads = [(p, c) for p, c in quads if cnt[key(p)] == 1]
    flat = []
    for pts, c in quads: flat += [round(v, 2) for p in pts for v in p] + [c]
    disp = d.get("display", {}).get("head", {})
    return {"q": flat, "h": [disp.get("translation", [0, 0, 0])[1], disp.get("scale", [1, 1, 1])[0]]}
names = ["hat_rocket", "hat_ufo", "earth", "oxygen_helmet", "star_shard", "hat_crown", "hat_halo", "hat_alien", "hat_saturn", "hat_propeller", "hat_satellite", "hat_mars", "hat_witch", "hat_santa",
         "balloon_red", "balloon_blue", "balloon_purple", "balloon_gold", "balloon_green", "balloon_white", "crate_common", "crate_rare", "crate_legendary"] + ["pet_" + p for p in ["spider", "chicken", "slime", "fox", "endy", "cat", "raccoon", "cthulhu", "pigeon"]]
M = {n: model(n) for n in names}
for n in names: print(n, len(M[n]["q"]) // 13)
open(OUT + "models3d.js", "w").write("// 3D modely ze serverového resource packu (předpočítané stěny: 4 body + barva). Vygenerováno tools/prep3d.py\nexport const MODELS = " + json.dumps(M, separators=(",", ":")) + ";\n")
# ---- postava + animace z BetterModel ----
bb = json.load(open("/home/claude/srv/plugins/BetterModel/players/emotes.bbmodel"))
groups = {g["uuid"]: g for g in bb["groups"]}; elems = {e["uuid"]: e for e in bb["elements"]}
bones = {}
def walk(lst, parent):
    for x in lst:
        if isinstance(x, dict):
            g = groups[x["uuid"]]; bones[g["name"]] = {"p": parent, "o": g["origin"], "b": []}
            for ch in x.get("children", []):
                if isinstance(ch, str) and ch in elems and elems[ch]["name"] == "skin": bones[g["name"]]["b"].append([elems[ch]["from"], elems[ch]["to"]])
            walk([c for c in x.get("children", []) if isinstance(c, dict)], g["name"])
walk(bb["outliner"], None)
def num(v):
    try: return float(v)
    except: return 0.0
anims = {}
for a in bb["animations"]:
    tr = {}
    for an in a["animators"].values():
        ch = {}
        for kf in an["keyframes"]:
            dp = kf["data_points"][0]; ch.setdefault(kf["channel"], []).append([kf["time"], num(dp["x"]), num(dp["y"]), num(dp["z"])])
        for k in ch: ch[k].sort()
        tr[an["name"]] = ch
    anims[a["name"]] = {"len": a["length"], "loop": a.get("loop"), "t": tr}
rig = {"bones": bones, "anims": anims}
open(OUT + "rig3d.js", "w").write("// Kostra postavy a animace emotů z BetterModel (plugins/BetterModel/players/emotes.bbmodel). Vygenerováno tools/prep3d.py\nexport const RIG = " + json.dumps(rig, separators=(",", ":")) + ";\n")
print({k: len(v["b"]) for k, v in bones.items()})
print(os.path.getsize(OUT + "models3d.js"), os.path.getsize(OUT + "rig3d.js"))
