package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

/** Explicitly assigned, loaded, owned furniture; no broad recurring scans or chunk loading. */
public final class FurnitureScenes {
    private FurnitureScenes(){}
    public enum State { NONE, OTHER_DIMENSION, UNLOADED, MISSING, HOME_UNAVAILABLE, TOO_FAR, BLOCKED, READY }
    public record Info(State state,BlockPos pos){}
    public record Scene(BlockPos home,BlockPos furniture,BlockPos approach,Vec3 rest,Vec3 leaf,Direction facing){}
    public static FurnitureEntity furniture(Companion mob){
        var p=mob.furniturePosition();
        if(p==null || !Objects.equals(mob.furnitureDimension(),mob.level().dimension().location().toString()) || !mob.level().hasChunkAt(p))return null;
        return mob.level().getBlockEntity(p) instanceof FurnitureEntity f && f.assigned(mob)?f:null;
    }
    public static Info info(Companion mob){
        var p=mob.furniturePosition();if(p==null)return new Info(State.NONE,BlockPos.ZERO);
        if(!Objects.equals(mob.furnitureDimension(),mob.level().dimension().location().toString()))return new Info(State.OTHER_DIMENSION,p);
        if(!mob.level().hasChunkAt(p))return new Info(State.UNLOADED,p);
        if(furniture(mob)==null)return new Info(State.MISSING,p);
        var home=mob.homeAnchor();if(home==null)return new Info(State.HOME_UNAVAILABLE,p);
        if(home.distSqr(p)>64)return new Info(State.TOO_FAR,p);
        return new Info(geometry(mob,home,p)==null?State.BLOCKED:State.READY,p);
    }
    private static Scene geometry(Companion mob,BlockPos home,BlockPos p){
        if(!mob.level().getWorldBorder().isWithinBounds(p) || !mob.level().hasChunkAt(p) || mob.level().canSeeSky(p.above()) || !mob.level().getFluidState(p).isEmpty())return null;
        var floor=mob.level().getBlockState(p.below());
        if(!floor.isSolid() || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || floor.is(net.minecraft.world.level.block.Blocks.CACTUS) || floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) || floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE) || !mob.level().getFluidState(p.above()).isEmpty())return null;
        var state=mob.level().getBlockState(p);if(!(state.getBlock() instanceof FurnitureBlock))return null;
        Direction facing=state.getValue(FurnitureBlock.FACING);BlockPos approach=p.relative(facing);
        if(!HomeRules.safeStanding(mob.level(),approach,mob) || mob.level().isRainingAt(approach))return null;
        // The cat stands on the cushion; the snail can really crawl into the rear opening.
        Vec3 rest=switch(mob.kind){
            case DUDUNKA -> Vec3.atBottomCenterOf(approach);
            case MARUSYA -> local(p,facing,6.0/16,4.0/16,9.0/16);
            case SYUSYA -> local(p,facing,.5,1.0/16,10.0/16);
        };
        if(mob.kind!=Kind.DUDUNKA && !safeRest(mob,rest))return null;
        Vec3 leaf=local(p,facing,.5,1.0/16,.25);
        if(mob.kind==Kind.SYUSYA && !safeRest(mob,leaf))return null;
        return new Scene(home,p,approach,rest,leaf,facing);
    }
    private static Vec3 local(BlockPos p,Direction facing,double x,double y,double z){
        int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        for(int i=0;i<turns;i++){double prev=x;x=1-z;z=prev;}
        return new Vec3(p.getX()+x,p.getY()+y,p.getZ()+z);
    }
    public static boolean safeRest(Companion mob,Vec3 v){
        var box=mob.getBoundingBox().move(v.subtract(mob.position()));
        return mob.level().getWorldBorder().isWithinBounds(box) && mob.level().hasChunkAt(BlockPos.containing(v)) && mob.level().noCollision(mob,box)
            && mob.level().getFluidState(BlockPos.containing(v)).isEmpty() && !mob.level().getBlockState(BlockPos.containing(v).below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }
    public static Scene select(Companion mob){
        if(!mob.atHomeMode() || mob.staying() || !mob.homeSceneReady() || mob.isInWaterOrBubble() || mob.isPassenger() || mob.isLeashed() || mob.homeWelcomePending())return null;
        var home=mob.homeAnchor();var f=furniture(mob);
        if(home==null || f==null || home.distSqr(f.getBlockPos())>64 || mob.distanceToSqr(Vec3.atCenterOf(home))>144)return null;
        var scene=geometry(mob,home,f.getBlockPos());
        if(scene==null || !HomeScenes.reachable(mob,Vec3.atBottomCenterOf(scene.approach())))return null;
        return scene;
    }
    public static boolean valid(Companion mob,Scene s){
        return s!=null && mob.isAlive() && mob.atHomeMode() && !mob.staying() && !mob.isInWaterOrBubble() && !mob.isPassenger() && !mob.isLeashed()
            && s.home().equals(mob.homeAnchor()) && s.furniture().equals(mob.furniturePosition()) && furniture(mob)!=null
            && s.equals(geometry(mob,s.home(),s.furniture()));
    }
    public static Activity activity(Kind kind,int tick){
        return switch(kind){
            case DUDUNKA -> tick<160?Activity.DRAW:Activity.SHOW_DRAWING;
            case MARUSYA -> tick<40?Activity.STRETCH:tick<120?Activity.SCRATCH:Activity.CURL;
            case SYUSYA -> tick<40?Activity.RETREAT:tick<120?Activity.SLEEP:tick<160?Activity.PEEK:Activity.NIBBLE;
        };
    }
}
