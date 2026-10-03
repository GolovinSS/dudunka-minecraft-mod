package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

public class EggBlock extends BaseEntityBlock {
    public final Kind kind;
    private static final VoxelShape SHAPE=Block.box(4,0,4,12,12,12);
    public EggBlock(Kind kind,Properties p) { super(p); this.kind=kind; }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new EggEntity(p,s); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) {
        return l.isClientSide?null:createTickerHelper(t,DudunkaMod.EGG_BE.get(),EggEntity::tick);
    }
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
        if(l.isClientSide) return InteractionResult.SUCCESS;
        if(l.getBlockEntity(p) instanceof EggEntity egg) egg.interact(player,hand);
        return InteractionResult.CONSUME;
    }
}
