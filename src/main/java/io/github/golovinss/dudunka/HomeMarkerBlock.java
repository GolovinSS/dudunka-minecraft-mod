package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class HomeMarkerBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    public final Kind kind;
    public HomeMarkerBlock(Kind kind, Properties properties) { super(properties); this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape=switch(kind){
            case DUDUNKA -> Shapes.or(Block.box(1,0,1,15,5,15),Block.box(1,5,13,15,8,15));
            case MARUSYA -> Block.box(1,0,1,15,3,15);
            case SYUSYA -> Shapes.or(Block.box(1,0,1,15,1,15),Block.box(1,1,1,4,8,15),Block.box(12,1,1,15,8,15),Block.box(4,1,12,12,8,15),Block.box(1,8,1,15,10,15));
        };return FurnitureBlock.rotateShape(shape,state.getValue(FACING));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HomeMarkerEntity(pos,state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof HomeMarkerEntity marker) marker.claim(player.getUUID());
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HomeMarkerEntity marker) marker.bindNearby(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
