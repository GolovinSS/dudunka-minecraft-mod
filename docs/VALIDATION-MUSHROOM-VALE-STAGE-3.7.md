# Mushroom Vale Stage 3.7 — The Family Portal

Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, Better MC [FORGE] BMC4 v55.5.
Implemented on the current local `main` after Stage 3.6. The developer's session
handoff reports that Stage 3.7 GameTests and developer-family command tests passed
after fixture/regression corrections. Local `runClient` works; gateway appearance,
activation and entry into Vale were accepted locally. These are developer-reported
results; their logs were not re-reviewed or runs repeated in the Stage 3.7b session.
Dedicated BMC4 acceptance is postponed. The user authorized committing and pushing
Stage 3.7/3.7b on 2026-10-12; no JAR release is implied.
The added physical return gateway has a separate report:
[VALIDATION-MUSHROOM-VALE-STAGE-3.7b.md](VALIDATION-MUSHROOM-VALE-STAGE-3.7b.md).

| Status | Evidence |
| --- | --- |
| Implementation | Stage 3.7 and Stage 3.7b complete |
| Cheap static review | `git diff --check` passed; gateway JSON parses, 24 blockstate variants resolve to local models, 29 English/Russian keys match |
| Gradle build/resource/model verification | **PASS**, run `20261011T220402Z-3213750` |
| Stage 3.7 GameTests | **PASS**, 245/245 including Stage 3.7b/regressions, run `20261011T220426Z-3214532` |
| Manual Minecraft visuals and gameplay | Both gateways accepted locally by the user, confirmed 2026-10-12; real multiplayer **NOT RUN** |
| Dedicated BMC4 existing-save, two-player, reconnect, real restart and rollback acceptance | **NOT RUN** |
| Publication | Commit/push authorized; no JAR release |

## Behavior implemented

- Craftable mushroom gateway from five stone, red/brown mushrooms and one amethyst
  shard. No Vale-only material, exploration reward or new Overworld generation.
- Native voxel mushroom frame, moss, white spots and crystal. Empty inactive
  opening/light 2; translucent pink active opening/light 12 and colored motes.
  Fixed north/south orientation, 3 × 4 × 1 structure, 3 × 3 floor/clearance preflight.
- Air-only placement rejects buildings, containers, replaceable plants, fluids,
  entities, unsupported/unloaded/out-of-border positions and placement permission
  failures. Normal Forge multi-place veto rolls back all cells and keeps the item.
- Exactly three live loaded owned companions, one of each kind, adult stage 2,
  trust ≥ 20, every pair's existing friendship score ≥ 10, within radius 8. These
  constants are together in `FamilyGatewayRules` for later balance adjustment.
- Canonical live UUID and cross-level duplicate checks, carrier exclusion, distinct
  kind checks and indexed owner/kind cross-checks. No recovery or chunk searches.
- Localized private named failure feedback, no penalty, offerings, score changes
  or consumption on failed activation. A 60-tick cooperative scene uses existing
  WAVE/PURR/CURIOUS poses and three colors of connecting particles.
- Owner-specific completion stored additively in the root block entity's chunk
  NBT. Competing players cannot replace an ongoing scene or inherit its completion;
  friends are reserved to one scene. Incomplete scenes cancel on unload/departure.
- A second explicit click enters using shared existing Vale safety/bookmarks.
  Repeated visits need no family ritual. `/dudunka return` works for non-operators
  anywhere in Vale, with a clickable hint on entry and login in Vale.
- Unsafe arrival, unsafe departure/fallback, missing Vale and Forge travel denial
  retain the player's location and existing bookmark as applicable. Explicit travel
  uses the existing bounded target-chunk loading; no custom tickets/force-loads.
- Companions are never moved, reconstructed, spawned or assigned replacement UUIDs.
  Existing home/furniture/follow/stay assignments, family scores, memories, carrier,
  recovery, album protocol 9 and other dimension IDs/formats remain unchanged.

## Changed files

