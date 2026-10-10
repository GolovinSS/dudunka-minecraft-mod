# Mushroom Vale Stage 3.1 — Pufik validation

Date: 2026-10-10. Base Git HEAD: `0750a5e` (`feat: refine Mushroom Vale vegetation and lighting balance`); the working tree was clean before implementation. Changes remain uncommitted. Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, Better MC BMC4 v55.5.

## Implementation and compatibility

- Added `dudunka:pufik` through the existing entity DeferredRegister, after existing family registrations; a dedicated `Pufik` class, renderer, model, 128×128 texture, two locale entries and ground spawn placement. `Kind`, `Companion`, family ownership/growth/album/carrier systems and album protocol 9 are unchanged.
- The glowing mushroom forest creature list adds weight 5, groups 1–2. Its mooshroom entry remains. The predicate checks Vale dimension, forest biome, dry sturdy floor, two-block clearance, hazards, adjacent fluid/fire and cliff edges. Wander samples at most eight targets within five blocks and checks loaded chunks first. No target or attack goal is registered. Unnamed Pufiks use ordinary creature distance despawning; named Pufiks resist distance despawning.
- The model has 43 baked parts and a 13×11.49-pixel (0.72-block tall) broad silhouette with a stepped red cap, raised cream spots, cream gills/body, eyes, cheeks, smile, arms and tiny feet. Four procedural states cover idle, walking, happy and shy. No GeckoLib, mixins, packets or new dependencies were added.
- Existing entity and worldgen resource keys, dimension/biome IDs, save formats, UUID handling and network protocol remain unchanged. The new entry is additive. `/summon dudunka:pufik ~ ~ ~` is supported for inspection.

## Automated results

All final commands used `/usr/lib/jvm/java-17-openjdk-amd64` and cached Forge/Minecraft artifacts (`--offline`).

| Check | Result |
| --- | --- |
| `./gradlew build --offline` | PASS, including `test`, `verifyValeMusic`, JAR and reobfuscation |
| `./gradlew verifyModels --offline` | PASS, all nine existing family model layers |
| `./gradlew verifyPufik --offline` | PASS: 43 parts, 960 rendered vertices, UV coordinates in bounds, 128×128 PNG, EN/RU name and greeting keys |
| `./gradlew runGameTestServer --offline` in fresh `run/pufik-stage31-final2` directory | PASS, **169/169 required GameTests**, including all three new Pufik GameTests |
| `git diff --check` | PASS |

The Pufik GameTests cover entity ID/category/size/attributes and family identity separation; creature-list weight and group size; Forge spawn placement; positive forest spawning; Overworld and other Vale biome rejection; water and cliff rejection; lack of hostile target/attack attribute; interaction/emote; ordinary despawning, named persistence, save/load UUID/name/health and absence of companion progression tags. The model check validates resource dimensions and geometry without starting a graphical client.

The first full run used the old `run/world` test world and found a mistaken coordinate in the new spawn fixture plus an unrelated finished-surface failure on saved Vale chunks. The fixture was corrected to select its actual generated biome, and later full runs used unused test directories. A second fresh run found only the fixture mismatch; the final fresh runs passed 169/169. The final result above was obtained after the last source and test edits.

To reproduce the fresh world requirement, set the Gradle JVM or `JAVA_HOME` to Java 17 and use a Gradle init script that changes `runGameTestServer`'s `runConfig.workingDirectory` to a new unused directory, guarded by a missing `world/level.dat` check. The final local script is at ignored `build/reports/pufik-stage31/final2.gradle`; its contents are:

```groovy
gradle.taskGraph.whenReady { graph ->
 def task = graph.allTasks.find { it.name == 'runGameTestServer' }
 task.doFirst {
  def dir = task.project.file('run/pufik-stage31-final2')
  assert !new File(dir, 'world/level.dat').exists() : 'Requires unused test directory'
  task.runConfig.get().workingDirectory(dir)
 }
}
```

Choose a new directory before repeating. A saved Stage 1/2 or earlier test world can retain old terrain or generated chunks, so it is unsuitable for fresh biome/worldgen assertions.

## Manual acceptance pending

