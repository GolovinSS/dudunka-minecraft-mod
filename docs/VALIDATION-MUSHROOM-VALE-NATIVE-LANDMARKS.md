# Native music landmark investigation — 2026-10-14

Implementation: **written, validation pending**. Build and GameTests: **NOT RUN**.
Manual Minecraft/BMC4 acceptance: **NOT RUN**. Publication: **NOT PERFORMED**.
Static whitespace checks (`git diff --check`, plus checks of changed untracked
files against `/dev/null`): **PASS**.
Version, registry IDs, piece NBT, album/network protocol and quest progress formats
are unchanged. No player save was opened, repaired or rewritten by Codex.

## Confirmed defect and remaining uncertainty

`ValeAdventureStructure.findGenerationPoint` accepts a predicted dry noise-terrain
site at STRUCTURE_STARTS and adds one nonempty `ValeAdventurePiece`. Physical
placement happens later, during FEATURES. Previously `postProcess` silently
returned if its actual-site preflight failed. Minecraft 1.20.1's
`StructureStart.isValid()` tests only whether its pieces container is nonempty;
it does not inspect blocks or the result of `postProcess`. That start therefore
remained valid, was saved with its piece, and could be returned by `/locate`
indefinitely despite having no blueprint. Block-write return values were also
ignored. This is a confirmed source-level defect shared by **all five** music
landmarks, not an assumption that the player missed a small structure.

The exact rejection at the user's two Music Bells sites is **not yet confirmed**:
their seed/coordinates and generation-time evidence were unavailable. Potential
preflight failures include actual support differing from predicted noise height,
the actual biome lookup differing from the unzoomed noise-biome footprint, and
neighboring chunks' features obstructing the site before its own decoration.
These are diagnostic possibilities, not claims about those two sites.

Checked against the locally cached Forge 47.4.13 / Minecraft 1.20.1 sources:

- `ChunkStatus`: starts precede noise, surface, carvers and FEATURES.
- `ChunkGenerator.applyBiomeDecoration`: native placement uses the current
  chunk's X/Z writable bounds and world-height Y bounds. A neighbor's feature
  work can precede this chunk's FEATURES even though structures precede
  vegetation within one chunk.
- `StructureStart.placeInChunk`/`isValid`/serialization: a void placement return
  neither removes a piece nor invalidates its start.
- `StructureCheck` and `ServerLevel.onStructureStartsAvailable`: presence has a
  separate cache that must be refreshed after invalidation.
- `StructurePlacement.getLocatePos`: the old default result was the chunk's
  minimum X/Z, while the anchor is its middle (+8,+8), about 11.3 blocks away.
  That offset does not explain searching 50 blocks and finding nothing.

The saved root Y is the maximum first-free noise-surface Y over a 13×13
footprint, with at most two blocks of variation. Pillars follow saved column
heights. Piece bounds contain all blueprint cells; X/Z footprint uses local
coordinates 2 through 14 in the owning chunk. No confirmed blueprint offset,
empty-blueprint branch or ordinary chunk-clipping error was found. Geometry
and terrain-selection rules were therefore preserved.

## Changes

- `ValeAdventurePiece`: preserve the preflight rules, report the first exact
  rejection, invalidate a rejected start **only during its owning new chunk's
  normal generation**, and refresh vanilla's presence cache. Check block writes
  and read them back; on failure restore this attempt's touched cells and
  invalidate its start. Finished chunks and their saved metadata remain untouched.
- `ValeLandmarkCommands`: the five exact `/locate structure dudunka:<music ID>`
  selectors use the existing native random-spread placements and starts, finish
  candidate chunks through normal generation, then require every blueprint cell
  to match, contained in both piece/start bounds and the owning chunk. Success
  reports the saved anchor's exact X/Z. Missing/damaged old starts are skipped
  without removing them or restoring blocks. Only an explicit operator locate
  performs this extra generation; no persistent forced tickets are installed.
  The search retains vanilla's 100-region ring bound and additionally caps
  physical candidate checks at 16, returning an explicit failure if exhausted.
  Other structure IDs, tag selectors and direct native map-search APIs retain
  vanilla behavior; those APIs still describe starts rather than block integrity.
- `MushroomVale`: register these exact selectors and the opt-in inspection command.
- `ValeAdventureTests`: retain the existing checks and add native regressions;
  the focused namespace now contains **six** required tests.

Quest interaction code, music/audio ducking, networking, album, AI and family
travel were not changed. Generated intact sites still use the saved native piece
and original interaction anchors. Completed progress is independent of physical
site discovery and is never reset by this change.

