package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Bounded, all-or-nothing site preflight for small JSON-authored Vale exteriors. */
public final class MushroomValeDecorFeature extends Feature<MushroomValeDecorFeature.Blueprint> {
    public record Piece(BlockPos offset, BlockState state) {
        public static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Piece::offset),
                BlockState.CODEC.fieldOf("state").forGetter(Piece::state)).apply(i, Piece::new));
    }

    public record Blueprint(int radius, int height, List<Piece> blocks) implements FeatureConfiguration {
        public static final Codec<Blueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 6).fieldOf("radius").forGetter(Blueprint::radius),
                Codec.intRange(1, 10).fieldOf("height").forGetter(Blueprint::height),
                Piece.CODEC.listOf().fieldOf("blocks").forGetter(Blueprint::blocks)).apply(i, Blueprint::new));
    }

    public MushroomValeDecorFeature() { super(Blueprint.CODEC); }

    @Override public boolean place(FeaturePlaceContext<Blueprint> context) {
        var level = context.level();
        if (!level.getLevel().dimension().equals(MushroomVale.LEVEL)) return false;
        return placeBlueprint(level, context.origin(), context.config(),
                Rotation.values()[context.random().nextInt(Rotation.values().length)]);
    }

    /** Shared by worldgen and explicit opt-in previews; never prepares or clears terrain. */
    public static boolean placeBlueprint(net.minecraft.world.level.WorldGenLevel level, BlockPos origin,
                                         Blueprint config, Rotation rotation) {
        if (!level.getLevel().dimension().equals(MushroomVale.LEVEL)) return false;
        if (config.blocks().isEmpty() || config.blocks().size() > 1024) return false;
        var occupied = new java.util.HashSet<BlockPos>();
        for (var piece : config.blocks()) {
            var p = piece.offset();
            if (Math.abs(p.getX()) > config.radius() || Math.abs(p.getZ()) > config.radius()
                    || p.getY() < 0 || p.getY() >= config.height() || !occupied.add(p)) return false;
        }
        // A one-block empty halo keeps neighboring builds apart and leaves routes around them.
        // Never level hills, fill water, remove trees, force chunks or partially build a rejected site.
        int extent = config.radius() + 1;
        // A standalone bench only needs support beneath its three seats. Requiring
        // a perfectly flat 5x5 halo made these tiny props reject ordinary hills.
        boolean standaloneBench = config.radius() == 1 && config.blocks().stream()
                .allMatch(piece -> piece.state().is(MushroomValeDecor.BLOCKS.get("vale_bench").get()));
        for (var p : BlockPos.betweenClosed(origin.offset(-extent, -1, -extent),
                origin.offset(extent, config.height() - 1, extent))) {
            if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p) || !level.ensureCanWrite(p)
                    || !level.getLevel().getWorldBorder().isWithinBounds(p) || !level.getFluidState(p).isEmpty()) return false;
            var state = level.getBlockState(p);
            if (p.getY() == origin.getY() - 1) {
                if (standaloneBench && config.blocks().stream().noneMatch(piece ->
                        origin.offset(piece.offset().rotate(rotation)).below().equals(p))) continue;
                if (!state.is(BlockTags.DIRT) && !state.is(Blocks.MOSS_BLOCK)) return false;
                if (!state.isFaceSturdy(level, p, net.minecraft.core.Direction.UP)) return false;
            } else if (!state.isAir() && !(state.getBlock() instanceof BushBlock)) return false;
        }
        for (var piece : config.blocks()) {
            var p = origin.offset(piece.offset().rotate(rotation));
            level.setBlock(p, piece.state().rotate(rotation), 2);
        }
        return true;
    }
}
