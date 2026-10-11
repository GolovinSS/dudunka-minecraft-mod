# Mushroom Vale Stage 3.7b — Return Gateway

Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, Better MC BMC4 v55.5.
Based on the authoritative local working tree on `main`, HEAD
`a29d8080fb910471f0a09c387a2715588d5d80ba`, initially two commits ahead of
`origin/main`, with modified and untracked Stage 3.7 code/assets/docs preserved.

The developer reports Stage 3.7 automated validation passed, including
`prepare_family`, and accepted local `runClient` appearance, awakening and entry.
The latest user handoff confirms local manual acceptance of both gateways
on 2026-10-12. The authorized automated
stabilization runs and corrections are recorded below. No manual client launch,
production-save operation, commit, push, PR or publication was performed.

| Status | Evidence |
| --- | --- |
| Implementation | Complete in local working tree |
| Cheap static checks | `git diff --check` and touched untracked-file whitespace checks; English/Russian localization JSON/key parity checked |
| Stage 3.7b build/resource/model verification | **PASS**, run `20261011T220402Z-3213750` |
| Nine Stage 3.7b GameTests and existing regression suite | **PASS**, all 245 required tests, run `20261011T220426Z-3214532` |
| Local `runClient` gateway acceptance | **PASS**, user-confirmed for both gateways, 2026-10-12; detailed real restart checks remain separate |
| Local multiplayer acceptance | **NOT RUN** |
| Dedicated BMC4 server, existing-save upgrade and real restart | **NOT RUN**, postponed |
| Publication | Commit/push authorized on 2026-10-12; no JAR release |

The investigation and stabilization notes below are chronological evidence; their
earlier **NOT RUN** statements are superseded by the current status table above.
Real multiplayer, dedicated BMC4 and real dedicated-server restart remain **NOT RUN**.

## Behavior

- Successful physical Overworld gateway entry creates or reuses a visible active
  return gate near the safe Vale arrival. Failed entry creates no gate.
- Existing gateway block, block entity, models, particles and twelve-part structure
  are reused. No new registry ID, dependency, packet or block property is needed.
- A complete Vale gate returns any eligible clicking player through the existing
  player-only travel service. No ritual, family presence, trust, friendship or
  personal gateway unlock is required for return.
- One shared gate has no destination of its own. Each click uses that player's
  UUID-keyed bookmark. Successful return removes only that bookmark; denied or
  unsafe return retains it. Missing/unsafe destinations use the established safe
  Overworld spawn fallback, preserving the bookmark if that transfer also fails.
- Ordinary chunk states/root block entity persist the gate. Re-entry discovers
  and reuses the existing frame after serialization/reload, including when its
  saved NBT is still deferred before live block-entity instantiation. Existing partial
  frames prevent duplicate automatic construction and are not destructively repaired.
- Search stays in the already loaded arrival chunk (0,0), within horizontal radius
  15, with terrain-relative height. At most 120 roots fit the interior geometry. No normal
  placement/reuse checks load chunks, force-load, create tickets or start a task.
- New sites require a dry supported floor, a clear frame plane, entity clearance,
  world-border/build limits, placement permissions and at least one safe
  terrain-relative approach among six north/south positions.
  Replaceable noncolliding flora and floor-level moss carpet may remain in unused side-clearance cells;
  existing structures, terrain, vegetation, fluids and container contents are
  never overwritten. Shared Forge multi-place veto restores all snapshots and
  terminates the placement attempt without consuming a held item.
- Gate placement failure preserves the successful arrival and bookmark, explains
  `/dudunka return`, and permits a safe attempt on a later physical entry.
- Re-entry prefers clear approach positions beside the existing gate. Standing
  on the gateway frame or mushroom roof is rejected as a travel landing floor.
- Developer travel does not automatically construct a gate. Players already in
  Vale when upgrading keep the public return command and can obtain a gate on a
  later physical visit. A fully removed gate may be placed again on later entry.
- Companions are never searched for, transferred, cloned, recreated or assigned
  new identities by the exit. UUIDs, ownership, care/home/furniture NBT, carrier,
  friendship, recovery, dimensions and album protocol 9 remain intact.
- Quests and music restoration are deferred.

## Changed files for Stage 3.7b

Paths below are relative to the repository root; pre-existing unrelated local
modifications remain in place.

