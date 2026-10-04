package io.github.golovinss.dudunka;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/** Bounded, loaded home interests; neither furniture nor player sleep is modified. */
public final class HomeScenes {
    private HomeScenes() {}
    public record Scene(BlockPos anchor, BlockPos focus, Vec3 destination, Activity activity) {}
    public static boolean evening(Companion mob) {
        long time=Math.floorMod(mob.level().getDayTime(),24000);
        return time>=12000 && time<23000;
    }
    private static boolean atHome(Companion mob,BlockPos anchor) {
        var owner=mob.ownerPlayer();
        return !mob.staying() && !mob.isInWaterOrBubble() && !mob.level().isRainingAt(mob.blockPosition()) && anchor!=null && owner!=null && owner.isAlive()
            && !owner.isSpectator() && !owner.isSleeping()
            && owner.distanceToSqr(Vec3.atCenterOf(anchor))<=64
            && mob.distanceToSqr(Vec3.atCenterOf(anchor))<=100;
    }
    public static Scene select(Companion mob) {
        if(!mob.homeSceneReady())return null;
        BlockPos anchor=mob.homeAnchor();
        if(!atHome(mob,anchor) || (mob.kind==Kind.MARUSYA && !evening(mob)))return null;
        if(mob.kind==Kind.DUDUNKA && mob.homeWelcomePending() && mob.trust()>=25
            && mob.distanceToSqr(mob.ownerPlayer())<=36 && mob.hasLineOfSight(mob.ownerPlayer())
            && HomeRules.safeStanding(mob.level(),mob.blockPosition(),mob))
            return new Scene(anchor,anchor,mob.position(),Activity.WAVE);
        var candidates=new java.util.ArrayList<BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(anchor.offset(-4,-1,-4),anchor.offset(4,3,4)))
            if(mob.level().hasChunkAt(p) && interest(mob,p))candidates.add(p.immutable());
        candidates.sort(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p))));
        for(BlockPos p:candidates.subList(0,Math.min(8,candidates.size()))) {
            if(mob.kind==Kind.MARUSYA) {
                Vec3 destination=new Vec3(p.getX()+.5,p.getY()+.5625,p.getZ()+.5);
                var scene=new Scene(anchor,p,destination,Activity.SLEEP);
                if(valid(mob,scene) && reachable(mob,destination))return scene;
            } else {
                BlockPos approach=InterestTargets.approach(mob,p);
                if(approach!=null) {
                    var scene=new Scene(anchor,p,Vec3.atBottomCenterOf(approach),mob.kind==Kind.SYUSYA?Activity.SEEK_PLANT:Activity.SIT);
                    if(valid(mob,scene))return scene;
                }
            }
        }
        return null;
    }
    private static boolean interest(Companion mob,BlockPos p) {
        var state=mob.level().getBlockState(p);
        if(mob.kind==Kind.MARUSYA)return state.getBlock() instanceof BedBlock
            && state.getValue(BedBlock.PART)==BedPart.HEAD && !state.getValue(BedBlock.OCCUPIED);
        return state.is(BlockTags.FLOWERS) || (mob.kind==Kind.SYUSYA && (
            state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.AZALEA)));
    }
    public static boolean reachable(Companion mob,Vec3 destination) {
        if(mob.distanceToSqr(destination)<=.09)return true;
        return CampfireScenes.safePath(mob,mob.getNavigation().createPath(BlockPos.containing(destination),0));
    }
    public static boolean valid(Companion mob,Scene scene) {
        if(scene==null || !scene.anchor().equals(mob.homeAnchor()) || !atHome(mob,scene.anchor())
            || !mob.level().hasChunkAt(scene.focus()) || (scene.activity()!=Activity.WAVE && !interest(mob,scene.focus()))
            || mob.level().canSeeSky(scene.focus().above()))return false;
        if(scene.activity()==Activity.WAVE)return mob.kind==Kind.DUDUNKA && mob.trust()>=25
            && mob.distanceToSqr(mob.ownerPlayer())<=36 && mob.hasLineOfSight(mob.ownerPlayer())
            && HomeRules.safeStanding(mob.level(),mob.blockPosition(),mob);
        if(mob.kind==Kind.MARUSYA) {
            if(!evening(mob))return false;
            var state=mob.level().getBlockState(scene.focus());
            BlockPos foot=scene.focus().relative(state.getValue(BedBlock.FACING).getOpposite());
            if(!mob.level().hasChunkAt(foot))return false;
            var other=mob.level().getBlockState(foot);
            if(other.getBlock()!=state.getBlock() || other.getValue(BedBlock.PART)!=BedPart.FOOT
                || other.getValue(BedBlock.FACING)!=state.getValue(BedBlock.FACING) || other.getValue(BedBlock.OCCUPIED))return false;
            if(!mob.level().getEntitiesOfClass(Companion.class,new net.minecraft.world.phys.AABB(scene.focus()).inflate(20),
                c->c!=mob && c.isAlive() && scene.focus().equals(c.homeSceneTarget())).isEmpty())return false;
            var box=mob.getBoundingBox().move(scene.destination().subtract(mob.position()));
            return mob.level().noCollision(mob,box) && mob.level().getFluidState(scene.focus()).isEmpty();
        }
        return HomeRules.safeStanding(mob.level(),BlockPos.containing(scene.destination()),mob);
    }
}
