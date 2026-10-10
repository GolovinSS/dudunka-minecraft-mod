package io.github.golovinss.dudunka;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.registries.RegistryObject;

/** Original static decorative models. No block entities; benches use transient mounts. */
public final class MushroomValeDecor {
    public static final Map<String, RegistryObject<Block>> BLOCKS = new LinkedHashMap<>();
    static {
        decor("vale_lantern", 15);
        decor("vale_bench", 0);
        decor("vale_signpost", 0);
        decor("vale_mushroom_table", 0);
        decor("vale_mushroom_stool", 0);
        decor("vale_planter", 4);
        decor("vale_cottage_window", 9);
        decor("vale_cottage_cap", 0);
        decor("vale_cottage_crown", 0);
        decor("vale_cottage_eave", 3);
        decor("vale_carved_wood", 0);
        decor("vale_book_stack", 0);
    }
    private static void decor(String id, int light) {
        RegistryObject<Block> block = DudunkaMod.BLOCKS.register(id, () -> new DecorBlock(id,
                BlockBehaviour.Properties.of().strength(.8f).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> light)));
        BLOCKS.put(id, block);
        DudunkaMod.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register() {}

    private static final class DecorBlock extends HorizontalDirectionalBlock {
        private final boolean bench;
        private final boolean lantern;
        private final VoxelShape[] shapes = new VoxelShape[4];
        DecorBlock(String id, BlockBehaviour.Properties properties) {
            super(properties);
            bench = id.equals("vale_bench");
            lantern = id.equals("vale_lantern");
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
            var boxes = MushroomValeDecorShapes.boxes(id);
            for (int turn = 0; turn < 4; turn++) {
                VoxelShape shape = Shapes.empty();
                for (var b : boxes) {
                    double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
                    for (int n = 0; n < turn; n++) {
                        double nextX0 = 16 - z1, nextX1 = 16 - z0;
                        z0 = x0; z1 = x1; x0 = nextX0; x1 = nextX1;
                    }
                    shape = Shapes.or(shape, Block.box(x0, b[1], z0, x1, b[4], z1));
                }
                shapes[turn] = shape.optimize();
            }
        }
        @Override public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level,
                BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                net.minecraft.world.phys.BlockHitResult hit) {
            return bench ? ValeBenchSeat.sit(level, pos, state, player) : net.minecraft.world.InteractionResult.PASS;
        }
        @Override public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                BlockState replacement, boolean moving) {
            if (bench && !state.is(replacement.getBlock())) ValeBenchSeat.removeAt(level, pos);
            super.onRemove(state, level, pos, replacement, moving);
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
        @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes[switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0;
            }];
        }
        // The ornamental frame is porous to block light; emission belongs to the
        // actual prop, so normal removal/chunk lighting owns its entire lifecycle.
        @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
            return lantern ? 0 : super.getLightBlock(state, level, pos);
        }
        @Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
    }
    private MushroomValeDecor() {}
}
