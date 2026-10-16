"""Cheap file-install safety tests; no Minecraft, Gradle or binary template generation."""

from pathlib import Path
import tempfile
import unittest

from export_dance_structure import digest, install


class InstallSafetyTests(unittest.TestCase):
    def test_new_and_identical_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = root / "candidate"
            candidate.write_bytes(b"fixture-candidate-not-NBT")
            target = root / "resources/preview.nbt"
            install(candidate, target, root)
            timestamp = target.stat().st_mtime_ns
            install(candidate, target, root)
            self.assertEqual(target.read_bytes(), candidate.read_bytes())
            self.assertEqual(target.stat().st_mtime_ns, timestamp)

    def test_manual_edit_and_wrong_hash_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate, target = root / "candidate", root / "target"
            candidate.write_bytes(b"new")
            target.write_bytes(b"manual-edit")
            for supplied in (None, digest(b"older-version")):
                with self.assertRaises(RuntimeError):
                    install(candidate, target, root, supplied)
                self.assertEqual(target.read_bytes(), b"manual-edit")
                self.assertFalse((root / "previous-resource.nbt").exists())

    def test_explicit_exact_hash_replacement_keeps_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate, target = root / "candidate", root / "target"
            candidate.write_bytes(b"new")
            target.write_bytes(b"old")
            install(candidate, target, root, digest(b"old"))
            self.assertEqual(target.read_bytes(), b"new")
            self.assertEqual((root / "previous-resource.nbt").read_bytes(), b"old")


if __name__ == "__main__":
    unittest.main()
