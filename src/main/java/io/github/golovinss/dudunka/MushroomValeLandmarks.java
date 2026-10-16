package io.github.golovinss.dudunka;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/** Static voxel parts; geometry and collision share the generated cuboid definitions. */
public final class MushroomValeLandmarks {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 4);
    public static final Map<String, RegistryObject<Block>> BLOCKS = new LinkedHashMap<>();
    public static final RegistryObject<Block> DANCE_FLOWER = DudunkaMod.BLOCKS.register("dance_flower", ValeDanceFlower::new);
    public static final Map<String, RegistryObject<Block>> MUSIC_BELLS = new LinkedHashMap<>();
    static {
        for (String color : new String[]{"turquoise", "violet", "golden"})
            MUSIC_BELLS.put(color, DudunkaMod.BLOCKS.register(color+"_music_bell", ValeMusicBell::new));
        part("turquoise_landmark_cap", 10);
        part("violet_landmark_cap", 8);
        part("golden_landmark_cap", 9);
        part("vale_landmark_stem", 0);
    }
    private static void part(String id, int light) {
        RegistryObject<Block> block = DudunkaMod.BLOCKS.register(id, () -> new PartBlock(id,
                BlockBehaviour.Properties.of().strength(.2f).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> light > 0 && s.getValue(PROFILE) == 4 ? 15 : light)));
        BLOCKS.put(id, block);
        DudunkaMod.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register() {}
    private static final class PartBlock extends Block {
        private final VoxelShape[] shapes = new VoxelShape[5];
        PartBlock(String id, BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(PROFILE, 1));
            for (int profile = 0; profile < 5; profile++) {
                var shape = Shapes.empty();
                for (var b : MushroomValeLandmarkShapes.boxes(id, profile))
                    shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
                shapes[profile] = shape.optimize();
            }
        }
        // Open voxel gills and hanging bulbs must not absorb their own block light.
        @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PROFILE); }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes[state.getValue(PROFILE)];
        }
    }
    private MushroomValeLandmarks() {}
}
