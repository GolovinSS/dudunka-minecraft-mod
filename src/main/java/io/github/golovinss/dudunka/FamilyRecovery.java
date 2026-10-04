package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, transient tickets: only the indexed chunk, no offline entity reconstruction. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class FamilyRecovery {
    private static final TicketType<UUID> TICKET=TicketType.create("dudunka_recovery",Comparator.comparing(UUID::toString),200);
    private record Job(java.lang.ref.WeakReference<ServerPlayer> player,UUID entity,UUID session,int request,ServerLevel source,ChunkPos chunk,long deadline) {}
    private static final Map<MinecraftServer,Map<UUID,Job>> JOBS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Long>> COOLDOWNS=new WeakHashMap<>();
    private FamilyRecovery() {}
    public static boolean grounded(ServerPlayer p){return p.isAlive() && !p.isSpectator() && !p.isSleeping() && p.onGround() && !p.isPassenger() && !p.isFallFlying() && !p.isInWaterOrBubble() && !p.isInLava();}
    public static boolean start(ServerPlayer player,UUID entity,UUID session,int request){
        var server=player.server;var jobs=JOBS.computeIfAbsent(server,s->new HashMap<>());long now=server.overworld().getGameTime();
        var cooldowns=COOLDOWNS.computeIfAbsent(server,s->new HashMap<>());
        if(!grounded(player) || jobs.size()>=8 || jobs.containsKey(player.getUUID()) || now<cooldowns.getOrDefault(player.getUUID(),Long.MIN_VALUE))return false;
        var member=FamilyRegistry.get(server).member(entity);
        if(member==null || !player.getUUID().equals(member.owner()) || CarrierLedger.get(server).carried(entity))return false;
        var source=server.getLevel(ResourceKey.create(Registries.DIMENSION,member.dimension()));if(source==null)return false;
        // Rate limit attempts too, including missing/stale records.
        cooldowns.put(player.getUUID(),now+200);
        var chunk=new ChunkPos(member.pos());source.getChunkSource().addRegionTicket(TICKET,chunk,0,entity);
        jobs.put(player.getUUID(),new Job(new java.lang.ref.WeakReference<>(player),entity,session,request,source,chunk,now+100));return true;
    }
    public static boolean pending(MinecraftServer server,UUID owner){var jobs=JOBS.get(server);return jobs!=null && jobs.containsKey(owner);}
    private static Companion find(MinecraftServer server,UUID id){for(var level:server.getAllLevels()){var e=level.getEntity(id);if(e instanceof Companion mob && mob.isAlive() && !mob.isRemoved())return mob;}return null;}
    public static boolean move(ServerPlayer player,Companion mob){
        if(!grounded(player) || !mob.isAlive() || !player.getUUID().equals(mob.ownerId()) || mob.isLeashed() || mob.isPassenger() || mob.isVehicle() || CarrierLedger.get(player.server).carried(mob.getUUID()))return false;
        var destination=player.serverLevel();var preview=DudunkaMod.TYPES.get(mob.kind).get().create(destination);if(preview==null)return false;
        var data=new CompoundTag();if(!mob.save(data))return false;preview.load(data);preview.moveTo(player.getX(),player.getY(),player.getZ(),0,0);
        BlockPos chosen=null;
        outer:for(int dy:new int[]{0,1,-1})for(int radius=2;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var pos=player.blockPosition().offset(dx,dy,dz);if(FamilyTravel.safeDestination(preview,pos)){chosen=pos;break outer;}
        }
        if(chosen==null)return false;
        mob.prepareRecovery();UUID id=mob.getUUID();
        boolean success=mob.teleportTo(destination,chosen.getX()+.5,chosen.getY(),chosen.getZ()+.5,Set.of(),mob.getYRot(),mob.getXRot());
        if(success){var actual=destination.getEntity(id);if(actual instanceof Companion arrived){arrived.prepareRecovery();FamilyRegistry.get(player.server).observe(arrived);}}
        return success;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();var jobs=JOBS.get(server);if(jobs==null)return;
        long now=server.overworld().getGameTime();var iterator=jobs.values().iterator();
        while(iterator.hasNext()){
            var job=iterator.next();var player=job.player().get();
            boolean valid=player!=null && !player.isRemoved() && AlbumCommands.recoveryValid(player,job.session(),job.request());
            var mob=valid?find(server,job.entity()):null;
            if(valid && mob==null && now<job.deadline())continue;
            boolean accepted=valid && mob!=null && move(player,mob);
            job.source().getChunkSource().removeRegionTicket(TICKET,job.chunk(),0,job.entity());iterator.remove();
            if(valid)AlbumCommands.finishRecovery(player,job.session(),job.request(),accepted);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){var server=event.getServer();var jobs=JOBS.remove(server);if(jobs!=null)for(var job:jobs.values())job.source().getChunkSource().removeRegionTicket(TICKET,job.chunk(),0,job.entity());COOLDOWNS.remove(server);}
}
