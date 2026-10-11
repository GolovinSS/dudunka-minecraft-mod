package io.github.golovinss.dudunka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small exploration-driven population support for old Vale chunks whose CREATURE cap is occupied. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeWildlifePopulation {
    static final int COOLDOWN_TICKS = 400;
    static final int CANDIDATE_CHECKS = 12;
    static final double MOVE_DISTANCE_SQR = 64.0;
    static final double LOCAL_RADIUS = 48.0;
    static final int TOTAL_LIMIT = 12;
    static final int PUFIK_LIMIT = 4;
    static final int SPIDER_LIMIT = 4;
    static final int JUMPER_LIMIT = 6;

    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();

    private ValeWildlifePopulation() {}

    private static final class State {
        long lastCycle = Long.MIN_VALUE;
        final Map<UUID, BlockPos> sampledPositions = new HashMap<>();
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(MushroomVale.LEVEL)) return;
        State state = LEVELS.computeIfAbsent(level, ignored -> new State());
        long now = level.getGameTime();
        if (!cooldownElapsed(state.lastCycle, now)) return;
        state.lastCycle = now;

        List<ServerPlayer> players = level.players();
        state.sampledPositions.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));

        List<ServerPlayer> explorers = new ArrayList<>();
        for (ServerPlayer player : players) {
            BlockPos currentPos = player.blockPosition();
            BlockPos previous = state.sampledPositions.put(player.getUUID(), currentPos);
            if (!player.isSpectator() && previous != null
                    && previous.distSqr(currentPos) >= MOVE_DISTANCE_SQR)
                explorers.add(player);
        }

        if (!mayAssist(level) || explorers.isEmpty()) return;

        // One explorer and one small group per level per cooldown, even when player ranges overlap.
        ServerPlayer explorer = explorers.get(level.random.nextInt(explorers.size()));
        assistNear(level, explorer, level.random);
    }

    public static boolean cooldownElapsed(long lastCycle, long now) {
        return lastCycle == Long.MIN_VALUE || now - lastCycle >= COOLDOWN_TICKS;
    }

    public static boolean mayAssist(ServerLevel level) {
        return !level.isClientSide && level.dimension().equals(MushroomVale.LEVEL)
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    static int[] nearbyCounts(ServerLevel level, BlockPos center) {
        AABB area = new AABB(center).inflate(LOCAL_RADIUS, 24, LOCAL_RADIUS);
        int[] counts = new int[4]; // Pufik, spider, jumper, total
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (mob instanceof Pufik) { counts[0]++; counts[3]++; }
            else if (mob instanceof ValeSpider) { counts[1]++; counts[3]++; }
            else if (mob instanceof FluffyJumper) { counts[2]++; counts[3]++; }
        }
        return counts;
    }

    public static boolean underPopulationLimits(int[] counts, EntityType<?> type) {
        if (counts[3] >= TOTAL_LIMIT) return false;
        if (type == DudunkaMod.PUFIK.get()) return counts[0] < PUFIK_LIMIT;
        if (type == DudunkaMod.VALE_SPIDER.get()) return counts[1] < SPIDER_LIMIT;
        if (type == DudunkaMod.FLUFFY_JUMPER.get()) return counts[2] < JUMPER_LIMIT;
        return false;
    }

    public static void assistNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (!mayAssist(level)) return;
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < CANDIDATE_CHECKS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = 12 + random.nextInt(21);
            int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
            BlockPos column = new BlockPos(x, origin.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.hasChunkAt(feet) || nearAnyPlayer(level, feet, 8)) continue;

            int[] counts = nearbyCounts(level, origin);
            EntityType<? extends Mob> type = typeForBiome(level, feet, random, counts);
            if (type == null) continue;
            if (!underPopulationLimits(counts, type)) continue;
            spawnGroup(level, feet, type, random, counts);
            return;
        }
    }

    private static EntityType<? extends Mob> typeForBiome(ServerLevel level, BlockPos pos, RandomSource random, int[] counts) {
        if (Pufik.inHabitat(level, pos)) {
            boolean pufikAvailable = counts[0] < PUFIK_LIMIT;
            boolean spiderAvailable = counts[1] < SPIDER_LIMIT;
            if (!pufikAvailable) return spiderAvailable ? DudunkaMod.VALE_SPIDER.get() : null;
            if (!spiderAvailable) return DudunkaMod.PUFIK.get();
            return random.nextInt(4) == 0 ? DudunkaMod.VALE_SPIDER.get() : DudunkaMod.PUFIK.get();
        }
        if (ValeSpider.habitat(level, pos)) return DudunkaMod.VALE_SPIDER.get();
        if (FluffyJumper.habitat(level, pos)) return DudunkaMod.FLUFFY_JUMPER.get();
        return null;
    }

    private static void spawnGroup(ServerLevel level, BlockPos origin, EntityType<? extends Mob> type,
                                   RandomSource random, int[] initialCounts) {
        int count = groupSize(type, initialCounts, random);
        for (int i = 0; i < count; i++) {
            BlockPos feet = origin.offset(i == 0 ? 0 : random.nextInt(5) - 2, 0, i == 0 ? 0 : random.nextInt(5) - 2);
            if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below()) || nearAnyPlayer(level, feet, 8)
                    || !safeCandidate(level, type, feet, random)
                    || !level.noCollision(type.getAABB(feet.getX() + .5, feet.getY(), feet.getZ() + .5))) continue;
            Mob mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, random.nextFloat() * 360, 0);
            if (!net.minecraftforge.event.ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
                mob.discard();
                continue;
            }
            net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(feet),
                    MobSpawnType.NATURAL, null, null);
            if (!level.tryAddFreshEntityWithPassengers(mob)) mob.discard();
        }
    }

    public static int groupSize(EntityType<?> type, int[] counts, RandomSource random) {
        int speciesCount = type == DudunkaMod.PUFIK.get() ? counts[0]
                : type == DudunkaMod.VALE_SPIDER.get() ? counts[1]
                : type == DudunkaMod.FLUFFY_JUMPER.get() ? counts[2] : Integer.MAX_VALUE;
        int speciesLimit = type == DudunkaMod.PUFIK.get() ? PUFIK_LIMIT
                : type == DudunkaMod.VALE_SPIDER.get() ? SPIDER_LIMIT
                : type == DudunkaMod.FLUFFY_JUMPER.get() ? JUMPER_LIMIT : 0;
        if (speciesCount >= speciesLimit || counts[3] >= TOTAL_LIMIT) return 0;
        int desired = type == DudunkaMod.FLUFFY_JUMPER.get() ? 2 + random.nextInt(2) : 1 + random.nextInt(2);
        return Math.min(desired, Math.min(speciesLimit - speciesCount, TOTAL_LIMIT - counts[3]));
    }

    static boolean safeCandidate(ServerLevel level, EntityType<? extends Mob> type, BlockPos feet, RandomSource random) {
        return level.hasChunkAt(feet) && level.hasChunkAt(feet.below())
                && SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, feet, random);
    }

    public static boolean nearAnyPlayer(ServerLevel level, BlockPos pos, double radius) {
        double limit = radius * radius;
        for (ServerPlayer player : level.players())
            if (player.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) < limit) return true;
        return false;
    }
}
