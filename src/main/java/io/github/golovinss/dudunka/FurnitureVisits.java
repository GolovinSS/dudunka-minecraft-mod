package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** One short visit per owner/level; only UUIDs/positions, never loaded chunks or retained mobs. */
public final class FurnitureVisits {
    public record Scene(UUID owner,UUID host,Kind kind,BlockPos furniture,Vec3 hostPosition,Map<UUID,BlockPos> homes,Map<UUID,BlockPos> seats,long until) {}
    private static final Map<Level,Map<UUID,State>> STATES=new WeakHashMap<>();
    private static final class State { Scene scene;long next; }
    private FurnitureVisits() {}
    public static Activity occasion(Kind kind){return switch(kind){case DUDUNKA->Activity.SHOW_DRAWING;case MARUSYA->Activity.CURL;case SYUSYA->Activity.PEEK;};}
    private static boolean calm(Companion mob){
        var owner=mob.ownerPlayer();var home=mob.homeAnchor();
        return !mob.level().isClientSide && mob.isAlive() && mob.atHomeMode() && !mob.staying() && home!=null
            && owner!=null && owner.isAlive() && !owner.isSpectator() && !owner.isSleeping()
            && !mob.isPassenger() && !mob.isVehicle() && !mob.isLeashed() && !mob.isInWaterOrBubble() && !mob.isInLava()
            && !mob.level().isRainingAt(mob.blockPosition()) && !mob.homeWelcomePending() && !mob.homeWelcomeRunning()
            && mob.openChestTarget()==null && mob.distanceToSqr(Vec3.atCenterOf(home))<=100
            && owner.distanceToSqr(mob)<=144
            && mob.level().getEntitiesOfClass(Monster.class,mob.getBoundingBox().inflate(10),m->m.isAlive()).isEmpty();
    }
    private static boolean visitor(Companion mob){return calm(mob) && mob.homeSceneReady() && mob.activity()!=Activity.PURR;}
    private static boolean seatSafe(Companion mob,BlockPos pos){
        if(!mob.level().hasChunkAt(pos) || mob.level().canSeeSky(pos.above()) || !FamilyTravel.safeDestination(mob,pos))return false;
        return true;
    }
    private static boolean visibleFrom(Companion guest,BlockPos pos,Companion host){
        var from=Vec3.atBottomCenterOf(pos).add(0,guest.getEyeHeight(),0);
        return guest.level().clip(new net.minecraft.world.level.ClipContext(from,host.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,guest)).getType()==HitResult.Type.MISS;
    }
    private static boolean hostValid(Companion host,Scene scene){
        if(scene==null || !(host.level() instanceof ServerLevel level) || level.getGameTime()>=scene.until() || !host.getUUID().equals(scene.host()) || !scene.owner().equals(host.ownerId()) || !calm(host)
            || host.distanceToSqr(scene.hostPosition())>.16 || host.activity()!=occasion(scene.kind()) || !scene.furniture().equals(host.homeSceneTarget()) || !scene.furniture().equals(host.furniturePosition()))return false;
        var furniture=FurnitureScenes.furniture(host);return furniture!=null && FurnitureScenes.info(host).state()==FurnitureScenes.State.READY;
    }
    private static boolean participant(Companion mob,Scene scene){
        var home=scene.homes().get(mob.getUUID());var seat=scene.seats().get(mob.getUUID());
        return visitor(mob) && scene.owner().equals(mob.ownerId()) && home!=null && home.equals(mob.homeAnchor()) && seat!=null
            && Vec3.atBottomCenterOf(seat).distanceToSqr(Vec3.atCenterOf(home))<=36 && seatSafe(mob,seat)
            && mob.distanceToSqr(Vec3.atCenterOf(scene.furniture()))<=100
            && mob.level() instanceof ServerLevel level && level.getEntity(scene.host()) instanceof Companion host && visibleFrom(mob,seat,host);
    }
    private static boolean current(Level level,Scene scene){var states=STATES.get(level);var state=states==null?null:states.get(scene.owner());return state!=null && state.scene==scene;}
    public static boolean active(Companion mob,Scene scene){
        if(scene==null || !(mob.level() instanceof ServerLevel level) || !current(level,scene) || !(level.getEntity(scene.host()) instanceof Companion host) || !hostValid(host,scene))return false;
        if(mob!=host && !participant(mob,scene))return false;
        for(UUID id:scene.seats().keySet())if(level.getEntity(id) instanceof Companion other && participant(other,scene))return true;
        return false;
    }
    public static Scene hosted(Companion host){var states=STATES.get(host.level());var state=states==null?null:states.get(host.ownerId());return state!=null && state.scene!=null && state.scene.host().equals(host.getUUID()) && active(host,state.scene)?state.scene:null;}
    /** Called only during the host's real occasion. An ordinary solo activity is never a gathering. */
    public static Scene offer(Companion host,FurnitureScenes.Scene furniture){
        var existing=hosted(host);if(existing!=null)return existing;
        if(!(host.level() instanceof ServerLevel level) || !calm(host) || host.activity()!=occasion(host.kind) || furniture==null || !FurnitureScenes.valid(host,furniture))return null;
        long now=level.getGameTime();var states=STATES.computeIfAbsent(level,l->new HashMap<>());var state=states.get(host.ownerId());
        if(state!=null && now<state.next)return null;
        if(state==null){if(states.size()>=128)states.entrySet().removeIf(e->now>=e.getValue().next);if(states.size()>=128)return null;state=new State();states.put(host.ownerId(),state);}
        state.next=now+100;state.scene=null;
        var family=level.getEntitiesOfClass(Companion.class,new AABB(furniture.furniture()).inflate(6),other->other!=host && other.kind!=host.kind && host.sameFamily(other) && visitor(other) && other.homeTogetherReady());
        var ledger=FamilyFriendships.get(level.getServer());family.sort(Comparator.comparingInt((Companion m)->ledger.score(host.ownerId(),host.getUUID(),m.getUUID())).reversed().thenComparingDouble(host::distanceToSqr).thenComparing(m->m.getUUID().toString()));
        var seats=new LinkedHashMap<UUID,BlockPos>();var homes=new LinkedHashMap<UUID,BlockPos>();var used=new HashSet<BlockPos>();
        for(var guest:family){if(seats.size()>=2)break;var home=guest.homeAnchor();
            var options=new ArrayList<BlockPos>();for(int radius=1;radius<=2;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
                if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
                var pos=furniture.approach().offset(x,0,z);
                // Guests stay in front of the furniture and out of its entry/host approach.
                var delta=pos.subtract(furniture.furniture());if(delta.getX()*furniture.facing().getStepX()+delta.getZ()*furniture.facing().getStepZ()<1)continue;
                if(pos.equals(furniture.approach()) || used.contains(pos) || Vec3.atBottomCenterOf(pos).distanceToSqr(Vec3.atCenterOf(home))>36)continue;
                options.add(pos);
            }
            options.sort(Comparator.comparingDouble(guest.blockPosition()::distSqr));
            for(var pos:options){if(!seatSafe(guest,pos) || !visibleFrom(guest,pos,host) || !HomeScenes.reachable(guest,Vec3.atBottomCenterOf(pos)))continue;
                var box=guest.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(guest.position()));
                boolean overlap=false;for(var chosen:seats.entrySet())if(level.getEntity(chosen.getKey()) instanceof Companion other && box.intersects(other.getBoundingBox().move(Vec3.atBottomCenterOf(chosen.getValue()).subtract(other.position())))){overlap=true;break;}
                if(overlap)continue;seats.put(guest.getUUID(),pos);homes.put(guest.getUUID(),home);used.add(pos);break;
            }
        }
        if(seats.isEmpty())return null;
        state.scene=new Scene(host.ownerId(),host.getUUID(),host.kind,furniture.furniture(),host.kind==Kind.DUDUNKA?Vec3.atBottomCenterOf(furniture.approach()):furniture.rest(),Map.copyOf(homes),Map.copyOf(seats),now+240);state.next=now+840;
        return active(host,state.scene)?state.scene:null;
    }
    /** Stagger new solo starts so three assigned friends do not stay busy in lockstep forever. */
    public static boolean waitForHost(Companion guest){
        if(!visitor(guest))return false;
        return !guest.level().getEntitiesOfClass(Companion.class,guest.getBoundingBox().inflate(6),other->{
            var position=other.furniturePosition();
            return other!=guest && other.kind!=guest.kind && guest.sameFamily(other) && calm(other) && position!=null
                && position.equals(other.homeSceneTarget()) && FurnitureScenes.furniture(other)!=null;
        }).isEmpty();
    }
    public static Scene select(Companion guest){
        var states=STATES.get(guest.level());var state=states==null?null:states.get(guest.ownerId());
        if(state==null || state.scene==null || !guest.homeTogetherReady() || !active(guest,state.scene) || guest.getUUID().equals(state.scene.host()))return null;
        return state.scene;
    }
    public static boolean settled(Companion guest,Scene scene){return scene!=null && participant(guest,scene) && guest.activity()==Activity.SIT && guest.distanceToSqr(Vec3.atBottomCenterOf(scene.seats().get(guest.getUUID())))<=.16;}
    public static void tickFriendship(Companion host,Scene scene){
        if(!(host.level() instanceof ServerLevel level) || !active(host,scene))return;
        long now=level.getServer().overworld().getGameTime();if(now%20!=0)return;
        var ledger=FamilyFriendships.get(level.getServer());var seated=new ArrayList<Companion>();
        for(var id:scene.seats().keySet())if(level.getEntity(id) instanceof Companion other && settled(other,scene) && other.hasLineOfSight(host))seated.add(other);
        for(var other:seated)ledger.observe(scene.owner(),host.getUUID(),host.kind,other.getUUID(),other.kind,now);
        for(int a=0;a<seated.size();a++)for(int b=a+1;b<seated.size();b++)ledger.observe(scene.owner(),seated.get(a).getUUID(),seated.get(a).kind,seated.get(b).getUUID(),seated.get(b).kind,now);
    }
    public static void cancelHost(Companion host){var states=STATES.get(host.level());var state=states==null?null:states.get(host.ownerId());if(state!=null && state.scene!=null && state.scene.host().equals(host.getUUID())){state.scene=null;FamilyFriendships.pause(host);}}
}
