# Stage 3.8 — Mystery of the Forgotten Arch

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Baseline: published Stage 3.7/3.7b commit
`e21c07abca08774084c8a0de8247d1b27b538366`, verified by `git ls-remote origin refs/heads/main`.
Stage 3.8 changes remain uncommitted and unpushed. No production save was opened or
modified; all automated worlds are fresh isolated test directories.

| Status | Evidence |
| --- | --- |
| Implementation | Native forest arch, existing awakening/travel, five illustrated album chapters, local locator and crafting alternative implemented |
| Build/model/resource verification | **PASS**, final source, `20261011T230108Z-3244937` |
| First full GameTest run | FAIL, `20261011T222648Z-3225594`: 253 complete, 252 passed; remote natural-arch family fixture fails readiness |
| Second full run | `20261011T223941Z-3232477`: build PASS, 252/253; remote fixture did not tick within the unchanged timeout |
| Earlier focused eight tests | **PASS**, `20261011T224400Z-stage38-focused-3235099` |
| Third full run | `20261011T224513Z-3236045`: build PASS, 252/253; isolated remote readiness timeout |
| Fourth full run | `20261011T225039Z-3239070`: build PASS, 251/253; remote readiness passed, forest-boundary fixture assertion and existing natural-arrival test failed |
| Latest focused eight tests | **PASS**, `20261011T225613Z-stage38-focused-3242140` |
| Fifth full run | `20261011T225711Z-3242934`: 252/253; native setup/continuation deadline expired; existing natural-arrival case passed |
| Final full automatic verification | **PASS**, **253/253** required GameTests (245 regressions + 8 new), `20261011T230108Z-3244937` |
| Static checks | **PASS**, `git diff --check`, new-file whitespace, five JSON files and 35 matching RU/EN keys/placeholders |
| Local graphical Minecraft / exploration / real restart | **NOT RUN** for Stage 3.8 |
| Real multiplayer and full BMC4 | **NOT RUN** |
| Stage 3.8 publication | Not performed; not authorized |

## Files and delivered behavior

- `ForgottenArchStructure.java`, `ForgottenArchPiece.java`, `DudunkaMod.java`:
  additive native structure type/piece, single-chunk forest surface placement,
  deterministic random spread, no retrofit or terrain adaptation; no new libraries.
- `data/dudunka/worldgen/structure/forgotten_mushroom_arch.json`,
  `worldgen/structure_set/forgotten_mushroom_arches.json`,
  `tags/worldgen/biome/has_structure/forgotten_mushroom_arch.json`: separate data
  using forest tags, spacing 24/separation 8/salt 38170841; no vanilla overrides.
- `FamilyGatewayEntity.java`: optional `ForgottenArch` root marker, unchanged
  owner-specific 60-tick awakening and original adult/trust/pairwise friendship
  checks; successful rituals unlock the album memory for their actual performer.
- `MushroomVale.java`: only successful travel awards First Journey; existing
  arrival/return safeguards, bookmarks and physical exit are reused.
- `MysteryAdventure.java`: independent owner-keyed SavedData for five persistent
  sequential chapters, real family/forest observation, real ritual and journey
  events, tolerant loading and no changes to old memories/requests/notes.
- `ForgottenArchDiscovery.java`: explicit, bounded local echo using loaded chunk
  block-entity positions and intact natural frames; no generation/force-loading,
  speculative coordinates or persisted locator target.
- `FamilyAlbum.java`, `AlbumCommands.java`, `AlbumNetwork.java`: private bounded
  snapshot and authenticated held-album locator command; protocol **10** is
  technically required by these additional wire fields/request. Older protocol-9
  clients are rejected rather than mis-decoding a changed snapshot. Registry IDs,
  companion UUIDs and old save formats are preserved.
- `client/FamilyAlbumScreen.java`, `client/MysteryIllustrations.java`, RU/EN locales:
  five chapters in the existing album with native GUI drawings, hidden later
  milestones, scroll/paging, direction/distance, no-echo feedback and craft advice.
