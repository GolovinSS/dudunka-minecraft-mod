# Stage 4.4 — prepared dedicated runtime fixtures, 2026-10-16

Implemented locally. Python evidence-parser regressions: **11 PASS**. Compilation,
GameTests, dedicated warmup/smoke/runtime validation, Minecraft/BMC4 and networked
client acceptance: **NOT RUN**. No runtime acceptance claim. Production
`FamilyTeleport.java`, Mushroom Bells, gameplay, assets and version are unchanged.
This supersedes the fixture architecture and run/count expectations in
[the queue correction](VALIDATION-TELEPORT-FIXTURE-QUEUE-FIX.md) and
[the watchdog correction](VALIDATION-TELEPORT-DEDICATED-WATCHDOG-FIX.md).

## Evidence and cause

Read the complete **1,275-line** failed log at
`build/reports/dudunka-tests/20261015T224805Z-1147821/gametest.log`, and read only the
26 actually issued chunk records from that disposable world's saved MCA files.
No Minecraft or generation was run during this investigation.

- Runtime batch started 01:48:47, dispatch began 01:48:48 and preparation timed
  out at 01:48:56: approximately eight wall-clock seconds for 400 fixture ticks.
- All 13 sites received exactly two tickets (26 total); 27 status records show
  progression through world generation. One request reached FULL near timeout.
  Every site timed out before a platform or teleport command was executed.
- At shutdown, the saved status of those 26 chunks was `minecraft:spawn` for 25
  and `minecraft:full` for one. The overworld save/flush took another 39 seconds
  (01:49:04–01:49:43). `spawn` is the last generation stage before FULL; this is
  evidence of continued generation, **not** proof those 25 became loaded FULL
  chunks or finished entity-data loading after their tickets were released.
- There is no demonstrated generation deadlock. Dispatch worked. The two-pending-
  requests-per-site gate limited issuance to 26 while those requests generated.
  The expensive setup, scattered geometry and accelerated test deadline made
  meaningful execution impractical. A single `parkNanos` yield did not establish
  a 20-TPS clock and is removed, along with the per-site queues.
- `forgevetoandfailednativeinsertionretainexactsources` shares this preparation
  failure. Its veto/insertion assertions were not reached. The final failed list
  contains precisely these 13 runtime cases; it establishes no independent
  production or unrelated GameTest defect.

Inspected pinned Forge 47.4.13 mapped sources: `GameTestServer.waitUntilNextTick`
runs tasks without normal tick pacing; `GameTestInfo.startExecution` starts its
setup/test clock before fixture callbacks. `ChunkMap.prepareEntityTickingChunk`
requires a radius-two FULL neighbourhood. `StructureUtils.forceLoadChunks` uses
chunk offsets -1 through +3. `ServerPlayer`'s constructor performs a synchronous
Overworld spawn search. `ServerChunkCache.getChunk` **and** `getChunkFuture` can
managed-block on the server thread. None is used to prepare cold chunks here.

## Geometry and footprint

The old per-site departure was `(1544 + (site % 4)*256, 160,
-1528 - (site / 4)*128)`, with the destination 128 blocks east, in either the
Overworld or Vale. Each site requested:

| Purpose | FULL chunks |
| --- | ---: |
| Source radius-two halo | 25 |
| Destination radius-two halo | 25 |
| Default native player spawn search | 4 |
| Per-site total | 54 |
| All 13 sites, including repeated spawn requests | 702 |
| Distinct dimension/chunk pairs across those sites | 654 |

A platform is only **11×11** blocks (floor Y159, clearance Y160–164). Its centre
is chunk-local `(8,8)`, so its edits occupy one chunk, local coordinates 3–13.
The family and bounded safe landing probes fit on it. Twenty-five FULL chunks
per operational region remain appropriate for native entity-ticking readiness;
reducing that halo to one would merely defer its generation to player arrival.
These counts exclude vanilla's larger transitive world-generation dependencies.

The new anchor is the Overworld spawn chunk's centre at Y160. The source pad is
64 chunks east and 64 chunks north of it; the same-dimension destination is
another eight chunks east. The Vale destination uses that destination's X/Z.
The existing `dudunka_journey:empty` template is 6×5×6 blocks; its clearing
padding fits the prepared template area. The three operational regions cannot
be promoted by the template's forced area.

