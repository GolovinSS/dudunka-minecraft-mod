package io.github.golovinss.dudunka;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/** One short, private, server-authoritative conversation per owner cooldown. */
public final class DudunkaDialogueGoal extends Goal {
    static final long MIN_INTERVAL = 20L * 60 * 5;
    static final long MAX_INTERVAL = 20L * 60 * 10;
    private static final long APPROACH_LIMIT = 20L * 30;
    private static final long WAVE_DURATION = 80;
    private static final double OWNER_RADIUS_SQR = 144;
    private static final double ARRIVAL_RADIUS_SQR = 6.25;
    private static final Map<MinecraftServer, Cooldowns> SERVERS = new WeakHashMap<>();

    enum Dialogue { BIG, MARUSYA }

    /** Transient scheduling only; no player, companion, or world save fields change. */
    static final class Cooldowns {
        private final Map<UUID, Long> next = new HashMap<>();

        boolean due(UUID owner, long now, net.minecraft.util.RandomSource random) {
            Long deadline = next.get(owner);
            if (deadline == null) {
                if (next.size() >= 2048) next.entrySet().removeIf(entry -> now >= entry.getValue());
                if (next.size() >= 2048) return false;
                deadline = now + interval(random);
                next.put(owner, deadline);
            }
            return now >= deadline;
        }

        boolean claim(UUID owner, long now, net.minecraft.util.RandomSource random) {
            if (!due(owner, now, random)) return false;
            next.put(owner, now + interval(random));
            return true;
        }

        void defer(UUID owner, long now) {
            if (next.containsKey(owner) && now >= next.get(owner)) next.put(owner, now + 100);
        }

        long deadline(UUID owner) { return next.getOrDefault(owner, Long.MIN_VALUE); }

        private static long interval(net.minecraft.util.RandomSource random) {
            return MIN_INTERVAL + random.nextInt((int)(MAX_INTERVAL - MIN_INTERVAL + 1));
        }
    }

