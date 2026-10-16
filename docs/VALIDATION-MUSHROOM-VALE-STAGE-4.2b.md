# Stage 4.2b — Dance of the Fluffy Jumpers

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Version **0.27.1-alpha**, album protocol **10**. Existing uncommitted work retained.
The user explicitly authorized build/model checks, focused and full GameTests.

| Status | Result |
| --- | --- |
| Implementation | Chapter 2 implemented; Chapters 3–5 deferred |
| Build and model checks | **PASS**, final `20261012T214024Z-3766186` |
| Focused music GameTests | **PASS**, **13/13**, `20261012T213514Z-3763149` |
| Full GameTests | **PASS**, **286/286**, `20261012T214024Z-3766186` |
| Manual runClient / BMC4 / real multiplayer / restart | **NOT RUN** |
| Commit / push / publication | **NOT DONE** |

## Changes

- `ValeDanceQuest.java`: native loaded-start and exact blueprint binding; all
  flower voices, private notes/sparkles, approach and repeatable demonstration,
  Marusya/jumper narrative, untimed two/one/two/one tap groups.
- `ValeMusicQuest.java`: exposes the existing read-only native-piece lookup for
  the dance while preserving bell resolution and interaction behavior.
- `ValeMusicProgress.java`: additive UUID Chapter 2 completion, Chapter 1 gate,
  separate transient dance attempts, replay idempotency and monotonic duplicate
  owner rows. Existing schema version/storage key and unknown fields retained.
- `ValeDanceTests.java`: six focused tests; existing music test fixture accepts
  either landmark and restores native starts/blocks on close.
- `FamilyGatewayTests.java`: the existing departure fixture now moves ten
  blocks (outside the eight-block ritual radius) rather than twenty, keeping
  the participant inside its ticking 3×3 chunks for all batch alignments. Added
  a loaded-identity assertion; existing pose assertions/timeouts unchanged.
- RU/EN language resources, storyline and workflow/wrapper descriptions updated.
  No new dependency, texture, registry ID, worldgen change or album UI.

Static checks: **PASS**, `git diff --check`, `bash -n tools/test.sh`, JSON
parsing and **11 matching RU/EN dance-message keys**.

## Automated coverage

1. All **4,096** six-tap combinations across the four clue flowers; only
   north/north/east/south/south/west completes; other flowers allow free retry.
2. Chapter order, independent UUID resets, separate bell/dance attempts and
   prevention of combining input from different landmark anchors.
3. Compressed saved-NBT round-trip, transient attempt loss, per-player progress,
   idempotent replay without dirtying data and duplicate-row monotonic completion.
4. Actual right-click subscriber on restored native dance geometry without NPCs,
   private feedback and progress, other player’s clue cannot reset an attempt;
   blocks, forced chunks and Mystery save unchanged.
5. Six-beat demonstration with repeated notes and four distinct highlighted
   flower voices; all ring flowers have unique voices; listening grants nothing.
6. Offhand, missing start, replaced flower, missing root and unloaded position
   rejection; logout discards only partial rhythm; dimension change keeps completion.

Tests install synthetic saved native pieces in an isolated Vale world, not a
production save. Naturally generated-site presentation and real multiplayer
remain manual acceptance items. Existing native-generation and travel tests run
in the full suite.

## Validation history

Initial build `20261012T213314Z-3760812` caught a UUID argument error in
the new test fixture; corrected before the passing build. Gradle build/model
checks passed in `20261012T213356Z-3761385`, but editing the wrapper’s help text
while it was running disrupted its final shell parsing. The unchanged final
wrapper passed syntax checking and the complete build rerun above.

First full run `20261012T213616Z-3764263` completed **285/286**, reporting
`gatewayUsesExistingCharacterPosesAndCancelsWhenAFriendLeaves` failing at
“Cancellation releases every pose”. Stage 4.1 previously documented the same
chunk-alignment fixture failure. Adding music tests shifts GameTest placement;
the old twenty-block move can leave the fixture’s forced 3×3 neighborhood.
The narrow test-only correction above retains actual participant departure and
checks entity visibility explicitly; production gateway/travel code is untouched.
The first run is retained as failed evidence rather than hidden by the rerun.
Final `./tools/test.sh all` run `20261012T214024Z-3766186` passed build/model
checks and **all 286 required tests**, including the strengthened loaded-identity
assertion, original pose checks, both music chapters and travel/Mystery regressions.

## Commands

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
bash -n tools/test.sh
git diff --check
```

Reports: `build/reports/dudunka-tests/<run-id>/summary.txt`, `build.log`,
`gametest.log`. Test worlds: `run/dudunka-test-<run-id>/`.

## Manual runClient acceptance — NOT RUN

1. With JDK 17, run `./gradlew --offline runClient` in a separate terminal after
   automated validation finishes. Use a backed-up test save. Enter Mushroom Vale
   through the existing family travel route. Locate an actual generated site:
   `/execute in dudunka:mushroom_vale run locate structure dudunka:jumper_dance`.
   Existing chunks are not retrofitted. Walk/teleport to the result in the Vale.
2. Before Chapter 1, approach within 12 blocks and right-click the carved stone.
   Check Marusya’s welcome and first-melody hint. Hear/watch the demonstration;
   taps must not unlock Chapter 2. Complete Chapter 1 at `dudunka:music_bells`.
3. Return to the dance. Watch the stone’s six sparkle/sound cues: north twice,
   east once, south twice, west once. Use English and Russian. Check each flower
   gives a gentle distinct note and visible feedback. Repeat clues as needed.
   Verify usability with audio muted and with particles reduced separately.
4. Try wrong flowers: no item/health loss, delay or punishment. Follow the pattern
   with long pauses, including a repeated flower; completion must still work.
   Check Marusya’s completion text. Play/relisten afterward with no extra reward.
5. Complete without naturally spawned jumpers or nearby Marusya. Repeat with
   them present; their normal AI, appearance, ownership and family travel stay
   intact. Confirm no blocks are changed or repaired by the puzzle.
6. In real multiplayer, interleave taps at the same site. One player’s errors,
   clue replay, logout or completion must not advance/reset the other player or
   leak private text/sound/particles. Each must complete Chapter 1 independently.
7. Logout mid-pattern: retry from the beginning. Save/restart after completion:
   both music chapters remain complete, replay produces no progress/reward.
   Check another generated dance site cannot combine partial inputs.
8. Recheck Stage 4.2a, family travel and the five existing Mystery album chapters.
   Repeat presentation/audio and multiplayer checks in BMC4 v55.5.

## Remaining limits

Marusya and jumpers participate through narrative; no NPC animation/AI changes.
Destroyed roots/flowers disable their interactions; use another intact site or
restore blocks manually. Direction text and repeated visual demonstrations need
child-focused in-game review. Resource packs and client audio/particle settings
can affect clue presentation. Automated success does not establish manual BMC4,
visual, audio, restart or real multiplayer acceptance. No Stage 4.2c work started.
