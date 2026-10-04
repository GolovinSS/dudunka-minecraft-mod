package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Walking and actual sitting are distinct: approaching never earns friendship. */
public final class HomeTogetherGoal extends Goal {
    private final Companion mob;private HomeTogetherScenes.Scene scene;private long nextCheck;
    public HomeTogetherGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;scene=HomeTogetherScenes.select(mob);return scene!=null;}
    @Override public boolean canContinueToUse(){return HomeTogetherScenes.active(mob,scene);}
    @Override public void start(){mob.getNavigation().stop();mob.setActivity(Activity.CURIOUS);}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}
        Vec3 target=Vec3.atBottomCenterOf(scene.seats().get(mob.getUUID()));
        Vec3 look=Vec3.atCenterOf(scene.focus());mob.getLookControl().setLookAt(look.x,look.y,look.z,10,25);
        if(mob.distanceToSqr(target)<=.36){mob.getNavigation().stop();mob.setActivity(Activity.SIT);}
        else {
            mob.setActivity(Activity.CURIOUS);
            if(mob.tickCount%10==0){
                var path=mob.getNavigation().createPath(scene.seats().get(mob.getUUID()),0);
                if(!CampfireScenes.safePath(mob,path)){stop();return;}
                mob.getNavigation().moveTo(path,1);
            }
        }
        FamilyFriendships.tickHome(mob,scene);
    }
    @Override public void stop(){FamilyFriendships.pause(mob);mob.pauseHomeScenes();mob.getNavigation().stop();mob.setActivity(Activity.IDLE);scene=null;nextCheck=mob.level().getGameTime()+200;}
}
