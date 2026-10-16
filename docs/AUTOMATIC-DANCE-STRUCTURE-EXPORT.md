# Stage 4.3a.5b — automatic native structure export

Stage 4.3a.5a clearing: **visually approved**, build/focused tests **PASS**, as
reported by the user. Release 0.28.0-alpha export validation (2026-10-17, Moscow):
**PASS** — generation 2/2, build/model/resource checks, exact JAR bytes, and
packaged-resource round-trip 1/1. The native NBT is now generated and included
as a mod resource. Manual acceptance of placement from this NBT: **NOT RUN**.
See [release evidence](VALIDATION-0.28.0.md) for the current release status.

Production worldgen integration remains deferred. Follow
[development workflow](DEVELOPMENT-WORKFLOW.md) with Minecraft 1.20.1 /
Forge 47.4.13 / Java 17. Codex ran this release's commands with explicit user
authorization; the normal external-validation workflow remains unchanged.

## One command, no graphical client or Structure Block

From the repository root:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 python3 tools/export_dance_structure.py
```

This launches **two separate, fresh headless GameTest worlds**, with the ordinary
build/check/model verification between them. It does not launch `runClient` or
touch an existing world. `tools/test.sh` keeps its usual offline default; set
`DUDUNKA_TEST_ONLINE=1` only if dependencies need fetching. Allow the operation
to finish before starting another build/test/client run.

The output resource is:

```text
src/main/resources/data/dudunka/structures/previews/jumper_dance_43a5a.nbt
```

Template ID: **`dudunka:previews/jumper_dance_43a5a`**. Minecraft 1.20.1 uses
`data/<namespace>/structures/<path>.nbt` (plural `structures`). The template size
is **25 × 6 × 25**, including the authored ground layer. The template's placement
position denotes its **minimum corner**, not the activation stone.

| Landmark | Template-local coordinate |
| --- | --- |
| Center | `(12, 1, 12)` |
| NORTH bloom 0 | `(12, 1, 7)` |
| EAST bloom 1 | `(17, 1, 12)` |
| SOUTH bloom 2 | `(12, 1, 17)` |
| WEST bloom 3 | `(7, 1, 12)` |

## How export works

1. `ValeDanceStructureExportTests` executes the existing
   `ValeDanceClearingPreview.plan(root)` in the Minecraft runtime. There is no
   independent layout, Java-source parser, random seed variant or changed design.
   Its deterministic evaluated block states are placed in an isolated high-altitude
   fixture with unrelated diamond-block sentinels between authored positions.
2. The small `NativeStructureExport.capture` helper calls native
   `StructureTemplate.fillFromWorld`, with entity capture disabled. Minecraft
   serializes IDs, block properties and any authored block-entity data.
3. The authored-position mask removes unplanned cells from the native block list.
   Native reload/save compacts the palette. **Untouched cells have no block entry**:
   this is a sparse template, equivalent to capturing structure-void cells and
   omitting them. The result contains neither air nor structure-void blocks, and
   captures no unrelated fixture terrain. The size remains 25 × 6 × 25.
4. Native `NbtIo` writes compressed NBT; the test reads those bytes, loads them
   through `StructureTemplateManager.readStructure`, and places the result in a
   second region. Every authored state and every untouched cell is compared.
   A second independent generator/capture invocation must produce identical NBT
   **and identical compressed bytes**. Bounds drift fails instead of truncating.
5. Only a completed passing generation suite permits the Python wrapper to
   install the validated candidate. It then builds the mod, checks the exact NBT
   entry/bytes in `build/libs/dudunka-<current-version>.jar`, and launches a fresh
   resource-validation suite. This resolves the template by native ID from the
   server resource manager, forbids a generated-world template shadow, compares
   the installed bytes and repeats native placement validation.

Exact placement tests use `setKnownShape(true)`, no rotation/mirror, entity
inclusion disabled, liquid retention disabled, and block flags 18. This preserves
the approved stairs, walls, mushroom faces and leaf properties without neighbor
shape reconciliation. Lighting is preserved through the emitting block IDs/states;
native structure NBT does not store light-engine arrays.

`NativeStructureExport` is reusable for another small loaded-world sketch by
supplying its bounds and evaluated authored-state map. It reads the world without
changing it. It rejects out-of-bounds blocks, unloaded chunks, missing authored
block entities and authored air/structure void. It is a development/test utility,
with no normal-server event hook, new registry entry or gameplay command.

## Reports and overwrite protection

Candidates and reports are retained under:

```text
build/reports/dance-structure-export/run-*/
  jumper_dance_43a5a.nbt   # candidate, written by Minecraft after round-trip checks
  report.json             # counts, dimensions, repeat determinism, SHA-256
  validated.json          # only after resource round-trip and JAR checks also pass
