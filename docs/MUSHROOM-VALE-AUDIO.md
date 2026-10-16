# Mushroom Vale quest audio presentation

Status: quest sound balance, optional native-landmark hints, scoped soundtrack
ducking and focused test coverage are **IMPLEMENTED**. Build, automated test
execution and runClient/BMC4 acceptance: **NOT RUN**. Publication: **NOT PERFORMED**.

## Quest cues

All notes still use `ServerPlayer.playNotifySound` exclusively for the listener,
with `SoundSource.BLOCKS`. Music and Master preferences continue to apply through
Minecraft normally. No dialogue, pitches, rhythms, progress, puzzles or rewards
change; gentle failure/success text and particles keep their existing behavior.

| Chapter | Vanilla timbre | Previous volume | New volume |
| --- | --- | --- | --- |
| Mushroom Bells | Note block harp (unchanged) | 0.45 | 0.9 |
| Dance of the Fluffy Jumpers | Note block harp (unchanged) | 0.4 | 0.9 |
| The Forest That Remembers | Amethyst chime → note block pling | 0.32 | 0.9 |
| Silver Web Harp | Note block harp (unchanged) | 0.32 per voice | 0.9 solo; 0.3 per chord voice |
| Song for All Friends | Harp; memory voice changes from amethyst chime to pling | 0.28 harp / 0.22 memory | 0.9 solo; 0.45 per duet voice; 0.3 per chord voice |

`ValeQuestAudio` shares the balance across demonstrations, interactions and the
finale. Simultaneous voices divide the 0.9 cue budget rather than each playing a
full-volume solo. No unrelated amethyst or environmental sound is replaced.

## Scoped soundtrack ducking

The previously defined pure policy is now connected to playback: **16-block
entry / 20-block exit**, **25% of normal effective volume**, **40 client ticks**
(about two seconds) for both fading down and restoring. Distance is three-
dimensional from the player to the native root block's center. Hysteresis keeps
boundary movement from repeatedly changing the target. Proximity alone ducks;
quest progress or an active demonstration is not required.

### Server-authoritative hints

The isolated optional `dudunka:vale_music_hints` channel, protocol `1`, has one
**PLAY_TO_CLIENT** message and no client-to-server messages. Missing channels are
accepted on either side, and the server checks channel presence before scanning
or sending. Existing `dudunka:album` protocol **10**, message IDs and codecs are
unchanged. The narrow network addition was explicitly authorized by the user.

`ValeMusicLandmarkHints` checks at most **25 already-loaded chunks** within two
chunks of each supported Vale player every **10 server ticks**. It uses
`getChunkNow`, saved valid starts for exactly the five native structure types,
matching `ValeAdventurePiece` kinds and roots, and full blueprint integrity.
Harp panes tolerate ordinary connections while rejecting waterlogging, matching
the Harp quest. Native starts whose placement was rejected, decorative copies,
damaged sites and unloaded chunks yield no hint. No starts are searched/generated
and no chunks, blocks, SavedData, tickets, quest state or entities are changed.

Only roots within **32 blocks** are sent, at most 125 per snapshot. Changed lists
are sent at the next check; unchanged nonempty lists refresh every **40 server
ticks**. An empty list is sent once when nearby landmarks disappear; empty areas
remain silent. State is per server/player and discarded on logout or dimension
change. Detection and hints do not reveal or change another player's progress.

### Client lifetime and gain

The handler runs on the client thread, checks the current network connection and
Vale dimension, and copies the bounded snapshot. Hints are bound to that
connection and the actual `ClientLevel` instance. Wrong-dimension/old-connection
packets are ignored; dimension changes, world replacement and disconnect reset
state. Hints expire after **60 active client ticks** without a refresh. The clock
freezes during a genuinely paused singleplayer game. Client-unloaded roots or a
missing central marker are ignored immediately using a non-loading chunk lookup.

The client measures distance every tick using only the small root list. Walking
or teleporting away immediately changes the fade target; teleporting to a new
landmark is recognized on the next server check. Missing or stale hints restore
normal gain rather than leaving the music ducked. Reversals start from the
current gain. Pauses/screens that suspend the existing music schedule also freeze
the gain transition. Leaving the Vale/disconnecting resets gain immediately;
existing scheduling stops the old Vale track and destination music stays vanilla.

Vanilla 1.20.1 exposes no public per-instance gain API. Three explicit access-
transformer field entries expose **only** `MusicManager.currentMusic`,
`SoundManager.soundEngine`, and `SoundEngine.instanceToChannel`; final modifiers
are retained. The client reads the current managed sound, requires the exact
`dudunka:music.mushroom_vale_theme` event and `MUSIC` category, then uses its
existing `ChannelHandle.execute` to set volume on the vanilla sound executor.
No raw channel is retained, so a released/reused source cannot be adjusted by an
old handle. Only the owned track is adjusted during ducking; its original level
is restored once, then normal gain updates are left to vanilla.

Effective channel gain is `clamp(sound volume * player Music volume, 0, 1) *
ducking gain`; vanilla's Master listener gain remains untouched. No sound instance
is replaced, and scheduling, quiet gaps, playback/stop tracking and streaming
remain unchanged. No Music/Master slider writes, global category changes,
reflection, mixins, dependencies, replacement scheduler or global audio event
interception are used. Puzzle effects retain the separate private `BLOCKS` path.

### Remaining limits

- Native discovery/new damage/removal can take up to 10 server ticks to reach a
  client. At low server TPS this takes longer; stale hints safely fade out rather
  than sticking. A loaded hint rejected during a world transition is recovered by
  the next refresh. Extreme stalls can cause a restore/re-duck cycle.
