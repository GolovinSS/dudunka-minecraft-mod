package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One egg per attempt; only probes the selected column and already available neighbors. */
public final class SnailEggFeature extends Feature<NoneFeatureConfiguration> {
    public SnailEggFeature(){super(NoneFeatureConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(!DudunkaMod.NATURAL_EGGS.get())return false;
        WorldGenLevel level=context.level();
        for(int down=0;down<24;down++) {
            BlockPos p=context.origin().below(down);
            if(p.getY()<=level.getMinBuildHeight() || p.getY()+1>=level.getMaxBuildHeight()
                    || !level.hasChunkAt(p) || !level.hasChunkAt(p.below()) || !level.isEmptyBlock(p))return false;
            var floor=level.getBlockState(p.below());
            if(!(floor.is(Blocks.MOSS_BLOCK)||floor.is(Blocks.GRASS_BLOCK)))continue;
            if(!level.isEmptyBlock(p.above()) || !habitatReady(level,p) || !level.ensureCanWrite(p))return false;
            return level.setBlock(p,DudunkaMod.EGGS.get(Kind.SYUSYA).get().defaultBlockState(),2);
        }
        return false;
    }
    private static boolean habitatReady(WorldGenLevel level,BlockPos pos) {
        boolean water=false;
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,2,2))) {
            if(!level.hasChunkAt(p))continue;
            var fluid=level.getFluidState(p);
            if(fluid.is(FluidTags.LAVA) || level.getBlockState(p).getBlock() instanceof EggBlock)return false;
            water|=fluid.is(FluidTags.WATER);
        }
        return water;
    }
}
