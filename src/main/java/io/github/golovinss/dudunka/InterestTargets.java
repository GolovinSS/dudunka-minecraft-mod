package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;

/** Reachable standing positions next to an interest, checked only when selecting it. */
public final class InterestTargets {
    private InterestTargets() {}
    public static BlockPos approach(Companion mob,BlockPos interest) {
        BlockPos best=null;double nearest=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(interest.offset(-1,0,-1),interest.offset(1,1,1))) {
            if(p.equals(interest) || !HomeRules.safeStanding(mob.level(),p,mob))continue;
            var path=mob.getNavigation().createPath(p,0);
            if(path==null || !path.canReach())continue;
            double distance=mob.blockPosition().distSqr(p);
            if(distance<nearest){nearest=distance;best=p.immutable();}
        }
        return best;
    }
}
