"""Dudunka fantasy art recipe: bounded palettes, deliberately drawn pixels and mesh edits.

This creates a separate editable Blockbench derivative, never writes the approved
source, and needs only Python's standard library. No image resampling or blur.
"""
import base64
import copy
import hashlib
import json
import math
import struct
import zlib
from collections import defaultdict, deque
from generate_mushroom_bells_preview import cross, triangulate, area

WIDTH, HEIGHT = 256, 128
PIXELS_PER_SOURCE_UNIT = 6  # .375 blocks/unit * 16 pixels/block.
OMIT = {"Soft_earth_diorama_edge_Refined", "Moss_clearing_surface_Refined"}


def png_bytes(pixels):
    height, width = len(pixels), len(pixels[0])
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    raw = b"".join(b"\x00" + bytes(c for pixel in row for c in pixel) for row in pixels)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def rgba(hex_color):
    return tuple(bytes.fromhex(hex_color.removeprefix("#"))) + (255,)


PALETTES = {
    "cap_rose": ["713448", "933D55", "B14D66", "CB7080", "E49B9D", "F1D9AA"],
    "cap_teal": ["244C53", "30676B", "3E8880", "63A89A", "93C5AE", "E4D99F"],
    "cap_amber": ["79502B", "A36B2E", "CA8E39", "E0AE52", "EFCE80", "F5E1AA"],
    "earth": ["423D36", "59463D", "705343", "89644A", "A77D58"],
    "moss": ["304C38", "426343", "58794A", "729353", "99AD6A"],
    "moss_light": ["385741", "58774C", "7E965B", "9AA96B", "BBBD84"],
    "stem_ivory": ["84795C", "B6A17B", "D7C39A", "E8D6AC", "F4E5BE"],
    "gill_cream": ["896E4E", "B89764", "D8BA81", "EFDBA1", "FFF0C2"],
    "cap_rose_light": ["833E51", "B65B70", "DA8895", "EDAFAB", "F2D8AF"],
    "cap_teal_light": ["315E60", "518E82", "81B4A1", "B4D0AB", "E6DDA8"],
    "cap_amber_light": ["926329", "C6943D", "E3B85B", "F0D48D", "F4E7B6"],
    "stone": ["414F4B", "596960", "778078", "98A294", "BEC3AB"],
    "carving": ["26383C", "35484B", "4B5E5A", "6D7A69", "8F9A7C"],
    "crystal": ["34415F", "48648C", "648FAF", "92CDD0", "DCEFCF"],
    "crystal_light": ["425B7A", "648DA7", "98CED0", "C4E5D8", "F3F2CD"],
    "leaf": ["264D3E", "346B53", "488168", "729875", "A0B286"],
    "magic": ["9E8953", "C4AA65", "E6CC83", "F5E4AE", "FFF3CC"],
    "bell_gold": ["78542D", "AD7D37", "D6A141", "EDC766", "FFE3A0"],
}


