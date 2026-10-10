# Mushroom Vale Stage 2.4 validation — 2026-10-10

Baseline: local `main` at `1e81828c4268b356fdda16bb7778c65946306a78`, including
ALL existing uncommitted Stage 2.2/2.3 changes. The user confirms both earlier
stages passed testing and visual acceptance. No reset, stash, discard, commit,
push or PR was performed. Minecraft 1.20.1, Forge 47.4.13, Java 17, Gradle 8.8;
target Better MC BMC4 v55.5. Stage 2.4 graphical/BMC4 acceptance remains pending.

## Automated checks

- `python3 tools/validate_vale_resources.py`: **PASS** — 223 JSON resources,
  three biomes, 115 recursively referenced worldgen resources, eight bounded
  decor blueprints, all 12 existing vegetation block/item/loot/localization
  sets, 33 model references and acyclic shared feature ordering. New checks
  cover blueprint bounds/duplicate positions, block references, feature-type
  registration, rarity/biome filters and cottage biome eligibility.
- `build verifyModels -x downloadAssets`: **BUILD SUCCESSFUL**, exit **0**,
  **15s**. Compilation, resources, test/check and reobfuscated JAR pass;
  all **nine** character model trees pass. This is not visual acceptance of
  the new vanilla-block exteriors. Final blueprint support adjustments are
  resources only; the subsequent server task reprocesses them and compiles
  the corrected test before packaging the final JAR.
- First full `runGameTestServer jar reobfJar -x downloadAssets`: exit **1**,
  **BUILD FAILED in 1m 42s**, **157/160** required GameTests passed. New test
  incorrectly expected fence connection booleans to remain at their blueprint
  defaults after adjacent blocks were placed. The test now checks rotated
  block positions and all non-connection state properties, permitting normal
  vanilla fence/mushroom-face updates. Production placement code was unchanged.
  Two untouched family tests also failed: `friendshipAccruesOnlyDuringSeatedCampfireRest`
  (one companion still IDLE instead of seated) and
  `realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes`
  (Syusya had not greeted by the deadline). The latter has documented
  intermittent failures in the Stage 1 report. Neither legacy assertion nor
  family gameplay was changed to suppress these failures.

- Corrected final full suite: **159/160** required GameTests passed,
  **12/12 Vale tests passed**, exit **1**, **BUILD FAILED in 1m 45s**.
  All eight new blueprints passed all four orientations and four unsafe-site
  scenarios. The campfire test passed this time. The sole remaining failure
  is the unchanged `realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes`:
  only Dudunka greeted before the assertion; Marusya and Syusya were still in
  CURIOUS activity. Its source and all family production sources are unchanged
  from the starting tree. This full regression check is **not green**; the
  documented legacy timing failure remains unresolved. There were exactly two
  full suite attempts, with the repeat justified by actual failures.
  No further suite repeats or unrelated family changes were made.
- Final packaging is performed separately because a failed GameTest task
  prevents later `jar reobfJar` tasks from running. `jar reobfJar -x downloadAssets` passed, exit **0**, **9s**.
  ZIP integrity and byte equality of **266/266** source JSON/PNG
  resources pass; the new feature class is present. SHA-256:
  `4c590288262ee85366c6bd469e4d47d23f713bf6cbc3fc1734e1061b5ba25c0d`. No production code changed after the successful build/model check.

Both suite attempts use unused, separately guarded RunConfig directories:
`run/vale-stage24-gametest/` and `run/vale-stage24-gametest-retry/`. The init
scripts assert no `world/level.dat` exists before starting. Existing worlds
and previous validation worlds are retained. Standard Forge/API/Gradle
notices, Netty debug reflection diagnostics and first-start missing
`server.properties` messages are present; the server creates its defaults.

Commands (headless validation excludes graphical client assets):

```bash
python3 tools/validate_vale_resources.py
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew build verifyModels -x downloadAssets
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I build/reports/mushroom-vale-stage24/fresh-world.gradle \
  runGameTestServer jar reobfJar -x downloadAssets
# Repeat only after actual failures, with corrected assertion and unused world:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I build/reports/mushroom-vale-stage24/fresh-world-retry.gradle \
  runGameTestServer jar reobfJar -x downloadAssets
```

## New coverage and architecture