```

The ordinary wrapper also retains per-phase logs/fresh-world paths under
`build/reports/dudunka-tests/`. The runtime reports `DANCE_STRUCTURE_ROUND_TRIP`
with authored, compared and untouched counts, zero mismatches, zero entities and
zero authored air. All **3,750** volume cells are checked: `authored + untouched`.
Release export measured **477 authored/compared** and **3,273 untouched** cells,
with zero mismatches, entities and authored air. Repeated native exports produced
identical compressed bytes: SHA-256
`2437a9df4f61e1a62c3658cc7ab62c6768eee1eb8d72f44d8e814cc5ac02b4fd`.
Report: `build/reports/dance-structure-export/run-4r2qtc3d/validated.json`.

If the target is absent, it is installed. If identical, it is left untouched.
If it differs, the operation **stops and preserves it**, retaining the candidate
for review. To regenerate without installing anything:

```bash
python3 tools/export_dance_structure.py --candidate-only
```

After reviewing a changed candidate, explicitly authorize replacement of the
current exact file with its SHA-256 (printed by the refusal):

```bash
python3 tools/export_dance_structure.py --replace-sha256 <current-target-sha256>
```

This checks the old hash, keeps `previous-resource.nbt` in the new report directory,
and replaces the resource atomically. There is no unconditional force option or
automatic replacement based on a filename. If later build/resource validation
fails, the installed candidate and reports remain for investigation; no final
PASS or `validated.json` is produced. Nothing is committed or published.

## Use as a Creative starting point; preserve later manual versions

After successful generation/build, restart a development client or install the
built mod in a **new disposable Creative world**. For a default structure-free
Superflat world, the following loads the NBT without a Structure Block GUI:

```mcfunction
/tp @s 145 -60 128
/place template dudunka:previews/jumper_dance_43a5a 116 -61 116
```

Travel there first so all affected chunks are loaded. This places the activation
stone at `(128,-60,128)`. Use an empty, dry, flat supported area: vanilla
`/place template` has no mod terrain/build protection and sparse templates do not
clear obstacles from the gaps. Vanilla placement may recompute neighboring wall,
stair or leaf properties; the NBT still contains the exact approved properties.
The existing guarded Java `dance_preview place` command remains available for
the original composition with exact placement flags.

Edit normally in Creative and **keep a backup of the edited world**. Running the
canonical exporter again evaluates Java; it does not read those manual edits.
An edited world is not recoverable from the Java source or the canonical NBT.
Automatic capture of an arbitrarily edited world is a separate future operation;
this stage exports the approved Java design only.

If an edited `.nbt` is supplied later, keep the untouched original and archive
the edited file under `docs/structure-prototypes/jumper_dance/` with its author,
versions, SHA-256, size and center/flower offsets. Import it under a **new template
ID**, such as `dudunka:previews/jumper_dance_manual_v1`, by using a correspondingly
named file under `data/dudunka/structures/previews/` after review. The canonical
exporter only manages `jumper_dance_43a5a.nbt` and never touches other IDs.
Compare and validate the manual template separately; it is expected to differ
from the canonical layout, so canonical equality checks cannot approve it.
Do not use replacement of the canonical resource as an implicit import workflow.

## Validation commands and coverage

The complete automated export/validation operation is the first command above.
To validate a resource that already exists, without generating/replacing it:

```bash
python3 tools/export_dance_structure.py --verify-only
```

Individual phases, for focused investigation:

```bash
DUDUNKA_TEST_STRUCTURE_EXPORT=generate ./tools/test.sh gametest
./tools/test.sh build
DUDUNKA_TEST_STRUCTURE_EXPORT=resource ./tools/test.sh gametest
python3 tools/test_export_dance_structure.py
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
```

The isolated `generate` suite has **two** tests: canonical sparse round-trip/
repeat determinism/bounds checks/entity exclusion, and reusable native capture
of a named chest with item contents. The `resource` suite has **one** test and
fails if the template is missing. Running the generation phase alone without the
Python wrapper validates in memory but does not install a project file. Python
unit tests cover new/identical installs, manual-edit protection, wrong hashes and
explicit replacement with backup; they do not simulate Minecraft serialization.
The music suite retains the existing production puzzle/preview regression tests.

The two export test namespaces are **explicit opt-in scopes**, separate from
ordinary full/music runs. The script uses `tools/test.sh`, with its fresh worlds
and exact completed-suite counts (2 for generation, 1 for resource validation),
and requires a matching candidate/report before
installation. It never launches a valuable saved world.

Static whitespace and Python file-install safety tests: **PASS**, **3/3**.
Release Gradle/build and export/GameTests: **PASS**; exact run IDs and artifact
details are recorded in [VALIDATION-0.28.0.md](VALIDATION-0.28.0.md).
Minecraft/BMC4 review and manual NBT placement acceptance: **NOT RUN**.

## Isolation and native template limits

The packaged preview resource is inert until explicitly placed. No production
structure JSON, generator, quest/music code, reward, saved progress, family
teleport or approved layout changes. The actual puzzle remains
**NORTH → NORTH → EAST → SOUTH → SOUTH → WEST**. A template is block geometry;
it contains no `ValeAdventurePiece`, native structure start, quest binding or
progress. Existing Dudunka plants require the mod; their IDs/properties remain
unchanged. Placement in nonempty terrain still overwrites authored coordinates,
although omitted cells preserve their original blocks.

## Exact stage files

- `src/main/java/io/github/golovinss/dudunka/test/NativeStructureExport.java` (new)
- `src/main/java/io/github/golovinss/dudunka/test/ValeDanceStructureExportTests.java` (new)
- `src/main/java/io/github/golovinss/dudunka/test/ValeDanceStructureResourceTests.java` (new)
- `src/main/resources/data/dudunka_structure_export/structures/empty.nbt` (copy of existing empty test fixture)
- `src/main/resources/data/dudunka_structure_resource/structures/empty.nbt` (copy of existing empty test fixture)
- `tools/test.sh` (two opt-in test scopes)
- `tools/export_dance_structure.py` (new headless orchestration)
- `tools/test_export_dance_structure.py` (new file-install tests)
- `docs/AUTOMATIC-DANCE-STRUCTURE-EXPORT.md` (new, this guide)
- `docs/VANILLA-BLOCK-DANCE-CLEARING-PROTOTYPE.md` (link to this stage and user-reported approval)

The generated clearing NBT is now present at the resource path above. It remains
an explicitly placed preview; generation did not integrate it into production
worldgen. The release preserves the accumulated source work.
