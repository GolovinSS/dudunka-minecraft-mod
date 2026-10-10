package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

import java.util.EnumSet;

/** A small, independent Vale inhabitant. No family ownership or progression state. */
public class Pufik extends PathfinderMob {
    public static final ResourceKey<net.minecraft.world.level.biome.Biome> HABITAT = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "glowing_mushroom_forest"));
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(Pufik.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE_START = SynchedEntityData.defineId(Pufik.class, EntityDataSerializers.INT);
    public static final int HAPPY = 1, SHY = 2;

    public Pufik(EntityType<? extends Pufik> type, Level level) {
        super(type, level);
        setPathfindingMalus(BlockPathTypes.WATER, -1);
        setPathfindingMalus(BlockPathTypes.LAVA, -1);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.MOVEMENT_SPEED, .16).add(Attributes.FOLLOW_RANGE, 8);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(EMOTE, 0);
        entityData.define(EMOTE_START, 0);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new SafeWanderGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    public int emote() { return entityData.get(EMOTE); }

    public float emoteStrength(float partialTicks) {
        if (emote() == 0) return 0;
        float elapsed = (float)(level().getGameTime() - entityData.get(EMOTE_START)) + partialTicks;
        if (elapsed < 0 || elapsed >= 24) return 0;
        return net.minecraft.util.Mth.sin((elapsed / 24f) * net.minecraft.util.Mth.PI);
    }

    private void playEmote(int value) {
        entityData.set(EMOTE, value);
        entityData.set(EMOTE_START, (int)level().getGameTime());
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 200 == 0 && getNavigation().isDone() && random.nextInt(3) == 0)
            playEmote(random.nextBoolean() ? HAPPY : SHY);
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            player.displayClientMessage(Component.translatable("message.dudunka.pufik_greeting"), true);
            playEmote(HAPPY);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }

    /** The biome and dimension are both checked because datapacks can reuse biome IDs elsewhere. */
    public static boolean canSpawn(EntityType<Pufik> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        return inHabitat(level, pos) && safeGround(level, pos);
    }

    public static boolean inHabitat(ServerLevelAccessor level, BlockPos pos) {
        return level.getLevel().dimension().equals(MushroomVale.LEVEL) && level.getBiome(pos).is(HABITAT);
    }

    public static boolean safeGround(ServerLevelAccessor level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below()) || !level.hasChunkAt(feet.above())) return false;
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || !floor.getFluidState().isEmpty()
                || hazard(floor) || !clear(level, feet) || !clear(level, feet.above())) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = feet.relative(direction);
            if (!level.hasChunkAt(adjacent) || !level.hasChunkAt(adjacent.below(2))) return false;
            BlockState nextFloor = level.getBlockState(adjacent.below());
            if (!nextFloor.getFluidState().isEmpty() || hazard(nextFloor) || !level.getFluidState(adjacent).isEmpty()
                    || hazard(level.getBlockState(adjacent))) return false;
            if (!nextFloor.isFaceSturdy(level, adjacent.below(), Direction.UP)
                    && !level.getBlockState(adjacent.below(2)).isFaceSturdy(level, adjacent.below(2), Direction.UP)) return false;
        }
        return true;
    }

    private static boolean clear(ServerLevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty() && !hazard(state);
    }

    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE);
    }

    private class SafeWanderGoal extends Goal {
        private BlockPos target;
        private SafeWanderGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (!(level() instanceof ServerLevel server) || random.nextInt(100) != 0) return false;
            BlockPos origin = blockPosition();
            for (int i = 0; i < 8; i++) {
                int x = origin.getX() + random.nextInt(11) - 5;
                int z = origin.getZ() + random.nextInt(11) - 5;
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!server.hasChunkAt(column)) continue;
                BlockPos candidate = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (Math.abs(candidate.getY() - origin.getY()) > 1 || !safeGround(server, candidate)) continue;
                target = candidate;
                return true;
            }
            return false;
        }
        @Override public void start() {
            if (target != null) getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, .8);
        }
        @Override public boolean canContinueToUse() { return !getNavigation().isDone(); }
        @Override public void stop() { target = null; getNavigation().stop(); }
    }
}
