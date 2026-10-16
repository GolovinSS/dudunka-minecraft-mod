"""Focused pure geometry tests; no Minecraft world or Gradle process."""
import copy
import json
import unittest
from generate_mushroom_bells_preview import ROOT, SOURCE, SCALE, area, cell_fragments, generate, height_at, triangulate


class ConverterTests(unittest.TestCase):
    def test_concave_carving_quad_keeps_its_hole_instead_of_overlapping_triangles(self):
        polygon = [(0, 0, 0), (2, 0, 0), (2, 2, 0), (1, .5, 0)]
        self.assertAlmostEqual(sum(area(t) for t in triangulate(polygon)), 1.5)
        self.assertAlmostEqual(sum(area(t) for t in triangulate(list(reversed(polygon)))), 1.5)

    def test_clipping_conserves_area_across_negative_cells_and_chunk_edges(self):
        for tri in [[(-1.2, .4, -1.3), (1.7, .4, -.2), (.3, 2.4, 1.2)],
                    [(15.8, -.2, 15.9), (17.2, 1.1, 16.2), (16.1, 2, 17.5)],
                    [(1, 1, 1), (1, 2, 1), (1, 1, 2)]]:
            pieces = list(cell_fragments(tri))
            self.assertAlmostEqual(area(tri), sum(area(p) for _, p in pieces), places=8)
            for cell, fragment in pieces:
                for p in fragment:
                    self.assertTrue(all(cell[i] - 1e-9 <= p[i] <= cell[i] + 1 + 1e-9 for i in range(3)))

    def test_ground_height_interpolation_and_vertical_faces(self):
        tri = [(0, .2, 0), (1, .6, 0), (0, .4, 1)]
        self.assertAlmostEqual(height_at(tri, .25, .25), .35)
        self.assertIsNone(height_at(tri, 2, 2))
        self.assertIsNone(height_at([(0, 0, 0), (0, 1, 0), (0, 1, 1)], 0, .5))

    def test_source_conversion_is_deterministic_and_fits_native_footprint(self):
        data = (ROOT / SOURCE).read_bytes()
        outputs, manifest = generate(data)
        repeated, _ = generate(data)
        self.assertEqual(outputs, repeated)
        self.assertEqual(manifest["source_meshes"], 164)
        self.assertEqual(manifest["source_groups"], 23)
        self.assertEqual(manifest["source_textures"], 18)
        self.assertEqual(SCALE, .375)
        for part in manifest["parts"]:
            x, y, z = part["offset"]
            self.assertTrue(-6 <= x <= 6 and -6 <= z <= 6 and -1 <= y <= 4)
            for box in part["collision"]:
                self.assertTrue(all(0 <= v <= 1 for v in box))
                self.assertTrue(all(box[i] < box[i + 3] for i in range(3)))

    def test_unsupported_artist_edits_fail_instead_of_silently_losing_detail(self):
        source = json.loads((ROOT / SOURCE).read_bytes())
        for field, value in [("rotation", [0, 15, 0]), ("visibility", False)]:
            changed = copy.deepcopy(source)
            changed["elements"][0][field] = value
            with self.assertRaises(ValueError):
                generate(json.dumps(changed).encode())


if __name__ == "__main__":
    unittest.main()
