# Stage 4.3a.1 / vanilla teleport GameTest investigation — 2026-10-15

Implementation: complete locally, uncommitted. Automated build and GameTests after
these changes: **NOT RUN**. Manual Minecraft/BMC4 acceptance: **NOT RUN**.
Publication: not performed. Registry IDs, UUIDs, saves, quests, version and protocol
are unchanged. Existing dirty-tree visual, audio and landmark work is preserved.

## Evidence inspected before editing

- `build/reports/dudunka-tests/20261015T075335Z-819985/gametest.log` and retained
  `run/dudunka-test-20261015T075335Z-819985/logs/latest.log` / `debug.log`.
- The equivalent logs for `20261015T075440Z-820754`.
- Both actual retained `world/level.dat`, family registry/carrier SavedData,
  Overworld/Vale entity region files and Vale chunk `(0,0)` in `region/r.0.0.mca`,
  decoded read-only with Python's standard library. No world was opened by Minecraft.
- Cached mapped Minecraft 1.20.1 / Forge 47.4.13 sources, including
  `PersistentEntitySectionManager`, `ServerLevel`, `DistanceManager`, `ChunkMap`,
  `ServerChunkCache`, `GameTestBatchRunner`, `GameTestInfo`, `EntityGetter` and `Mob`.
- Development workflow, family travel/teleport documentation and prior Stage 4.0
  validation notes. No Gradle, builds, GameTests, broad surveys or new dependencies.

The supplied build PASS, landmarks 7/7 and music 50/50 are historical developer
results; this change does not claim to have rerun them.

## Findings and changes

`ServerLevel.areEntitiesLoaded` delegates to a disk-load status check. It does
**not** test entity ticking. `isPositionEntityTicking` checks both section ticking
visibility and the ticking ticket range. FULL chunks can already have loaded
entity data. The previous cold fixture assertion therefore cannot establish the
state named in its failure message. Both cold command fixtures also reused Vale
chunk `(0,0)`, which earlier fixtures load, force and visit. The full-suite pending
test ran before these visits and passed; the focused pending test ran after a
warm transfer and failed before executing a command. Retained chunk `(0,0)` is
`minecraft:full` in both worlds, with no saved forced chunks. Saved NBT cannot
reconstruct the transient ticket graph or prove past ticking state.

Production command accompaniment incorrectly used that disk-load predicate for
its promised source/arrival simulation gates. Gateway arrival preparation had the
same mismatch. Both now require entity data **and** actual ticking. Native source
reinsertion still uses its original loaded/safe checks: rollback restoration is
not made dependent on a player continuing to simulate the abandoned source.
No new production ticket, transfer retry, timeout or terrain edit was added.

The exclusion failure was a real command transfer, not ordinary AI movement:
focused log lines 895–899 and full log lines 2317–2321 show `eligible=1` and an
`accompanied` UUID at the destination. The fixture sends STAY to Dudunka and marks
Syusya carried. Marusya's ignored `commandHome` return is false because `homes()`
creates bindings without the room conditions required by `HomeMarkerEntity` /
`HomeRules`. She remains FOLLOW and is correctly eligible under the implemented
rules. No evidence establishes a production transfer of a companion actually in
HOME/STAY/carried state. The fixture now completes the room, asserts both mode
commands succeeded, and keeps exact source-position and live-object checks.
Transfer logs now include Kind, and failed position assertions identify UUID,
Kind, prior/current positions and exclusion flags.

Both worlds retain the failed exclusion owner's **carried** Syusya ticket and
backup after the failed assertion. Scheduled late cleanup does not run after a
GameTest stops ticking. All 14 command and 17 journey batches now have idempotent
`AfterBatch` cleanup; carrier tokens and the deliberately stale registry row are
also cleaned on failure. Operation snapshots are taken after asynchronous setup.

The journey eligibility test bypassed its existing readiness sequence and
inspected immediately after source registration/forcing. It now waits for source
visibility and source/arrival simulation before asserting unchanged eligibility
rules. The old assertion did not print the returned Eligibility, so its exact
failed branch cannot be recovered retrospectively. The new assertion includes it.

