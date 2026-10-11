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
        var rotation = Rotation.values()[context.random().nextInt(Rotation.values().length)];
        if (placeBlueprint(level, context.origin(), context.config(), rotation)) return true;
        if (!isCottage(context.config())) return false;
        // Bounded local search, only in the selected chunk and original biome.
        // Every candidate still passes the complete, non-destructive preflight.
        var chunk = new net.minecraft.world.level.ChunkPos(context.origin());
        var biome = level.getBiome(context.origin());
        for (int i = 0; i < 15; i++) {
            int x = chunk.getMinBlockX() + context.random().nextInt(16);
            int z = chunk.getMinBlockZ() + context.random().nextInt(16);
            var candidate = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                    new BlockPos(x, context.origin().getY(), z));
            if (!level.getBiome(candidate).equals(biome)) continue;
            if (placeBlueprint(level, candidate, context.config(), rotation)) return true;
        }
        return false;
    }

    private static boolean isCottage(Blueprint config) {
        return config.radius() >= 4 && config.blocks().stream()
                .anyMatch(piece -> piece.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock);
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
        // Flat support is required under every ground-level blueprint piece.
        // Outside that footprint, a dry one-block rise/drop is walkable and is
        // left untouched. The full halo still excludes trees, builds and fluids.
        boolean standaloneBench = config.radius() == 1 && config.blocks().stream()
                .allMatch(piece -> piece.state().is(MushroomValeDecor.BLOCKS.get("vale_bench").get()));
        boolean fittedGround = standaloneBench || isCottage(config) || (config.radius() == 1 && config.height() == 4
                && config.blocks().stream().anyMatch(piece -> piece.state().is(MushroomValeDecor.BLOCKS.get("vale_lantern").get())));
        var supports = new java.util.HashSet<BlockPos>();
        for (var piece : config.blocks()) if (piece.offset().getY() == 0)
            supports.add(origin.offset(piece.offset().rotate(rotation)).below());
        // Keep a level approach to the half-height porch. A one-block exterior
        // drop here would otherwise create an unwalkable 1.5-block step.
        if (isCottage(config)) for (var piece : config.blocks())
            if (piece.offset().getY() == 0 && piece.state().getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                supports.add(origin.offset(piece.offset().rotate(rotation)).relative(rotation.rotate(net.minecraft.core.Direction.NORTH)).below());
        for (var p : BlockPos.betweenClosed(origin.offset(-extent, -1, -extent),
                origin.offset(extent, config.height() - 1, extent))) {
            if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p) || !level.ensureCanWrite(p)
                    || !level.getLevel().getWorldBorder().isWithinBounds(p) || !level.getFluidState(p).isEmpty()) return false;
            var state = level.getBlockState(p);
            if (p.getY() == origin.getY() - 1) {
                if (standaloneBench && !supports.contains(p)) continue;
                if (fittedGround && !supports.contains(p) && state.isAir()) {
                    var lower = p.below();
                    if (level.isOutsideBuildHeight(lower) || !level.hasChunkAt(lower) || !level.ensureCanWrite(lower)
                            || !soil(level.getBlockState(lower))
                            || !level.getBlockState(lower).isFaceSturdy(level, lower, net.minecraft.core.Direction.UP)) return false;
                } else if (!soil(state) || !state.isFaceSturdy(level, p, net.minecraft.core.Direction.UP)) return false;
            } else if (fittedGround && p.getY() == origin.getY() && !supports.contains(p.below()) && soil(state)) {
                if (!state.isFaceSturdy(level, p, net.minecraft.core.Direction.UP)) return false;
            } else if (!state.isAir() && !(state.getBlock() instanceof BushBlock)) return false;
        }
        for (var piece : config.blocks()) {
            var p = origin.offset(piece.offset().rotate(rotation));
            level.setBlock(p, piece.state().rotate(rotation), 2);
        }
        return true;
    }

    private static boolean soil(BlockState state) {
        return state.getFluidState().isEmpty() && (state.is(BlockTags.DIRT) || state.is(Blocks.MOSS_BLOCK));
    }
}
