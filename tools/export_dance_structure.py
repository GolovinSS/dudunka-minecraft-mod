#!/usr/bin/env python3
"""Run native headless export, install with overwrite protection, then verify the mod resource/JAR."""

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = Path("data/dudunka/structures/previews/jumper_dance_43a5a.nbt")
TARGET = ROOT / "src/main/resources" / RESOURCE


def digest(data):
    return hashlib.sha256(data).hexdigest()


def install(candidate, target, report_dir, replace_sha256=None):
    """Never overwrite different bytes without an explicit hash, preserving a backup on replacement."""
    data = candidate.read_bytes()
    if target.exists():
        previous = target.read_bytes()
        if previous == data:
            return "identical; existing resource retained"
        old_hash = digest(previous)
        if replace_sha256 != old_hash:
            raise RuntimeError(
                f"Existing resource differs and was preserved: {target}\n"
                f"Validated candidate: {candidate}\n"
                f"Review both files; to explicitly replace this version, use --replace-sha256 {old_hash}"
            )
        backup = report_dir / "previous-resource.nbt"
        with backup.open("xb") as output:
            output.write(previous)
        if target.read_bytes() != previous:
            raise RuntimeError("Resource changed during export; refusing replacement")
        # Temp file is created in the same directory for atomic replacement.
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix=".dance-export-", delete=False) as output:
            temp_path = Path(output.name)
            output.write(data)
        try:
            os.replace(temp_path, target)
        finally:
            temp_path.unlink(missing_ok=True)
        return f"explicitly replaced; previous version backed up to {backup}"
    if replace_sha256:
        raise RuntimeError("--replace-sha256 supplied but the existing resource is missing")
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("xb") as output:
        output.write(data)
    return "installed new resource"


def run(mode, phase=None, export_dir=None):
    env = os.environ.copy()
    for name in ("DUDUNKA_TEST_JOURNEY", "DUDUNKA_TEST_LANDMARKS", "DUDUNKA_TEST_MUSIC",
                 "DUDUNKA_TEST_STRUCTURE_EXPORT", "DUDUNKA_DANCE_EXPORT_DIR"):
        env.pop(name, None)
    if phase:
        env["DUDUNKA_TEST_STRUCTURE_EXPORT"] = phase
    if export_dir:
        env["DUDUNKA_DANCE_EXPORT_DIR"] = str(export_dir)
    subprocess.run(["bash", str(ROOT / "tools/test.sh"), mode], cwd=ROOT, env=env, check=True)


def verify_jar(expected):
    props = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    jar = ROOT / "build/libs" / f"{props['mod_id']}-{props['mod_version']}.jar"
    with zipfile.ZipFile(jar) as archive:
        name = RESOURCE.as_posix()
        if archive.namelist().count(name) != 1 or archive.read(name) != expected:
            raise RuntimeError(f"JAR does not contain exactly the validated resource: {jar}")
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-only", action="store_true", help="Generate/round-trip a candidate without installing or packaging it")
    parser.add_argument("--verify-only", action="store_true", help="Build and validate an existing resource against the Java prototype")
    parser.add_argument("--replace-sha256", help="Explicitly replace only an existing resource with this exact SHA-256; keep a backup")
    args = parser.parse_args()
    if args.candidate_only and (args.verify_only or args.replace_sha256) or args.verify_only and args.replace_sha256:
        parser.error("candidate-only, verify-only and replace-sha256 cannot be combined")
    report_root = ROOT / "build/reports/dance-structure-export"
    report_root.mkdir(parents=True, exist_ok=True)
    with (report_root / ".export.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("Another dance export operation is active") from error
        report_dir = Path(tempfile.mkdtemp(prefix="run-", dir=report_root))
        print(f"Export reports/candidate: {report_dir}", flush=True)
        if args.verify_only:
            expected = TARGET.read_bytes()
            report = {"sha256": digest(expected), "operation": "verify-only"}
            export_dir = None
        else:
            run("gametest", "generate", report_dir)
            candidate = report_dir / "jumper_dance_43a5a.nbt"
            expected = candidate.read_bytes()
            report = json.loads((report_dir / "report.json").read_text())
            if (report["size"] != [25, 6, 25] or report["sha256"] != digest(expected)
                    or not 0 < report["authored"] < 3750 or report["compared"] != report["authored"]
                    or report["untouched"] != 3750 - report["authored"] or report["mismatches"] != 0
                    or report["entities"] != 0 or report["air"] != 0 or report["deterministic"] is not True):
                raise RuntimeError("Candidate is missing the complete native validation report")
            print(json.dumps(report, indent=2), flush=True)
            if args.candidate_only:
                print("Candidate validated. Installation, resource lookup and JAR validation NOT RUN.")
                return
            print(install(candidate, TARGET, report_dir, args.replace_sha256), flush=True)
            export_dir = report_dir
        run("build")
        jar = verify_jar(expected)
        run("gametest", "resource", export_dir)
        if TARGET.read_bytes() != expected:
            raise RuntimeError("Installed resource changed during validation; review it before proceeding")
        report.update(resource=str(TARGET), jar=str(jar), resource_round_trip="PASS", jar_bytes="PASS")
        with (report_dir / "validated.json").open("x") as output:
            json.dump(report, output, indent=2)
            output.write("\n")
        print(f"PASS: native sparse template, resource round-trip and identical JAR entry.\n{TARGET}\n{report_dir / 'validated.json'}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        raise SystemExit(f"EXPORT FAILED: {error}\nExisting manual work is never silently overwritten. Inspect the retained reports; no success is claimed.")