## External automated validation — NOT RUN

With JDK 17, run separately from any active runClient:

```bash
./tools/test.sh build
DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest
./tools/test.sh gametest
```

Check the focused report contains all **6** required tests. Coverage includes:

1. Registry/biome/noise-generator restrictions and saved piece geometry.
2. Existing synthetic safety guards and finished-chunk no-retrofit behavior.
3. The real public locate command for all five IDs, complete world blocks,
   anchor coordinates, all five quest resolvers, and full chunk save/reload.
4. Direct native start-only searches at positive and negative remote origins for
   every kind, neighbors completed first, multiple absolute terrain heights,
   complete blocks for accepted starts and explicit invalidation for rejections.
5. An oak-log obstruction inserted after a real native start reaches CARVERS;
   ordinary FULL generation must reject it, preserve the obstruction, invalidate
   the start/cache, refuse quest resolution, and locate another physical site.
6. A genuinely generated native landmark made empty to model an old saved ghost;
   discovery must skip it, preserve its start/empty blocks and chapter progress,
   and find another intact native landmark.

Persistence checks write Minecraft's **whole chunk** with `ChunkSerializer` to
a temporary compressed NBT file and reload it with the native chunk reader.
They compare actual block sections and start validity/anchor/bounds/blueprint,
including rejected and legacy-empty sites. Temporary files are deleted. They
do not insert synthetic starts or replacement chunks into the live world.
This covers the chunk disk codec; actual unloading and a complete game restart
are separate manual checks below. No executed result is claimed here.

## runClient acceptance and narrowly scoped diagnostics — NOT RUN

Launch the existing runClient configuration with `-PmushroomValeDev=true`
(the project's opt-in `dudunka.devMushroomVale` JVM property). Use a fresh test
world or a backup COPY of an existing world; enable structures and enter Vale.

1. At each previously missing site's X/Z, travel there so its chunk is loaded,
   then execute `/dudunka_dev mushroom_vale inspect_music <x> <z>`. This command
   does **not** load chunks or place blocks. Record its chat output / `latest.log`.
   It prints seed/dimension/generator, chunk status, start validity/piece count,
   start/piece bounds, saved anchor/floor-height range, matching block count,
   and up to eight expected-versus-actual mismatches. Its current preflight is
   explicitly **not** a historical explanation of an old rejection.
2. In new exploration territory, execute each exact command:

   ```text
   /locate structure dudunka:music_bells
   /locate structure dudunka:jumper_dance
   /locate structure dudunka:memory_stone
   /locate structure dudunka:silver_web_harp
   /locate structure dudunka:singing_mushroom
   ```

   These commands may take longer because they now complete physical generation
   before reporting success. Teleport in Spectator from above: `~` in the clickable
   result preserves your current Y and is not a safe surface teleport height.
   Inspect the returned chunk: every blueprint block must match, and saved anchor
   X/Z must equal locate X/Z. Record seed, coordinates and visible screenshots.
3. Right-click Bells stems/marker, Dance flowers/root, Memory Stone's exposed
   center, Harp strings/root and Concert root. Use the existing chapter
   prerequisites. Check private music, progression and family travel normally.
4. Save, exit the world completely, reload, return to each saved coordinate and
   inspect again. Blocks, anchors, locate results and completed progress must
   persist. Repeat in BMC4 v55.5 and a second seed, including negative coordinates
   and a different neighboring-chunk exploration order.
5. For new rejected placements, capture the bounded `Vale landmark rejected:`
   log line: ID, seed, chunk, anchor, bounds and exact first rejection reason.
   New rejected attempts should inspect as invalid starts after completion/reload.
   No successful exact-ID locate should lead to such a rejected site.

## Existing missing sites and recovery

No automatic retrofit, chunk deletion, start rewrite or quest-progress migration
is performed for already completed worlds. Old empty starts can still exist in
their saves, but the five exact-ID commands now skip them. Use another intact
site or explore fresh chunks; completed chapters remain completed.

A saved `ValeAdventurePiece` retains enough Anchor/Ground/kind data to reconstruct
its blueprint, but **safe recovery requires a separate explicit restoration**
after a backup and inspection of every affected cell for player edits, fluids,
trees and builds. This patch supplies diagnosis, not a repair command. Never
regenerate/delete an occupied chunk to recover a landmark. Vanilla `/place
structure` is not a recovery mechanism for these pieces: their finished-world
placement guard deliberately refuses it. If the old start itself is absent or
corrupt, exact original placement cannot be recovered from that start alone.