- A silent vanilla music interval remains silent: ducking does not start another
  track. Manual `/playsound` copies, jukeboxes and other mods' tracks are excluded,
  even if a diagnostic copy uses the same Vale event.
- The access transformer is deliberately specific to Minecraft 1.20.1; future
  Minecraft ports must recheck the three field mappings and gain API. Mods that
  replace MusicManager sound instances or independently alter the same track's
  channel need actual compatibility testing.
- No audio-device, graphical, real multiplayer or BMC4 behavior is claimed as
  verified. These checks remain external.

## Files changed for this audio task

- `src/main/java/io/github/golovinss/dudunka/ValeMusicQuest.java`
- `src/main/java/io/github/golovinss/dudunka/ValeDanceQuest.java`
- `src/main/java/io/github/golovinss/dudunka/ValeMemoryQuest.java`
- `src/main/java/io/github/golovinss/dudunka/ValeHarpQuest.java`
- `src/main/java/io/github/golovinss/dudunka/ValeConcertQuest.java`
- `src/main/java/io/github/golovinss/dudunka/ValeQuestAudio.java` (new)
- `src/main/java/io/github/golovinss/dudunka/ValeMusicDucking.java` (new, pure policy)
- `src/main/java/io/github/golovinss/dudunka/ValeMusicHints.java` (new)
- `src/main/java/io/github/golovinss/dudunka/ValeMusicLandmarkHints.java` (new)
- `src/main/java/io/github/golovinss/dudunka/ValeMusicNetwork.java` (new)
- `src/main/java/io/github/golovinss/dudunka/client/MushroomValeMusic.java`
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java` (channel registration)
- `src/main/resources/META-INF/accesstransformer.cfg` (new)
- `build.gradle` (enable the narrow access transformer)
- `src/main/java/io/github/golovinss/dudunka/test/ValeAudioTests.java` (new)
- `src/main/java/io/github/golovinss/dudunka/test/ValeMusicHintTests.java` (new)
- `src/test/java/CheckValeMusic.java`
- `docs/MUSHROOM-VALE-AUDIO.md` (this report)

Other pre-existing working-tree edits are preserved. Existing album networking,
registry IDs, save data, soundtrack scheduling policy, version and publication
state are unchanged by this audio task.

## External validation

Run with Java 17, separately from runClient:

```bash
./tools/test.sh build
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
```

`check` includes `verifyValeMusic`: deterministic scheduling, pure fade policy,
proximity geometry, publication cadence, stale hint leases, connection/dimension
binding, unloaded roots and resets (no graphical client or audio device).
`ValeMusicHintTests` covers bounded codec round-trips/malformed counts, intact vs
missing/damaged native starts across all five types, pane tolerance, unloaded
remote lookups and absent optional channels. `ValeAudioTests` inspects actual
private sound packets for all five chapters, categories, timbres, note counts and
solo/duet/chord volumes. Existing chapter tests retain progress, timing, privacy,
structure integrity and replay coverage. These tests are **NOT RUN** by Codex.

## Manual runClient acceptance

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true
```

Use a disposable or backed-up save. Enter the Vale through the ordinary gateway,
or the enabled operator command `/dudunka_dev mushroom_vale enter`. Locate the
five native structures with `/locate structure dudunka:<id>`: `music_bells`,
`jumper_dance`, `memory_stone`, `silver_web_harp`, `singing_mushroom`. Decorative
copies or `/place structure` without a saved native start do not establish quest
acceptance. Complete chapters in their existing order.

1. At normal Master, Music and Blocks settings, compare each demonstration and
   clicked note. Check distinct pitches, dance tap groups, Memory listening and
   choices, Harp tuning/retry/success chords and the complete finale/replay.
2. Confirm the memory voice is audible pling in both Chapter 3 and the finale;
   duets/chords should not produce a sudden loudness spike. Check gentle mistake
   feedback and rapid interactions without changing quest completion semantics.
3. With two clients, listen/start independently; another player's puzzle notes
   must stay private. Check normal mobs and environmental sounds alongside cues.
4. Lower Blocks and Master, including zero; cues must obey those preferences.
   Restore normal settings, save/reload and confirm chapter progress and the album.

5. Use the naturally scheduled Vale theme (entry starts it, then vanilla retains
   normal quiet gaps). Approach each intact native landmark from beyond 20 blocks:
   by about 16 blocks it should fade to one quarter over roughly two seconds.
   Walk between 16 and 20 blocks: the target should stay ducked. Move beyond 20:
   it should restore smoothly. Repeat while idle, demonstrating and replaying.
6. Teleport far away, to another landmark, out of the Vale and back. Disconnect,
   reconnect and reload the world while near a landmark. Check that old hints and
   gain never carry into another world/dimension/connection. Pause singleplayer
   for over three seconds and resume: there should be no stale-hint oscillation.
7. During a track, change Music and Master (including zero), then restore them.
   Verify preferences/sliders are unchanged by entering/leaving a landmark. Check
   normal mobs, ambience, puzzle notes and destination/menu music. A separate
   diagnostic `/playsound` copy should be unaffected; stop it before judging the
   naturally scheduled theme. Do not use category-wide stop commands as evidence
   of automatic ducking.
8. In a disposable save, remove the native root or another blueprint block; music
   should restore without repairs or progress mutation. Restore it and approach
   again. An identical decorative copy without its saved start should not duck.
9. With two real clients, keep one near a landmark and one far away. Only the near
   client's theme should duck. Repeat on a dedicated BMC4 v55.5 server and a copied
   old save. Where a matching album-protocol-10 older peer is available, confirm
   the absent optional audio channel still connects and ordinary cues/scheduling
   work, with no ducking for that unsupported peer.

Manual acceptance and publication remain separate from automated verification.
