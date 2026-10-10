# Mushroom Vale Stage 2.3 validation — 2026-10-10

Baseline: local `main` at `1e81828c4268b356fdda16bb7778c65946306a78`, with the
existing **uncommitted Stage 2.2 implementation** retained. This task continues
that working state; it does not reset to HEAD or reimplement terrain/biome
selection. Minecraft 1.20.1, Forge 47.4.13, OpenJDK 17.0.20.1, Gradle 8.8.

Implementation and automated checks are complete. Graphical Minecraft,
Syusya walking routes and full Better MC BMC4 v55.5 acceptance remain pending.

## Exact automated results

1. `python3 tools/validate_vale_resources.py`: **PASS**. **207/207** source JSON
   resources parse; **99** recursively referenced configured/placed worldgen
   resources resolve against source/cached Minecraft 1.20.1 resources. All
   **12** new block/item/loot/RU+EN sets resolve; **33** block/item/parent model
   references and their direct texture resources resolve. Shared biome feature
   ordering is acyclic. This preflight checks references, not graphical baking.
   The first preflight attempt exposed a validator traversal error for vanilla
   weighted selectors (`pine_checked` was interpreted as configured instead
   of placed); the validator was corrected before successful validation.
2. `build verifyModels -x downloadAssets`: **BUILD SUCCESSFUL**, exit **0**,
   **24s**. Includes compilation, resources, tests/check and reobfuscated JAR;
   all **nine** existing character model trees pass `verifyModels`, including
   the existing pose checks. The sandbox attempt failed on Gradle's read-only
   cache lock before configuration; the approved cache-access rerun is the
   only executed build/model-check run. No Minecraft suite ran on that attempt.
3. Final `runGameTestServer jar reobfJar -x downloadAssets` with the fresh-world
   init script: **BUILD SUCCESSFUL**, exit **0**, **1m 38s**. **159/159 required
   GameTests passed**, including **11/11 Vale tests**. The full suite ran
   **once**, with no failing tests or repeats. The final invocation recompiles
   the completed patch-placement assertions added after the build and packages
   those validated classes. No production code changed after the build.
4. Actual Forge startup decoded the new blocks, all three biomes and every
   referenced feature through the real registries. The isolated RunConfig
   working directory is `run/vale-stage23-gametest/`; the init script checks
   that `world/level.dat` does not exist before execution. Logs confirm this
   directory. No existing worlds/chunks were removed or edited.
5. JAR ZIP integrity and byte equality with **250/250** source JSON/PNG files
   pass. SHA-256 for `build/libs/dudunka-0.26.0-alpha.jar`:
   `03ed7c6efedf44211a9d2e75fafd6a4d04d6c1ee13ebfe22566517202f38519a`.
   Version remains unchanged. `git diff --check` passes.
6. Hash comparison against the starting working state passes for all **nine**
   captured compatibility-critical files: dimension/type, noise settings,
   terrain density, hill noise, travel, return SavedData, album network and
   Dudunka dialogue. Terrain and climate selection are byte-identical to the
   local Stage 2.2 state. Other production changes are confined to additive
   vegetation registration and the existing creative tab.

Commands (Java 17; client assets intentionally excluded for headless checks):

```bash
python3 tools/validate_vale_resources.py
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew build verifyModels -x downloadAssets
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I /tmp/vale-stage23-fresh-world.gradle \
  runGameTestServer jar reobfJar -x downloadAssets
```

Existing API/Gradle deprecation notices, terminal warnings, Netty debug
reflection diagnostics and first-start missing `server.properties` are present;
the server creates defaults and completes successfully. No worldgen codec,
feature ordering or registry errors were observed. No new dependencies or web
research were needed. Future fresh validation must choose another unused world
directory; the init script is copied into the evidence below.

## New feature checks

`valeSignatureFeaturesPlaceWithLightClearanceAndSurvival` places the eight
loaded giant/landmark configurations on a controlled mycelium platform with
RandomSource seed 42. It checks real generated cap/stem blocks and emitted
light. Broad landmarks require radius four and caps at least four blocks above
the root. Occupied root placement must refuse without replacing the obstacle.
The test restores every platform block in a `finally` clause.

| Configured mushroom | Cap blocks | Stem blocks | Cap radius |
| --- | ---: | ---: | ---: |
| giant_red | 45 | 6 | 2 |
| giant_brown | 45 | 6 | 3 |
| giant_turquoise | 45 | 6 | 3 |
| giant_violet | 45 | 6 | 2 |
| giant_golden | 21 | 6 | 2 |
| turquoise_landmark | 77 | 6 | 4 |
| violet_landmark | 77 | 6 | 4 |
| golden_landmark | 77 | 6 | 4 |

The same test checks all eight ground plants for survival on grass/mycelium/
podzol/moss, refusal on stone/water, empty collision, land pathfinding and
positive block light. All eight loaded random patches also place successfully:
glowing moss **11** plants, spore bushes **5**, glowing small mushrooms **11**,
memory small mushrooms **11**, pink small mushrooms **5**, glowing flowers
**11**, memory flowers **8**, pink flowers **15**. These controlled counts test
codecs/state providers and support behavior, not natural density estimates.

