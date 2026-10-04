package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Same-world loaded-area catch-up only. Never adds tickets or changes ownership/home. */
public final class FamilyTravel {
    private FamilyTravel() {}
    public static final class Counter {
        private long last=Long.MIN_VALUE,nextTeleport;
        private int blockedTicks;
        public void reset(){last=Long.MIN_VALUE;blockedTicks=0;}
        public boolean sample(long now,boolean blocked){
            if(now==last)return false;
            if(last==Long.MIN_VALUE || now-last!=20){reset();last=now;return false;}
            last=now;blockedTicks=blocked?Math.min(120,blockedTicks+20):0;
            return blockedTicks>=120 && now>=nextTeleport;
        }
        public void teleported(long now){blockedTicks=0;nextTeleport=now+400;}
    }
    public static boolean safePath(Companion mob,net.minecraft.world.level.pathfinder.Path path) {
        if(path==null || path.getNodeCount()==0)return false;
        for(int i=0;i<path.getNodeCount();i++) {
            var p=path.getNode(i).asBlockPos();
            if(!mob.level().hasChunkAt(p) || hazard(mob,p) || hazard(mob,p.below()))return false;
        }
        return true;
    }
    public static boolean eligible(Companion mob,Player owner){
        return !mob.level().isClientSide && DudunkaMod.FAMILY_CATCH_UP.get() && owner!=null && owner.level()==mob.level()
            && mob.ownerId()!=null && mob.ownerId().equals(owner.getUUID()) && mob.isAlive() && owner.isAlive()
            && !owner.isSpectator() && !owner.isSleeping() && owner.onGround() && !owner.isPassenger() && !owner.isFallFlying()
            && !owner.isInWaterOrBubble() && !owner.isInLava() && !mob.staying() && mob.willingToFollow()
            && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle() && mob.activity()==Activity.IDLE
            && mob.distanceToSqr(owner)>=144 && mob.distanceToSqr(owner)<=4096
            && mob.level().hasChunkAt(mob.blockPosition()) && mob.level().hasChunkAt(owner.blockPosition());
    }
    private static boolean hazard(Companion mob,BlockPos pos){
        var state=mob.level().getBlockState(pos);
        var reported=state.getBlockPathType(mob.level(),pos,mob);
        if(reported!=null && reported.getMalus()!=0)return true;
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || !mob.level().getFluidState(pos).isEmpty();
    }
    public static boolean safeDestination(Companion mob,BlockPos pos){
        if(!HomeRules.safeStanding(mob.level(),pos,mob) || !mob.level().getWorldBorder().isWithinBounds(pos)
            || mob.level().getBlockState(pos.below()).is(BlockTags.LEAVES))return false;
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1)))
            if(!mob.level().hasChunkAt(p) || hazard(mob,p))return false;
        var box=mob.getBoundingBox().move(pos.getX()+.5-mob.getX(),pos.getY()-mob.getY(),pos.getZ()+.5-mob.getZ());
        return mob.level().getEntities(mob,box,e->e.isAlive() && (e instanceof LivingEntity || e.isVehicle())).isEmpty();
    }
    public static BlockPos destination(Companion mob,Player owner){
        if(!eligible(mob,owner))return null;
        BlockPos center=owner.blockPosition();
        for(int dy:new int[]{0,1,-1})for(int radius=2;radius<=3;radius++)
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                BlockPos pos=center.offset(dx,dy,dz);if(safeDestination(mob,pos))return pos;
            }
        return null;
    }
    public static boolean teleportNearOwner(Companion mob,Player owner){
        BlockPos pos=destination(mob,owner);if(pos==null)return false;
        // Revalidate just before movement, including other family members already placed this tick.
        if(!eligible(mob,owner) || !safeDestination(mob,pos))return false;
        mob.getNavigation().stop();mob.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;return true;
    }
}
