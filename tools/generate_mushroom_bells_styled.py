#!/usr/bin/env python3
"""Generate the Minecraft-fantasy derivative; keep the approved/legacy preview intact."""
import argparse
from collections import defaultdict
import hashlib
import itertools
import json
import math
from pathlib import Path

from generate_mushroom_bells_preview import ROOT, SOURCE, SCALE, EPS, area, bounds, cross, json_bytes, local_box, triangulate
from mushroom_bells_art import WIDTH, HEIGHT, styled_scene, png_bytes

STYLED = Path("docs/blockbench/mushroom_bells_clearing_styled.bbmodel")
ASSETS = Path("src/main/resources/assets/dudunka")
MODELS = ASSETS / "models/block/mushroom_bells_styled"
ATLAS = ASSETS / "textures/block/mushroom_bells_styled/atlas.png"
LAYOUT = Path("src/main/resources/data/dudunka/mushroom_bells_styled/layout.json")
BLOCKSTATE = ASSETS / "blockstates/mushroom_bells_styled_part.json"
BASE_MODEL = "dudunka:block/mushroom_bells_styled/base"
GROUND_Y = .7


def polygon_area(poly):
    return sum(area([poly[0][:3], poly[i][:3], poly[i + 1][:3]]) for i in range(1, len(poly) - 1))


def normal(poly):
    n = next((cross(poly[0][:3], poly[i][:3], poly[i + 1][:3]) for i in range(1, len(poly) - 1)
              if area([poly[0][:3], poly[i][:3], poly[i + 1][:3]]) > EPS), None)
    if n is None:
        return (0, 1, 0)
    length = math.sqrt(sum(v * v for v in n))
    return tuple(v / length for v in n)


def convex_planar(poly):
    if len(poly) == 3:
        return True
    n = normal(poly)
    if any(abs(sum((p[i] - poly[0][i]) * n[i] for i in range(3))) > 1e-5 for p in poly):
        return False
    turns = [sum(cross(poly[i - 1][:3], poly[i][:3], poly[(i + 1) % len(poly)][:3])[k] * n[k] for k in range(3)) for i in range(len(poly))]
    return min(turns) >= -EPS


def clip(poly, axis, edge, greater):
    """Clip position AND UV with the same parameter; no texture seams at cell boundaries."""
    result = []
    for a, b in zip(poly, poly[1:] + poly[:1]):
        ia, ib = (a[axis] >= edge, b[axis] >= edge) if greater else (a[axis] <= edge, b[axis] <= edge)
        if ia:
            result.append(a)
        if ia != ib:
            t = (edge - a[axis]) / (b[axis] - a[axis])
            result.append(tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a))))
    clean = []
    for p in result:
        if not clean or any(abs(p[i] - clean[-1][i]) > EPS for i in range(3)):
            clean.append(p)
    if len(clean) > 1 and all(abs(clean[0][i] - clean[-1][i]) <= EPS for i in range(3)):
        clean.pop()
    return clean if len(clean) >= 3 else []


def small_polygons(poly):
    # A convex clipped n-gon needs fewer baked quads than a triangle fan.
    if len(poly) <= 4:
        if polygon_area(poly) > EPS:
            yield poly
    else:
        i = 1
        while i + 1 < len(poly):
            end = min(i + 3, len(poly))
            part = [poly[0], *poly[i:end]]
            if polygon_area(part) > EPS:
                yield part
            i += 2


def fragments(poly):
    ranges = [range(math.floor(min(p[i] for p in poly)), math.floor(max(p[i] for p in poly)) + 1) for i in range(3)]
    for cell in itertools.product(*ranges):
        clipped = poly
        for axis in range(3):
            clipped = clip(clipped, axis, cell[axis], True)
            clipped = clip(clipped, axis, cell[axis] + 1, False)
            if not clipped:
                break
        if clipped:
            for part in small_polygons(clipped):
                yield cell, part


def lighting(name):
    if name in ("Small_luminous_inlay_inside_recess", "Crown_tip_light_facet") or name.startswith("Floating_warm_mote"):
        return "glint"
    if "clapper" in name and "suspension" not in name or name == "Fine_warm_inlay_ring" or name.startswith("Tone_dot"):
        return "warm"
    return "surface"


def scene_paths(scene):
    groups = {g["uuid"]: g["name"] for g in scene["groups"]}
    paths = {}
    def walk(nodes, path=()):
        for node in nodes:
            if isinstance(node, str):
                paths[node] = path
            else:
                walk(node["children"], path + (groups[node["uuid"]],))
    walk(scene["outliner"])
    return paths


