# Mushroom Vale Stage 2.2 validation — 2026-10-10

Baseline: clean local main at `1e81828c4268b356fdda16bb7778c65946306a78`.
Minecraft 1.20.1, Forge 47.4.13, OpenJDK 17.0.20.1, Gradle 8.8.
Implementation and automated validation complete; graphical Minecraft and full
Better MC BMC4 v55.5 acceptance remain pending.

## Automated results

- **126/126** source JSON resources parse successfully. Every biome placed-feature
  reference resolves against source or the cached Minecraft 1.20.1 client JAR.
  Recursive feature-reference inspection resolves 80 biome/placed/configured
  resources, including nested inline placements.
  Fresh Forge startup decodes all three biomes and their configured/placed features,
  surface rules and climate parameters through the actual datapack registries.
  No web research or new dependencies were needed.
- `build verifyModels runGameTestServer -x downloadAssets`: **BUILD SUCCESSFUL**,
  exit **0**, **1m 44s**. Build includes Java compilation, resource processing and
  reobfuscated JAR. `verifyModels` passes all nine model trees and existing checks.
- **158/158 required GameTests passed**, including **10/10 Vale tests**. The full
  suite ran **once** in a fresh, isolated `run/vale-stage22-gametest/` world.
  The initial sandbox launch failed at Gradle's read-only cache lock before
  configuration or tests; the approved cache-access rerun is the only suite run.
- The init script asserts no existing `world/level.dat` and sets the actual
  RunConfig working directory immediately before execution. Runtime GAMEDIR
  confirms the isolated path. No existing worlds or chunks were removed.
- Existing Gradle/API deprecation notices and first-start missing
  `server.properties` message remain; the server creates its defaults normally.
- `git diff --check` passes. JAR ZIP integrity and packaged dimension/type plus
  all Dudunka worldgen JSON equality against source pass.

Command (the init script is copied with the local evidence):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I /tmp/vale-stage22-fresh-world.gradle \
  build verifyModels runGameTestServer -x downloadAssets
```

A future fresh run must choose another unused directory. Client assets were
excluded only for headless validation. Artifact:
`build/libs/dudunka-0.26.0-alpha.jar` (version unchanged).
SHA-256: `82a758c24724739b93ea35bd80d0f48c658a2651c97a75807754a5b116dd8728`.

Local ignored evidence under `build/reports/mushroom-vale-stage22/`:
`vale-stage22-validation.log`, `vale-stage22-fresh-world.gradle`, and
`finished-chunk-palettes.json`.

## Biome distribution

The test uses the actual loaded multi-noise biome source and a separately seeded
vanilla RandomState for each seed. Each seed samples **4225 positions**:
X/Z=-2048…2048 at 64-block intervals, Y=80 (quart Y=20). All climate axes except
humidity are full range; biome selection has no intended vertical split.
Counts describe sampled coverage, not exact area. Nearest distance is the
Chebyshev distance (maximum of absolute X/Z) on that grid, from the origin.

| Seed | Glowing forest | Pink meadows | Memory forest | Nearest blocks: glowing / pink / memory |
| --- | ---: | ---: | ---: | --- |
| 0 | 2944 | 620 | 661 | 0 / 192 / 256 |
| 42 | 2964 | 664 | 597 | 64 / 128 / 0 |
| -71021 | 3049 | 624 | 552 | 0 / 64 / 64 |
| 1 | 2985 | 633 | 607 | 64 / 0 / 256 |
| -1 | 3062 | 559 | 604 | 0 / 64 / 128 |
| 12345 | 2866 | 701 | 658 | 64 / 128 / 0 |
| 8675309 | 2918 | 647 | 660 | 0 / 128 / 64 |

Each biome must have at least 100 samples and occur within 1536 blocks for
all seven seeds; actual nearest samples are all within 256 blocks. Glowing
Mushroom Forest remains the main region, about 68–72% of sampled coverage.
These samples do not generate distant chunks or alter the server seed.

## Terrain and finished chunks

Stage 2.1's density function, hill noise and sea level are unchanged. Its
1089 base-column samples per seed, plus east/south neighbors and actual water
checks at Y=62, reproduce the previous results exactly:

| Seed | Height range | Maximum adjacent step | Wet / dry |
| --- | --- | --- | --- |
| 0 | 52–100 | 1 | 67 / 1022 |
| 42 | 52–100 | 1 | 71 / 1018 |
| -71021 | 52–100 | 1 | 76 / 1013 |

A new GameTest also selects dry locations using the fresh server's actual seed,
generates finished chunks, and verifies matching biome surface material and
standing clearance. Meadows require at least 64 clear grass columns.

| Biome | Selected base position | Matching ground columns | Clear columns |
| --- | --- | ---: | ---: |
| Glowing forest | (0,83,0) | 137 | 136 |
| Pink meadows | (-64,77,64) | 133 | 133 |
| Memory forest | (0,81,-64) | 256 | 226 |

A read-only NBT inspection of the three saved FULL chunks additionally confirms
mycelium, grass, podzol, moss blocks/carpet, glow lichen, red/brown giant mushroom
blocks, pink petals and spruce logs in their section palettes. Sparse cherry
placement did not produce a cherry log in this tiny sample; its vanilla
configured feature and placement references loaded successfully, but broader
client exploration must confirm cherry frequency and appearance. Palette
presence is not a block count or a visual-quality assessment.

The registration test checks all three selectable biome IDs, separate dimension
type/settings, bedrock, and empty hostile spawn lists. Existing permissions,
travel/return, companion UUID/NBT isolation, safety refusal, bookmark persistence,
fallback and Forge travel-veto regression tests remain intact and pass.

## Changed files and compatibility

- Dimension JSON: explicit three-region climate list.
- Noise settings JSON: biome-specific dry ground and vegetation climate scale;
  terrain density, sea level and other settings are unchanged.
- Three independent biome JSON resources under `worldgen/biome/`.
- Four placed features under `worldgen/placed_feature/mushroom_vale/`:
  `cherry_trees`, `memory_trees`, `surface_moss`, `surface_glow_lichen`.
- One configured feature under `worldgen/configured_feature/mushroom_vale/`:
  `surface_glow_lichen`.
- `MushroomValeTests.java`: updated registration assertions and new multi-seed /
  finished-surface test; existing terrain/travel tests retained.
- `MUSHROOM-VALE.md`, `TESTING.md`, `VALIDATION.md`, and this report.

No production Java changes. Dimension/type IDs, terrain density, travel/return
implementation and saved data, dialogue, companion/family code, UUID behavior,
assets and album protocol **9** retain baseline contents. No structures, mobs,
Pufik, Wise Fly Agaric, quests, final portal or music playback were introduced.
No global biome modifications or new dependencies. No commit, push or PR.

## Remaining manual acceptance

Follow the [NEW runClient world checklist](TESTING.md#stage-22-three-mushroom-vale-biomes).
Check all three biomes with `/locate biome`, colours/fog, luminous ground,
cherry frequency, actual mooshroom spawning, walking routes, borders and water.
Graphical save/reload, multiplayer/dedicated-server travel, old-world backup
loading and full BMC4 v55.5 compatibility have not been manually accepted.

Existing chunks are never regenerated or silently migrated. Saved generator
settings can retain Stage 1 flat or Stage 2.1 single-biome behavior, including
in newly visited chunks; `/reload` does not guarantee migration. Use new worlds
for Stage 2.2 acceptance and backup copies for legacy loading checks. Finished
sample clearance and base slopes do not prove every route is navigable.
