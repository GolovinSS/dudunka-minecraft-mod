package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;

/** Dispatcher + registered Forge listeners + natural server ticks. No service helper calls.
 * Opt-in dedicated integration: cold generation finishes BEFORE these 500-tick tests.
 * FULL-only remote sources deliberately avoid StructureUtils' forced GameTest chunks.
 * The packet adapter acknowledges native teleports and sends a normal movement packet;
 * it is not a socket, login/Forge handshake, or a graphical/BMC4 acceptance test. */
@PrefixGameTestTemplate(false)
public final class FamilyTeleportRuntimeTests {
    private static final Set<Trip> OPEN=new HashSet<>();
    // Registered explicitly by FamilyTeleportFixture only in disposable integration runs.
    static void cleanup() { for(var t:List.copyOf(OPEN))t.close(); }

    private static final List<String> STABLE=List.of("UUID","FamilyOwner","FamilyHome","HomeDimension","HomeMarker",
            "HomeMode","Furniture","FurnitureDimension","Staying","Trust","GrowthTicks","CustomName",
            "CharacterVariant","NextCharacterVariant","NoAI","PersistenceRequired");

    private static final class Trip implements AutoCloseable {
        final GameTestHelper h;
        final int site;
        final ServerLevel source,destination;
        final BlockPos departure,landing;
        final Map<ServerLevel,Map<BlockPos,BlockState>> blocks=new HashMap<>();
        final List<ServerPlayer> players=new ArrayList<>();
        final List<Companion> family=new ArrayList<>();
        final List<Consumer<?>> listeners=new ArrayList<>();
        final Map<UUID,CompoundTag> saved=new HashMap<>();
        final Map<UUID,List<FamilyFriendships.Relation>> bonds=new HashMap<>();
        final Map<ServerLevel,Set<Long>> forced=new HashMap<>();
        List<FamilyMemories.Memory> memories;
        ServerPlayer owner;
        int departureEvents;
        int platformSnapshots;
        boolean readyReported;
        boolean closed;
        Trip(GameTestHelper h,int site,boolean cross) {
            this.h=h;this.site=site;source=h.getLevel();destination=cross?source.getServer().getLevel(MushroomVale.LEVEL):source;
            var fixture=FamilyTeleportFixture.requireReady(source.getServer());
            departure=fixture.departure;landing=fixture.landing;
            h.assertTrue(OPEN.isEmpty(),"Prepared regions have a single scenario lease; no shared mutable entities");
            OPEN.add(this);h.runAtTickTime(495,this::close);
            h.runAtTickTime(400,()->{if(!closed && !readyReported)
                LogUtils.getLogger().error("FAMILY_TP_FIXTURE_SETTLE_TIMEOUT site={}, source={}, destination={}",site,
                        GameTestChunkDiagnostics.describe(source,departure),GameTestChunkDiagnostics.describe(destination,landing));});
            for(var level:source.getServer().getAllLevels())forced.put(level,remoteForced(level));
        }
        Set<Long> remoteForced(ServerLevel level) {
            var result=new HashSet<Long>();
            // Native structure setup is near spawn; operational pads are 64 chunks
            // away. Never use forced chunks to retain the operational source.
            for(long id:level.getForcedChunks()) {
                var p=new ChunkPos(id);
                if(Math.abs(p.x)>64 || Math.abs(p.z)>64)result.add(id);
            }
            return result;
        }
        void snapshotPlatform(ServerLevel level,BlockPos center) {
            // Warmup built this platform once. Snapshot its baseline for restoration
            // and assert the preceding scenario left no terrain mutations behind.
            var chunk=level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4);
            h.assertTrue(chunk!=null && level.areEntitiesLoaded(new ChunkPos(center).toLong()),"Platform may only edit an already prepared chunk");
            var originals=blocks.computeIfAbsent(level,k->new LinkedHashMap<>());
            for(var p:BlockPos.betweenClosed(center.offset(-5,-1,-5),center.offset(5,4,5))) {
                originals.put(p.immutable(),chunk.getBlockState(p));
                h.assertTrue(chunk.getBlockState(p).equals(p.getY()==center.getY()-1
                        ?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState()),"Prepared platform baseline restored between scenarios: "+p);
            }
            LogUtils.getLogger().info("FAMILY_TP_FIXTURE_PLATFORM_SNAPSHOT site={}, dimension={}, chunk={}, testTick={}",
                    site,level.dimension().location(),new ChunkPos(center),h.getTick());
        }
        void ready() {
            FamilyTeleportFixture.requireReady(source.getServer()).assertPrepared();
            // A preceding scenario's native player tickets must really demote. FULL
            // tickets retain data, but never keep either region entity ticking.
            h.assertTrue(!source.isPositionEntityTicking(departure),"Remote source must be FULL, not forced/entity ticking");
            h.assertTrue(!destination.isPositionEntityTicking(landing),"Destination starts cold for actual deferred lifecycle");
            if(platformSnapshots==0) {
                snapshotPlatform(source,departure);platformSnapshots++;
                h.fail("Waiting one tick between prepared platform snapshots");
            }
            if(platformSnapshots==1) { snapshotPlatform(destination,landing);platformSnapshots++; }
            if(!readyReported) {
                readyReported=true;
                LogUtils.getLogger().info("FAMILY_TP_FIXTURE_READY_FOR_COMMANDS site={}, testTick={}",site,h.getTick());
            }
        }
        ServerPlayer player(ServerLevel level,BlockPos pos) {
            var p=new ServerPlayer(source.getServer(),source,new GameProfile(UUID.randomUUID(),"TpRuntime"));
            var outgoing=new ArrayList<ClientboundPlayerPositionPacket>();
            var connection=new Connection(PacketFlow.SERVERBOUND) {
                @Override public void send(Packet<?> packet) { if(packet instanceof ClientboundPlayerPositionPacket teleport)outgoing.add(teleport); }
                @Override public void send(Packet<?> packet,PacketSendListener listener) { send(packet); }
            };
            p.connection=new ServerGamePacketListenerImpl(p.server,connection,p) {
                @Override public void send(Packet<?> packet) { connection.send(packet); }
                @Override public void send(Packet<?> packet,PacketSendListener listener) { connection.send(packet); }
            };
            p.moveTo(Vec3.atBottomCenterOf(level==source?pos:departure.south(4)));
            source.addNewPlayer(p);players.add(p);
            if(level!=source) {
                p.teleportTo(level,pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
                h.assertTrue(p.serverLevel()==level && p.distanceToSqr(Vec3.atBottomCenterOf(pos))<1.0E-8,
                        "Destination-player fixture uses native transfer to its prepared platform");
            }
            // A client sends acceptance then movement after the position packet. Keep
            // this asynchronous; never call FamilyTeleport.tick/capture/install here.
            h.onEachTick(()->{
                if(closed || p.isRemoved())return;
                for(var packet:List.copyOf(outgoing)) {
                    outgoing.remove(packet);
                    p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
                    p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),true));
                }
            });
            return p;
        }
        void createFamily() {
            owner=player(source,departure.south(3));
            for(var kind:Kind.values()) {
                var mob=DudunkaMod.TYPES.get(kind).get().create(source);
                mob.initialize(owner.getUUID(),departure.west(4));
                mob.moveTo(departure.getX()-3.5+kind.ordinal()*2,departure.getY(),departure.getZ()-2.5,0,0);
                var tag=new CompoundTag();mob.addAdditionalSaveData(tag);
                tag.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);tag.putInt("Trust",73);
                mob.readAdditionalSaveData(tag);mob.setNoAi(true);mob.setPersistenceRequired();
                mob.setCustomName(Component.literal("Runtime "+kind));source.addFreshEntity(mob);family.add(mob);
                h.assertTrue(mob.commandStay(owner,false),"Real FOLLOW command accepted");
                h.assertTrue(mob.stage()==2 && mob.isAlive() && !mob.isRemoved() && !mob.staying() && !mob.atHomeMode()
                        && owner.getUUID().equals(mob.ownerId()) && source.getEntity(mob.getUUID())==mob
                        && mob.distanceToSqr(owner)<=64 && source.areEntitiesLoaded(new ChunkPos(mob.blockPosition()).toLong())
                        && !CarrierLedger.get(owner.server).carried(mob.getUUID()),"Live loaded adult FOLLOW eligibility");
                FamilyRegistry.get(owner.server).observe(mob);
                FamilyMemories.get(owner.server).record(owner.getUUID(),kind,FamilyMemories.Event.ADULT,source.getGameTime());
            }
            var ledger=FamilyFriendships.get(owner.server);
            for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)for(int sample=0;sample<=60;sample++)
                ledger.observe(owner.getUUID(),family.get(a).getUUID(),family.get(a).kind,family.get(b).getUUID(),family.get(b).kind,sample*20L);
            h.assertTrue(!source.isPositionEntityTicking(departure),"Insertion has not promoted the FULL source before dispatch");
            listen(EntityTeleportEvent.TeleportCommand.class,e->{
                if(e.getEntity()==owner) {
                    departureEvents++;
                    if(departureEvents==1)h.assertTrue(owner.serverLevel()==source && owner.distanceToSqr(Vec3.atBottomCenterOf(departure.south(3)))<.01,
                            "Real Forge departure event fires before player movement");
                }
            });
        }
        void snapshot() {
            for(var mob:family) {
                saved.put(mob.getUUID(),mob.saveWithoutId(new CompoundTag()));
                bonds.put(mob.getUUID(),FamilyFriendships.get(owner.server).relations(owner.getUUID(),mob.getUUID()));
            }
            memories=FamilyMemories.get(owner.server).owned(owner.getUUID());
        }
        String coordinates(){return (landing.getX()+.5)+" "+landing.getY()+" "+(landing.getZ()+.5);}
        String crossCommand(){return "execute in "+destination.dimension().location()+" run tp @s "+coordinates();}
        int command(String text){return owner.server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2),text);}
        <T extends net.minecraftforge.eventbus.api.Event> void listen(Class<T> type,Consumer<T> listener) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,type,listener);listeners.add(listener);
        }
        void ownerArrived() {
            h.assertTrue(owner.serverLevel()==destination && owner.position().distanceToSqr(Vec3.atBottomCenterOf(landing))<1.0E-8,
                    "Native player command reached exact destination before asynchronous family verification");
        }
        void verify(Set<UUID> expected) {
            int owned=0;
            var counts=new HashMap<UUID,Integer>();
            for(var level:owner.server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof Companion mob
                    && owner.getUUID().equals(mob.ownerId())) {owned++;counts.merge(mob.getUUID(),1,Integer::sum);}
            h.assertTrue(owned==3,"Exactly three companions for this owner, including any unexpected UUID clones");
            for(var original:family) {
                UUID id=original.getUUID();boolean moved=expected.contains(id);
                var level=moved?destination:source;var entity=level.getEntity(id);
                h.assertTrue(entity instanceof Companion,"Expected live UUID in "+level.dimension().location()+": "+id);
                var actual=(Companion)entity;
                h.assertTrue(counts.getOrDefault(id,0)==1,"Exact UUID appears once across all loaded entity collections");
                if(moved)h.assertTrue(actual.distanceToSqr(Vec3.atBottomCenterOf(landing))<=32
                        && MushroomVale.safeLanding(destination,actual.position(),actual),"Safe bounded family arrival");
                else h.assertTrue(actual==original && saved.get(id).get("Pos").equals(actual.saveWithoutId(new CompoundTag()).get("Pos")),
                        "Excluded/failed original remains at departure");
                var data=actual.saveWithoutId(new CompoundTag());
                for(var key:STABLE)h.assertTrue(Objects.equals(saved.get(id).get(key),data.get(key)),"Preserved persisted "+key+" for "+id);
                // Real NBT save/load boundary; this detached object is never inserted.
                var restored=DudunkaMod.TYPES.get(actual.kind).get().create(level);restored.load(data);
                for(var key:STABLE)h.assertTrue(Objects.equals(data.get(key),restored.saveWithoutId(new CompoundTag()).get(key)),"Saved identity/state round trip: "+key);
                h.assertTrue(bonds.get(id).equals(FamilyFriendships.get(owner.server).relations(owner.getUUID(),id)),"Friendships unchanged");
                if(moved) {
                    var indexed=FamilyRegistry.get(owner.server).member(id);
                    h.assertTrue(indexed!=null && indexed.dimension().equals(destination.dimension().location())
                            && indexed.pos().equals(actual.blockPosition()),"Registry follows real arrival");
                }
            }
            h.assertTrue(memories.equals(FamilyMemories.get(owner.server).owned(owner.getUUID())),"Existing memories preserved");
            forced.forEach((level,before)->h.assertTrue(before.equals(remoteForced(level)),"No remote forced chunk changes"));
        }
        Set<UUID> ids(){var result=new HashSet<UUID>();for(var mob:family)result.add(mob.getUUID());return result;}
        void run(Runnable dispatch,Set<Integer> movedKinds) {
            run(dispatch,movedKinds,false);
        }
        void runPreparationProbe() {
            h.startSequence().thenWaitUntil(this::ready).thenExecute(()->{
                try {
                    var fixture=FamilyTeleportFixture.requireReady(source.getServer());
                    h.assertTrue(fixture.initialCold>0 && fixture.initialSourceCold,"Fresh warmup began with missing FULL chunks");
                    h.assertTrue(fixture.cycles>1 && fixture.issued==fixture.required.size(),
                            "Dedicated tick callbacks dispatched every bounded warmup request");
                    fixture.assertPrepared();
                    h.assertTrue(fixture.platforms==3,"All three reusable platforms completed before GameTest clocks started");
                    LogUtils.getLogger().info("FAMILY_TP_FIXTURE_DISPATCH_PROBE_VERIFIED {}",fixture.describe());
                } finally {close();}
                LogUtils.getLogger().info("FAMILY_TELEPORT_RUNTIME_COMPLETE site={}",site);
            }).thenSucceed();
        }
        void run(Runnable dispatch,Set<Integer> movedKinds,boolean warmSource) {
            var expected=new HashSet<UUID>();
            var sequence=h.startSequence().thenWaitUntil(this::ready).thenExecute(this::createFamily);
            if(warmSource)sequence.thenWaitUntil(()->h.assertTrue(source.isPositionEntityTicking(departure),"Native source player simulation is active"));
            sequence.thenExecute(()->{
                dispatch.run();h.assertTrue(departureEvents>=1,"Registered vanilla Forge command departure event observed");
                for(int kind:movedKinds)expected.add(family.get(kind).getUUID());
            }).thenWaitUntil(()->h.assertTrue(destination.isPositionEntityTicking(landing)
                    && destination.areEntitiesLoaded(new ChunkPos(landing).toLong()),"Native destination player loading becomes ready"))
                    .thenWaitUntil(()->verify(expected)).thenIdle(5)
                    .thenExecute(()->{
                        try{verify(expected);}finally{close();}
                        LogUtils.getLogger().info("FAMILY_TELEPORT_RUNTIME_COMPLETE site={}",site);
                    }).thenSucceed();
        }
        @Override public void close() {
            if(closed)return;closed=true;listeners.forEach(MinecraftForge.EVENT_BUS::unregister);
            var remaining=new ArrayList<Companion>();
            if(owner!=null)for(var level:owner.server.getAllLevels())for(var entity:level.getAllEntities())
                if(entity instanceof Companion mob && owner.getUUID().equals(mob.ownerId()))remaining.add(mob);
            remaining.forEach(Entity::discard);
            // Use the real Forge logout event rather than calling the service helper.
            for(var p:players) {
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            }
            try {
                blocks.forEach((level,originals)->originals.forEach((p,state)->{
                    // On a failed fixture do not recreate/unload-load a missing chunk
                    // merely to restore test terrain. Retained FULL tickets normally
                    // guarantee this check succeeds until cleanup completes.
                    h.assertTrue(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null,
                            "Prepared chunk disappeared before fixture restoration: "+new ChunkPos(p));
                    level.setBlock(p,state,18);
                }));
            } finally {
                OPEN.remove(this);
            }
        }
    }

    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void freshServerDispatchesAndCompletesFixtureQueue(GameTestHelper h) {
        new Trip(h,12,false).runPreparationProbe();
    }

    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void implicitTpFromLoadedNonTickingSource(GameTestHelper h) {
        var t=new Trip(h,0,false);t.run(()->{
            t.snapshot();h.assertTrue(t.command("tp "+t.coordinates())==1,"Actual implicit /tp");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void implicitTeleportFromLoadedNonTickingSource(GameTestHelper h) {
        var t=new Trip(h,1,false);t.run(()->{
            t.snapshot();h.assertTrue(t.command("teleport "+t.coordinates())==1,"Actual implicit /teleport");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void executeInCrossDimensionUsesRegisteredLifecycle(GameTestHelper h) {
        var t=new Trip(h,2,true);t.run(()->{
            t.snapshot();h.assertTrue(t.command(t.crossCommand())==1,"Native execute redirect");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void sameDimensionDepartureLosesSourceSimulationTicket(GameTestHelper h) {
        var t=new Trip(h,9,false);t.run(()->{
            t.snapshot();h.assertTrue(t.command("tp "+t.coordinates())==1,"Native tp from ticking source");t.ownerArrived();
            h.assertTrue(!t.destination.isPositionEntityTicking(t.landing),"Arrival is actually deferred");
            h.onEachTick(()->{if(!t.closed && t.destination.getEntity(t.family.get(0).getUUID()) instanceof Companion mob
                    && mob.distanceToSqr(Vec3.atBottomCenterOf(t.landing))<=32)
                h.assertTrue(!t.source.isPositionEntityTicking(t.departure),"Loaded source loses simulation after owner departure");});
        },Set.of(0,1,2),true);
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void crossDimensionDepartureLosesSourceSimulationTicket(GameTestHelper h) {
        var t=new Trip(h,10,true);t.run(()->{
            t.listen(EntityTravelToDimensionEvent.class,e->{if(e.getEntity() instanceof Companion mob && t.family.contains(mob))
                h.assertTrue(t.source.areEntitiesLoaded(new ChunkPos(t.departure).toLong())
                        && !t.source.isPositionEntityTicking(t.departure),"Source is still loaded but no longer ticking at real transfer callback");});
            t.snapshot();h.assertTrue(t.command(t.crossCommand())==1,"Native cross-dimension tp from ticking source");t.ownerArrived();
        },Set.of(0,1,2),true);
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void consoleEntityDestinationUsesTargetOwnersFamily(GameTestHelper h) {
        var t=new Trip(h,3,true);t.run(()->{
            var target=t.player(t.destination,t.landing);t.snapshot();
            h.assertTrue(t.owner.server.getCommands().performPrefixedCommand(t.owner.server.createCommandSourceStack(),
                    "teleport "+t.owner.getUUID()+" "+target.getUUID())==1,"Real console source and entity arguments");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void implicitTpToPlayerInAnotherDimension(GameTestHelper h) {
        var t=new Trip(h,11,true);t.run(()->{
            var target=t.player(t.destination,t.landing);t.snapshot();
            h.assertTrue(t.command("tp "+target.getUUID())==1,"Actual implicit /tp destination player");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void commandBlockExecutesVanillaTp(GameTestHelper h) {
        var t=new Trip(h,4,false);t.run(()->{
            var pos=t.departure.west(5);t.source.setBlockAndUpdate(pos,Blocks.COMMAND_BLOCK.defaultBlockState());
            var block=(CommandBlockEntity)t.source.getBlockEntity(pos);t.snapshot();
            block.getCommandBlock().setCommand("tp "+t.owner.getUUID()+" "+t.coordinates());
            h.assertTrue(block.getCommandBlock().performCommand(t.source),"Actual BaseCommandBlock execution");t.ownerArrived();
        },Set.of(0,1,2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void stayAndPersistedHomeModesRemainAtSource(GameTestHelper h) {
        var t=new Trip(h,5,false);t.run(()->{
            h.assertTrue(t.family.get(0).commandStay(t.owner,true),"STAY accepted");
            // Persisted HOME mode is a valid exclusion even when its marker is unavailable.
            var home=t.family.get(1);var tag=new CompoundTag();home.addAdditionalSaveData(tag);
            tag.putBoolean("HomeMode",true);home.readAdditionalSaveData(tag);
            h.assertTrue(home.atHomeMode(),"Persisted HOME fixture");t.snapshot();
            h.assertTrue(t.command("tp "+t.coordinates())==1,"Owner tp with exclusions");t.ownerArrived();
        },Set.of(2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void unsafeArrivalLeavesLoadedOriginalsAndConsumesRequest(GameTestHelper h) {
        var t=new Trip(h,6,false);t.run(()->{
            for(var p:BlockPos.betweenClosed(t.landing.offset(-5,-1,-5),t.landing.offset(5,-1,5)))
                t.destination.setBlock(p,Blocks.MAGMA_BLOCK.defaultBlockState(),18);
            t.snapshot();h.assertTrue(t.command("tp "+t.coordinates())==1,"Owner native command unaffected");t.ownerArrived();
        },Set.of());
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void forgeVetoAndFailedNativeInsertionRetainExactSources(GameTestHelper h) {
        var t=new Trip(h,7,true);t.run(()->{
            t.listen(EntityTravelToDimensionEvent.class,e->{if(e.getEntity()==t.family.get(0))e.setCanceled(true);});
            t.listen(EntityJoinLevelEvent.class,e->{if(e.getLevel()==t.destination && e.getEntity().getUUID().equals(t.family.get(1).getUUID()))e.setCanceled(true);});
            t.snapshot();h.assertTrue(t.command(t.crossCommand())==1,"Owner native command succeeds");t.ownerArrived();
        },Set.of(2));
    }
    @GameTest(templateNamespace="dudunka_journey",template="empty",timeoutTicks=500,batch="isolated_tp_runtime")
    public static void supersedingCommandPreventsDeferredReplay(GameTestHelper h) {
        var t=new Trip(h,8,true);t.run(()->{
            t.snapshot();h.assertTrue(t.command(t.crossCommand())==1,"First actual vanilla command");t.ownerArrived();
            h.assertTrue(!t.destination.isPositionEntityTicking(t.landing),"First arrival must actually be pending");
            h.assertTrue(t.command("tp ~1 ~ ~")==1,"Superseding native command");
        },Set.of());
    }
}
