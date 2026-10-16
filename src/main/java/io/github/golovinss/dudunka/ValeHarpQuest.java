package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Three independently tuned voices; all blocks, inhabitants and ambient music stay read-only. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeHarpQuest {
    private static final Map<MinecraftServer, Map<UUID, BlockPos>> NOTICED = new WeakHashMap<>();
    private static final int[] COLUMNS = {-2, 0, 2};
    private static final float[] PITCHES = {.75F, 1F, 1.25F};
    public record Target(ValeAdventurePiece piece, int string) {} // -1 plays the combined harmony.
    private ValeHarpQuest() {}
    private static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos) {
        return ValeMusicQuest.pieceAt(level, pos, ValeAdventureStructure.Landmark.SILVER_WEB_HARP);
    }
    private static boolean intact(ServerLevel level, ValeAdventurePiece piece) {
        for (var entry : piece.plan().entrySet()) {
            var pos = entry.getKey();
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
            var actual = level.getBlockState(pos);
            // Pane connections can change through ordinary neighbor updates; material and dryness cannot.
            if (entry.getValue().is(Blocks.WHITE_STAINED_GLASS_PANE)) {
                if (!actual.is(Blocks.WHITE_STAINED_GLASS_PANE)
                        || actual.getValue(BlockStateProperties.WATERLOGGED)) return false;
            } else if (!actual.equals(entry.getValue())) return false;
        }
        return true;
    }
    public static Target resolve(ServerLevel level, BlockPos pos) {
        var piece = pieceAt(level, pos);
        if (piece == null || !intact(level, piece)) return null;
        var root = piece.anchor();
        if (pos.equals(root)) return new Target(piece, -1);
        if (pos.getZ() != root.getZ() || pos.getY() <= root.getY()
                || pos.getY() >= root.getY() + 7 || !piece.plan().containsKey(pos)) return null;
        for (int string = 0; string < 3; string++)
            if (pos.getX() == root.getX() + COLUMNS[string]) return new Target(piece, string);
        return null;
    }
    private static Component text(String key) { return Component.translatable("message.dudunka.harp." + key); }
    private static void message(ServerPlayer p, String key) { p.displayClientMessage(text(key), false); }
    private static Map<UUID, BlockPos> noticed(ServerPlayer p) {
        return NOTICED.computeIfAbsent(p.server, server -> new HashMap<>());
    }
    private static void voice(ServerPlayer p, BlockPos root, int string, int tone, int simultaneousVoices) {
        ValeQuestAudio.play(p, ValeQuestAudio.Voice.HARP, PITCHES[tone], simultaneousVoices);
        // One, two or three vertically separated knots, also described explicitly in text.
        for (int dot = 0; dot <= tone; dot++)
            p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, false,
                    root.getX() + COLUMNS[string] + .5, root.getY() + 1.3 + dot * .6,
                    root.getZ() - .25, 1, 0, 0, 0, 0);
    }
    private static void status(ServerPlayer p, BlockPos root, int string) {
        int tone = ValeMusicProgress.get(p.server).harpTone(p.getUUID(), root, string);
        p.displayClientMessage(Component.translatable("message.dudunka.harp.status",
                text("string." + string), text("tone." + tone)), false);
    }
    private static void spiders(ServerPlayer p, BlockPos root) {
        // Optional private applause at real peaceful spiders, without changing their AI or emotes.
        var spiders = p.serverLevel().getEntitiesOfClass(ValeSpider.class, new AABB(root).inflate(8), s -> s.isAlive());
        if (!spiders.isEmpty()) message(p, "spiders");
        for (var spider : spiders)
            p.serverLevel().sendParticles(p, ParticleTypes.HAPPY_VILLAGER, false,
                    spider.getX(), spider.getY() + .7, spider.getZ(), 5, .15, .15, .15, 0);
    }
    public static boolean interact(ServerPlayer p, BlockPos pos) {
        if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return false;
        var target = resolve(p.serverLevel(), pos);
        if (target == null) return false;
        var root = target.piece.anchor(); var data = ValeMusicProgress.get(p.server);
        if (!root.equals(noticed(p).put(p.getUUID(), root))) message(p, "welcome");
        var gate = data.harpPrerequisite(p.getUUID());
        if (gate != ValeMusicProgress.HarpResult.TUNED) {
            message(p, gate.name().toLowerCase(Locale.ROOT)); return true;
        }
        if (target.string >= 0) {
            data.tuneHarp(p.getUUID(), root, target.string);
            voice(p, root, target.string, data.harpTone(p.getUUID(), root, target.string), 1);
            status(p, root, target.string);
        } else {
            message(p, "clue");
            for (int string = 0; string < 3; string++) {
                voice(p, root, string, data.harpTone(p.getUUID(), root, string), 3);
                status(p, root, string);
            }
            var result = data.playHarp(p.getUUID(), root);
            message(p, result.name().toLowerCase(Locale.ROOT));
            if (result == ValeMusicProgress.HarpResult.COMPLETED || result == ValeMusicProgress.HarpResult.ALREADY_COMPLETE) {
                p.serverLevel().sendParticles(p, ParticleTypes.HAPPY_VILLAGER, false,
                        root.getX()+.5, root.getY()+3, root.getZ()+.5, 24, 2, 1, .2, 0);
                spiders(p, root);
            }
        }
        return true;
    }
    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p && interact(p,event.getPos())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!p.isAlive() || p.isSpectator() || !p.serverLevel().dimension().equals(MushroomVale.LEVEL)) { clear(p); return; }
        if (p.tickCount % 40 != 0) return;
        ValeAdventurePiece nearby = null; var pos = p.blockPosition();
        for (int dx=-1; dx<=1 && nearby==null; dx++) for (int dz=-1; dz<=1 && nearby==null; dz++) {
            var probe = new BlockPos(((pos.getX()>>4)+dx)*16+8,pos.getY(),((pos.getZ()>>4)+dz)*16+8);
            var piece = pieceAt(p.serverLevel(), probe);
            if (piece != null && pos.distSqr(piece.anchor())<=144 && intact(p.serverLevel(),piece)) nearby=piece;
        }
        if (nearby == null) noticed(p).remove(p.getUUID());
        else if (!nearby.anchor().equals(noticed(p).put(p.getUUID(),nearby.anchor()))) message(p,"welcome");
    }
    private static void clear(ServerPlayer p) {
        var noticed = NOTICED.get(p.server); if (noticed != null) noticed.remove(p.getUUID());
        ValeMusicProgress.get(p.server).resetHarpAttempt(p.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }
}
