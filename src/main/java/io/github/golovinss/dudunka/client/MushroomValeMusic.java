package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.MushroomVale;
import io.github.golovinss.dudunka.MushroomBellsStyledPreview;
import io.github.golovinss.dudunka.ValeMusicDucking;
import io.github.golovinss.dudunka.ValeMusicHints;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vale-only scheduling through vanilla's single MusicManager slot, including Creative. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID, value=Dist.CLIENT)
public final class MushroomValeMusic {
    private static final Schedule SCHEDULE = new Schedule();
    private static final ValeMusicDucking DUCKING = new ValeMusicDucking();
    private static final ValeMusicHints HINTS = new ValeMusicHints();
    private static Object previousLevel, previousConnection;
    private static SoundInstance adjustedSound;
    private static long hintClock;

    /** Invoked on the client thread by the optional, direction-checked audio channel. */
    public static void hint(Connection connection, ValeMusicHints.Snapshot snapshot) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getConnection() == null
                || !client.level.dimension().equals(MushroomVale.LEVEL)) return;
        HINTS.accept(connection, client.getConnection().getConnection(), client.level,
                client.level.dimension().location().toString(), snapshot, hintClock);
    }

    private static float duckingGain(Minecraft client, boolean inVale, boolean suspended) {
        var connection = client.getConnection() == null ? null : client.getConnection().getConnection();
        if (previousLevel != client.level || previousConnection != connection) {
            // A replacement ClientLevel cannot inherit a latch.
            DUCKING.tick(false, false, Double.POSITIVE_INFINITY);
            previousLevel = client.level;
            previousConnection = connection;
        }
        if (!client.isPaused()) ++hintClock;
        if (!inVale || connection == null) {
            HINTS.reset();
            return DUCKING.tick(false, false, Double.POSITIVE_INFINITY);
        }
        var player = client.player;
        double distance = HINTS.nearestDistanceSquared(connection, client.level,
                client.level.dimension().location().toString(), hintClock,
                player.getX(), player.getY(), player.getZ(), anchor -> {
                    var chunk = client.level.getChunkSource().getChunk(anchor.x() >> 4, anchor.z() >> 4,
                            ChunkStatus.FULL, false);
                    return chunk != null && MushroomBellsStyledPreview.musicAnchor(
                            chunk.getBlockState(new BlockPos(anchor.x(), anchor.y(), anchor.z())));
                });
        return DUCKING.tick(true, suspended, distance);
    }

    private static void applyGain(Minecraft client, float gain) {
        // Three explicit 1.20.1 access-transformer fields; no reflection or sound replacement.
        var sound = client.getMusicManager().currentMusic;
        if (sound == null || sound.getSource() != SoundSource.MUSIC
                || !sound.getLocation().equals(DudunkaMod.MUSHROOM_VALE_THEME.get().getLocation())) {
            adjustedSound = null;
            return;
        }
        // Outside a fade, leave vanilla alone after restoring a sound we actually adjusted.
        if (gain == 1 && adjustedSound != sound) return;
        var handle = client.getSoundManager().soundEngine.instanceToChannel.get(sound);
        if (handle == null || handle.isStopped()) return;
        float volume = ValeMusicDucking.channelVolume(sound.getVolume(),
                client.options.getSoundSourceVolume(SoundSource.MUSIC), gain);
        // ChannelHandle guards release/reuse and dispatches to vanilla's sound executor.
        handle.execute(channel -> channel.setVolume(volume));
        adjustedSound = gain == 1 ? null : sound;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HINTS.reset();
        DUCKING.tick(false, false, Double.POSITIVE_INFINITY);
        previousLevel = null;
        previousConnection = null;
        applyGain(Minecraft.getInstance(), 1);
        adjustedSound = null;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        var manager = client.getMusicManager();
        var music = new Music(Holder.direct(DudunkaMod.MUSHROOM_VALE_THEME.get()),1200,3600,true);
        boolean inVale = client.player != null && client.level != null && client.level.dimension().equals(MushroomVale.LEVEL);
        boolean suspended = client.isPaused() || client.screen != null && client.screen.getBackgroundMusic() != null;
        float gain = duckingGain(client, inVale, suspended);
        var action = SCHEDULE.tick(inVale, suspended, manager.isPlayingMusic(music),
                () -> 1200 + client.level.random.nextInt(2401));
        if(action == Action.STOP) manager.stopPlaying(music);
        else if(action == Action.START) {
            manager.stopPlaying();
            manager.startPlaying(music);
        }
        applyGain(client, gain);
    }

    public enum Action { NONE, START, STOP }

    /** Pure scheduling policy, also exercised without a graphical/audio client. */
    public static final class Schedule {
        private boolean wasInVale;
        private int delay = -1;
        public Action tick(boolean inVale, boolean suspended, boolean playing, IntSupplier quietGap) {
            if(!inVale) {
                boolean leaving = wasInVale;
                wasInVale = false;
                delay = -1;
                return leaving ? Action.STOP : Action.NONE;
            }
            if(!wasInVale) {
                wasInVale = true;
                delay = 0;
            }
            if(suspended) return Action.NONE;
            if(playing) {
                delay = -1;
                return Action.NONE;
            }
            if(delay < 0) delay = quietGap.getAsInt();
            if(delay-- > 0) return Action.NONE;
            delay = -1;
            return Action.START;
        }
    }
    private MushroomValeMusic() {}
}