| Shared region, prepared once per fresh server | FULL chunks |
| --- | ---: |
| Overworld source | 25 |
| Overworld destination | 25 |
| Vale destination | 25 |
| Native test-template area, offsets -1..3 | 25 |
| Spawn-constructor search, deduplicated against template area | Usually 0 extra; maximum 9 |
| Total explicit FULL requests | Normally **100**, hard cap **109** |

Default spawn radius is 10. The spawn search includes a one-block collision
margin, with a supported radius cap of 31; an unsupported configuration fails
before tickets are issued. Actual dimension/chunk coordinates and exact request
count are logged. Native player view/loading and generation dependencies can
load additional chunks; 100/109 bounds the fixture's explicit requests, not the
entire server's chunk population.

## Lifecycle and environment split

Use the normal, paced **dedicated server** for the 13 expensive runtime cases.
They remain actual native Forge GameTests, each with the unchanged **500-tick**
deadline, real ServerPlayer/Companion objects, actual vanilla Brigadier command
execution and production Forge listeners/ticks. They are not helper substitutes.
Their registration is opt-in through `RegisterGameTestsEvent`, with an explicit
template namespace. Ordinary GameTestServer no longer discovers these 13 cold
integration cases. Its existing focused journey/teleport tests are untouched;
currently the journey namespace contains **31** such tests rather than 44.
Running that focused suite alone does **not** validate the runtime integration.

The split is deliberate: cold generation has no reliable wall-clock allocation
inside accelerated GameTestServer's 500-tick lifetime. More pacing or a longer
assertion timeout would retain this architectural mismatch. A custom lightweight
world was considered, but would change generation/player-spawn assumptions and
still require Overworld constructor preparation. The dedicated warmup keeps the
real worlds and moves their cost outside assertion clocks. The new readiness
smoke is a native GameTest on this dedicated server, not a claim of cold-world
GameTestServer coverage.

1. The runner creates a new work directory/world, enables the test-only JVM
   property, writes a disposable marker and starts the dedicated server. The
   fixture refuses mutation without the dedicated type, explicit opt-in, marked
   `dudunka-teleport-server-*` parent and `world` directory. The existing Gradle
   fresh-world guard rejects an existing `world/level.dat`.
2. Console `dudunka_test_teleport prepare` registers one finite request list. A
   Forge server-END-tick callback issues **at most four FULL tickets per tick**;
   there are no per-site queues, starvation slots or completion-gated issuance.
   All tickets are level 33, `forceTicks=false`.
3. Poll only `getChunkNow` and `areEntitiesLoaded`. No blocking future, cold
   `getChunk`, `getChunkFuture`, sleep or tick pacing override is used. Separate
   warmup limits are **180 wall-clock seconds / 3,600 server ticks**; a timeout
   fails with progress and bounded pending-coordinate/holder samples. These
   limits do not change any runtime test deadline.
4. Once every requested chunk has FULL plus entity data, build **one platform
   per tick**, three in total. `WARMUP_READY` starts no GameTest by itself.
5. The runner issues `dudunka_test_teleport run <native-test-name>` one case at a
   time. This calls native `GameTestRunner.runTest` and `GameTestTicker`; native
   test callbacks report success/failure. Each case snapshots the prepared pads,
   asserts their clean baseline, creates unique owners/companions and waits for
   source/destination simulation to demote before substantive commands.
6. Cases retain all existing UUID, original-object, owner, NBT round-trip,
   friendship/memory, registry, safe landing, STAY/HOME, Forge veto, insertion
   restoration, supersession, duplicate and forced-chunk checks. Warm-source
   cases still use native player loading, then assert genuinely loaded but
   non-entity-ticking sources after departure. No simulation ticket is retained
   by the fixture. Existing production-timeout/journey rollback tests are
   untouched in the focused GameTest suite.
