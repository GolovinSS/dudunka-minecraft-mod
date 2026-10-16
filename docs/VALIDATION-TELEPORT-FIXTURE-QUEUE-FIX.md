# Stage 4.4 — fixture queue starvation correction, 2026-10-16

Implementation: **changed locally**. Cheap static checks: **PASS**. Compilation,
GameTests and dedicated-server verification of this change: **NOT RUN**. Runtime
Minecraft/BMC4 acceptance remains open. No production `FamilyTeleport` change,
watchdog setting, version change, commit or publication.

## Confirmed diagnosis from the full failed log

Reviewed all 1,340 lines of
`build/reports/dudunka-tests/20261015T221453Z-1134650/gametest.log`, including startup,
the full runtime batch, subsequent regressions, final test counts and shutdown.
The run used a fresh `run/dudunka-test-20261015T221453Z-1134650` world.

The dispatcher was **not completely inactive**:

| Evidence | Observed |
| --- | --- |
| Registered sites | 12, each with 54 requests |
| Issuance records | 41 for site 3, then 4 for site 4: **45 total** |
| First preparation timeout | Site 3: issued 41/54, queued 607, in-flight 16 |
| Generation status at that timeout | Vale holders at BIOMES / STRUCTURE_STARTS; source FULL and entity data ready |
| Platform / runtime completion records | 0 / 0 |
| Final result | 43 tests completed; exactly the 12 runtime tests failed |

The original shared FIFO placed all 54 requests for the first registered site
before the next site's requests. Its global 16-request in-flight limit filled
with that first site's unfinished Vale chunks. No other site had independent
capacity, so the remaining cases starved. The pump itself was called from
`Trip.ready()` and gated once per `MinecraftServer.getTickCount`; the log proves
that this gate advanced and issued work over multiple ticks.

At test tick 400, timeout callbacks ran and `close()` removed the first site's
queued and in-flight work. Site 4 received four requests during that timeout
frame, then its timeout closed those too. Remaining callbacks reported the
consequential states, including `globalQueued=216, globalInFlight=0`. Those are
**cleanup-time snapshots**, not evidence that the initial dispatcher never ran.
The tick's issuance budget had already been used; the other sites then closed
without another normal preparation tick.

There was also a concrete environment difference: this batch started at
01:15:35 and reached its 400-tick preparation deadlines at 01:15:37. The pinned
`GameTestServer.waitUntilNextTick()` only calls `runAllTasks()`, unlike the normal
paced server loop. Cold asynchronous generation received roughly two seconds
before the same tick-based deadline expired. This is established from the source
and timestamps, not an assumption that generation merely needed a larger timeout.

No queue subscription was missing: the old fixture had no Forge tick subscription.
No world-identity mismatch or static-state leak is evidenced. Old before/after
batch hooks and site cleanup were present. The correction nevertheless makes the
server identity and clean lifecycle explicit instead of silently accepting stale
state across server instances.

## Fixture-only correction

`FamilyTeleportRuntimeTests` now drives preparation from each active helper's
`onEachTick` callback. In Minecraft 1.20.1, `GameTestInfo.tickInternal` runs these
scheduled callbacks before sequence polling; `GameTestTicker` is driven by the
shared `MinecraftServer.tickChildren` path in both GameTestServer and the dedicated
console GameTests. Readiness assertions cannot disable this driver. No new Forge
event subscription is needed.

Scheduling is round-robin by site, with **two pending request slots per site** and
at most **four new tickets per server tick**. One site's unready chunks cannot
consume another site's capacity. The original two-platform-builds-per-tick limit
remains. Queue registration is bounded to 13 sites and 75 requests per site
(including the bounded spawn search), so the maximum registered backlog is 975;
actual default fixtures have 54 requests each. Pending capacity is at most 26.

For **GameTestServer preparation only**, a single bounded
`LockSupport.parkNanos` yield gives the accelerated loop the ordinary 50ms/tick
cadence while requests are queued or pending. It never waits on a chunk/future,
has no retry/sleep loop, and parks for at most 50ms per server tick. Generation
and entity loading remain asynchronous and normal server ticks continue. The
native dedicated-server pacing is unchanged; pacing stops when no preparation
requests remain. No world clock or test clock is rewritten, and neither the
400-tick preparation deadline nor any original 500-tick test deadline is extended.
This addresses the demonstrated accelerated-clock mismatch explicitly, rather
than concealing it with a larger timeout or an unbounded generation wait.

