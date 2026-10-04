package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Walk first; six consecutive stalled seconds may permit a safe catch-up. */
public final class FamilyFollowGoal extends Goal {
    private final Companion mob;
    private final FamilyTravel.Counter counter=new FamilyTravel.Counter();
    private Player owner;
    private Vec3 previous;
    private long nextSample;
    public FamilyFollowGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    private boolean follows(){return owner!=null && owner.level()==mob.level() && owner.isAlive() && !owner.isSpectator()
        && !owner.isSleeping() && !mob.staying() && !mob.atHomeMode() && mob.willingToFollow() && mob.distanceToSqr(owner)<=4096;}
    @Override public boolean canUse(){owner=mob.ownerPlayer();return follows() && mob.distanceToSqr(owner)>9;}
    @Override public boolean canContinueToUse(){return follows() && mob.distanceToSqr(owner)>4;}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){counter.reset();previous=mob.position();nextSample=0;mob.setActivity(Activity.IDLE);}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}
        mob.getLookControl().setLookAt(owner,10,30);
        long now=mob.level().getGameTime();if(now<nextSample)return;nextSample=now+20;
        var path=mob.getNavigation().createPath(owner,0);boolean usable=FamilyTravel.safePath(mob,path);
        boolean stalled=!usable || previous!=null && previous.distanceToSqr(mob.position())<.0625;
        previous=mob.position();
        if(usable)mob.getNavigation().moveTo(path,1.1);else mob.getNavigation().stop();
        boolean ready=counter.sample(now,stalled && FamilyTravel.eligible(mob,owner));
        if(ready && FamilyTravel.teleportNearOwner(mob,owner)){counter.teleported(now);previous=mob.position();}
    }
    @Override public void stop(){counter.reset();mob.getNavigation().stop();owner=null;previous=null;nextSample=0;}
}
