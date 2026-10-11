package io.github.golovinss.dudunka;

import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.joml.Vector3f;

/** Twelve reserved cells: three wide, four high, facing north/south. Only the bottom centre owns data. */
public final class FamilyGatewayBlock extends BaseEntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 11);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final int ROOT = 1;

    public FamilyGatewayBlock() {
        super(Properties.of().strength(2, 1200).sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(ACTIVE) ? 12 : 2));
        registerDefaultState(stateDefinition.any().setValue(PART, ROOT).setValue(ACTIVE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(PART, ACTIVE); }
    public static BlockPos cell(BlockPos root, int part) { return root.offset(part % 3 - 1, part / 3, 0); }
    public static BlockPos root(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.offset(1 - part % 3, -part / 3, 0);
    }
    public static boolean complete(Level level, BlockPos root) {
        for (int part = 0; part < 12; part++) {
            var p = cell(root, part);
            if (!level.hasChunkAt(p)) return false;
            var state = level.getBlockState(p);
            if (!state.is(DudunkaMod.FAMILY_GATEWAY.get()) || state.getValue(PART) != part) return false;
        }
        return true;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == ROOT ? new FamilyGatewayEntity(pos, state) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DudunkaMod.FAMILY_GATEWAY_BE.get(), FamilyGatewayEntity::tick);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var collision = getCollisionShape(state, level, pos, context);
        return collision.isEmpty() ? box(0, 0, 7, 16, 16, 9) : collision;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = state.getValue(PART), y = part / 3;
        if (y == 0) {
            var foot = box(0, 0, 3, 16, 4, 13);
            return part % 3 == 1
                    ? Shapes.or(foot, box(5, 4, 5, 11, 7, 11), box(6, 7, 6, 10, 11, 10))
                    : Shapes.or(foot, box(5, 4, 6, 11, 13, 10), box(2, 11, 3, 14, 14, 13), box(4, 14, 5, 12, 16, 11));
        }
        if (y == 3) return box(0, 0, 2, 16, 11, 14);
        if (part % 3 == 1) return Shapes.empty();
        var stem = box(4, 0, 5, 12, 16, 11);
        return y == 2 ? Shapes.or(stem, box(1, 13, 3, 15, 16, 13)) : stem;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                            InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(root(pos, state)) instanceof FamilyGatewayEntity gateway) gateway.interact(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && !level.restoringBlockSnapshots) {
            var root = root(pos, state);
            if (state.getValue(PART) == ROOT) {
                if (level.getBlockEntity(pos) instanceof FamilyGatewayEntity gateway) gateway.cancel();
                for (int part = 0; part < 12; part++) if (part != ROOT) {
                    var p = cell(root, part);
                    if (level.hasChunkAt(p) && level.getBlockState(p).is(this)
                            && level.getBlockState(p).getValue(PART) == part) level.removeBlock(p, false);
                }
            } else if (level.hasChunkAt(root) && level.getBlockState(root).is(this)
                    && level.getBlockState(root).getValue(PART) == ROOT) level.destroyBlock(root, true);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(DudunkaMod.FAMILY_GATEWAY_ITEM.get());
    }
    @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != ROOT || !state.getValue(ACTIVE)) return;
        var color = random.nextBoolean() ? new Vector3f(.3f, .95f, .85f) : new Vector3f(.9f, .45f, .85f);
        level.addParticle(new DustParticleOptions(color, 1), pos.getX() - .7 + random.nextDouble() * 2.4,
                pos.getY() + .5 + random.nextDouble() * 2.7, pos.getZ() + .5, 0, .025, 0);
    }
}
