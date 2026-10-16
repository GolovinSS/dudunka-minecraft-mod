package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A quiet, private observation puzzle bound to the saved native Memory Stone. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeMemoryQuest {
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private static final int[][] CORNER_LIGHTS = {{-4,-3},{-4,3},{4,-3},{4,3}};
    private static final class Session { BlockPos noticed, playback; int elapsed; }
    public record Target(ValeAdventurePiece piece, int symbol) {} // -1 starts listening.
    private ValeMemoryQuest() {}

    private static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos) {
        return ValeMusicQuest.pieceAt(level, pos, ValeAdventureStructure.Landmark.MEMORY_STONE);
    }

    /** Any damaged blueprint piece disables the site; this path never loads or repairs blocks. */
    private static boolean intact(ServerLevel level, ValeAdventurePiece piece) {
        for (var entry : piece.plan().entrySet())
            if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) return false;
        return true;
    }

    public static Target resolve(ServerLevel level, BlockPos pos) {
        var piece = pieceAt(level, pos);
        if (piece == null || !intact(level, piece)) return null;
        var root = piece.anchor();
        if (pos.equals(root.above())) return new Target(piece, -1); // Exposed bottom-center crying obsidian.
        if (pos.equals(root.offset(0,6,0))) return new Target(piece, 0); // One bright moment.
        if (pos.equals(root.offset(0,3,-1)) || pos.equals(root.offset(0,3,1))) return new Target(piece, 1); // Paired crystals.
        for (var offset : CORNER_LIGHTS)
            if (pos.equals(root.offset(offset[0],1,offset[1]))) return new Target(piece, 2); // Little lights together.
        return null;
    }

    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.server, server -> new HashMap<>())
                .computeIfAbsent(p.getUUID(), id -> new Session());
    }
    private static void message(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.dudunka.memory." + key), false);
    }
    private static void glow(ServerPlayer p, BlockPos pos, int count) {
        p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, false, pos.getX()+.5, pos.getY()+.8, pos.getZ()+.5,
                count, .18, .22, .18, .005);
        p.serverLevel().sendParticles(p, ParticleTypes.HAPPY_VILLAGER, false, pos.getX()+.5, pos.getY()+.8, pos.getZ()+.5,
                Math.max(2,count/2), .22, .2, .22, .005);
    }
    private static void chime(ServerPlayer p, float pitch) {
        ValeQuestAudio.play(p, ValeQuestAudio.Voice.MEMORY, pitch, 1);
    }
    private static BlockPos symbolPosition(BlockPos root, int symbol) {
        return switch (symbol) {
            case 0 -> root.offset(0,6,0);
            case 1 -> root.offset(0,3,-1);
            default -> root.offset(-4,1,-3);
        };
    }

    public static boolean interact(ServerPlayer p, BlockPos pos) {
        if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return false;
        var target = resolve(p.serverLevel(), pos);
        if (target == null) return false;
        var root = target.piece.anchor();
        var data = ValeMusicProgress.get(p.server);
        var s = session(p); s.noticed = root; s.playback = null;
        if (target.symbol < 0) {
            data.listenToMemory(p.getUUID(), root);
            s.playback = root; s.elapsed = 0;
            message(p, "listen");
            chime(p, .8F); glow(p, root.offset(0,6,0), 8);
            return true;
        }
        var result = data.interpretMemory(p.getUUID(), root, target.symbol);
        chime(p, target.symbol == 2 ? 1.1F : .75F);
        glow(p, pos, result == ValeMusicProgress.MemoryResult.COMPLETED ? 18 : 5);
        switch (result) {
            case RETRY -> message(p, "hint");
            case COMPLETED -> { message(p, "complete"); glow(p, root.offset(0,6,0), 18); }
            case ALREADY_COMPLETE -> message(p, "again");
            case NEED_FIRST_NOTE -> message(p, "need_first_note");
            case NEED_DANCE -> message(p, "need_dance");
            case LISTEN_FIRST -> message(p, "listen_first");
        }
        return true;
    }

    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()==InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p && interact(p,event.getPos())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if(!p.isAlive() || p.isSpectator() || !p.serverLevel().dimension().equals(MushroomVale.LEVEL)) { clear(p); return; }
        var s=session(p);
        if(s.playback!=null) {
            var piece=pieceAt(p.serverLevel(),s.playback);
            if(p.blockPosition().distSqr(s.playback)>256 || piece==null || !intact(p.serverLevel(),piece)) s.playback=null;
            else if(++s.elapsed==20 || s.elapsed==40 || s.elapsed==60) {
                int beat=s.elapsed/20;
                var pos=switch(beat) { case 1 -> s.playback.offset(0,3,-1); case 2 -> s.playback.offset(-4,1,-3); default -> s.playback.offset(0,6,0); };
                message(p,"beat."+beat); chime(p,.8F+beat*.12F); glow(p,pos,8);
                if(beat==3) s.playback=null;
            }
        }
        if(p.tickCount%40!=0) return;
        ValeAdventurePiece nearby=null; var pos=p.blockPosition();
        for(int dx=-1;dx<=1 && nearby==null;dx++) for(int dz=-1;dz<=1 && nearby==null;dz++) {
            var probe=new BlockPos(((pos.getX()>>4)+dx)*16+8,pos.getY(),((pos.getZ()>>4)+dz)*16+8);
            var piece=pieceAt(p.serverLevel(),probe);
            if(piece!=null && pos.distSqr(piece.anchor())<=144) nearby=piece;
        }
        if(nearby==null || pos.distSqr(nearby.anchor())>256) {
            s.noticed=null; ValeMusicProgress.get(p.server).resetMemoryListening(p.getUUID());
        } else if(!nearby.anchor().equals(s.noticed)) { s.noticed=nearby.anchor(); message(p,"welcome"); }
    }
    private static void clear(ServerPlayer p) {
        var sessions=SESSIONS.get(p.server); if(sessions!=null) sessions.remove(p.getUUID());
        ValeMusicProgress.get(p.server).resetMemoryListening(p.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) clear(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) clear(p);
    }
}
