#!/usr/bin/env python3
"""Optional nearest-neighbor software art review, NOT Minecraft/shader acceptance.

Uses the same art recipe, polygon winding and UVs as the styled converter. Pure
Python rasterization; no OpenGL, image-editing package or game launch required.
"""
import math
from pathlib import Path
from generate_mushroom_bells_preview import ROOT, SOURCE, SCALE, cross, triangulate
from generate_mushroom_bells_styled import lighting
from mushroom_bells_art import styled_scene, png_bytes, WIDTH, HEIGHT


def main():
    scene, _, atlas, metadata = styled_scene((ROOT / SOURCE).read_bytes())
    width, height, zoom = 960, 720, 63
    def unit(v):
        n = math.sqrt(sum(x * x for x in v)); return [x / n for x in v]
    view = unit([-1.05, .85, 1.6]); right = unit([view[2], 0, -view[0]]); up = unit(cross([0, 0, 0], view, right))
    sun = unit([-.5, 1, .7])
    pixels = [[(31, 43, 46, 255) for _ in range(width)] for _ in range(height)]
    depth = [[-math.inf] * width for _ in range(height)]
    def project(p):
        centered = [p[0] - .5, p[1] - 1.35, p[2] - .5]
        return (width / 2 + sum(centered[i] * right[i] for i in range(3)) * zoom,
                height / 2 - sum(centered[i] * up[i] for i in range(3)) * zoom,
                sum(centered[i] * view[i] for i in range(3)))
    def draw_triangle(poly, bright, double=False):
        n = unit(cross(*[p[:3] for p in poly]))
        if sum(n[i] * view[i] for i in range(3)) <= 0:
            if not double:
                return
            n = [-v for v in n]
        shade = max(bright, .45 + .55 * max(0, sum(n[i] * sun[i] for i in range(3))))
        p = [project(v) for v in poly]
        def edge(a, b, x, y):
            return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0])
        denom = edge(p[0], p[1], p[2][0], p[2][1])
        if abs(denom) < 1e-8:
            return
        for y in range(max(0, math.floor(min(v[1] for v in p))), min(height, math.ceil(max(v[1] for v in p)))):
            for x in range(max(0, math.floor(min(v[0] for v in p))), min(width, math.ceil(max(v[0] for v in p)))):
                weights = [edge(p[1], p[2], x + .5, y + .5) / denom,
                           edge(p[2], p[0], x + .5, y + .5) / denom]
                weights.append(1 - sum(weights))
                if min(weights) < -1e-6:
                    continue
                z = sum(weights[i] * p[i][2] for i in range(3))
                if z <= depth[y][x]:
                    continue
                u = sum(weights[i] * poly[i][3] for i in range(3)); v = sum(weights[i] * poly[i][4] for i in range(3))
                color = atlas[min(HEIGHT - 1, max(0, int(v)))][min(WIDTH - 1, max(0, int(u)))]
                pixels[y][x] = tuple(round(c * shade) for c in color[:3]) + (255,); depth[y][x] = z
    # A rectangular existing block ground plane for orientation; not a shipped model/disk.
    regions = scene["dudunka_style"]["atlas_regions"]
    ox, oy, _ = regions["concept_moss"]
    for x in range(-6, 7):
        for z in range(-5, 6):
            tile = [(x, -.01, z, ox + 2, oy + 2), (x, -.01, z + 1, ox + 2, oy + 30),
                    (x + 1, -.01, z + 1, ox + 30, oy + 30), (x + 1, -.01, z, ox + 30, oy + 2)]
            for indices in [(0, 1, 2), (0, 2, 3)]:
                draw_triangle([tile[i] for i in indices], .6)
    for e in scene["elements"]:
        for face in e["faces"].values():
            keys = face["vertices"]; vertices = {k: [e["vertices"][k][0] * SCALE + .5,
                (e["vertices"][k][1] - .7) * SCALE, e["vertices"][k][2] * SCALE + .5, *face["uv"][k]] for k in keys}
            geom = [e["vertices"][k] for k in keys]; mapped = {tuple(e["vertices"][k]): vertices[k] for k in keys}
            bright = {"surface": 0, "warm": .82, "glint": 1}[lighting(e["name"])]
            for tri in triangulate(geom):
                if sum(v * v for v in cross(*tri)) > 1e-14:
                    draw_triangle([mapped[tuple(p)] for p in tri], bright, not metadata[e["uuid"]]["closed"])
    target = ROOT / "docs/blockbench/mushroom_bells_styled_art_preview.png"
    target.write_bytes(png_bytes(pixels))
    print("Wrote software art review (not Minecraft acceptance):", target)


if __name__ == "__main__":
    main()
