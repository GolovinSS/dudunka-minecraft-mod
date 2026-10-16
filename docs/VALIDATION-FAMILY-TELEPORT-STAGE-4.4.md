# Stage 4.4 — vanilla command accompaniment runtime failure

Latest follow-up: [fixture queue starvation correction](VALIDATION-TELEPORT-FIXTURE-QUEUE-FIX.md).
The failed GameTestServer run dispatched 45 tickets, then starved later sites;
that report supersedes the earlier FIFO scheduling details. Its correction and
new queue probe are **NOT RUN** in Minecraft.

Implementation: **implemented locally, not compiled**. New automated verification:
**NOT RUN**. Real Minecraft / BMC4 acceptance: **FAILED previously; unresolved
until an actual retest passes**. Publication: **NOT DONE**. No version change.
The developer's dedicated run `teleport-dedicated-20261015T194709Z-1081780`
confirmed interception and some real transfers, then crashed in synchronous
runtime-fixture chunk preparation. See [the watchdog correction and validation
handoff](VALIDATION-TELEPORT-DEDICATED-WATCHDOG-FIX.md); verification of that correction
is **NOT RUN**. The developer reports the previous automated suite passed. That evidence does
not establish runtime acceptance of either the previous or this implementation.

## Confirmed defect and scope of the evidence

`FamilyTeleport.eligible` previously required
`FamilyJourney.entityTickingWithData(source, mob.blockPosition())` both during
capture and again after the owner arrived. This conflated **loaded live source
entities** with **source simulation**. `ChunkMap.move` / `updatePlayerStatus`
remove the departing player's distance/simulation ticket. The source can become
FULL/TRACKED, with loaded entity data and the exact living companions still
available through `ServerLevel.getEntity(UUID)`, while
`ServerLevel.isPositionEntityTicking` becomes false. The pending request then
skipped them as `source unloaded/changed/ineligible`, consumed the request and
never accompanied them. This is a confirmed code-path defect, established from
the pinned source; the new tests have not been executed yet.

The minimal behavior fix is confined to `FamilyTeleport`: source eligibility
requires already loaded entity data and the existing canonical/live identity
check, without requiring source simulation. The destination still requires both
loaded entity data and active simulation. No ticket, loading request, registry
reconstruction, new transport mechanism, save format or protocol was added.
`FamilyJourney`, its gateway transactions/recovery/rollback, and `FamilyTravel`
were not changed in this stage.

**Incident correlation is not yet proven:** no log or exact command from the
failed manual session was provided. A missing/outdated deployed JAR, a mod
replacing vanilla executables, unsafe source/target positions, or another
eligibility failure cannot be ruled out for that specific session. The previous
all-green suite already demonstrated interception in its own environment; it
would be inaccurate to describe it as entirely fake or to assert that event
registration was absent. Bounded diagnostics now distinguish these cases.

## Actual 1.20.1 / Forge 47.4.13 execution trace

Reviewed the local cached
`forge-1.20.1-47.4.13_mapped_official_1.20.1-sources.jar` and Brigadier 1.1.8
sources. This investigation did not depend on a family test dispatcher helper.

1. `Commands` registers `TeleportCommand` before
   `ForgeEventFactory.onCommandRegister`. That posts `RegisterCommandsEvent` on
   the Forge bus. `FamilyTeleport` is a default FORGE-bus `@EventBusSubscriber`,
   with no client-only restriction; integrated `runClient` servers and dedicated
   servers construct the same server `Commands` and install the wrapper. Reloads
   construct a new dispatcher and install again.
2. `teleport location` and `teleport destination` implement implicit self;
   `teleport targets location/destination` implement explicit targets. `tp`
   redirects to the original `teleport` node. Brigadier `addChild` merges the
   replacement executable into that original node, preserving redirect identity.
   `execute in` transforms the source level and `run` redirects to the dispatcher
   root; the terminal vanilla executable remains wrapped. Vanilla's requirement,
   argument parsing, selectors, relative/local coordinates and command result
   remain in control.
