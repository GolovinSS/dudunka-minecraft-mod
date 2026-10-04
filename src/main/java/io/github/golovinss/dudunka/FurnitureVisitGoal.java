package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Visitors walk to reserved separate spots; hosts keep their original furniture goal. */
public final class FurnitureVisitGoal extends Goal {
    private final Companion mob;private FurnitureVisits.Scene scene;private long nextCheck;
    public FurnitureVisitGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){if(mob.level().getGameTime()<nextCheck)return false;nextCheck=mob.level().getGameTime()+10;scene=FurnitureVisits.select(mob);return scene!=null;}
    @Override public boolean canContinueToUse(){return FurnitureVisits.active(mob,scene);}
    @Override public void start(){mob.getNavigation().stop();mob.reserveHomeScene(scene.seats().get(mob.getUUID()));mob.setActivity(Activity.CURIOUS);}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}
        var host=mob.level() instanceof net.minecraft.server.level.ServerLevel level?level.getEntity(scene.host()):null;
        if(host!=null)mob.getLookControl().setLookAt(host,25,25);
        var pos=scene.seats().get(mob.getUUID());var target=Vec3.atBottomCenterOf(pos);
        if(mob.distanceToSqr(target)<=.16){mob.getNavigation().stop();mob.setActivity(Activity.SIT);return;}
        mob.setActivity(Activity.CURIOUS);FamilyFriendships.pause(mob);
        if(mob.getNavigation().isDone() && mob.distanceToSqr(target)<=1){
            var delta=target.subtract(mob.position());var step=delta.scale(Math.min(mob.kind==Kind.SYUSYA?.02:.04,delta.length())/delta.length());
            if(FurnitureScenes.safeRest(mob,mob.position().add(step))){mob.setDeltaMovement(Vec3.ZERO);mob.move(MoverType.SELF,step);return;}
        }
        if(mob.tickCount%10==0){var path=mob.getNavigation().createPath(pos,0);if(!CampfireScenes.safePath(mob,path)){stop();return;}mob.getNavigation().moveTo(path,1);}
    }
    @Override public void stop(){FamilyFriendships.pause(mob);mob.getNavigation().stop();mob.reserveHomeScene(null);mob.setActivity(Activity.IDLE);mob.pauseHomeScenes();scene=null;nextCheck=mob.level().getGameTime()+200;}
}
