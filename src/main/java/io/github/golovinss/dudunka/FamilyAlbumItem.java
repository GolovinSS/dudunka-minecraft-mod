package io.github.golovinss.dudunka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class FamilyAlbumItem extends Item {
    public FamilyAlbumItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer serverPlayer){AlbumNetwork.send(serverPlayer,FamilyAlbum.collect(serverPlayer));player.getCooldowns().addCooldown(this,20);}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
