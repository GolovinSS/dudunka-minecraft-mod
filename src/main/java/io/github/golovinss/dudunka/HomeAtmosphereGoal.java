package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Approach first, then greet or enjoy the rain. Original modes, homes and furniture stay bound. */
public final class HomeAtmosphereGoal extends Goal {
    private final Companion mob;private HomeAtmosphereScenes.Scene scene;private long nextCheck;private boolean greeted;
    public HomeAtmosphereGoal(Companion mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){long now=mob.level().getGameTime();if(now<nextCheck)return false;nextCheck=now+20;scene=HomeAtmosphereScenes.select(mob);return scene!=null;}
    @Override public boolean canContinueToUse(){return HomeAtmosphereScenes.active(mob,scene);}
    @Override public void start(){greeted=false;mob.getNavigation().stop();mob.reserveHomeScene(scene.spots().get(mob.getUUID()));if(scene.occasion()==HomeAtmosphereScenes.Occasion.WELCOME)mob.beginHomeWelcome();mob.setActivity(Activity.CURIOUS);}
    @Override public void tick(){
        if(!canContinueToUse()){stop();return;}
        var target=Vec3.atBottomCenterOf(scene.spots().get(mob.getUUID()));
        if(scene.occasion()==HomeAtmosphereScenes.Occasion.WELCOME)mob.getLookControl().setLookAt(mob.ownerPlayer(),20,25);
        else {var focus=Vec3.atCenterOf(scene.focuses().get(mob.getUUID()));mob.getLookControl().setLookAt(focus.x,focus.y,focus.z,20,25);}
        if(mob.distanceToSqr(target)<=.16){
            mob.getNavigation().stop();HomeAtmosphereScenes.arrived(mob,scene);mob.setActivity(HomeAtmosphereScenes.activity(mob,scene));
            if(!greeted){greeted=true;if(scene.occasion()==HomeAtmosphereScenes.Occasion.WELCOME && mob.kind==Kind.MARUSYA && mob.trust()>=25 && mob.level() instanceof net.minecraft.server.level.ServerLevel l)l.playSound(null,mob.blockPosition(),net.minecraft.sounds.SoundEvents.CAT_PURR,net.minecraft.sounds.SoundSource.NEUTRAL,.35f,1f);}
            HomeAtmosphereScenes.friendship(mob,scene);return;
        }
        mob.setActivity(Activity.CURIOUS);FamilyFriendships.pause(mob);
        if(mob.getNavigation().isDone() && mob.distanceToSqr(target)<=1){var delta=target.subtract(mob.position());var step=delta.scale(Math.min(mob.kind==Kind.SYUSYA?.02:.04,delta.length())/delta.length());
            if(FurnitureScenes.safeRest(mob,mob.position().add(step))){mob.setDeltaMovement(Vec3.ZERO);mob.move(MoverType.SELF,step);return;}}
        if(mob.tickCount%10==0){var path=mob.getNavigation().createPath(scene.spots().get(mob.getUUID()),0);if(!HomeAtmosphereScenes.safePath(mob,path)){stop();return;}mob.getNavigation().moveTo(path,1);}
    }
    @Override public void stop(){HomeAtmosphereScenes.cancel(mob);FamilyFriendships.pause(mob);mob.endHomeWelcome();mob.reserveHomeScene(null);mob.getNavigation().stop();mob.setActivity(Activity.IDLE);mob.pauseHomeScenes();scene=null;nextCheck=mob.level().getGameTime()+200;}
}
