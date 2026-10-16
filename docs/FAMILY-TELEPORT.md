# Vanilla teleport accompaniment

Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, Better MC BMC4 v55.5.
Current release evidence: [0.28.0-alpha validation](VALIDATION-0.28.0.md).
Ordinary journey tests and opt-in dedicated runtime tests are separate scopes;
manual Minecraft/BMC4 acceptance remains outstanding.

## Original Stage 4.4 handoff (historical)

Stage 4.4 runtime fix and 12 additional end-to-end tests are implemented locally.
The developer reports the previous GameTests passed, but real `/tp` acceptance
FAILED. Current changes: compilation/GameTests **NOT RUN**; runtime acceptance
remains unresolved. See [Stage 4.4 investigation and validation](VALIDATION-FAMILY-TELEPORT-STAGE-4.4.md).
No version change, commit, push or publication.

Runtime fixture update (2026-10-16): the 13 full command/lifecycle cases now run
sequentially on an opt-in disposable dedicated server, after bounded asynchronous
warmup. The ordinary Journey GameTestServer scope retains its 31 focused tests;
that scope alone does not validate these runtime cases. See
[fixture redesign and smoke/full validation commands](VALIDATION-TELEPORT-FIXTURE-REDESIGN.md).
The former per-site cold queues and GameTestServer pacing workaround are removed.

## Investigation of the current local implementation

Inspected local HEAD `eff277b` and the dirty working tree, including the existing
Mushroom Vale work. Git history contains `1bd0c8e` (0.15.0 loaded-area catch-up),
`e21c07a` (family gateways), and `eff277b` (family journey stabilization).
`FamilyTravel`/`FamilyFollowGoal` already implemented ordinary catch-up: same
dimension, 12–64 blocks apart, six stalled seconds, grounded eligible owner,
loaded chunks, `familyCatchUp` enabled. Beyond 64 blocks the follow goal stops;
after a dimension change `ownerPlayer()` cannot find the owner in that world.
These mechanisms cannot accompany a distant vanilla command teleport.

`FamilyJourney` already implemented explicit gateway entry, physical return and
`/dudunka return_family`, with adult three-member eligibility, temporary arrival
preparation and rollback. Its historical cold-arrival/uneven-terrain issues and
fixes are documented in `VALIDATION-FAMILY-JOURNEY-STAGE-4.0.md`. That work did
**not** hook vanilla commands. Before this change there was no teleport-command
event listener or command completion hook in local source. This paragraph describes the original pre-feature investigation, not the later
runtime failure after interception was implemented. The Stage 4.4 investigation
above supersedes any explanation of that later acceptance failure.

## Interception and safety

Inspected the cached Forge **47.4.13** mapped sources, rather than assuming another
Minecraft version's event behavior:

- `TeleportCommand.performTeleport` posts a cancelable
  `EntityTeleportEvent.TeleportCommand` **before** bounds checking and movement.
  The event contains mutable coordinates, but neither destination level nor a
  success callback. An event alone or a dimension-change listener is insufficient.
- `ServerPlayer.teleportTo(ServerLevel, ..., Set, ...)` can return `true` even
  when the delegated cross-dimension method's Forge travel hook vetoes movement.
  A positive command result also does not establish successful arrival.
- Brigadier 1.1.8 `CommandNode.addChild` publicly merges a new executable into an
  existing node without replacing that node. `FamilyTeleport` uses this at each
  executable beneath the registered `teleport` root, at `RegisterCommandsEvent`.
  Existing arguments, permissions, `/tp` redirects, `/execute` forks and source
  contexts remain vanilla. No selector is reevaluated to guess a destination.
  Installation is idempotent and reapplied when commands rebuild on reload.
- The wrapper calls the original executable and retains pre-departure command
  and player-travel event objects. After native execution returns, it checks
  their **final** cancellation/coordinate values, actual player dimension and
  exact target position. A thrown command, veto, canceled event, unexpected
  redirection or no-op cannot trigger accompaniment.

Capture uses the owner's source position and only nearby live canonical entities
within **8 blocks**, with already loaded source entity data. Source simulation
may stop after the owner leaves; loaded canonical companions remain eligible. UUID, owner and
registry Kind must agree and no other loaded level may contain that UUID.
STAY, HOME, carried, leashed, mounted/passenger and active gateway-pose companions
are excluded. The owner must be live, present and nonspectating. Age and Marusya's
temporary independent pause do not change permanent FOLLOW mode. Pending album
recovery and active/pending/releasing FamilyJourney operations exclude accompaniment.
`familyCatchUp` controls ordinary stalled-walking catch-up only; explicit vanilla
command accompaniment is independent of that setting.

After confirmed player arrival, wait at most **40 server ticks** for its arrival
chunk to become entity ticking through vanilla player loading. No accompaniment
chunk tickets, source loads or terrain edits are added. A later vanilla command,
logout, death, spectator mode, dimension change, moving more than four blocks from
the captured arrival, or a conflicting journey cancels this pending request. The
four-block check bounds an already confirmed command request; it never detects a
teleport by sampling movement. Source unloading makes a captured member ineligible;
it is never fetched from its registry position.