No graphical `runClient`, full BMC4, shader/resource-pack, multiplayer or old-save upgrade acceptance was performed in this automated session. The owner should assess cap silhouette/texture, animation timing, walking near hazards, right-click greeting and natural spawn balance in a fresh Vale forest using the exact [IntelliJ and command steps](TESTING.md#stage-31-pufik). Automated checks do not establish visual quality or BMC4 behavior.

## Changed files

- Entity and client: `Pufik.java`, `PufikModel.java`, `PufikRenderer.java`, `DudunkaMod.java`, `ClientEvents.java`.
- Assets and data: `textures/entity/pufik.png`, both `lang` JSON files, and `worldgen/biome/glowing_mushroom_forest.json`.
- Validation: `PufikTests.java`, `CheckPufikModel.java`, `build.gradle`, this report, `MUSHROOM-VALE.md`, `TESTING.md`, `VALIDATION.md`, and the reference README.


## Visual polish follow-up

The follow-up pass rounds the cap profile with five receding canopy tiers, a
wide brim and visible cream gills. The torso now narrows at the shoulders and
feet, with enlarged brown-rimmed eyes, layered pupil highlights, a clear smile,
rosy cheeks and forward-set brown toes. Cap surfaces and the body/face/limbs use
separate shaded pixel-art atlas regions. Idle, walking, happy and shy animation
code and pivots were preserved; only the cap's rest pivot moved to keep the
canopy joined to the smaller shoulders.

After this visual-only change, `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew build verifyPufik --offline` passed. The updated resource check baked
43 parts / 960 vertices, confirmed a 13×11.49-pixel silhouette, in-bounds UVs,
the 128×128 texture and both locale keys. The 169/169 GameTest result above is
from the Stage 3.1 implementation run; the visual-only follow-up did not change
entity behavior, registration, spawning or interaction. Graphical inspection in
Minecraft and BMC4 remains pending as described below.

## Proportion polish follow-up

The next requested visual pass narrows the canopy from 13 to 12.1 pixels across
(about 7%), raises its pivot by 0.4 pixel, and extends the upper shoulder
silhouette by 0.5 pixel. The face keeps its existing eye, glint, cheek and smile
geometry; the smaller brim and higher front edge leave more of it visible. The
full baked silhouette is now 12.1×11.89 pixels, within the same approximate
Minecraft scale. The texture atlas and all UV offsets remain unchanged; the
focused bake confirms the resized cap still samples in-bounds atlas coordinates.

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyPufik
--offline` passed after this change. The model check reports 43 parts, 960
vertices, 12.1×11.89-pixel dimensions, valid UVs and the 128×128 PNG. No entity,
AI, spawn, interaction or animation behavior changed. In-game front and side
views remain a manual acceptance check.

## Close-range face readability follow-up

The canopy diameter is now **11.01 pixels**, down from 12.1 pixels in the prior
pass: an 8.999% reduction across width and depth. All five cap tiers, raised
spots and cream gills were scaled around the same center; height and vertical
profile were preserved. The face parts now sit on their own small pivot. When a non-spectator player is within 3.5 blocks, the pivot adds a small
upward lift that fades smoothly to zero with distance. It also follows the
existing renderer pitch, eased by LookAtPlayerGoal/LookControl; upward gaze
contributes 35% of the look angle, clamped to 0.16 radians. Neutral pitch and
no nearby player return the face to rest. This is a client-side model pose; no
AI goal or timer was added.

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyPufik
--offline` passed. The focused check baked 44 parts / 960 vertices, confirmed the
11.01×11.89-pixel silhouette, valid UVs, the 128×128 texture, bounded look
pitch and a smooth proximity lift at 0, 1.75 and 3.5 blocks. In-game close-range inspection and BMC4 visual
acceptance remain manual.

## Close-range look-up correction

The close-range pose now drives the dedicated face pivot directly: at a player's
eye-level approach the face rotates upward by **0.25 radians** (about 14.3°),
with the existing eased look pitch still contributing naturally and the combined
result bounded to 0.30 radians. The smoothstep transition spans 2.75 blocks and
returns exactly to rest at its edge. This pivot contains the eyes, cheeks and
smile, so the rotation raises the whole expression instead of deforming its
geometry. The body rises by 0.12 model pixels at maximum proximity, and the
separate cap pivot tilts back by 0.06 radians to open the front brim slightly.
The cap dimensions and texture are unchanged. Shy cap tilt adds to the new
baseline, preserving that emote; walking and happy pose channels remain intact.

The focused model check now asserts the close face angle, midpoint and end of
the easing curve, and cap tilt endpoints. Minecraft front-angle inspection is
still needed to judge the visible expression from a normal player camera, and
walking, happy and shy transitions should be observed in-game.

## Look-up investigation and deterministic preview — 2026-10-11

The previous successful angle checks did not establish visibility. Cached
Forge/Minecraft sources and the renderer registrations confirm that
`PufikRenderer` bakes `PufikModel.LAYER`, `LivingEntityRenderer` calls
`setupAnim` before rendering, and `ModelPart` uses negative X rotation to lift
its forward (-Z) surface in downward-Y model coordinates. The pose was not
reset afterward. The existing nearest-player query works on the client and
already excludes spectators; it was not the primary failure.

The previous hierarchy was `root/body/face` plus a separate `root/cap`, with
no shared head. It rotated the raised facial details around their own surface
pivot while leaving their cream backing upright. The brim still occluded the
eyes, and tilted upper details could retreat into the backing. The old
smoothstep curve supplied only about 0.006 radians of proximity lift at 2.5
blocks and reached 0.25 only at zero distance. A baked-triangle ray test now
reproduces the old full-strength pose's occlusion from a standing camera one
block away; no AI/distance simulation is needed to reproduce it.

The corrected hierarchy is `root/head/body/face` and `root/head/cap`.
`head` pivots at (0, 20.5, 0) model pixels, near the stem base. Body/face rest
geometry is unchanged; cap's local pivot becomes (0, -3.9, 0), preserving its
original absolute position of (0, 16.6, 0). A full look-up rotates the shared
head **-0.25 radians**, carries the cream backing with the expression, and
raises the shared pivot **0.12 pixels**. The cap adds **-0.06 radians**, moves
**0.75 pixels back (+Z)** and **0.45 pixels up (-Y)** to clear the standing
camera sightline. These translations were needed because shared rotation
alone still occluded the eye centers in the new geometry check. Cap diameter,
all cube dimensions, UVs and the texture are unchanged.

Normal rendering reads interpolated client player-eye positions, applies full
proximity weight within 2.5 horizontal blocks, and smoothsteps to zero by 3.5
blocks. Close yaw is limited and smoothly interpolated; pitch and pose weight
use a four-tick exponential time constant stored separately for each Pufik in
a weak-key map. No AI goal, entity data, save data, network packet, registration,
spawn or interaction changed. Root bob/hop, leg steps, idle breathing and shy
body/cap overlays still compose with the head pose. All parts reset before
each pose, so a shared renderer cannot leak a pose into another Pufik.

`/dudunka_pufik_pose rest|look_up|off` is registered only on the client when
`-PmushroomValeDev=true` enables the existing developer property. The two fixed
modes bypass AI, proximity, idle, walking and emote inputs and use the same
pose function as live tracking. The fixture instructions are in `TESTING.md`.

The focused check now executes the actual command registration/handlers and
`setupAnim` preview branch, verifies developer gating and reset, reproduces
old/rest occlusion, and casts rays through the actual transformed baked
triangles. The look-up pose clears the centers and inset corners of both eye
front surfaces and the smile from a 1.62-block standing camera at 1, 1.5, 2,
2.5, 2.75 and 3 blocks, for both the full preview and the settled live pose. It also checks happy/shy composition, walking, atlas bounds,
PNG dimensions and locales. Graphical Minecraft/BMC4 acceptance is still
pending: test the fixed comparison, cap/stem attachment, approach/retreat
smoothness, and live emote transitions. The preview is not a claim of manual
visual acceptance; directly overhead views at very short distance can still
be occluded by the cap.

Final validation: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build
verifyPufik --offline` **PASS**. The model check reports 45 parts (one new empty
shared pivot), 960 vertices, unchanged 11.01×11.89-pixel resting dimensions,
valid 128×128 atlas/UVs/locales, fixed and settled-live camera clearance,
developer command gating, and animation composition/reset. Existing
ResourceLocation/Gradle deprecation warnings remain. Intermediate geometry
failures guided the cap translations and the final approach curve; the final
curve has full weight at 2.5 blocks, half weight at 3, and zero at 3.5, so the
pose does not fade before the requested inspection distance. No graphical
client or new GameTest server run is included in these results.
