package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class ChestCuriosityGoal extends Goal {
    private final Companion mob;
    private BlockPos chest,standing;
    public ChestCuriosityGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){
        chest=mob.openChestTarget();
        standing=chest==null?null:InterestTargets.approach(mob,chest);
        return standing!=null;
    }
    @Override public boolean canContinueToUse(){return chest!=null && chest.equals(mob.openChestTarget()) && standing!=null && HomeRules.safeStanding(mob.level(),standing,mob);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){mob.setActivity(Activity.CURIOUS);mob.getNavigation().stop();}
    @Override public void tick(){
        if(!canContinueToUse()){mob.getNavigation().stop();return;}
        Vec3 look=Vec3.atCenterOf(chest);
        mob.getLookControl().setLookAt(look.x,look.y,look.z,15,25);
        Vec3 target=Vec3.atBottomCenterOf(standing);
        if(mob.distanceToSqr(target)>.36){if(mob.tickCount%10==0)mob.getNavigation().moveTo(target.x,target.y,target.z,1);}
        else mob.getNavigation().stop();
    }
    @Override public void stop(){mob.getNavigation().stop();mob.setActivity(Activity.IDLE);chest=null;standing=null;}
}
