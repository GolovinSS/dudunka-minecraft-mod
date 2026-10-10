# Mushroom Vale Stage 2.4b validation — 2026-10-10

Baseline is the existing uncommitted Stage 2.2–2.4a working tree. No reset,
stash, deletion, commit, push or PR. Java 17, Minecraft 1.20.1, Forge 47.4.13;
target Better MC BMC4 v55.5. Approved `cottages_and_decor.png` inspected.

## Delivered

- Pufik: compact rounded cream shell, low spotted cap, flowers and small
  interior mushroom furniture. Wise: elongated timber library, asymmetric
  study bay, tall offset cap, covered porch, high transom, paired side windows,
  six interior bookshelves, lectern, books and warm interior lantern.
- Complete full-block boundary walls, including recessed corners, door jambs
  and lintels; one usable two-half spruce door per cottage. Full glass backs
  ornamental windows; solid ceilings seal the interior. All standing interior
  cells connect to the entrance. Floor plans contain 21 versus 37 blocks;
  final blueprints contain 150 versus 264 pieces.
- Root cause was the generator's intentional three-wide vestibule and its
  substitution of partial decor models for full walls. The shared placement
  rotation logic already rotates positions and states correctly; it needed no
  production changes. Existing cottages are not rewritten.
- Right-click bench seating uses one temporary server-owned mount per block,
  vanilla passenger/position packets, initial facing and seated pelvis height.
  Shift dismounts to supported clear adjacent space (above the seat if every
  side is blocked). Bench removal immediately ejects/removes; dismount,
  logout, dimension transfer and invalid riders leave no mount after cleanup.
  The new additive `dudunka:vale_bench_seat` type cannot serialize or summon.
  No block entity, occupancy map, custom packet or dependency was introduced.

## Automated validation

- `python3 tools/validate_vale_resources.py`: PASS (271 JSON resources, eight
  bounded blueprints, 24 block/item/loot/localization sets, acyclic worldgen).
- Initial `build verifyModels -x downloadAssets`: PASS; all nine character
  model trees bake. This does not establish graphical block-model acceptance.
- Focused final run: **3/3 PASS**, including all eight blueprints × four
  rotations/unsafe sites, both explicit previews, full cottage boundaries,
  both door halves opening, standing doorway/aisle clearance, connected
  interior standing cells, distinct footprints/library, and bench right-click,
  height/facing, repeated clicks, occupancy, serialization refusal, vanilla
  Shift input, breaking, dimension transfer and actual PlayerList logout.
- Investigation: first two focused runs passed decor/benches but failed the
  existing preview live-occupant rejection. Diagnostic assertions showed the
  armor stand was inserted into a terrain-loaded chunk whose entity section
  was not yet accessible. A fixed delay also failed that fixture visibility
  assertion. The test now temporarily forces its fixture chunk and waits for
  entity ticking before asserting visibility and refusal. Production safety
  checks were retained. The final focused run includes all final production code; the subsequent
  full run also includes the last preview fixture readiness correction.
- One diagnostic launch was stopped before configuration completed because
  ForgeGradle's remote HEAD connection check stalled. Subsequent invocations
  disable only that preflight with `-Dnet.minecraftforge.gradle.check.certs=false`;
  dependency transport remains unchanged. No project configuration was changed.

- First full suite: **161/162 PASS**; only the preview readiness fixture timed
  out on its completely fresh chunk neighborhood. All family tests passed.
  The fixture now synchronously loads the required two-chunk neighborhood,
  then waits for actual entity ticking, with a 400-tick budget and timeout
  ticket cleanup. No production chunk loading was added.
- Final full suite after that correction: **162/162 required tests PASS**.
  All 14 Vale tests pass. No remaining intermittent failures in the final run;
  the historical family greeting timing failure did not reproduce in either
  full run. Family production/test sources were not changed or weakened.
- Generator rerun: byte-identical. `git diff --check`: PASS.

Final normal `build verifyModels -x downloadAssets`: **PASS**, exit 0,
**9s**, including all nine character model trees. Final full GameTest command:
**BUILD SUCCESSFUL in 1m 32s**, exit 0. JAR integrity passes, all **323/323**
source JSON/PNG resources match the JAR, the seat class is packaged, and the
focused harness/template are absent. Artifact:
`build/libs/dudunka-0.26.0-alpha.jar`; SHA-256:
`b9964725a46b3dfa0580ec4e6e7b8534c638ee146d70e389bd2e2680dc61f647`.

## Preservation

Hash comparison against the starting working tree: **481 unchanged files,
10 modified, 2 added, zero deleted** across source/docs/tools. Existing changes
are retained. All **169 asset files**, **30 placed-feature files**, three biome
files, dimension/noise settings, family production/tests and album protocol
**9** are unchanged. No dependencies, commits or pushes.

Modified: `DudunkaMod.java`, `MushroomValeDecor.java`, `ClientEvents.java`,
`MushroomValeTests.java`, the two cottage configured-feature JSON files,
`tools/generate_vale_decor.py`, `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, and
`docs/VALIDATION.md`. Added: `ValeBenchSeat.java` and this report.

## Reproduction and manual acceptance

Ignored evidence and fresh-world init scripts are under
`build/reports/mushroom-vale-stage24b/`. Init scripts guard against reusing a
world. Choose another unused directory before rerunning. The focused harness
is added only by its init script and is excluded from the normal build.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/generate_vale_decor.py
python3 tools/validate_vale_resources.py
./gradlew -Dnet.minecraftforge.gradle.check.certs=false build verifyModels -x downloadAssets
./gradlew -Dnet.minecraftforge.gradle.check.certs=false \
  -I build/reports/mushroom-vale-stage24b/focused.gradle runGameTestServer -x downloadAssets
./gradlew -Dnet.minecraftforge.gradle.check.certs=false \
  -I build/reports/mushroom-vale-stage24b/full.gradle runGameTestServer -x downloadAssets
./gradlew runClient -PmushroomValeDev=true
```

[Exact platform, cottage and bench inspection commands](TESTING.md#stage-24b-cottage-architecture-and-usable-benches-in-intellij-idea).
Manual Minecraft/BMC4 acceptance remains pending: cottage silhouettes and
lighting, actual seated pose/height, free looking and Shift landing in all
rotations, two real clients racing for the same/adjacent seats, occupied bench
breaking, logout/reconnect, dimension travel, save/restart while seated,
old-save loading, shaders/resource packs, companion traversal and natural
worldgen density. Offline isometric images were inspected but approximate
vanilla blocks/lighting and are not Minecraft screenshots.
