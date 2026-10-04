package io.github.golovinss.dudunka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class FamilyAlbumItem extends Item {
    public FamilyAlbumItem(Properties properties){super(properties);}
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        // Secondary use skips Block.use while an item is held; route it through the album.
        if(player!=null && player.isShiftKeyDown() && state.getBlock() instanceof FurnitureBlock block)
            return block.use(state,level,pos,player,context.getHand(),new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),pos,context.isInside()));
        return net.minecraft.world.InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer serverPlayer){AlbumNetwork.open(serverPlayer);player.getCooldowns().addCooldown(this,20);}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
