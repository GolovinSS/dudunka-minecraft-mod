package io.github.golovinss.dudunka;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.*;

/** One-block furniture, with a persisted owner and a single assigned companion. */
public final class FurnitureBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty PICTURE=net.minecraft.world.level.block.state.properties.IntegerProperty.create("picture",0,2);
    public final Kind kind;
    public FurnitureBlock(Kind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PICTURE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,PICTURE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new FurnitureEntity(p,s);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity e,ItemStack stack){if(!l.isClientSide && e instanceof Player && l.getBlockEntity(p) instanceof FurnitureEntity f)f.claim(e.getUUID());}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof FurnitureEntity f){
            String key="message.dudunka.furniture_album";
            if(f.ownerId()!=null && !f.ownerId().equals(player.getUUID()))key="message.dudunka.home_owned";
            else if(player.isShiftKeyDown() && player.getItemInHand(hand).is(DudunkaMod.ALBUM.get())){
                var id=f.memberId();if(f.clear(player.getUUID())){
                    if(level instanceof net.minecraft.server.level.ServerLevel server && id!=null && server.getEntity(id) instanceof Companion mob && pos.equals(mob.furniturePosition()))mob.clearFurniture();
                    key="screen.dudunka.result.furniture_cleared";
                }
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        VoxelShape shape=switch(kind){
            case DUDUNKA -> Shapes.or(Block.box(1,8,1,15,10,15),Block.box(2,0,2,4,8,4),Block.box(12,0,2,14,8,4),Block.box(2,0,12,4,8,14),Block.box(12,0,12,14,8,14));
            case MARUSYA -> Shapes.or(Block.box(1,0,1,15,2,15),Block.box(2,2,3,10,4,14),Block.box(11,2,3,14,14,6));
            case SYUSYA -> Shapes.or(Block.box(1,0,1,15,1,15),Block.box(3,1,8,5,8,14),Block.box(11,1,8,13,8,14),Block.box(5,1,13,11,8,14),Block.box(3,8,8,13,10,14),Block.box(5,10,9,11,12,14),Block.box(7,12,10,9,13,13));
        };
        return rotateShape(shape,s.getValue(FACING));
    }
    public static VoxelShape rotateShape(VoxelShape shape,Direction facing){
        int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        for(int i=0;i<turns;i++){var boxes=shape.toAabbs();shape=Shapes.empty();for(var b:boxes)shape=Shapes.or(shape,Shapes.box(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX));}
        return shape;
    }
}
