package io.github.golovinss.dudunka.test;

import com.mojang.brigadier.CommandDispatcher;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;

/** Real vanilla dispatcher/event/native entity transfer regressions, not distance simulation. */
@GameTestHolder("dudunka_journey")
@PrefixGameTestTemplate(false)
public final class FamilyTeleportTests {
    private static final Set<Trip> OPEN = new HashSet<>();
    private static final TicketType<UUID> COLD = TicketType.create("dudunka_test_command_full",Comparator.comparing(UUID::toString));
    private static void cleanup() { for(var trip:List.copyOf(OPEN))trip.close(); }
    @AfterBatch(batch="isolated_tp_same") public static void afterSame(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_alias") public static void afterAlias(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_cross") public static void afterCross(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_entity") public static void afterEntity(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_exclusions") public static void afterExclusions(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_cancel") public static void afterCancel(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_veto") public static void afterVeto(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_member_veto") public static void afterMemberVeto(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_hazards") public static void afterHazards(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_rewrite") public static void afterRewrite(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_young") public static void afterYoung(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_multi") public static void afterMulti(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_cold") public static void afterCold(ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_tp_pending_cancel") public static void afterPending(ServerLevel level) { cleanup(); }
    private static final List<String> STABLE=List.of("UUID","FamilyOwner","FamilyHome","HomeDimension","HomeMarker",
            "HomeMode","Furniture","FurnitureDimension","Staying","Trust","GrowthTicks","CustomName",
            "CharacterVariant","NextCharacterVariant","NoAI");

    private static final class Trip implements AutoCloseable {
        final FamilyGatewayTests.Fixture f;
        final ServerLevel destination;
        final BlockPos landing;
        final List<Companion> family;
        final Map<UUID,CompoundTag> saved=new HashMap<>();
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final Map<ServerLevel,Set<Long>> tickets=new HashMap<>();
        final Set<Long> fullChunks=new HashSet<>();
        final List<Consumer<?>> listeners=new ArrayList<>();
        final List<Runnable> cleanupActions=new ArrayList<>();
        final boolean cold;
        int oldView,oldSimulation;
        CompoundTag memories,friendship;
        boolean closed;
        Trip(GameTestHelper h,boolean cross) { this(h,cross,true); }
        Trip(GameTestHelper h,boolean cross,boolean warm) { this(h,cross,warm,null); }
        Trip(GameTestHelper h,boolean cross,boolean warm,BlockPos target) {
            cold=!warm;
            f=new FamilyGatewayTests.Fixture(h,true,495,true);
            family=f.family(f.owner,true); f.homes(family);
            for (var mob:family) {
                mob.commandStay(f.owner,false);
                mob.setCustomName(net.minecraft.network.chat.Component.literal("Teleport "+mob.kind));
                saved.put(mob.getUUID(),mob.saveWithoutId(new CompoundTag()));
            }
            memories=FamilyMemories.get(f.owner.server).save(new CompoundTag());
            friendship=FamilyFriendships.get(f.owner.server).save(new CompoundTag());
            destination=cross?f.owner.server.getLevel(MushroomVale.LEVEL):f.level;
            landing=target!=null?target:cross?new BlockPos(8,301,8):f.root.east(96);
            keep(f.level,f.root);
            if(warm)keep(destination,landing);
            else {
                var players=f.owner.server.getPlayerList();oldView=players.getViewDistance();oldSimulation=players.getSimulationDistance();
                // GameTestServer defaults to zero distances. Real player loading needs
                // nonzero distances; this FULL-only fixture ticket never enables ticks.
                players.setViewDistance(2);players.setSimulationDistance(2);
                var center=new ChunkPos(landing);
                // Entity ticking promotion needs a FULL two-chunk halo. Prepare its
                // blocks before the service's 40-tick deadline, without enabling ticks.
                for(int x=center.x-2;x<=center.x+2;x++)for(int z=center.z-2;z<=center.z+2;z++) {
                    var chunk=new ChunkPos(x,z);fullChunks.add(chunk.toLong());
                    destination.getChunkSource().addRegionTicket(COLD,chunk,0,f.owner.getUUID());destination.getChunk(x,z);
                }
            }
            // Test-only controlled platform. Production accompaniment never loads chunks.
            destination.getChunk(landing);
            for(var p:BlockPos.betweenClosed(landing.offset(-5,-1,-5),landing.offset(5,4,5))) {
                blocks.put(p.immutable(),destination.getBlockState(p));
                destination.setBlock(p,p.getY()==landing.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            }
            h.runAtTickTime(495,this::close);
            OPEN.add(this);
        }
        void keep(ServerLevel level,BlockPos pos) {
            var center=new ChunkPos(pos);
            for(int x=center.x-1;x<=center.x+1;x++)for(int z=center.z-1;z<=center.z+1;z++) {
                level.getChunk(x,z);long id=new ChunkPos(x,z).toLong();
                if(!level.getForcedChunks().contains(id)) {
                    tickets.computeIfAbsent(level,k->new HashSet<>()).add(id);level.setChunkForced(x,z,true);
                }
            }
        }
        boolean ready() {
            return sourceReady() && destination.areEntitiesLoaded(new ChunkPos(landing).toLong())
                    && destination.isPositionEntityTicking(landing);
        }
        boolean sourceReady() {
            return f.level.areEntitiesLoaded(new ChunkPos(f.root).toLong()) && f.level.isPositionEntityTicking(f.root)
                    && family.stream().allMatch(mob->f.level.getEntity(mob.getUUID())==mob);
        }
        void coldReady(GameTestHelper h) {
            h.assertTrue(sourceReady() && destination.areEntitiesLoaded(new ChunkPos(landing).toLong()),
                    "Source visible and cold FULL entity data loaded: source="+GameTestChunkDiagnostics.describe(f.level,f.root)
                    +"; target="+GameTestChunkDiagnostics.describe(destination,landing));
        }
        void assertCold(GameTestHelper h) {
            var chunk=destination.getChunkSource().getChunkNow(landing.getX()>>4,landing.getZ()>>4);
            h.assertTrue(chunk!=null && chunk.getFullStatus()==FullChunkStatus.FULL && !destination.isPositionEntityTicking(landing),
                    "Cold FULL target is not entity ticking: "+GameTestChunkDiagnostics.describe(destination,landing));
            System.out.println("FAMILY_TELEPORT_COLD "+GameTestChunkDiagnostics.describe(destination,landing));
        }
        void ready(GameTestHelper h,Runnable assertions) {
            ready(h,()->{},assertions);
        }
        void ready(GameTestHelper h,Runnable prerequisites,Runnable assertions) {
            h.startSequence().thenWaitUntil(()->{
                h.assertTrue(ready(),"Fixture entity sections ready: source="
                        +GameTestChunkDiagnostics.describe(f.level,f.root)+"; target="+GameTestChunkDiagnostics.describe(destination,landing));
                prerequisites.run();
            })
                    .thenExecute(()->{try{snapshotLedgers();assertions.run();}finally{close();}}).thenSucceed();
        }
        void snapshotLedgers() {
            // Baseline at the operation boundary, after asynchronous fixture setup.
            for(var mob:family)saved.put(mob.getUUID(),mob.saveWithoutId(new CompoundTag()));
            memories=FamilyMemories.get(f.owner.server).save(new CompoundTag());
            friendship=FamilyFriendships.get(f.owner.server).save(new CompoundTag());
        }
        String coordinates(){return (landing.getX()+.5)+" "+landing.getY()+" "+(landing.getZ()+.5);}
        String crossCommand(){return "execute in "+destination.dimension().location()+" run tp @s "+coordinates();}
        int command(String text) {
            return f.owner.server.getCommands().performPrefixedCommand(f.owner.createCommandSourceStack().withPermission(2),text);
        }
        <T extends net.minecraftforge.eventbus.api.Event> void listen(Class<T> type,Consumer<T> listener) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,type,listener);listeners.add(listener);
        }
        void arrived(GameTestHelper h,List<Companion> expected) {
            for(var original:expected) {
                var id=original.getUUID();var actual=destination.getEntity(id);
                h.assertTrue(actual instanceof Companion,"Companion arrives: "+id);
                var mob=(Companion)actual;
                h.assertTrue(mob.position().distanceToSqr(Vec3.atBottomCenterOf(landing))<=32
                        && MushroomVale.safeLanding(destination,mob.position(),mob),"Bounded distinct safe landing");
                int count=0;for(var level:f.owner.server.getAllLevels())if(level.getEntity(id)!=null)count++;
                h.assertTrue(count==1,"One live UUID across dimensions");
                var current=mob.saveWithoutId(new CompoundTag());
                for(var key:STABLE)h.assertTrue(Objects.equals(saved.get(id).get(key),current.get(key)),"Preserved "+key);
                var indexed=FamilyRegistry.get(f.owner.server).member(id);
                h.assertTrue(indexed.dimension().equals(destination.dimension().location())&&indexed.pos().equals(mob.blockPosition()),"Immediate live registry position");
            }
            h.assertTrue(memories.equals(FamilyMemories.get(f.owner.server).save(new CompoundTag())),"Memories unchanged");
            h.assertTrue(friendship.equals(FamilyFriendships.get(f.owner.server).save(new CompoundTag())),"Friendship unchanged");
        }
        void stayed(GameTestHelper h,Companion mob) {
            h.assertTrue(f.level.getEntity(mob.getUUID())==mob&&!mob.isRemoved(),"Original live source object retained");
            h.assertTrue(mob.position().equals(new Vec3(saved.get(mob.getUUID()).getList("Pos",6).getDouble(0),
                    saved.get(mob.getUUID()).getList("Pos",6).getDouble(1),saved.get(mob.getUUID()).getList("Pos",6).getDouble(2))),
                    "Source position retained: uuid="+mob.getUUID()+", kind="+mob.kind+", before="+saved.get(mob.getUUID()).get("Pos")
                    +", actual="+mob.position()+", stay="+mob.staying()+", home="+mob.atHomeMode()
                    +", carried="+CarrierLedger.get(f.owner.server).carried(mob.getUUID()));
        }
        @Override public void close() {
            if(closed)return;closed=true;
            listeners.forEach(MinecraftForge.EVENT_BUS::unregister);
            FamilyTeleport.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.owner));
            cleanupActions.forEach(Runnable::run);
            f.close();blocks.forEach((p,state)->destination.setBlock(p,state,18));
            tickets.forEach((level,chunks)->chunks.forEach(id->{var c=new ChunkPos(id);level.setChunkForced(c.x,c.z,false);}));
            if(cold) {
                fullChunks.forEach(id->destination.getChunkSource().removeRegionTicket(COLD,new ChunkPos(id),0,f.owner.getUUID()));
                var players=f.owner.server.getPlayerList();players.setViewDistance(oldView);players.setSimulationDistance(oldSimulation);
            }
            OPEN.remove(this);
        }
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_same")
    public static void vanillaTpFarAwayPreservesIdentityCareHomesAndLedgers(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            h.assertTrue(t.command("tp @s "+t.coordinates())==1,"Native tp result");t.arrived(h,t.family);
            var positions=t.family.stream().map(Companion::position).toList();
            for(int i=0;i<4;i++)FamilyTeleport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,t.f.owner.server));
            h.assertTrue(positions.equals(t.family.stream().map(Companion::position).toList()),"Repeated callbacks never transfer twice");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_alias")
    public static void aliasesRelativeLocalFacingSelectorsAndIdempotentInstallation(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            var dispatcher=new CommandDispatcher<CommandSourceStack>();TeleportCommand.register(dispatcher);
            var redirect=dispatcher.getRoot().getChild("tp").getRedirect();
            FamilyTeleport.install(dispatcher);FamilyTeleport.install(dispatcher);
            h.assertTrue(dispatcher.getRoot().getChild("tp").getRedirect()==redirect,"Original redirect identity retained");
            try {
                dispatcher.execute("teleport @s ~96 ~ ~-3 facing 0 160 0",t.f.owner.createCommandSourceStack().withPermission(2));
            }catch(Exception e){throw new AssertionError(e);}
            t.arrived(h,t.family);
            // The vanilla parser also retains local-coordinate and implicit-self forms.
            h.assertTrue(t.command("tp ^ ^ ^2")==1,"Local coordinate teleport");
            h.assertTrue(t.command("teleport ~ ~ ~1")==1,"Implicit self and relative coordinates");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_cross")
    public static void executeInCrossDimensionAndReturnAccompanyAfterOwnerArrival(GameTestHelper h) {
        var t=new Trip(h,true);t.ready(h,()->{
            t.listen(EntityTravelToDimensionEvent.class,e->{
                if(e.getEntity() instanceof Companion mob&&t.f.owner.getUUID().equals(mob.ownerId()))
                    h.assertTrue(t.f.owner.serverLevel()==t.destination,"Owner arrives before companion travel callbacks");
            });
            h.assertTrue(t.command(t.crossCommand())==1,"Execute in native tp");t.arrived(h,t.family);
            // Return also goes through vanilla and never creates/consumes Vale bookmarks.
            h.assertTrue(MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())==null,"No gateway bookmark mutation");
            t.listeners.forEach(MinecraftForge.EVENT_BUS::unregister);t.listeners.clear();
            var p=t.f.root.south(3);
            t.command("execute in "+t.f.level.dimension().location()+" run teleport @s "+(p.getX()+.5)+" "+p.getY()+" "+(p.getZ()+.5));
            for(var mob:t.family)h.assertTrue(t.f.level.getEntity(mob.getUUID()) instanceof Companion,"Native cross-dimension return");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_entity")
    public static void entityDestinationAndConsoleSourceMoveTargetsFamily(GameTestHelper h) {
        var t=new Trip(h,true);t.ready(h,()->{
            var target=t.f.player();target.teleportTo(t.destination,t.landing.getX()+.5,t.landing.getY(),t.landing.getZ()+.5,0,0);
            int result=t.f.owner.server.getCommands().performPrefixedCommand(t.f.owner.server.createCommandSourceStack(),
                    "teleport "+t.f.owner.getUUID()+" "+target.getUUID());
            h.assertTrue(result==1,"Console explicit player target to entity destination");t.arrived(h,t.family);
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_exclusions")
    public static void stayHomeCarriedForeignFarAndStaleMembersNeverSubstitute(GameTestHelper h) {
        var t=new Trip(h,false);
        var homeMob=t.family.get(1);var home=homeMob.saveWithoutId(new CompoundTag()).getLong("FamilyHome");
        var anchor=BlockPos.of(home);
        // Build before readiness: roof validity reads skylight, which is updated
        // asynchronously. Refreshing the marker cache alone cannot settle lighting.
        var room=new LinkedHashMap<BlockPos,BlockState>();
        room.put(anchor.above(3),Blocks.STONE.defaultBlockState());
        room.put(anchor.north(2),Blocks.WHITE_BED.defaultBlockState());
        room.put(anchor.north(2).west(),Blocks.BARREL.defaultBlockState());
        room.put(anchor.north(2).east(),Blocks.CAKE.defaultBlockState());
        room.put(anchor.north(2).east(2),Blocks.TORCH.defaultBlockState());
        for(var p:List.copyOf(room.keySet())) if(p.getY()==anchor.getY())room.put(p.below(),Blocks.STONE.defaultBlockState());
        room.forEach((p,state)->{t.f.blocks.putIfAbsent(p,t.f.level.getBlockState(p));t.f.level.setBlock(p,state,18);});
        System.out.println("FAMILY_TELEPORT_HOME_PREPARED anchor="+anchor+", skyBrightness="
                +t.f.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,anchor.above())
                +", conditions="+((HomeMarkerEntity)t.f.level.getBlockEntity(anchor)).conditions(true));
        t.ready(h,()->assertHomePrerequisites(h,t,homeMob,anchor),()->{
            h.assertTrue(t.family.get(0).commandStay(t.f.owner,true) && t.family.get(0).staying(),"STAY fixture command accepted");
            h.assertTrue(homeMob.commandHome(t.f.owner) && homeMob.atHomeMode(),"HOME fixture command accepted with a valid room");
            var carried=t.family.get(2);var ledger=CarrierLedger.get(t.f.owner.server);UUID token=UUID.randomUUID();
            ledger.capture(carried.getUUID(),token,t.f.owner.getUUID(),carried.saveWithoutId(new CompoundTag()));
            t.cleanupActions.add(()->ledger.consume(carried.getUUID(),token,t.f.owner.getUUID()));
            var foreign=t.f.friend(Kind.DUDUNKA,t.f.player());foreign.commandStay(t.f.players.get(1),false);
            var far=t.f.friend(Kind.SYUSYA,t.f.owner);far.commandStay(t.f.owner,false);far.moveTo(Vec3.atBottomCenterOf(t.f.root.south(12)));
            var foreignPos=foreign.position();var farPos=far.position();
            t.command("tp @s "+t.coordinates());for(var mob:t.family)t.stayed(h,mob);
            h.assertTrue(t.f.level.getEntity(foreign.getUUID())==foreign&&t.f.level.getEntity(far.getUUID())==far
                    &&foreign.position().equals(foreignPos)&&far.position().equals(farPos),"Foreign and distant members retain exact source positions");
            ledger.consume(carried.getUUID(),token,t.f.owner.getUUID());
            // Stale last-known data cannot materialize another companion.
            var stale=t.f.friend(Kind.SYUSYA,t.f.owner);UUID staleId=stale.getUUID();
            t.cleanupActions.add(()->FamilyRegistry.get(t.f.owner.server).forget(staleId));
            stale.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            t.command("tp @s ~1 ~ ~");
            for(var level:t.f.owner.server.getAllLevels())h.assertTrue(level.getEntity(staleId)==null,"No recreation from stale registry");
        });
    }

    /** Diagnose each commandHome gate without changing modes or moving the companion. */
    private static void assertHomePrerequisites(GameTestHelper h,Trip t,Companion mob,BlockPos anchor) {
        var level=t.f.level;var owner=t.f.owner;var data=mob.saveWithoutId(new CompoundTag());
        String context="uuid="+mob.getUUID()+", kind="+mob.kind+", anchor="+anchor;
        h.assertTrue(!mob.level().isClientSide && mob.level()==level && owner.level()==level,"HOME server/dimension prerequisite: "+context);
        h.assertTrue(mob.isAlive() && owner.isAlive() && !owner.isSpectator(),"HOME living owner/member prerequisite: "+context);
        h.assertTrue(owner.getUUID().equals(mob.ownerId()),"HOME companion ownership prerequisite: "+context);
        h.assertTrue(mob.distanceToSqr(owner)<=4096,"HOME commandStay distance prerequisite: "+context+", distanceSquared="+mob.distanceToSqr(owner));
        h.assertTrue(data.getBoolean("HomeMarker") && data.contains("FamilyHome") && data.getLong("FamilyHome")==anchor.asLong(),
                "HOME marker binding prerequisite: "+context);
        h.assertTrue(level.dimension().location().toString().equals(data.getString("HomeDimension")),
                "HOME binding dimension prerequisite: "+context+", boundDimension="+data.getString("HomeDimension"));
        h.assertTrue(level.hasChunkAt(anchor),"HOME loaded marker prerequisite: "+context);
        h.assertTrue(level.getBlockEntity(anchor) instanceof HomeMarkerEntity,"HOME marker block entity prerequisite: "+context);
        var marker=(HomeMarkerEntity)level.getBlockEntity(anchor);
        h.assertTrue(owner.getUUID().equals(marker.ownerId()) && marker.kind()==mob.kind,"HOME marker ownership/Kind prerequisite: "+context);
        var conditions=marker.conditions(true);
        h.assertTrue(conditions.roof(),"HOME roof/skylight prerequisite: "+context+", skyBrightness="
                +level.getBrightness(net.minecraft.world.level.LightLayer.SKY,anchor.above())+", conditions="+conditions);
        h.assertTrue(conditions.bed(),"HOME bed prerequisite: "+context);
        h.assertTrue(conditions.chest(),"HOME chest/barrel prerequisite: "+context);
        h.assertTrue(conditions.light(),"HOME light source prerequisite: "+context);
        h.assertTrue(conditions.food(),"HOME cake prerequisite: "+context);
        h.assertTrue(marker.validFor(mob) && anchor.equals(mob.homeAnchor()),"HOME validFor/homeAnchor prerequisite: "+context);
        h.assertTrue(mob.homePosition()!=null,"HOME nearby safe-standing prerequisite: "+context+", memberPosition="+mob.position());
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_cancel")
    public static void lateCommandCancellationAndInvalidPositionLeaveFamilyUntouched(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            var before=t.f.owner.position();
            t.listen(EntityTeleportEvent.TeleportCommand.class,e->{if(e.getEntity()==t.f.owner)e.setCanceled(true);});
            t.command("tp @s "+t.coordinates());
            h.assertTrue(t.f.owner.position().equals(before),"Canceled owner remains");for(var mob:t.family)t.stayed(h,mob);
            t.listeners.forEach(MinecraftForge.EVENT_BUS::unregister);t.listeners.clear();
            h.assertTrue(t.command("tp @s 30000001 160 0")==0,"Invalid position fails vanilla command");
            for(var mob:t.family)t.stayed(h,mob);
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_veto")
    public static void playerTravelVetoEvenAtIdenticalCoordinatesNeverMovesCompanions(GameTestHelper h) {
        var t=new Trip(h,true);t.ready(h,()->{
            t.listen(EntityTravelToDimensionEvent.class,e->{if(e.getEntity()==t.f.owner)e.setCanceled(true);});
            var p=t.f.owner.position();
            t.command("execute in dudunka:mushroom_vale run tp @s "+p.x+" "+p.y+" "+p.z);
            h.assertTrue(t.f.owner.serverLevel()==t.f.level,"Native Forge player travel veto");
            for(var mob:t.family)t.stayed(h,mob);
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_member_veto")
    public static void companionVetoAndDestinationJoinRejectionRetainOriginalSource(GameTestHelper h) {
        var t=new Trip(h,true);t.ready(h,()->{
            var denied=t.family.get(0);var rejected=t.family.get(1);
            t.listen(EntityTravelToDimensionEvent.class,e->{if(e.getEntity()==denied)e.setCanceled(true);});
            t.listen(EntityJoinLevelEvent.class,e->{if(e.getLevel()==t.destination&&e.getEntity().getUUID().equals(rejected.getUUID()))e.setCanceled(true);});
            t.command(t.crossCommand());t.stayed(h,denied);t.stayed(h,rejected);t.arrived(h,List.of(t.family.get(2)));
            h.assertTrue(t.f.owner.serverLevel()==t.destination,"Player command is never rolled back by accompaniment");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_hazards")
    public static void unsafeDestinationHasBoundedFailureAndNoChunkRequests(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            for(var p:BlockPos.betweenClosed(t.landing.offset(-5,-1,-5),t.landing.offset(5,-1,5)))
                t.destination.setBlock(p,Blocks.MAGMA_BLOCK.defaultBlockState(),18);
            int chunks=t.f.level.getChunkSource().getLoadedChunksCount();var forced=Set.copyOf(t.f.level.getForcedChunks());
            t.command("tp @s "+t.coordinates());for(var mob:t.family)t.stayed(h,mob);
            h.assertTrue(t.f.level.getChunkSource().getLoadedChunksCount()==chunks&&forced.equals(t.f.level.getForcedChunks()),"Companion safety scan never loads/forces chunks");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_rewrite")
    public static void lateEventTargetRewriteAndModeMutationAreRevalidated(GameTestHelper h) {
        var t=new Trip(h,true);t.ready(h,()->{
            t.listen(EntityTeleportEvent.TeleportCommand.class,e->{if(e.getEntity()==t.f.owner){e.setTargetX(t.landing.getX()+1.5);t.family.get(0).commandStay(t.f.owner,true);}});
            t.command(t.crossCommand());t.stayed(h,t.family.get(0));t.arrived(h,t.family.subList(1,3));
            h.assertTrue(t.f.owner.getX()==t.landing.getX()+1.5,"Final Forge target honored");
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_young")
    public static void juvenileFollowModeAccompaniesWithoutChangingGrowth(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            var young=t.family.get(2);var data=new CompoundTag();young.addAdditionalSaveData(data);
            data.putInt("GrowthTicks",0);young.readAdditionalSaveData(data);
            t.saved.put(young.getUUID(),young.saveWithoutId(new CompoundTag()));t.snapshotLedgers();
            h.assertTrue(young.stage()==0,"Juvenile fixture");
            t.command("tp @s "+t.coordinates());t.arrived(h,t.family);
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_multi")
    public static void selectorTeleportKeepsTwoOwnersFamiliesSeparate(GameTestHelper h) {
        var t=new Trip(h,false);t.ready(h,()->{
            var other=t.f.player();var second=t.f.family(other,false);
            for(var mob:second){mob.commandStay(other,false);mob.moveTo(t.f.root.getX()-1.5+mob.kind.ordinal()*2,t.f.root.getY(),t.f.root.getZ()+.5,0,0);}
            h.assertTrue(t.command("teleport @e[type=minecraft:player,distance=..8] "+t.coordinates())==2,"Two selected players moved");
            var all=new ArrayList<Companion>(t.family);all.addAll(second);
            for(var original:all) {
                h.assertTrue(t.destination.getEntity(original.getUUID()) instanceof Companion mob
                        && mob.position().distanceToSqr(Vec3.atBottomCenterOf(t.landing))<=32
                        && mob.ownerId().equals(original.ownerId()),"Each captured UUID arrives with its own owner binding");
            }
        });
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_cold")
    public static void coldArrivalWaitsForVanillaPlayerLoadingWithoutSourceTickets(GameTestHelper h) {
        var t=new Trip(h,true,false,new BlockPos(16392,301,-16376));
        var sourceForced=Set.copyOf(t.f.level.getForcedChunks());var targetForced=Set.copyOf(t.destination.getForcedChunks());
        h.startSequence().thenWaitUntil(()->t.coldReady(h))
                .thenExecute(()->{
                    t.snapshotLedgers();
                    t.assertCold(h);
                    t.command(t.crossCommand());for(var mob:t.family)t.stayed(h,mob);
                })
                .thenWaitUntil(()->{for(var mob:t.family)h.assertTrue(t.destination.getEntity(mob.getUUID()) instanceof Companion,"Deferred native arrival");})
                .thenExecute(()->{try{
                    h.assertTrue(t.destination.isPositionEntityTicking(t.landing),"Native player loading enables arrival simulation");
                    h.assertTrue(sourceForced.equals(t.f.level.getForcedChunks())&&targetForced.equals(t.destination.getForcedChunks()),
                            "Command accompaniment adds no forced chunks at source or target");
                    t.arrived(h,t.family);
                }finally{t.close();}}).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=500,batch="isolated_tp_pending_cancel")
    public static void coldArrivalTimeoutAndSupersedingCommandCannotReplayCapturedFamily(GameTestHelper h) {
        var t=new Trip(h,true,false,new BlockPos(18440,301,-16376));
        h.startSequence().thenWaitUntil(()->t.coldReady(h))
                .thenExecute(()->{
                    try {
                        t.snapshotLedgers();t.assertCold(h);
                        t.command(t.crossCommand());for(var mob:t.family)t.stayed(h,mob);
                        // Advance only the service's bounded clock, synchronously, before
                        // vanilla has any opportunity to make the cold chunk entity ticking.
                        for(int i=0;i<42;i++)FamilyTeleport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,t.f.owner.server));
                        for(var mob:t.family)t.stayed(h,mob);
                        t.command("execute in "+t.f.level.dimension().location()+" run tp @s "+(t.f.root.getX()+.5)+" "+t.f.root.getY()+" "+(t.f.root.getZ()+3.5));
                        t.command(t.crossCommand());
                        t.command("tp @s ~1 ~ ~"); // No source family at this new departure; cancels old request.
                        for(int i=0;i<42;i++)FamilyTeleport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,t.f.owner.server));
                        for(var mob:t.family)t.stayed(h,mob);
                    }catch(RuntimeException|Error failure){t.close();throw failure;}
                })
                .thenWaitUntil(()->h.assertTrue(t.destination.isPositionEntityTicking(t.landing)
                        &&t.destination.areEntitiesLoaded(new ChunkPos(t.landing).toLong()),
                        "Native arrival becomes ready after cancellation: "+GameTestChunkDiagnostics.describe(t.destination,t.landing)))
                .thenExecute(()->{try{
                    FamilyTeleport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,t.f.owner.server));
                    for(var mob:t.family)t.stayed(h,mob);
                }finally{t.close();}}).thenSucceed();
    }
}