- `test/MysteryAdventureTests.java`: eight focused GameTests; existing gateway
  fixtures shared at package scope, with a safe-departure diagnostic in the existing
  natural-arrival case. `test/FamilyTests.java` updates only malformed
  payload offsets for the two appended protocol fields; assertions remain intact.
- README, Mushroom Vale roadmap/gameplay/technical contract, family album,
  memories, compatibility/workflow and testing documentation updated.

## Coverage and limits

The eight new tests check:

1. Registered native structure/set, forest-only eligibility, deterministic rare
   placement and native piece serialization.
2. Fresh chunk generation in an already running world, dormant unowned frame,
   moss/carpet/vines, no FULL-chunk retrofit, natural awakening/owner isolation,
   BE reload, successful journey memory, physical return and companion identity.
3. Owned Dudunka/adult-family observation, private server snapshot and untouched
   pre-existing family memories.
4. Monotonic per-owner chapter persistence, malformed/duplicate row handling.
5. Only complete natural loaded frames produce locator coordinates; crafting and
   broken frames do not; no additional loaded/forced chunks.
6. Crafted gateway remains an adventure route; unsafe entry cannot award First Journey.
7. Held-album/authentic-session checks and replay/foreign-session rejection.
8. Protocol round trip preserving old memories, five-chapter limit and no later
   coordinates for a locked story.

Existing 245 tests remain the regression baseline, including two-player access,
independent return bookmarks, travel veto and unsafe fallback, companion care/home/
carrier/recovery state, dimension/worldgen/saved-data behavior and resources.
Serialization boundaries are automated evidence, not an actual dedicated restart.

## Stabilization evidence

First full run `20261011T222648Z-3225594` reported only
`newChunksGenerateUnownedArchAndFamilyCanAwakenTravelAndReturn` failing at
“Legitimate family starts unowned natural arch ritual”. Physical native forest
placement, dormant unowned state and overgrown decoration assertions had passed.
The first focused follow-up `20261011T223123Z-stage38-focused-3227736` ran only the
new eight cases and reported the same test failing earlier with the precise
missing-Dudunka readiness feedback. The remote generated chunk begins inaccessible
to entity lookup; ordinary GameTest fixture tickets cover the test origin, not the
remote arch. Test-site loading was corrected without relaxing the ritual.
No production chunk tickets or changes to family rules are introduced.

Single-test follow-ups `20261011T223254Z-stage38-focused-3228671` and
`20261011T223555Z-stage38-focused-3230260` retained the failure. Diagnostics showed
that a chunk's desired `ENTITY_TICKING` status was insufficient: the family objects
were absent from live lookup. Fixed-delay promotion was replaced with a standard
GameTest sequence waiting for `isPositionEntityTicking` **and** `areEntitiesLoaded`
before introducing/moving companions. Canonical live UUID/readiness assertions and
the original 220-tick test timeout remain intact. The temporary test-site portal
ticket is removed in cleanup. Callback failure paths now clean up immediately.
Single-test run `20261011T223817Z-stage38-focused-3231565`: **PASS**, native generation,
actual natural-arch awakening, private memory, BE reload, entry and physical return.

Full run `20261011T223941Z-3232477` exposed a further fixture limitation: the
normal portal ticket did not reliably activate the remote entity sections under
the complete GameTest workload. Build passed; again 252/253 cases passed, with
only the remote readiness condition timing out. The test now uses the repository's
existing remote-entity convention (`FamilyTests`): temporarily force **only the
isolated test world's generated arch chunk**, retain the previous forced state,
and restore it in cleanup. Actual loaded/ticking section checks still precede
family creation, and the original timeout/readiness/UUID/ritual assertions remain.
Production code contains no added forced chunks or custom travel tickets.
Focused run `20261011T224400Z-stage38-focused-3235099`: **PASS**, all eight new cases, including the
natural round trip and the new unloaded-range locator probe.

