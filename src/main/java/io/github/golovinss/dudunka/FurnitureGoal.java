package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Walk before acting; props produce no items and never affect care or friendship scores. */
public final class FurnitureGoal extends Goal {
    private final Companion mob;private FurnitureScenes.Scene scene;private int acted,elapsed,sharedTicks;private boolean approached;private long nextCheck;
    public FurnitureGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;if(FurnitureVisits.waitForHost(mob))return false;scene=FurnitureScenes.select(mob);return scene!=null;}
    @Override public void start(){int variant=mob.beginFurnitureVariant();
        if(mob.kind==Kind.DUDUNKA){var state=mob.level().getBlockState(scene.furniture());mob.level().setBlock(scene.furniture(),state.setValue(FurnitureBlock.PICTURE,variant),3);}
        acted=elapsed=sharedTicks=0;approached=false;mob.reserveHomeScene(scene.furniture());mob.getNavigation().stop();mob.setActivity(Activity.CURIOUS);}
    @Override public boolean canContinueToUse(){return elapsed<600 && acted<240 && FurnitureScenes.valid(mob,scene)
        && (mob.kind!=Kind.MARUSYA || mob.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,mob.getBoundingBox().inflate(10),e->e.isAlive()).isEmpty());}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}elapsed++;
        Vec3 front=Vec3.atBottomCenterOf(scene.approach());
        if(!approached){
            if(mob.distanceToSqr(front)>.09){
                // Navigation can finish within its waypoint tolerance before the activity threshold.
                // Complete only a nearby, collision-checked approach; never move through obstacles.
                if(mob.distanceToSqr(front)<=1 && mob.getNavigation().isDone()){
                    Vec3 delta=front.subtract(mob.position());
                    Vec3 step=delta.scale(Math.min(.04,delta.length())/delta.length());
                    if(FurnitureScenes.safeRest(mob,mob.position().add(step))){mob.setActivity(Activity.CURIOUS);mob.setDeltaMovement(Vec3.ZERO);mob.move(net.minecraft.world.entity.MoverType.SELF,step);return;}
                }
                walk(scene.approach());return;
            }
            approached=true;mob.getNavigation().stop();
        }
        Activity activity=FurnitureScenes.activity(mob.kind,acted);
        Vec3 destination=mob.kind==Kind.MARUSYA && acted>=120 || mob.kind==Kind.SYUSYA && acted<160?scene.rest():mob.kind==Kind.SYUSYA?scene.leaf():front;
        if(mob.kind!=Kind.DUDUNKA)destination=destination.add(0,.003,0);
        if(mob.distanceToSqr(destination)>.0064){
            // Only after reaching the front: a slow, collision-checked step onto/into the furniture.
            Vec3 delta=destination.subtract(mob.position());double len=delta.length();
            Vec3 step=destination.y>mob.getY()+.0001?new Vec3(0,Math.min(.06,destination.y-mob.getY()),0):delta.scale(Math.min(.06,len)/len);
            if(step.y>.02)step=new Vec3(step.x,Math.min(.06,step.y),step.z);
            Vec3 next=mob.position().add(step);
            if(!FurnitureScenes.safeRest(mob,next)){stop();return;}
            mob.getNavigation().stop();mob.setActivity(Activity.CURIOUS);mob.setDeltaMovement(Vec3.ZERO);mob.move(net.minecraft.world.entity.MoverType.SELF,step);
            return;
        }
        mob.getNavigation().stop();mob.setActivity(activity);
        var look=Vec3.atCenterOf(scene.furniture());mob.getLookControl().setLookAt(look.x,look.y,look.z,20,25);
        mob.setYRot(scene.facing().getOpposite().toYRot());mob.setYBodyRot(mob.getYRot());
        if(mob.kind==Kind.MARUSYA && acted==120 && mob.level() instanceof net.minecraft.server.level.ServerLevel l)l.playSound(null,mob.blockPosition(),net.minecraft.sounds.SoundEvents.CAT_PURR,net.minecraft.sounds.SoundSource.NEUTRAL,.35f,1f);
        var visit=activity==FurnitureVisits.occasion(mob.kind)?FurnitureVisits.offer(mob,scene):null;
        if(visit!=null){
            FurnitureVisits.tickFriendship(mob,visit);
            // Face the guests rather than the table while presenting a drawing.
            if(mob.kind==Kind.DUDUNKA){mob.setYRot(scene.facing().toYRot());mob.setYBodyRot(mob.getYRot());var frontLook=front.add(scene.facing().getStepX()*2,mob.getBbHeight(),scene.facing().getStepZ()*2);mob.getLookControl().setLookAt(frontLook.x,frontLook.y,frontLook.z,30,25);}
            if(sharedTicks++<240)return;
        }
        acted++;
    }
    private void walk(net.minecraft.core.BlockPos target){
        mob.setActivity(Activity.CURIOUS);if(mob.tickCount%10!=0)return;
        var path=mob.getNavigation().createPath(target,0);if(!CampfireScenes.safePath(mob,path)){stop();return;}mob.getNavigation().moveTo(path,1);
    }
    @Override public void stop(){FurnitureVisits.cancelHost(mob);mob.getNavigation().stop();mob.reserveHomeScene(null);mob.setActivity(Activity.IDLE);mob.pauseHomeScenes();scene=null;nextCheck=mob.level().getGameTime()+200;}
}
