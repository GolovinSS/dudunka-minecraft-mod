package io.github.golovinss.dudunka;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Root-supported canopy: hills below the cap need not be flattened or filled. */
public final class MushroomValeLandmarkFeature extends Feature<MushroomValeDecorFeature.Blueprint> {
    public MushroomValeLandmarkFeature() { super(MushroomValeDecorFeature.Blueprint.CODEC); }
    @Override public boolean place(FeaturePlaceContext<MushroomValeDecorFeature.Blueprint> context) {
        var level = context.level();
        var origin = context.origin();
        var config = context.config();
        if (config.blocks().isEmpty() || config.blocks().size() > 1024) return false;
        var positions = new HashSet<BlockPos>();
        for (var piece : config.blocks()) {
            var p = piece.offset();
            if (Math.abs(p.getX()) > config.radius() || Math.abs(p.getZ()) > config.radius()
                    || p.getY() < 0 || p.getY() >= config.height() || !positions.add(p)) return false;
        }
        var floor = level.getBlockState(origin.below());
        if ((!floor.is(BlockTags.DIRT) && !floor.is(Blocks.MOSS_BLOCK))
                || !floor.isFaceSturdy(level, origin.below(), Direction.UP)) return false;
        // Keep canopy/headroom and all actual pieces clear. Low dirt in unused
        // cells is a natural hillside, not a canopy obstruction. Never remove it.
        // Trunks/builds/fluids still reject the entire site, including the halo.
        int extent = config.radius() + 1;
        for (var p : BlockPos.betweenClosed(origin.offset(-extent, 0, -extent),
                origin.offset(extent, config.height(), extent))) {
            if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p) || !level.ensureCanWrite(p)
                    || !level.getLevel().getWorldBorder().isWithinBounds(p) || !level.getFluidState(p).isEmpty()) return false;
            var state = level.getBlockState(p);
            if (!state.isAir() && !(state.getBlock() instanceof BushBlock)) {
                var offset = p.subtract(origin);
                boolean lowSlope = p.getY() <= origin.getY() + 1 && !positions.contains(offset)
                        && (state.is(BlockTags.DIRT) || state.is(Blocks.MOSS_BLOCK));
                if (!lowSlope) return false;
            }
        }
        for (var piece : config.blocks()) level.setBlock(origin.offset(piece.offset()), piece.state(), 2);
        return true;
    }
}