Firefly failures occurred at fixture readiness, before assistance behavior was
tested (full log lines 946 and 1380). The fixture assumed an elapsed 80 ticks plus
global view/simulation changes established ticking. The logs do not reveal which
internal prerequisite was still false. A ticket owned by this synthetic fixture
now prepares simulation; a condition sequence waits for actual ticking, entity
data and player registration within the original **180 ticks**. Global player
distances are no longer changed by these two tests. The production assistance
gates, spawn limits, colors, Forge vetoes and despawn behavior are unchanged.

The natural-despawn fixture searched only for unrelated players within 128 blocks,
then asserted that its player at 256 blocks was nearest. Another fixture player
between those radii invalidates that identity assertion. Fresh FluffyJumper
construction sets neither persistence nor a name, and `removeWhenFarAway` accepts
an unnamed entity. The corrected search excludes unrelated players throughout
the observer's distance, preserving the nearest-player assertion and actual
`checkDespawn` call. It additionally asserts no custom persistence/name and logs
the nearest UUID/position if the preconditions fail. The old combined assertion
does not identify the competing player's UUID.

## Seven-test handoff

Java filenames below are in `src/main/java/io/github/golovinss/dudunka/`; test
filenames are in its `test/` subdirectory. `GameTestChunkDiagnostics.java` is a new
shared, loaded-only diagnostic helper. This document is the only documentation
file added by this investigation.

| Failed test | Cause / remaining uncertainty | Classification | Exact Java files changed | Fix / still needs validation |
| --- | --- | --- | --- | --- |
| `coldArrivalWaitsForVanillaPlayerLoadingWithoutSourceTickets` | Wrong disk-load predicate; shared previously visited target. Historical ticking graph unavailable. | Fixture + production simulation guard | `FamilyTeleport.java`, `FamilyJourney.java`, `test/FamilyTeleportTests.java`, `test/GameTestChunkDiagnostics.java` | Separate distant FULL target; require entity data loaded while ticking is false before command; real player loading then deferred single transfer. Validate no forced chunk additions, identity and safety. |
| `stayHomeCarriedForeignFarAndStaleMembersNeverSubstitute` | Logged transfer of the remaining FOLLOW member after rejected HOME setup; leaked carrier token. | Fixture | `FamilyTeleport.java` (Kind diagnostics), `test/FamilyTeleportTests.java`, `test/GameTestChunkDiagnostics.java` | Valid room and successful mode preconditions; exact source/object assertions, foreign/far exact positions and failure cleanup. Validate no excluded transfer or stale recreation. |
| `coldArrivalTimeoutAndSupersedingCommandCannotReplayCapturedFamily` | Same incorrect predicate/shared target; focused/full order explains different precondition outcomes. | Fixture + production simulation guard | `FamilyTeleport.java`, `FamilyJourney.java`, `test/FamilyTeleportTests.java`, `test/GameTestChunkDiagnostics.java` | Its own FULL target; preserve 40-tick service deadline and supersession; additionally verify no replay after native loading actually becomes ready. |
| `journeyRejectsMissingYoungDuplicateCarriedForeignTetheredMountedAndUnsafeMembers` | Immediate inspection races fixture visibility; exact old Eligibility branch unlogged. | Fixture; historical branch uncertain | `test/FamilyJourneyTests.java`, `test/GameTestChunkDiagnostics.java` | Use established readiness sequence and batch cleanup; print Eligibility. Revalidate every original exclusion and zero-trust/no-friendship acceptance. |
| `bellAssistanceRespectsGatesVetoesReloadAndDeparture` | Simulation prerequisite false after fixed delay; exact transient ticket/section state unlogged. | Fixture; internal readiness cause unresolved | `test/ValeLandmarkFireflyTests.java`, `test/GameTestChunkDiagnostics.java` | Scoped simulation ticket and condition wait, unchanged timeout and assistance assertions. Validate gates, vetoes, reload, variants, departure/despawn and cleanup. |
| `danceAssistanceRespectsGatesVetoesReloadAndDeparture` | Same shared firefly fixture defect/uncertainty. | Fixture; internal readiness cause unresolved | `test/ValeLandmarkFireflyTests.java`, `test/GameTestChunkDiagnostics.java` | Same correction; validate dance landmark assistance and all original safety assertions. |
| `disabledAiAndActualNaturalDespawn` | 128-block isolation cannot establish nearest identity for a 256-block observer; competing UUID unlogged. | Fixture | `test/FluffyJumperTests.java`, `test/GameTestChunkDiagnostics.java` | Exclude unrelated players over the complete observer distance; preserve nearest identity and actual vanilla despawn, with detailed preconditions. |

