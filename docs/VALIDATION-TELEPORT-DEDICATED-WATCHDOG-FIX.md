# Dedicated teleport fixture watchdog correction — 2026-10-16

Latest follow-up: [fixture queue starvation correction](VALIDATION-TELEPORT-FIXTURE-QUEUE-FIX.md).
The failed GameTestServer run dispatched 45 tickets, then starved later sites;
that report supersedes the earlier FIFO scheduling details. Its correction and
new queue probe are **NOT RUN** in Minecraft.

Implementation: **changed locally**. Cheap static checks and six pure Python
report-parser regressions: **PASS**. Compilation, GameTests and dedicated server
validation of this correction: **NOT RUN**. Minecraft/BMC4 acceptance remains
open. No production gameplay file, watchdog setting, mod version or publication
was changed.

## Confirmed evidence and coordinates

Inspected the exact failed run:

- `build/reports/dudunka-tests/teleport-dedicated-20261015T194709Z-1081780/dedicated.log`
- `run/dudunka-teleport-server-20261015T194709Z-1081780/crash-reports/crash-2026-10-15_22.48.51-server.txt`

At 22:47:51 the log confirms command interception, three eligible companions,
actual native player arrival, and accompaniment of all three UUIDs in an existing
teleport regression. Immediately afterward the 12-test runtime batch starts.
At 22:48:51 the watchdog reports one tick exceeding 60 seconds. This is a failed
suite, not runtime-batch acceptance.

The server-thread stack is:

```text
unsafeArrivalLeavesLoadedOriginalsAndConsumesRequest
Trip.<init> (line 71)
Trip.platform (line 89): level.getChunk(x,z)
Level.getChunk / ServerChunkCache.getChunk
BlockableEventLoop.managedBlock / parkNanos
```

The blocked overload requests `ChunkStatus.FULL` with creation enabled. It adds
an UNKNOWN ticket as necessary and waits for the chunk future on the **server
thread**, before any fixture readiness sequence can execute. A generation worker
is concurrently in `NoiseBasedChunkGenerator.doFill` / aquifer noise calculation.
Another log entry reports ongoing Vale generation at `[2413, -2050]` from another
fixture region. The evidence establishes synchronous fixture generation/waiting;
it does not establish a deadlock or an independent production teleport defect.

The method identifies **site 6**, using the previous coordinate formula:

| Platform | Block centre | Chunk centre | Requested 5×5 FULL area |
| --- | --- | --- | --- |
| Source | `(35848,160,-32760)` | `(2240,-2048)` | X `2238..2242`, Z `-2050..-2046` |
| Arrival | `(36104,160,-32760)` | `(2256,-2048)` | X `2254..2258`, Z `-2050..-2046` |

Both platforms use `minecraft:overworld` in this test. **The exact loop iteration
cannot be recovered from this report:** stack traces do not retain `x/z` locals,
both calls occur on constructor line 71, and the original fixture did not log
individual chunk requests. Naming the first chunk or the worker's Vale chunk as
the exact blocked coordinate would be unsupported. New request diagnostics and
readiness-timeout samples provide exact dimension/chunk coordinates going forward.

## Pinned asynchronous and ticket behavior

Reviewed the cached Minecraft 1.20.1 / Forge 47.4.13 mapped sources before editing:

- `ServerChunkCache.getChunk` calls `managedBlock(future::isDone)` and then joins.
  Moving that call to a GameTest callback does not make it asynchronous.
- `ServerChunkCache.getChunkFuture` **also** calls `managedBlock` when invoked
  from the main server thread. It is not a safe replacement here.
- `addRegionTicket(FULL, chunk, 0, fixtureId)` registers a level-33 FULL ticket
  without waiting. Normal distance-manager updates and `ChunkHolder.updateFutures`
  schedule accessible FULL chunks and their generation dependencies. The default
  `forceTicks` argument is false. No source entity-ticking ticket is added.
- `getChunkNow` reads an existing FULL result without scheduling or waiting;
  `areEntitiesLoaded` independently verifies persistent entity-data readiness.
  Platform edits start only after **both** predicates hold for the whole halo.

The old constructors synchronously prepared 50 FULL chunks per fixture, up to
600 for this batch, plus their transitive generation dependencies, at widely
separated 32,000-block sites. Distance itself is not intrinsically expensive;
independent cold generation regions and serial server-thread waits are the
problem. The dump supports an active generation backlog, not a measured claim
about CPU saturation or lock contention.

## Fixture correction

`FamilyTeleportRuntimeTests` now constructs a queue without touching terrain.
A shared preparation pump advances once per real server tick and limits:

