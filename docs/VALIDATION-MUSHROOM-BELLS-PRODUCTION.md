# Stage 4.3a.4 — Mushroom Bells production integration

Implementation: **WRITTEN**. Gradle, Java/resource verifiers and GameTests: **NOT RUN**.
Production Minecraft/BMC4 gameplay, terrain and performance acceptance: **NOT RUN**.
The styled terrain preview's daytime/nighttime visual acceptance was supplied by
the developer; it does not establish acceptance of production placement.
Cheap static whitespace checks (including changed untracked files) and localization
JSON syntax/duplicate-key checks: **PASS**. Resource counts and original source hash
were inspected directly; no automated resource verifier was executed.
Publication: **NOT PERFORMED**. Version, commits, network protocols and registry IDs
are unchanged. All pre-existing local edits, sources and generated assets remain.

## Production and placement

Only new `dudunka:music_bells` pieces select `VisualRevision=3`. The exact 180
approved styled OBJ cell states, seven moss carpets, textures, collision/selection
shapes and four lighting states are reused from the terrain preview. No source,
geometry, texture or lighting asset was regenerated. The scene stays rigid, with
one carved stone and crystal, three mushrooms and the musical ring.

The existing saved 13×13 ground profile still defines placement. New Bells accept
at most one block of variation, checked before creating a start and again before
placement. On a level site the plan equals the accepted terrain preview exactly:
all existing ground is preserved. On a shallow grade, only lower columns under
the 11×9 scene receive one moss support block. Selected neighboring lower ground
cells receive moss to feather the raised edge. Higher terrain is never excavated.
This deliberately restricts suitable terrain compared with old Bells' two-block
allowance. No other landmark's terrain selection changes.

The existing whole-volume dry natural-support, biome, border, build-height,
block-entity, obstruction and writable-chunk checks run before writes. Natural
moss is also accepted as support for revision 3. Only approved small plants are
cleared from scene gaps during new-chunk generation. Trees, builds, fluids and
unavailable/clipped cells reject the whole placement. Gap air is a placement
operation, not persisted geometry: ordinary later vegetation may grow in gaps.

The 13×13 bounds remain inside the owning chunk at native center +8, including
negative coordinates. Only a new owning WorldGenRegion before FEATURES may write.
FULL/upgrading/player chunks are never retrofitted. Block writes retain immediate
readback, touched-cell rollback on failed writes, and start/cache invalidation on
rejection. Revision 3 treats an already matching state as a successful no-op (air
gaps and natural moss can already match); other revisions retain their write path.

The existing exact `/locate structure dudunka:music_bells` route finishes ordinary
candidate generation and checks bounds and required physical objects before
reporting the saved anchor. Rejected new attempts become INVALID_START and refresh
vanilla's separate presence cache; damaged/empty saved sites are skipped without
repairing or rewriting them. The existing 16-physical-candidate / 100-region
search bounds and no-forced-ticket behavior remain. Vanilla tag/map searches still
have their existing start-based semantics; no generic locate system was changed.

## Interactions and compatibility

| Saved visual revision | Layout and listening targets |
| --- | --- |
| Absent / 0 | Original large bells; central carved root and separate stone/crystal column |
| 1 | First modeled cap layout; both historical clue targets |
| 2 | Compact sculpted bells; both historical clue targets |
| 3 | Approved styled scene; one stone body and crown crystal share the listening action |

Every old blueprint branch, saved Anchor/Ground/bounds, structure/piece registry ID,
quest chapter, completion/reward format, prerequisite, pitch and private sound path
is preserved. Saved pieces keep their own revision; merely loading the new mod does
not replace their blocks or advance their revision. Existing chapter progress is
neither migrated nor reset. The four other landmarks and family travel are unchanged.

Source groups bind teal→turquoise/note 0, rose→violet/note 1 and amber→golden/note 2.
The sequence remains 0→1→2 at pitches .75→1→1.25. Stone-body, carving and crown
cells bind the historical listening/reset/replay action. The ring remains scenery.
Matching expected part states are required; arbitrary copies have no native start.
The source-object bindings and canonical anchor cells are validated when the common
layout loads, without changing its format or allocating a per-tick parser.