3. Player `ServerGamePacketListenerImpl.performChatCommand` parses against the
   same server dispatcher and calls `Commands.performCommand`. Dedicated console
   and `BaseCommandBlock.performCommand` call `performPrefixedCommand`, which
   parses then calls `performCommand`. Forge's `CommandEvent` precedes actual
   Brigadier execution. A canceled command never enters the wrapper or captures
   companions. Explicit player targets work with a non-player command source.
4. The wrapper establishes `SCOPE`, then the original vanilla executable calls
   `TeleportCommand.performTeleport`. Forge's cancelable mutable
   `EntityTeleportEvent.TeleportCommand` fires **before movement**. Capture uses
   the targeted player's UUID, actual source level and position, not the command
   source's potentially rewritten level. It records the exact live companion
   objects. Native cross-dimension player travel also emits
   `EntityTravelToDimensionEvent`; that same ServerPlayer object/UUID survives
   `removePlayerImmediately`, `revive`, `setServerLevel` and destination insertion.
5. After the original executable returns, completion checks final event
   cancellation/coordinates, actual player level and position. Native player
   `teleportTo` can report true despite a Forge veto; that alone is not accepted
   as arrival. Same-world movement has no dimension event; completion still runs.
   Failure, no-op or unexpected destination consumes the request explicitly.
6. Player teleport adds vanilla's `POST_TELEPORT` ticket, and normal player
   tracking/movement supplies destination simulation. Production END server ticks
   retry readiness for at most 40 ticks. Source chunks are never requested.
   Superseding commands, owner movement/death/logout/busy state and timeouts end
   the request. A single placement/transfer pass respects Forge companion vetoes
   and the existing native insertion/source-object restoration logic.

## Why the prior tests could pass

All 14 existing tests and their safety assertions remain. They really execute
vanilla dispatcher nodes and native transfer code, so they cover much of the
production mechanism. Their limits were:

- `Trip.keep(source, root)` forces a source 3×3 region. GameTest's own
  `StructureUtils.forceLoadChunks` also forces the test structure area. Even the
  cold-target tests retained a forced source. Source simulation loss was masked.
- Most checks happened synchronously with both ends already entity-ticking.
  The existing cold fixture prepared FULL target chunks but kept the source
  ticking, so it tested destination readiness without source demotion.
- One test redundantly invoked `FamilyTeleport.capture` from its own Forge
  listener. That extra invocation was removed; it now relies exclusively on
  production subscription. No assertion was removed.
- Explicit service tick calls in the old duplicate/timeout tests are clock and
  idempotence regressions, not evidence of an actual server-tick lifecycle.
  They remain as safety tests. The new tests never call service helpers.
- Synthetic player connections skipped teleport acknowledgement/movement. The
  new fixture sends acceptance and movement through vanilla's packet handlers,
  allowing real player distance-ticket updates on same-dimension departures.

## New headless end-to-end tests

`FamilyTeleportRuntimeTests` adds **12** tests in `dudunka_journey`:

- implicit `tp` and implicit `teleport` from loaded, non-ticking source chunks;
- cross-dimension `execute in … run tp @s …`;
- same- and cross-dimension departures from initially ticking sources, followed
  by loss of source simulation while the original entity data remains loaded;
- implicit player entity-destination teleport, console entity-destination
  teleport and actual `BaseCommandBlock` execution;
- STAY/HOME exclusions, unsafe destination, companion travel veto and rejected
  destination insertion, and a superseding command before cold arrival.

The remote platforms are outside GameTest's forced structure area. Fixture-only
FULL tickets retain entity data and prepare a small target halo; they do not
force or simulate source chunks. The pre-dispatch non-ticking-source assertions
make the former production predicate reject all three followers deterministically
in the first scenarios. The initially ticking-source scenarios additionally
exercise native departure demotion, rather than constructing only a cold-source
boundary case. Neither scenario's result has been run/observed yet.

