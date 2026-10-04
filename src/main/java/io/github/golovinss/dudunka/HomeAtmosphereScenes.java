package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Actual loaded homes, outdoor rain, indoor destinations. No tickets, rewards or block changes. */
public final class HomeAtmosphereScenes {
    public enum Occasion { WELCOME, RAIN }
    public enum Role { GREET, WINDOW, NAP, POT }
    public record Scene(UUID owner,Occasion occasion,Vec3 ownerStart,Map<UUID,BlockPos> homes,
        Map<UUID,BlockPos> spots,Map<UUID,BlockPos> focuses,Map<UUID,Role> roles,boolean reunion,long until) {}
    private static final class State { Scene scene;long nextRain;final Map<UUID,Long> arrived=new HashMap<>(); }
    private static final Map<Level,Map<UUID,State>> STATES=new WeakHashMap<>();
    private HomeAtmosphereScenes(){}
    private static boolean calm(Companion mob){
        var player=mob.ownerPlayer();var home=mob.homeAnchor();
        return !mob.level().isClientSide && home!=null && player!=null && player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isShiftKeyDown()
            && mob.isAlive() && !mob.staying() && !mob.isInWaterOrBubble() && !mob.isOnFire() && !mob.isPassenger() && !mob.isVehicle() && !mob.isLeashed()
            && !mob.level().isRainingAt(mob.blockPosition()) && mob.distanceToSqr(Vec3.atCenterOf(home))<=100 && player.distanceToSqr(Vec3.atCenterOf(home))<=64
            && mob.openChestTarget()==null && mob.level().getEntitiesOfClass(Monster.class,mob.getBoundingBox().inflate(10),Entity::isAlive).isEmpty();
    }
    private static boolean covered(Companion mob,BlockPos spot){return mob.level().hasChunkAt(spot) && mob.level().getWorldBorder().isWithinBounds(spot)
        && !mob.level().canSeeSky(spot.above()) && mob.level().getBlockState(spot.below()).isFaceSturdy(mob.level(),spot.below(),net.minecraft.core.Direction.UP) && HomeRules.safeStanding(mob.level(),spot,mob) && spot.distSqr(mob.homeAnchor())<=36;}
    private static boolean window(Level level,BlockPos pos){if(!level.hasChunkAt(pos))return false;var s=level.getBlockState(pos);return s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.TINTED_GLASS) || s.getBlock() instanceof StainedGlassBlock || s.getBlock() instanceof StainedGlassPaneBlock || s.is(net.minecraftforge.common.Tags.Blocks.GLASS) || s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES);}
    private static boolean rainyOutside(Level level,BlockPos window){
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){var p=window.relative(direction);if(level.hasChunkAt(p) && level.isRainingAt(p))return true;}return false;
    }
    private static boolean pot(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos))return false;var state=level.getBlockState(pos);
        return state.getBlock() instanceof FlowerPotBlock p && p.getContent()!=Blocks.AIR && !state.is(Blocks.POTTED_WITHER_ROSE);
    }
    private static boolean visible(Companion mob,BlockPos spot,BlockPos focus){
        var eye=Vec3.atBottomCenterOf(spot).add(0,mob.getEyeHeight(),0);var target=Vec3.atCenterOf(focus);
        for(var p:BlockPos.betweenClosed(BlockPos.containing(Math.min(eye.x,target.x),Math.min(eye.y,target.y),Math.min(eye.z,target.z)),BlockPos.containing(Math.max(eye.x,target.x),Math.max(eye.y,target.y),Math.max(eye.z,target.z))))if(!mob.level().hasChunkAt(p))return false;
        var hit=mob.level().clip(new net.minecraft.world.level.ClipContext(eye,target,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(focus);
    }
    private static boolean availableSpot(Companion mob,BlockPos spot,Collection<BlockPos> used){
        if(used.contains(spot) || !covered(mob,spot))return false;
        var box=mob.getBoundingBox().move(Vec3.atBottomCenterOf(spot).subtract(mob.position()));
        return mob.level().getEntities(mob,box,e->e instanceof net.minecraft.world.entity.LivingEntity
            && !(e instanceof Companion other && mob.sameFamily(other) && calm(other))).isEmpty();
    }
    private static BlockPos spot(Companion mob,BlockPos focus,Collection<BlockPos> used,boolean greeting){
        var options=new ArrayList<BlockPos>();var home=mob.homeAnchor();if(home==null)return null;
        for(var p:BlockPos.betweenClosed(home.offset(-4,-1,-4),home.offset(4,1,4))){
            double distance=greeting?mob.ownerPlayer().distanceToSqr(Vec3.atBottomCenterOf(p)):p.distSqr(focus);
            if(distance<1 || distance>(greeting?6.25:5) || !availableSpot(mob,p,used) || !visible(mob,p,focus))continue;options.add(p.immutable());
        }
        options.sort(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atBottomCenterOf(p))));
        for(var p:options)if(reachable(mob,p))return p;return null;
    }
    public static boolean safePath(Companion mob,net.minecraft.world.level.pathfinder.Path path){
        if(!CampfireScenes.safePath(mob,path))return false;
        for(int i=0;i<path.getNodeCount();i++){var p=path.getNode(i).asBlockPos();var floor=mob.level().getBlockState(p.below());
            if(mob.level().isRainingAt(p) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || !mob.level().getFluidState(p).isEmpty())return false;}
        return true;
    }
    private static boolean pathFits(Companion mob,net.minecraft.world.level.pathfinder.Path path){
        if(!safePath(mob,path))return false;
        // Vanilla nodes may cut a diagonal across a small home marker. Reject that route.
        for(int i=1;i<path.getNodeCount();i++){
            var a=i==1?mob.position():Vec3.atBottomCenterOf(path.getNode(i-1).asBlockPos());var b=Vec3.atBottomCenterOf(path.getNode(i).asBlockPos());
            if(a.y!=b.y)continue; // Let vanilla navigate steps and low furniture at their actual height.
            int steps=(int)Math.ceil(a.distanceTo(b)/.2);
            for(int n=1;n<steps;n++)if(!FurnitureScenes.safeRest(mob,a.lerp(b,(double)n/steps)))return false;
        }
        return true;
    }
    private static boolean reachable(Companion mob,BlockPos spot){return mob.distanceToSqr(Vec3.atBottomCenterOf(spot))<=.16 || pathFits(mob,mob.getNavigation().createPath(spot,0));}
    private static boolean rainingHome(Companion mob){var home=mob.homeAnchor();return home!=null && mob.level().isRaining() && mob.level().getBiome(home).value().getPrecipitationAt(home)==net.minecraft.world.level.biome.Biome.Precipitation.RAIN;}
    public static Scene current(Companion mob){var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());return state!=null && active(mob,state.scene)?state.scene:null;}
    private static boolean participant(Companion mob,Scene scene){
        var home=scene.homes().get(mob.getUUID());var spot=scene.spots().get(mob.getUUID());var focus=scene.focuses().get(mob.getUUID());var role=scene.roles().get(mob.getUUID());
        if(!scene.owner().equals(mob.ownerId()) || home==null || spot==null || focus==null || role==null || !calm(mob) || !home.equals(mob.homeAnchor()) || !covered(mob,spot))return false;
        if(scene.occasion()==Occasion.WELCOME)return role==Role.GREET && mob.ownerPlayer().distanceToSqr(scene.ownerStart())<=4 && visible(mob,spot,focus)
            && (mob.distanceToSqr(Vec3.atBottomCenterOf(spot))>.16 || mob.hasLineOfSight(mob.ownerPlayer()));
        return !mob.homeWelcomeEligible() && rainingHome(mob) && visible(mob,spot,focus)
            && switch(role){case WINDOW->mob.kind==Kind.DUDUNKA && window(mob.level(),focus) && rainyOutside(mob.level(),focus);
                case POT->mob.kind==Kind.SYUSYA && pot(mob.level(),focus);case NAP->mob.kind==Kind.MARUSYA;default->false;};
    }
    public static boolean active(Companion mob,Scene scene){
        if(scene==null || !(mob.level() instanceof ServerLevel level) || level.getGameTime()>=scene.until())return false;
        var map=STATES.get(level);var state=map==null?null:map.get(scene.owner());if(state==null || state.scene!=scene || !participant(mob,scene))return false;
        for(var id:scene.spots().keySet())if(!(level.getEntity(id) instanceof Companion other) || !participant(other,scene))return false;return true;
    }
    public static Scene select(Companion mob){
        var current=current(mob);if(current!=null)return current;if(!(mob.level() instanceof ServerLevel level) || !calm(mob))return null;
        long now=level.getGameTime();var map=STATES.computeIfAbsent(level,l->new HashMap<>());
        if(map.size()>=128 && !map.containsKey(mob.ownerId()))map.entrySet().removeIf(e->e.getValue().scene==null && now>=e.getValue().nextRain || e.getValue().scene!=null && now>=e.getValue().scene.until() && now>=e.getValue().nextRain);
        if(map.size()>=128 && !map.containsKey(mob.ownerId()))return null;var state=map.computeIfAbsent(mob.ownerId(),id->new State());
        if(state.scene!=null){
            boolean live=false;for(var id:state.scene.spots().keySet())if(level.getEntity(id) instanceof Companion other && active(other,state.scene)){live=true;break;}
            if(live)return null;state.scene=null;state.arrived.clear();
        }
        var family=level.getEntitiesOfClass(Companion.class,new AABB(mob.homeAnchor()).inflate(8),other->mob.sameFamily(other) && calm(other));
        family.sort(Comparator.comparing((Companion c)->c.kind.ordinal()).thenComparing(c->c.getUUID().toString()));
        var homes=new LinkedHashMap<UUID,BlockPos>();var spots=new LinkedHashMap<UUID,BlockPos>();var focuses=new LinkedHashMap<UUID,BlockPos>();var roles=new LinkedHashMap<UUID,Role>();var kinds=EnumSet.noneOf(Kind.class);
        boolean welcome=family.stream().anyMatch(Companion::homeWelcomeEligible);
        if(welcome){
            var focus=mob.ownerPlayer().blockPosition();
            for(var other:family){if(!other.homeWelcomeEligible() || !kinds.add(other.kind))continue;var seat=spot(other,focus,spots.values(),true);if(seat!=null)add(other,seat,focus,Role.GREET,homes,spots,focuses,roles);}
        }else{
            if(!rainingHome(mob) || now<state.nextRain)return null;state.nextRain=now+100;
            for(var other:family){
                if(other.kind==Kind.MARUSYA || !other.homeTogetherReady() || !kinds.add(other.kind))continue;
                var interests=new ArrayList<BlockPos>();var home=other.homeAnchor();
                for(var p:BlockPos.betweenClosed(home.offset(-4,0,-4),home.offset(4,2,4))){if(!level.hasChunkAt(p))continue;
                    if(other.kind==Kind.DUDUNKA?window(level,p) && rainyOutside(level,p):pot(level,p))interests.add(p.immutable());}
                interests.sort(Comparator.comparingDouble(p->other.distanceToSqr(Vec3.atCenterOf(p))));
                for(var focus:interests.stream().limit(8).toList()){var seat=spot(other,focus,spots.values(),false);if(seat!=null){add(other,seat,focus,other.kind==Kind.DUDUNKA?Role.WINDOW:Role.POT,homes,spots,focuses,roles);break;}}
            }
            if(!spots.isEmpty())for(var cat:family){if(cat.kind!=Kind.MARUSYA || !cat.homeTogetherReady())continue;
                // Curl next to an actual friend's reserved spot, not alone beside an arbitrary wall.
                var focus=spots.values().iterator().next();var seat=spot(cat,focus,spots.values(),false);if(seat!=null){add(cat,seat,focus,Role.NAP,homes,spots,focuses,roles);break;}}
        }
        if(!spots.containsKey(mob.getUUID()))return null;
        boolean reunion=false;var ledger=FamilyFriendships.get(level.getServer());var ids=new ArrayList<>(spots.keySet());
        if(welcome)for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++)if(level.getEntity(ids.get(i)) instanceof Companion a && level.getEntity(ids.get(j)) instanceof Companion b && a.trust()>=60 && b.trust()>=60 && ledger.score(mob.ownerId(),a.getUUID(),b.getUUID())>=10)reunion=true;
        state.scene=new Scene(mob.ownerId(),welcome?Occasion.WELCOME:Occasion.RAIN,mob.ownerPlayer().position(),Map.copyOf(homes),Map.copyOf(spots),Map.copyOf(focuses),Map.copyOf(roles),reunion,now+(welcome?240:320));state.arrived.clear();
        if(!active(mob,state.scene)){state.scene=null;return null;}if(!welcome)state.nextRain=now+1200;return state.scene;
    }
    private static void add(Companion mob,BlockPos spot,BlockPos focus,Role role,Map<UUID,BlockPos> homes,Map<UUID,BlockPos> spots,Map<UUID,BlockPos> focuses,Map<UUID,Role> roles){var id=mob.getUUID();homes.put(id,mob.homeAnchor());spots.put(id,spot);focuses.put(id,focus);roles.put(id,role);}
    public static Activity greeting(Companion mob){if(mob.trust()<25)return Activity.CURIOUS;return switch(mob.kind){case DUDUNKA->Activity.WAVE;case MARUSYA->Activity.PURR;case SYUSYA->Activity.PEEK;};}
    public static Activity activity(Companion mob,Scene scene){
        var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());
        if(scene.occasion()==Occasion.WELCOME){
            if(scene.reunion() && state!=null && state.arrived.size()==scene.spots().size() && state.arrived.values().stream().allMatch(t->mob.level().getGameTime()-t>=60))return Activity.SIT;
            return greeting(mob);
        }
        return switch(scene.roles().get(mob.getUUID())){case WINDOW->Activity.SIT;case NAP->Activity.CURL;case POT->Activity.SEEK_PLANT;default->Activity.CURIOUS;};
    }
    public static void arrived(Companion mob,Scene scene){var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());if(state!=null && state.scene==scene)state.arrived.putIfAbsent(mob.getUUID(),mob.level().getGameTime());}
    public static boolean settled(Companion mob,Scene scene){return active(mob,scene) && mob.distanceToSqr(Vec3.atBottomCenterOf(scene.spots().get(mob.getUUID())))<=.16 && mob.activity()==activity(mob,scene);}
    public static void friendship(Companion mob,Scene scene){
        if(!(mob.level() instanceof ServerLevel level) || !settled(mob,scene))return;long now=level.getServer().overworld().getGameTime();if(now%20!=0)return;
        var ledger=FamilyFriendships.get(level.getServer());for(var id:scene.spots().keySet())if(!id.equals(mob.getUUID()) && level.getEntity(id) instanceof Companion other && settled(other,scene) && mob.hasLineOfSight(other))ledger.observe(scene.owner(),mob.getUUID(),mob.kind,id,other.kind,now);
    }
    public static void cancel(Companion mob){var map=STATES.get(mob.level());var state=map==null?null:map.get(mob.ownerId());if(state!=null && state.scene!=null && state.scene.spots().containsKey(mob.getUUID())){state.scene=null;state.arrived.clear();state.nextRain=Math.max(state.nextRain,mob.level().getGameTime()+200);}}
}
