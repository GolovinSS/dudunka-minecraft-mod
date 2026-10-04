package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Once per player UUID and world, independent of death, dimension and item ownership. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class FamilyGuideGifts extends SavedData {
    private final Set<UUID> delivered=new HashSet<>();
    public boolean delivered(UUID player){return delivered.contains(player);}
    public boolean give(ServerPlayer player){
        if(player instanceof FakePlayer || player.isSpectator() || delivered(player.getUUID()))return false;
        boolean existing=false;var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(DudunkaMod.ALBUM.get())){existing=true;break;}
        if(!existing && !inventory.add(new ItemStack(DudunkaMod.ALBUM.get()))){
            player.displayClientMessage(Component.translatable("message.dudunka.guide_full"),false);return false;
        }
        delivered.add(player.getUUID());setDirty();
        if(!existing){player.inventoryMenu.broadcastChanges();player.displayClientMessage(Component.translatable("message.dudunka.guide_received"),false);}
        return !existing;
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player)
            player.server.getLevel(net.minecraft.world.level.Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(FamilyGuideGifts::load,FamilyGuideGifts::new,"dudunka_guide_gifts").give(player);
    }
    public static FamilyGuideGifts load(CompoundTag tag){
        var gifts=new FamilyGuideGifts();var list=tag.getList("Delivered",Tag.TAG_STRING);
        for(int i=0;i<list.size();i++)try{gifts.delivered.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}
        return gifts;
    }
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();delivered.stream().sorted().forEach(id->list.add(StringTag.valueOf(id.toString())));tag.put("Delivered",list);return tag;}
}
