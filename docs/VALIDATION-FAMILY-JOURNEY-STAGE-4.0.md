# Stage 4.0 — Family Journey

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Inspected local `main` HEAD: `23b1db0d70a2ef2aa02233e80437e63e4ddf6cbc`
(`build: update 0.27.0-alpha jar with Stage 3.8`), tracking origin/main with a clean
working tree before implementation. No network fetch or remote HEAD assertion.
Baseline historical results remain in VALIDATION-MUSHROOM-VALE-STAGE-3.8.md.
Implementation is uncommitted. No production world was opened; all test worlds
are fresh directories created by tools/test.sh. No publication/version bump.

## Arrival-failure follow-up (2026-10-12)

Investigation reproduced a planner limitation: companion candidates were tested
only at five fixed Y offsets from the player's landing. On raised or dipping
natural terrain, no candidate could reach the surface even when safe ground was
available nearby. The shared safety check also applied pathfinding malus to
non-colliding vegetation in its halo, rejecting harmless leaves. The planner now
tests the already-loaded no-leaves motion-blocking surface in each bounded
candidate column, then the same five nearby heights. It stays in the landing
chunk, reads only loaded positions and never changes terrain. Unsafe blocks,
fluids, collision, occupied boxes, world border and Forge veto checks remain.

If a member has no landing, one warning reports its owner and Kind, bounded
candidate count, reason totals and at most four position samples. This is a
single summary per rejected member, not a per-block log stream.

## Cold destination and return gateway follow-up (2026-10-12)

The client report was caused by two different chunk states in the travel sequence.
Block planning can safely use a `FULL` chunk, but native companion insertion fails
until that chunk reaches `ENTITY_TICKING`. A first-time family request now leaves
the player and companions at the source, requests a temporary region ticket for
the single selected arrival chunk, and retries the complete route after entity
ticking becomes available. It releases the ticket after player tracking takes
over, if the player moves or changes dimension, or on timeout (200 ticks). The cold regression observed
`FULL`, `entityTicking=false`, then confirmed the queued transfer completes without
adding a persistent forced chunk.

The missing return gateway in the client report followed the same incomplete entry:
the physical gateway called automatic placement only after successful entry. The
deferred completion path now performs that call too. Normal natural placement,
incomplete-frame refusal, unsafe-terrain refusal and Forge placement veto remain
separate outcomes. `MushroomValeReturnGateway` now logs one bounded reason summary
on an unavailable site or veto, with at most four candidate samples. In the full
suite, expected diagnostics appeared for intentionally broken/unsafe/veto fixtures;
the normal natural-arrival placement tests passed.

| Status | Evidence |
| --- | --- |
| Final focused build and GameTests | **PASS**, 17/17, `20261012T162907Z-3640404`; includes explicit FULL/non-ENTITY_TICKING cold transfer, ticket cleanup and emergency solo return during pending family travel |
| Final full build, model checks and GameTests | **PASS**, 270/270, `20261012T163028Z-3641956` |
| Whitespace and locale JSON checks | **PASS**, `git diff --check`; English/Russian locale files parse |
| Updated runClient, restart, multiplayer and full BMC4 acceptance | **NOT RUN** |

| Status | Evidence |
| --- | --- |
| Focused regression suite | **PASS 16/16**, `20261012T141042Z-3574053`; uneven raised terrain, harmless leaves/tall grass, distinct safe landings and block-state preservation |
| Full rerun 1 | Build **PASS**; **1 existing gateway fixture failed** (`gatewayRejectsDuplicateKindsAndUuids`, expected `duplicate_kind`), `20261012T140105Z-3567942`; focused journey suite passed |
| Full rerun 2 | Build **PASS**, **269/269 GameTests PASS**, `20261012T140526Z-3570664` |
| Final full verification | Build **PASS**, **269/269 GameTests PASS**, `20261012T141145Z-3574830` |
| Diff whitespace | **PASS** (`git diff --check`) |

| Status | Evidence |
| --- | --- |
| Implementation | Stage 4.0 family entry/physical return/command, explicit solo, bounded rollback and private recovery feedback |
| Focused setup run | `20261012T131905Z-3545146`: compiled; startup failed because new test template used a doubled namespace; corrected |
| Focused fixture run | `20261012T132026Z-3546155`: server stopped on missing source instance after delayed fixture cleanup; test-only section visibility corrected |
| Focused nine tests | `20261012T132254Z-3547477`: 6/9; three fixture failures (overlapping families/edge landing space); corrected |
| Focused nine tests | `20261012T132412Z-3548378`: 7/9; gather helper excluded the player from companion occupancy; corrected |
| Focused fourteen tests | `20261012T132623Z-3549616`: 13/14; incomplete home fixture was incorrectly asserted ready; replaced with ownership/Kind/binding retention assertions |
| Fourteen focused tests | **PASS 14/14**, `20261012T132803Z-3550651` |
| First full verification | Build **PASS**, **265/267** GameTests, `20261012T132940Z-3551650`; exact solo return used a chunk-edge departure and duplicate fixture inspected invisible target entity; fixtures corrected with original assertions/timeouts retained |
| Final focused tests | **PASS 15/15**, `20261012T133355Z-3554270`, including player post-veto collision/rollback |
| Final full build and GameTests | **PASS** build/model/resource checks and **268/268** required GameTests (253 existing + 15 new), `20261012T133455Z-3555090` |
| Static validation | **PASS**: git diff --check; changed/new-file whitespace; shell syntax; locale JSON and all 9 new EN/RU keys/placeholders; 15 focused cases and copied empty template |
| Graphical runClient / actual disk restart / copied old-world acceptance | **NOT RUN** |
| Real multiplayer / full BMC4 v55.5 / forced JVM crash | **NOT RUN** |
| Commit, push, PR, published JAR, Stage 4.1 | Not performed; not authorized |

