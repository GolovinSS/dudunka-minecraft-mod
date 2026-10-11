package io.github.golovinss.dudunka;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Independent, peaceful wildlife. Hops are validated impulses followed by vanilla physics. */
public class FluffyJumper extends PathfinderMob {
    public static final ResourceKey<net.minecraft.world.level.biome.Biome> HABITAT = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "pink_meadows"));
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(FluffyJumper.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> GREETING = SynchedEntityData.defineId(FluffyJumper.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> HOP = SynchedEntityData.defineId(FluffyJumper.class, EntityDataSerializers.LONG);
    private int restTicks = 20, socialCooldown;
    private boolean encouraged, wasAirborne;
    private float landing, previousVertical, currentVertical;

    public FluffyJumper(EntityType<? extends FluffyJumper> type, Level level) {
        super(type, level);
        for (var danger : new net.minecraft.world.level.pathfinder.BlockPathTypes[]{
                net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA,
                net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE,
                net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_OTHER}) setPathfindingMalus(danger, -1);
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.MOVEMENT_SPEED, .12).add(Attributes.FOLLOW_RANGE, 6);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(COLOR, 0); entityData.define(GREETING, -100L); entityData.define(HOP, -100L);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new HopGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5, 1));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    public int colorVariant() { return entityData.get(COLOR); }
    public float greetingStrength(float partial) {
        float elapsed = level().getGameTime() - entityData.get(GREETING) + partial;
        return elapsed < 0 || elapsed >= 30 ? 0 : Mth.sin(elapsed * Mth.PI / 30);
    }
    public float hopAge(float partial) { return level().getGameTime() - entityData.get(HOP) + partial; }
    public float visualVerticalSpeed(float partial) { return Mth.lerp(partial, previousVertical, currentVertical); }
    public float landingStrength(float partial) { return Math.max(0, landing - partial / 6); }
    @Override public void aiStep() {
        super.aiStep();
        // Client-only easing; animation never changes collision, motion or server state.
        if (level().isClientSide) {
            previousVertical = currentVertical; currentVertical = (float)getDeltaMovement().y;
            if (onGround() && wasAirborne) landing = 1;
            else landing = Math.max(0, landing - 1f / 6);
            wasAirborne = !onGround();
        }
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (socialCooldown > 0) socialCooldown--;
        if (onGround() && restTicks > 0) restTicks--;
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && socialCooldown == 0) {
            player.displayClientMessage(Component.translatable("message.dudunka.fluffy_jumper." + random.nextInt(3)), true);
            entityData.set(GREETING, level().getGameTime());
            getLookControl().setLookAt(player, 30, 50);
            encouraged = true; restTicks = Math.min(restTicks, 6); socialCooldown = 40;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public void setTarget(LivingEntity target) { super.setTarget(null); }
    @Override public boolean canTrample(net.minecraft.world.level.block.state.BlockState state, BlockPos pos, float distance) { return false; }
    @Override public int getMaxFallDistance() { return 0; }
    @Override public int getMaxSpawnClusterSize() { return 4; }
    @Override public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("FluffyColor", colorVariant()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(COLOR, Mth.clamp(tag.getInt("FluffyColor"), 0, 2));
        entityData.set(GREETING, -100L); entityData.set(HOP, -100L); restTicks = 20; socialCooldown = 0; encouraged = false;
    }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        entityData.set(COLOR, random.nextInt(3));
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }
    public static boolean habitat(ServerLevelAccessor level, BlockPos pos) {
        return level.getLevel().dimension().equals(MushroomVale.LEVEL) && level.hasChunkAt(pos) && level.getBiome(pos).is(HABITAT);
    }
    public static boolean canSpawn(EntityType<FluffyJumper> type, ServerLevelAccessor level, MobSpawnType reason,
            BlockPos pos, RandomSource random) {
        if (!habitat(level, pos) || !safeFloor(level, pos)) return false;
        var floor = level.getBlockState(pos.below());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return (floor.is(BlockTags.DIRT) || floor.is(Blocks.MOSS_BLOCK)) && pos.getY() >= surface - 4 && pos.getY() <= surface
                && level.getLevel().getWorldBorder().isWithinBounds(type.getAABB(pos.getX()+.5, pos.getY(), pos.getZ()+.5));
    }
    /** Flat, fully supported landing neighborhood. Never plan a jump down a ledge. */
    private static boolean safeFloor(ServerLevelAccessor level, BlockPos feet) {
        if (!Pufik.safeGround(level, feet)) return false;
        for (var direction : Direction.Plane.HORIZONTAL) {
            var floor = feet.relative(direction).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return !level.getBlockState(feet.below()).is(BlockTags.LEAVES);
    }
    /** Check the entire swept body and supported flight corridor without loading chunks.
     * Margins cover the first tick's ground friction and collision rounding. No position is written.
     */
    public boolean safeHop(Vec3 impulse) {
        if (!(level() instanceof ServerLevel server) || !onGround() || isInWaterOrBubble() || isOnFire()
                || !Double.isFinite(impulse.x + impulse.y + impulse.z) || impulse.horizontalDistance() > .12 || impulse.y < .28 || impulse.y > .43) return false;
        Vec3 offset = Vec3.ZERO, velocity = impulse;
        for (int tick = 0; tick < 24; tick++) {
            var next = offset.add(velocity);
            if (tick > 0 && next.y <= 0) return true;
            var swept = getBoundingBox().move(offset).minmax(getBoundingBox().move(next)).inflate(.04, 0, .04);
            if (!server.getWorldBorder().isWithinBounds(swept)) return false;
            for (var p : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                    BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
                if (!server.hasChunkAt(p)) return false;
                var state = server.getBlockState(p);
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.CAMPFIRE)
                        || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                        || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)) return false;
            }
            if (!server.noCollision(this, swept)) return false;
            // Check both center and body corners at launch elevation, including diagonal cliffs.
            for (double x : new double[]{swept.minX, swept.maxX}) for (double z : new double[]{swept.minZ, swept.maxZ})
                if (!safeFloor(server, BlockPos.containing(x, getY(), z))) return false;
            offset = next; velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
        }
        return false;
    }
    private class HopGoal extends Goal {
        private Vec3 impulse;
        private boolean excited;
        HopGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() {
            if (!onGround() || restTicks > 0 || !(level() instanceof ServerLevel)) return false;
            var player = level().getNearestPlayer(FluffyJumper.this, 3);
            excited = encouraged || random.nextInt(player == null ? 8 : 3) == 0;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                // Curious approach at a distance, playful sideways hops within personal space.
                if (player != null && attempt < 3) angle = Math.atan2(player.getZ()-getZ(), player.getX()-getX())
                        + (distanceToSqr(player) < 2.25 ? Mth.HALF_PI : 0) + (random.nextDouble()-.5);
                var candidate = new Vec3(Math.cos(angle) * .10, excited ? .42 : .30, Math.sin(angle) * .10);
                if (safeHop(candidate)) { impulse = candidate; return true; }
            }
            restTicks = 20; return false;
        }
        @Override public void start() {
            getNavigation().stop(); setSpeed(0); setZza(0); setXxa(0);
            setYRot((float)(Math.atan2(impulse.z, impulse.x) * Mth.RAD_TO_DEG) - 90);
            setDeltaMovement(impulse); hasImpulse = true;
            entityData.set(HOP, level().getGameTime());
            restTicks = 18 + random.nextInt(23);
            boolean share = excited && !encouraged; encouraged = false;
            if (share) for (var friend : level().getEntitiesOfClass(FluffyJumper.class, getBoundingBox().inflate(3))) {
                if (friend != FluffyJumper.this && friend.onGround() && !friend.isNoAi()) {
                    friend.encouraged = true; friend.restTicks = Math.min(friend.restTicks, 3 + random.nextInt(6));
                }
            }
        }
        @Override public boolean canContinueToUse() { return false; }
    }
}
