package io.github.golovinss.dudunka;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class HomeMarkerEntity extends BlockEntity {
    private UUID owner;
    private long inspectedAt = Long.MIN_VALUE;
    private HomeRules.Conditions cached;
    public HomeMarkerEntity(BlockPos pos, BlockState state) { super(DudunkaMod.HOME_BE.get(),pos,state); }
    public UUID ownerId() { return owner; }
    public Kind kind() { return ((HomeMarkerBlock)getBlockState().getBlock()).kind; }
    public void claim(UUID player) { if (owner==null) { owner=player; setChanged(); } }
    public boolean validFor(Companion mob) {
        return level!=null && owner!=null && owner.equals(mob.ownerId()) && mob.kind==kind() && conditions(false).ready();
    }
    public HomeRules.Conditions conditions(boolean fresh) {
        if (fresh || cached==null || level.getGameTime()-inspectedAt>=100) {
            cached=HomeRules.inspect(level,worldPosition); inspectedAt=level.getGameTime();
        }
        return cached;
    }
    public int bindNearby(Player player) {
        if (owner!=null && !owner.equals(player.getUUID())) { player.displayClientMessage(Component.translatable("message.dudunka.home_owned"),true); return 0; }
        claim(player.getUUID());
        var conditions=conditions(true);
        if (!conditions.ready()) {
            player.displayClientMessage(Component.translatable("message.dudunka.home_conditions",flag(conditions.roof()),flag(conditions.bed()),flag(conditions.chest()),flag(conditions.light()),flag(conditions.food())),true);
            return 0;
        }
        int count=0;
        for (Companion mob:level.getEntitiesOfClass(Companion.class,new AABB(worldPosition).inflate(8),m->m.kind==kind() && player.getUUID().equals(m.ownerId()))) {
            if (HomeRules.nearbyStanding(level,worldPosition,mob)==null) continue;
            mob.bindHome(worldPosition); count++;
        }
        player.displayClientMessage(Component.translatable("message.dudunka.home_bound",count),true);
        return count;
    }
    private Component flag(boolean value) { return Component.translatable(value?"message.dudunka.yes":"message.dudunka.no"); }
    @Override public void load(CompoundTag tag) { super.load(tag); owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null; cached=null; }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if(owner!=null)tag.putUUID("Owner",owner); }
}
