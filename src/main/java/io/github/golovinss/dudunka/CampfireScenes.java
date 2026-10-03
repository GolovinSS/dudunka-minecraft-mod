package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;

/** Temporary per-world, per-owner seat reservations. No persistent references to a world/entity. */
public final class CampfireScenes {
    public record Scene(UUID owner,BlockPos fire,Map<UUID,BlockPos> seats,long created) {}
    private static final Map<Level,Map<UUID,Scene>> SCENES=new WeakHashMap<>();
    private static final int[][] OFFSETS={{-2,0},{2,0},{0,-2},{0,2},{-2,-2},{2,-2},{-2,2},{2,2}};
    private CampfireScenes() {}
    public static boolean lit(Level level,BlockPos pos) {
        if(!level.hasChunkAt(pos))return false;
        var state=level.getBlockState(pos);
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }
    private static boolean ownerResting(Player player) {
        return player!=null && player.isAlive() && !player.isSpectator() && player.isShiftKeyDown();
    }
    private static boolean member(Companion mob,UUID owner,BlockPos fire) {
        return mob.isAlive() && owner.equals(mob.ownerId()) && !mob.staying() && mob.blockPosition().distSqr(fire)<=100;
    }
    private static boolean baseValid(Level level,Scene scene) {
        Player owner=level.getPlayerByUUID(scene.owner());
        if(!ownerResting(owner) || owner.blockPosition().distSqr(scene.fire())>16 || !lit(level,scene.fire()))return false;
        int present=0;
        for(UUID id:scene.seats().keySet()) {
            Entity entity=((net.minecraft.server.level.ServerLevel)level).getEntity(id);
            if(entity instanceof Companion mob && member(mob,scene.owner(),scene.fire()))present++;
        }
        return present>=2;
    }
    public static boolean active(Companion mob,Scene scene) {
        if(scene==null || mob.level().isClientSide || !member(mob,scene.owner(),scene.fire()) || mob.isInWaterOrBubble()
                || mob.level().isRainingAt(mob.blockPosition()))return false;
        var current=SCENES.get(mob.level());
        BlockPos seat=scene.seats().get(mob.getUUID());
        if(current==null || current.get(scene.owner())!=scene)return false;
        if(!baseValid(mob.level(),scene)){current.remove(scene.owner());return false;}
        return seat!=null && HomeRules.safeStanding(mob.level(),seat,mob);
    }
    public static boolean safePath(Companion mob,net.minecraft.world.level.pathfinder.Path path) {
        if(path==null || !path.canReach())return false;
        for(int i=0;i<path.getNodeCount();i++) {
            BlockPos p=path.getNode(i).asBlockPos();
            if(!mob.level().hasChunkAt(p) || lit(mob.level(),p) || lit(mob.level(),p.below()))return false;
        }
        return true;
    }
    public static Scene select(Companion mob) {
        if(mob.level().isClientSide || mob.ownerId()==null || mob.staying() || mob.isInWaterOrBubble()
                || mob.level().isRainingAt(mob.blockPosition()))return null;
        Player owner=mob.ownerPlayer();if(!ownerResting(owner)) {
            var cached=SCENES.get(mob.level());if(cached!=null)cached.remove(mob.ownerId());return null;
        }
        var scenes=SCENES.computeIfAbsent(mob.level(),level->new HashMap<>());
        long now=mob.level().getGameTime();
        // Expire abandoned owners during selection; cap the remaining temporary cache.
        scenes.entrySet().removeIf(entry->now-entry.getValue().created()>200 && !baseValid(mob.level(),entry.getValue()));
        Scene old=scenes.get(owner.getUUID());
        if(old!=null && baseValid(mob.level(),old)) {
            BlockPos seat=old.seats().get(mob.getUUID());
            if(seat==null)return null;
            if(HomeRules.safeStanding(mob.level(),seat,mob))return active(mob,old)?old:null;
        }
        scenes.remove(owner.getUUID());if(scenes.size()>=128)scenes.clear();
        BlockPos fire=null;double nearest=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(owner.blockPosition().offset(-4,-1,-4),owner.blockPosition().offset(4,1,4))) {
            double distance=owner.blockPosition().distSqr(p);
            if(distance<=16 && distance<nearest && lit(mob.level(),p)){nearest=distance;fire=p.immutable();}
        }
        if(fire==null)return null;
        var family=mob.level().getEntitiesOfClass(Companion.class,new AABB(fire).inflate(8),
                other->other.isAlive() && owner.getUUID().equals(other.ownerId()) && !other.staying());
        family.sort(Comparator.comparing((Companion other)->other.kind.ordinal()).thenComparing(other->other.getUUID().toString()));
        Map<UUID,BlockPos> seats=new LinkedHashMap<>();Set<BlockPos> used=new HashSet<>();
        for(Companion other:family) {
            if(seats.size()>=8)break;
            if(other.isInWaterOrBubble() || other.level().isRainingAt(other.blockPosition()))continue;
            List<BlockPos> candidates=new ArrayList<>();
            for(int[] offset:OFFSETS)candidates.add(fire.offset(offset[0],0,offset[1]));
            BlockPos friend=FamilyFriendships.get(((net.minecraft.server.level.ServerLevel)mob.level()).getServer()).friendSeat(owner.getUUID(),other.getUUID(),seats);
            candidates.sort(Comparator.comparingDouble((BlockPos seat)->friend==null?0:friend.distSqr(seat))
                .thenComparingDouble(other.blockPosition()::distSqr));
            for(BlockPos seat:candidates) {
                if(used.contains(seat) || !HomeRules.safeStanding(other.level(),seat,other))continue;
                var path=other.getNavigation().createPath(seat,0);
                if(path==null || !path.canReach())continue;
                if(safePath(other,path)){seats.put(other.getUUID(),seat);used.add(seat);break;}
            }
        }
        if(seats.size()<2)return null;
        Scene scene=new Scene(owner.getUUID(),fire,Map.copyOf(seats),now);scenes.put(owner.getUUID(),scene);
        return active(mob,scene)?scene:null;
    }
}