Each member gets one bounded placement/transfer pass: horizontal rings 1–4,
Y offsets 0, +1, -1, +2, -2 (**400 maximum candidates per member**). Each target
chunk must already be entity ticking. Shared MushroomVale checks validate loaded
safety halos, entity dimensions, border/build height, floor, fluids, hazards,
collision and occupancy. Successful earlier arrivals occupy their slots for later
members. A safe source position is also required for possible native insertion
failure restoration. No broad terrain/heightmap search or repeated transfer retry.

Transfers reuse the existing `FamilyJourney.transfer` and canonical identity
checks; only those helpers' package visibility changed. This explicitly posts
Forge's companion travel veto (native Entity teleport itself does not), then
revalidates source, owner, carrier/mode and destination after listeners return.
Native cross-dimension transfer copies the **live entity**, retaining UUID, care,
growth and bindings. A rejected destination insertion attempts to revive/reinsert
the same source object through Forge. No entity is created from registry or backup
NBT. Registry observations follow surviving actual instances immediately.
Memories/friendships, home/furniture dimensions, save formats, registry IDs and
album protocol are unchanged. Transient goals/navigation/falling are cleared.

An owner request is guarded during callbacks and consumed once; subsequent ticks
cannot repeat it. Canceled/failed/unsafe members remain behind where possible;
already successful members and the player are not rolled back by this service.
Third-party refusal of both destination insertion and source restoration, or
third-party destruction, cannot be overridden safely: report the missing UUID and
existing recovery feedback, never manufacture a replacement. Gateway transaction
planning, all-member rollback, bookmarks and its ticket lifecycle are unchanged.

## Supported paths and limits

These are implemented paths, **not yet runtime-validated claims**.

| Path | Accompaniment |
| --- | --- |
| `/tp X Y Z`, `/teleport X Y Z` | Yes, same dimension, implicit player source |
| `/tp @s X Y Z`, `/teleport Player X Y Z`, player selectors | Yes, each targeted owner's captured family |
| Relative `~`, local `^`, rotation, `facing`, `facing entity` | Yes; original vanilla parser/executable retained |
| `/tp Player`, `/teleport PlayerA PlayerB` | Yes, same or different dimension of destination entity |
| `/execute in <dimension> run tp @s X Y Z` | Yes, same or cross dimension |
| `/execute as ... at ... run teleport ...` and multi-player forks | Yes, scoped independently for each actual execution |
| Console/command blocks/datapack functions invoking these nodes | Yes, for explicit player targets; normal permissions/selectors apply |
| `/reload` then the same commands | Yes, registration installs on the rebuilt dispatcher |
| Non-player entities explicitly selected by vanilla tp | Vanilla behavior; no automatic collection of their owner's family |
| No-op/rotation-only, canceled/failed/vetoed player command | No companion transfer |
| Unsafe/no-floor destination, not entity ticking within 40 ticks, unloaded/changed source | No unsafe transfer; diagnostics, no force-load |
| Vanilla Nether/End portals, pearls, chorus fruit, `/spreadplayers`, death/respawn, third-party teleport APIs | No new accompaniment hook |
| Developer Vale entry/return, `/dudunka return`, Shift solo gates | Existing solo behavior |
| Awakened family gates, `/dudunka return_family` | Existing FamilyJourney transaction/rollback |
| Mods replacing the vanilla executables/commands or bypassing the wrapped nodes/events | Not guaranteed; verify in BMC4 |

Because an explicit vanilla pre-event plus a synchronous original-command wrapper
exists in this pinned version, a separate family teleport fallback command is not
needed. No mixin, access transformer, dependency or movement-distance inference
was introduced. Arbitrary mod teleports are deliberately outside this hook.

## External automated validation

Run from the developer terminal, Java 17; none of these were run by Codex:

```bash
./tools/test.sh build
DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest
```

The focused namespace includes the existing gateway journey regressions,
14 `FamilyTeleportTests`, and 12 `FamilyTeleportRuntimeTests` added in Stage 4.4. These cover
far same-world tp, aliases/relative/local/facing parsing and idempotent install,
cross-dimensional entry/return and player-first ordering, console/entity target,
STAY/HOME/carrier/foreign/distant/stale exclusion, late cancellation/invalid bounds,
player travel veto at identical coordinates, companion veto/rejected native join,
unsafe destination/no chunk growth, late target/mode mutations, juvenile growth,
two owners in one selector, cold vanilla-loaded arrival, timeout and pending
request supersession. The test fixtures
alone create platforms and force visibility; production never does.

## runClient acceptance

Launch IntelliJ Gradle task **`runClient -PmushroomValeDev=true`**, Gradle JVM
Java 17, ordinary client assets. Use a **new disposable Creative world with
cheats**. Repeat gameplay checks later in full BMC4 v55.5 and real multiplayer.