    static synchronized Cooldowns cooldowns(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new Cooldowns());
    }

    static Dialogue choose(boolean marusyaNearby, boolean chooseMarusya) {
        return marusyaNearby && chooseMarusya ? Dialogue.MARUSYA : Dialogue.BIG;
    }

    static boolean marusyaNearby(Companion mob, ServerPlayer owner) {
        var bounds = owner.getBoundingBox().inflate(8);
        return !mob.level().getEntitiesOfClass(Companion.class, bounds, candidate -> candidate.isAlive()
                && candidate.kind == Kind.MARUSYA && mob.ownerId().equals(candidate.ownerId())
                && candidate.level() == mob.level() && candidate.distanceToSqr(owner) <= 64).isEmpty();
    }

    static boolean eligible(Companion mob, ServerPlayer owner) {
        return available(mob, owner) && mob.activity() == Activity.IDLE;
    }

    private static boolean available(Companion mob, ServerPlayer owner) {
        return mob.kind == Kind.DUDUNKA && mob.isAlive() && !mob.isNoAi()
                && mob.level() instanceof ServerLevel && !mob.level().isClientSide
                && mob.ownerId() != null && owner != null && mob.ownerId().equals(owner.getUUID())
                && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && !owner.isSleeping()
                && owner.level() == mob.level() && mob.distanceToSqr(owner) <= OWNER_RADIUS_SQR
                && !mob.staying() && !mob.atHomeMode() && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle()
                && !mob.isInWaterOrBubble() && !mob.isInLava() && !mob.recovering() && !mob.beingPetted()
                && mob.homeSceneTarget() == null && !mob.homeWelcomeEligible() && !mob.homeWelcomeRunning()
                && !mob.homeWelcomePending() && mob.openChestTarget() == null
                && WalkScenes.current(mob) == null && HomeAtmosphereScenes.current(mob) == null;
    }

    private final Companion mob;
    private final Cooldowns cooldowns;
    private final LongSupplier clock;
    private ServerPlayer owner;
    private net.minecraft.world.level.pathfinder.Path path;
    private Dialogue dialogue;
    private long nextCheck, startedAt, waveEnds;
    private boolean announced, arrived, cancelled;

    public DudunkaDialogueGoal(Companion mob) {
        this(mob, mob.level() instanceof ServerLevel level ? cooldowns(level.getServer()) : new Cooldowns(), mob.level()::getGameTime);
    }

    DudunkaDialogueGoal(Companion mob, Cooldowns cooldowns, LongSupplier clock) {
        this.mob = mob;
        this.cooldowns = cooldowns;
        this.clock = clock;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private long now() { return clock.getAsLong(); }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canUse() {
        long now = now();
        if (now < nextCheck) return false;
        nextCheck = now + 20;
        owner = mob.ownerPlayer() instanceof ServerPlayer player ? player : null;
        if (!eligible(mob, owner) || !cooldowns.due(owner.getUUID(), now, mob.getRandom())) return false;

        boolean hasMarusya = marusyaNearby(mob, owner);
        dialogue = choose(hasMarusya, mob.getRandom().nextBoolean());
        path = null;
        if (mob.distanceToSqr(owner) > ARRIVAL_RADIUS_SQR) {
            path = mob.getNavigation().createPath(owner, 0);
            if (path == null || !path.canReach() || !FamilyTravel.safePath(mob, path)) {
                cooldowns.defer(owner.getUUID(), now);
                return false;
            }
        }
        if (!cooldowns.claim(owner.getUUID(), now, mob.getRandom())) return false;
        cancelled = false;
        announced = false;
        arrived = false;
        return true;
    }

    @Override public void start() {
        startedAt = now();
        mob.getNavigation().stop();
        if (path != null) mob.getNavigation().moveTo(path, 1.0);
    }

    private boolean ownerStillAvailable() {
        if (!available(mob, owner) || mob.distanceToSqr(owner) > OWNER_RADIUS_SQR
                || (arrived ? mob.activity() != Activity.WAVE : mob.activity() != Activity.IDLE)
                || WalkScenes.current(mob) != null || HomeAtmosphereScenes.current(mob) != null) return false;
        if (dialogue == Dialogue.MARUSYA && !marusyaNearby(mob, owner)) return false;
        return true;
    }

    @Override public boolean canContinueToUse() {
        return !cancelled && now() - startedAt < APPROACH_LIMIT && ownerStillAvailable()
                && (!arrived || now() < waveEnds);
    }

    @Override public void tick() {
        long now = now();
        if (!canContinueToUse()) { stop(); return; }
        mob.getLookControl().setLookAt(owner, 20, 25);
        if (arrived) {
            mob.getNavigation().stop();
            mob.setActivity(Activity.WAVE);
            return;
        }
        if (mob.distanceToSqr(owner) <= ARRIVAL_RADIUS_SQR) {
            mob.getNavigation().stop();
            arrived = true;
            waveEnds = now + WAVE_DURATION;
            mob.setActivity(Activity.WAVE);
            announce();
            return;
        }
        if (mob.tickCount % 10 != 0) return;
        path = mob.getNavigation().createPath(owner, 0);
        if (path == null || !path.canReach() || !FamilyTravel.safePath(mob, path)) {
            cancelled = true;
            stop();
            return;
        }
        mob.getNavigation().moveTo(path, 1.0);
    }

    private void announce() {
        if (announced || owner == null || !(mob.level() instanceof ServerLevel)) return;
        String key = dialogue == Dialogue.MARUSYA ? "dialogue.dudunka.marusya" : "dialogue.dudunka.big";
        owner.sendSystemMessage(Component.translatable("dialogue.dudunka.named", mob.getDisplayName(), Component.translatable(key)));
        announced = true;
    }

    @Override public void stop() {
        if (arrived || mob.activity() == Activity.IDLE) mob.setActivity(Activity.IDLE);
        mob.getNavigation().stop();
        owner = null;
        path = null;
        dialogue = null;
        arrived = false;
        announced = false;
    }
}