FULL tickets remain level 33 (`forceTicks=false`), source simulation is never
held by the fixture, and source-demotion assertions remain. Platform writes and
restoration still require already present FULL chunks. Neither `getChunk`,
`getChunkFuture`, future `join` nor a managed generation wait is called by the
fixture. The old watchdog's synchronous generation path remains removed.

Before/after hooks reset round-robin order, stage observations, counters, pacing
state and server identity. Site cleanup removes its queue entries, pending slots,
stage observations and tickets. Another server instance cannot inherit requests;
an unclean preceding batch fails explicitly. Fresh-world wrapper behavior is
unchanged.

## Diagnostics and regression coverage

Bounded records distinguish:

- `QUEUED`: registered requests and source/target coordinates;
- `DISPATCHER_ACTIVE`: actual helper driver cycle and server type;
- `ISSUANCE_STARTED` / DEBUG `REQUEST`: first site service and actual ticket issuance;
- `GENERATING` / DEBUG `CHUNK_STAGE`: submitted chunk status, with separate FULL
  and entity-data predicates rather than a combined ambiguous readiness flag;
- `FULL_READY` and `ENTITY_DATA_READY`: first observed milestones for each site;
- `PLATFORM_READY` and `READY_FOR_COMMANDS`: terrain edits and completed fixture;
- `DISPATCH_FAILED`: callback absent versus active dispatcher starving a site;
- `TIMEOUT`: bounded pending-coordinate samples, ticket/FULL/entity-data state,
  driver/cycle counters, first-issuance tick, and source/arrival state.

Every site now has a scheduled **tick-six assertion** that its helper driver ran
and issued its first ticket, plus shared work/capacity bounds. This assertion is
outside `thenWaitUntil`, so a dispatch failure fails immediately instead of being
retried as ordinary loading.

The original **12 command/transfer tests are unchanged**, including their
500-tick annotations, UUID/persistence/ownership, exclusion, safe-landing,
no-duplication, insertion restoration, supersession and source-demotion checks.
Existing timeout and journey rollback tests are untouched.

One additional 500-tick regression,
`freshServerDispatchesAndCompletesFixtureQueue`, starts with a cold FULL source
chunk and zero issued requests, requires ticket issuance by tick six, then
verifies every queued request reaches FULL plus entity-data readiness and both
platforms finish. It uses the actual shared helper driver and tickets, without
service helper calls or a substitute dispatcher. The runtime batch now has
**13 tests**: 12 original teleport cases plus the queue probe. The existing
dedicated runner discovers the expected count/sites from source and requires all
13 completion markers, suite completion and normal shutdown; it needed no change.
The probe remains **NOT RUN** until external validation executes it.

## Other reported failures

`forgevetoandfailednativeinsertionretainexactsources` (site 7) failed at fixture
preparation: issued **0/54**, platforms **0**. It shares this queue failure;
its command, Forge-veto and native-insertion assertions were never reached.

The later `FamilyJourney` rollback error was emitted by
`isolated_journey_rollback_veto`. Its source deliberately vetoes rollback and
asserts `ROLLBACK_FAILED`, correct indexing and album recovery. That regression
passed. The final summary lists only the 12 fixture failures; no unrelated failed
GameTest is established by this run. Startup Netty reflective-access diagnostics
were DEBUG capability probes, not additional test failures. No production fix is
justified by this evidence.

## External validation

From the repository root with JDK 17:

```bash
./tools/test.sh build
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
# Set this only if you accept the Minecraft EULA linked by the runner.
DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py
```

Expect a 13-test `isolated_tp_runtime` batch, issuance for every site by tick six,
`FAMILY_TP_FIXTURE_DISPATCH_PROBE_VERIFIED` (with the actual server type), all 13
completion markers and the completed required-test summary. The focused namespace
should contain 44 tests if no other tests have been added. Inspect any failure's
phase diagnostics; actual cold loading can still fail the unchanged bounded
deadlines and must not be called a pass. Keep new reports and the original failed
run; neither existing world is reused or removed by this change.

The harness still uses in-process players/packet handlers, not network login,
Forge handshake or signed client chat. Minecraft/BMC4 manual acceptance and real
process-restart identity verification remain separate and unverified.