One new GameTest loads all eight registered JSON features, places each in all
four rotations on a controlled Vale platform, checks generated blocks,
non-connection states, survival/support, a clear perimeter and three-wide,
three-high cottage entrances. Each blueprint must reject occupied, wet,
missing-floor and unsuitable leaf-floor sites without changing any block.
Direct placement in another dimension must refuse. The platform is restored
in a `finally` clause. These checks establish placement behavior, not natural
frequency or connected companion routes.

| Blueprint | Authored blocks |
| --- | ---: |
| lantern_post | 7 |
| direction_marker | 5 |
| bench | 6 |
| mushroom_picnic | 4 |
| flower_bed | 12 |
| cozy_cluster | 15 |
| pufik_cottage | 175 |
| wise_fly_agaric_cottage | 361 |

The new feature type `dudunka:mushroom_vale_decor` consumes small bounded JSON
block blueprints with a full dry/level/clear site preflight and cardinal
rotation. There are no new blocks or items. Six sparse props reference vanilla
blocks and existing Vale plants in all three Vale biomes. Pufik's cream/red
cottage is meadow-only; Wise's larger cottage has external shelves/reading
nook and is memory-forest-only. Future small exteriors can add blueprint and
placement JSON plus eligible biome wiring. No large structure framework,
entities, quests, final portal, music playback or progression is introduced.
Detailed limits, attempt rates and extension points are in
[MUSHROOM-VALE.md](MUSHROOM-VALE.md#stage-24-cozy-decor-and-landmark-cottages).

## Changed files relative to the starting working tree

Stage 2.4 changes **28 files** (including tools and this report):

- `src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java`:
  new bounded blueprint codec and Vale-only safe-site placer.
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`: one additive
  feature registration; all earlier working-tree registration changes retained.
- `src/main/java/io/github/golovinss/dudunka/test/MushroomValeTests.java`:
  one new isolated decor GameTest; all prior tests retained.
- `src/main/resources/data/dudunka/worldgen/biome/`:
  `glowing_mushroom_forest.json`, `pink_meadows.json`, `memory_forest.json`:
  added biome-local feature references before existing vegetation.
- `src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/`
  and `placed_feature/mushroom_vale/`: **eight pairs**, named `lantern_post`,
  `direction_marker`, `bench`, `mushroom_picnic`, `flower_bed`, `cozy_cluster`,
  `pufik_cottage`, `wise_fly_agaric_cottage`.
- `tools/generate_vale_decor.py`: reproducible blueprints/idempotent biome wiring.
- `tools/validate_vale_resources.py`: extended existing offline preflight.
- `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION.md` and this report:
  architecture, NEW-world client checks, results and earlier acceptance status.

`git diff --check` passes. No baseline source/resource/documentation file was removed.
All **325** baseline source/resource files outside the reported Stage 2.4
change list retain their original SHA-256 hashes, including `FamilyTests.java`. Hash comparisons
against the initial working tree preserve dimension/type JSON, noise settings,
terrain density, hills, travel, return SavedData, album network and Dudunka
dialogue. Existing IDs, UUID behavior and album protocol **9** are preserved.
No dependencies, global vanilla behavior, localization or vegetation assets
were changed by this stage. Earlier uncommitted dimension/terrain/vegetation
changes remain present rather than being reset to HEAD.

Ignored evidence: `build/reports/mushroom-vale-stage24/` contains baseline
hashes, source manifest, preflight/build/both suite logs and fresh-world init
scripts. Version remains `0.26.0-alpha`.

## Remaining manual checks

Follow [TESTING.md](TESTING.md#stage-24-cozy-decor-and-landmark-cottages):
`./gradlew runClient -PmushroomValeDev=true` with Java 17, normal client assets
and a **NEW** Creative world (seed 42, then 0 and -71021). Use biome location
and `/place feature` for controlled inspection; cottages are features and have
no `/locate structure` support. Natural placement requires exploration.

Pending: cottage/prop visual readability in every rotation, ordinary block
lighting under fixed noon, porch/support quality, natural density/rarity,
biome boundaries, unobstructed walking and actual Syusya routes, graphical
save/reload and travel/return, old-save backup loading, dedicated multiplayer,
and full BMC4 v55.5 acceptance. Strict flat-ground checks can make cottages
much rarer than their 1/48 and 1/64 attempt rates; record seeds/coordinates
before tuning. Reference/placement tests do not establish visual acceptance.
Markers are decorative arrows without promised destinations; seats have no
sitting action, cottages have plain empty shells, and Wise's empty lectern is
an ordinary vanilla exterior decoration. Generated chunks are never rewritten;
old saved generators can retain earlier biome sources. `/reload` is not a
migration mechanism.
