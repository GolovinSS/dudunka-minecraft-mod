package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.MushroomVale;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vale-only scheduling through vanilla's single MusicManager slot, including Creative. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID, value=Dist.CLIENT)
public final class MushroomValeMusic {
    private static final Schedule SCHEDULE = new Schedule();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        var manager = client.getMusicManager();
        var music = new Music(Holder.direct(DudunkaMod.MUSHROOM_VALE_THEME.get()),1200,3600,true);
        boolean inVale = client.player != null && client.level != null && client.level.dimension().equals(MushroomVale.LEVEL);
        boolean suspended = client.isPaused() || client.screen != null && client.screen.getBackgroundMusic() != null;
        var action = SCHEDULE.tick(inVale, suspended, manager.isPlayingMusic(music),
                () -> 1200 + client.level.random.nextInt(2401));
        if(action == Action.STOP) manager.stopPlaying(music);
        else if(action == Action.START) {
            manager.stopPlaying();
            manager.startPlaying(music);
        }
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
