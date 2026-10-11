# Stage 3.4a — Wise Fly Agaric cottage residency

Continued from the existing uncommitted Stage 3.4 tree at `ae81ad4`, version
`0.27.0-alpha`. Minecraft 1.20.1, Forge 47.4.13, OpenJDK 17.0.20.1; target
modpack Better MC BMC4 v55.5. No commit, push, dependency, mixin, custom packet
or protocol change. The user's approved model/texture/animation/renderer are
preserved byte for byte. Six bilingual dialogue lines and all natural spawn
entries remain unchanged from the task's local baseline.

## Architecture and lifecycle

The inspected cottages are `MushroomValeDecorFeature` placed-feature blueprints,
not registered structures. Their JSON, placement logic and rates remain intact.
Server ticks recognize loaded, furnished Wise cottages near non-spectator
visitors using existing lectern block entities and a rotated structural
fingerprint. Discovery processes one nearby chunk each 20 ticks, at most 64
block entities and four rotations per lectern, and at most one issuance per
pass. Visitors share a rotating nine-chunk scan. Memory Forest and the Vale
are required. Production code requests no chunks or tickets and performs no
world scan. Feature placement creates no entities or shared saved-data writes.

The Overworld saved-data ledger `data/dudunka_wise_cottages.dat` reserves the
cottage floor origin, rotation and UUID. A compressed NBT temporary file is
flushed and atomically renamed before entity addition. Write failure prevents
entity creation; the session keeps its reservation. Vanilla entity NBT retains
UUID and persistence plus `WiseCottageOrigin` and `WiseCottageRotation`.
Server-thread activation is idempotent. The record is a permanent issuance,
including after death, `/kill`, cancelled joins or deliberate removal. Missing
loaded UUIDs never authorize a replacement. An interruption after durable
issuance but before entity saving can leave an empty cottage; this is the
explicit fail-closed policy. No automatic recovery or reset command exists.
Backups must retain ledger and ordinary world entity/chunk data together.

Old furnished cottages matching the current structural fingerprint enroll on
nearby visits without block edits. Door/book/redstone state and movable props
are ignored for recognition. Older unfurnished shells and structurally damaged
cottages remain unchanged and do not enroll. Natural elders are not adopted,
removed or changed; only the recorded UUID is the designated resident.

Residents use dry, supported and collision-checked paths, including bottom slabs,
and recheck the next node. Path creation/recomputation refuses missing regions.
They make local trips, open the existing wooden entrance before contact, pass
through its free aperture, return home and pause with an existing think/read
emote. There is no teleport fallback. Unsafe or blocked routes wait and retry.

Focused development exposed two real navigation constraints: the .72-block body
clips the open door's .1875-block hinged panel at a block-centered waypoint,
and Forge's width-based waypoint completion threshold is asymmetric under
rotation. Steering centers the body in the .8125-block opening; residency paths
follow each checked node without corner cutting. Collision size, speed, models,
textures, animation code and cottage geometry remain unchanged. Tests retained
the actual exit/return/hazard assertions throughout. A fixture readiness issue
was corrected by waiting for real entity-section ticking before activation.
Development failures remain in `build/reports/mushroom-vale-stage34a/`; only
focused checks were repeated while diagnosing changed code.

## Automated validation

Evidence paths below are relative to `build/reports/mushroom-vale-stage34a/`.
The runner requires actual passing GameTest completion summaries, not merely
Gradle exit 0. Test-only forced chunks prepare fixtures; production has none.

| Check | Result / evidence |
| --- | --- |
| Residency tests | **PASS 8/8**, `residency-0-20261011-140126/gametest.log` |
| Existing Wise Fly Agaric tests | **PASS 6/6**, `focused-0-20261011-140023/gametest.log`; final full suite also checks the final implementation |
| Firefly failing-seed replay | **PASS 1/1**, original 600-tick assertions, `firefly--4853257702107486027-20261011-140238/gametest.log` |
| Build and model verifiers | **PASS**, `build-default-20261011-140334/gametest.log`; build/check/reobfuscation, `verifyModels`, `verifyPufik`, `verifyValeSpider`, `verifyWiseFlyAgaric`, `verifyValeFirefly`, `verifyValeMusic` |
| Resource/JAR and preservation audit | **PASS**, `resources.log`, `preservation.log`; 304 JSON resources, 115 worldgen references, 8 blueprints, 81 model references, 372 JSON/PNG/OGG packaged resources; all 478 initial production paths audited |
| One final full GameTest suite | **PASS 201/201**, exit 0, `full-default-20261011-140503/gametest.log`; run once after focused checks/build |

