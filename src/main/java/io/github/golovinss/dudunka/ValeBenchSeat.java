package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One transient mount per bench block; vanilla packets and dismount controls only. */
public final class ValeBenchSeat extends Entity {
    public ValeBenchSeat(EntityType<? extends ValeBenchSeat> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static InteractionResult sit(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isShiftKeyDown() || player.isPassenger() || player.isSleeping()
                || !player.isAlive() || player.isSpectator()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // All interaction runs on the server thread. No separate occupancy map can go stale.
        for (var seat : level.getEntitiesOfClass(ValeBenchSeat.class, new AABB(pos))) {
            if (seat.isVehicle()) return InteractionResult.CONSUME;
            seat.discard();
        }
        if (!level.getFluidState(pos).isEmpty() || !level.isEmptyBlock(pos.above())
                || !level.isEmptyBlock(pos.above(2))) return InteractionResult.CONSUME;
        var seat = DudunkaMod.VALE_BENCH_SEAT.get().create(level);
        if (seat == null) return InteractionResult.CONSUME;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Boards end at y=8/16. Seated pelvis is ~0.70 above the player origin;
        // the mount and vanilla player offsets place that origin at y=-0.20.
        seat.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, facing.toYRot(), 0);
        if (!level.addFreshEntity(seat)) return InteractionResult.CONSUME;
        if (!player.startRiding(seat)) seat.discard();
        else {
            player.setYRot(facing.toYRot());
            player.setYHeadRot(facing.toYRot());
            player.setYBodyRot(facing.toYRot());
            seat.positionRider(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                serverPlayer.connection.teleport(player.getX(), player.getY(), player.getZ(), facing.toYRot(), 0);

        }
        return InteractionResult.CONSUME;
    }

    public static void removeAt(Level level, BlockPos pos) {
        if (!level.isClientSide) for (var seat : level.getEntitiesOfClass(ValeBenchSeat.class, new AABB(pos))) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (!level().getBlockState(blockPosition())
                .is(MushroomValeDecor.BLOCKS.get("vale_bench").get()) || getPassengers().isEmpty()
                || getPassengers().stream().anyMatch(p -> !p.isAlive() || p.isRemoved() || p.level() != level()))) {
            ejectPassengers();
            discard();
        }
    }
    @Override public double getPassengersRidingOffset() { return -.35; }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof Player && getPassengers().isEmpty(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        var front = Direction.fromYRot(getYRot());
        for (var direction : new Direction[]{front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()}) {
            var p = blockPosition().relative(direction);
            var target = Vec3.atBottomCenterOf(p);
            var box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(target);
            if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), Direction.UP)
                    && level().getFluidState(p).isEmpty() && level().noCollision(passenger, box)) return target;
        }
        // The interaction requires two empty blocks above the seat.
        return Vec3.atBottomCenterOf(blockPosition().above());
    }
}