| Area | Files |
| --- | --- |
| Bounded placement and reuse | `src/main/java/io/github/golovinss/dudunka/MushroomValeReturnGateway.java` (new) |
| Shared placement preflight/transaction | `src/main/java/io/github/golovinss/dudunka/FamilyGatewayItem.java` |
| Dimension-specific interaction and post-entry placement | `src/main/java/io/github/golovinss/dudunka/FamilyGatewayEntity.java` |
| Safe arrival beside the gate | `src/main/java/io/github/golovinss/dudunka/MushroomVale.java` |
| Focused tests, unsafe physical return and fixture restoration | `src/main/java/io/github/golovinss/dudunka/test/FamilyGatewayTests.java` |
| Player feedback | `src/main/resources/assets/dudunka/lang/en_us.json`, `ru_ru.json` |
| Guide, architecture, acceptance and status | `README.md`, `docs/FAMILY-PORTAL.md`, `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION-MUSHROOM-VALE-STAGE-3.7.md`, this report |

## Nine Stage 3.7b GameTests

All are in `FamilyGatewayTests`, with separate isolated batches and existing
fixtures. Arrival restoration now snapshots root block-entity NBT and removes
gateway roots before restoring blocks, so teardown cannot erase restored cells.

| Test | Assertions |
| --- | --- |
| `returnGatewaySupportsRepeatedPhysicalRoundTripsWithoutMovingFamily` | Actual ritual/entry/block-click return, active complete exit without owner unlock, repeat command-free round trip, one shared frame, safe arrival beside it, companion instances and complete NBT unchanged |
| `returnGatewayAndTwoPersonalBookmarksSurviveSerializationTogether` | Two distinct departure/arrival points, shared gate reuse, deferred chunk NBT discovery, registered block-entity deserialization, detached bookmark deserialization and independent live per-player returns, vanilla player dimension saves, reverse-order returns to each player's own destination |
| `returnGatewayFitsThroughNaturalFoliageWithoutChangingNearbyTerrain` | Previously activated physical entry places a complete frame beside grass on valid soil and floor-level moss carpet; both remain in the selected side clearance; nearby plants, water and a diamond-filled chest remain unchanged; physical return uses the exact bookmark |
| `returnGatewayProtectionVetoRollsBackWithoutLosingTravelOrItems` | One Forge multi-place veto, all cells/root BE rolled back, successful travel/bookmark/held items retained, later safe placement and physical return work |
| `returnGatewayTravelVetoRetainsBookmarkForPhysicalRetry` | Entry veto leaves no frame; return veto preserves exact position and bookmark; deliberate retry returns and consumes only that bookmark |
| `returnGatewayPlacementAndReuseNeverLoadOrForceAdditionalChunks` | All frame cells stay in arrival chunk; placement/reuse/out-of-area refusal leave loaded/forced chunk counts unchanged, measured synchronously after travel and detached-player setup |
| `returnGatewayIncompleteStructureCannotDuplicateAndCommandStillWorks` | Partial saved frame creates no second gate; broken interaction retains bookmark; shared command travel remains available |
| `returnGatewayAvoidsOccupiedSitesAndHazardousGround` | Occupied site avoided with resident identity/full NBT intact; sturdy magma ground cannot support placement; command return remains usable |
| `previouslyActivatedPhysicalGatewayCreatesExitOnNaturalValeArrival` | Uses generated Vale terrain without the flat `Arrival` fixture; a previously unlocked physical gate creates a visible active return gate in the one arrival chunk and retains the per-player bookmark |

Existing `gatewayUnsafeReturnPreservesBookmarkUntilDepartureIsRepaired` also clicks
the physical exit with both saved/fallback destinations unsafe, asserting unchanged
location and bookmark before its existing command-path failure/retry assertions.
Existing assertions are retained; serialization tests do not restart a real server.

## External validation and remaining limits

### GameTest failure triage — run `20261011T201738Z-3169178`

Build passed; 244 GameTests ran and two failed. The log's `LogTestReporter` lines
contain the test name and assertion message but no Java exception stack trace.

| Test | Reported assertion | Previous attempted correction (superseded diagnosis below) |
| --- | --- | --- |
| `returngatewayblockedplacementpreservesterraincontainersandcommandexit` | `Fallback command uses the same saved personal departure` | Suspected replacement of live `MushroomValeReturns` SavedData by the multiplayer persistence fixture. Changed that fixture to deserialize a detached copy and use the original live entries for returns. This isolation precaution is retained, but the recorded batch order rules it out as the cause of this failure. |
| `returngatewayplacementandreuseneverloadorforceadditionalchunks` | `Placement, reuse and out-of-arrival refusal introduce no loaded chunks or force-loads` | Removed the global loaded-count comparison and replaced the live-player remote probe with a detached player. Retained remote-unloaded, forced-set and frame-boundary checks. The old compound assertion did not identify its failing subexpression; unrelated concurrent tests were not established as its cause. The detached probe introduced the exception documented below. |