def generate(source_bytes):
    scene, png, image, mesh_meta = styled_scene(source_bytes)
    scene_bytes = json_bytes(scene)
    paths = scene_paths(scene)
    cells, meshes, solids, points = defaultdict(list), [], [], []
    retained_area, clipped_area, input_faces, removed_ground_faces = 0, 0, 0, 0
    reversed_faces_avoided, planar_quads = 0, 0
    for e in scene["elements"]:
        path = paths[e["uuid"]]
        verts = {key: (p[0] * SCALE + .5, (p[1] - GROUND_Y) * SCALE, p[2] * SCALE + .5) for key, p in e["vertices"].items()}
        points.extend(verts.values())
        meshes.append({"name": e["name"], "uuid": e["uuid"], "groups": path, "bounds": bounds(list(verts.values())), "closed": mesh_meta[e["uuid"]]["closed"]})
        if path[-1] in ("Rose_Stem", "Teal_Stem", "Amber_Stem", "Stone_Body"):
            solids.append(bounds(list(verts.values())))
        for face in e["faces"].values():
            input_faces += 1
            keys = face["vertices"]
            poly = [(*verts[k], face["uv"][k][0] / WIDTH, face["uv"][k][1] / HEIGHT) for k in keys]
            if polygon_area(poly) < EPS:
                continue
            # Keep planar convex quads; triangulate only concave or nonplanar source faces.
            if convex_planar(poly):
                polygons = [poly]; planar_quads += len(poly) == 4
            else:
                indexes = {tuple(verts[k]): poly[i] for i, k in enumerate(keys)}
                polygons = [[indexes[tuple(p)] for p in tri] for tri in triangulate([list(verts[k]) for k in keys])]
            double = not mesh_meta[e["uuid"]]["closed"]
            if not double:
                reversed_faces_avoided += len(polygons)
            for polygon in polygons:
                above = clip(polygon, 1, 0, True)
                if not above:
                    removed_ground_faces += 1
                    continue
                # Horizontal downward faces flush with Minecraft ground are buried and unobservable.
                if max(p[1] for p in above) < .025 and normal(above)[1] < -.9:
                    removed_ground_faces += 1
                    continue
                retained_area += polygon_area(above)
                for cell, part in fragments(above):
                    clipped_area += polygon_area(part)
                    cells[cell].append((lighting(e["name"]), part, double, e["name"], path[1]))
    if abs(retained_area - clipped_area) > retained_area * 1e-6:
        raise ValueError("Optimized clipping lost visible surface area")
    if len(cells) > 256 or any(abs(x) > 6 or abs(z) > 6 or y < 0 or y > 4 for x, y, z in cells):
        raise ValueError("Styled scene exceeds the reviewed footprint")
    outputs = {str(STYLED): scene_bytes, str(ATLAS): png, str(ATLAS) + ".mcmeta": json_bytes({"texture": {"blur": False, "clamp": True}})}
    # A reviewable, nearest-neighbor enlarged atlas. Not shipped with the mod.
    enlarged = [[pixel for pixel in row for _ in range(4)] for row in image for _ in range(4)]
    outputs["docs/blockbench/mushroom_bells_style_atlas.png"] = png_bytes(enlarged)
    mtl = []
    for name, ka in [("surface", "0 0 0"), ("warm", "0.4 0.4 0.4"), ("glint", "0.8 0.8 0.8")]:
        mtl.extend([f"newmtl {name}", "Kd 1 1 1", "forge_TintIndex -1", f"Ka {ka}", "map_Kd dudunka:block/mushroom_bells_styled/atlas", ""])
    outputs[str(MODELS / "palette.mtl")] = ("\n".join(mtl) + "\n").encode()
    lines = ["# Shared optimized cells; one OBJ parse, cell-local geometry, interpolated pixel-art UVs.", "mtllib palette.mtl"]
    vi = ti = ni = 0
    parts, variants, default_visibility = [], {}, {}
    for index, (cell, polys) in enumerate(sorted(cells.items())):
        name = f"cell_{index:03d}"; default_visibility[name] = False
        lines.append("g " + name)
        last_material, faces = None, 0
        for material, poly, double, mesh, _ in polys:
            if last_material != material:
                lines.append("usemtl " + material); last_material = material
            lines.append("# " + mesh)
            for p in poly:
                lines.append("v " + " ".join(f"{min(1, max(0, p[i] - cell[i])):.8f}" for i in range(3)))
                lines.append(f"vt {p[3]:.9f} {p[4]:.9f}")
            n = normal(poly); ni += 1
            lines.append("vn " + " ".join(f"{v:.8f}" for v in n))
            count = len(poly)
            lines.append("f " + " ".join(f"{vi + j + 1}/{ti + j + 1}/{ni}" for j in range(count)))
            faces += 1
            if double:
                ni += 1; lines.append("vn " + " ".join(f"{-v:.8f}" for v in n))
                lines.append("f " + " ".join(f"{vi + j + 1}/{ti + j + 1}/{ni}" for j in reversed(range(count))))
                faces += 1
            vi += count; ti += count
        outputs[str(MODELS / f"{name}.json")] = json_bytes({"parent": BASE_MODEL, "visibility": {name: True}})
        variants[f"part={index}"] = {"model": f"dudunka:block/mushroom_bells_styled/{name}"}
        outline = bounds([tuple(p[i] - cell[i] for i in range(3)) for _, poly, _, _, _ in polys for p in poly])
        for i in range(3):
            if outline[i + 3] - outline[i] < .01:
                outline[i], outline[i + 3] = max(0, outline[i] - .005), min(1, outline[i + 3] + .005)
        boxes = [box for solid in solids if (box := local_box(solid, cell)) is not None]
        parts.append({"part": index, "offset": cell, "outline": outline, "collision": boxes, "light": 0,
                      "faces": faces, "groups": sorted({g for _, _, _, _, g in polys}), "meshes": sorted({m for _, _, _, m, _ in polys})})
    # Exactly four bounded real world-light sources, not one lamp for every emissive polygon.
    for name, level in [("Tall_central_prismatic_crown", 13), ("Rose_readable_gold_clapper", 10),
                        ("Teal_readable_gold_clapper", 10), ("Amber_readable_gold_clapper", 10)]:
        choices = [p for p in parts if name in p["meshes"]]
        if not choices:
            raise ValueError("Missing intended light anchor: " + name)
        chosen = min(choices, key=lambda p: (p["offset"][1], p["part"]))
        chosen["light"] = max(chosen["light"], level)
    # Real native moss carpets on empty terrain cells; never replace or flatten the supporting ground.
    ground_accents = []
    candidates = [(-4, 3), (-3, 4), (-4, -2), (-2, -3), (2, -3), (4, -2), (3, 3), (4, 2),
                  (-2, 3), (2, 4), (-4, 1), (4, 1), (-3, -2), (-3, 1), (1, -3), (3, 0), (4, -1)]
    for x, z in candidates:
        if (x, 0, z) not in cells:
            ground_accents.append([x, 0, z])
    outputs[str(MODELS / "scene.obj")] = ("\n".join(lines) + "\n").encode()
    outputs[str(MODELS / "base.json")] = json_bytes({"loader": "forge:obj", "model": "dudunka:models/block/mushroom_bells_styled/scene.obj",
        "automatic_culling": False, "shade_quads": True, "emissive_ambient": True, "ambientocclusion": False,
        "render_type": "minecraft:solid", "textures": {"particle": "dudunka:block/mushroom_bells_styled/atlas"}, "visibility": default_visibility})
    outputs[str(BLOCKSTATE)] = json_bytes({"variants": variants})
    old = json.loads((ROOT / "src/main/resources/data/dudunka/mushroom_bells_preview/layout.json").read_text())
    manifest = {"schema": 1, "recipe": 1, "source": str(SOURCE), "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
                "styled_source": str(STYLED), "styled_sha256": hashlib.sha256(scene_bytes).hexdigest(), "blocks_per_source_unit": SCALE,
                "ground_source_y": GROUND_Y, "world_bounds": bounds([p for p in points if p[1] >= 0]), "meshes": meshes,
                "parts": parts, "ground_accents": ground_accents, "atlas": {"width": WIDTH, "height": HEIGHT, "regions": scene["dudunka_style"]["atlas_regions"]},
                "surface_area": retained_area, "clipped_area": clipped_area,
                "optimization": {"original_cells": len(old["parts"]), "original_faces": sum(p["faces"] for p in old["parts"]),
                    "original_resource_files": len(old["files"]), "original_bytes": sum((ROOT / p).stat().st_size for p in old["files"]),
                    "styled_cells": len(parts), "styled_faces": sum(p["faces"] for p in parts), "input_faces": input_faces,
                    "closed_meshes": scene["dudunka_style"]["closed_meshes"], "reversed_input_faces_avoided": reversed_faces_avoided,
                    "planar_input_quads": planar_quads, "buried_faces_removed": removed_ground_faces,
                    "real_light_cells": sum(p["light"] > 0 for p in parts), "obj_files": 1, "material_count": 3, "texture_images": 1},
                "files": sorted([*outputs, str(LAYOUT)])}
    manifest["optimization"]["styled_resource_files"] = sum(p.startswith("src/main/resources/") for p in manifest["files"])
    outputs[str(LAYOUT)] = json_bytes(manifest)
    return outputs, manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    outputs, manifest = generate((ROOT / SOURCE).read_bytes())
    if (ROOT / LAYOUT).exists():
        previous = json.loads((ROOT / LAYOUT).read_text())
        if not args.check and (ROOT / STYLED).exists() and hashlib.sha256((ROOT / STYLED).read_bytes()).hexdigest() != previous["styled_sha256"]:
            raise SystemExit("The separate styled Blockbench copy has manual edits. Preserve/reconcile them with the art recipe before regenerating.")
        stale = set(previous["files"]) - outputs.keys()
        if stale:
            raise SystemExit("Stale styled derivative paths require explicit review: " + repr(sorted(stale)))
    differences = []
    for name, data in outputs.items():
        p = ROOT / name
        if args.check:
            if not p.is_file() or p.read_bytes() != data:
                differences.append(name)
        else:
            p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
    if differences:
        raise SystemExit("Styled assets out of date:\n" + "\n".join(differences))
    metrics = manifest["optimization"]
    print(("Checked" if args.check else "Generated") + " styled derivative: " + json.dumps(metrics, sort_keys=True))


if __name__ == "__main__":
    main()