New English/Russian clue and retry keys explain that the rose-pink mushroom carries
the violet middle note. Old layouts retain their exact existing clue/retry wording.
Note feedback uses the new clapper positions only for revision 3.

31 cells comprising the stone/carving/crown, stems and four light anchors decide
revision-3 playable integrity. Missing ground, plants, ring or cap ornament does
not disable the puzzle. Missing required cells remove integrity/ducking eligibility.
The existing firefly assistance uses that same required subset and native root;
its scheduler, spawn gates, vetoes, caps and despawn rules were not changed.

The server still sends the same optional audio anchor payload. The client accepts
both the historical chiseled root and the exact styled root part, using loaded
chunks only. No protocol change, second music scheduler or new audio channel was
added. Entry/exit radii, fade, gains, music preferences and private cues are unchanged.
The production scene and interaction subset are cached per saved piece; metadata
is parsed once. Static chunk rendering has no new tickers, block entities or entities.

## Measured resource and block budgets

Counts are from the actual local manifests/files, not a runtime profile:

| Package | Runtime files | Uncompressed bytes | Mesh block cells | Baked faces | Max faces/cell |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original registered preview | 715 | 4,780,980 | 347 | 30,394 | 704 |
| Approved styled / production | 187 | 1,770,179 | 180 | 5,556 | 130 |
| Both packaged variants | **902** | **6,551,159** | 527 possible states | 35,950 | 704 |

Each new landmark uses only the styled geometry: **81.72% fewer faces** than the
original preview, one shared OBJ, one atlas and three materials. It places **187
non-air cells on flat terrain** (180 mesh + seven carpet cells), up to **320** with
99 one-block grade fills and 34 eligible feather cells. The half-grade fixture uses
248 cells. These are plan cells, including unchanged existing moss where applicable;
they are not additional model faces. Clearing air is excluded from the non-air count.
Actual lights remain **13 at the crown and 10 at each clapper**, four emitters total.

No runtime resource was removed. Inspection found the old 715-file set is still
referenced by the registered original-preview blockstates, its retained commands,
manifest and verifiers, and may be represented in saved preview blocks. Removing
it would sacrifice saved rendering or pre-existing local work. The 187 styled files
are now production dependencies. Retaining compatibility also retains the original
variant's resource-loading/baking cost; the 902-file total has not been disguised
as a package-size reduction. Runtime memory, chunk rebuilds and many-landmark frame
cost require external profiling.

Both approved `.bbmodel` files, original source hash, atlas/review artifacts,
`mushroom_bells_art.py`, generators and exact-check pipelines are unchanged.

## Exact changes relative to the inspected local tree

Modified existing local files (some were already untracked or modified before this task):

- `src/main/java/io/github/golovinss/dudunka/MushroomBellsStyledPreview.java`
- `src/main/java/io/github/golovinss/dudunka/ValeAdventurePiece.java`
- `src/main/java/io/github/golovinss/dudunka/ValeAdventureStructure.java`
- `src/main/java/io/github/golovinss/dudunka/ValeMusicQuest.java`
- `src/main/java/io/github/golovinss/dudunka/client/MushroomValeMusic.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValeAdventureTests.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValeFantasyVisualTests.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValeLandmarkFireflyTests.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValeMusicTests.java`
- `src/main/resources/assets/dudunka/lang/en_us.json`
- `src/main/resources/assets/dudunka/lang/ru_ru.json`
- `docs/MUSHROOM-BELLS-ART-STYLE.md`

Added by this task:

- `src/main/java/io/github/golovinss/dudunka/test/MushroomBellsProductionTests.java`
- `docs/VALIDATION-MUSHROOM-BELLS-PRODUCTION.md` (this report)

The revision-2 crown test is explicitly pinned to its saved revision so its existing
geometry/light assertions remain intact. The old interaction fixture still defaults
to revision 2 and reconstructs its requested revision from NBT; old tests continue
to exercise old layouts. Native tests now expect revision 3 for newly generated
Bells and verify its exact physical root rather than a historical block type.
No assertions or timeouts were weakened.

## External automated verification — NOT RUN