The saved log does not provide finer failure subexpressions or stack traces. The
two test assertions above were the reporter's complete failure details.

### Persistent-failure investigation — run `20261011T203510Z-3176230`

Both supplied logs report 244 completed tests and the same two required test
names. The latest wrapper skipped its separate build phase; the preceding run
reported build PASS. This investigation did not execute either build or GameTests.

**`FamilyGatewayTests.returnGatewayFitsThroughNaturalFoliageWithoutChangingNearbyTerrain`**

- Exact assertion in both runs: `Fallback command uses the same saved personal departure`.
  The compound condition required the original dimension, exact saved block position
  and consumption of the player's bookmark. The inward departure correction is
  retained, and this case now verifies successful placement through side-clearance
  foliage plus exact physical return.
- Proven fixture defect: read-only inspection of each retained world's
  `world/region/r.0.0.mca` finds this test's structure block at `(280,-60,0)`,
  rotation `NONE`. `GameTestHelper.absolutePos(8,4,8)` therefore puts the gateway
  root at `(288,-56,8)`. `Fixture.player()` starts its first player at
  `(288.5,-56,11.5)`, so entry saves block position `(288,-56,11)`.
  Its local chunk X is zero.
- Production trace: physical interaction calls `MushroomVale.enterPlayer`,
  which records that position. The public command calls `returnPlayer`, then
  `landingNear`. The latter applies `insideChunk` even to radius-zero candidates,
  rejecting local X/Z 0 or 15. It can select a safe nearby position in the same
  saved chunk. Exact return to this fixture's original position is thus impossible
  under the established bounded landing rules. The positional mismatch does not
  require a missing bookmark.
- Correction: move only this fixture's departure one block inward on any boundary
  axis, before entry. For the recorded layout it becomes `(289,-56,11)`. Assert
  that it is interior and safe, that entry records this exact destination and that
  it remains safe. Retain exact return equality, dimension equality and bookmark
  consumption as separate assertions, with expected/actual position diagnostics.
  Terrain, fluids and valuable-container preservation assertions remain intact.
- Why the previous fix missed it: the reload batch starts at log line 2550 in the
  older run and 2554 in the latest, long after this failure at 1155 / 1161. It
  cannot have caused this earlier failure. Detaching SavedData deserialization
  did not change the boundary departure.
- Code file changed: `src/main/java/io/github/golovinss/dudunka/test/FamilyGatewayTests.java`.

**`FamilyGatewayTests.returnGatewayPlacementAndReuseNeverLoadOrForceAdditionalChunks`**

- Previous assertion: `Placement, reuse and out-of-arrival refusal introduce no loaded chunks or force-loads`.
- Latest failure is a new setup regression, before the revised no-load assertion:
  `Cannot invoke "net.minecraft.server.network.ServerGamePacketListenerImpl.resetPosition()" because "this.connection" is null`.
- Proven fixture defect from the locally cached Forge 47.4.13 Minecraft sources:
  `Entity.moveTo(Vec3)` dispatches to `ServerPlayer.moveTo(double,double,double)`;
  that override calls `this.connection.resetPosition()`. The previous fix constructs
  a detached `ServerPlayer` without a connection and then calls `moveTo(Vec3)`.
  The exception occurs before `MushroomValeReturnGateway.ensure(remotePlayer)`.
- Production trace: `ensure` first rejects any player outside the Vale arrival
  chunk, before any chunk lookup. Placement/reuse inside that chunk uses
  `getChunkNow`, loaded-state checks and interior frame/approach bounds. The latest
  failure supplies no evidence of an additional chunk being loaded by that code.
- Correction: retain the detached player, attach a no-op
  `ServerGamePacketListenerImpl`, then position it with
  `moveTo(BlockPos,float,float)`. Assert that the probe is absent from the level
  entity lookup and its remote chunk is initially unloaded.
  Restore the loaded-count comparison around only the synchronous placement,
  reuse and refusal calls. Construct/position the probe before taking the baseline:
  `ServerPlayer` construction itself runs spawn-position checks that can load
  chunks in a dimension with skylight. Keep frame-boundary, refusal, remote-unloaded
  and forced-set checks, using separate diagnostic assertions.
