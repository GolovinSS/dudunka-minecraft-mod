# Stage 4.3a.3 — Dudunka Minecraft fantasy art and optimization

Production integration now follows in [Stage 4.3a.4](VALIDATION-MUSHROOM-BELLS-PRODUCTION.md).
The prototype scope and historical validation statements below describe Stage 4.3a.3.

**Implemented:** a separate styled, terrain-integrated developer prototype. The
approved original, the existing OBJ preview, production generation, all quest
interactions/progress, audio/ducking, fireflies and family teleportation are retained.
This stage does not connect the new centerpiece to the production listening action.

## Signature art direction

Minecraft-native fantasy: expressive silhouettes, deliberate pixel clusters,
restrained magical highlights and a cozy, walkable composition. Geometry adds
character rather than substituting for texture painting.

- Keep the rose broad/scalloped, teal pointed/umbrella and amber squat/lobed. The
  styled cap contours differ from approved bounds by less than .12 world blocks.
  Small horizontal ledges and one-pixel contour snapping introduce stepped details;
  curved stems and hanging undersides remain recognizable.
- Use approximately 16 visible texels per world block where practical. Cap art is
  64×64 per mushroom; smaller surfaces use 32×32 tiles, with UV density bounded for
  tall/narrow components. Use limited related palettes, hard pixel edges and sparse
  clusters rather than blurry ramps, photographic grain or per-pixel noise.
- Rose/teal/amber caps have their own clustered spots and stitched edging. Stems
  have warm grain and a knot; gills have cream filaments; clappers have gold bands.
  Stone has chiseling, cracks and moss, with the actual recessed diamond retained.
  Crystals use contrasting hard facet bands and small highlights. Moss and leaves
  use clustered greens, veins, soil shadows and occasional light tips.
- Keep the carved stone as the one central focus. Preserve the broken musical ring,
  the three tone markers, tiny mushrooms, plant clusters, moss and crystals. There
  are no second clue pillars, extra entities or particle emitters.

The handcrafted pixel patterns and geometry edits are explicit deterministic code
in `tools/mushroom_bells_art.py`. The art atlas is one **256×128 RGBA** PNG; all
regions align with the mip grid and UVs stay inside padded tiles. Generation and the
software art review use nearest-neighbor sampling only. `atlas.png.mcmeta` requests
no blur. Normal Minecraft atlas sampling/mipmap and third-party shader settings can
still affect distant appearance; the generator adds no linear-resampling step.

Review artifacts:

- `docs/blockbench/mushroom_bells_clearing_styled.bbmodel`: separately named copy,
  embedded atlas, real per-face UVs, original group organization and source UUIDs.
- `docs/blockbench/mushroom_bells_style_atlas.png`: nearest-neighbor enlarged atlas.
- `docs/blockbench/mushroom_bells_styled_art_preview.png`: optional software art
  review, **not a Minecraft screenshot or acceptance result**. Its finite ground
  plane is for orientation and is not a shipped platform model.

The original `docs/blockbench/mushroom_bells_clearing.bbmodel` remains byte-for-byte
unchanged: SHA256 `6bb7545660bdbb9090624adedc4ea9997ee8e649331fb6405300657875e50551`.
It contains 164 meshes, 23 groups, 18 solid-color source textures. The styled copy
retains **162 meshes and all 23 groups**; only the two terrain-disk meshes are omitted.
Open the separate copy in Blockbench for inspection. Manual edits to that copy must
be reconciled with the art recipe before regeneration; the writer refuses to
overwrite a manually changed styled copy whose hash differs from its last manifest.

## Actual rendering pipeline and measured optimization

Forge 47.4.13's built-in OBJ loader remains the rendering path. Inspection of its
locally cached `ObjModel`, `ObjLoader` and `BlockGeometryBakingContext` established:

