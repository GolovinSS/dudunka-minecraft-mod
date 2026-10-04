package io.github.golovinss.dudunka;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** A shareable memento reads its own text, never changes the reader's quest or trail progress. */
public final class FriendKeepsakeItem extends Item {
    public FriendKeepsakeItem(Properties properties){super(properties);}
    private static Kind kind(ItemStack stack){var tag=stack.getTag();if(tag!=null)for(var k:Kind.values())if(k.id.equals(tag.getString("Friend")))return k;return null;}
    private static FriendRequests.Task task(ItemStack stack){try{return stack.hasTag()?FriendRequests.Task.valueOf(stack.getTag().getString("Moment")):null;}catch(IllegalArgumentException e){return null;}}
    @Override public Component getName(ItemStack stack){var kind=kind(stack);return Component.translatable(kind==null?"item.dudunka.friend_keepsake":"keepsake.dudunka.name."+kind.id);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){var stack=player.getItemInHand(hand);var k=kind(stack);var t=task(stack);if(level.isClientSide && k!=null && t!=null)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->io.github.golovinss.dudunka.client.FriendKeepsakeScreen.open(k,t));return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("keepsake.dudunka.tooltip"));}
}