1. Create two known safe platforms and travel **before** preparing companions:

   ```text
   /fill 0 159 0 15 159 15 minecraft:stone
   /fill 0 160 0 15 165 15 minecraft:air
   /fill 96 159 0 111 159 15 minecraft:stone
   /fill 96 160 0 111 165 15 minecraft:air
   /tp @s 8.5 160 8.5
   /dudunka_dev mushroom_vale prepare_family
   ```

   Record UUIDs with `/data get entity @e[type=dudunka:dudunka,limit=1,sort=nearest] UUID`
   and the corresponding `marusya`/`syusya` types. Record home/care/album state.
   Keep companions within eight blocks; observe mode in the album.
2. **Same dimension:** `/tp @s 104.5 160 8.5`, then
   `/teleport @s 8.5 160 8.5`. All three should arrive once, near the player,
   with the recorded UUIDs. Repeat using `/teleport 104.5 160 8.5`,
   `/tp @s ~-96 ~ ~`, `/tp @s ^ ^ ^2`,
   `/tp @s 104.5 160 8.5 90 0`, and
   `/tp @s 8.5 160 8.5 facing entity @s eyes`.
   Regather within eight blocks between commands. `/reload`, then repeat `/tp`.
3. **Cross dimension:** prepare a disposable Vale platform (no family at source
   is moved by these fill commands):

   ```text
   /execute in dudunka:mushroom_vale run fill 0 299 0 15 299 15 minecraft:stone
   /execute in dudunka:mushroom_vale run fill 0 300 0 15 305 15 minecraft:air
   /execute in dudunka:mushroom_vale run tp @s 8.5 300 8.5
   /execute in minecraft:overworld run teleport @s 8.5 160 8.5
   ```

   Allow up to two seconds of server ticks for a cold arrival. The player must
   arrive first, then the same companions. Check UUIDs and unchanged album
   memories/friendship, growth and original home bindings. Verify no Vale return
   bookmark/gateway is created by vanilla commands. Save/restart and recheck.
4. **Entity target / another source:** with a second client `OtherPlayer` on the
   second platform, `/teleport @s OtherPlayer`; repeat with that player in Vale.
   From server console or an operator command block use
   `teleport OwnerName OtherPlayer`. Only OwnerName's nearby FOLLOW family should
   accompany. Test `/execute as OwnerName at @s run tp @s 104.5 160 8.5`.
   Gather two owners with separate families, then `/teleport @a 8.5 160 8.5`;
   both families retain their owners and distinct companion landing positions.
5. **Exclusions:** gather on the first platform, put Dudunka in STAY, Marusya in
   HOME and Syusya in the carrier. `/tp @s 104.5 160 8.5`: none is transferred
   as a live follower; the carrier remains in inventory with its original UUID.
   Return, restore FOLLOW/unpack, test one member beyond eight blocks and another
   player's member nearby. Neither should be selected. Test a juvenile FOLLOW
   companion without forcing adulthood; growth must remain unchanged by transfer.
6. **Safe failure:** replace the destination floor with magma in this disposable
   world (`/fill 96 159 0 111 159 15 minecraft:magma_block`), then teleport there
   in Creative. The player command succeeds; companions stay at their source,
   one bounded diagnostic per rejected member. Restore stone afterward.
   Also test `/tp @s 104.5 250 8.5` (no nearby floor), an obstructed arrival,
   and `/tp @s 30000001 160 0` (vanilla failure). No duplicate/disappearing UUIDs.
   `/tp @s ~ ~ ~` should not move companions at all.
7. **Veto/cancellation and deferred edge cases:** run the focused GameTests above;
   ordinary runClient has no command to inject Forge cancellation/join rejection.
   In BMC4, repeat with any installed protection that actually vetoes travel;
   a vetoed player stays with all companions, a vetoed companion stays while
   permitted members accompany. Cold arrival, immediate second `/tp`, walking
   away, disconnect and source unload must never repeat or recreate a transfer.
   Watch logs if a source becomes unavailable during the bounded loading wait.
8. **Other travel / gateway regression:** pearls, chorus fruit, ordinary portals
   and `/spreadplayers` must not invoke this service. Test awakened normal family
   entry and `/dudunka return_family` (all-three transaction), Shift gate solo,
   `/dudunka return` and developer entry/return (solo). Use existing gateway
   acceptance instructions for rollback/bookmark checks. Existing ordinary
   walking catch-up can still occur under its own rules.

Inspect `run/logs/latest.log`: DEBUG `Family teleport capture` gives eligible
count, DEBUG `waiting for native arrival loading` explains delayed movement, DEBUG
`accompanied` identifies successful UUIDs; WARN `skipped` includes source/actual
arrival and cancellation/timeout reasons. Per-member WARN contains UUID, Kind,
source/destination and aggregated landing rejection counts (maximum 400 probes).
An unavailable native source restoration logs recovery guidance; it never creates
a replacement. Record exact commands, coordinates, dimension, UUIDs and relevant
log lines when reporting an acceptance failure.