- Why the previous fix missed it: the BlockPos movement overload still invokes
  `ServerPlayer`'s connection position-reset hook. The probe needs a stub listener
  even while detached. Removing the original count assertion also reduced coverage;
  this correction restores it after fixture setup.
  The older log alone cannot establish which subexpression of its compound
  assertion failed; this investigation does not claim to have recovered that value.
- Code file changed: `src/main/java/io/github/golovinss/dudunka/test/FamilyGatewayTests.java`.

**Isolation review.** These two tests execute their assertions synchronously inside
try-with-resources and restore arrival blocks/block-entity NBT and their own
UUID-keyed bookmarks on exit. The one-test batches run sequentially in vanilla's
`GameTestBatchRunner`; a same-second batch log is not evidence of concurrent
test bodies. `GameTestInfo` stops ticking completed tests, so the fixture's
tick-195 fallback callback cannot subsequently run in another completed test's
context. No current test replaces live return SavedData. Shared spawn changes in
the neighboring unsafe-return fixture are restored in `finally`. Forge config
corrections in the logs occur during fresh-world startup; neither failed path
depends on growth, loot, catch-up or egg-generation settings. The concrete causes
above do not require a configuration leak, stale save, delayed callback or another
GameTest's terrain mutation. Automatic gate placement still adds no persistent
force-loads. Production code, save formats and companion identity are unchanged.

### Local client investigation — 2026-10-12

The current `run/logs/latest.log` records two successful entries through an
already awakened Overworld gateway. Both are followed by the return hint and the
localized `message.dudunka.gateway.return_unavailable`; therefore interaction did
reach `MushroomValeReturnGateway.ensure`, and placement returned `null` without
being reported as success. The same failure appeared on repeated entry.

Read-only inspection of the local `тп 2` Vale region showed naturally generated
forest terrain with harmless plants and decoration. The previous automatic
preflight reused the manual item's air-only 3 × 3 × 4 clearance check even though
the frame writes only its central 3 × 4 plane. It rejected any non-air state in
the unused side clearance, including replaceable noncolliding Vale flora. The
flattened `Arrival` fixture cleared the whole volume to air, so it could not
reproduce that natural-terrain placement rejection. No save data was modified.

Correction: automatic return placement now keeps the frame plane, all support,
fluid, border, permission, entity, protection-veto and approach checks strict,
while accepting only replaceable, empty-collision, dry and nonhazardous states in
the unused side clearance. It leaves those blocks untouched. Manually placed
gateways keep their previous strict air-only rule. The bounded radius and one
already-loaded arrival chunk are unchanged. New coverage exercises natural Vale
worldgen, an already activated physical gate, preservation of side foliage and
localized failure feedback plus the public command fallback.

Why the earlier corrections did not fix the visible issue: they repaired test
bookmarks and chunk-count fixture setup; they did not alter the production
air-only preflight or exercise generated Vale terrain. The code already called
`ensure` correctly and already sent a failure message, but the placement predicate
rejected every candidate that intersected harmless foliage.

Changed files in this correction: `FamilyGatewayItem.java`,
`MushroomValeReturnGateway.java`, `FamilyGatewayTests.java`, `MUSHROOM-VALE.md`
and this report. Build/resource/model validation and GameTests: **NOT RUN**.
Minecraft/BMC4 manual acceptance: **NOT RUN**.
Publication: not performed.

Run externally with Java 17:

```bash
./tools/test.sh build
./tools/test.sh gametest
# Or:
./tools/test.sh all
```

