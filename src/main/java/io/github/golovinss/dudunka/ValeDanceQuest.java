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

/** Private, untimed tap groups on the existing flower ring. No entity or terrain mutations. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeDanceQuest {
    private static final int[][] FLOWERS = {{0,-5},{5,0},{0,5},{-5,0}};
    private static final int[] PATTERN = {0,0,1,2,2,3};
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();
    private static final class Session { BlockPos noticed, playback; int elapsed; }
    public record Target(ValeAdventurePiece piece, int flower, int voice) {}
    private ValeDanceQuest() {}
    private static ValeAdventurePiece pieceAt(ServerLevel level, BlockPos pos) {
        return ValeMusicQuest.pieceAt(level, pos, ValeAdventureStructure.Landmark.JUMPER_DANCE);
    }
    private static boolean flower(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.ALLIUM) || state.is(Blocks.PINK_TULIP) || state.is(MushroomValeLandmarks.DANCE_FLOWER.get());
    }
    public static Target resolve(ServerLevel level, BlockPos pos) {
        var piece = pieceAt(level, pos);
        if (piece == null) return null;
        var expected = piece.plan().get(pos);
        if (expected == null || !level.getBlockState(pos).equals(expected)) return null;
        var root = piece.anchor();
        if (pos.getX() == root.getX() && pos.getZ() == root.getZ()) return new Target(piece,-1,-1);
        if (!flower(expected)) return null;
        int flower = 4;
        for (int i=0;i<FLOWERS.length;i++)
            if (pos.getX()==root.getX()+FLOWERS[i][0] && pos.getZ()==root.getZ()+FLOWERS[i][1]) flower=i;
        int voice=0;
        for (var entry : piece.plan().entrySet()) {
            if (!flower(entry.getValue())) continue;
            if (entry.getKey().equals(pos)) return new Target(piece,flower,voice);
            voice++;
        }
        return null;
    }
    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.server, server->new HashMap<>()).computeIfAbsent(p.getUUID(),id->new Session());
    }
    private static void message(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.dudunka.dance."+key),false);
    }
    private static void note(ServerPlayer p, BlockPos pos, int voice) {
        ValeQuestAudio.play(p, ValeQuestAudio.Voice.DANCE, .55F+voice*.025F, 1);
        p.serverLevel().sendParticles(p,ParticleTypes.NOTE,false,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,1,voice/48.0,0,0,1);
        p.serverLevel().sendParticles(p,ParticleTypes.HAPPY_VILLAGER,false,pos.getX()+.5,pos.getY()+.7,pos.getZ()+.5,6,.2,.25,.2,0);
    }
    private static void beat(ServerPlayer p, BlockPos root, int step) {
        var piece=pieceAt(p.serverLevel(),root);
        if(piece==null) return;
        int flower=PATTERN[step];
        for(var pos:piece.plan().keySet()) {
            if(pos.getX()!=root.getX()+FLOWERS[flower][0] || pos.getZ()!=root.getZ()+FLOWERS[flower][1]) continue;
            var target=resolve(p.serverLevel(),pos);
            if(target!=null && target.flower==flower) { note(p,pos,target.voice); message(p,"beat."+flower); return; }
        }
    }
    public static boolean interact(ServerPlayer p, BlockPos pos) {
        if(!p.isAlive() || p.isSpectator() || p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return false;
        var target=resolve(p.serverLevel(),pos);
        if(target==null) return false;
        var data=ValeMusicProgress.get(p.server); var s=session(p);
        s.noticed=target.piece.anchor(); s.playback=null;
        if(target.flower<0) {
            data.resetDanceAttempt(p.getUUID()); message(p,"clue");
            if(!data.completed(p.getUUID())) message(p,"need_first_note");
            s.playback=target.piece.anchor(); s.elapsed=0; beat(p,s.playback,0);
        } else {
            note(p,pos,target.voice);
            message(p,data.dance(p.getUUID(),target.piece.anchor(),target.flower).name().toLowerCase(Locale.ROOT));
        }
        return true;
    }
    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()==InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p && interact(p,event.getPos())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if(!p.isAlive() || p.isSpectator() || !p.serverLevel().dimension().equals(MushroomVale.LEVEL)) { clear(p); return; }
        var s=session(p);
        if(s.playback!=null) {
            if(p.blockPosition().distSqr(s.playback)>256 || pieceAt(p.serverLevel(),s.playback)==null) s.playback=null;
            else if(++s.elapsed%20==0) {
                beat(p,s.playback,s.elapsed/20);
                if(s.elapsed==100) s.playback=null;
            }
        }
        if(p.tickCount%40!=0) return;
        ValeAdventurePiece nearby=null; var pos=p.blockPosition();
        for(int dx=-1;dx<=1 && nearby==null;dx++) for(int dz=-1;dz<=1 && nearby==null;dz++) {
            var probe=new BlockPos(((pos.getX()>>4)+dx)*16+8,pos.getY(),((pos.getZ()>>4)+dz)*16+8);
            var piece=pieceAt(p.serverLevel(),probe);
            if(piece!=null && pos.distSqr(piece.anchor())<=144) nearby=piece;
        }
        if(nearby==null) { s.noticed=null; ValeMusicProgress.get(p.server).resetDanceAttempt(p.getUUID()); }
        else if(!nearby.anchor().equals(s.noticed)) { s.noticed=nearby.anchor(); message(p,"welcome"); }
    }
    private static void clear(ServerPlayer p) {
        var sessions=SESSIONS.get(p.server); if(sessions!=null) sessions.remove(p.getUUID());
        ValeMusicProgress.get(p.server).resetDanceAttempt(p.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) clear(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) clear(p);
    }
}