Each test creates real owned live adult companions and a ServerPlayer, verifies
FOLLOW prerequisites, dispatches vanilla commands through the server's actual
`Commands`, and lets registered Forge listeners and natural ticks finish.
Successful tests assert the owner's exact arrival first, then safe nearby
companions with original UUIDs, ownership, growth, trust, home bindings, custom
names and persistence flags, saved NBT round trips, existing memories/friendships
and registry position. Iterating every level's loaded entity collection checks
both duplicate UUIDs and unexpected new UUIDs owned by that player. Verification
repeats after five more natural ticks. Negative cases wait for actual native
arrival readiness before checking that originals stayed behind. The original
suite retains carried/foreign/far/stale exclusions, player veto, target rewriting,
juvenile growth, timeout, idempotence and other regressions.

The new HOME fixture loads persisted HOME mode (including an unavailable home);
the old test retains its stronger valid-room / real `commandHome` preconditions.
NBT round trips are save-boundary checks, **not a process-restart test**.

## External commands (Java 17)

Run only from the developer terminal. Codex ran neither command:

```bash
./tools/test.sh build
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
```

The second includes the existing journey/teleport regressions and the new 12-test
`isolated_tp_runtime` batch. Check both the batch and a completed nonzero passing
suite in the retained logs; Gradle exit zero alone is insufficient.

Additional **actual DedicatedServer**, fresh-world, console-driven validation:

```bash
# Set the environment variable only if you accept the Minecraft EULA linked by the runner.
DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py
```

The runner starts `runServer` with `--nogui` in a new isolated directory, enables
only `dudunka_journey`, sends `test runall` through console stdin, requires the
12-test runtime batch, every individual runtime completion marker, and an explicit
completed passing suite, then sends `stop` and requires normal save/shutdown
completion with exit zero. Shutdown-tail watchdog/crash reports invalidate PASS.
It shares the normal validation lock, binds loopback to an ephemeral port, keeps
world/log/init-script/summary artifacts, and never reuses a player's world.
Gradle dependencies default to offline; use `DUDUNKA_TEST_ONLINE=1` deliberately
if needed. A 20-minute runner deadline is a harness failure, not a test PASS.

This dedicated run checks startup registration and real console processing in
addition to the normal GameTestServer environment. Both still use an in-process
player/packet adapter: **no client login, real socket, Forge handshake or signed
chat packet was tested**. A real Forge-compatible headless client is not present
in this repository. Adding one would require a separate supported client harness;
a generic vanilla protocol bot is not equivalent to a Forge/BMC4 client. The
server-side shared dispatcher path is exercised here; networked client acceptance
remains open.

## Diagnostics and later minimal manual acceptance

Launch the built server/client JVM with `-Ddudunka.debugFamilyTeleport=true` to
promote bounded traces to INFO. The dedicated runner already sets it. Otherwise
traces are DEBUG and failures WARN/ERROR. There is no per-tick polling log and no
new mutating debug command. Candidate rejection samples are capped at 16 per
player command; aggregate nearby/eligible counts are always traced. Loaded-area
enumeration deliberately does not fetch unloaded registry entries.

Expect hook installation → executable interception → candidate counts/reasons →
pending creation → native player arrival → waiting/readiness → accompanied UUIDs
or explicit failure categories. Missing hook/interception lines in an opted-in
session distinguish deployment/command replacement issues from eligibility.
Timeouts include actual loaded/ticking state. Safe-source refusals now include the
specific landing rejection category.

Later, in a disposable world with the newly built JAR (then BMC4 v55.5):

1. Prepare two safe platforms, gather all three adult owned FOLLOW companions
   within eight blocks, and record their UUIDs/state. Use source/target outside
   spawn simulation and far enough apart to lose source simulation.
2. Run implicit `/tp X Y Z`, gather, then `/teleport X Y Z`. Check arrival within
   40 server ticks, exactly one of each UUID, unchanged owner/growth/home/album
   state, and no errors in the scoped diagnostics.
3. Repeat `/execute in dudunka:mushroom_vale run tp @s X Y Z` to a known safe
   platform, then an entity target and console/command block explicit player
   target. Check STAY/HOME remain behind and unsafe targets do not move family.
4. Save, restart and recheck identity/state. Record actual Minecraft/BMC4
   acceptance separately; a green headless suite alone does not close this stage.
