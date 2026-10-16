# Stage 4.3a.1 — Fantasy Visual Refinement

Targets: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / BMC4 v55.5.
Local uncommitted Stage 4.1–4.3a work is retained. Stage 4.3b is not started.

| Status | Result |
| --- | --- |
| Implementation | Implemented, first two landmarks only |
| Static checks | `git diff --check` and syntax parsing of 18 touched JSON resources passed |
| Build / resource verifier / focused and full GameTests | **NOT RUN** |
| Day/night Minecraft / BMC4 / saved-world acceptance | **NOT RUN** |
| Commit / push / PR / publication | **NOT DONE** |

The user reports the preceding build, focused/full GameTests, quest mechanics,
audio ducking and persistence passed. The preceding visual acceptance failed.
Those results do not validate this refinement.

## Implementation and compatibility

- New pieces save `VisualRevision: 2`. Mushroom Bells uses thin modeled stems
  and three compact, stepped octagonal domes with hanging gills, fullbright
  undersides and droplets. Turquoise/golden crown blocks are at Y+2, violet at
  Y+3, rather than Y+4/+6/+5. Distinct color, crown height/profile and musical
  identities remain. Crown outlines include the overhang; narrow collision
  preserves access. Moss, miniature luminous mushrooms, small crystals and
  occasional client-side end-rod spores enrich the clearing.
- Both original carved stones still give the SAME listening clue. The original
  center and the crystal at Z−3 remain at their original coordinates, connected
  by a mossy stone ground inlay into one listening alcove. Neither is removed.
  The three original low-stem click positions (Y+1) remain. Old upper crown
  coordinates remain valid in their old layouts; new crowns are intentionally lower.
- Dance uses separate sloped petals, raised tips, leaves and exposed central
  lantern volumes. Forge 47 face data supplies fullbright centers without shaders;
  real block light 10 illuminates the ground. This is conventional white Minecraft
  block light, not colored dynamic light. It does not require bloom or shaders.
  Models use existing textures; ordinary landmark mushroom assets are unchanged.
- Four posts, including buried extensions on slopes, are removed from the new
  blueprint in favor of mushroom/crystal clusters. Moss/calcite/rooted-dirt ground
  inlays and four low quartz petals frame the unchanged central clue block.
  Every ring flower position, insertion order/voice and six-tap rule is preserved.
- Additive `turquoise_music_bell`, `violet_music_bell`, `golden_music_bell` blocks
  each have two static segment states, no item/drop, block entity or server block
  ticker. `tools/generate_music_visuals.py` regenerates their native JSON geometry
  and the flower models. No new textures/dependencies/mixins/renderers.
- New bell bounds are Y−3…Y+5, dance Y−3…Y+3, X/Z±6. Model overhangs and the
  allowed two-block ground slope fit these conservative bounds and the owning
  chunk. Native height preflight uses the corresponding 6/4-block clearance;
  the other three keep 12. Deserialized pieces retain their saved bounds, revision and blueprint.
- Revisions 0 (absent tag) and 1 retain their original layouts. Existing revision-1
  flower blocks receive the richer shared models/light, but no blocks are moved,
  replaced or retrofitted. Candidate starts also keep their stored revision.
- Integrity checks for the first two landmarks require the real clue and musical
  targets, rather than fragile decorative moss/plants/crystals/inlays. Audio hints
  use the same required subset. Exact target-state checks and native-start binding
  remain. The other three landmarks keep their original full integrity checks.
- Structure IDs, progress ledger, prerequisites/retries/replay, version, protocol,
  audio and ducking remain unchanged. Existing completed quests stay complete.

## Landmark fireflies

`ValeLandmarkFireflies` scans only already-loaded starts near living,
non-spectator players, every 100 ticks, and skips assistance when the server has
no spare tick time. Only the first two intact, generated music sites qualify;
quest completion is not a prerequisite for ambience. No cache, tickets, forced
chunks or distant ticking is introduced.

At evening/night (12000–22999), with `doMobSpawning` and server animal spawning
enabled, assistance tops up to **three** existing `ValeFirefly` entities within
16 blocks. All flies count, including natural, named and reloaded entities; it
never culls an existing population. Nearby overlapping sites share this budget.
Players must be within 24 blocks, and candidate cells must be entity-ticking,
loaded, dry, collision-free and inside world/build bounds, near real ground.
There are at most 12 placement attempts per site/pass. Existing mob caps do not
control this small assistance budget. Forge spawn-position, finalization and
entity-join vetoes remain effective. Assisted entities use spawn reason `EVENT`.
Colors cycle through the existing three variants. No persistence flag is set;
existing flight bounds, NBT and natural despawn with Forge vetoes apply. On player
departure replenishment stops; unloaded entities use ordinary chunk persistence,
and are counted on return instead of accumulating replacements.

## External verification — NOT RUN

With JDK 17 and no other running Gradle/client process:

```bash
./tools/test.sh build
DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
./tools/test.sh gametest
```

The build includes `verifyValeFantasyVisuals` (model limits, exposed fullbright
parts, sloped geometry and texture references). New focused GameTests cover all
three saved revisions, original interaction coordinates, six taps after losing
decorations, compact crowns, flower block light, and real firefly assistance at
both sites: player/time/gamerule gates, Forge vetoes, three variants, repeated
passes, NBT reload, pre-existing population, departure/despawn and no forced chunks.
Existing landmark tests cover terrain bounds and native start/chunk persistence.
Tests are authored, **NOT RUN**; no automated PASS is claimed.

## Day/night visual acceptance checklist — NOT RUN

Use genuinely new chunks for revision 2, plus backed-up existing sites for 0/1.

- **Day:** inspect both from eye level, all sides and above. Bells should read as
  cozy mushrooms with curved tiered crowns, slender stems and open hanging gills,
  rather than broad roofs. Both clue stones should read as one listening alcove.
  Flower petals/leaves should have individual silhouettes and visible volume.
  Check varied terrain for floating/buried details and unobstructed approaches.
- **Night, shaders OFF:** flower lantern centers and bell undersides stay visibly
  luminous; nearby grass/moss visibly receives block light. Check no opaque dark
  centers or excessive washed-out clearing. Inspect again with reduced particles;
  the landmark's identity must remain readable without spores or shader bloom.
- **Ambience:** at 18000 with mob spawning enabled, stand within 24 blocks of each
  site for 5–10 seconds. Expect about 2–4 real flies (assistance target 3), familiar
  colors and bounded gentle flight. Remain for several passes, add another player,
  leave beyond 48 blocks and return; no growing population. Repeat with spawning
  disabled, in daylight, spectator mode and near simulation/chunk boundaries.
- **Interaction:** click both bell clues and all original low stems; complete
  turquoise/violet/golden. Play the dance north/north/east/south/south/west;
  retry/replay and test another player. Remove a decorative plant/inlay: play still
  works. Remove a true target: that target must not impersonate an intact one.
- **Compatibility:** visit old generated layouts, save/restart, revisit completed
  quests and compare geometry/progress. Check the other three sites, audio ducking,
  vanilla and BMC4 v55.5, missing-model logs, light comfort and client performance.

Stop at Stage 4.3a.1; graphical acceptance is required before approval.