| Area | Files |
| --- | --- |
| Gateway structure and placement | `src/main/java/io/github/golovinss/dudunka/FamilyGatewayBlock.java`, `FamilyGatewayItem.java` |
| Eligibility, owner persistence and scene | `src/main/java/io/github/golovinss/dudunka/FamilyGatewayRules.java`, `FamilyGatewayEntity.java` |
| Registration and temporary existing poses | `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`, `Companion.java` |
| Shared travel, public return, login hint | `src/main/java/io/github/golovinss/dudunka/MushroomVale.java`; `MushroomValeReturns.java` documentation only |
| Automated coverage | `src/main/java/io/github/golovinss/dudunka/test/FamilyGatewayTests.java` |
| Models/state | `src/main/resources/assets/dudunka/blockstates/family_gateway.json`; fourteen `models/block/family_gateway_*.json` models; `models/item/family_gateway.json` |
| Localization | `src/main/resources/assets/dudunka/lang/en_us.json`, `ru_ru.json` |
| Craft, book unlock, drop | `src/main/resources/data/dudunka/recipes/family_gateway.json`, `advancements/recipes/decorations/family_gateway.json`, `loot_tables/blocks/family_gateway.json` |
| Documentation | `README.md`, `docs/FAMILY-PORTAL.md`, `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, this report |

No existing test assertions were removed or weakened. Stage 3.7b extends the gateway
test fixture and physical-return coverage in the separate report. Stage 3.6 wildlife
code/data, dimension/biome JSON, entity IDs, protocol, build configuration and
dependencies retain their previous implementation.

## Sixteen Stage 3.7 gateway GameTests

Stage 3.7, Stage 3.7b and regression verification **PASS**, 245/245 in the
final Stage 3.7b run linked above. The developer-family setup test is also present locally
and was reported as passing in the handoff.

| Test | Main assertions |
| --- | --- |
| `gatewayPlacementNeverOverwritesBuildingsFluidsPlantsOrEntities` | Failed placement preserves existing blocks/containers/item; unsupported and occupied floors fail; valid complete inactive gate; frame cleanup and one ordinary item drop |
| `gatewayProtectedPlacementRollsBackAndKeepsItem` | Real ItemStack/Forge multi-place protection veto restores twelve cells and removes temporary BE |
| `gatewayReportsMissingJuvenileTrustAndEveryFriendshipPair` | Exact localized missing keys, adult/trust boundary and all three friendship pairs; reads never award points |
| `gatewayIgnoresOtherOwnersDeadCarriedUnloadedAndDistantFriends` | Other family, death, carried Syusya, distance and stale unloaded registry rows cannot substitute |
| `gatewayRejectsDuplicateKindsAndUuids` | Same-kind excess, repeated UUID, detached clone and actual loaded duplicate in another level rejected |
| `gatewayThreeAdultsAwakenWithoutMovingOrChangingHomeAndFriendship` | Timed completion, all active states, same instances/UUIDs/positions, companion persistent care/home/furniture NBT and unchanged friendship data |
| `gatewayUsesExistingCharacterPosesAndCancelsWhenAFriendLeaves` | Real AI produces three existing poses; participant departure cancels and releases reservations |
| `gatewayOwnerUnlockSurvivesBlockEntityReloadAndRejectsMalformedOwners` | Actual BE replacement/serialization keeps owner access, ignores malformed rows and does not authorize a stranger |
| `gatewayCompetingPlayersProgressIndependently` | Two complete families serialize their own rituals on the same gate without sharing unlocks |
| `gatewayIdleChecksPlacementAndRitualNeverLoadOrForceChunks` | Unloaded-area placement and entity lookup refuse without loading; activation/tick does not change loaded/forced chunk counts |
| `gatewayInterruptedSceneCannotUnlockAfterReloadOrOwnerDeparture` | Incomplete BE reload releases poses and grants no access; owner departure prevents delayed completion |
| `gatewayFriendsCannotAwakenTwoGatewaysAtOnce` | Two nearby complete gates cannot reserve the same friends concurrently; named busy feedback |
| `gatewayExplicitEntryReturnAndSavedDimensionKeepCompanionIdentities` | Actual gate entry, safe arrival, vanilla player Dimension save, bookmark round trip, non-op public return with dev opt-in absent, original companion complete NBT and repeat entry without family |
| `gatewayUnsafeArrivalAndTravelVetoPreservePositionAndBookmarks` | Unsafe Vale surface and Forge veto preserve exact source position and previous bookmark |
| `gatewayUnsafeReturnPreservesBookmarkUntilDepartureIsRepaired` | Both saved/spawn candidates unsafe: no move/no forgotten bookmark; retry with safe saved point succeeds |
| `gatewayTwoPlayerReturnBookmarksAndOtherDimensionsStayIndependent` | Independent two-player arrivals/returns, unchanged Nether/End availability and return rejected outside Vale |

Existing `MushroomValeTests` assertions remain intact and exercise the now-shared
travel path: developer authorization, original UUID/full-NBT round trips, malformed
bookmarks, hazards/passengers, removed dimension fallback, unsafe fallback and
Forge entry/return veto. Existing family/recovery/carrier and Stage 3.6 wildlife
coverage remains part of the full suite.

Serialization/BE replacement tests exercise restart data boundaries; they do
**not** actually stop/relaunch a dedicated server or load a BMC4 production save.
Those are mandatory separate manual steps.

## External commands and manual acceptance

With Java 17, run from the repository root:

```bash
./tools/test.sh all
```

Share its summary and build/GameTest logs. Retain the generated isolated world for
investigation if anything fails. Do not use the actual player's world for this run.

Follow [TESTING.md — Stage 3.7](TESTING.md#stage-37-family-portal-on-an-existing-dedicated-bmc4-save)
for the complete deployment/rollback procedure. Required manual evidence includes:

1. A separate BMC4 Server Pack v55.5 dedicated Forge 47.4.13/Java 17 instance with
   a complete **copy** of an existing save and matching JAR hashes on every client.
2. Existing inventory/family/memories intact before construction; correct Survival
   recipe and safe placement in already-generated chunks.
3. Inactive/active visuals, three poses, localized private failures and explicit
   entry without unexpected collision travel or companion movement.
4. Two players with independent progression and bookmarks, leave/rejoin at home
   and in Vale, and a real server restart with both players inside Vale.
5. Identical companion UUID, owner, home/furniture modes and recorded locations
   after travel/restart; safe-failure retry and no persistent portal force-loads.
6. Existing carrier/recovery/album/other dimensions and Stage 3.6 wildlife checks;
   complete snapshot rollback with the matching old JAR/config/datapacks.

## Remaining risks and limits

- Build and 245/245 GameTests passed; the user confirmed local acceptance of both
  gateways. Full-pack compatibility and real multiplayer remain **NOT RUN**.
- Existing Vale arrival terrain can be obstructed. The portal never terraforms;
  an observer/operator must clear a safe landing in the bounded origin chunk if
  all candidates fail. Unsafe return retains the bookmark for a repaired retry.
- Visible activation is shared; permission is per owner. A glowing gate may still
  require another player's family ritual. Breaking/replacing a gate clears its
  per-gate unlocks; it does not disable the public return command.
- The frame has fixed north/south orientation. Custom resource packs/shaders and
  pack territory protection need manual visual/event acceptance.
- Minecraft saves chunks, playerdata and SavedData separately. Normal restart is
  supported; crash/partial-backup recovery is not an atomic transaction. Rollback
  must restore a consistent whole snapshot, not only replace the JAR.