def draw_tile(name, size):
    colors = [rgba(c) for c in PALETTES[name]]
    pixels = [[colors[2] for _ in range(size)] for _ in range(size)]
    def dot(x, y, shade):
        if 0 <= x < size and 0 <= y < size:
            pixels[y][x] = colors[shade]
    def rect(x, y, w, h, shade):
        for py in range(y, y + h):
            for px in range(x, x + w):
                dot(px, py, shade)
    def line(points, shade):
        for a, b in zip(points, points[1:]):
            steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
            for i in range(steps + 1):
                dot(round(a[0] + (b[0] - a[0]) * i / steps), round(a[1] + (b[1] - a[1]) * i / steps), shade)
    seed = sum(ord(c) for c in name)
    # Sparse clustered marks, not independent per-pixel white noise.
    for y in range(1, size, 4):
        for x in range(1, size, 4):
            mark = (x * 7 + y * 13 + seed) % 13
            if mark in (0, 3, 7):
                rect(x, y, 2 + mark % 2, 2, 1 if mark == 0 else 3)
    if name in ("cap_rose", "cap_teal", "cap_amber"):
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - 31.5, y - 31.5)
                if d > 29:
                    dot(x, y, 0 if (x + y) % 7 == 0 else 1)
                elif 26.5 < d < 28.5:
                    # A restrained stitched pixel border, not a fully glowing ring.
                    dot(x, y, 4 if (int(math.atan2(y - 31.5, x - 31.5) * 12) % 3) else 5)
        spots = [(16, 14, 3), (43, 12, 2), (48, 30, 4), (37, 44, 3), (15, 39, 4), (27, 26, 2)]
        for index, (x, y, radius) in enumerate(spots):
            if name == "cap_teal":
                x, y, radius = y, 63 - x, max(2, radius - 1)
            for py in range(-radius, radius + 1):
                for px in range(-radius, radius + 1):
                    included = abs(px) + abs(py) <= radius + 1 if name == "cap_teal" else px * px + py * py <= radius * radius
                    if included:
                        dot(x + px, y + py, 5 if py <= 0 else 4)
            dot(x - 1, y - radius - 1, 0)
        line([(24, 7), (27, 6), (31, 6)], 3)
        line([(8, 25), (7, 30), (8, 33)], 3)
    elif name == "stem_ivory":
        for x in (3, 11, 20, 28):
            line([(x, 0), (x - 1, 8), (x + 1, 17), (x, 31)], 1)
            line([(x + 2, 0), (x + 1, 7), (x + 3, 16), (x + 2, 31)], 3)
        line([(12, 18), (14, 16), (16, 18), (15, 22), (13, 22), (12, 18)], 0)
        rect(14, 18, 1, 3, 3)
        line([(17, 16), (18, 19), (17, 23)], 4)
    elif name == "gill_cream":
        for x in range(2, size, 5):
            line([(x, 0), (x + 1, 9), (x, 20), (x + 1, 31)], 1)
            line([(x + 2, 0), (x + 3, 11), (x + 2, 31)], 3)
        for x, y in [(4, 5), (14, 21), (25, 12)]:
            rect(x, y, 1, 3, 4)
            dot(x - 2, y + 2, 0)
    elif name == "bell_gold" or name.endswith("_light") and name.startswith("cap_"):
        for y in (2, 7, 24, 29):
            rect(0, y, size, 1, 1 if y in (7, 29) else 3)
        for x in range(3, size, 8):
            line([(x, 15), (x + 2, 12), (x + 4, 15), (x + 2, 18), (x, 15)], 4)
            dot(x + 2, 14, 0)
    elif name in ("stone", "carving", "earth"):
        for x, y, w, h in [(2, 3, 5, 3), (16, 2, 8, 4), (23, 14, 6, 4), (4, 19, 6, 5), (16, 26, 8, 3)]:
            rect(x, y, w, h, 1)
            rect(x, y - 1, w - 1, 1, 3)
        for crack in [[(10, 1), (9, 5), (12, 7), (11, 11)], [(27, 20), (24, 22), (25, 27), (23, 31)]]:
            line(crack, 0)
        line([(17, 1), (21, 1), (23, 2)], 4)
        if name == "stone":
            green = rgba("4C6B4D")
            for y in range(24, size):
                for x in range(size):
                    if (x // 3 + y // 2) % 5 < 2 and y > 25 + (x // 4) % 4:
                        pixels[y][x] = green
    elif name.startswith("crystal"):
        for y in range(size):
            for x in range(size):
                band = (x + y // 5) % 18
                dot(x, y, 0 if band < 2 else 1 if band < 6 else 2 if band < 13 else 3)
        line([(6, 3), (6, 10), (9, 13), (9, 18)], 4)
        line([(23, 18), (23, 23), (26, 26)], 4)
        rect(18, 6, 3, 1, 4); rect(19, 5, 1, 3, 4)
    elif name in ("moss", "moss_light", "leaf"):
        rect(0, 0, 2, 2, 1)  # A small soil shadow beneath the leaf clusters.
        for y in range(2, size, 6):
            for x in range(2, size, 7):
                line([(x, y + 3), (x + 2, y), (x + 4, y + 2)], 3)
                dot(x + 1, y + 3, 0)
                dot(x + 2, y - 1, 4)
        if name == "leaf":
            line([(3, 30), (12, 19), (20, 12), (28, 3)], 4)
            for x, y in [(11, 20), (17, 14), (23, 8)]:
                line([(x - 5, y - 2), (x, y), (x + 3, y + 4)], 3)
    elif name == "magic":
        rect(0, 0, size, size, 3)
        for x, y in [(8, 8), (23, 19), (11, 27)]:
            rect(x - 2, y + 2, 4, 1, 1)
            rect(x - 1, y + 1, 3, 1, 2)
            dot(x - 3, y + 3, 0)
            line([(x - 2, y), (x + 2, y)], 4); line([(x, y - 2), (x, y + 2)], 4)
    return pixels


def atlas():
    image = [[rgba("26383C") for _ in range(WIDTH)] for _ in range(HEIGHT)]
    regions = {}
    for i, name in enumerate(("cap_rose", "cap_teal", "cap_amber")):
        regions["concept_" + name] = (i * 64, 0, 64)
    others = [n for n in PALETTES if "concept_" + n not in regions]
    for i, name in enumerate(others):
        regions["concept_" + name] = ((i % 8) * 32, 64 + (i // 8) * 32, 32)
    for material, (ox, oy, size) in regions.items():
        tile = draw_tile(material.removeprefix("concept_"), size)
        for y in range(size):
            image[oy + y][ox:ox + size] = tile[y]
    return image, regions


def orient_faces(element):
    """Repair manifold winding before removing redundant reversed solid faces.

    Open panels, blades and leaves retain two-sided rendering. Connected closed
    components are oriented by positive signed volume, not a guessed radial normal.
    """
    faces = list(element["faces"].values())
    edges = defaultdict(list)
    for i, f in enumerate(faces):
        keys = f["vertices"]
        for a, b in zip(keys, keys[1:] + keys[:1]):
            edges[tuple(sorted((a, b)))].append((i, a, b))
    closed = bool(edges) and all(len(v) == 2 for v in edges.values())
    adjacency = defaultdict(list)
    for uses in edges.values():
        if len(uses) == 2:
            a, b = uses
            same = a[1:] == b[1:]
            adjacency[a[0]].append((b[0], same)); adjacency[b[0]].append((a[0], same))
    flipped, components = {}, []
    for start in range(len(faces)):
        if start in flipped:
            continue
        flipped[start] = False; pending = deque([start]); component = []
        while pending:
            current = pending.popleft(); component.append(current)
            for other, invert in adjacency[current]:
                desired = flipped[current] != invert
                if other in flipped:
                    if flipped[other] != desired:
                        raise ValueError("Non-orientable source mesh: " + element["name"])
                else:
                    flipped[other] = desired; pending.append(other)
        components.append(component)
    for i, face in enumerate(faces):
        if flipped[i]:
            face["vertices"].reverse()
    if closed:
        for component in components:
            volume = 0
            for i in component:
                poly = [element["vertices"][key] for key in faces[i]["vertices"]]
                for tri in triangulate(poly):
                    # cross of b and c against the origin.
                    n = cross([0, 0, 0], tri[1], tri[2])
                    volume += sum(tri[0][j] * n[j] for j in range(3)) / 6
            if abs(volume) < 1e-9:
                raise ValueError("Closed mesh has zero volume: " + element["name"])
            if volume < 0:
                for i in component:
                    faces[i]["vertices"].reverse()
    return closed


def mesh_replace(element, vertices, faces, texture=None):
    old_texture = next(iter(element["faces"].values()))["texture"] if texture is None else texture
    unique, lookup, aliases = [], {}, []
    for p in vertices:
        key = tuple(round(v, 6) for v in p)
        if key not in lookup:
            lookup[key] = len(unique); unique.append(list(key))
        aliases.append(lookup[key])
    clean_faces = []
    for face in faces:
        clean = []
        for index in face:
            if not clean or clean[-1] != aliases[index]:
                clean.append(aliases[index])
        if len(clean) > 1 and clean[-1] == clean[0]:
            clean.pop()
        if len(clean) >= 3 and sum(area([unique[clean[0]], unique[clean[j]], unique[clean[j + 1]]]) for j in range(1, len(clean) - 1)) > 1e-9:
            clean_faces.append(clean)
    element["vertices"] = {f"v{i}": p for i, p in enumerate(unique)}
    element["faces"] = {f"f{i}": {"vertices": [f"v{j}" for j in f], "texture": old_texture, "uv": {}}
                        for i, f in enumerate(clean_faces)}


def stepped_cap(element, label):
    old = list(element["vertices"].values())
    if len(old) != 102:
        raise ValueError("Main cap ring topology changed; review the art recipe")
    cx, cz, sides = {"Rose": (-8, -2, 16), "Teal": (6, -6, 12), "Amber": (7, 6, 16)}[label]
    profiles = {
        "Rose": [(0, 0, 1), (0, .35, 1), (1, -.1, 1), (2, -.1, 1), (2, -.1, .94), (3, 0, 1), (3, 0, .93)],
        "Teal": [(0, 0, 1), (0, .3, 1), (1, -.05, 1), (2, 0, 1), (2, 0, .91), (3, 0, 1)],
        "Amber": [(0, 0, 1), (1, 0, 1), (1, 0, .94), (2, 0, 1), (2, 0, .94), (3, 0, 1)],
    }[label]
    vertices, faces = [], []
    for ring, dy, scale in profiles:
        for i in range(sides):
            s = i * 20 / sides; a, t = math.floor(s), s % 1
            p, q = old[ring * 20 + a], old[ring * 20 + (a + 1) % 20]
            x, z = p[0] * (1 - t) + q[0] * t, p[2] * (1 - t) + q[2] * t
            vertices.append([round((cx + (x - cx) * scale) * 6) / 6,
                             p[1] + dy, round((cz + (z - cz) * scale) * 6) / 6])
    for ring in range(len(profiles) - 1):
        for i in range(sides):
            a, b = ring * sides + i, ring * sides + (i + 1) % sides
            faces.append([a, a + sides, b + sides, b])
    bottom = len(vertices); vertices.append([cx, old[0][1], cz])
    tip = len(vertices); vertices.append([cx, old[80][1], cz])
    for i in range(sides):
        faces.extend([[i, (i + 1) % sides, bottom], [(len(profiles) - 1) * sides + i, tip, (len(profiles) - 1) * sides + (i + 1) % sides]])
    mesh_replace(element, vertices, faces)
    return vertices[:sides]


def styled_scene(source_bytes):
    scene = copy.deepcopy(json.loads(source_bytes))
    if scene["meta"]["model_format"] != "free":
        raise ValueError("Expected approved Generic Model")
    for element in [*scene["elements"], *scene["groups"]]:
        if any(element.get("rotation", [0, 0, 0])) or any(element.get("origin", [0, 0, 0])):
            raise ValueError("Transforms need explicit art-recipe support")
        if not element.get("visibility", True) or not element.get("export", True):
            raise ValueError("Hidden elements need an explicit art decision")
    original_textures = scene["textures"]
    texture_names = [t["name"] for t in original_textures]
    image, regions = atlas()
    png = png_bytes(image)
    removed = {e["uuid"] for e in scene["elements"] if e["name"] in OMIT}
    scene["elements"] = [e for e in scene["elements"] if e["uuid"] not in removed]
    def clean(nodes):
        for node in nodes[:]:
            if isinstance(node, str) and node in removed:
                nodes.remove(node)
            elif isinstance(node, dict):
                clean(node["children"])
    clean(scene["outliner"])
    by_name = {e["name"]: e for e in scene["elements"]}
    # The original gold hairline was below one Minecraft pixel at this world scale.
    for p in by_name["Fine_warm_inlay_ring"]["vertices"].values():
        dx, dz = p[0], p[2] - 1
        r = math.hypot(dx, dz)
        target = 3.95 + (1 / 12 if r > 3.95 else -1 / 12)
        p[0], p[2] = dx * target / r, 1 + dz * target / r
    for label in ("Rose", "Teal", "Amber"):
        perimeter = stepped_cap(by_name[label + "_sculpted_cap"], label)
        lip = by_name[label + "_scalloped_lip"]
        h = perimeter[0][1]; n = len(perimeter)
        mesh_replace(lip, [[x, h + dy, z] for dy in (-.08, .13) for x, _, z in perimeter],
                     [[i, i + n, (i + 1) % n + n, (i + 1) % n] for i in range(n)])
        lining = by_name[label + "_scalloped_bell_underside_lining"]
        old = list(lining["vertices"].values())
        if len(old) != 144:
            raise ValueError("Underside topology changed")
        mesh_replace(lining, [old[r * 48 + i] for r in range(3) for i in range(0, 48, 2)],
                     [[r * 24 + i, (r + 1) * 24 + i, (r + 1) * 24 + (i + 1) % 24, r * 24 + (i + 1) % 24]
                      for r in range(2) for i in range(24)])
        for i in range(1, 13):
            gill = by_name[f"{label}_sweeping_bell_gill_{i}"]
            old = list(gill["vertices"].values())
            if len(old) != 20:
                raise ValueError("Gill topology changed")
            # Retain the full hanging contour; thickness below one pixel becomes a two-sided blade.
            v = []
            for r in range(5):
                v.extend([[sum(old[r * 4 + j][k] for j in (0, 1)) / 2 for k in range(3)],
                          [sum(old[r * 4 + j][k] for j in (2, 3)) / 2 for k in range(3)]])
            mesh_replace(gill, v, [[j * 2, j * 2 + 1, j * 2 + 3, j * 2 + 2] for j in range(4)])
    # The former tip shard mostly lived inside the crown. Put its light on a visible surface.
    crown = by_name["Tall_central_prismatic_crown"]
    cone = [f for f in crown["faces"].values() if len(f["vertices"]) == 3]
    face = max(cone, key=lambda f: sum(crown["vertices"][k][2] for k in f["vertices"]))
    tri = [crown["vertices"][k] for k in face["vertices"]]
    normal = cross(*tri); length = math.sqrt(sum(n * n for n in normal)); normal = [n / length for n in normal]
    center = [sum(p[k] for p in tri) / 3 for k in range(3)]
    highlight = [[center[k] + (p[k] - center[k]) * .68 + normal[k] * .006 for k in range(3)] for p in tri]
    mesh_replace(by_name["Crown_tip_light_facet"], highlight, [[0, 1, 2]])
    meta, closed_count = {}, 0
    for element in scene["elements"]:
        closed = orient_faces(element); closed_count += closed
        vertices = list(element["vertices"].values())
        low = [min(p[k] for p in vertices) for k in range(3)]
        high = [max(p[k] for p in vertices) for k in range(3)]
        for face in element["faces"].values():
            material = texture_names[face["texture"]]
            keys = face["vertices"]; poly = [element["vertices"][k] for k in keys]
            normal = next((cross(*t) for t in triangulate(poly) if area(t) > 1e-9), [0, 1, 0])
            if "sculpted_cap" in element["name"] and normal[1] < 0 and all(abs(p[1] - poly[0][1]) < 1e-5 for p in poly):
                material = "concept_gill_cream"
            ox, oy, size = regions[material]
            if element["name"].endswith("_sculpted_cap") and material in ("concept_cap_rose", "concept_cap_teal", "concept_cap_amber"):
                cx, cz = {"Rose": (-8, -2), "Teal": (6, -6), "Amber": (7, 6)}[element["name"].split("_")[0]]
                uv = {key: [ox + 32 + (p[0] - cx) * 6, oy + 32 + (p[2] - cz) * 6] for key, p in zip(keys, poly)}
            else:
                axis = max(range(3), key=lambda i: abs(normal[i]))
                axes = (0, 2) if axis == 1 else (2, 1) if axis == 0 else (0, 1)
                factors = [min(6, (size - 4) / max(high[i] - low[i], 1e-5)) for i in axes]
                uv = {key: [ox + 2 + (p[axes[0]] - low[axes[0]]) * factors[0],
                            oy + size - 2 - (p[axes[1]] - low[axes[1]]) * factors[1]] for key, p in zip(keys, poly)}
            if any(not (ox + .5 <= p[0] <= ox + size - .5 and oy + .5 <= p[1] <= oy + size - .5) for p in uv.values()):
                raise ValueError("UV escaped its padded art tile: " + element["name"])
            face["uv"] = {key: [round(v, 6) for v in p] for key, p in uv.items()}; face["texture"] = 0
        meta[element["uuid"]] = {"closed": closed, "name": element["name"]}
    texture = copy.deepcopy(original_textures[0])
    texture.update({"name": "dudunka_fantasy_atlas", "width": WIDTH, "height": HEIGHT,
                    "uv_width": WIDTH, "uv_height": HEIGHT, "source": "data:image/png;base64," + base64.b64encode(png).decode(),
                    "render_mode": "default", "render_sides": "double", "relative_path": "", "path": ""})
    scene["textures"] = [texture]; scene["resolution"] = {"width": WIDTH, "height": HEIGHT}
    scene["name"] = "mushroom_bells_clearing_styled"
    scene["model_identifier"] = "mushroom_bells_clearing_styled"
    scene["dudunka_style"] = {"recipe": 1, "approved_sha256": hashlib.sha256(source_bytes).hexdigest(),
                              "atlas_regions": regions, "closed_meshes": closed_count,
                              "notes": "Terrain disk omitted; terrain supplied by Minecraft. Forge lighting is in the generated MTL/layout."}
    return scene, png, image, meta