Run with JDK 17, separately from an active runClient:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
DUDUNKA_TEST_MUSIC=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
DUDUNKA_TEST_LANDMARKS=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
```

Optional exact reproduction checks (also **NOT RUN** by Codex):

```bash
python3 tools/generate_mushroom_bells_preview.py --check
python3 tools/generate_mushroom_bells_preview.py --styled --check
python3 tools/test_mushroom_bells_converter.py
python3 tools/test_mushroom_bells_style.py
```

The music namespace adds three production tests (all four saved revisions, both
clues, note identities/private audio/progress persistence, decorative vs required
integrity/ducking, FULL-chunk protection, exact scene/light reuse, grade/bounds/NBT)
and one full styled-firefly test reusing the existing gate/veto/reload/departure
assertions. Its synthetic high-altitude scene supplies and restores natural ground
explicitly, since production no longer repaints level ground.

The landmark namespace has **eight required tests** in ValeAdventureTests. Existing
physical locate, native signed boundaries/neighbors-first, whole-chunk compressed
save/reload, injected obstruction/cache invalidation and old-empty-site cases remain.
The added eighth case injects water into predicted natural support after CARVERS;
normal placement must preserve the water, save an invalid start, reject interaction,
clear the presence cache and locate another intact scene. Existing assertions check
the complete blueprint at public locate results. No executed PASS is claimed here.

## Manual Mushroom Vale / BMC4 acceptance — NOT RUN

Use a disposable world or backup copy, structures enabled, Forge 47.4.13 / Minecraft
1.20.1 / Java 17; repeat in Better MC BMC4 v55.5. Restart after installing the build.
Use the normal Vale gateway; existing developer entry is optional with its existing
`-PmushroomValeDev=true` opt-in. Do not use `/place structure` to create a quest site.

1. Explore new territory in the glowing mushroom forest and run
   `/locate structure dudunka:music_bells`. Record seed and returned X/Z. Travel in
   Spectator above the destination, then descend to its actual surface. The locate
   click preserves your Y; choose a safe height. With developer commands enabled,
   `/dudunka_dev mushroom_vale inspect_music X Z` reports saved blocks/bounds without
   loading or repairing another chunk. Confirm a complete revision-3 scene.
2. Compare with the accepted terrain preview at day and midnight, shaders off first:
   rose broad/scalloped, teal pointed, amber squat; one stone/crystal; recessed carving,
   ring, gills/clappers, moss, accents and the four light pools. Walk all approaches,
   including the low amber bell, and right-click each stem/clapper. Check the unchanged
   collision/selection approximations for comfortable access. Inspect a level site
   and a shallow grade for floating stems, buried paths, abrupt edges or tree intrusion.
3. Right-click both the carved stone body and high crystal: each replays the same
   three-note clue. Check rose/violet wording in English/Russian. Complete turquoise
   → rose-violet → golden; replay, make a mistake, retry and check existing rewards
   and later prerequisites. With a second player, verify notes/progress remain private.
4. At night with doMobSpawning and animal spawning enabled, remain within 24 blocks:
   existing assistance should supply up to three fireflies. Check no accumulation,
   daylight behavior and departure. Approach during the naturally scheduled Vale
   theme: its existing 16/20-block ducking/fade should work for the new root.
5. Cross neighboring chunk edges, including negative coordinates; F3+T, unload/revisit,
   save and fully restart. Check geometry/light persistence, valid locate coordinates,
   completed progress and ducking recovery. Capture missing texture/material errors.
6. In a copy of an old world, revisit real revision-0/1/2 Bells. Both old clue targets,
   all bells, saved progress, ducking and firefly assistance must remain usable. Record
   before/after blocks: no automatic retrofit should occur. Locate in new territory
   should find revision 3 while intact old sites can still be returned nearby.
7. In a disposable save, remove one required bell/crown/root cell: hints/firefly
   assistance should fail closed and exact locate should skip the damaged site without
   repairing it or changing completed progress. Losing a small plant/path/ring ornament
   should preserve gameplay. For natural placement rejections, capture the bounded
   `Vale landmark rejected:` reason and confirm the saved start is invalid after restart.

Stop after these Bells checks. Other music landmark redesigns, family teleport changes,
publication, version changes and retirement of registered preview assets are separate work.
