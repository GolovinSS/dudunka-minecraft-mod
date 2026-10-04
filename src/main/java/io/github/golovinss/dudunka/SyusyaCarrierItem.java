package io.github.golovinss.dudunka;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class SyusyaCarrierItem extends Item {
    public SyusyaCarrierItem(Properties properties) { super(properties); }
    public static boolean filled(ItemStack stack) { return stack.hasTag()&&stack.getTag().contains("Companion",10); }
    public static boolean capture(Companion mob,Player player,ItemStack stack) {
        if (mob.level().isClientSide) return false;
        if (mob.kind!=Kind.SYUSYA || !player.getUUID().equals(mob.ownerId()) || !stack.is(DudunkaMod.CARRIER.get()) || stack.getCount()!=1 || filled(stack)
                || mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || !mob.isAlive()) {
            player.displayClientMessage(Component.translatable("message.dudunka.carrier_capture_failed"),true);return false;
        }
        CompoundTag data=new CompoundTag();if(!mob.save(data))return false;
        UUID token=UUID.randomUUID();var ledger=CarrierLedger.get(((ServerLevel)mob.level()).getServer());
        if(!ledger.capture(mob.getUUID(),token,player.getUUID(),data))return false;
        stack.getOrCreateTag().put("Companion",data);stack.getOrCreateTag().putUUID("Ticket",token);
        mob.getNavigation().stop();mob.discard();player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("message.dudunka.carrier_captured"),true);return true;
    }
    public static boolean release(ServerLevel level,BlockPos pos,Player player,ItemStack stack) {
        if(!filled(stack)||stack.getCount()!=1||!stack.is(DudunkaMod.CARRIER.get()))return false;
        var root=stack.getTag();var data=root.getCompound("Companion");
        if(!root.hasUUID("Ticket")||!data.hasUUID("UUID")||!data.hasUUID("FamilyOwner")
                ||!data.getString("id").equals("dudunka:syusya")||!player.getUUID().equals(data.getUUID("FamilyOwner")))return fail(player,"message.dudunka.carrier_owner");
        UUID entity=data.getUUID("UUID"),token=root.getUUID("Ticket");var ledger=CarrierLedger.get(level.getServer());
        if(!ledger.matches(entity,token,player.getUUID()))return fail(player,"message.dudunka.carrier_stale");
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(entity)!=null)return fail(player,"message.dudunka.carrier_stale");
        ledger.adopt(entity,token,player.getUUID(),data);
        var backup=ledger.backup(entity,player.getUUID());if(backup!=null)data=backup;
        Companion mob=DudunkaMod.TYPES.get(Kind.SYUSYA).get().create(level);if(mob==null)return false;
        mob.load(data.copy());mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,player.getYRot(),0);mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mob.fallDistance=0;
        if(!HomeRules.safeStanding(level,pos,mob))return fail(player,"message.dudunka.carrier_space");
        // Mark as released before addFreshEntity: the join-event guard must allow this entity.
        if(!ledger.consume(entity,token,player.getUUID()))return false;
        if(!level.addFreshEntity(mob)){ledger.restore(entity,token,player.getUUID());return false;}
        root.remove("Companion");root.remove("Ticket");player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("message.dudunka.carrier_released"),true);return true;
    }
    private static boolean fail(Player player,String key){player.displayClientMessage(Component.translatable(key),true);return false;}
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getPlayer()==null || !filled(context.getItemInHand()))return InteractionResult.PASS;
        if(!context.getPlayer().mayUseItemAt(context.getClickedPos(),context.getClickedFace(),context.getItemInHand()) || !context.getLevel().mayInteract(context.getPlayer(),context.getClickedPos()))return InteractionResult.FAIL;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        return release((ServerLevel)context.getLevel(),context.getClickedPos().relative(context.getClickedFace()),context.getPlayer(),context.getItemInHand())?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public Component getName(ItemStack stack) { return filled(stack)?Component.translatable("item.dudunka.syusya_carrier.filled"):super.getName(stack); }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable(filled(stack)?"tooltip.dudunka.carrier_filled":"tooltip.dudunka.carrier_empty"));
    }
}