The cold command fixtures retain FULL-only tickets for a two-chunk halo, so block
generation finishes **before** the service's 40-tick clock begins. These fixture
tickets do not enable simulation. Warm fixtures alone force chunks as before.
Production command accompaniment still adds no tickets. Targets are separate
from the shared Vale arrival, spawn region and population fixtures.

Batch names do not create separate worlds. The vanilla runner runs batches
sequentially, but shares levels, SavedData, players/tickets and static subscribers;
cleanup is therefore necessary on failed assertions as well as success. No
unscoped event listener, duplicate dispatcher installation or owner UUID reuse was
found in these fixtures. Existing UUID and owner scoping is preserved; no global
registry reset was added.

## External validation

Static checks: `git diff --check` **PASS**; changed Java whitespace and all 31
journey/command batch cleanup registrations checked. No compiled/runtime success
is inferred from these checks. Test names/count remain 17 journey + 14 command.

Developer terminal, Java 17:

```bash
./tools/test.sh build
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
./tools/test.sh gametest
```

All three commands above: **NOT RUN** here. Review the expected test counts,
`FAMILY_TELEPORT_COLD`, `LANDMARK_FIREFLY_READY`, revised arrival logs and any detailed
eligibility/despawn assertion. Full-suite validation must include existing Forge
vetoes, rejected insertion/source restoration, group rollback, UUID uniqueness,
landmark/firefly behavior and quest progression. Manual Minecraft/BMC4 acceptance
and publication remain separate statuses.

## HOME fixture follow-up — run `20261015T083658Z-837218`

Developer validation improved to **30/31** family-journey tests. The remaining
failure is `HOME fixture command accepted with a valid room`, before teleport
exclusions execute. The first correction still constructed the roof and called
`commandHome` in the same synchronous assertion callback.

The failed prerequisite is roof readiness: `HomeRules.inspect` uses
`!level.canSeeSky(anchor.above())`, and the pinned Minecraft source implements
`canSeeSky` using SKY light brightness, not a synchronous roof-block/heightmap
lookup. Placing stone queues asynchronous lighting; immediately refreshing
`marker.conditions(true)` only rereads the existing light data. Thus the newly
built roof is not yet recognized, `validFor`/`homeAnchor` reject it, and
`commandHome` short-circuits. Existing `FamilyTests.readyAtmosphere` already waits
for this same lighting prerequisite. The old failure log did not record the
numeric skylight value; no retrospective runtime value is claimed.

The fixture's Marusya marker is claimed by the owner, matches Kind, and is bound
with `HomeMarker=true` in the source dimension. Bed, barrel, torch and cake are
inside the inspection volume. The original platform leaves standing space south
of the marker, away from furniture, and the owner is alive, nonspectating, in the
same dimension and well within the 64-block command range. These gates are now
each checked explicitly, including `homePosition`, before attempting HOME.

Only `test/FamilyTeleportTests.java` and this document changed in this follow-up.
Room construction now precedes the readiness sequence. A condition callback
refreshes marker conditions and checks every HOME prerequisite, allowing actual
lighting to complete within the unchanged 500-tick test limit. It never sets HOME
through NBT or substitutes FOLLOW. The real `commandHome` success assertion,
all exclusion checks, exact source positions, live UUID assertions and cleanup
remain. `FAMILY_TELEPORT_HOME_PREPARED` records initial room flags/skylight; failed
readiness assertions identify the specific gate and companion/anchor.

Production HOME, teleport, visual, firefly and music behavior are unchanged.
Follow-up build, GameTests and manual acceptance: **NOT RUN**. External validation:
`DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest` (Java 17). No commit/publication.
