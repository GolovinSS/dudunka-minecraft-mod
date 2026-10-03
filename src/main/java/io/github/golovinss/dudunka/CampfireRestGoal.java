package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class CampfireRestGoal extends Goal {
    private final Companion mob;
    private CampfireScenes.Scene scene;
    private long nextCheck;
    public CampfireRestGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){
        long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;
        scene=CampfireScenes.select(mob);return scene!=null;
    }
    @Override public boolean canContinueToUse(){return CampfireScenes.active(mob,scene);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){mob.getNavigation().stop();mob.setActivity(Activity.CURIOUS);}
    @Override public void tick(){
        if(!canContinueToUse()){mob.getNavigation().stop();mob.setActivity(Activity.IDLE);return;}
        Vec3 target=Vec3.atBottomCenterOf(scene.seats().get(mob.getUUID()));
        Vec3 look=Vec3.atCenterOf(scene.fire());mob.getLookControl().setLookAt(look.x,look.y,look.z,10,25);
        if(mob.distanceToSqr(target)>.36){
            mob.setActivity(Activity.CURIOUS);
            if(mob.tickCount%10==0){
                var path=mob.getNavigation().createPath(scene.seats().get(mob.getUUID()),0);
                if(CampfireScenes.safePath(mob,path))mob.getNavigation().moveTo(path,1);
                else {mob.getNavigation().stop();scene=null;mob.setActivity(Activity.IDLE);}
            }
        }else{mob.getNavigation().stop();mob.setActivity(Activity.CAMP_REST);}
    }
    @Override public void stop(){mob.getNavigation().stop();mob.setActivity(Activity.IDLE);scene=null;nextCheck=mob.level().getGameTime()+40;}
}