Existing seven-seed biome distribution and three-seed base terrain checks pass;
their settings and expected bounds are unchanged from Stage 2.2. Existing
permissions, travel/return, UUID/NBT isolation, safety refusal, fallback,
bookmark persistence, Forge travel veto and dialogue regressions pass.

## Natural finished chunks

The existing biome test selects dry sites using this fresh server's actual
seed and generates finished chunks. It checks each biome's ground and standing
clearance; the meadow still requires at least 64 clear grass columns.

| Biome | Selected base position | Matching ground columns | Clear columns |
| --- | --- | ---: | ---: |
| Glowing forest | (-64,71,-64) | 81 | 77 |
| Pink meadows | (0,77,-64) | 255 | 255 |
| Memory forest | (-64,84,-128) | 231 | 218 |

A read-only NBT inspection after server shutdown confirms these saved palettes:

- Glowing sample: turquoise and golden caps, custom stems, vanilla brown giant
  blocks, moss and glow lichen.
- Meadow sample: pink magical flowers, small golden/turquoise mushrooms,
  cherry logs/leaves and pink petals.
- Memory sample: violet caps, stems, violet memory flowers, glowing moss,
  spore bushes and all three small mushroom colours, alongside spruce.

Across the four saved FULL Vale chunks, **11/12** new block IDs appear in
palettes. Cyan flowers are absent from this tiny natural sample; their loaded
patch places successfully in the controlled test. Larger exploration must
confirm their frequency and all rare landmarks. Palette presence neither
counts blocks nor distinguishes a broad landmark from its smaller cap palette.
The three clearance samples do not establish connected walking routes.

## Changed files and architecture

Stage 2.3 changes **109 files** relative to the starting Stage 2.2 working state:

- `MushroomValeVegetation.java` (new): 12 additive block/item registrations,
  vanilla huge-mushroom blocks, passive no-collision ground plant behavior.
- `DudunkaMod.java`: initialize vegetation before attaching deferred registers;
  expose decorative items through the existing Family creative tab.
- `MushroomValeTests.java`: one new isolated vegetation test; Stage 2.2 tests
  and all existing regression checks retained.
- `assets/dudunka/`: **12** blockstates, **13** block models (including shared
  gills), **12** item models, **13** original 16px PNG textures; two localization
  files updated with matching keys.
- `data/dudunka/loot_tables/blocks/`: **12** self-drop tables.
- `worldgen/configured_feature/mushroom_vale/` and
  `worldgen/placed_feature/mushroom_vale/`: **16** new feature pairs. Names:
  `giant_red`, `giant_brown`, `giant_turquoise`, `giant_violet`, `giant_golden`,
  `turquoise_landmark`, `violet_landmark`, `golden_landmark`, `glowing_moss`,
  `spore_bushes`, `glowing_small_mushrooms`, `memory_small_mushrooms`,
  `pink_small_mushrooms`, `glowing_flowers`, `memory_flowers`, `pink_flowers`.
- Three biome resources: vegetation lists and glowing/memory effect colours;
  existing `memory_trees` placement reduced from four to one attempt per chunk.
- `tools/generate_vale_vegetation.py`, `tools/validate_vale_resources.py` (new).
- `MUSHROOM-VALE.md`, `TESTING.md`, `VALIDATION.md`, and this report.

Block IDs, light levels, placement rates and extension points are documented
in the [Stage 2.3 architecture](MUSHROOM-VALE.md#stage-23-signature-vegetation-and-giant-mushroom-landmarks).
The system uses vanilla huge-mushroom/random-patch codecs, data-driven state
providers and biome-local placements. No custom Java feature or terrain
generator is needed. Landmarks are natural features, not structures.

Initial Stage 2.2 modifications to dimension/noise settings and its validation
report remain in the working tree. No IDs were renamed or removed. Album
protocol remains **9**. No mobs, villagers, quests, final portal, music playback,
structures, global vanilla overrides, dependencies, commits, pushes or PRs.

Local ignored evidence in `build/reports/mushroom-vale-stage23/`: build/suite
logs, fresh-world init script, compatibility hashes, texture sheet, individual
and aggregate finished-chunk palette JSON, and full changed-file manifest.

## Remaining manual acceptance and limitations

Follow the [NEW runClient world checklist](TESTING.md#stage-23-signature-vegetation-and-landmarks).
Block/item rendering, cutout transparency, visible glow under fixed noon,
all natural feature frequencies, rare landmark appearance, biome transitions,
fog colours, walkable routes and actual Syusya navigation require client review.
Vanilla huge-mushroom shapes retain their usual 4–6/occasional 8–12 heights;
broad landmarks are wider, without guaranteeing taller stems. Domed red/violet
skirts may be low. Glow uses ordinary block light; fog is colour-only. These
decorations have no growth, feeding, progression, special interactions or
audio. The art is original pixel game assets, not a copy of concept illustrations.

Graphical save/reload, old-save backup loading, multiplayer/dedicated-server
travel and full BMC4 v55.5 remain unaccepted manually. Generated chunks are not
rewritten; earlier saved generator/biome sources may persist even in newly
visited areas. `/reload` does not migrate a world. Use NEW worlds for acceptance
and retain all new block registrations after saving them into chunks.
