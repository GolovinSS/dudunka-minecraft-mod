package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/** Short home scene, interrupted by commands, reactive goals or a changing house. */
public final class HomeSceneGoal extends Goal {
    private final Companion mob;
    private HomeScenes.Scene scene;
    private long nextCheck,until;
    public HomeSceneGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){
        long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;
        if(HomeTogetherScenes.select(mob)!=null)return false;
        scene=HomeScenes.select(mob);return scene!=null;
    }
    @Override public void start(){until=mob.level().getGameTime()+(scene.activity()==Activity.WAVE?80:200);mob.reserveHomeScene(scene.focus());
        mob.getNavigation().stop();
        if(scene.activity()==Activity.WAVE){mob.beginHomeWelcome();mob.setActivity(Activity.WAVE);}else mob.setActivity(Activity.CURIOUS);}
    @Override public boolean canContinueToUse(){
        return mob.level().getGameTime()<until && HomeScenes.valid(mob,scene) && mob.openChestTarget()==null
            && CampfireScenes.select(mob)==null && !mob.level().isRainingAt(mob.blockPosition())
            && (mob.kind!=Kind.MARUSYA || mob.level().getEntitiesOfClass(Monster.class,mob.getBoundingBox().inflate(10),e->e.isAlive()).isEmpty());
    }
    @Override public void tick(){
        // Recheck even when GoalSelector gives one final tick after invalidation.
        if(!canContinueToUse()){stop();return;}
        if(scene.activity()==Activity.WAVE){mob.getNavigation().stop();mob.getLookControl().setLookAt(mob.ownerPlayer(),15,25);mob.setActivity(Activity.WAVE);return;}
        Vec3 look=Vec3.atCenterOf(scene.focus());mob.getLookControl().setLookAt(look.x,look.y,look.z,15,25);
        if(mob.distanceToSqr(scene.destination())<=.36){
            mob.getNavigation().stop();mob.setActivity(scene.activity());
            if(mob.kind==Kind.MARUSYA)FamilyAchievements.award(mob,"occupied_bed");
        } else {
            mob.setActivity(Activity.CURIOUS);
            if(mob.tickCount%10==0){
                var path=mob.getNavigation().createPath(net.minecraft.core.BlockPos.containing(scene.destination()),0);
                if(!CampfireScenes.safePath(mob,path)){stop();return;}
                // The coordinate overload uses accuracy=1 and can stop a whole block early.
                mob.getNavigation().moveTo(path,1);
            }
        }
    }
    @Override public void stop(){mob.endHomeWelcome();mob.pauseHomeScenes();mob.reserveHomeScene(null);scene=null;until=0;nextCheck=mob.level().getGameTime()+200;mob.getNavigation().stop();mob.setActivity(Activity.IDLE);}
}
