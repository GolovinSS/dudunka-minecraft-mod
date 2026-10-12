package io.github.golovinss.dudunka;

import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.TicketType;

/** Explicit gateway journeys. Only a temporary ticket for the chosen arrival chunk may be used. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class FamilyJourney {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Pending> RELEASING = new HashMap<>();
    private static final TicketType<UUID> JOURNEY_TICKET = TicketType.create("dudunka_family_journey",
            Comparator.comparing(UUID::toString), 240);
    private static final int PREPARATION_TIMEOUT_TICKS = 200;
    private FamilyJourney() {}
    public record Eligibility(List<Companion> family, Component missing, boolean absent) {
        public boolean ready() { return missing == null && family.size() == Kind.values().length; }
    }
    private record Member(UUID id, Companion original, Vec3 position, float yaw, float pitch, BlockPos landing) {}
    private static final class Pending {
        final ServerPlayer player;
        final ServerLevel source;
        final Vec3 departure;
        final ServerLevel destination;
        final ChunkPos chunk;
        final java.util.function.Supplier<MushroomVale.TravelResult> retry;
        int ticks;
        Pending(ServerPlayer player, ServerLevel source, Vec3 departure, ServerLevel destination, ChunkPos chunk,
                java.util.function.Supplier<MushroomVale.TravelResult> retry) {
            this.player=player; this.source=source; this.departure=departure;
            this.destination=destination; this.chunk=chunk; this.retry=retry;
        }
    }

    static boolean busy(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID()) || PENDING.containsKey(player.getUUID()) || RELEASING.containsKey(player.getUUID());
    }

    static boolean cancelPending(ServerPlayer player) {
        var pending=PENDING.remove(player.getUUID());
        if (pending==null) return false;
        releaseTicket(pending);
        LogUtils.getLogger().info("Cancelled pending family journey for solo return: owner={}, chunk={}",
                player.getUUID(),pending.chunk);
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : List.copyOf(RELEASING.entrySet())) {
            var pending=entry.getValue();
            if ((++pending.ticks >= 2 && pending.destination.areEntitiesLoaded(pending.chunk.toLong()))
                    || pending.ticks >= 20) {
                RELEASING.remove(entry.getKey(),pending);
                releaseTicket(pending);
            }
        }
        if (PENDING.isEmpty()) return;
        for (var entry : List.copyOf(PENDING.entrySet())) {
            UUID owner=entry.getKey(); Pending pending=entry.getValue();
            if (PENDING.get(owner)!=pending) continue;
            pending.ticks++;
            if (pending.ticks > PREPARATION_TIMEOUT_TICKS || !pending.player.isAlive()
                    || pending.player.server != pending.destination.getServer()
                    || pending.player.serverLevel()!=pending.source || !pending.player.position().equals(pending.departure)) {
                finishPending(owner,pending,MushroomVale.TravelResult.ARRIVAL_UNSAFE);
                continue;
            }
            if (!pending.destination.areEntitiesLoaded(pending.chunk.toLong())) continue;
            PENDING.remove(owner);
            ACTIVE.add(owner);
            MushroomVale.TravelResult result;
            try {
                result=pending.retry.get();
            } catch (RuntimeException failure) {
                LogUtils.getLogger().error("Prepared family journey failed: owner={}, chunk={}",owner,pending.chunk,failure);
                result=MushroomVale.TravelResult.DENIED;
            } finally {
                ACTIVE.remove(owner);
            }
            MushroomVale.tell(pending.player,result);
            if (result==MushroomVale.TravelResult.ENTERED
                    && pending.player.serverLevel()==pending.destination) {
                pending.ticks=0;
                RELEASING.put(owner,pending);
                if (pending.player.serverLevel().dimension().equals(MushroomVale.LEVEL))
                    MushroomValeReturnGateway.ensure(pending.player);
            } else releaseTicket(pending);
        }
    }

    private static void finishPending(UUID owner,Pending pending,MushroomVale.TravelResult result) {
        if (!PENDING.remove(owner,pending)) return;
        releaseTicket(pending);
        LogUtils.getLogger().warn("Family journey chunk preparation ended: owner={}, chunk={}, ticks={}, result={}",
                owner,pending.chunk,pending.ticks,result);
        if (pending.player.isAlive()) MushroomVale.tell(pending.player,result);
    }

    private static void releaseTicket(Pending pending) {
        pending.destination.getChunkSource().removeRegionTicket(JOURNEY_TICKET,pending.chunk,2,pending.player.getUUID());
    }
    private static Eligibility missing(String reason, Kind kind) {
        return new Eligibility(List.of(), Component.translatable("message.dudunka.journey." + reason,
                Component.translatable("entity.dudunka." + kind.id)), false);
    }

    /** Loaded entity lookup only; a stale registry entry cannot stand in for a live companion. */
    public static Eligibility inspect(ServerPlayer player, BlockPos anchor) {
        var level = player.serverLevel();
        return evaluate(player, anchor, level.getEntitiesOfClass(Companion.class, new AABB(anchor).inflate(8)));
    }

    public static Eligibility evaluate(ServerPlayer player, BlockPos anchor, List<Companion> candidates) {
        var chosen = new EnumMap<Kind, Companion>(Kind.class);
        var ids = new HashSet<UUID>();
        for (var mob : candidates) {
            if (!player.getUUID().equals(mob.ownerId()) || mob.level() != player.level()
                    || mob.distanceToSqr(Vec3.atCenterOf(anchor)) > 64) continue;
            if (!ids.add(mob.getUUID()) || chosen.putIfAbsent(mob.kind, mob) != null || !canonical(player, mob))
                return missing("duplicate", mob.kind);
        }
        if (chosen.isEmpty()) return new Eligibility(List.of(), null, true);
        for (var kind : Kind.values()) {
            var mob = chosen.get(kind);
            if (mob == null) return missing("missing", kind);
            if (CarrierLedger.get(player.server).carried(mob.getUUID())) return missing("carried", kind);
            if (mob.stage() != 2) return missing("young", kind);
            if (mob.staying() || mob.atHomeMode()) return missing("follow", kind);
            if (!eligible(player, mob, anchor)) return missing("unsafe", kind);
        }
        return new Eligibility(List.copyOf(chosen.values()), null, false);
    }

    private static boolean canonical(ServerPlayer player, Companion mob) {
        if (!mob.isAlive() || mob.isRemoved() || !mob.level().hasChunkAt(mob.blockPosition())
                || !(mob.level() instanceof ServerLevel source) || source.getEntity(mob.getUUID()) != mob) return false;
        for (var level : player.server.getAllLevels()) {
            var other = level.getEntity(mob.getUUID());
            if (other != null && other != mob) return false;
        }
        var indexed = FamilyRegistry.get(player.server).member(mob.getUUID());
        return indexed == null || indexed.owner().equals(player.getUUID()) && indexed.kind() == mob.kind;
    }

    private static boolean eligible(ServerPlayer player, Companion mob, BlockPos anchor) {
        return mob.level() == player.level() && player.getUUID().equals(mob.ownerId()) && canonical(player, mob)
                && mob.distanceToSqr(Vec3.atCenterOf(anchor)) <= 64 && mob.stage() == 2
                && !CarrierLedger.get(player.server).carried(mob.getUUID()) && !mob.staying() && !mob.atHomeMode()
                && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle() && !mob.portalPosing()
                && !mob.isInWaterOrBubble() && !mob.isInLava() && !mob.isOnFire()
                && MushroomVale.safeLanding(player.serverLevel(), mob.position(), mob);
    }

    /** Physical gates select family when any owned member is present. The command requires all three. */
    public static MushroomVale.TravelResult travel(ServerPlayer player, BlockPos anchor, boolean returning, boolean requireFamily) {
        if (busy(player)) return MushroomVale.TravelResult.DENIED;
        if (returning && !player.level().dimension().equals(MushroomVale.LEVEL)) return MushroomVale.TravelResult.WRONG_DIMENSION;
        // Sneaking is an explicit solo choice at a gate; it never weakens the family command.
        if (!requireFamily && player.isShiftKeyDown())
            return returning ? MushroomVale.returnPlayer(player) : MushroomVale.enterPlayer(player);
        var check = inspect(player, anchor);
        if (!check.ready() && !(check.absent() && !requireFamily)) {
            player.displayClientMessage(check.missing() == null ? missing("missing", Kind.DUDUNKA).missing() : check.missing(), false);
            return MushroomVale.TravelResult.FAMILY_INELIGIBLE;
        }
        if (!check.family().isEmpty() && FamilyRecovery.pending(player.server, player.getUUID())) return MushroomVale.TravelResult.DENIED;
        ACTIVE.add(player.getUUID());
        try {
            return returning ? MushroomVale.returnPlayer(player, check.family(), anchor)
                    : MushroomVale.enterPlayer(player, check.family(), anchor);
        } finally { ACTIVE.remove(player.getUUID()); }
    }

    // Entity.teleportTo(ServerLevel, ...) itself does not post Forge's dimension veto.
    // Keep native identity-preserving transfer, but explicitly honor that event in both directions.
    private static boolean transfer(Companion mob, ServerLevel destination, Vec3 pos, float yaw, float pitch,
                                    java.util.function.BooleanSupplier revalidate) {
        if (mob.level() != destination && !net.minecraftforge.common.ForgeHooks.onTravelToDimension(mob, destination.dimension())) return false;
        if (!revalidate.getAsBoolean()) return false;
        var source = (ServerLevel) mob.level();
        try {
            boolean moved = mob.teleportTo(destination, pos.x, pos.y, pos.z, Set.of(), yaw, pitch);
            return moved && destination.getEntity(mob.getUUID()) instanceof Companion;
        } finally {
            // Native teleport removes the source before EntityJoinLevelEvent can reject
            // destination insertion. Retain that very same live source object on rejection,
            // never a reconstruction from NBT. Both insertion and restoration honor Forge.
            if (mob.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION
                    && destination.getEntity(mob.getUUID()) == null && source.getEntity(mob.getUUID()) == null
                    && source.areEntitiesLoaded(new ChunkPos(mob.blockPosition()).toLong())
                    && MushroomVale.safeLanding(source, mob.position(), mob)) {
                mob.revive();
                source.addFreshEntity(mob);
                FamilyRegistry.get(source.getServer()).observe(mob);
            }
        }
    }

    private static boolean arrived(ServerPlayer player, Member member, ServerLevel destination) {
        return destination.getEntity(member.id()) instanceof Companion mob && canonical(player, mob)
                && player.getUUID().equals(mob.ownerId()) && mob.kind == member.original().kind && mob.stage() == 2
                && !mob.staying() && !mob.atHomeMode() && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle()
                && !mob.isOnFire() && !mob.portalPosing() && !CarrierLedger.get(player.server).carried(member.id())
                && mob.position().distanceToSqr(Vec3.atBottomCenterOf(member.landing())) < .01
                && MushroomVale.safeLanding(destination, member.landing(), mob);
    }

    /** All landings are reserved before movement. Success uses ENTERED as an internal acknowledgement. */
    static MushroomVale.TravelResult move(ServerPlayer player, List<Companion> family, BlockPos anchor,
                                          ServerLevel destination, BlockPos landing, float yaw, float pitch) {
        return move(player,family,anchor,destination,landing,yaw,pitch,null);
    }

    static MushroomVale.TravelResult move(ServerPlayer player, List<Companion> family, BlockPos anchor,
                                          ServerLevel destination, BlockPos landing, float yaw, float pitch,
                                          java.util.function.Supplier<MushroomVale.TravelResult> retry) {
        if (family.isEmpty()) return MushroomVale.move(player, destination, landing, yaw, pitch)
                ? MushroomVale.TravelResult.ENTERED : MushroomVale.TravelResult.DENIED;
        var source = player.serverLevel();
        var departure = player.position();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        if (!MushroomVale.safeLanding(source, departure, player)) return MushroomVale.TravelResult.DEPARTURE_UNSAFE;
        var check = inspect(player, anchor);
        if (!check.ready() || !check.family().equals(family) || !MushroomVale.canTravel(player))
            return MushroomVale.TravelResult.FAMILY_INELIGIBLE;
        var arrivalChunk=new ChunkPos(landing);
        if (!destination.areEntitiesLoaded(arrivalChunk.toLong())) {
            if (retry==null) return MushroomVale.TravelResult.ARRIVAL_UNSAFE;
            if (PENDING.containsKey(player.getUUID())) return MushroomVale.TravelResult.PREPARING;
            var pending=new Pending(player,source,departure,destination,arrivalChunk,retry);
            destination.getChunkSource().addRegionTicket(JOURNEY_TICKET,arrivalChunk,2,player.getUUID());
            PENDING.put(player.getUUID(),pending);
            LogUtils.getLogger().info("Preparing family journey arrival: owner={}, chunk={}, full={}, entityTicking={}",
                    player.getUUID(),arrivalChunk,destination.getChunkSource().getChunkNow(arrivalChunk.x,arrivalChunk.z)!=null,
                    destination.areEntitiesLoaded(arrivalChunk.toLong()));
            return MushroomVale.TravelResult.PREPARING;
        }
        var occupied = new ArrayList<AABB>();
        occupied.add(MushroomVale.landingBox(player, landing));
        var plan = new ArrayList<Member>();
        for (var mob : family) {
            BlockPos chosen = null;
            var rejected = new LinkedHashMap<String, Integer>();
            var samples = new ArrayList<String>();
            int examined = 0;
            search: for (int radius = 1; radius <= 4; radius++)
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var column = landing.offset(dx, 0, dz);
                    var chunk = new ChunkPos(column);
                    // The player's explicit entry synchronously prepares this destination
                    // chunk to FULL before family planning. Entity ticking starts only
                    // after a player arrives, so areEntitiesLoaded() rejects every
                    // companion on the first trip into a cold dimension. FULL is enough
                    // for safe block inspection and native entity insertion; no adjacent
                    // chunks or tickets are needed.
                    if (!chunk.equals(new ChunkPos(landing))
                            || destination.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                        reject(rejected, samples, column, "unloaded/outside arrival chunk"); examined++; continue;
                    }
                    var surface = destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    for (int dy : new int[]{0, 1, -1, 2, -2}) {
                        var pos = surface.offset(0, dy, 0);
                        examined++;
                        var box = MushroomVale.landingBox(mob, pos);
                        String reason = occupied.stream().anyMatch(box::intersects) ? "overlaps reserved landing"
                                : MushroomVale.landingFailure(destination, Vec3.atBottomCenterOf(pos), mob);
                        if (reason != null) { reject(rejected, samples, pos, reason); continue; }
                        chosen = pos; occupied.add(box); break search;
                    }
                }
            if (chosen == null) {
                LogUtils.getLogger().warn("Family arrival has no landing: owner={}, kind={}, examined={}, rejected={}, samples={}",
                        player.getUUID(), mob.kind, examined, rejected, samples);
                return MushroomVale.TravelResult.ARRIVAL_UNSAFE;
            }
            plan.add(new Member(mob.getUUID(), mob, mob.position(), mob.getYRot(), mob.getXRot(), chosen));
        }
        boolean success = false;
        try {
            for (var member : plan) {
                var mob = member.original();
                if (!MushroomVale.canTravel(player) || player.serverLevel() != source || !player.position().equals(departure)
                        || !eligible(player, mob, anchor) || !MushroomVale.safeLanding(destination, member.landing(), mob)) break;
                mob.prepareJourney();
                boolean moved = transfer(mob, destination, Vec3.atBottomCenterOf(member.landing()), member.yaw(), member.pitch(),
                        () -> eligible(player, mob, anchor) && MushroomVale.safeLanding(destination, member.landing(), mob));
                var actual = destination.getEntity(member.id());
                if (actual instanceof Companion arrived) {
                    arrived.prepareJourney(); FamilyRegistry.get(player.server).observe(arrived);
                }
                if (!moved || !(actual instanceof Companion arrived) || !canonical(player, arrived)
                        || arrived.position().distanceToSqr(Vec3.atBottomCenterOf(member.landing())) > .01) break;
                if (member == plan.get(plan.size() - 1)) {
                    // Forge listeners can change earlier landings or ownership during subsequent transfers.
                    boolean intact = plan.stream().allMatch(m -> arrived(player, m, destination));
                    if (intact && MushroomVale.canTravel(player) && player.serverLevel() == source
                            && player.position().equals(departure) && MushroomVale.safeLanding(source, player.position(), player)
                            && MushroomVale.safeLanding(destination, landing, player))
                        success = MushroomVale.move(player, destination, landing, yaw, pitch);
                }
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Family journey failed for owner {}; attempting bounded rollback", player.getUUID(), failure);
        }
        if (success) success = MushroomVale.canTravel(player) && player.serverLevel() == destination
                && MushroomVale.safeLanding(destination, player.position(), player)
                && plan.stream().allMatch(m -> arrived(player, m, destination));
        if (success) return MushroomVale.TravelResult.ENTERED;
        boolean restored = true;
        // Normally the player never moved on failure. A listener may have redirected them.
        if (player.serverLevel() != source || !player.position().equals(departure)) {
            try {
                if (MushroomVale.safeLanding(source, departure, player))
                    player.teleportTo(source, departure.x, departure.y, departure.z, oldYaw, oldPitch);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().error("Family journey player rollback failed: owner={}", player.getUUID(), failure);
            }
            restored = player.serverLevel() == source && player.position().equals(departure);
            if (!restored) LogUtils.getLogger().error("Family journey player rollback incomplete: owner={}", player.getUUID());
        }
        for (int i = plan.size() - 1; i >= 0; i--) {
            var member = plan.get(i);
            try {
                Companion actual = null;
                for (var level : player.server.getAllLevels()) if (level.getEntity(member.id()) instanceof Companion c) {
                    if (actual != null) throw new IllegalStateException("Conflicting live UUID " + member.id());
                    actual = c;
                }
                if (actual == null) throw new IllegalStateException("Transferred UUID unavailable " + member.id());
                if (actual.level() != source || !actual.position().equals(member.position())) {
                    if (!MushroomVale.safeLanding(source, member.position(), actual))
                        throw new IllegalStateException("Rollback landing unsafe " + member.id());
                    actual.prepareJourney();
                    var rollbackMob = actual;
                    transfer(actual, source, member.position(), member.yaw(), member.pitch(),
                            () -> canonical(player, rollbackMob) && !rollbackMob.isLeashed() && !rollbackMob.isPassenger()
                                    && !rollbackMob.isVehicle() && !CarrierLedger.get(player.server).carried(member.id())
                                    && MushroomVale.safeLanding(source, member.position(), rollbackMob));
                }
                var returned = source.getEntity(member.id());
                if (!(returned instanceof Companion c) || !canonical(player,c) || !player.getUUID().equals(c.ownerId())
                        || !c.position().equals(member.position()) || c.getYRot()!=member.yaw() || c.getXRot()!=member.pitch())
                    throw new IllegalStateException("Rollback denied " + member.id());
                FamilyRegistry.get(player.server).observe(c);
            } catch (RuntimeException failure) {
                restored = false;
                for (var level : player.server.getAllLevels()) if (level.getEntity(member.id()) instanceof Companion c)
                    FamilyRegistry.get(player.server).observe(c);
                LogUtils.getLogger().error("Family journey rollback incomplete: owner={}, companion={}; use album recovery",
                        player.getUUID(), member.id(), failure);
                player.displayClientMessage(Component.translatable("message.dudunka.journey.recover", member.id().toString()), false);
            }
        }
        return restored ? MushroomVale.TravelResult.DENIED : MushroomVale.TravelResult.ROLLBACK_FAILED;
    }

    private static void reject(Map<String, Integer> counts, List<String> samples, BlockPos pos, String reason) {
        counts.merge(reason, 1, Integer::sum);
        if (samples.size() < 4) samples.add(pos.toShortString() + "=" + reason);
    }
}
