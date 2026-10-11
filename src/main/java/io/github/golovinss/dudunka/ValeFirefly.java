package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Independent ambient inhabitant. Flight uses only a bounded, loaded local volume. */
public class ValeFirefly extends FlyingMob {
    public static final int GOLDEN = 0, TURQUOISE = 1, VIOLET = 2;
    public static final double RADIUS = 6, VERTICAL_RANGE = 3;
    public static final int NATURAL_PLAYER_RANGE = 48;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(ValeFirefly.class, EntityDataSerializers.INT);
    private Vec3 home, destination;
    private FireflySpawnGroup flightGroup;
    private long naturalSpawnTick=Long.MIN_VALUE;
    // LivingEntity zeros sub-.003 velocity components before AI each tick. Keep
    // steering momentum separately so gentle daytime acceleration can accumulate.
    private Vec3 flightVelocity = Vec3.ZERO;
    private boolean flightInitialized;
    private int nextFlight;
    private Vec3 circleCenter;
    private int circleTicks;
    private double circleAngle;

    public ValeFirefly(EntityType<? extends ValeFirefly> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2).add(Attributes.FOLLOW_RANGE, 6);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VARIANT, GOLDEN); }
    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int value) { entityData.set(VARIANT, Mth.clamp(value, GOLDEN, VIOLET)); }
    public Vec3 flightHome() { return home; }
    /** Server-only pack identity; ordinary reloads can regroup with nearby flies. */
    public FireflySpawnGroup flightGroup() { return flightGroup; }
    public static boolean activeTime(long time) { long t = Math.floorMod(time, 24000); return t >= 12000 && t < 23000; }
    public static boolean habitat(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getLevel().dimension().equals(MushroomVale.LEVEL) || !level.hasChunkAt(pos)) return false;
        var key = level.getBiome(pos).unwrapKey().orElse(null);
        return key != null && key.location().getNamespace().equals(DudunkaMod.ID)
                && switch (key.location().getPath()) {
                    case "glowing_mushroom_forest", "pink_meadows", "memory_forest" -> true;
                    default -> false;
                };
    }
    public static boolean canSpawn(EntityType<ValeFirefly> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        if (!habitat(level, pos) || !activeTime(level.getLevel().getDayTime())) return false;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        // The heightmap includes solid giant mushroom caps. Permit their nearby
        // understory too, but never deep caves or arbitrary high air.
        if (pos.getY() < surface-12 || pos.getY() > surface+4) return false;
        var player=level.getLevel().getNearestPlayer(pos.getX()+.5,pos.getY(),pos.getZ()+.5,-1,false);
        // NaturalSpawner already enforces its ordinary 24-block minimum. Keep
        // this tiny surface inhabitant's share of the ambient cap near explorers.
        if (player!=null && player.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>NATURAL_PLAYER_RANGE*NATURAL_PLAYER_RANGE) return false;
        double ground=groundBelow(level,Vec3.atBottomCenterOf(pos),5);
        return Double.isFinite(ground) && pos.getY()>=ground && pos.getY()<=ground+4
                && safeVolume(level, type.getAABB(pos.getX()+.5, pos.getY(), pos.getZ()+.5));
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CompoundTag tag) {
        SpawnGroupData inherited = super.finalizeSpawn(level, difficulty, reason, group, tag);
        // NaturalSpawner passes the data returned here to each successful member in this pack.
        // COMMAND/SUMMON deliberately leaves variant selection to its EntityTag NBT.
        if (reason != MobSpawnType.NATURAL) return inherited;
        var key = level.getBiome(blockPosition()).unwrapKey().orElse(null);
        String biome = key == null ? "" : key.location().getPath();
        naturalSpawnTick=level.getLevel().getGameTime();
        FireflySpawnGroup spawnGroup = group instanceof FireflySpawnGroup existing && existing.spawnedCount()<5
                ? existing : null;
        if (spawnGroup==null) {
            // Vanilla resets group data between its three local spawn loops,
            // even when rejected positions leave adjacent singletons/pairs.
            // Combine only same-tick newborns: no extra spawns or cap bypass.
            var newborns=level.getLevel().getEntitiesOfClass(ValeFirefly.class,getBoundingBox().inflate(6),
                    other->other!=this && other.naturalSpawnTick==naturalSpawnTick && other.flightGroup!=null
                            && other.flightGroup.spawnedCount()<5 && other.flightGroup.biome.equals(biome));
            if (!newborns.isEmpty()) spawnGroup=newborns.get(0).flightGroup;
        }
        if (spawnGroup==null) spawnGroup=new FireflySpawnGroup(biome);
        setVariant(spawnGroup.next(random));
        flightGroup=spawnGroup;
        return spawnGroup;
    }
    public static final class FireflySpawnGroup implements SpawnGroupData {
        private final String biome;
        private int firstColor = -1;
        private int spawned;
        public FireflySpawnGroup(String biome) { this.biome = biome; }
        public int spawnedCount() { return spawned; }
        public int next(RandomSource random) {
            int color;
            if (spawned == 0) {
                color = chooseVariant(biome, random);
                firstColor = color;
            } else if (spawned == 1) {
                // The second successfully finalized member is forced to a palette alternative.
                color = chooseVariant(biome, random, firstColor);
            } else {
                color = chooseVariant(biome, random);
            }
            spawned++;
            return color;
        }
    }
    public static int chooseVariant(String biome, RandomSource random) {
        int[] weights = palette(biome);
        return weightedColor(weights, random, -1);
    }
    private static int chooseVariant(String biome, RandomSource random, int excluded) {
        return weightedColor(palette(biome), random, excluded);
    }
    private static int[] palette(String biome) {
        return switch (biome) {
            case "pink_meadows" -> new int[]{70, 20, 10};
            case "memory_forest" -> new int[]{10, 20, 70};
            case "glowing_mushroom_forest" -> new int[]{55, 30, 15};
            default -> new int[]{1, 1, 1};
        };
    }
    private static int weightedColor(int[] weights, RandomSource random, int excluded) {
        int total = 0;
        for (int i = 0; i < weights.length; i++) if (i != excluded) total += weights[i];
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            if (i == excluded) continue;
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return GOLDEN;
    }
    @Override public int getMaxSpawnClusterSize() { return 5; }
    public boolean withinHome(Vec3 point) {
        return home != null && Math.abs(point.y-home.y) <= VERTICAL_RANGE
                && Mth.square(point.x-home.x)+Mth.square(point.z-home.z) <= RADIUS*RADIUS;
    }
    /** Check chunks BEFORE block/collision access; callers never load missing chunks. */
    public static boolean safeVolume(LevelAccessor level, AABB box) {
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
                BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            if (!level.hasChunkAt(p)) return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
                BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            var state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(net.minecraft.tags.BlockTags.FIRE)) return false;
        }
        return level.noCollision(box);
    }
    /** Small loaded-column probe, including understory terrain beneath a canopy. */
    public static double groundBelow(LevelAccessor level, Vec3 point, int depth) {
        BlockPos top=BlockPos.containing(point.x,point.y-.01,point.z);
        if (!level.hasChunkAt(top)) return Double.NaN;
        for (int i=0;i<depth && top.getY()-i>=level.getMinBuildHeight();i++) {
            BlockPos p=top.below(i);var state=level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(net.minecraft.tags.BlockTags.FIRE)) return Double.NaN;
            if (state.isFaceSturdy(level,p,net.minecraft.core.Direction.UP)) return p.getY()+1;
            if (!state.getCollisionShape(level,p).isEmpty()) return Double.NaN;
        }
        return Double.NaN;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = position();
        // Commands/dimension transfers may relocate an existing entity; establish a new local area there.
        if (position().distanceToSqr(home) > 256) { home = position(); destination = null; circleTicks=0;flightInitialized=false; }
        if (!flightInitialized) {
            double ground=groundBelow(level(),home,8);
            // Also lets existing ground-level FlightHome NBT take off after upgrading.
            if (Double.isFinite(ground) && home.y<ground+1.5) home=new Vec3(home.x,ground+2,home.z);
            flightInitialized=true;
        }
        setNoGravity(true);
        boolean active = activeTime(level().getDayTime());
        if (destination!=null && distanceToSqr(destination)<.12) nextFlight=Math.min(nextFlight,10);
        if (--nextFlight <= 0 && circleTicks==0) {
            nextFlight = active ? 40 + random.nextInt(60) : 100 + random.nextInt(80);
            Vec3 center = home;
            if (active && random.nextInt(4) == 0) {
                var neighbors = level().getEntitiesOfClass(ValeFirefly.class, getBoundingBox().inflate(4),
                        e -> e != this && e.isAlive() && (flightGroup==null || e.flightGroup==flightGroup));
                if (!neighbors.isEmpty()) center = neighbors.get(random.nextInt(neighbors.size())).position();
            }
            for (int i=0; i<6; i++) {
                Vec3 column = center.add((random.nextDouble()-.5)*6, 3, (random.nextDouble()-.5)*6);
                double ground=groundBelow(level(),column,10);
                Vec3 candidate = Double.isFinite(ground)
                        ? new Vec3(column.x,ground+1.2+random.nextDouble()*1.5,column.z)
                        : center.add((random.nextDouble()-.5)*4,(random.nextDouble()-.5)*2,(random.nextDouble()-.5)*4);
                if (withinHome(candidate) && safeVolume(level(), getBoundingBox().move(candidate.subtract(position())).inflate(.15))
                        && safeVolume(level(),getBoundingBox().expandTowards(candidate.subtract(position())))) {
                    destination = candidate; break;
                }
            }
            if (active && random.nextInt(8)==0) {
                double ground=groundBelow(level(),center,8);
                circleCenter=Double.isFinite(ground)?new Vec3(center.x,ground+2,center.z):center;
                circleAngle=Mth.atan2(getZ()-center.z,getX()-center.x);circleTicks=240+random.nextInt(80);
            }
        }
        if (circleTicks>0) {
            circleAngle+=.025;
            Vec3 candidate=circleCenter.add(Math.cos(circleAngle)*1.4,Math.sin(circleAngle*1.7)*.35,Math.sin(circleAngle)*1.4);
            if (withinHome(candidate) && safeVolume(level(),getBoundingBox().move(candidate.subtract(position())).inflate(.15))
                    && safeVolume(level(),getBoundingBox().expandTowards(candidate.subtract(position())))) destination=candidate;
            else circleTicks=1;
            if (--circleTicks==0) nextFlight=0;
        }
        Vec3 target = destination == null ? home : destination;
        double phase = (getUUID().getLeastSignificantBits() & 65535) * .001;
        Vec3 offset = target.add(0, Math.sin(tickCount*.09+phase)*.12, 0).subtract(position());
        Vec3 desired = offset.scale(.045);
        double speed = active ? .055 : .018;
        if (desired.length() > speed) desired = desired.normalize().scale(speed);
        flightVelocity=flightVelocity.scale(.85).add(desired.scale(.15));
        setDeltaMovement(flightVelocity);
        if (getDeltaMovement().horizontalDistanceSqr() > .00001) {
            float yaw = (float)(Mth.atan2(getDeltaMovement().z, getDeltaMovement().x)*180/Math.PI)-90;
            setYRot(getYRot()+Mth.clamp(Mth.wrapDegrees(yaw-getYRot()), -6, 6));
            yBodyRot = getYRot();
        }
    }
    @Override public void travel(Vec3 input) {
        if (!level().isClientSide) {
            Vec3 motion = isNoAi() ? Vec3.ZERO : getDeltaMovement();
            // Test the exact swept body. Inflating its lower face includes the
            // supporting floor at spawn and rejects every tiny takeoff step.
            if (!withinHome(position().add(motion)) || !safeVolume(level(), getBoundingBox().expandTowards(motion))) {
                Vec3 upward=new Vec3(0,Math.max(0,motion.y),0);
                motion=withinHome(position().add(upward)) && safeVolume(level(),getBoundingBox().expandTowards(upward))?upward:Vec3.ZERO;
                destination = null; nextFlight = 0;circleTicks=0;
            }
            flightVelocity=motion;
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
        }
        calculateEntityAnimation(false);
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public void setTarget(net.minecraft.world.entity.LivingEntity target) { super.setTarget(null); }
    @Override public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }
    @Override public void checkDespawn() {
        if (!level().isClientSide && !isPersistenceRequired() && !requiresCustomPersistence() && !hasCustomName()) {
            var player=level().getNearestPlayer(this,-1);
            if (player!=null && distanceToSqr(player)>NATURAL_PLAYER_RANGE*NATURAL_PLAYER_RANGE) {
                // Respect modpack despawn vetoes, just like Mob.checkDespawn.
                if (net.minecraftforge.event.ForgeEventFactory.canEntityDespawn(this,(net.minecraft.server.level.ServerLevel)level())
                        !=net.minecraftforge.eventbus.api.Event.Result.DENY) discard();
                else noActionTime=0;
                return;
            }
        }
        super.checkDespawn();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("ValeVariant", variant());
        if (home != null) { tag.putDouble("FlightHomeX",home.x); tag.putDouble("FlightHomeY",home.y); tag.putDouble("FlightHomeZ",home.z); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setVariant(tag.getInt("ValeVariant")); setNoGravity(true);
        if (tag.contains("FlightHomeX") && tag.contains("FlightHomeY") && tag.contains("FlightHomeZ")) {
            Vec3 saved = new Vec3(tag.getDouble("FlightHomeX"),tag.getDouble("FlightHomeY"),tag.getDouble("FlightHomeZ"));
            if (Double.isFinite(saved.x) && Double.isFinite(saved.y) && Double.isFinite(saved.z)) home = saved;
        }
        destination = null;flightGroup=null;naturalSpawnTick=Long.MIN_VALUE;flightVelocity=Vec3.ZERO;flightInitialized=false;circleTicks=0;
    }
}
