package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Owner unlocks live in chunk NBT. A ritual is transient and must be retried after an unload/restart. */
public final class FamilyGatewayEntity extends BlockEntity {
    private final Set<UUID> awakened = new HashSet<>();
    private boolean forgottenArch;
    public boolean forgottenArch() { return forgottenArch; }
    public void markForgottenArch() { forgottenArch = true; setChanged(); }
    private UUID performer;
    private List<UUID> participants = List.of();
    private long finishes;

    public FamilyGatewayEntity(BlockPos pos, BlockState state) { super(DudunkaMod.FAMILY_GATEWAY_BE.get(), pos, state); }
    public boolean awakened(UUID owner) { return awakened.contains(owner); }
    public boolean awakening() { return performer != null; }
    private void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.dudunka.gateway." + key), false);
    }
    public void interact(ServerPlayer player) {
        if (!(level instanceof ServerLevel world) || player.serverLevel() != world || !player.isAlive()
                || player.isSpectator() || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 36) return;
        if (!FamilyGatewayBlock.complete(world, worldPosition)) {
            tell(player, "broken"); return;
        }
        // In Vale the same persistent structure is a shared exit, with no owner or ritual requirement.
        if (world.dimension().equals(MushroomVale.LEVEL)) {
            MushroomVale.tell(player, FamilyJourney.travel(player, worldPosition, true, false)); return;
        }
        if (!world.dimension().equals(Level.OVERWORLD)) { tell(player, "broken"); return; }
        if (awakened(player.getUUID())) {
            MysteryAdventure.get(player.server).unlockThrough(player.getUUID(),4);
            var result = FamilyJourney.travel(player, worldPosition, false, false);
            MushroomVale.tell(player, result);
            if (result == MushroomVale.TravelResult.ENTERED)
                tell(player, MushroomValeReturnGateway.ensure(player) == null ? "return_unavailable" : "return_ready");
            return;
        }
        if (performer != null) { tell(player, "busy"); return; }
        var eligibility = FamilyGatewayRules.inspect(world, worldPosition, player.getUUID());
        if (!eligibility.ready()) { player.displayClientMessage(eligibility.missing(), false); return; }
        performer = player.getUUID();
        participants = eligibility.friends().stream().map(Companion::getUUID).toList();
        finishes = world.getGameTime() + FamilyGatewayRules.RITUAL_TICKS;
        for (var friend : eligibility.friends()) friend.beginPortalPose(worldPosition, finishes);
        tell(player, "awakening");
    }
    public void cancel() {
        if (level instanceof ServerLevel world) for (var id : participants)
            if (world.getEntity(id) instanceof Companion friend) friend.endPortalPose(worldPosition);
        performer = null; participants = List.of(); finishes = 0;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, FamilyGatewayEntity gateway) {
        if (!(level instanceof ServerLevel world) || gateway.performer == null) return;
        var player = world.getPlayerByUUID(gateway.performer);
        if (!(player instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()
                || owner.distanceToSqr(Vec3.atCenterOf(pos)) > 100 || !FamilyGatewayBlock.complete(level, pos)) {
            gateway.cancel(); return;
        }
        var eligibility = FamilyGatewayRules.inspect(world, pos, gateway.performer);
        if (!eligibility.ready() || !gateway.participants.equals(eligibility.friends().stream().map(Companion::getUUID).toList())) {
            owner.displayClientMessage(eligibility.ready() ? Component.translatable("message.dudunka.gateway.changed") : eligibility.missing(), false);
            gateway.cancel(); return;
        }
        long now = world.getGameTime();
        if (now % 10 == 0) {
            for (var friend : eligibility.friends()) {
                var color = switch (friend.kind) {
                    case DUDUNKA -> new Vector3f(1, .5f, .8f);
                    case MARUSYA -> new Vector3f(.7f, .45f, 1);
                    case SYUSYA -> new Vector3f(.3f, 1, .85f);
                };
                var from = friend.position().add(0, friend.getBbHeight() + .2, 0);
                var to = Vec3.atCenterOf(pos).add(0, 1, 0);
                for (int n = 0; n <= 8; n++) {
                    var p = from.lerp(to, n / 8.0);
                    world.sendParticles(new DustParticleOptions(color, 1), p.x, p.y, p.z, 1, .05, .05, .05, 0);
                }
            }
        }
        if (now < gateway.finishes) return;
        gateway.awakened.add(gateway.performer);
        MysteryAdventure.get(world.getServer()).unlockThrough(gateway.performer,4);
        gateway.setChanged();
        for (int part = 0; part < 12; part++) {
            var cell = FamilyGatewayBlock.cell(pos, part);
            world.setBlockAndUpdate(cell, world.getBlockState(cell).setValue(FamilyGatewayBlock.ACTIVE, true));
        }
        world.sendParticles(ParticleTypes.END_ROD, pos.getX() + .5, pos.getY() + 1.5, pos.getZ() + .5, 30, 1, 1, .2, .02);
        gateway.tell(owner, "awakened");
        gateway.cancel();
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); cancel(); awakened.clear(); forgottenArch = tag.getBoolean("ForgottenArch");
        var rows = tag.getList("AwakenedOwners", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) if (rows.getCompound(i).hasUUID("Owner")) awakened.add(rows.getCompound(i).getUUID("Owner"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (forgottenArch) tag.putBoolean("ForgottenArch", true);
        var rows = new ListTag();
        for (var owner : awakened) { var row = new CompoundTag(); row.putUUID("Owner", owner); rows.add(row); }
        tag.put("AwakenedOwners", rows);
    }
    @Override public void setRemoved() { cancel(); super.setRemoved(); }
}
