# Mushroom Vale Stage 4.1 validation

2026-10-14 follow-up: manual exploration found missing Bells at two located
sites. The previous one-site-per-kind PASS below does not exclude valid empty
starts. See [native landmark investigation and pending regression validation](VALIDATION-MUSHROOM-VALE-NATIVE-LANDMARKS.md).

Implementation: five native adventure landmarks; unchanged 0.27.1-alpha and
album protocol 10. Publication: **NOT PERFORMED**. Minecraft/BMC4 graphical,
multiplayer and existing-world acceptance: **NOT RUN**.

## Automated commands

Use JDK 17; each wrapper GameTest invocation creates a fresh isolated world.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
```

The focused suite checks loaded structure/set/type/biome resources, deterministic
native piece NBT, wrong-generator starts, complete one-chunk geometry,
water/tree/build/support rejection, finished-chunk no-op behavior, and actual
vanilla `/locate structure` for all five IDs from (40008,80,40008) in a fresh
Vale. It finishes each located chunk and compares every planned block with the
world, then round-trips its native start NBT. NBT tests do not substitute for
restarting a graphical game and reloading the chunk from disk.

## Manual exploration acceptance — NOT RUN

1. Create a fresh world with structures enabled. Enter Vale through the approved
   family portal, or use an operator development run with
   `-PmushroomValeDev=true` and `/dudunka_dev mushroom_vale enter`.
2. Execute each command **while in Mushroom Vale**:

   ```text
   /locate structure dudunka:music_bells
   /locate structure dudunka:jumper_dance
   /locate structure dudunka:memory_stone
   /locate structure dudunka:silver_web_harp
   /locate structure dudunka:singing_mushroom
   ```

3. Travel to each returned X/Z and allow normal terrain generation. Locate Y is
   not a safe teleport height; use spectator flight from above if teleporting.
   Confirm the matching visible design, correct biome, dry supported geometry,
   open approach, light and preserved surrounding slopes/vegetation. Capture
   seed, coordinates and screenshots. Check the chiseled anchor at the center.
4. Explore normally between several landmarks and judge whether frequency feels
   appropriate. Test at least a second seed and BMC4 v55.5. No fixed radius is a
   promise of a landmark; unsuitable sites legitimately reduce density.
5. Save, quit and reload. Confirm blocks, native `/locate` results and anchors
   persist. Check a second player sees the same landmarks.
6. Upgrade a COPY of a 0.27.1-alpha Stage 4.0 world: existing chunks, cottages,
   family UUIDs, gateways, album chapters, ownership and return routes remain
   intact. Explore new Vale chunks for landmarks. No landmarks should appear in
   previously generated chunks or other dimensions; legacy flat generators are
   excluded. Exercise family entry and return again.

Limitations: screenshots/visual acceptance, actual disk restart, multiplayer and
full BMC4 compatibility require manual execution. Unsupported datapacks that
clone Vale generation into another dimension can expose empty structure starts;
actual placement still refuses that dimension. Third-party worldgen may obstruct
a site between noise prediction and decoration; preserving it takes priority.

## Recorded evidence (2026-10-12)

- Build/model/resource checks: **PASS**, report
  `build/reports/dudunka-tests/20261012T192838Z-3706392/summary.txt`.
- Focused suite: **PASS — all 3 required tests**, report
  `build/reports/dudunka-tests/20261012T193346Z-3711244/gametest.log`.
- Seed: `-255494441983840131`. Real vanilla command results were completed to
  FULL chunks and every planned block was present:

  | Landmark | Saved anchor X/Y/Z | Generated plan blocks |
  | --- | --- | --- |
  | music_bells | 39752 / 76 / 40392 | 120 |
  | jumper_dance | 39576 / 81 / 39928 | 50 |
  | memory_stone | 40392 / 86 / 39688 | 50 |
  | silver_web_harp | 40184 / 77 / 39560 | 81 |
  | singing_mushroom | 40040 / 68 / 40344 | 88 |

Earlier attempts are retained: `20261012T192905Z-3707222` failed at startup
because of test template naming; `20261012T193027Z-3708169` executed **zero**
tests and is **INVALID EVIDENCE**, despite the old wrapper saying PASS. The
wrapper now rejects zero-test results. `20261012T193119Z-3708951` and
`20261012T193239Z-3710399` failed fixture/assertion checks: the synthetic meadow
footprint crossed a biome edge, and the flat Memory Stone has exactly 35 blocks,
not more than 35. Those checks are corrected; actual locate generation passed
in both runs as well. No production placement guards were weakened.

## Changed files

- `src/main/java/io/github/golovinss/dudunka/ValeAdventureStructure.java` —
  native codec, kinds, eligible biome and dry bounded start selection.
- `src/main/java/io/github/golovinss/dudunka/ValeAdventurePiece.java` —
  five blueprints, atomic preflight, new-chunk/dimension guards, saved anchor.
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java` — type/piece registration.
- `src/main/java/io/github/golovinss/dudunka/test/ValeAdventureTests.java` — focused tests.
- Under `src/main/resources/data/dudunka/worldgen/structure/`:
  `music_bells.json`, `jumper_dance.json`, `memory_stone.json`,
  `silver_web_harp.json`, `singing_mushroom.json`.
