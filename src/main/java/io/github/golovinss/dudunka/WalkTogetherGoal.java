package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class WalkTogetherGoal extends Goal {
    private final Companion mob;private WalkScenes.Scene scene;private long nextCheck;
    public WalkTogetherGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;scene=WalkScenes.select(mob);return scene!=null;}
    @Override public boolean canContinueToUse(){return WalkScenes.active(mob,scene);}
    @Override public void start(){mob.getNavigation().stop();mob.setActivity(Activity.CURIOUS);}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}
        var target=Vec3.atBottomCenterOf(scene.spots().get(mob.getUUID()));var look=Vec3.atCenterOf(scene.flower());
        mob.getLookControl().setLookAt(look.x,look.y,look.z,15,25);
        if(mob.distanceToSqr(target)<=.36){mob.getNavigation().stop();mob.setActivity(mob.kind==Kind.MARUSYA?Activity.SIT:mob.kind==Kind.SYUSYA?Activity.SEEK_PLANT:Activity.CURIOUS);}
        else {mob.setActivity(Activity.CURIOUS);if(mob.tickCount%10==0){var path=mob.getNavigation().createPath(scene.spots().get(mob.getUUID()),0);if(!CampfireScenes.safePath(mob,path)){stop();return;}mob.getNavigation().moveTo(path,1);}}
        WalkScenes.friendship(mob,scene);
    }
    @Override public void stop(){WalkScenes.cancel(mob);FamilyFriendships.pause(mob);mob.getNavigation().stop();mob.setActivity(Activity.IDLE);scene=null;nextCheck=mob.level().getGameTime()+60;}
}