## Changed files and design

- `FamilyJourney.java`: loaded-only selection and canonical identity/registry/carrier
  checks, reserved four-member landing plan, post-veto revalidation, native transfer,
  immediate registry observations, reverse bounded rollback and failure diagnostics.
- `MushroomVale.java`: shared entity-size/exact-position safety checks, reused entry
  and return destination/bookmark logic; additive `/dudunka return_family` command.
- `FamilyGatewayEntity.java`: chooses group/solo on already awakened entry and shared
  physical return. Awakening rules, persisted owner unlocks and five Stage 3.8
  album chapters are unchanged.
- `Companion.java`: explicit transient-goal cleanup before journey. No companion NBT
  schema or permanent behavior changes; home and furniture assignments remain original.
- `FamilyJourneyTests.java`: 17 focused cases described below; namespace-only empty
  template in `data/dudunka_journey/structures/empty.nbt` for isolated selection.
- `FamilyGatewayTests.java`, `MysteryAdventureTests.java`: retain old player-only
  expectations as explicit Shift solo tests; shared fixture cleans actual live UUIDs
  in all dimensions and supports centred journey platforms.
- `build.gradle`, `tools/test.sh`: include both test namespaces by default; optional
  `DUDUNKA_TEST_JOURNEY=1` chooses only new cases in a fresh world, same report runner.
- English/Russian locale files: named eligibility and incomplete-rollback messages.
- FAMILY-PORTAL, FAMILY-TRAVEL, FAMILY-RECOVERY, MUSHROOM-VALE, TESTING and VALIDATION docs:
  current behavior, transfer limits, validation commands and manual acceptance.

No new registered gameplay IDs, entity types, dependencies, mixins, worldgen,
quests, music or album chapters. Album network protocol remains **10**. Existing
companion, carrier, home, registry, bookmark and gateway save formats are unchanged.

## Operation and failure semantics

See [FAMILY-TRAVEL.md](FAMILY-TRAVEL.md#stage-40-explicit-family-journeys) for the
complete selection and landing rules. Plan all four boxes before moving anyone;
transfer Dudunka → Marusya → Syusya → player; validate canonical live arrivals and
update registry immediately. Explicit native Forge dimension veto before companion
movement is necessary because Entity.teleportTo(ServerLevel, ...) does not post it.
Revalidate after that event as well as immediately before/after group movement.
Native destination EntityJoinLevelEvent refusal restores the same removed live
source object with Forge revive/addFreshEntity, without recreating from backup NBT.
A refused source reinsertion or third-party destruction cannot be overridden safely;
it reports incomplete rollback rather than manufacturing another entity.

On failure, attempt at most one backward transfer per moved companion in reverse
order, using exact original positions/rotations, loaded safe source space and Forge
vetoes. No retries, force-loading or terrain writes. Player is normally still at
source; if a listener redirected the player, try one safe player rollback. Preserve
old bookmark on failure, including incomplete rollback. Entry bookmark is written
only after successful group transfer; successful return consumes it only then.
Incomplete rollback logs owner/UUID, sends private feedback and observes surviving
live entities at actual positions for existing album recovery. A vetoed rollback
is tested recoverable via FamilyRecovery without duplicate UUIDs.

## Coverage

Seventeen focused tests now cover physical group round trip, awakened crafted and
natural saved gateway metadata, UUID/owner/Kind/age/trust/name/permanent NBT, original
home and furniture assignments, unchanged friendship, companion/bookmark/registry
NBT reload, family command with no gate, emergency solo, absent/partial family,
juvenile, duplicate UUID/kind, noncanonical instance, registry conflict, wrong owner,
carrier/live conflict, WAIT, leash, rider/vehicle, active ritual, unsafe departure,
border, loaded-only inspection, player-only destination space, companion and player
Forge vetoes, backward veto plus recovery, destination insertion refusal, event-time
eligibility mutation, player landing obstructed during transfer, return veto/retry, independent owners/bookmarks, UUID uniqueness
in all loaded dimensions, cold destination preparation with a temporary scoped ticket,
emergency solo return cancelling pending group travel, and family placement over
uneven raised terrain with harmless vegetation and no
travel-time block-state changes.

Stable save assertions exclude transient navigation, petting/scene/catch-up state
and cooldown cleanup. Fixture visibility tickets are test-only and restored; the
cold destination test verifies the temporary journey ticket is not a persistent
forced chunk. NBT round trips
are **not** a real disk unload/restart or crash-consistency demonstration.

## Remaining acceptance and risks

No persistent transition journal or claim of crash atomicity. JVM death between
native transfers, player save and SavedData writes can leave split family locations
or stale bookmarks/registry; observation is dirty-marked, not synchronously flushed.
Arbitrary third-party listeners that destroy/mutate entities or veto restoration
remain a compatibility risk. Live survivors are recoverable through existing album
recovery; missing disk entities cannot safely be reconstructed by this feature.

Cold destination entity data can require an explicit retry. Searches stay within
one arrival chunk and bounded radius, so crowded/uneven terrain can conservatively
refuse even when more distant space exists. No custom chunk loader is introduced.
Two synthetic owners are tested, but real clients, saved-world restart, protection
mods, BMC4 hazard blocks and dimension interactions need manual acceptance.

Manual runClient steps, including old-world, restart, multiplayer and BMC4 checks:
[TESTING.md — Stage 4.0](TESTING.md#stage-40-family-journey).
