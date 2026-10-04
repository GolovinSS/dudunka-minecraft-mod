package io.github.golovinss.dudunka;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** A reusable note can be shared; only the authenticated reader's progress changes. */
public final class TrailNoteItem extends Item {
    private final int page;
    public TrailNoteItem(int page,Properties properties){super(properties);this.page=page;}
    public static boolean read(ServerPlayer player,int page){
        if(!player.isAlive() || player.isSpectator())return false;
        var progress=TrailProgress.get(player.server);boolean added=progress.read(player.getUUID(),page);
        String key=!added?"trail.dudunka.already_read":TrailProgress.unlocked(progress.mask(player.getUUID()))<page?"trail.dudunka.saved_ahead":"trail.dudunka.read";
        player.displayClientMessage(Component.translatable(key),false);return added;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){if(player instanceof ServerPlayer serverPlayer)read(serverPlayer,page);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("trail.dudunka.note_tooltip"));}
}
