package io.github.golovinss.dudunka;

import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
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

/** Independent scholarly Vale inhabitant. All movement and dialogue run on the server. */
public class WiseFlyAgaric extends PathfinderMob {
    public static final int GREETING = 1, THINK = 2, READ = 3;
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(WiseFlyAgaric.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> EMOTE_START = SynchedEntityData.defineId(WiseFlyAgaric.class, EntityDataSerializers.LONG);
    private int socialCooldown;
    private BlockPos cottageOrigin;
    private Rotation cottageRotation = Rotation.NONE;
    public BlockPos cottageOrigin() { return cottageOrigin; }
    public Rotation cottageRotation() { return cottageRotation; }
    public BlockPos cottageHome() { return cottageOrigin == null ? null : cottageOrigin.offset(new BlockPos(0, 1, -1).rotate(cottageRotation)); }
    public void setCottage(BlockPos origin, Rotation rotation) {
        cottageOrigin = origin.immutable(); cottageRotation = rotation;
        setPersistenceRequired();
        ((GroundPathNavigation)getNavigation()).setCanOpenDoors(true);
        restrictTo(cottageHome(), 10);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (cottageOrigin != null) {
            tag.putLong("WiseCottageOrigin", cottageOrigin.asLong());
            tag.putInt("WiseCottageRotation", cottageRotation.ordinal());
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cottageOrigin = null;
        int rotation = tag.getInt("WiseCottageRotation");
        if (level().dimension().equals(MushroomVale.LEVEL) && tag.contains("WiseCottageOrigin", Tag.TAG_LONG)
                && rotation >= 0 && rotation < 4) setCottage(BlockPos.of(tag.getLong("WiseCottageOrigin")), Rotation.values()[rotation]);
    }

    public WiseFlyAgaric(EntityType<? extends WiseFlyAgaric> type, Level level) {
        super(type, level);
        moveControl = new MoveControl(this) {
            @Override public void setWantedPosition(double x, double y, double z, double speed) {
                if (cottageOrigin != null) {
                    var door = cottageOrigin.offset(new BlockPos(0,1,-3).rotate(cottageRotation));
                    if (level().hasChunkAt(door) && Math.abs(y-door.getY())<=1) {
                        var state=level().getBlockState(door);
                        if (state.is(BlockTags.WOODEN_DOORS) && state.getValue(DoorBlock.OPEN)) {
                            var bounds=state.getCollisionShape(level(),door).bounds();
                            // A .72-wide body fits the .8125 opening, but clips its panel at block center.
                            // Align the approach and doorway waypoints with the free aperture's center.
                            if (bounds.getXsize()<.5 && Math.abs(x-door.getX()-.5)<.25 && Math.abs(z-door.getZ()-.5)<2)
                                x=door.getX()+(bounds.minX<.5?(1+bounds.maxX)/2:bounds.minX/2);
                            if (bounds.getZsize()<.5 && Math.abs(z-door.getZ()-.5)<.25 && Math.abs(x-door.getX()-.5)<2)
                                z=door.getZ()+(bounds.minZ<.5?(1+bounds.maxZ)/2:bounds.minZ/2);
                        }
                    }
                }
                super.setWantedPosition(x,y,z,speed);
            }
        };
        for (var danger : new BlockPathTypes[]{BlockPathTypes.WATER, BlockPathTypes.LAVA,
                BlockPathTypes.DAMAGE_FIRE, BlockPathTypes.DANGER_FIRE, BlockPathTypes.DAMAGE_OTHER})
            setPathfindingMalus(danger, -1);
    }
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override protected void followThePath() {
                if (cottageOrigin == null) { super.followThePath(); return; }
                // Follow each checked node without corner cutting. Forge's generic width-based
                // waypoint threshold is asymmetric around an open door's free aperture.
                var target=path.getNextEntityPos(WiseFlyAgaric.this);
                if (Math.abs(getX()-target.x)<=.2 && Math.abs(getZ()-target.z)<=.2
                        && Math.abs(getY()-getGroundY(target))<.75) path.advance();
                doStuckDetection(getTempMobPos());
            }
            @Override public Path createPath(BlockPos target, int accuracy) {
                if (cottageOrigin != null && level instanceof ServerLevel server
                        && (!server.hasChunkAt(target) || !WiseCottageResidency.loadedSquare(server,blockPosition(),16))) return null;
                return super.createPath(target,accuracy);
            }
            @Override protected Path createPath(java.util.Set<BlockPos> targets, int range, boolean above, int accuracy, float followRange) {
                if (cottageOrigin != null && level instanceof ServerLevel server
                        && !WiseCottageResidency.loadedSquare(server,blockPosition(),(int)(followRange+range))) return null;
                return super.createPath(targets,range,above,accuracy,followRange);
            }
        };
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, .12).add(Attributes.FOLLOW_RANGE, 8);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(EMOTE, 0); entityData.define(EMOTE_START, 0L);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new CottageDoorGoal());
        goalSelector.addGoal(1, new CottageWalkGoal());
        goalSelector.addGoal(1, new LocalStrollGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6, 1));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    public int emote() { return entityData.get(EMOTE); }
    public float emoteStrength(float partial) {
        float elapsed = level().getGameTime() - entityData.get(EMOTE_START) + partial;
        return emote() == 0 || elapsed < 0 || elapsed >= emoteDuration() ? 0 : Mth.sin(elapsed * Mth.PI / emoteDuration());
    }
    private void playEmote(int value) {
        entityData.set(EMOTE, value); entityData.set(EMOTE_START, level().getGameTime());
    }
    public int emoteDuration() { return emote() == GREETING ? 40 : 100; }
    private boolean performing() { return emoteStrength(0) > 0 || level().getGameTime() == entityData.get(EMOTE_START) && emote() != 0; }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (socialCooldown > 0) socialCooldown--;
        if (tickCount % 160 == 0 && getNavigation().isDone() && !performing() && (cottageOrigin == null || blockPosition().distSqr(cottageHome()) <= 4) && random.nextInt(3) == 0)
            playEmote(random.nextBoolean() ? THINK : READ);
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && socialCooldown == 0) {
            player.displayClientMessage(Component.translatable("message.dudunka.wise_fly_agaric." + random.nextInt(6)), true);
            getNavigation().stop();
            getLookControl().setLookAt(player, 30, 60);
            playEmote(GREETING); socialCooldown = 80;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public boolean canTrample(net.minecraft.world.level.block.state.BlockState state, BlockPos pos, float fallDistance) { return false; }
    @Override public int getMaxFallDistance() { return 1; }
    @Override public void setTarget(LivingEntity target) { super.setTarget(null); }
    @Override public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override public int getMaxHeadXRot() { return 60; }

    public static boolean habitat(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getLevel().dimension().equals(MushroomVale.LEVEL) || !level.hasChunkAt(pos)) return false;
        var key = level.getBiome(pos).unwrapKey().orElse(null);
        return key != null && key.location().getNamespace().equals(DudunkaMod.ID)
                && key.location().getPath().equals("memory_forest");
    }
    public static boolean canSpawn(EntityType<WiseFlyAgaric> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        if (!habitat(level, pos) || !Pufik.safeGround(level, pos)) return false;
        // Soil, including sheltered mushroom understory, rather than roofs/caps or deep caves.
        var floor = level.getBlockState(pos.below());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return (floor.is(BlockTags.DIRT) || floor.is(Blocks.MOSS_BLOCK))
                && pos.getY() >= surface - 12 && pos.getY() <= surface
                && level.getWorldBorder().isWithinBounds(type.getAABB(pos.getX()+.5, pos.getY(), pos.getZ()+.5));
    }

    /** Dry supported body volume at a path node, including the existing porch's bottom slabs.
     * Wooden doors are admitted only to route planning; CottageDoorGoal operates the home door during walking.
     */
    public static boolean safeCottageNode(ServerLevel level, BlockPos node) {
        if (!WiseCottageResidency.loadedSquare(level, node, 1) || level.isOutsideBuildHeight(node.below(2))
                || level.isOutsideBuildHeight(node.above(2))) return false;
        var floor = level.getBlockState(node.below());
        boolean slab = floor.getBlock() instanceof SlabBlock && floor.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        if ((!floor.isFaceSturdy(level, node.below(), Direction.UP) && !slab) || hazard(floor)) return false;
        double y = WalkNodeEvaluator.getFloorLevel(level, node);
        var box = new AABB(node.getX()+.14, y+.001, node.getZ()+.14, node.getX()+.86, y+1.15, node.getZ()+.86);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        for (var p : BlockPos.betweenClosed(node.offset(-1,-1,-1), node.offset(1,2,1))) {
            var state = level.getBlockState(p);
            if (hazard(state)) return false;
            if (state.getBlock() instanceof DoorBlock && state.is(BlockTags.WOODEN_DOORS)) continue;
            if (Shapes.joinIsNotEmpty(Shapes.create(box), state.getCollisionShape(level,p).move(p.getX(),p.getY(),p.getZ()), BooleanOp.AND)) return false;
        }
        for (var direction : Direction.Plane.HORIZONTAL) {
            var adjacent = node.relative(direction);
            double ground = WalkNodeEvaluator.getFloorLevel(level, adjacent);
            if (ground < y-1 || level.getBlockState(adjacent.below()).getCollisionShape(level,adjacent.below()).isEmpty()) {
                var lower = adjacent.below(2);
                if (!level.getBlockState(lower).isFaceSturdy(level,lower,Direction.UP) || y-(lower.getY()+1)>1) return false;
            }
        }
        return true;
    }
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE);
    }
    /** Open the known entrance before contact; keep it open until the resident clears the doorway. */
    private class CottageDoorGoal extends Goal {
        private BlockPos door;
        @Override public boolean canUse() {
            if (cottageOrigin == null || getNavigation().isDone()) return false;
            door = cottageOrigin.offset(new BlockPos(0,1,-3).rotate(cottageRotation));
            if (!level().hasChunkAt(door) || distanceToSqr(door.getX()+.5,getY(),door.getZ()+.5)>4) return false;
            var state = level().getBlockState(door);
            return state.is(BlockTags.WOODEN_DOORS) && !state.getValue(DoorBlock.OPEN);
        }
        @Override public void start() {
            var state=level().getBlockState(door);
            if (state.getBlock() instanceof DoorBlock block) block.setOpen(WiseFlyAgaric.this,level(),state,door,true);
        }
        private boolean inDoorway() { return getBoundingBox().intersects(new AABB(door).inflate(.1,1,.1)); }
        @Override public boolean canContinueToUse() {
            return inDoorway() || !getNavigation().isDone() && distanceToSqr(door.getX()+.5,getY(),door.getZ()+.5)<6.25;
        }
        @Override public void stop() {
            if (!level().hasChunkAt(door) || inDoorway()) return;
            var state=level().getBlockState(door);
            if (state.getBlock() instanceof DoorBlock block) block.setOpen(WiseFlyAgaric.this,level(),state,door,false);
        }
    }
    private class CottageWalkGoal extends Goal {
        private Path route;
        private int remaining, nextDecision;
        private boolean returning;
        CottageWalkGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (cottageOrigin == null || !(level() instanceof ServerLevel server) || performing()) return false;
            if (nextDecision == 0) nextDecision = tickCount + 240;
            if (tickCount < nextDecision) return false;
            nextDecision = tickCount + 100;
            // Vanilla builds a pathfinding region with FOLLOW_RANGE + 8 around the mob.
            // Check that entire region BEFORE createPath, which otherwise requests missing chunks.
            if (!WiseCottageResidency.loadedSquare(server, blockPosition(), 16)) return false;
            boolean atHome = blockPosition().distSqr(cottageHome()) <= 4;
            for (int attempt = 0; attempt < (atHome ? 8 : 1); attempt++) {
                var target = atHome ? cottageOrigin.offset(new BlockPos(random.nextInt(7)-3, 1, -6-random.nextInt(3)).rotate(cottageRotation)) : cottageHome();
                for (int dy : new int[]{0, -1, 1}) {
                    var candidate = target.offset(0,dy,0);
                    if (!safeCottageNode(server,candidate)) continue;
                    var path = getNavigation().createPath(candidate, 0);
                    if (path == null || !path.canReach() || path.getNodeCount() > 32) continue;
                    boolean safe = true;
                    for (int n = 0; n < path.getNodeCount(); n++) {
                        var node = path.getNode(n).asBlockPos();
                        if (node.distSqr(cottageHome()) > 100 || !safeCottageNode(server,node)) { safe = false; break; }
                    }
                    if (safe) { route = path; returning = !atHome; return true; }
                }
            }
            return false;
        }
        @Override public void start() { remaining = 400; getNavigation().moveTo(route, .8); }
        @Override public void tick() {
            remaining--;
            var path = getNavigation().getPath();
            if (!WiseCottageResidency.loadedSquare((ServerLevel)level(),blockPosition(),16)
                    || path != null && !path.isDone() && !safeCottageNode((ServerLevel)level(),path.getNextNode().asBlockPos())) getNavigation().stop();
        }
        @Override public boolean canContinueToUse() { return remaining > 0 && !performing() && !getNavigation().isDone(); }
        @Override public void stop() {
            route = null; getNavigation().stop();
            if (returning && blockPosition().distSqr(cottageHome())<=4 && !performing())
                playEmote(random.nextBoolean()?THINK:READ);
            nextDecision = tickCount + 240 + random.nextInt(241);
        }
    }

    /** Bounded local choices; all social movement uses the same dry-ground checks. */
    private class LocalStrollGoal extends Goal {
        private net.minecraft.world.level.pathfinder.Path route;
        private int remaining;
        LocalStrollGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (cottageOrigin != null || !(level() instanceof ServerLevel server) || performing() || random.nextInt(100) != 0) return false;
            BlockPos origin = blockPosition();
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = origin.getX()+random.nextInt(9)-4, z = origin.getZ()+random.nextInt(9)-4;
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!server.hasChunkAt(column)) continue;
                // Search near our feet, so giant mushroom heightmaps do not strand understory walkers.
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos candidate = column.offset(0, dy, 0);
                    if (!Pufik.safeGround(server, candidate) || !server.getWorldBorder().isWithinBounds(candidate)) continue;
                    var path = getNavigation().createPath(candidate, 0);
                    if (path == null || !path.canReach()) continue;
                    boolean safe = true;
                    for (int n = 0; n < path.getNodeCount(); n++)
                        if (!Pufik.safeGround(server, path.getNode(n).asBlockPos())) { safe = false; break; }
                    if (!safe) continue;
                    route = path; return true;
                }
            }
            return false;
        }
        @Override public void start() {
            remaining = 80;
            getNavigation().moveTo(route, .8);
        }
        @Override public void tick() {
            remaining--;
            var path = getNavigation().getPath();
            if (path != null && !path.isDone() && level() instanceof ServerLevel server
                    && !Pufik.safeGround(server, path.getNextNode().asBlockPos())) getNavigation().stop();
        }
        @Override public boolean canContinueToUse() { return remaining > 0 && !performing() && !getNavigation().isDone(); }
        @Override public void stop() { route = null; getNavigation().stop(); }
    }
}
