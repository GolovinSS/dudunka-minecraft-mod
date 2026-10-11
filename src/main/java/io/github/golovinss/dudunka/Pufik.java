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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
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
    private BlockPos cottageOrigin;
    private Rotation cottageRotation = Rotation.NONE;
    private int homeDecision;
    public BlockPos cottageOrigin() { return cottageOrigin; }
    public Rotation cottageRotation() { return cottageRotation; }
    public BlockPos cottageHome() { return cottageOrigin == null ? null : cottageOrigin.offset(new BlockPos(0, 1, -1).rotate(cottageRotation)); }
    public void setCottage(BlockPos origin, Rotation rotation) {
        cottageOrigin = origin.immutable(); cottageRotation = rotation; setPersistenceRequired();
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) ground.setCanOpenDoors(true);
        restrictTo(cottageHome(), 10);
    }
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (cottageOrigin != null) { tag.putLong("PufikCottageOrigin", cottageOrigin.asLong()); tag.putInt("PufikCottageRotation", cottageRotation.ordinal()); }
    }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag); cottageOrigin = null;
        int rotation = tag.getInt("PufikCottageRotation");
        if (level().dimension().equals(MushroomVale.LEVEL) && tag.contains("PufikCottageOrigin", net.minecraft.nbt.Tag.TAG_LONG)
                && rotation >= 0 && rotation < 4) setCottage(BlockPos.of(tag.getLong("PufikCottageOrigin")), Rotation.values()[rotation]);
    }

    public Pufik(EntityType<? extends Pufik> type, Level level) {
        super(type, level);
        moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this) {
            @Override public void setWantedPosition(double x, double y, double z, double speed) {
                var target = cottageWaypoint(new net.minecraft.world.phys.Vec3(x, y, z));
                super.setWantedPosition(target.x, target.y, target.z, speed);
            }
        };
        setPathfindingMalus(BlockPathTypes.WATER, -1);
        setPathfindingMalus(BlockPathTypes.LAVA, -1);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1);
    }

    /** Center the body in the open door aperture rather than against its hinged panel. */
    private net.minecraft.world.phys.Vec3 cottageWaypoint(net.minecraft.world.phys.Vec3 target) {
        if (cottageOrigin == null) return target;
        var door = cottageOrigin.offset(new BlockPos(0, 1, -2).rotate(cottageRotation));
        if (!level().hasChunkAt(door) || Math.abs(target.y - door.getY()) > 1) return target;
        var state = level().getBlockState(door);
        if (!state.is(BlockTags.WOODEN_DOORS) || !state.getValue(DoorBlock.OPEN)) return target;
        var bounds = state.getCollisionShape(level(), door).bounds();
        double x = target.x, z = target.z;
        if (bounds.getXsize() < .5 && Math.abs(x - door.getX() - .5) < .25 && Math.abs(z - door.getZ() - .5) < 2)
            x = door.getX() + (bounds.minX < .5 ? (1 + bounds.maxX) / 2 : bounds.minX / 2);
        if (bounds.getZsize() < .5 && Math.abs(z - door.getZ() - .5) < .25 && Math.abs(x - door.getX() - .5) < 2)
            z = door.getZ() + (bounds.minZ < .5 ? (1 + bounds.maxZ) / 2 : bounds.minZ / 2);
        return new net.minecraft.world.phys.Vec3(x, target.y, z);
    }

    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level) {
            @Override protected void followThePath() {
                if (cottageOrigin == null) { super.followThePath(); return; }
                // Keep checked nodes in order. Forge's width-offset threshold and corner
                // cutting can skip the aligned approach, sending the body into the panel.
                var target = cottageWaypoint(path.getNextEntityPos(Pufik.this));
                if (Math.abs(getX() - target.x) <= .2 && Math.abs(getZ() - target.z) <= .2
                        && Math.abs(getY() - getGroundY(target)) < .75) path.advance();
                doStuckDetection(getTempMobPos());
            }
            @Override protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<BlockPos> targets,
                    int range, boolean above, int accuracy, float followRange) {
                if (cottageOrigin != null && level instanceof ServerLevel server
                        && !WiseCottageResidency.loadedSquare(server, blockPosition(), (int)(followRange + range))) return null;
                return super.createPath(targets, range, above, accuracy, followRange);
            }
        };
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
        goalSelector.addGoal(1, new CottageGoal());
        goalSelector.addGoal(2, new SafeWanderGoal());
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 5));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
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

    public static boolean safeResidentNode(ServerLevel level, BlockPos feet) {
        if (!WiseCottageResidency.loadedSquare(level, feet, 1) || !level.hasChunkAt(feet.below())) return false;
        var floor = level.getBlockState(feet.below());
        boolean slab = floor.getBlock() instanceof SlabBlock && floor.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        if ((!floor.isFaceSturdy(level, feet.below(), Direction.UP) && !slab) || hazard(floor)) return false;
        double elevation = net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getFloorLevel(level, feet);
        var body = getAABBAt(feet).move(0, elevation - feet.getY() + .001, 0);
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || hazard(state)) return false;
            if (state.getBlock() instanceof DoorBlock && state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) continue;
            var shape=state.getCollisionShape(level,pos);
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.create(body),
                    shape.move(pos.getX(),pos.getY(),pos.getZ()),net.minecraft.world.phys.shapes.BooleanOp.AND)) return false;
        }
        return level.getWorldBorder().isWithinBounds(body);
    }
    private static net.minecraft.world.phys.AABB getAABBAt(BlockPos pos) {
        return new net.minecraft.world.phys.AABB(pos.getX()+.16,pos.getY(),pos.getZ()+.16,pos.getX()+.84,pos.getY()+.62,pos.getZ()+.84);
    }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target) { return false; }
    @Override public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) { return false; }
    @Override public int getMaxFallDistance() { return 1; }
    @Override public void setTarget(net.minecraft.world.entity.LivingEntity target) { super.setTarget(null); }

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
            if (cottageOrigin != null || !(level() instanceof ServerLevel server) || random.nextInt(100) != 0) return false;
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

    private class CottageGoal extends Goal {
        private net.minecraft.world.level.pathfinder.Path path;
        private BlockPos door;
        private int timeout;
        CottageGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (cottageOrigin == null || !(level() instanceof ServerLevel server) || !getNavigation().isDone()) return false;
            if (tickCount < homeDecision || !WiseCottageResidency.loadedSquare(server, blockPosition(), 12)) return false;
            homeDecision = tickCount + 240 + random.nextInt(241);
            var home = cottageHome(); boolean atHome = blockPosition().distSqr(home) <= 3;
            BlockPos target = atHome
                    ? cottageOrigin.offset(new BlockPos(random.nextInt(5)-2, 1, -5-random.nextInt(3)).rotate(cottageRotation))
                    : home;
            for (int dy : new int[]{0,-1,1}) {
                var candidate = target.offset(0,dy,0);
                if (!safeResidentNode(server,candidate)) continue;
                var candidatePath = getNavigation().createPath(candidate,0);
                if (candidatePath == null || !candidatePath.canReach() || candidatePath.getNodeCount()>24) continue;
                boolean safe = true;
                for (int n=0;n<candidatePath.getNodeCount();n++) {
                    var node = candidatePath.getNode(n).asBlockPos();
                    if (node.distSqr(home)>64 || !safeResidentNode(server,node)) {
                        safe=false; break;
                    }
                }
                if (safe) { path=candidatePath; return true; }
            }
            return false;
        }
        @Override public void start() {
            timeout=280; door=cottageOrigin.offset(new BlockPos(0,1,-2).rotate(cottageRotation));
            if (level().hasChunkAt(door)) { var state=level().getBlockState(door); if(state.getBlock() instanceof DoorBlock block && !state.getValue(DoorBlock.OPEN)) block.setOpen(Pufik.this,level(),state,door,true); }
            getNavigation().moveTo(path,.72);
        }
        @Override public void tick() {
            timeout--;
            if (!(level() instanceof ServerLevel server) || !WiseCottageResidency.loadedSquare(server,blockPosition(),12)) { getNavigation().stop(); return; }
            var active=getNavigation().getPath();
            if (active!=null && !active.isDone() && !safeResidentNode(server,active.getNextNode().asBlockPos())) getNavigation().stop();
        }
        @Override public boolean canContinueToUse() { return timeout>0 && !getNavigation().isDone(); }
        @Override public void stop() {
            getNavigation().stop(); path=null;
            if (door!=null && level().hasChunkAt(door) && !getBoundingBox().intersects(new net.minecraft.world.phys.AABB(door).inflate(.1,1,.1))) {
                var state=level().getBlockState(door); if(state.getBlock() instanceof DoorBlock block && state.getValue(DoorBlock.OPEN)) block.setOpen(Pufik.this,level(),state,door,false);
            }
        }
    }
}
