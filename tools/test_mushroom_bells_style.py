"""Focused source, art, topology, UV and resource checks; no Gradle/game process."""
import hashlib
import json
import math
import unittest
from collections import Counter
from pathlib import Path
from generate_mushroom_bells_preview import ROOT, SOURCE, SCALE, area, cross, triangulate
from generate_mushroom_bells_styled import ATLAS, MODELS, LAYOUT, STYLED, generate, fragments, polygon_area
from mushroom_bells_art import styled_scene, PALETTES, OMIT, orient_faces

APPROVED_SHA = "6bb7545660bdbb9090624adedc4ea9997ee8e649331fb6405300657875e50551"


class StyleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (ROOT / SOURCE).read_bytes()
        cls.original = json.loads(cls.source)
        cls.scene, cls.png, cls.image, cls.meta = styled_scene(cls.source)
        cls.outputs, cls.manifest = generate(cls.source)

    def test_approved_source_preserved_and_separate_copy_is_reproducible(self):
        self.assertEqual(hashlib.sha256(self.source).hexdigest(), APPROVED_SHA)
        self.assertEqual((ROOT / SOURCE).read_bytes(), self.source)
        repeated, _ = generate(self.source)
        self.assertEqual(self.outputs, repeated)
        self.assertNotIn(str(SOURCE), self.outputs)
        self.assertEqual(hashlib.sha256(self.outputs[str(STYLED)]).hexdigest(), self.manifest["styled_sha256"])

    def test_every_approved_object_except_the_ground_disk_is_retained(self):
        original = {e["uuid"]: e for e in self.original["elements"]}
        styled = {e["uuid"]: e for e in self.scene["elements"]}
        self.assertEqual(set(original) - set(styled), {e["uuid"] for e in original.values() if e["name"] in OMIT})
        self.assertEqual(len(styled), 162)
        self.assertEqual(len(self.scene["groups"]), 23)
        self.assertEqual(len([e for e in styled.values() if "sculpted_cap" in e["name"]]), 3)
        self.assertTrue(any(e["name"] == "Carving_panel_open_diamond" for e in styled.values()))

    def test_compact_cap_silhouettes_and_hanging_contours_survive(self):
        original = {e["name"]: e for e in self.original["elements"]}
        for e in self.scene["elements"]:
            if e["name"].endswith("_sculpted_cap"):
                a, b = list(e["vertices"].values()), list(original[e["name"]]["vertices"].values())
                for axis in range(3):
                    for extreme in (min, max):
                        self.assertLess(abs(extreme(p[axis] for p in a) - extreme(p[axis] for p in b)) * SCALE, .12)
                levels = Counter(round(p[1], 5) for p in a)
                horizontal_steps = 0
                for f in e["faces"].values():
                    points = [e["vertices"][k] for k in f["vertices"]]
                    if len(points) == 4 and len({p[1] for p in points}) == 1:
                        horizontal_steps += 1
                self.assertGreater(horizontal_steps, 0)
            if "sweeping_bell_gill" in e["name"]:
                a, b = e["vertices"].values(), original[e["name"]]["vertices"].values()
                self.assertAlmostEqual(min(p[1] for p in a), min(p[1] for p in b), places=5)
                self.assertEqual(len(e["faces"]), 4)

    def test_closed_solids_have_consistent_outward_winding(self):
        for e in self.scene["elements"]:
            if not self.meta[e["uuid"]]["closed"]:
                continue
            edges = Counter(); volume = 0
            for f in e["faces"].values():
                keys = f["vertices"]
                for a, b in zip(keys, keys[1:] + keys[:1]):
                    edges[(a, b)] += 1
                for tri in triangulate([e["vertices"][key] for key in keys]):
                    n = cross([0, 0, 0], tri[1], tri[2]); volume += sum(tri[0][i] * n[i] for i in range(3)) / 6
            self.assertTrue(all(count == 1 and edges[(b, a)] == 1 for (a, b), count in edges.items()), e["name"])
            self.assertGreater(volume, 0, e["name"])

    def test_pixel_art_is_opaque_palette_limited_and_not_flat(self):
        regions = self.scene["dudunka_style"]["atlas_regions"]
        for name, (x, y, size) in regions.items():
            used = {self.image[py][px] for py in range(y, y + size) for px in range(x, x + size)}
            self.assertGreaterEqual(len(used), 5, name)
            self.assertLessEqual(len(used), 8, name)
            self.assertTrue(all(c[3] == 255 for c in used))
        self.assertEqual(len(self.scene["textures"]), 1)
        self.assertEqual(self.outputs[str(ATLAS)], self.png)
        self.assertFalse(json.loads(self.outputs[str(ATLAS) + ".mcmeta"])["texture"]["blur"])

    def test_uvs_are_real_and_survive_cell_clipping(self):
        quad = [(-.4, .4, -.2, .1, .2), (1.6, .4, -.2, .3, .2),
                (1.6, .4, 1.8, .3, .4), (-.4, .4, 1.8, .1, .4)]
        pieces = list(fragments(quad))
        self.assertAlmostEqual(sum(polygon_area(p) for _, p in pieces), polygon_area(quad), places=8)
        for cell, poly in pieces:
            self.assertIn(len(poly), (3, 4))
            for x, y, z, u, v in poly:
                self.assertAlmostEqual(u, .1 + (x + .4) * .1)
                self.assertAlmostEqual(v, .2 + (z + .2) * .1)
                self.assertTrue(all(cell[i] - 1e-9 <= p <= cell[i] + 1 + 1e-9 for i, p in enumerate((x, y, z))))
        for e in self.scene["elements"]:
            for f in e["faces"].values():
                self.assertEqual(set(f["uv"]), set(f["vertices"]))
                self.assertTrue(all(0 < u < 256 and 0 < v < 128 for u, v in f["uv"].values()))

    def test_planar_quad_does_not_expand_to_redundant_triangles(self):
        poly = [(0, .5, 0, .1, .1), (1, .5, 0, .2, .1), (1, .5, 1, .2, .2), (0, .5, 1, .1, .2)]
        self.assertEqual(len(list(fragments(poly))), 1)
        self.assertEqual(len(list(fragments(poly))[0][1]), 4)

    def test_shared_obj_and_parent_visibility_are_complete(self):
        base = json.loads(self.outputs[str(MODELS / "base.json")])
        self.assertEqual(base["loader"], "forge:obj")
        self.assertFalse(any(base["visibility"].values()))
        self.assertEqual(len([p for p in self.outputs if p.endswith(".obj")]), 1)
        for p in self.manifest["parts"]:
            name = f"cell_{p['part']:03d}"
            child = json.loads(self.outputs[str(MODELS / (name + ".json"))])
            self.assertEqual(child["visibility"], {name: True})
            self.assertIn(name, base["visibility"])
            self.assertEqual(child["parent"], "dudunka:block/mushroom_bells_styled/base")

    def test_measured_reduction_and_four_real_lights(self):
        metrics = self.manifest["optimization"]
        self.assertLess(metrics["styled_cells"], metrics["original_cells"] * .6)
        self.assertLess(metrics["styled_faces"], metrics["original_faces"] * .3)
        self.assertLess(metrics["styled_resource_files"], metrics["original_resource_files"] * .4)
        self.assertEqual(metrics["real_light_cells"], 4)
        self.assertTrue(all(0 <= p["light"] <= 13 for p in self.manifest["parts"]))
        self.assertEqual({(p["part"], tuple(p["offset"])): p["light"]
                          for p in self.manifest["parts"] if p["light"]},
                         {(33, (-3, 1, -1)): 10, (86, (0, 1, 0)): 13,
                          (121, (2, 1, -2)): 10, (124, (2, 1, 2)): 10})
        self.assertAlmostEqual(self.manifest["surface_area"], self.manifest["clipped_area"], places=5)
        occupied = {tuple(p["offset"]) for p in self.manifest["parts"]}
        self.assertGreaterEqual(len(self.manifest["ground_accents"]), 5)
        self.assertTrue(all(tuple(p) not in occupied and p[1] == 0 for p in self.manifest["ground_accents"]))

    def test_every_generated_resource_matches_disk(self):
        self.assertEqual(sorted(self.outputs), self.manifest["files"])
        for name, data in self.outputs.items():
            self.assertEqual((ROOT / name).read_bytes(), data, name)


if __name__ == "__main__":
    unittest.main()