Residency tests cover placement with no worldgen entities; recognition and
rotations; exactly one issuance across repeated visits; a flushed on-disk ledger
loaded into a fresh instance; entity UUID/home/rotation/persistence NBT reload;
actual vanilla chunk/entity unload and disk reload; simultaneous visitors and
queued server requests; automatic nearby-player discovery; deliberate removal;
interrupted issuance; absent old-save data; existing cottage block preservation;
wrong dimension and unloaded-candidate rejection; and actual live navigation in
all four rotations beside pond, magma and cliff fixtures. Live checks require
outside travel, return, home emotes, unchanged health, local bounds and no
teleport jumps. The chunk test explicitly observes both the chunk and UUID
unload, then waits for vanilla entity storage to reload the same UUID.

## Intermittent firefly investigation

The historical full-suite failure occurred at tick 20, position
`(602.9926,-41.4008,3.6549)`, world seed `-4853257702107486027`.
The Stage 3.4 isolated seed replay previously passed. This stage retains the
600-tick takeoff/hover assertions and original 640-tick timeout. Failure-only
GameTest diagnostics now report UUID, chunk availability, border, collision
and exact intersected block states; diagnostics guard collision access when
chunks are unavailable. No firefly production behavior has been changed.

The focused replay at the historical failing seed passed **1/1** (600 ticks).
The single final full suite also passed the unchanged firefly test as part of
**201/201**. No collision diagnostic was triggered in either run. The intermittent
cause remains **unconfirmed**: these passes do not establish that the historical
failure was fixed. Production `ValeFirefly.java` remains byte-identical to the
initial local baseline; only failure diagnostics were added to its GameTest.

## Changed files for Stage 3.4a

- `WiseCottageResidency.java`: discovery, durable issuance and saved association.
- `WiseFlyAgaric.java`: resident NBT, persistence, bounded excursions/home pauses,
  dry porch navigation and entrance control; approved client appearance untouched.
- `MushroomValeCottagePreview.java`, `MushroomVale.java`: read-only developer
  resident inspection under the existing opt-in/permission gate.
- `test/WiseCottageTests.java`: eight targeted residency tests.
- `test/ValeFireflyTests.java`: failure-only collision diagnostics.
- `tools/run_vale_stage34a_checks.py`: isolated focused/residency/firefly/build/full
  runner with fresh worlds and completion-summary enforcement.
- `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION.md`, this report
  and Stage 3.4a validation metrics/telemetry.

All unrelated existing uncommitted changes were retained. The preservation
audit compares with a hash manifest captured at task start, including untracked
Stage 3.4 assets; it is separate from comparisons against Git HEAD.

Built artifact: `build/libs/dudunka-0.27.0-alpha.jar`. The final full-suite world
seed was **-2079127450513311024**. Final residency/build/full-suite production
input hashes match the final source files. Existing deprecation notices remain.
No temporary validation wrappers/templates are packaged. Evidence:
[structured metrics and source/artifact hashes](validation-data/mushroom-vale-stage34a-metrics.json),
[completion/model/resource/audit telemetry](validation-data/mushroom-vale-stage34a-telemetry.txt).
Python runner syntax and `git diff --check` pass.

## Developer inspection and remaining manual checks

Exact fixture, discovery and inspection commands are in
[TESTING.md](TESTING.md#stage-34a-wise-cottage-residency). For a cottage whose
floor origin is `32 160 8`:

```mcfunction
/dudunka_dev mushroom_vale resident 32 160 8
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s UUID
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s WiseCottageOrigin
```

The first command prints the designated UUID, rotation, home and loaded state.
Substitute that UUID in `/data get entity UUID` for unambiguous full NBT.
A second independent natural elder nearby is not a duplicate designated resident.

No graphical Minecraft or BMC4 session was run during Stage 3.4a. The existing
visual approval stands. Manual checks remain for actual process save/quit/restart
identity retention, old-save enrollment, multiplayer client synchronization,
door/player access, gentle movement under modpack terrain/path changes and
full BMC4 acceptance. Automated disk/NBT tests do not replace a process restart
or visual/multiplayer modpack acceptance.
