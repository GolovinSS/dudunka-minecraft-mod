package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A private, replayable reward. Native blocks, entity AI and the ambient soundtrack stay read-only. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeConcertQuest {
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private static final float[] PITCHES = {.75F, 1F, 1.25F};
    private static final int[] DANCE = {0, 0, 1, 2, 2, 3};
    private static final class Session { BlockPos noticed, playing; int elapsed; }
    private ValeConcertQuest() {}
    private static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos) {
        return ValeMusicQuest.pieceAt(level, pos, ValeAdventureStructure.Landmark.SINGING_MUSHROOM);
    }
    private static boolean intact(ServerLevel level, ValeAdventurePiece piece) {
        for (var entry : piece.plan().entrySet()) {
            var pos = entry.getKey();
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || !level.getBlockState(pos).equals(entry.getValue())) return false;
        }
        return true;
    }
    /** Only the original central stone activates; seats, stem and decorative copies cannot. */
    public static ValeAdventurePiece resolve(ServerLevel level, BlockPos pos) {
        var piece = pieceAt(level, pos);
        return piece != null && pos.equals(piece.anchor()) && intact(level, piece) ? piece : null;
    }
    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.server, server -> new HashMap<>())
                .computeIfAbsent(p.getUUID(), id -> new Session());
    }
    private static void message(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.dudunka.concert." + key), false);
    }
    private static void note(ServerPlayer p, BlockPos root, int tone, int voice) {
        note(p, root, tone, voice, 1);
    }
    private static void note(ServerPlayer p, BlockPos root, int tone, int voice, int simultaneousVoices) {
        var timbre = switch (voice) {
            case 0 -> ValeQuestAudio.Voice.BELLS;
            case 1 -> ValeQuestAudio.Voice.DANCE;
            case 2 -> ValeQuestAudio.Voice.MEMORY;
            case 3 -> ValeQuestAudio.Voice.HARP;
            default -> throw new IllegalArgumentException("Unknown concert voice");
        };
        ValeQuestAudio.play(p, timbre, PITCHES[tone], simultaneousVoices);
        double x = root.getX() + .5 + (voice - 1.5) * 1.4;
        p.serverLevel().sendParticles(p, ParticleTypes.NOTE, false,
                x, root.getY() + 2, root.getZ() - 1, 0, (tone * 4 + voice) / 24.0, 0, 0, 1);
        p.serverLevel().sendParticles(p, voice == 2 || voice == 3 ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
                false, x, root.getY() + 1.5, root.getZ() - 1, 4, .25, .3, .25, .005);
    }
    private static void chord(ServerPlayer p, BlockPos root) {
        for (int tone : new int[]{0, 2, 1}) note(p, root, tone, 3, 3);
    }
    private static void applause(ServerPlayer p, BlockPos root) {
        // Optional private sparkles on existing friends. No emotes, navigation, ownership or home edits.
        var friends = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(root).inflate(12), e -> e.isAlive() &&
                        (e instanceof Companion c && p.getUUID().equals(c.ownerId())
                        || e instanceof Pufik || e instanceof WiseFlyAgaric || e instanceof FluffyJumper
                        || e instanceof ValeFirefly || e instanceof ValeSpider));
        if (!friends.isEmpty()) message(p, "nearby");
        for (var friend : friends.stream().limit(24).toList())
            p.serverLevel().sendParticles(p, ParticleTypes.HAPPY_VILLAGER, false,
                    friend.getX(), friend.getY() + .8, friend.getZ(), 4, .2, .2, .2, 0);
    }
    /** One server-owned score: four introductions, then interwoven reprise and final chord. */
    private static void perform(ServerPlayer p, Session s) {
        int t = s.elapsed; var root = s.playing;
        if (t == 0) message(p, "bells");
        if (t == 80) message(p, "dance");
        if (t == 180) message(p, "memory");
        if (t == 280) message(p, "harp");
        if (t == 360) message(p, "together");
        if (t == 0 || t == 12 || t == 24) note(p, root, t / 12, 0);
        if (t >= 80 && t <= 140 && (t - 80) % 12 == 0) {
            int flower = DANCE[(t - 80) / 12]; note(p, root, flower % 3, 1);
        }
        if (t == 180 || t == 200 || t == 220) note(p, root, (t - 180) / 20, 2);
        if (t == 280) chord(p, root);
        if (t == 360 || t == 372 || t == 384) note(p, root, (t - 360) / 12, 0);
        if (t == 396 || t == 408) { note(p, root, 2, 1, 2); note(p, root, 1, 2, 2); }
        if (t == 420) chord(p, root);
        if (t == 440) {
            var result = ValeMusicProgress.get(p.server).finishConcert(p.getUUID());
            message(p, result == ValeMusicProgress.ConcertResult.COMPLETED ? "complete" : "encore");
            p.serverLevel().sendParticles(p, ParticleTypes.NOTE, false,
                    root.getX() + .5, root.getY() + 3, root.getZ() + .5, 12, 2, .6, 2, 0);
            applause(p, root); s.playing = null;
        }
    }
    public static boolean interact(ServerPlayer p, BlockPos pos) {
        if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 64) return false;
        var piece = resolve(p.serverLevel(), pos);
        if (piece == null) return false;
        var data = ValeMusicProgress.get(p.server); var s = session(p); s.noticed = piece.anchor();
        var gate = data.concertPrerequisite(p.getUUID());
        if (gate != ValeMusicProgress.ConcertResult.READY) {
            message(p, gate.name().toLowerCase(Locale.ROOT)); return true;
        }
        if (s.playing != null) return true; // Repeated clicks do not restart, stack, or skip the score.
        message(p, data.concertCompleted(p.getUUID()) ? "replay" : "start");
        s.playing = piece.anchor(); s.elapsed = 0; perform(p, s); return true;
    }
    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p && interact(p, event.getPos())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!p.isAlive() || p.isSpectator() || !p.serverLevel().dimension().equals(MushroomVale.LEVEL)) { clear(p); return; }
        var s = session(p);
        if (s.playing != null) {
            if (p.blockPosition().distSqr(s.playing) > 256 || resolve(p.serverLevel(), s.playing) == null) {
                s.playing = null; message(p, "cancelled");
            } else { ++s.elapsed; perform(p, s); }
            return;
        }
        if (p.tickCount % 40 != 0) return;
        ValeAdventurePiece nearby = null; var pos = p.blockPosition();
        for (int dx = -1; dx <= 1 && nearby == null; dx++) for (int dz = -1; dz <= 1 && nearby == null; dz++) {
            var probe = new BlockPos(((pos.getX() >> 4) + dx)*16+8, pos.getY(), ((pos.getZ() >> 4) + dz)*16+8);
            var piece = pieceAt(p.serverLevel(), probe);
            if (piece != null && pos.distSqr(piece.anchor()) <= 144 && intact(p.serverLevel(), piece)) nearby = piece;
        }
        if (nearby == null) s.noticed = null;
        else if (!nearby.anchor().equals(s.noticed)) { s.noticed = nearby.anchor(); message(p, "welcome"); }
    }
    private static void clear(ServerPlayer p) {
        var sessions = SESSIONS.get(p.server); if (sessions != null) sessions.remove(p.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }
}
