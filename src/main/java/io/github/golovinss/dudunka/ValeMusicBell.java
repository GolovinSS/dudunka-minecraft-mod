package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Dedicated small musical parts; ordinary landmark mushroom models remain untouched. */
public final class ValeMusicBell extends Block {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment",0,1);
    public ValeMusicBell() {
        super(Properties.of().strength(.2F).sound(SoundType.WOOD).noOcclusion()
                .lightLevel(s->s.getValue(SEGMENT)==1?12:0));
        registerDefaultState(stateDefinition.any().setValue(SEGMENT,0));
    }
    @Override public void animateTick(BlockState state,net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(state.getValue(SEGMENT)==1 && random.nextInt(8)==0)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX()+random.nextDouble(),pos.getY()+.2,pos.getZ()+random.nextDouble(),0,.008,0);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(SEGMENT); }
    @Override public int getLightBlock(BlockState state,BlockGetter level,BlockPos pos) { return 0; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        // Crown outline covers the overhanging model; collision keeps the approach open.
        return state.getValue(SEGMENT)==0 ? Block.box(5,0,5,11,16,11) :
                Shapes.or(Block.box(-8,4,-2,24,18,18),Block.box(-2,4,-8,18,18,24),Block.box(2,18,2,14,28,14));
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return Block.box(5,0,5,11,16,11);
    }
}
