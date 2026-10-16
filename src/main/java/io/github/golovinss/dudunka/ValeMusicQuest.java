package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only native-piece binding, private feedback and disposable listening/attempt state. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeMusicQuest {
    private static final int[][] CENTERS = {{-3, 0, 4}, {0, 2, 6}, {3, -1, 5}};
    private static final String[] COLORS = {"turquoise", "violet", "golden"};
    private static final float[] PITCHES = {0.75F, 1.0F, 1.25F};
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private static final class Session {
        BlockPos noticed, playback;
        int elapsed;
    }
    public record Target(ValeAdventurePiece piece, int bell) {} // -1 is the listening marker.
    private ValeMusicQuest() {}

    /** Never asks StructureManager to search/generate starts, and never loads a chunk. */
    private static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos) {
        return pieceAt(level, pos, ValeAdventureStructure.Landmark.MUSIC_BELLS);
    }
    static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos, ValeAdventureStructure.Landmark landmark) {
        if (!level.dimension().equals(MushroomVale.LEVEL)) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, landmark.id));
        var start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) return null;
        for (var part : start.getPieces()) if (part instanceof ValeAdventurePiece piece
                && piece.landmark() == landmark
                && piece.getBoundingBox().isInside(pos)
                && (piece.styledBells() ? piece.anchorPresent(level) : level.getBlockState(piece.anchor()).is(Blocks.CHISELED_STONE_BRICKS))) return piece;
        return null;
    }
    public static Target resolve(ServerLevel level, BlockPos pos) {
        var piece = pieceAt(level, pos);
        if (piece == null) return null;
        var expected = piece.plan().get(pos);
        if (expected == null || !level.getBlockState(pos).equals(expected)) return null;
        if (piece.styledBells()) {
            int bell = MushroomBellsStyledPreview.interaction(expected);
            return bell == -2 ? null : new Target(piece, bell);
        }
        var root = piece.anchor();
        if (pos.equals(root) || (pos.getX() == root.getX() && pos.getZ() == root.getZ() - 3))
            return new Target(piece, -1);
        for (int bell = 0; bell < 3; bell++) {
            var c = CENTERS[bell];
            if (expected.is(MushroomValeLandmarks.MUSIC_BELLS.get(COLORS[bell]).get())
                    || expected.is(MushroomValeLandmarks.BLOCKS.get(COLORS[bell] + "_landmark_cap").get())
                    || (pos.getX() == root.getX() + c[0] && pos.getZ() == root.getZ() + c[1]
                        && (expected.is(Blocks.MUSHROOM_STEM) || expected.is(Blocks.SHROOMLIGHT))))
                return new Target(piece, bell);
        }
        return null;
    }
    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.server, server -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), id -> new Session());
    }
    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.dudunka.music." + key), false);
    }
    private static void note(ServerPlayer player, BlockPos root, int bell) {
        // Vanilla harp asset is already shipped with Minecraft. Packets go only to this listener.
        ValeQuestAudio.play(player, ValeQuestAudio.Voice.BELLS, PITCHES[bell], 1);
        var c = CENTERS[bell];
        var piece=pieceAt(player.serverLevel(),root);
        var position = piece != null ? piece.bellPosition(bell) : root.offset(c[0],c[2],c[1]);
        player.serverLevel().sendParticles(player, ParticleTypes.NOTE, false,
                position.getX() + .5, position.getY() + 1.5, position.getZ() + .5,
                1, bell / 12.0, 0, 0, 1);
    }
    private static void melody(ServerPlayer player, BlockPos root, boolean immediate) {
        var s = session(player); s.playback = root.immutable(); s.elapsed = immediate ? 0 : -12;
        if (immediate) note(player, root, 0);
    }
    public static boolean interact(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(pos.getX() + .5,
                pos.getY() + .5, pos.getZ() + .5) > 64) return false;
        var target = resolve(player.serverLevel(), pos);
        if (target == null) return false;
        var data = ValeMusicProgress.get(player.server);
        var s = session(player); s.noticed = target.piece.anchor(); s.playback = null;
        if (target.bell < 0) {
            data.resetAttempt(player.getUUID());
            message(player, target.piece.styledBells() ? "clue_styled" : "clue");
            melody(player, target.piece.anchor(), true);
        } else {
            note(player, target.piece.anchor(), target.bell);
            switch (data.strike(player.getUUID(), target.piece.anchor(), target.bell)) {
                case RETRY -> message(player, target.piece.styledBells() ? "retry_styled" : "retry");
                case CORRECT -> message(player, "correct");
                case COMPLETED -> { message(player, "complete"); melody(player, target.piece.anchor(), false); }
                case ALREADY_COMPLETE -> message(player, "again");
            }
        }
        return true;
    }
    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player
                && interact(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.isAlive() || player.isSpectator() || !player.serverLevel().dimension().equals(MushroomVale.LEVEL)) {
            clear(player); return;
        }
        var s = session(player);
        if (s.playback != null) {
            if (player.blockPosition().distSqr(s.playback) > 256 || pieceAt(player.serverLevel(), s.playback) == null)
                s.playback = null;
            else {
                if (++s.elapsed == 0) note(player, s.playback, 0);
                if (s.elapsed == 12) note(player, s.playback, 1);
                if (s.elapsed == 24) { note(player, s.playback, 2); s.playback = null; }
            }
        }
        if (player.tickCount % 40 != 0) return;
        // Inspect at most nine already-loaded chunks, including a listener just across a chunk edge.
        ValeAdventurePiece nearby = null;
        var pos = player.blockPosition();
        for (int dx = -1; dx <= 1 && nearby == null; dx++) for (int dz = -1; dz <= 1 && nearby == null; dz++) {
            var probe = new BlockPos(((pos.getX() >> 4) + dx) * 16 + 8, pos.getY(), ((pos.getZ() >> 4) + dz) * 16 + 8);
            var piece = pieceAt(player.serverLevel(), probe);
            if (piece != null && pos.distSqr(piece.anchor()) <= 144) nearby = piece;
        }
        if (nearby == null) {
            s.noticed = null;
            ValeMusicProgress.get(player.server).resetAttempt(player.getUUID());
        } else if (!nearby.anchor().equals(s.noticed)) {
            s.noticed = nearby.anchor(); message(player, "welcome");
        }
    }
    private static void clear(ServerPlayer player) {
        var sessions = SESSIONS.get(player.server);
        if (sessions != null) sessions.remove(player.getUUID());
        ValeMusicProgress.get(player.server).resetAttempt(player.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
