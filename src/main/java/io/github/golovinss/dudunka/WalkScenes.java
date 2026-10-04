package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Brief loaded-only flower stops. Positions/UUIDs are transient; owner movement cancels them. */
public final class WalkScenes {
    public record Scene(UUID owner,UUID leader,BlockPos flower,Vec3 ownerStart,Map<UUID,BlockPos> spots,long until) {}
    private static final class State { Scene scene;Vec3 ownerPos;long stillSince,next; }
    private static final Map<Level,Map<UUID,State>> STATES=new WeakHashMap<>();
    private WalkScenes(){}
    private static boolean calm(Companion mob){
        var p=mob.ownerPlayer();
        return !mob.level().isClientSide && mob.isAlive() && !mob.staying() && !mob.atHomeMode() && mob.getDeltaMovement().y>=-.1
            && !mob.isInWaterOrBubble() && !mob.isOnFire() && !mob.isPassenger() && !mob.isLeashed() && Math.floorMod(mob.level().getDayTime(),24000)<12000 && !mob.level().isRaining()
            && p!=null && p.isAlive() && !p.isSpectator() && !p.isSleeping() && p.onGround() && !p.isPassenger()
            && !p.isInWaterOrBubble() && mob.distanceToSqr(p)<=100
            && mob.level().getEntitiesOfClass(Monster.class,mob.getBoundingBox().inflate(10),Entity::isAlive).isEmpty();
    }
    public static Scene current(Companion mob){var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());return state!=null && active(mob,state.scene)?state.scene:null;}
    public static boolean active(Companion mob,Scene scene){
        if(scene==null || !scene.owner().equals(mob.ownerId()) || !scene.spots().containsKey(mob.getUUID()) || !calm(mob)
            || mob.level().getGameTime()>=scene.until() || mob.ownerPlayer().distanceToSqr(scene.ownerStart())>1.5625
            || !mob.level().hasChunkAt(scene.flower()) || !mob.level().getBlockState(scene.flower()).is(BlockTags.SMALL_FLOWERS) || mob.level().getBlockState(scene.flower()).is(net.minecraft.world.level.block.Blocks.WITHER_ROSE))return false;
        if(!(mob.level() instanceof ServerLevel level) || !(level.getEntity(scene.leader()) instanceof Companion leader) || !calm(leader))return false;
        for(var e:scene.spots().entrySet())if(!(level.getEntity(e.getKey()) instanceof Companion other) || !calm(other)
            || !scene.owner().equals(other.ownerId()) || !HomeRules.safeStanding(level,e.getValue(),other) || !visible(other,e.getValue(),scene.flower()))return false;
        return true;
    }
    private static boolean visible(Companion mob,BlockPos spot,BlockPos flower){
        var eye=Vec3.atBottomCenterOf(spot).add(0,mob.getEyeHeight(),0);var focus=Vec3.atCenterOf(flower);
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(Math.min(eye.x,focus.x),Math.min(eye.y,focus.y),Math.min(eye.z,focus.z)),BlockPos.containing(Math.max(eye.x,focus.x),Math.max(eye.y,focus.y),Math.max(eye.z,focus.z))))if(!mob.level().hasChunkAt(pos))return false;
        return mob.level().clip(new net.minecraft.world.level.ClipContext(eye,focus,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public static Scene select(Companion mob){
        var current=current(mob);if(current!=null)return current;
        if(mob.kind!=Kind.DUDUNKA || !mob.onGround() || !calm(mob))return null;
        long now=mob.level().getGameTime();var map=STATES.computeIfAbsent(mob.level(),l->new HashMap<>());
        if(map.size()>=128 && !map.containsKey(mob.ownerId()))map.entrySet().removeIf(e->e.getValue().scene==null || now>=e.getValue().scene.until());
        if(map.size()>=128 && !map.containsKey(mob.ownerId()))return null;
        var state=map.computeIfAbsent(mob.ownerId(),id->new State());var owner=mob.ownerPlayer();
        if(state.scene!=null){
            if(mob.level() instanceof ServerLevel level && level.getEntity(state.scene.leader()) instanceof Companion leader && active(leader,state.scene))return null;
            state.scene=null;state.next=Math.max(state.next,now+600);
        }
        if(now<state.next)return null;state.next=now+20;
        if(state.ownerPos==null || state.ownerPos.distanceToSqr(owner.position())>.0625){state.ownerPos=owner.position();state.stillSince=now;return null;}
        if(now-state.stillSince<60 || mob.distanceToSqr(owner)>36 || mob.openChestTarget()!=null || mob.homeWelcomePending())return null;
        var members=mob.level().getEntitiesOfClass(Companion.class,mob.getBoundingBox().inflate(8),c->c!=mob && c.kind!=Kind.DUDUNKA && mob.sameFamily(c) && c.onGround() && calm(c));
        members.sort(Comparator.comparing((Companion c)->c.kind.ordinal()).thenComparingDouble(mob::distanceToSqr));
        var chosen=new ArrayList<Companion>();chosen.add(mob);var kinds=EnumSet.of(Kind.DUDUNKA);
        for(var other:members)if(kinds.add(other.kind))chosen.add(other);
        if(chosen.size()<2)return null;
        var flowers=new ArrayList<BlockPos>();var origin=mob.blockPosition();
        for(var pos:BlockPos.betweenClosed(origin.offset(-4,-1,-4),origin.offset(4,1,4)))if(mob.level().hasChunkAt(pos)
            && mob.level().getBlockState(pos).is(BlockTags.SMALL_FLOWERS) && !mob.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) && owner.distanceToSqr(Vec3.atCenterOf(pos))<=36)flowers.add(pos.immutable());
        flowers.sort(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p))));
        for(var flower:flowers.stream().limit(4).toList()){
            var spots=new LinkedHashMap<UUID,BlockPos>();
            for(var member:chosen){
                var options=new ArrayList<BlockPos>();
                for(var pos:BlockPos.betweenClosed(flower.offset(-2,0,-2),flower.offset(2,0,2)))if(!pos.equals(flower) && !spots.containsValue(pos)
                    && pos.distSqr(flower)<=5 && HomeRules.safeStanding(mob.level(),pos,member))options.add(pos.immutable());
                options.sort(Comparator.comparingDouble(member.blockPosition()::distSqr));
                for(var spot:options)if(visible(member,spot,flower) && HomeScenes.reachable(member,Vec3.atBottomCenterOf(spot))){spots.put(member.getUUID(),spot);break;}
            }
            if(spots.size()!=chosen.size())continue;
            var scene=new Scene(mob.ownerId(),mob.getUUID(),flower,owner.position(),Map.copyOf(spots),now+200);
            if(!active(mob,scene))continue;state.scene=scene;state.next=now+800;return scene;
        }
        state.next=now+60;return null;
    }
    public static void cancel(Companion mob){var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());if(state!=null && state.scene!=null && state.scene.spots().containsKey(mob.getUUID())){state.scene=null;state.ownerPos=null;state.next=Math.max(state.next,mob.level().getGameTime()+600);}}
    public static boolean settled(Companion mob,Scene scene){return active(mob,scene) && mob.distanceToSqr(Vec3.atBottomCenterOf(scene.spots().get(mob.getUUID())))<=.36
        && mob.activity()==(mob.kind==Kind.MARUSYA?Activity.SIT:mob.kind==Kind.SYUSYA?Activity.SEEK_PLANT:Activity.CURIOUS);}
    public static void friendship(Companion mob,Scene scene){
        if(!(mob.level() instanceof ServerLevel level) || !settled(mob,scene))return;long now=level.getServer().overworld().getGameTime();if(now%20!=0)return;
        var ledger=FamilyFriendships.get(level.getServer());
        for(var id:scene.spots().keySet())if(!id.equals(mob.getUUID()) && level.getEntity(id) instanceof Companion other && settled(other,scene) && mob.hasLineOfSight(other))ledger.observe(scene.owner(),mob.getUUID(),mob.kind,id,other.kind,now);
    }
}