Full run `20261011T224513Z-3236045` again passed 252/253 with only remote
readiness timing out. The existing cross-dimension recovery test explicitly loads
the surrounding 3×3 FULL chunks before waiting for ticking entity sections. Applying
that same bounded, test-only setup resolves readiness under the full workload,
without changing the 220-tick timeout or any production chunk-loading behavior.

Run `20261011T225039Z-3239070` passed build and 251/253 GameTests. Readiness now
passed, exposing a separate fixture assumption: the player's southern approach
crossed the root's four-block biome quart, so the player could stand in plains
beside a valid forest arch. The test now uses a safe approach in the root's biome
quart and keeps the exact chapter-three assertion. The same run also failed the
existing natural-arrival test; a separate safe-departure invariant was added to
make any recurrence diagnostic, without changing its travel/return assertions.
Focused run `20261011T225613Z-stage38-focused-3242140`: **PASS**, all eight new tests.

Run `20261011T225711Z-3242934` completed 252/253, with only the native remote test
failing at its deadline ("No sequences finished"). The existing natural-arrival
case passed its new safe-departure invariant and original assertions; the prior
single failure remains retained and its cause is not established. Repeated full
workload deadlines, alongside focused passes, show that the 220-tick native fixture
budget cannot reliably cover remote asynchronous entity-section setup plus the
ritual. It now uses the established `recovery_dimension` **1200-tick setup/test
budget**, with matching cleanup lifetime, and one sequence through completion.
The actual ritual result is still asserted **62 ticks after its start**; no gameplay
requirements, timing, readiness or identity assertions are relaxed. A single log
records the actual readiness tick for review. Production code is unchanged.
In final run `20261011T230108Z-3244937`, readiness was logged at **tick 160**
(root `13287, 71, 12423` in this isolated seeded test world). The unchanged 62-tick
ritual assertion therefore occurs at tick **222**, beyond the previous 220-tick
deadline and its tick-195 fixture cleanup. This directly confirms the old lifetime
mismatch. These seeded fixture coordinates are not player-world guidance. The final server
reported **253 complete, all 253 required tests passed**. Build/model/resource
verification also passed in the same run; no further production changes followed.

The final suite also strengthens no-retrofit coverage to compare the entire piece's
chunk-height footprint, checks a juvenile cannot unlock adult cooperation, and uses
a detached connected player to probe an unloaded locator area without movement
itself loading that chunk. Existing malformed-packet fixture offsets move by two
bytes for the appended default story-count/echo fields; their original rejection
assertions are preserved.

Native worldgen initially wrote provisional `DUMMY` block-entity NBT for all twelve
EntityBlock states, although only the root owns an entity. The piece now removes
only the eleven provisional non-root records before chunk promotion. Block states,
root metadata and access semantics are retained; focused logs no longer contain
those non-root block-entity load warnings.

## Reproduction and remaining acceptance

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
git diff --check
```

Focused diagnostics use the same existing temporary namespace/delegating-test
pattern as the repository's focused tools, with generated helpers/init script and
fresh worlds under ignored `build/reports/dudunka-tests/<run-id>/` and `run/`.
They never change production source to skip tests. Complete logs and failed runs
are retained.

Manual steps: [TESTING.md — Stage 3.8](TESTING.md#stage-38-mystery-of-the-forgotten-arch).
Accept RU/EN pictures, tab fit and scrolling at several GUI scales; natural
exploration rarity/findability and access in BMC4 forests; new chunks of a copied
old save with unchanged old terrain; real two-player ritual/bookmark/progression
isolation and real restart; territory/modded-hazard interactions. The local locator
can only hear already loaded land and deliberately gives no distant guarantee.
Unsafe terrain or another worldgen mod may suppress a potential arch. Crafting
remains available, including in structure-disabled or extensively explored worlds.
Stage 4 music restoration is deferred. No Stage 3.8 commit, push or JAR release.
