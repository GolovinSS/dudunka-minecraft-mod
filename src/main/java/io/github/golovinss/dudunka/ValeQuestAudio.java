package io.github.golovinss.dudunka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Shared musical balance; never broadcasts a player's puzzle feedback. */
public final class ValeQuestAudio {
    public enum Voice { BELLS, DANCE, MEMORY, HARP }
    public static final float SOLO_VOLUME = .9F;

    public static void play(ServerPlayer player, Voice voice, float pitch, int simultaneousVoices) {
        if (simultaneousVoices < 1) throw new IllegalArgumentException("A cue needs at least one voice");
        player.playNotifySound(voice == Voice.MEMORY ? SoundEvents.NOTE_BLOCK_PLING.get()
                : SoundEvents.NOTE_BLOCK_HARP.get(), SoundSource.BLOCKS,
                SOLO_VOLUME / simultaneousVoices, pitch);
    }

    private ValeQuestAudio() {}
}
