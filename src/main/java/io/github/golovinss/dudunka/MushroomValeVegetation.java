package io.github.golovinss.dudunka;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/** Decorative blocks only. Shapes, palettes and frequencies belong to the Vale datapack. */
public final class MushroomValeVegetation {
    public static final Map<String, RegistryObject<Block>> BLOCKS = new LinkedHashMap<>();

    static {
        cap("turquoise_mushroom_cap", MapColor.COLOR_CYAN, 10);
        cap("violet_mushroom_cap", MapColor.COLOR_PURPLE, 6);
        cap("golden_mushroom_cap", MapColor.COLOR_YELLOW, 9);
        cap("vale_mushroom_stem", MapColor.SAND, 0);
        plant("glowing_moss", MapColor.COLOR_CYAN, 6, 1);
        plant("cyan_magic_flower", MapColor.COLOR_CYAN, 5, 12);
        plant("pink_magic_flower", MapColor.COLOR_PINK, 3, 12);
        plant("violet_memory_flower", MapColor.COLOR_PURPLE, 4, 12);
        plant("spore_bush", MapColor.COLOR_PURPLE, 4, 9);
        plant("small_turquoise_mushroom", MapColor.COLOR_CYAN, 5, 6);
        plant("small_violet_mushroom", MapColor.COLOR_PURPLE, 3, 6);
        plant("small_golden_mushroom", MapColor.COLOR_YELLOW, 4, 6);
    }

    private static void cap(String id, MapColor color, int light) {
        add(id, () -> new HugeMushroomBlock(BlockBehaviour.Properties.of().mapColor(color)
                .strength(.2f).sound(SoundType.WOOD).lightLevel(s -> light)));
    }

    private static void plant(String id, MapColor color, int light, int height) {
        add(id, () -> new ValePlant(BlockBehaviour.Properties.of().mapColor(color).noCollission()
                .instabreak().sound(SoundType.GRASS).lightLevel(s -> light), height));
    }

    private static void add(String id, java.util.function.Supplier<Block> factory) {
        var block = DudunkaMod.BLOCKS.register(id, factory);
        BLOCKS.put(id, block);
        DudunkaMod.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register() { /* Initialize before attaching the existing deferred registers. */ }

    private static final class ValePlant extends BushBlock {
        private final VoxelShape shape;

        private ValePlant(BlockBehaviour.Properties properties, int height) {
            super(properties);
            shape = height == 1 ? Block.box(0, 0, 0, 16, 1, 16) : Block.box(2, 0, 2, 14, height, 14);
        }

        @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(BlockTags.DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.FARMLAND);
        }

        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }

        @Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            return type != PathComputationType.WATER;
        }
    }

    private MushroomValeVegetation() {}
}
