package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class ChestCuriosity {
    private ChestCuriosity() {}
    public static boolean isViewing(Player player,BlockPos pos) {
        if(!player.level().hasChunkAt(pos) || !(player.containerMenu instanceof ChestMenu menu)
                || !(player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest))return false;
        var container=menu.getContainer();
        return container==chest || container instanceof CompoundContainer compound && compound.contains(chest);
    }
    @SubscribeEvent public static void onClose(PlayerContainerEvent.Close event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof ChestMenu))return;
        for(Companion mob:player.level().getEntitiesOfClass(Companion.class,player.getBoundingBox().inflate(16)))mob.forgetOpenedChest(player);
    }
    @SubscribeEvent public static void onOpen(PlayerContainerEvent.Open event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof ChestMenu menu))return;
        BlockPos pos=null;var container=menu.getContainer();
        if(container instanceof ChestBlockEntity chest)pos=chest.getBlockPos();
        else if(container instanceof CompoundContainer compound) {
            // CompoundContainer exposes identity membership, not its children. No slots are read.
            double nearest=Double.MAX_VALUE;
            for(BlockPos p:BlockPos.betweenClosed(player.blockPosition().offset(-5,-3,-5),player.blockPosition().offset(5,3,5))) {
                if(!player.level().hasChunkAt(p) || !(player.level().getBlockEntity(p) instanceof ChestBlockEntity chest) || !compound.contains(chest))continue;
                double distance=p.distToCenterSqr(player.position());
                if(distance<nearest){nearest=distance;pos=p.immutable();}
            }
        }
        if(pos==null || !isViewing(player,pos))return;
        for(Companion mob:player.level().getEntitiesOfClass(Companion.class,new AABB(pos).inflate(8)))mob.noticeOpenedChest(player,pos);
    }
}
