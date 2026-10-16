#!/usr/bin/env python3
"""Read the approved Generic Model; generate static Forge OBJ cells, never edit the source.

No runtime parser, custom renderer, Pillow or third-party Python package is needed.
Only the current solid-color, unrotated mesh contract is accepted. Fail explicitly
if a later artist edit needs a richer converter, rather than silently losing it.
"""
import argparse
import base64
from collections import defaultdict
import hashlib
import itertools
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path("docs/blockbench/mushroom_bells_clearing.bbmodel")
ASSETS = Path("src/main/resources/assets/dudunka")
MODELS = ASSETS / "models/block/mushroom_bells_preview"
LAYOUT = Path("src/main/resources/data/dudunka/mushroom_bells_preview/layout.json")
BLOCKSTATE = ASSETS / "blockstates/mushroom_bells_preview_part.json"
SCALE = 0.375  # world blocks / source unit; vanilla's implicit 1/16 is NOT used.
EPS = 1e-9


def solid_png(data):
    """Validate an embedded RGBA8 PNG and return its one color (all PNG row filters)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Expected embedded PNG")
    offset, compressed, header = 8, b"", None
    while offset < len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        if kind == b"IDAT":
            compressed += payload
        offset += size + 12
    width, height, depth, color, compression, filtering, interlace = header
    if (depth, color, compression, filtering, interlace) != (8, 6, 0, 0, 0):
        raise ValueError("Converter expects noninterlaced RGBA8 palette PNGs")
    raw, previous, pixels, offset = zlib.decompress(compressed), [0] * (width * 4), [], 0
    for _ in range(height):
        filter_type = raw[offset]
        row = list(raw[offset + 1:offset + 1 + width * 4])
        offset += 1 + width * 4
        for i in range(len(row)):
            a, b, c = row[i - 4] if i >= 4 else 0, previous[i], previous[i - 4] if i >= 4 else 0
            p = a + b - c
            nearest = min((a, b, c), key=lambda q: abs(p - q))
            if filter_type not in range(5):
                raise ValueError("Unknown PNG filter")
            row[i] = (row[i] + (0, a, b, (a + b) // 2, nearest)[filter_type]) & 255
        pixels.extend(tuple(row[i:i + 4]) for i in range(0, len(row), 4))
        previous = row
    if len(set(pixels)) != 1 or pixels[0][3] != 255:
        raise ValueError("Non-solid or transparent texture: add UV/material support before converting")
    return pixels[0]


def clip_plane(poly, axis, edge, greater):
    result = []
    for a, b in zip(poly, poly[1:] + poly[:1]):
        inside_a = a[axis] >= edge if greater else a[axis] <= edge
        inside_b = b[axis] >= edge if greater else b[axis] <= edge
        if inside_a:
            result.append(a)
        if inside_a != inside_b:
            t = (edge - a[axis]) / (b[axis] - a[axis])
            result.append(tuple(a[i] + (b[i] - a[i]) * t for i in range(3)))
    return result


def clip_cell(poly, cell):
    for axis in range(3):
        poly = clip_plane(poly, axis, cell[axis], True)
        poly = clip_plane(poly, axis, cell[axis] + 1, False)
        if len(poly) < 3:
            return []
    return poly


def cross(a, b, c):
    u, v = [b[i] - a[i] for i in range(3)], [c[i] - a[i] for i in range(3)]
    return (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])


def area(tri):
    return math.sqrt(sum(x * x for x in cross(*tri))) / 2


def triangulate(poly):
    """Ear clipping also preserves the concave quads around the carved diamond hole."""
    if len(poly) == 3:
        return [poly]
    normal = [sum((a[(i + 1) % 3] - b[(i + 1) % 3]) * (a[(i + 2) % 3] + b[(i + 2) % 3])
                  for a, b in zip(poly, poly[1:] + poly[:1])) for i in range(3)]
    axis = max(range(3), key=lambda i: abs(normal[i]))
    projected = [tuple(p[i] for i in range(3) if i != axis) for p in poly]

    def turn(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])

    signed_area = sum(a[0] * b[1] - a[1] * b[0] for a, b in zip(projected, projected[1:] + projected[:1]))
    if abs(signed_area) < EPS:
        # Degenerate faces are counted/discarded later, never promoted to renderable geometry.
        if all(area([poly[0], poly[i], poly[i + 1]]) <= EPS for i in range(1, len(poly) - 1)):
            return [[poly[0], poly[i], poly[i + 1]] for i in range(1, len(poly) - 1)]
        raise ValueError("Self-crossing mesh polygon")
    sign = 1 if signed_area > 0 else -1
    for i in (1, 0, 2, 3):
        ear = [(i - 1) % 4, i, (i + 1) % 4]
        a, b, c = [projected[j] for j in ear]
        if turn(a, b, c) * sign <= EPS:
            continue
        other = (i + 2) % 4
        point = projected[other]
        if all(turn(u, v, point) * sign >= -EPS for u, v in [(a, b), (b, c), (c, a)]):
            continue
        remainder = [j for j in range(4) if j != i]
        return [[poly[j] for j in ear], [poly[j] for j in remainder]]
    raise ValueError("Cannot triangulate mesh polygon without losing its boundary")


def cell_fragments(tri):
    ranges = [range(math.floor(min(p[i] for p in tri)), math.floor(max(p[i] for p in tri)) + 1) for i in range(3)]
    for cell in itertools.product(*ranges):
        clipped = clip_cell(list(tri), cell)
        for i in range(1, len(clipped) - 1):
            fragment = [clipped[0], clipped[i], clipped[i + 1]]
            if area(fragment) > EPS:
                yield cell, fragment


def height_at(tri, x, z):
    a, b, c = tri
    denominator = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2])
    if abs(denominator) < EPS:
        return None
    u = ((b[2] - c[2]) * (x - c[0]) + (c[0] - b[0]) * (z - c[2])) / denominator
    v = ((c[2] - a[2]) * (x - c[0]) + (a[0] - c[0]) * (z - c[2])) / denominator
    if min(u, v, 1 - u - v) < -EPS:
        return None
    return u * a[1] + v * b[1] + (1 - u - v) * c[1]


def bounds(points):
    return [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]


def local_box(box, cell):
    low = [max(box[i], cell[i]) - cell[i] for i in range(3)]
    high = [min(box[i + 3], cell[i] + 1) - cell[i] for i in range(3)]
    return [round(v, 7) for v in low + high] if all(high[i] - low[i] > EPS for i in range(3)) else None


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def generate(source_bytes):
    source = json.loads(source_bytes)
    if source["meta"]["model_format"] != "free":
        raise ValueError("Expected the approved Generic Model")
    groups = {g["uuid"]: g for g in source["groups"]}
    paths = {}

    def walk(nodes, path=()):
        for node in nodes:
            if isinstance(node, str):
                if node in paths:
                    raise ValueError("Mesh occurs twice in the outliner")
                paths[node] = path
            else:
                group = groups[node["uuid"]]
                if any(group.get("rotation", [0, 0, 0])) or any(group.get("origin", [0, 0, 0])):
                    raise ValueError("Group transforms require explicit converter support")
                if not group.get("visibility", True) or not group.get("export", True):
                    raise ValueError("Hidden groups require an explicit export decision")
                walk(node["children"], path + (group["name"],))
    walk(source["outliner"])
    outputs, materials = {}, []
    for texture in source["textures"]:
        name = texture["name"]
        if not name.replace("_", "").isalnum():
            raise ValueError("Unsafe material name")
        image = base64.b64decode(texture["source"].split(",", 1)[1])
        color = solid_png(image)
        png = ASSETS / f"textures/block/mushroom_bells_preview/{name}.png"
        outputs[str(png)] = image  # Byte-for-byte original embedded texture.
        glow = texture.get("render_mode") == "emissive"
        if texture.get("render_mode", "default") not in ("default", "emissive"):
            raise ValueError("Unsupported material render mode")
        materials.append({"name": name, "rgba": color, "emissive": glow,
                          "double_sided": texture.get("render_sides") == "double"})
    cells, ground, solids, meshes, all_points = defaultdict(list), [], [], [], []
    source_area, fragment_area, degenerates = 0, 0, 0
    for element in source["elements"]:
        if not element.get("visibility", True) or not element.get("export", True):
            raise ValueError("Hidden meshes require an explicit export decision")
        if "vertices" not in element or any(element.get("rotation", [0, 0, 0])) or any(element.get("origin", [0, 0, 0])):
            raise ValueError("Only untransformed mesh elements are supported")
        path = paths[element["uuid"]]
        vertices = {key: tuple(point[i] * SCALE + (0.5 if i != 1 else 0) for i in range(3))
                    for key, point in element["vertices"].items()}
        all_points.extend(vertices.values())
        meshes.append({"name": element["name"], "uuid": element["uuid"], "groups": path,
                       "bounds": bounds(list(vertices.values()))})
        if path[-1] in ("Rose_Stem", "Teal_Stem", "Amber_Stem", "Stone_Body"):
            solids.append(bounds(list(vertices.values())))
        for face in element["faces"].values():
            texture = face.get("texture")
            if not isinstance(texture, int) or isinstance(texture, bool) or texture not in range(len(materials)):
                raise ValueError("Missing/unresolved mesh material")
            polygon = [vertices[key] for key in face["vertices"]]
            if len(polygon) not in (3, 4):
                raise ValueError("Only triangle/quad faces are supported")
            for tri in triangulate(polygon):
                if area(tri) <= EPS:
                    degenerates += 1
                    continue
                source_area += area(tri)
                if element["name"] in ("Soft_earth_diorama_edge_Refined", "Moss_clearing_surface_Refined"):
                    ground.append(tri)
                for cell, fragment in cell_fragments(tri):
                    fragment_area += area(fragment)
                    cells[cell].append((texture, fragment, path[1], element["name"]))
    if abs(source_area - fragment_area) > source_area * 1e-7:
        raise ValueError("Clipping lost or duplicated source surface area")
    mtl = []
    for material in materials:
        name = material["name"]
        mtl.extend([f"newmtl {name}", "Kd 1 1 1", "forge_TintIndex -1",
                    "Ka 1 1 1" if material["emissive"] else "Ka 0 0 0",
                    f"map_Kd dudunka:block/mushroom_bells_preview/{name}", ""])
    outputs[str(MODELS / "palette.mtl")] = ("\n".join(mtl) + "\n").encode()
    parts, variants = [], {}
    for index, (cell, triangles) in enumerate(sorted(cells.items())):
        lines = ["# Generated from approved bbmodel; coordinates are local world-block units.",
                 "mtllib palette.mtl", "vt 0.5 0.5"]
        vertex_index, normal_index, last_material, baked_faces = 0, 0, None, 0
        for texture, tri, _, name in triangles:
            material = materials[texture]
            if texture != last_material:
                lines.append(f"usemtl {material['name']}")
                last_material = texture
            lines.append(f"# {name}")
            for point in tri:
                local = [min(1, max(0, point[i] - cell[i])) for i in range(3)]
                lines.append("v " + " ".join(f"{v:.8f}" for v in local))
            normal = cross(*tri)
            length = math.sqrt(sum(v * v for v in normal))
            normal = [v / length for v in normal]
            lines.append("vn " + " ".join(f"{v:.8f}" for v in normal))
            normal_index += 1
            vi = [vertex_index + 1, vertex_index + 2, vertex_index + 3]
            lines.append("f " + " ".join(f"{v}/1/{normal_index}" for v in vi))
            baked_faces += 1
            if material["double_sided"]:
                lines.append("vn " + " ".join(f"{-v:.8f}" for v in normal))
                normal_index += 1
                lines.append("f " + " ".join(f"{v}/1/{normal_index}" for v in reversed(vi)))
                baked_faces += 1
            vertex_index += 3
        filename = f"cell_{index:03d}"
        outputs[str(MODELS / f"{filename}.obj")] = ("\n".join(lines) + "\n").encode()
        outputs[str(MODELS / f"{filename}.json")] = json_bytes({
            "loader": "forge:obj", "model": f"dudunka:models/block/mushroom_bells_preview/{filename}.obj",
            "automatic_culling": False, "shade_quads": True, "emissive_ambient": True,
            "ambientocclusion": False, "render_type": "minecraft:solid",
            "textures": {"particle": "dudunka:block/mushroom_bells_preview/concept_moss"}})
        variants[f"part={index}"] = {"model": f"dudunka:block/mushroom_bells_preview/{filename}"}
        boxes = []
        # Four-by-four support columns approximate the ground; no full-cell invisible walls.
        for sx, sz in itertools.product(range(4), repeat=2):
            x, z = cell[0] + (sx + .5) / 4, cell[2] + (sz + .5) / 4
            heights = [y for tri in ground if (y := height_at(tri, x, z)) is not None]
            if heights:
                box = local_box([cell[0] + sx / 4, min(p[1] for p in all_points), cell[2] + sz / 4,
                                 cell[0] + (sx + 1) / 4, max(heights), cell[2] + (sz + 1) / 4], cell)
                if box:
                    boxes.append(box)
        for solid in solids:
            box = local_box(solid, cell)
            if box:
                boxes.append(box)
        outline = bounds([tuple(p[i] - cell[i] for i in range(3)) for _, tri, _, _ in triangles for p in tri])
        # Flat ornaments still need a selectable outline, confined to this cell.
        for i in range(3):
            if outline[i + 3] - outline[i] < .01:
                outline[i] = max(0, outline[i] - .005)
                outline[i + 3] = min(1, outline[i + 3] + .005)
        parts.append({"part": index, "offset": cell, "outline": outline, "collision": boxes,
                      "light": 7 if any(materials[t]["emissive"] for t, _, _, _ in triangles) else 0,
                      "groups": sorted({g for _, _, g, _ in triangles}), "faces": baked_faces})
    outputs[str(BLOCKSTATE)] = json_bytes({"variants": variants})
    manifest = {"schema": 1, "source": str(SOURCE), "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
                "blocks_per_source_unit": SCALE, "source_meshes": len(meshes), "source_groups": len(groups),
                "source_textures": len(materials), "world_bounds": bounds(all_points),
                "source_surface_area": source_area, "clipped_surface_area": fragment_area,
                "discarded_degenerate_triangles": degenerates, "materials": materials, "meshes": meshes, "parts": parts,
                "files": sorted([*outputs, str(LAYOUT)])}
    outputs[str(LAYOUT)] = json_bytes(manifest)
    return outputs, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Read-only comparison of every generated asset")
    parser.add_argument("--styled", action="store_true", help="Generate/check the separate Minecraft-fantasy derivative")
    args = parser.parse_args()
    if args.styled:
        from generate_mushroom_bells_styled import main as styled_main
        styled_main(["--check"] if args.check else [])
        return
    outputs, manifest = generate((ROOT / SOURCE).read_bytes())
    if (ROOT / LAYOUT).exists():
        stale = set(json.loads((ROOT / LAYOUT).read_text())["files"]) - outputs.keys()
        if stale:
            raise SystemExit(f"Stale generated assets require explicit review/removal: {sorted(stale)}")
    differences = []
    for name, data in outputs.items():
        path = ROOT / name
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                differences.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if differences:
        raise SystemExit("Out-of-date assets:\n" + "\n".join(differences))
    print(f"{'Checked' if args.check else 'Generated'} {len(outputs)} assets; "
          f"{len(manifest['parts'])} static cells; {sum(p['faces'] for p in manifest['parts'])} baked faces; "
          f"source SHA256 {manifest['source_sha256']}")


if __name__ == "__main__":
    main()
