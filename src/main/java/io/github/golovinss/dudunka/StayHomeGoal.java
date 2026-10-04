package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.BlockPos;

/** Home mode walks back inside the home radius; a missing home never causes a teleport. */
public final class StayHomeGoal extends Goal {
    private final Companion mob;private BlockPos target;
    public StayHomeGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
    @Override public boolean canUse(){if(!mob.atHomeMode() || mob.staying())return false;target=mob.homePosition();return target!=null && mob.blockPosition().distSqr(target)>36;}
    @Override public boolean canContinueToUse(){return mob.atHomeMode() && !mob.staying() && mob.homePosition()!=null && mob.blockPosition().distSqr(target)>9;}
    @Override public void tick(){if(!canContinueToUse()){stop();return;}if(mob.tickCount%20!=0)return;var path=mob.getNavigation().createPath(target,0);if(CampfireScenes.safePath(mob,path))mob.getNavigation().moveTo(path,1);else mob.getNavigation().stop();}
    @Override public void stop(){mob.getNavigation().stop();target=null;}
}
