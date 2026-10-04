package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Short, automatic owner-scoped gatherings around nearby, valid personal homes. */
public final class HomeTogetherScenes {
    public record Scene(UUID owner,BlockPos focus,Map<UUID,BlockPos> homes,Map<UUID,BlockPos> seats,long until) {}
    private static final Map<Level,Map<UUID,State>> STATES=new WeakHashMap<>();
    private static final class State { Scene scene;long next; }
    private static final int[][] OFFSETS={{-1,0},{1,0},{0,-1},{0,1},{-2,0},{2,0},{0,-2},{0,2},{-1,-1},{1,-1},{-1,1},{1,1},{-2,-2},{2,-2},{-2,2},{2,2}};
    private HomeTogetherScenes() {}
    private static boolean eligible(Companion mob) {
        var owner=mob.ownerPlayer();var home=mob.homeAnchor();
        if(home==null || owner==null || !owner.isAlive() || owner.isSpectator() || owner.isSleeping() || owner.isShiftKeyDown()
            || !mob.isAlive() || mob.staying() || !mob.homeTogetherReady() || mob.isInWaterOrBubble()
            || mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || mob.level().isRainingAt(mob.blockPosition())
            || owner.distanceToSqr(Vec3.atCenterOf(home))>64 || mob.distanceToSqr(Vec3.atCenterOf(home))>100)return false;
        // Cats keep their evening bed routine; urgent family waiting is never bypassed.
        if(mob.kind==Kind.MARUSYA && (HomeScenes.evening(mob) || !mob.level().getEntitiesOfClass(Monster.class,mob.getBoundingBox().inflate(10),m->m.isAlive()).isEmpty()))return false;
        if(mob.kind!=Kind.SYUSYA && !mob.level().getEntitiesOfClass(Companion.class,mob.getBoundingBox().inflate(12),
            other->other.kind==Kind.SYUSYA && mob.sameFamily(other) && !other.staying() && mob.distanceToSqr(other)>16).isEmpty())return false;
        return true;
    }
    private static boolean seatSafe(Companion mob,BlockPos seat) {
        if(!mob.level().hasChunkAt(seat) || !mob.level().getWorldBorder().isWithinBounds(seat)
            || mob.level().canSeeSky(seat.above()) || !HomeRules.safeStanding(mob.level(),seat,mob))return false;
        var box=mob.getBoundingBox().move(Vec3.atBottomCenterOf(seat).subtract(mob.position()));
        return mob.level().getEntities(mob,box,entity->entity instanceof net.minecraft.world.entity.LivingEntity
            && !(entity instanceof Companion other && mob.sameFamily(other) && eligible(other))).isEmpty();
    }
    private static boolean participant(Companion mob,Scene scene) {
        BlockPos seat=scene.seats().get(mob.getUUID()),home=scene.homes().get(mob.getUUID());
        return scene.owner().equals(mob.ownerId()) && home!=null && home.equals(mob.homeAnchor()) && eligible(mob)
            && seat!=null && seatSafe(mob,seat) && mob.distanceToSqr(Vec3.atCenterOf(scene.focus()))<=100;
    }
    public static boolean active(Companion mob,Scene scene) {
        if(scene==null || !(mob.level() instanceof ServerLevel level) || level.getGameTime()>=scene.until())return false;
        var states=STATES.get(level);var state=states==null?null:states.get(scene.owner());
        if(state==null || state.scene!=scene || !participant(mob,scene))return false;
        int present=0;
        for(UUID id:scene.seats().keySet())if(level.getEntity(id) instanceof Companion other && participant(other,scene))present++;
        return present>=2;
    }
    public static Scene select(Companion mob) {
        if(!(mob.level() instanceof ServerLevel level) || !eligible(mob))return null;
        long now=level.getGameTime();var states=STATES.computeIfAbsent(level,key->new HashMap<>());
        var state=states.get(mob.ownerId());
        if(state!=null && state.scene!=null && active(mob,state.scene))return state.scene;
        if(state!=null && now<state.next)return null;
        if(state==null){
            if(states.size()>=128)states.entrySet().removeIf(e->now>=e.getValue().next);
            if(states.size()>=128)return null;
            state=new State();states.put(mob.ownerId(),state);
        }
        state.next=now+100;state.scene=null;
        var home=mob.homeAnchor();
        var family=level.getEntitiesOfClass(Companion.class,new AABB(home).inflate(8),other->mob.sameFamily(other) && eligible(other));
        family.sort(Comparator.comparing((Companion other)->other.kind.ordinal()).thenComparing(other->other.getUUID().toString()));
        if(family.size()<2)return null;
        var focus=family.get(0).homeAnchor();var homes=new LinkedHashMap<UUID,BlockPos>();var seats=new LinkedHashMap<UUID,BlockPos>();var used=new HashSet<BlockPos>();
        for(var other:family) {
            if(seats.size()>=8)break;
            var candidates=new ArrayList<BlockPos>();for(int[] offset:OFFSETS)candidates.add(focus.offset(offset[0],0,offset[1]));
            var friend=FamilyFriendships.get(level.getServer()).friendSeat(mob.ownerId(),other.getUUID(),seats);
            candidates.sort(Comparator.comparingDouble((BlockPos p)->friend==null?0:friend.distSqr(p)).thenComparingDouble(other.blockPosition()::distSqr));
            for(var seat:candidates) {
                if(used.contains(seat) || !seatSafe(other,seat) || !HomeScenes.reachable(other,Vec3.atBottomCenterOf(seat)))continue;
                seats.put(other.getUUID(),seat);homes.put(other.getUUID(),other.homeAnchor());used.add(seat);break;
            }
        }
        if(seats.size()<2 || !seats.containsKey(mob.getUUID()))return null;
        state.scene=new Scene(mob.ownerId(),focus,Map.copyOf(homes),Map.copyOf(seats),now+200);state.next=now+400;
        return active(mob,state.scene)?state.scene:null;
    }
}
