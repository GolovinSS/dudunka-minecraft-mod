# Mushroom Vale Stage 2.4a validation — 2026-10-10

Baseline: the complete uncommitted Stage 2.2/2.3/2.4 working tree on `main`
(`1e81828c4268b356fdda16bb7778c65946306a78`). No reset, stash, discard,
commit, push or PR. Minecraft 1.20.1, Forge **47.4.13**, Java 17, target Better
MC BMC4 v55.5. Earlier user-confirmed visual acceptance remains historical;
**Stage 2.4a graphical Minecraft/BMC4 acceptance is pending**.

## Delivered appearance and developer access

Inspected the actual approved `cottages_and_decor.png`, `giant_mushrooms.png`
and `plants_and_flowers.png`. Original cuboid geometry and nine 16×16 pixel
textures translate their layered red/cream caps, carved wood, amber lamps,
flower boxes and scholarly details. No image pixels were copied. Twelve new
static block/item registrations include detailed furniture, lanterns, arrows,
planters, books, windows, carved panels, gilled eaves and rounded roof crowns.
Collision/selection boxes are generated from the exact same model cuboids and
rotate with each block. No new runtime dependencies or block entities.

Both existing cottage feature IDs remain; Pufik is small and welcoming, Wise
is larger with exterior shelves, books and a reading nook. Entrances retain
three-wide, three-high vestibules, now with ordinary spruce doors in the
central two blocks and clear side lanes. Interiors stay plain. Eight blueprint
IDs, footprint/height limits and placement attempt frequencies stay stable.
The complete preflight and halo remain; no terrain leveling, tree removal or
background loading. All placed-feature and biome JSON are byte-identical to
the starting tree. NPCs, quests, portals and gameplay are not introduced.

With server JVM `-Ddudunka.devMushroomVale=true` (Gradle
`-PmushroomValeDev=true`), a permission-level-2 player in Vale can run:

```text
/dudunka_dev mushroom_vale cottage pufik X Y Z north
/dudunka_dev mushroom_vale cottage wise X Y Z east
```

