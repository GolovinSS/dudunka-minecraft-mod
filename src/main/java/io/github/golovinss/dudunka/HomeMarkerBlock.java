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
    public final Kind kind;
    public HomeMarkerBlock(Kind kind, Properties properties) { super(properties); this.kind=kind; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(1,0,1,15,kind==Kind.SYUSYA?10:kind==Kind.DUDUNKA?5:3,15);
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
