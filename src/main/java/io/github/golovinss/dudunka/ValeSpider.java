package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

/** Independent peaceful ground creature; no spider hostility, climbing or web code. */
public class ValeSpider extends PathfinderMob {
    public static final int GREETING = 1, SHY = 2;
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(ValeSpider.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> EMOTE_START = SynchedEntityData.defineId(ValeSpider.class, EntityDataSerializers.LONG);
    private int socialCooldown;
    private boolean retreat;

    public ValeSpider(EntityType<? extends ValeSpider> type, Level level) {
        super(type, level);
        for (var danger : new BlockPathTypes[]{BlockPathTypes.WATER, BlockPathTypes.LAVA,
                BlockPathTypes.DAMAGE_FIRE, BlockPathTypes.DANGER_FIRE, BlockPathTypes.DAMAGE_OTHER})
            setPathfindingMalus(danger, -1);
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.MOVEMENT_SPEED, .14).add(Attributes.FOLLOW_RANGE, 8);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(EMOTE, 0); entityData.define(EMOTE_START, 0L);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LocalStrollGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6, 1));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    public int emote() { return entityData.get(EMOTE); }
    public float emoteStrength(float partial) {
        float elapsed = level().getGameTime() - entityData.get(EMOTE_START) + partial;
        return emote() == 0 || elapsed < 0 || elapsed >= 32 ? 0 : Mth.sin(elapsed * Mth.PI / 32);
    }
    private void playEmote(int value) {
        entityData.set(EMOTE, value); entityData.set(EMOTE_START, level().getGameTime());
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (socialCooldown > 0) socialCooldown--;
        if (tickCount % 10 != 0 || socialCooldown > 0) return;
        Player player = level().getNearestPlayer(this, 4);
        if (player == null || player.isSpectator()) return;
        if (distanceToSqr(player) < 6.25 && player.isSprinting()) {
            playEmote(SHY); retreat = true; getNavigation().stop(); socialCooldown = 160;
        } else if (random.nextInt(12) == 0) {
            playEmote(GREETING); socialCooldown = 120;
        }
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            player.displayClientMessage(Component.translatable("message.dudunka.vale_spider_greeting"), true);
            playEmote(GREETING); socialCooldown = 120;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public boolean canTrample(net.minecraft.world.level.block.state.BlockState state, BlockPos pos, float fallDistance) { return false; }
    @Override public int getMaxFallDistance() { return 1; }
    @Override public void setTarget(LivingEntity target) { super.setTarget(null); }
    @Override public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override public int getMaxHeadXRot() { return 60; }

    public static boolean habitat(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getLevel().dimension().equals(MushroomVale.LEVEL) || !level.hasChunkAt(pos)) return false;
        var key = level.getBiome(pos).unwrapKey().orElse(null);
        return key != null && key.location().getNamespace().equals(DudunkaMod.ID)
                && (key.location().getPath().equals("memory_forest") || key.location().getPath().equals("glowing_mushroom_forest"));
    }
    public static boolean canSpawn(EntityType<ValeSpider> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        if (!habitat(level, pos) || !Pufik.safeGround(level, pos)) return false;
        // Soil, including sheltered mushroom understory, rather than roofs/caps or deep caves.
        var floor = level.getBlockState(pos.below());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return (floor.is(BlockTags.DIRT) || floor.is(Blocks.MOSS_BLOCK))
                && pos.getY() >= surface - 12 && pos.getY() <= surface
                && level.getWorldBorder().isWithinBounds(type.getAABB(pos.getX()+.5, pos.getY(), pos.getZ()+.5));
    }

    /** Bounded local choices; all social movement uses the same dry-ground checks. */
    private class LocalStrollGoal extends Goal {
        private BlockPos destination;
        private int remaining;
        LocalStrollGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (!(level() instanceof ServerLevel server) || (!retreat && random.nextInt(80) != 0)) return false;
            Player player = level().getNearestPlayer(ValeSpider.this, 6);
            boolean away = retreat;
            retreat = false;
            boolean approach = !away && player != null && distanceToSqr(player) > 6.25 && random.nextInt(3) == 0;
            BlockPos origin = blockPosition();
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = origin.getX()+random.nextInt(9)-4, z = origin.getZ()+random.nextInt(9)-4;
                if (player != null && (away || approach)) {
                    double current = distanceToSqr(player), next = player.distanceToSqr(x+.5, getY(), z+.5);
                    if (away ? next < current+1 || next > 36 : next >= current || next < 4) continue;
                }
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!server.hasChunkAt(column)) continue;
                // Search near our feet, so giant mushroom heightmaps do not strand understory walkers.
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos candidate = column.offset(0, dy, 0);
                    if (!Pufik.safeGround(server, candidate) || !server.getWorldBorder().isWithinBounds(candidate)) continue;
                    var path = getNavigation().createPath(candidate, 0);
                    if (path == null || !path.canReach()) continue;
                    destination = candidate; return true;
                }
            }
            return false;
        }
        @Override public void start() {
            remaining = 80;
            getNavigation().moveTo(destination.getX()+.5, destination.getY(), destination.getZ()+.5, .8);
        }
        @Override public void tick() { remaining--; }
        @Override public boolean canContinueToUse() { return remaining > 0 && !getNavigation().isDone(); }
        @Override public void stop() { destination = null; getNavigation().stop(); }
    }
}
