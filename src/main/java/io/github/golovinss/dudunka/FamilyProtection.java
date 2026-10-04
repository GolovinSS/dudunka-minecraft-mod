package io.github.golovinss.dudunka;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class FamilyProtection {
    @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent event) {
        var entity=event.getLevel().getBlockEntity(event.getPos());
        var owner=entity instanceof EggEntity egg?egg.ownerId():entity instanceof HomeMarkerEntity home?home.ownerId():entity instanceof FurnitureEntity furniture?furniture.ownerId():null;
        if(owner!=null && !owner.equals(event.getPlayer().getUUID()) && !event.getPlayer().canUseGameMasterBlocks()) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.dudunka.not_owner"),true);
        }
    }
    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if(event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Companion mob){
            if(mob.kind==Kind.SYUSYA && CarrierLedger.get(level.getServer()).carried(mob.getUUID())){event.setCanceled(true);return;}
            for(var world:level.getServer().getAllLevels()){
                var existing=world.getEntity(mob.getUUID());
                if(existing!=mob && existing instanceof Companion other && other.isAlive() && !other.isRemoved()){event.setCanceled(true);return;}
            }
            FamilyRegistry.get(level.getServer()).observe(mob);
        }
    }
}