- Every OBJ face, including a triangle, becomes a four-vertex `BakedQuad`.
- OBJ models are cached by resource/settings. The baseline loaded 347 separate OBJ
  resources; the styled variant loads one shared OBJ with cell-local groups.
- [Inherited part visibility](https://docs.minecraftforge.net/en/1.20.1/rendering/modelextensions/visibility/)
  lets a base JSON hide all groups and each small child JSON enable exactly one
  cell. Each block still bakes only its own geometry; there is no oversized anchor
  model, dynamic renderer or out-of-cell geometry. See also
  [Forge OBJ loading](https://docs.minecraftforge.net/en/1.20.1/rendering/modelloaders/).
- All these block textures already use Minecraft's block atlas. Consolidating the
  art into one atlas/three lighting materials reduces resource/parser overhead;
  it is **not** a claim of reducing 18 draw calls to one. Vertex/face reduction is
  the main rendering improvement. No FPS or GPU timing has been measured here.

Measured from generated resources, per active clearing variant:

| Metric | Working baseline | Styled terrain variant | Reduction |
|---|---:|---:|---:|
| Custom mesh cells | 347 | 180 | 48.1% |
| Baked faces / quads | 30,394 | 5,556 | 81.7% |
| Baked vertex slots (4/face) | 121,576 | 22,224 | 81.7% |
| Runtime resource files | 715 | 187 | 73.8% |
| OBJ files | 347 | 1 | 99.7% |
| Material definitions | 18 | 3 | 83.3% |
| Texture images | 18 | 1 | 94.4% |
| Uncompressed runtime resource bytes | 4,780,980 | 1,770,179 | 63.0% |

The terrain variant also places seven native moss carpets: **187 placed blocks**
including the 180 mesh cells. The supporting Minecraft ground already exists and
is not part of the generated assembly. Maximum styled face count per cell is 130.

What changed, with geometry retained where it matters:

1. Omit only the earth/moss terrain disk: it accounted for **6,240 baseline baked
   faces**. Existing grass/dirt/moss supplies the floor.
2. Repair winding by shared-edge adjacency and signed volume for 58 closed meshes.
   Closed solids no longer emit unnecessary reversed faces. Open leaves, gill blades,
   carving panels, ring and ribbons remain two-sided; they are not blindly culled.
3. Replace the main cap rings with 12/16-sided stepped contours at the approved
   scale. Keep gill contours while replacing sub-pixel tube thickness with two-sided
   blades. Reduce the lining from 48 to 24 perimeter samples, preserving its 12 folds.
4. Remove hidden lip disks and 213 buried/flush downward input faces. Replace the
   mostly buried crown-tip shard with a small visible surface highlight. Preserve
   the true recessed carving and all named decorative objects.
5. Keep 1,112 planar convex input quads as quads; triangulate concave/nonplanar faces
   when required. Clip positions **and UVs together**. Convex clipped polygons are
   partitioned into quads where safe, rather than expanding every face into a
   triangle fan. Visible surface-area conservation is checked after intended edits.
6. Use shared OBJ geometry plus inherited cell visibility, one art atlas and three
   light-response materials. Geometry stays inside each cell, including negative
   coordinates and neighboring chunks.

These are per-variant costs, not a claim that the whole mod became smaller. To keep
existing saved preview states/commands and all pre-existing uncommitted resources
intact, **the legacy variant is also retained**. Both variants together currently
package **902 runtime resources / 6,551,159 uncompressed bytes**, and legacy models
still contribute to initial resource baking/memory. An old placed preview retains
its old rendering cost. Retiring that compatibility preview requires a separate
decision before production adoption; this stage does not disguise that overhead.
The new atlas intentionally spends more texels on art than the original flat PNGs.

## Light response and collision

The shared MTL has three responses, all sampling the same atlas:

| Material | Forge emissive-ambient input | Use |
|---|---|---|
| `surface` | `Ka 0 0 0` | Caps, stems, stone, ordinary crystal faces, plants and moss |
| `warm` | `Ka .4 .4 .4` | Small gold clappers, fine musical inlay and tone dots |
| `glint` | `Ka .8 .8 .8` | Crown highlight, recessed luminous inset and original static motes |

These `Ka` values make selected faces self-lit (approximately lightmap levels 6 and
12 in this Forge loader); **they do not illuminate neighboring blocks**. Exactly
four existing mesh cells emit real Minecraft block light: **13** at the crown base
and **10** at each bell clapper. A bright cap spot or crystal pixel does not create
world lighting. Ordinary vanilla lighting works without shaders; colored glow,
refraction, bloom and shadows depend on client shaders and are not promised.

The lighting polish changes only these four existing emitters. Offsets below are
relative to the terrain preview origin, in X/Y/Z order:

| Anchor | Mesh cell / part | Offset | Old → new block light |
|---|---|---|---|
| Crystal crown base | 86 | 0, 1, 0 | 11 → 13 |
| Rose clapper | 33 | −3, 1, −1 | 8 → 10 |
| Teal clapper | 121 | 2, 1, −2 | 8 → 10 |
| Amber clapper | 124 | 2, 1, 2 | 8 → 10 |

No extra musical-ring emitter is added: the stronger centerpiece should help its
surroundings without increasing the emitting-cell count. Expect slightly wider,
brighter pools with some overlap in this compact clearing; actual coverage depends
on nearby blocks. Textures, emissive materials, geometry and collision are unchanged.
Real block light remains uncolored and may brighten nearby surfaces during the day.
No particles, ticking renderers or new light-emitting cells are introduced.

Restart the development client/server to load the updated common-side layout;
texture reload alone is insufficient. Place a fresh terrain preview for acceptance.
With shaders disabled, set midnight and check the crystal, carved recesses, ring
and all three clappers from ground level. Confirm the light pools retain surrounding
shadows rather than washing out the clearing. Set day and check that the palettes
still feel natural, then revisit after a chunk reload to check lighting stability.
Minecraft visual acceptance and Gradle/GameTests for this polish are **NOT RUN**.

There are no ticking blocks, block entities, new entities or particles. Collision
uses conservative component bounds for the stone and three stems, clipped to each
cell. Caps, crystal tips, gill blades and small decorations remain passable in this
prototype. Path stones are low visual overlays; moss carpets have ordinary native
carpet collision. Broad cell-local selection boxes are not final per-face hitboxes.
Flying/jumping inside a passable closed cap can expose intentionally culled backs;
final production collision should prevent that. Check overhead clearance and ring
readability in Minecraft before accepting them.

## Terrain preview and commands

The styled scene uses the original `.375 blocks/source unit` scale, with source
Y=.7 aligned to the top of the existing ground. Its visible bounds are about
**10.24 × 8.37 × 4.05 world blocks**, plus the natural ground around it. Render-cell
envelope is X −5…5, Y 0…4, Z −4…4 relative to origin: **11×9×5** cells.

The new developer command requires an already loaded, dry, **level** 11×9 patch of
sturdy natural dirt/grass/moss at origin Y−1 and clear air above it. It reads the
whole volume, including gaps; rejects trees, plants, builds, fluids, block entities,
living occupants, borders and unavailable chunks. It does not clear or grade a site.
It adds only matching mesh cells and moss carpets. Cleanup removes those additions
only, rejects changed parts/gaps, and preserves every original ground block.

No automatic terrain fitting is claimed. Production should use the existing saved
13×13 ground profile, preflight object bases and neighbor boundaries, allow a bounded
gentle grade, keep the musical ring walkable, feather moss/path treatment on existing
natural surfaces, and reject unsuitable sites rather than excavating. That placement
design, final collision and native quest binding remain outside this stage.

Start the development client externally with JDK 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline -PmushroomValeDev=true runClient
```

In a disposable Creative world with cheats, stand on a clear level natural patch.
The target must be beyond the player's occupied volume. For example:

```mcfunction
/dudunka_dev mushroom_vale bells_preview terrain ~ ~ ~10
/time set day
/time set midnight
```

Use **the first air-block Y above the ground**, not an assumed Y=64. Default modern
superflat ground is often near Y=−61. The command prints the exact origin; record it
and, after moving outside the clearing, clean up with those exact coordinates:

```mcfunction
/dudunka_dev mushroom_vale bells_preview clear_terrain X Y Z
```

The original `bells_preview place X Y Z` / `clear X Y Z` remain unchanged for the old
airborne comparison fixture. All routes require the existing JVM developer opt-in,
permission 2 and a living non-spectator player. Neither variant creates a native
landmark or grants quest progress. No production generator/revision was replaced.
The new single centerpiece's eventual listening action still follows the mapping
described in `BLOCKBENCH-MUSHROOM-BELLS-PROTOTYPE.md`.

## Reproduction and external verification

```bash
python3 tools/generate_mushroom_bells_preview.py --styled
python3 tools/generate_mushroom_bells_preview.py --styled --check
python3 tools/generate_mushroom_bells_preview.py --check
python3 tools/test_mushroom_bells_style.py
python3 tools/preview_mushroom_bells_styled.py # optional software art review
git diff --check

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
DUDUNKA_TEST_MUSIC=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
```

`build/check` includes the additive `verifyMushroomBellsStyle` Java resource task.
New GameTests are in `dudunka_music`, covering additive registration, cell/shape
bounds, sparse world light, old command availability, loaded-neighbor placement,
authorization/occupants/obstructions, untouched ground, safe cleanup and unchanged
quest progress/forced tickets. Existing quest/old-layout tests are unchanged.

Static converter tests and exact styled/legacy resource comparison: **PASS**.
Gradle, Java verifier, GameTests, Minecraft/BMC4 daytime/nighttime and performance
acceptance: **NOT RUN**. The software image was inspected only as an art review.
No commit, push, publication, version bump or production replacement occurred.

Concise manual acceptance, with shaders **off** first:

- **Day:** all three cap silhouettes/colors read at ground level; spots/trim/stone
  remain crisp; the recess has depth; stems, gills and clappers connect visibly.
- **Walking:** no floating disk, altered floor or awkward invisible walls; paths
  and ring remain readable; low amber parts and carpet edges feel comfortable.
- **Night:** crown/inset and gold accents remain visible; nearby grass/stone gets
  actual light; caps/plants still have shadows; no blanket fullbright or particles.
- **Reload/boundaries:** F3+T, save/reload and cross a chunk edge; no missing purple
  textures, vanished cells, inverted faces, material errors or whole-scene duplication.
- **Safety/comparison:** old `place/clear` still work, new `clear_terrain` leaves
  ground untouched, and a changed player block makes cleanup refuse. Review in BMC4
  and profile multiple styled clearings before accepting a recurring landmark budget.

## Change inventory

Modified existing files: `build.gradle`, `DudunkaMod.java` (one registration call),
`MushroomBellsPreview.java` (two additive command children),
`tools/generate_mushroom_bells_preview.py` (optional styled dispatch), and the
prototype document (link to this stage). Existing asset bytes and source are retained.

Added handwritten files: `tools/mushroom_bells_art.py`,
`tools/generate_mushroom_bells_styled.py`, `tools/preview_mushroom_bells_styled.py`,
`tools/test_mushroom_bells_style.py`, `MushroomBellsStyledPreview.java`,
`test/MushroomBellsStyleTests.java`, `src/test/java/CheckMushroomBellsStyle.java`, and
this document. The new layout manifest at
`src/main/resources/data/dudunka/mushroom_bells_styled/layout.json` lists every
generated path (187 runtime resources plus the separate source copy and atlas review).
The optional software-art PNG is the remaining review artifact.
