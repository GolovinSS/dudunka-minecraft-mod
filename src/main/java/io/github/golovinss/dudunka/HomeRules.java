package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Small loaded-area checks only; no house flood fill or chunk tickets. */
public final class HomeRules {
    private HomeRules() {}
    public record Conditions(boolean roof, boolean bed, boolean chest, boolean light, boolean food) {
        public boolean ready() { return roof && bed && chest && light && food; }
    }
    public static Conditions inspect(Level level, BlockPos anchor) {
        boolean roof = level.hasChunkAt(anchor) && !level.canSeeSky(anchor.above());
        boolean bed = false, chest = false, light = false, food = false;
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-4,-1,-4), anchor.offset(4,3,4))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState state = level.getBlockState(p);
            bed |= state.is(BlockTags.BEDS);
            chest |= state.getBlock() instanceof ChestBlock || state.is(Blocks.BARREL);
            light |= state.getLightEmission(level,p) > 0;
            food |= state.is(Blocks.CAKE);
        }
        return new Conditions(roof,bed,chest,light,food);
    }
    public static boolean safeStanding(Level level, BlockPos p, Companion mob) {
        if (!level.hasChunkAt(p) || p.getY() < level.getMinBuildHeight() + 1 || p.getY() + mob.getBbHeight() >= level.getMaxBuildHeight()) return false;
        BlockState floor = level.getBlockState(p.below());
        if (!floor.isSolid() || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || !level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) return false;
        var box = mob.getBoundingBox().move(p.getX()+.5-mob.getX(),p.getY()-mob.getY(),p.getZ()+.5-mob.getZ());
        return level.noCollision(mob,box);
    }
    public static BlockPos nearbyStanding(Level level, BlockPos anchor, Companion mob) {
        for (int dy=0;dy<=2;dy++) for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) {
            BlockPos p = anchor.offset(dx,dy,dz);
            if (safeStanding(level,p,mob)) return p;
        }
        return null;
    }
}
