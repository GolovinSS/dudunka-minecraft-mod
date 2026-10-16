package io.github.golovinss.dudunka;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla command accompaniment, captured before departure and consumed after native arrival.
 * No movement sampling, saved-data reconstruction, chunk requests or gateway transactions. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class FamilyTeleport {
    /** Opt-in INFO traces on an ordinary dedicated/integrated server; otherwise DEBUG. */
    public static final String DIAGNOSTIC_PROPERTY = "dudunka.debugFamilyTeleport";
    private static final int WAIT_TICKS = 40;
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private FamilyTeleport() {}

    private static final class Scope {
        final Map<UUID, Request> requests = new LinkedHashMap<>();
    }

    private static final class Request {
        final ServerPlayer player;
        final ServerLevel source;
        final Vec3 departure;
        final List<Companion> family;
        final EntityTeleportEvent.TeleportCommand command;
        EntityTravelToDimensionEvent travel;
        ServerLevel destination;
        Vec3 arrival;
        int ticks;
        Request(ServerPlayer player, List<Companion> family, EntityTeleportEvent.TeleportCommand command) {
            this.player=player; source=player.serverLevel(); departure=player.position();
            this.family=family; this.command=command;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterCommandsEvent event) { install(event.getDispatcher()); }

    /** Public for focused dispatcher tests. Repeated registration never stacks wrappers.
     * Brigadier addChild merges a command into the EXISTING node. Redirects (/tp and
     * execute) therefore still reference the original nodes, requirements and arguments. */
    public static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=dispatcher.getRoot().getChild("teleport");
        if (root == null) {
            LogUtils.getLogger().warn("Family teleport hook unavailable: vanilla teleport command is missing");
            return;
        }
        wrap(root);
        trace("Family teleport hook installed: dispatcher={}, tpRedirectsToTeleport={}",
                System.identityHashCode(dispatcher), dispatcher.getRoot().getChild("tp") != null
                        && dispatcher.getRoot().getChild("tp").getRedirect() == root);
    }

    private static void wrap(CommandNode<CommandSourceStack> parent) {
        for (var child : List.copyOf(parent.getChildren())) {
            var command=child.getCommand();
            if (command != null && !(command instanceof Envelope))
                parent.addChild(child.createBuilder().executes(new Envelope(command)).build());
            wrap(child);
        }
    }

    private record Envelope(Command<CommandSourceStack> original) implements Command<CommandSourceStack> {
        @Override public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            trace("Family teleport vanilla executable intercepted: sourceEntity={}, sourceDimension={}",
                    context.getSource().getEntity() == null ? "console/block" : context.getSource().getEntity().getUUID(),
                    context.getSource().getLevel().dimension().location());
            var previous=SCOPE.get();
            var scope=new Scope(); SCOPE.set(scope);
            boolean returned=false;
            try {
                int result=original.run(context); returned=true; return result;
            } finally {
                if (previous == null) SCOPE.remove(); else SCOPE.set(previous);
                for (var request : scope.requests.values()) {
                    // Accompaniment cannot change vanilla command results or exceptions.
                    try { complete(request, returned); }
                    catch (RuntimeException failure) {
                        REQUESTS.remove(request.player.getUUID(),request);
                        LogUtils.getLogger().error("Family teleport completion failed: owner={}",request.player.getUUID(),failure);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void capture(EntityTeleportEvent.TeleportCommand event) {
        try { captureRequest(event); }
        catch (RuntimeException failure) {
            REQUESTS.remove(event.getEntity().getUUID());
            LogUtils.getLogger().error("Family teleport capture failed: entity={}",event.getEntity().getUUID(),failure);
        }
    }

    private static void captureRequest(EntityTeleportEvent.TeleportCommand event) {
        var scope=SCOPE.get();
        if (scope == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        UUID owner=player.getUUID();
        var previous=scope.requests.get(owner);
        if (previous != null && previous.command == event) return;
        // A later command supersedes an earlier pending arrival, even when canceled.
        var superseded=REQUESTS.remove(owner);
        if (superseded != null) diagnostic(superseded,"superseded");
        if (ACTIVE.contains(owner) || FamilyJourney.busy(player) || FamilyRecovery.pending(player.server,owner)
                || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
            trace("Family teleport capture excluded owner: owner={}, active={}, journeyBusy={}, recovery={}, alive={}, removed={}, spectator={}",
                    owner,ACTIVE.contains(owner),FamilyJourney.busy(player),FamilyRecovery.pending(player.server,owner),
                    player.isAlive(),player.isRemoved(),player.isSpectator());
            return;
        }
        var source=player.serverLevel();
        Vec3 departure=player.position();
        var nearby=source.getEntitiesOfClass(Companion.class,player.getBoundingBox().inflate(8));
        var family=new ArrayList<Companion>();
        var rejected=new LinkedHashMap<String,Integer>();
        int reported=0;
        for (var mob : nearby) {
            String reason=exclusion(player,source,departure,mob);
            if (reason == null) family.add(mob);
            else {
                rejected.merge(reason,1,Integer::sum);
                if (reported++ < 16) trace("Family teleport candidate excluded: owner={}, companion={}, kind={}, reason={}",
                        owner,mob.getUUID(),mob.kind,reason);
            }
        }
        trace("Family teleport capture: owner={}, source={}, departure={}, nearby={}, eligible={}, excluded={}",
                owner,source.dimension().location(),departure,nearby.size(),family.size(),rejected);
        if (family.isEmpty()) return;
        var request=new Request(player,List.copyOf(family),event);
        scope.requests.put(owner,request); REQUESTS.put(owner,request);
        trace("Family teleport pending created: owner={}, members={}, sourceEntityTicking={}",
                owner,family.size(),source.isPositionEntityTicking(player.blockPosition()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void travel(EntityTravelToDimensionEvent event) {
        var scope=SCOPE.get();
        if (scope == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        var request=scope.requests.get(player.getUUID());
        if (request != null && REQUESTS.get(player.getUUID()) == request) {
            request.travel=event;
            trace("Family teleport player travel observed: owner={}, destination={}",player.getUUID(),event.getDimension().location());
        }
    }

    private static boolean eligible(ServerPlayer player, ServerLevel source, Vec3 departure, Companion mob) {
        return exclusion(player,source,departure,mob) == null;
    }

    private static String exclusion(ServerPlayer player, ServerLevel source, Vec3 departure, Companion mob) {
        if (mob.level() != source) return "source dimension changed";
        if (!player.getUUID().equals(mob.ownerId())) return "foreign owner";
        if (!mob.isAlive() || mob.isRemoved()) return "dead/removed";
        if (mob.position().distanceToSqr(departure) > 64) return "outside departure radius";
        // Vanilla removes the owner's simulation ticket at departure. FULL source
        // chunks can still contain the exact live entities without ticking them.
        // Requiring source simulation here loses valid pending arrivals. Never load
        // the source: canonical identity + already loaded entity data are sufficient.
        if (!source.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(mob.blockPosition()).toLong()))
            return "source entity data unloaded";
        if (!FamilyJourney.canonical(player,mob)) return "noncanonical UUID/registry";
        if (mob.staying()) return "STAY";
        if (mob.atHomeMode()) return "HOME";
        if (CarrierLedger.get(player.server).carried(mob.getUUID())) return "carried";
        if (mob.isLeashed()) return "leashed";
        if (mob.isPassenger() || mob.isVehicle()) return "riding/passengers";
        if (mob.portalPosing()) return "gateway pose";
        return null;
        // FOLLOW is a permanent mode: Marusya's temporary independent pause and age
        // do not turn a command departure into a request to leave her behind.
    }

    private static void complete(Request request, boolean returned) {
        var player=request.player;
        if (REQUESTS.get(player.getUUID()) != request) return;
        // Retain the event objects until dispatch finishes: later listeners can cancel
        // or rewrite targets, including listeners at our own event priority.
        boolean dimension=request.travel == null ? player.serverLevel() == request.source
                : !request.travel.isCanceled() && player.level().dimension().equals(request.travel.getDimension());
        String failure=!returned ? "vanilla executable threw"
                : request.command.isCanceled() ? "Forge teleport event canceled"
                : !dimension ? "Forge travel veto/dimension mismatch"
                : !player.isAlive() || player.isRemoved() ? "owner dead/removed"
                : player.position().distanceToSqr(request.command.getTarget()) > 1.0E-8 ? "actual position differs from final Forge target" : null;
        if (failure != null) {
            REQUESTS.remove(player.getUUID(),request); diagnostic(request,failure); return;
        }
        if (player.serverLevel() == request.source && player.position().equals(request.departure)) {
            REQUESTS.remove(player.getUUID(),request);
            trace("Family teleport no-op consumed: owner={}",player.getUUID()); return;
        }
        request.destination=player.serverLevel(); request.arrival=player.position();
        trace("Family teleport native player arrival confirmed: owner={}, destination={}, arrival={}, members={}",
                player.getUUID(),request.destination.dimension().location(),request.arrival,request.family.size());
        if (!FamilyJourney.entityTickingWithData(request.destination,BlockPos.containing(request.arrival)))
            trace("Family teleport waiting for native arrival loading: owner={}, destination={}, arrival={}, timeoutTicks={}",
                    player.getUUID(),request.destination.dimension().location(),request.arrival,WAIT_TICKS);
        attempt(request);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var request : List.copyOf(REQUESTS.values())) {
            if (request.destination == null) continue;
            if (++request.ticks > WAIT_TICKS) {
                REQUESTS.remove(request.player.getUUID(),request); diagnostic(request,"arrival loading timeout");
            } else {
                try { attempt(request); }
                catch (RuntimeException failure) {
                    REQUESTS.remove(request.player.getUUID(),request);
                    LogUtils.getLogger().error("Family teleport deferred attempt failed: owner={}",request.player.getUUID(),failure);
                }
            }
        }
    }

    private static boolean ownerReady(Request request) {
        var player=request.player;
        return REQUESTS.get(player.getUUID()) == request && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.serverLevel() == request.destination && player.position().distanceToSqr(request.arrival) <= 16
                && !FamilyJourney.busy(player) && !FamilyRecovery.pending(player.server,player.getUUID());
    }

    private static void attempt(Request request) {
        UUID owner=request.player.getUUID();
        if (!ownerReady(request)) {
            REQUESTS.remove(owner,request); diagnostic(request,"owner left arrival/busy"); return;
        }
        var center=BlockPos.containing(request.arrival);
        if (!FamilyJourney.entityTickingWithData(request.destination,center)) return;
        // ACTIVE prevents reentrant consumption. Retain the request for post-hook
        // revalidation, then remove it unconditionally after this single pass.
        if (!ACTIVE.add(owner)) return;
        trace("Family teleport destination ready: owner={}, destination={}, waitedTicks={}, sourceEntitiesLoaded={}, sourceEntityTicking={}",
                owner,request.destination.dimension().location(),request.ticks,
                request.source.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(BlockPos.containing(request.departure)).toLong()),
                request.source.isPositionEntityTicking(BlockPos.containing(request.departure)));
        try {
            for (var mob : request.family) {
                if (!ownerReady(request)) break;
                String excluded=exclusion(request.player,request.source,request.departure,mob);
                if (excluded != null) {
                    memberDiagnostic(request,mob,excluded); continue;
                }
                if (!MushroomVale.safeLanding(request.source,mob.position(),mob)) {
                    memberDiagnostic(request,mob,"unsafe source restoration position: "
                            +MushroomVale.landingFailure(request.source,mob.position(),mob)); continue;
                }
                var rejected=new LinkedHashMap<String,Integer>();
                BlockPos chosen=null;
                search: for (int radius=1;radius<=4;radius++)
                    for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
                        if (Math.max(Math.abs(dx),Math.abs(dz)) != radius) continue;
                        for (int dy : new int[]{0,1,-1,2,-2}) {
                            var pos=center.offset(dx,dy,dz);
                            String reason=!FamilyJourney.entityTickingWithData(request.destination,pos)
                                    ? "arrival chunk not entity ticking"
                                    : MushroomVale.landingFailure(request.destination,Vec3.atBottomCenterOf(pos),mob);
                            if (reason == null) { chosen=pos; break search; }
                            rejected.merge(reason,1,Integer::sum);
                        }
                    }
                if (chosen == null) { memberDiagnostic(request,mob,"no safe landing: "+rejected); continue; }
                Vec3 landing=Vec3.atBottomCenterOf(chosen);
                try {
                    mob.prepareJourney();
                    boolean moved=FamilyJourney.transfer(mob,request.destination,landing,mob.getYRot(),mob.getXRot(),
                            () -> ownerReady(request) && eligible(request.player,request.source,request.departure,mob)
                                    && MushroomVale.safeLanding(request.source,mob.position(),mob)
                                    && FamilyJourney.entityTickingWithData(request.destination,BlockPos.containing(landing))
                                    && MushroomVale.safeLanding(request.destination,landing,mob));
                    var actual=request.destination.getEntity(mob.getUUID());
                    if (moved && actual instanceof Companion arrived && FamilyJourney.canonical(request.player,arrived)
                            && request.player.getUUID().equals(arrived.ownerId()) && !arrived.staying() && !arrived.atHomeMode()
                            && !CarrierLedger.get(request.player.server).carried(arrived.getUUID())
                            && MushroomVale.safeLanding(request.destination,arrived.position(),arrived)
                            && arrived.position().distanceToSqr(landing) < .01) {
                        arrived.prepareJourney(); FamilyRegistry.get(request.player.server).observe(arrived);
                        trace("Family teleport accompanied: owner={}, companion={}, kind={}, destination={}, landing={}",
                                owner,arrived.getUUID(),arrived.kind,request.destination.dimension().location(),landing);
                    } else memberDiagnostic(request,mob,"transfer vetoed/rejected/changed");
                } catch (RuntimeException failure) {
                    LogUtils.getLogger().error("Family teleport transfer failed: owner={}, companion={}",owner,mob.getUUID(),failure);
                } finally {
                    // Observe surviving native instances, including a refused insertion's
                    // restored source. Never reconstruct a missing UUID from registry/NBT.
                    boolean found=false;
                    for (var level : request.player.server.getAllLevels())
                        if (level.getEntity(mob.getUUID()) instanceof Companion live) {
                            found=true; FamilyRegistry.get(request.player.server).observe(live);
                        }
                    if (!found) {
                        memberDiagnostic(request,mob,"native insertion AND source restoration unavailable; use album recovery");
                        request.player.displayClientMessage(Component.translatable("message.dudunka.journey.recover",mob.getUUID().toString()),false);
                    }
                }
            }
        } finally { REQUESTS.remove(owner,request); ACTIVE.remove(owner); }
    }

    private static void diagnostic(Request request, String reason) {
        var level=request.player.serverLevel();
        var pos=request.player.blockPosition();
        LogUtils.getLogger().warn("Family teleport skipped: owner={}, source={}, departure={}, target={}, actualDimension={}, actualPosition={}, members={}, reason={}, entitiesLoaded={}, entityTicking={}",
                request.player.getUUID(),request.source.dimension().location(),request.departure,request.command.getTarget(),
                request.player.level().dimension().location(),request.player.position(),request.family.size(),reason,
                level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong()),level.isPositionEntityTicking(pos));
    }
    private static void memberDiagnostic(Request request, Companion mob, String reason) {
        LogUtils.getLogger().warn("Family teleport companion skipped: owner={}, companion={}, kind={}, source={}, destination={}, reason={}",
                request.player.getUUID(),mob.getUUID(),mob.kind,request.source.dimension().location(),
                request.destination.dimension().location(),reason);
    }

    private static void trace(String message, Object... arguments) {
        var logger=LogUtils.getLogger();
        if (Boolean.getBoolean(DIAGNOSTIC_PROPERTY)) logger.info(message,arguments);
        else logger.debug(message,arguments);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var request=REQUESTS.remove(event.getEntity().getUUID());
        if (request != null) diagnostic(request,"owner logout");
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { REQUESTS.clear(); ACTIVE.clear(); SCOPE.remove(); }
}