Directions: `north`, `east`, `south`, `west`. The explicit floor origin must be
loaded, dry, level and clear across the footprint and halo. Live entities,
including dropped items, reject placement. Commands reuse the production
placer and refuse before writing on unsafe sites. They do not search, prepare
terrain, teleport, change natural frequency or introduce a saved locator.
Exact IntelliJ settings, disposable platform commands, prop placements and
manual checks are in [TESTING.md](TESTING.md#stage-24a-visual-polish-and-explicit-cottage-previews-in-intellij-idea).

## Automated results

- `python3 tools/validate_vale_resources.py`: **PASS** — 271 JSON resources,
  three biomes, 115 recursively referenced worldgen resources, eight bounded
  blueprints, 24 block/item/loot/localization sets and 57 model references;
  acyclic feature ordering. New original models also check cuboid bounds,
  face texture references and UV bounds. Forge remains the codec authority.
- Both generators rerun byte-identically: **PASS**. Original geometry and
  textures can be regenerated without changing vegetation or placement rates.
- `build verifyModels -x downloadAssets`: successful before the full suite
  (**11s**, exit 0); all nine character model trees pass. An initial compile
  caught generic registry inference and an ambiguous null entity overload;
  both were corrected before GameTests. Final build/packaging result below.
  `verifyModels` bakes character models; it is **not** client visual acceptance
  of the new static block models.
- Exactly **one full suite**: `runGameTestServer -x downloadAssets`, **159/161**
  required tests passed, exit **1**, **BUILD FAILED in 1m 43s**. **12/13 Vale
  tests passed**. Every one of the eight blueprints passed all four rotations,
  support/state checks and four unsafe-site scenarios. New preview test failed
  because clearing its first cottage produced dropped spruce-door item
  entities; the command correctly refused its second placement over them.
  The unrelated unchanged `realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes`
  also failed (documented legacy timing failure). Family production/test
  sources were not changed or weakened. The full regression suite is **not green**.
- Focused investigation only, without a second full suite: an initial isolated
  namespace harness selected **0 tests**; its exit 0 is **not counted as a
  passing test**. Corrected two-test harness reproduced the preview refusal and
  identified the occupant as `Spruce Door`. Fixture clearing/restoration now
  uses `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (18), suppressing neighbor-shape
  updates while dismantling test cottages, so no door drops contaminate the
  next fixture. Production placement safety was retained; the refusal message
  now identifies the first occupant and its coordinates.
- Final **focused 2/2 PASS**, exit **0**, **BUILD SUCCESSFUL in 1m 3s**: the new
  preview command test and all-eight-blueprint rotation/unsafe-site test. This
  run includes the final lower rounded crown/taper refinement. It verifies JVM
  opt-in, permission, player, dimension, live-occupant rejection, both cottage
  choices, explicit east orientation, amber window light 9, repeated occupied
  placement refusal and unchanged blocks after refusal. Four rotations and
  unsafe sites still pass for every final blueprint. Full suite was not
  repeated after the fixture correction/resource refinement.

Final blueprint block counts: lantern 5, marker 4, bench 3, picnic 4, flower
bed 9, cozy cluster 10, Pufik 146, Wise 290. More geometry lives inside the
original block models rather than in simplistic full-block arrangements.

All runs use separately guarded fresh directories, retaining old worlds:
`run/vale-stage24a-gametest/`, `run/vale-stage24a-preview-focused/` (zero-test
harness), `run/vale-stage24a-preview-focused-actual/` (diagnostic), and
`run/vale-stage24a-preview-focused-fixed/` (2/2 pass). Evidence/init scripts/logs
are ignored under `build/reports/mushroom-vale-stage24a/`. The focused harness
adds a temporary annotated wrapper and template only through an ignored
Gradle init script. They are excluded from the final normal build/JAR.

```bash
python3 tools/generate_vale_polish.py
python3 tools/generate_vale_decor.py
python3 tools/validate_vale_resources.py
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew build verifyModels -x downloadAssets
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I build/reports/mushroom-vale-stage24a/fresh-world.gradle \
  runGameTestServer -x downloadAssets
# Failure investigation only; fresh directory is guarded in this ignored init:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I build/reports/mushroom-vale-stage24a/focused.gradle \
  runGameTestServer -x downloadAssets
```

## Manual acceptance remaining

Offline isometric sheets were generated from the resource geometry and
inspected; `tools/preview_vale_decor.py` reproduces them in the ignored report
directory. Vanilla blocks are approximated, with no Minecraft lighting,
resource-pack or shader rendering. They are **not** game screenshots or manual
acceptance. Use the exact IntelliJ checklist for texture/culling/overlap,
lighting under a shaded canopy, all rotations, doors and traversal, actual
Syusya routes, save/reload, old-save backup loading, natural density, dedicated
multiplayer and full BMC4 v55.5. Large flat-site requirements can still make
natural cottages rare. Previously generated decor is not rewritten.

## Final packaging and preservation

Final normal `build verifyModels -x downloadAssets`: **PASS**, exit **0**,
**18s**; compilation/check/reobfuscation and all nine character model trees
pass after the final changes. JAR ZIP integrity and byte equality of
**323/323** source JSON/PNG resources pass. Original 16×16 texture
headers pass; all three new production classes are packaged and the focused
harness/template are absent. JAR: `build/libs/dudunka-0.26.0-alpha.jar`. SHA-256:
`9bf8fabd1cee5a39bb73513d200bc0e6aaebd320b97086837945f20c40bb4c80`.

Baseline hash comparison: **409 unchanged files**, **19 modified**,
**63 added**, **zero deleted** across source/docs/tools. All previous
uncommitted changes are retained. Changes to existing files are limited to
the list below. All placed-feature/biome JSON, dimension/type/noise settings,
vegetation, family production code and `FamilyTests.java` remain byte-identical
to the initial working tree. Travel/return behavior, UUIDs, old registry IDs,
saves and album protocol **9** remain unchanged. `MushroomVale.java` only adds
the preview subtree under the existing opt-in/permission-gated root. No
dependencies, commits or pushes. `git diff --check` passes.

## Exact changed files relative to the starting working tree

Modified (19):

- `docs/MUSHROOM-VALE.md`
- `docs/TESTING.md`
- `docs/VALIDATION.md`
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`
- `src/main/java/io/github/golovinss/dudunka/MushroomVale.java`
- `src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java`
- `src/main/java/io/github/golovinss/dudunka/test/MushroomValeTests.java`
- `src/main/resources/assets/dudunka/lang/en_us.json`
- `src/main/resources/assets/dudunka/lang/ru_ru.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/bench.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/cozy_cluster.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/direction_marker.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/flower_bed.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/lantern_post.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/mushroom_picnic.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/pufik_cottage.json`
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/wise_fly_agaric_cottage.json`
- `tools/generate_vale_decor.py`
- `tools/validate_vale_resources.py`

Added (63):

- `docs/VALIDATION-MUSHROOM-VALE-STAGE-2.4a.md`
- `src/main/java/io/github/golovinss/dudunka/MushroomValeCottagePreview.java`
- `src/main/java/io/github/golovinss/dudunka/MushroomValeDecor.java`
- `src/main/java/io/github/golovinss/dudunka/MushroomValeDecorShapes.java`
- `src/main/resources/assets/dudunka/blockstates/vale_bench.json`
- `src/main/resources/assets/dudunka/blockstates/vale_book_stack.json`
- `src/main/resources/assets/dudunka/blockstates/vale_carved_wood.json`
- `src/main/resources/assets/dudunka/blockstates/vale_cottage_cap.json`
- `src/main/resources/assets/dudunka/blockstates/vale_cottage_crown.json`
- `src/main/resources/assets/dudunka/blockstates/vale_cottage_eave.json`
- `src/main/resources/assets/dudunka/blockstates/vale_cottage_window.json`
- `src/main/resources/assets/dudunka/blockstates/vale_lantern.json`
- `src/main/resources/assets/dudunka/blockstates/vale_mushroom_stool.json`
- `src/main/resources/assets/dudunka/blockstates/vale_mushroom_table.json`
- `src/main/resources/assets/dudunka/blockstates/vale_planter.json`
- `src/main/resources/assets/dudunka/blockstates/vale_signpost.json`
- `src/main/resources/assets/dudunka/models/block/vale_bench.json`
- `src/main/resources/assets/dudunka/models/block/vale_book_stack.json`
- `src/main/resources/assets/dudunka/models/block/vale_carved_wood.json`
- `src/main/resources/assets/dudunka/models/block/vale_cottage_cap.json`
- `src/main/resources/assets/dudunka/models/block/vale_cottage_crown.json`
- `src/main/resources/assets/dudunka/models/block/vale_cottage_eave.json`
- `src/main/resources/assets/dudunka/models/block/vale_cottage_window.json`
- `src/main/resources/assets/dudunka/models/block/vale_lantern.json`
- `src/main/resources/assets/dudunka/models/block/vale_mushroom_stool.json`
- `src/main/resources/assets/dudunka/models/block/vale_mushroom_table.json`
- `src/main/resources/assets/dudunka/models/block/vale_planter.json`
- `src/main/resources/assets/dudunka/models/block/vale_signpost.json`
- `src/main/resources/assets/dudunka/models/item/vale_bench.json`
- `src/main/resources/assets/dudunka/models/item/vale_book_stack.json`
- `src/main/resources/assets/dudunka/models/item/vale_carved_wood.json`
- `src/main/resources/assets/dudunka/models/item/vale_cottage_cap.json`
- `src/main/resources/assets/dudunka/models/item/vale_cottage_crown.json`
- `src/main/resources/assets/dudunka/models/item/vale_cottage_eave.json`
- `src/main/resources/assets/dudunka/models/item/vale_cottage_window.json`
- `src/main/resources/assets/dudunka/models/item/vale_lantern.json`
- `src/main/resources/assets/dudunka/models/item/vale_mushroom_stool.json`
- `src/main/resources/assets/dudunka/models/item/vale_mushroom_table.json`
- `src/main/resources/assets/dudunka/models/item/vale_planter.json`
- `src/main/resources/assets/dudunka/models/item/vale_signpost.json`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_amber.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_cream.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_gold.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_leaf.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_paper.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_pink.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_red.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_violet.png`
- `src/main/resources/assets/dudunka/textures/block/vale_decor_wood.png`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_bench.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_book_stack.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_carved_wood.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_cottage_cap.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_cottage_crown.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_cottage_eave.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_cottage_window.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_lantern.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_mushroom_stool.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_mushroom_table.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_planter.json`
- `src/main/resources/data/dudunka/loot_tables/blocks/vale_signpost.json`
- `tools/generate_vale_polish.py`
- `tools/preview_vale_decor.py`
