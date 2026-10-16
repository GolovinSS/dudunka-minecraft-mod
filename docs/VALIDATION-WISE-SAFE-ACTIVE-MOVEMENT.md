# Wise Fly Agaric active movement fixture investigation

Source run: `20261014T005737Z-159683`, seed `-255494441983840131`.
Implementation/static review: complete. Build/GameTests/client validation of this
correction: **NOT RUN**. Nothing committed, pushed or published.

## Confirmed evidence and limits

- The full run reports exactly one required failure, `safeactivemovement`, with
  only the combined message `Active AI remains on safe dry island`.
- Read-only inspection of the saved Overworld Anvil data locates its structure
  block at **(640, -60, 0)**. `GameTestHelper.absolutePos` makes the island center
  **(643, -56, 3)**. The old fixture replaces only X=638..648, Y=-58..-54,
  Z=-2..8; its top and sides are open to the surrounding world.
- The saved natural aquifer has source water (`level=0`) at **(644, -35, 3)**
  and falling water (`level=8`) down to **(644, -53, 3)**, immediately above the
  old fixture. A further falling-water column at X=647, Z=3 extends through the
  fixture's elevation. These are saved block states, not generated/simulated
  during investigation.
- Local Minecraft 1.20.1 `StructureUtils.clearSpaceForStructure` clears above
  the six-by-five-by-six empty template only through Y=-36, leaving the aquifer
  at Y=-35 exposed. The test's own AIR writes supply no water/falling-block seal.
  **The environmental isolation defect is confirmed.**
- The old `@AfterBatch` discards the NPC and restores the captured block states
  on failure before the final world is saved. Its failure book also contains
  only the combined assertion. Therefore the historical `inWaterOrBubble`,
  `onFire`, and `belowSafeY` flags, position, velocity and path are **not
  recoverable**. Aquifer ingress is the evidence-backed explanation; its exact
  effect on that NPC still requires the external rerun. No production navigation
  defect has been established.

`LocalStrollGoal` validates destinations and every path node with
`Pufik.safeGround`, then rechecks the next node during movement. Negative danger
pathfinding maluses and the existing FloatGoal are unchanged. A validated path
does not isolate an NPC from subsequently arriving water or falling terrain.

The batch runner uses grouped test collections without a stable spatial
assignment contract. Changing the suite can move this test into different
natural terrain. The new audio/hint tests in `isolated_music_interaction` execute
after this failure in this run. Music landmark fixtures use the Vale dimension;
the optional proximity handler processes ServerPlayers and does not move this
Overworld NPC. There is no evidence of a direct audio/network cause.

## Narrow correction

Only `WiseFlyAgaricTests.safeActiveMovement` changes:

- Capture/restore a 13x7x13 region and add a non-falling stone perimeter, roof and
  base. The roof excludes aquifer/falling-block ingress. The outer wall is beyond
  the original island edge, and the base preserves a two-block drop: falling
  off still violates the unchanged minimum Y.
- Keep the same center, nine-by-nine two-layer mycelium island, north water row,
  active AI, random seed 340, 800-tick observation, 850-tick timeout and movement
  distance requirement. All three original safety predicates remain enforced.
- On failure, report each violated predicate, test/entity ticks, center/minimum
  Y, current/previous position, velocity, bounding box, ground/alive/removed
  status, next path node, and nearby block/fluid states. Use a positional GameTest
  failure so the report also identifies the failing location.

No production, music, network, registry, version or gameplay files are changed by
this correction. All earlier working-tree changes are preserved.

## Developer validation

Repeat the full suite on the same seed; clear focused-scope overrides:

```bash
env -u DUDUNKA_TEST_MUSIC -u DUDUNKA_TEST_LANDMARKS -u DUDUNKA_TEST_JOURNEY \
  DUDUNKA_TEST_SEED=-255494441983840131 ./tools/test.sh gametest
```

Confirm `safeactivemovement` passes both its per-tick safety and actual-movement
checks and that the full required-test summary passes. If it still fails, share
the new `violated=...` diagnostic and run ID before changing production AI.
This command has **NOT RUN** in Codex.