7. Cleanup restores each pad's block baseline, discards that owner's live
   companions and removes players/listeners. Native failure callbacks also
   clean up immediately. Only one scenario holds the reusable regions; duplicate
   or concurrent commands are rejected. Fresh owner UUIDs isolate saved ledgers.
   Shared FULL tickets retain data through cleanup and are released on stop or
   warmup failure. There is no cross-server/session queue state.
8. The runner requires matching runtime completion **and native testPassed** for
   every expected name/site, then sends `stop`. Zero exit, normal stop and all
   dimensions saved are mandatory. It drains shutdown output and rejects late
   crashes, duplicate/missing cases, incomplete warmup and unexpected cases.
   It requires exactly 13 annotated cases and unique sites 0–12 in the source inventory.

Cold probe `freshServerDispatchesAndCompletesFixtureQueue` now asserts warmup
history: the source began without a FULL chunk, actual server callbacks issued
all requests, all remain FULL/entity-data-ready, and all three platforms completed
before the probe's 500-tick clock. It also checks the live source and destination
remain non-entity-ticking. `--smoke` executes this probe only after a fresh warmup;
its report explicitly says `scope=fixture-smoke`, never full runtime coverage.

## Files and diagnostics

- `FamilyTeleportFixture.java`: opt-in registration/console orchestration,
  guarded disposable setup, bounded warmup, native completion callbacks, cleanup.
- `FamilyTeleportRuntimeTests.java`: remove cold per-site queues and pacing;
  lease prepared pads, preserve the 12 scenarios, adapt the cold probe to setup
  history and assert baseline restoration between cases.
- `tools/test-teleport-dedicated.py`: warmup then sequential native cases, smoke
  scope, strict complete-evidence/shutdown accounting and fresh marker.
- `tools/test_teleport_dedicated_report.py`: 11 pure parser regressions including
  missing passes, incomplete readiness, inactive dispatcher, overlapping cases,
  duplicate markers, late failure/watchdog and abnormal shutdown.
- This document and `docs/FAMILY-TELEPORT.md` describe the split.

INFO records distinguish `WARMUP_REGISTERED`, `DISPATCHER_ACTIVE`, `PROGRESS`,
`PLATFORM_READY`, `WARMUP_READY`, `TEST_START`, `READY_FOR_COMMANDS`, native
`TEST_PASS`, and `INTEGRATION_FAILED`. Progress every five seconds includes
requested/issued/queued/in-flight/FULL/entity-data counts, newly generated FULL
count, initial cold/source-cold state, elapsed wall time,
cycle and platform counts, with at most four unready chunk samples. DEBUG records
list each registered coordinate. Runtime simulation-settle failure logs source
and destination state once at tick 400; native timeout remains 500.

The old watchdog mechanism cannot occur through cold platform preparation:
platform writes are gated on all required FULL/entity data, bounded to one small
pad per tick. Native constructor/template accesses likewise use prepared regions.
This is not a claim that arbitrary engine/mod work can never stall a tick; the
watchdog and its configuration are unchanged.

## External validation (JDK 17)

```bash
./tools/test.sh build
# Fresh readiness smoke, independently scoped; accepts EULA only when explicitly set:
DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py --smoke
# All 12 real command cases + cold preparation probe, fresh dedicated world:
DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py
# Existing focused GameTestServer regressions, fresh world (currently 31):
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
# Cheap runner/parser checks only:
python3 tools/test_teleport_dedicated_report.py
```

Expect full-run summary `scope=runtime-13`, `runtime_test_count=13`, 13 distinct
native passes and completion sites, `warmup_ready=True`, `normal_shutdown=True`,
and `dedicated=PASS`. Smoke expects exactly one probe and clean shutdown. Preserve
all failed/fresh worlds and reports for investigation; no existing world is
reused or deleted. World generation performance may still hit the explicit setup
bound on slow hardware: that is a diagnosed setup failure, never runtime PASS.

Remaining coverage limits: no real socket login/Forge handshake, signed client
chat, graphical/BMC4 acceptance or process-restart save/reload. The existing
in-process packet adapter uses native acceptance/movement handlers. Full cold
preparation is now deliberately dedicated-only; asynchronous setup and native
runtime cases still need the external executions above.