- new FULL-ticket requests to **4 per tick**;
- submitted requests awaiting FULL/entity data to **16**;
- platform construction to **2 per tick** across the batch.

The same queue also prepares the bounded source-level spawn search used by
`ServerPlayer` / `PlayerRespawnLogic` constructors (stock spawn radius; fixture
supports at most 31). Players are constructed in that prepared source level;
secondary-dimension target players move natively to their already prepared
platform. Thus a later entity-target test cannot silently trigger generation at
an unprepared Vale spawn. These extra common spawn requests are normally already
loaded; they use the same budget and never simulate the remote departure chunks.

Normal server ticks continue servicing generation, lighting and entity loading.
No `getChunk`, `getChunkFuture`, `join`, managed wait, background-thread world
mutation or forced chunk was introduced. The 11×11 platform lies within its
centre chunk. Edits and restoration use already present chunks; cleanup checks
availability rather than requesting a missing chunk. Issued tickets are retained
through restoration and removed on success, failure or the preparation deadline.
Unsubmitted queue entries are also removed during cleanup.

Sites now form a compact 4×3 grid: source centres are
`(1544 + (site % 4)*256, 160, -1528 - (site / 4)*128)`, with arrival 128 blocks east.
Each platform centre remains eight chunks from its nearest neighbour, safely
beyond the configured two-chunk player simulation radius. This keeps spawn and
GameTest structure forcing separate while letting generation dependency halos
overlap and avoiding unnecessarily remote regions. Forced-chunk comparison now
covers these nearer fixture regions, rather than retaining the old 16,384-block
filter that would omit them.

All **12 tests**, their **500-tick timeouts**, source-demotion requirements and
UUID, ownership, NBT, growth, friendship, memory, registry, exclusion, insertion
restoration and no-duplication assertions remain. Real vanilla commands, registered
production listeners, packet acknowledgements/movement and native entity transfers
remain the operation under test. Existing `FamilyTeleportTests` and journey tests
are untouched, including timeout/rollback regressions.

Preparation has a separate **400-tick failure deadline within the original
500-tick test budget**. It emits one bounded diagnostic with queued/issued/in-flight
counts, built-platform count, pending dimension/chunk/holder status samples and
source/arrival loaded/ticking/player state, then releases resources and fails.
This uses a scheduled task: throwing from `thenWaitUntil` alone would retry until
the final test deadline and could spam timeout logs. Generation overload now
produces a bounded readiness failure rather than a watchdog wait. The new budget
has not yet been runtime-validated; an actual timeout must be investigated using
its diagnostics, not reported as a pass.

## Dedicated runner completion evidence

`tools/test-teleport-dedicated.py` still creates a fresh isolated world and invokes
the actual dedicated console `test runall` for `dudunka_journey`. It now requires:

1. An announced nonzero suite count matching the completed suite count.
2. The expected runtime batch count and a unique completion marker for **every**
   runtime site, emitted after its assertions and cleanup succeed.
3. The explicit all-required-tests-passed summary.
4. A sent `stop`, observed normal server stopping/save completion, and exit zero.
5. No watchdog/crash/required-test failure in **any** output, including the queue
   drained after the passing summary and during shutdown.

The summary retains completed/missing runtime site evidence, suite counts,
shutdown status and the existing report/world paths. Launcher exit zero or a
runtime batch-start line alone cannot establish success.

`tools/test_teleport_dedicated_report.py` tests the log evidence gate without
starting Minecraft: normal completion, missing runtime completions, a watchdog
after a passing summary, duplicate completions, mismatched totals, and missing
shutdown/nonzero exit/unsent stop. All six tests passed locally.

## External validation

From the repository root with JDK 17:

```bash
./tools/test.sh build
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
# Set this only if you accept the Minecraft EULA linked by the runner.
DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py
```

Optional cheap runner-only regressions (already run once for this correction):

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/test_teleport_dedicated_report.py
```

Check `FAMILY_TP_FIXTURE_QUEUED`, `_REQUEST` (DEBUG), `_PLATFORM_READY` and any
`_TIMEOUT` records, all 12 `FAMILY_TELEPORT_RUNTIME_COMPLETE` sites, the completed
suite and normal shutdown. Retain new logs/world artifacts; the failed run remains
untouched. Do not disable the watchdog or change its global deadline.

Coverage still uses an in-process ServerPlayer/packet adapter. Real socket login,
Forge handshake, signed client chat, graphical play, process-restart identity and
BMC4 compatibility are not validated by this harness. A passing headless run must
remain separate from real Minecraft/BMC4 acceptance.