- Under `src/main/resources/data/dudunka/tags/worldgen/biome/has_structure/`:
  the same five JSON filenames (exact eligible biome tags).
- `src/main/resources/data/dudunka/worldgen/structure_set/vale_adventure_landmarks.json`.
- `src/main/resources/data/dudunka_landmarks/structures/empty.nbt` — isolated test template.
- `build.gradle`, `tools/test.sh` — include/select focused namespace; reject zero tests.
- `docs/MUSHROOM-VALE.md`, `docs/DEVELOPMENT-WORKFLOW.md`, this validation record.

First full wrapper `./tools/test.sh all`: **build PASS; full GameTests FAIL**.
Report: `build/reports/dudunka-tests/20261012T193457Z-3712113/summary.txt`.
The suite ran 273 tests and reported two required failures:

- `gatewayUsesExistingCharacterPosesAndCancelsWhenAFriendLeaves` —
  `Cancellation releases every pose`.
- `valeRoundTripKeepsPlayerIdentityAndAllCompanionsAtDeparture` —
  `Travel must leave each companion instance, UUID, owner, position, care, home and stable saved state at departure`.

The three new landmark tests passed in the full run too. This is **not a green
full-suite result**. Existing gateway/travel production code was not edited.
A targeted comparison against baseline commit `eff277b` is recorded below when
completed; graphical/BMC4 acceptance remains **NOT RUN** regardless of it.

Baseline comparison: both original failing tests passed alone at `eff277b`, then
an entirely clean baseline checkout passed **all 270 required tests**. Reports
are copied into `build/reports/dudunka-tests/baseline-focused-20261012T193928Z-3714854/`
and `build/reports/dudunka-tests/baseline-full-20261012T194115Z-3715981/`.
The first comparison changed only test namespace selection/template; the full
comparison had zero Git changes. The temporary comparison worktree was removed
after copying its reports. These failures are therefore not claimed as
preexisting baseline failures.

Minecraft lays out consecutive GameTest batches at different X coordinates;
adding batches changes later fixtures' alignment with chunk boundaries. The
existing two tests depended on companion entities being observable outside the
fixture's reliably ticking chunk. Their fixtures now temporarily load a 3×3
neighborhood and wait for entity ticking, releasing only tickets acquired by
the test on success, exception or timeout. Production travel/portal code,
assertions and timeouts remain unchanged. The final rerun result follows below.

Additional changed test files:
`src/main/java/io/github/golovinss/dudunka/test/FamilyGatewayTests.java` and
`src/main/java/io/github/golovinss/dudunka/test/MushroomValeTests.java` (fixture
chunk visibility only).

## Final status

- Implementation: **COMPLETE**, pending manual acceptance.
- Final build/model/resource checks: **PASS**.
- Focused landmark tests: **3/3 PASS**.
- Final full GameTests: **273/273 required tests PASS**, including all five real
  vanilla `/locate` results, native start/piece persistence, and the two existing
  gateway/travel assertions with their corrected chunk-visibility fixtures.
- Final wrapper report:
  `build/reports/dudunka-tests/20261012T194535Z-3717976/summary.txt`;
  evidence in neighboring `build.log` and `gametest.log`.
- Cheap static checks: **PASS** (`git diff --check`, `bash -n tools/test.sh`,
  parsing all 11 added worldgen JSON resources).
- Graphical Minecraft/BMC4, multiplayer, actual disk restart/reload and
  existing-world upgrade acceptance: **NOT RUN**. Use the manual steps above.
- Commit/push/PR/JAR publication: **NOT PERFORMED**. Stage 4.2: **NOT STARTED**.

Remaining risks are seed-dependent frequency/visual quality and third-party
worldgen/datapack conflicts described in the design contract. Automated success
on the wrapper seed does not establish BMC4 acceptance or guarantee every
third-party predicted site can safely be placed. Existing flat generators stay
excluded; previously generated chunks receive no retroactive landmarks.