Share the generated summary and build/GameTest logs. Then follow
[TESTING.md — local Stage 3.7b acceptance](TESTING.md#stage-37b-return-gateway-in-local-runclient)
for visible placement, repeated physical round trips, real save/reload, local
multiplayer and failure/retry checks. Dedicated BMC4 acceptance stays postponed.

Compilation and runtime behavior await those external checks. Native visuals are
reused, but appearance in Vale still needs manual acceptance. Uneven, obstructed
or protected arrival terrain may legitimately prevent gate placement; the command
is the supported exit in that case. Protection integrations and real multiplayer
restart need manual checks beyond serialized fixtures.

No save migration is introduced. Normal persistence uses the existing chunk and
bookmark formats; Minecraft does not save playerdata, chunks and SavedData as one
atomic transaction. Crash/partial-backup recovery still requires a consistent
whole-world snapshot. No dedicated BMC4 compatibility or publication is claimed.

### Authorized autonomous stabilization — 2026-10-12

This session explicitly authorized local builds and the full GameTest suite through
`tools/test.sh`. The shell's default Java was 8; every validation run selected the
installed JDK 17 using `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
All GameTest worlds were fresh wrapper-created directories. No production/player
save was opened or changed, and the original 44 dirty/untracked entries were retained.

**Compiler failure `20261011T215520Z-3208901`: all five errors.**
The `Heightmap` import in `MushroomValeReturnGateway.java:9` named the nonexistent
`net.minecraft.world.level.Heightmap`. Its two uses at lines 39 and 64 consequently
failed too. `FamilyGatewayTests.java:664` and `:665` repeated that incorrect fully
qualified package. Both files now import Minecraft 1.20.1's
`net.minecraft.world.level.levelgen.Heightmap`. The 15 deprecation warnings were
unrelated and were not cleaned up. Build `20261011T215801Z-3210487` then passed.

**Full GameTest run `20261011T215822Z-3211237`: 245 completed, two failed.**
The complete log reports exactly these failures:

- `returnGatewayFitsThroughNaturalFoliageWithoutChangingNearbyTerrain`:
  `The selected return site actually uses replaceable natural foliage in side clearance`.
  The fixture installed only the lower halves of tall grass directly on stone.
  Those states cannot survive normal neighbor updates from placing the frame;
  the post-placement check therefore found no remaining foliage. The fixture now
  uses single-block grass on dirt and supported moss carpet. It explicitly checks
  grass survival, requires both kinds to remain in the selected side clearance,
  and retains all terrain, water, diamond-container and exact-return assertions.
- `previouslyActivatedPhysicalGatewayCreatesExitOnNaturalValeArrival`:
  `An owner with a previously activated physical gateway receives one exit on first natural arrival`.
  Read-only inspection of this isolated world's saved arrival chunk found a clear
  frame plane and sturdy 3 × 3 floor at root `(2,66,10)`, with moss carpet at
  `(1,66,11)` and `(2,66,11)` in unused side clearance. Other supported clear-frame
  candidates intersected colliding terrain or decoration in that clearance.
  Moss carpet has a thin collision shape, so the previous empty-collision rule
  rejected this otherwise safe site. `FamilyGatewayItem` now permits exactly
  moss carpet at root-floor height in unused side cells, leaving it untouched.
  All twelve frame cells still require air; support, fluids, hazards, entities,
  permissions, build/border bounds, safe standing landings and Forge veto/rollback
  still apply. Manual item placement remains air-only, with an added moss-carpet
  rejection case. No block replacement or general colliding-block allowance was added.

`FamilyGatewayTests` also removes its newly created natural-surface frame in
`finally`. The old natural test had no such cleanup; after successful placement,
that gate could have leaked into later flat-arrival fixtures, which restore only
Y=300–305. This was a latent fixture isolation defect, not a failure observed in
the first run, where natural placement failed. Shared configuration, live return
SavedData, mock connections and delayed callbacks did not cause either recorded
failure. The existing fixes for those earlier fixture concerns were retained.

The previously implemented radius 0–15, terrain-relative frame/approach heights,
at least one safe approach, loaded-arrival-chunk bounds and partial-frame duplicate
prevention remain intact. Source corrections are limited to
`MushroomValeReturnGateway.java`, `FamilyGatewayItem.java` and
`test/FamilyGatewayTests.java`; the guides and this report reflect the actual
placement rules and validation status. No registry, protocol or save-format change
was introduced.

Final build `20261011T220402Z-3213750`: **PASS**, including build/check and the
existing resource/model verification tasks. Final GameTest run
`20261011T220426Z-3214532`: **PASS**, all 245 required tests completed successfully.
The run used the wrapper's unchanged seed `-255494441983840131`, including the
same generated arrival terrain that failed before the correction.

Final `git diff --check` and whitespace checks for all untracked files: **PASS**.
The remaining local `runClient` acceptance includes visible placement on the
developer's actual terrain, repeated physical round trips, real save/reload and
local multiplayer. Dedicated BMC4 and protection-plugin acceptance remain
**NOT RUN**. More obstructed or protected arrival chunks may still legitimately
require `/dudunka return`; no unsafe construction is forced to make an exit appear.

Reproduction commands (already executed successfully in this session):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
git diff --check
```

Full evidence is retained under
`build/reports/dudunka-tests/20261011T220402Z-3213750/` and
`build/reports/dudunka-tests/20261011T220426Z-3214532/`. The failed first GameTest
run is retained too. No commit, push, publication or destructive change was made.
