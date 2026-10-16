package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Four static quest blooms. No block entities, ticking, random offsets or ambient particles. */
public final class ValeDanceFlower extends BushBlock {
    public static final IntegerProperty BLOOM = IntegerProperty.create("bloom",0,3);
    private static final VoxelShape[] SHAPES = {
        Block.box(2,0,2,14,14,14), Block.box(3,0,3,13,16,13),
        Block.box(1,0,1,15,11,15), Block.box(2,0,2,14,13,14)
    };
    public ValeDanceFlower() {
        super(Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.GRASS).lightLevel(s->10));
        registerDefaultState(stateDefinition.any().setValue(BLOOM,0));
    }
    @Override public int getLightBlock(BlockState state,BlockGetter level,BlockPos pos) { return 0; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(BLOOM);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return SHAPES[state.getValue(BLOOM)];
    }
}
