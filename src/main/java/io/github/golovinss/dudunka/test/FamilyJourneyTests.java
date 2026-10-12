package io.github.golovinss.dudunka.test;

import com.mojang.brigadier.CommandDispatcher;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_journey")
@PrefixGameTestTemplate(false)
public final class FamilyJourneyTests {
    private static final List<String> STABLE = List.of("UUID", "FamilyOwner", "FamilyHome", "HomeDimension", "HomeMarker",
            "HomeMode", "Furniture", "FurnitureDimension", "Staying", "Trust", "GrowthTicks", "CustomName",
            "CharacterVariant", "NextCharacterVariant", "NoAI");

    private static final class Trip implements AutoCloseable {
        final FamilyGatewayTests.Fixture f;
        final FamilyGatewayTests.Arrival arrival;
        final List<UUID> ids;
        boolean closed;
        final Map<ServerLevel, Set<Long>> testTickets = new HashMap<>();
        void keepVisible(ServerLevel level, net.minecraft.world.level.ChunkPos center) {
            for (int x=center.x-1;x<=center.x+1;x++) for(int z=center.z-1;z<=center.z+1;z++) {
                level.getChunk(x,z);
                long id=new net.minecraft.world.level.ChunkPos(x,z).toLong();
                if (!level.getForcedChunks().contains(id)) {
                    testTickets.computeIfAbsent(level, k->new HashSet<>()).add(id); level.setChunkForced(x,z,true);
                }
            }
        }
        Trip(GameTestHelper h) { this(h,true); }
        Trip(GameTestHelper h, boolean keepTargetVisible) {
            f = new FamilyGatewayTests.Fixture(h, true, 1195, true);
            FamilyGatewayTests.savedUnlock(f);
            var family = f.family(f.owner, false); // Journey must not recheck ritual friendship.
            f.homes(family);
            for (var mob : family) {
                mob.commandStay(f.owner, false);
                var tag = new CompoundTag(); mob.addAdditionalSaveData(tag); tag.putInt("Trust", 0); mob.readAdditionalSaveData(tag);
                mob.setCustomName(net.minecraft.network.chat.Component.literal("Journey " + mob.kind));
            }
            ids = family.stream().map(Companion::getUUID).toList();
            arrival = new FamilyGatewayTests.Arrival(f.owner);
            // Test-only visibility fixture, matching recovery_dimension. Production adds no tickets.
            keepVisible(f.level, new net.minecraft.world.level.ChunkPos(f.root));
            if (keepTargetVisible) keepVisible(arrival.vale, new net.minecraft.world.level.ChunkPos(0,0));
            h.runAtTickTime(1195, this::close);
        }
        void ready(GameTestHelper h, Runnable assertions) {
            h.startSequence().thenWaitUntil(() -> h.assertTrue(arrival.vale.areEntitiesLoaded(0L) && ids.stream().allMatch(id -> f.level.getEntity(id) instanceof Companion),
                    "Arrival entity sections must be visible before synchronous group travel"))
                    .thenExecute(() -> { try { assertions.run(); } finally { close(); } }).thenSucceed();
        }
        Companion live(UUID id) {
            for (var level : f.owner.server.getAllLevels()) if (level.getEntity(id) instanceof Companion c) return c;
            throw new GameTestAssertException("Missing live companion " + id);
        }
        void unique(GameTestHelper h, ServerLevel expected) {
            for (var id : ids) {
                int count = 0;
                for (var level : f.owner.server.getAllLevels()) if (level.getEntity(id) != null) count++;
                h.assertTrue(count == 1 && expected.getEntity(id) instanceof Companion,
                        "Exactly one instance of each UUID in the expected dimension");
                var saved = FamilyRegistry.get(f.owner.server).member(id);
                h.assertTrue(saved != null && saved.dimension().equals(expected.dimension().location())
                        && saved.pos().equals(live(id).blockPosition()), "Registry follows the canonical live instance immediately");
            }
        }
        void enter(GameTestHelper h) {
            f.gateway().interact(f.owner);
            h.assertTrue(f.owner.serverLevel() == arrival.vale, "Previously awakened gateway enters with family");
            unique(h, arrival.vale);
        }
        @Override public void close() { if (closed) return; closed=true; f.close(); arrival.close();
            testTickets.forEach((level, chunks)->chunks.forEach(id->{var c=new net.minecraft.world.level.ChunkPos(id);level.setChunkForced(c.x,c.z,false);})); }
    }

    private static int command(ServerPlayer player, String text) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>(); MushroomVale.registerCommands(dispatcher);
        try { return dispatcher.execute(text, player.createCommandSourceStack().withPermission(0)); }
        catch (Exception e) { throw new GameTestAssertException(e.toString()); }
    }
    private static MushroomVale.TravelResult moveInto(ServerPlayer player,List<Companion> family,BlockPos anchor,
                                                       ServerLevel destination,BlockPos landing,float yaw,float pitch) {
        return moveInto(player,family,anchor,destination,landing,yaw,pitch,null);
    }
    private static MushroomVale.TravelResult moveInto(ServerPlayer player,List<Companion> family,BlockPos anchor,
                                                       ServerLevel destination,BlockPos landing,float yaw,float pitch,
                                                       java.util.function.Supplier<MushroomVale.TravelResult> retry) {
        try {
            var method=FamilyJourney.class.getDeclaredMethod("move",ServerPlayer.class,List.class,BlockPos.class,
                    ServerLevel.class,BlockPos.class,float.class,float.class,java.util.function.Supplier.class);
            method.setAccessible(true);
            return (MushroomVale.TravelResult)method.invoke(null,player,family,anchor,destination,landing,yaw,pitch,retry);
        } catch(ReflectiveOperationException failure) {
            throw new GameTestAssertException("Could not invoke family journey coordinator: "+failure);
        }
    }
    private static Map<UUID, CompoundTag> snapshots(Trip t) {
        var result = new HashMap<UUID, CompoundTag>();
        for (var id : t.ids) result.put(id, t.live(id).saveWithoutId(new CompoundTag()));
        return result;
    }
    private static void retained(GameTestHelper h, Trip t, Map<UUID, CompoundTag> before) {
        for (var id : t.ids) {
            var current = t.live(id).saveWithoutId(new CompoundTag());
            for (var key : STABLE) h.assertTrue(Objects.equals(before.get(id).get(key), current.get(key)),
                    "Journey preserves " + key + " for " + id);
        }
    }
    // Gather on actual safe approaches without relying on the return frame's chosen position.
    private static void gather(Trip t, BlockPos root) {
        var level = t.f.owner.serverLevel();
        var entities = new ArrayList<Entity>(); entities.add(t.f.owner);
        for (var id : t.ids) entities.add(t.live(id));
        for (var entity : entities) {
            boolean placed = false;
            search: for (int r = 2; r <= 4; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                var pos = root.offset(dx, 0, dz);
                // Check the actual entity, including occupancy by the player.
                if (!MushroomVale.safeLanding(level, pos, entity)) continue;
                entity.moveTo(Vec3.atBottomCenterOf(pos)); placed = true; break search;
            }
            if (!placed) throw new GameTestAssertException("No clear gate approach for fixture");
        }
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_round_trip")
    public static void groupPhysicalRoundTripRetainsIdentityHomesFurnitureFriendshipAndSaveData(GameTestHelper h) {
        var t = new Trip(h);
        t.ready(h, () -> {
            var before = snapshots(t);
            var bonds = FamilyFriendships.get(t.f.owner.server).save(new CompoundTag());
            t.enter(h); retained(h, t, before);
            var registry = FamilyRegistry.load(FamilyRegistry.get(t.f.owner.server).save(new CompoundTag()));
            for (var id : t.ids) {
                h.assertTrue(registry.member(id).dimension().equals(MushroomVale.ID), "Registry save/load retains destination");
                // Actual registered entity loader, not a production backup reconstruction.
                var tag = t.live(id).saveWithoutId(new CompoundTag());
                var preview = DudunkaMod.TYPES.get(t.live(id).kind).get().create(t.arrival.vale); preview.load(tag);
                h.assertTrue(preview.getUUID().equals(id) && preview.ownerId().equals(t.f.owner.getUUID()), "Entity NBT round trip retains identity");
                var again = preview.saveWithoutId(new CompoundTag());
                for (var key : STABLE) h.assertTrue(Objects.equals(tag.get(key), again.get(key)), "Entity reload retains " + key);
            }
            var bookmarks = MushroomValeReturns.get(t.f.owner.server);
            h.assertTrue(MushroomValeReturns.load(bookmarks.save(new CompoundTag())).point(t.f.owner.getUUID()) != null, "Entry bookmark survives serialization");
            var root = MushroomValeReturnGateway.find(t.arrival.vale);
            h.assertTrue(root != null, "Physical entry creates or reuses a return gateway");
            gather(t, root);
            h.assertTrue(FamilyJourney.inspect(t.f.owner,root).ready(), "Gathered physical family is eligible: "+FamilyJourney.inspect(t.f.owner,root));
            FamilyGatewayTests.clickReturn(t.f.owner, root);
            h.assertTrue(t.f.owner.serverLevel() == t.f.level && bookmarks.point(t.f.owner.getUUID()) == null, "Physical group return consumes bookmark only on success");
            t.unique(h, t.f.level); retained(h, t, before);
            h.assertTrue(bonds.equals(FamilyFriendships.get(t.f.owner.server).save(new CompoundTag())), "Journey never awards or rewrites friendship");
            for (var id : t.ids) {
                var mob = t.live(id); var home = BlockPos.of(before.get(id).getLong("FamilyHome"));
                var marker=(HomeMarkerEntity)t.f.level.getBlockEntity(home);
                h.assertTrue(marker!=null && marker.ownerId().equals(mob.ownerId()) && marker.kind()==mob.kind,
                        "Original home marker retains compatible ownership and Kind, even if its room is incomplete");
                var furniture = (FurnitureEntity)t.f.level.getBlockEntity(mob.furniturePosition());
                h.assertTrue(furniture != null && furniture.assigned(mob), "Original furniture still recognizes the same companion");
            }
            // The same saved owner unlock also works on a natural arch, without ritual migration.
            t.f.gateway().markForgottenArch();var oldGate=t.f.gateway().saveWithFullMetadata();t.f.gateway().load(oldGate);
            gather(t,t.f.root);t.enter(h);
            h.assertTrue(t.f.gateway().forgottenArch(),"Previously awakened natural arch metadata remains valid");
            h.assertTrue(command(t.f.owner,"dudunka return_family")==1,"Natural arch family can return without another ritual");
            t.unique(h,t.f.level);retained(h,t,before);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_command")
    public static void familyCommandWorksWithoutGateAndEmergencyReturnAlwaysRemainsSolo(GameTestHelper h) {
        var t = new Trip(h);
        t.ready(h, () -> {
            t.enter(h);
            var root = MushroomValeReturnGateway.find(t.arrival.vale); if (root != null) t.arrival.vale.removeBlock(root, false);
            h.assertTrue(command(t.f.owner, "dudunka return_family") == 1, "Family command requires neither gate nor operator; eligibility="+FamilyJourney.inspect(t.f.owner,t.f.owner.blockPosition()));
            t.unique(h, t.f.level);
            gather(t, t.f.root); t.enter(h);
            var missing = t.live(t.ids.get(2)); missing.commandStay(t.f.owner, true);
            var bookmark = MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID());
            h.assertTrue(command(t.f.owner, "dudunka return_family") == 0
                    && bookmark.equals(MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())), "Ineligible family return keeps bookmark");
            h.assertTrue(command(t.f.owner, "dudunka return") == 1 && t.f.owner.serverLevel() == t.f.level, "Emergency solo command never requires companions");
            t.unique(h, t.arrival.vale);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_eligibility")
    public static void journeyRejectsMissingYoungDuplicateCarriedForeignTetheredMountedAndUnsafeMembers(GameTestHelper h) {
        try (var t = new Trip(h)) {
            var f = t.f; var mob = f.friends.get(2); var pos = mob.position();
            h.assertTrue(FamilyJourney.inspect(f.owner, f.root).ready(), "Zero trust and no friendship still allow travel after awakening");
            mob.moveTo(pos.add(30,0,0));
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(), "Partial family never silently becomes solo");
            f.gateway().interact(f.owner); h.assertTrue(f.owner.serverLevel()==f.level, "Missing member blocks actual gate entry"); mob.moveTo(pos);
            var saved = mob.saveWithoutId(new CompoundTag()); var young=saved.copy(); young.putInt("GrowthTicks",0); mob.load(young);
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(), "Juvenile rejected"); mob.load(saved);
            var repeated=new ArrayList<>(f.friends); repeated.add(mob);
            h.assertTrue(!FamilyJourney.evaluate(f.owner,f.root,repeated).ready(), "Repeated UUID rejected");
            var duplicate=f.friend(Kind.SYUSYA,f.owner); duplicate.commandStay(f.owner,false);
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(), "Ambiguous kind rejected"); duplicate.discard();
            var token=UUID.randomUUID(); var ledger=CarrierLedger.get(f.owner.server); ledger.capture(mob.getUUID(),token,f.owner.getUUID());
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(), "Carried/live conflict rejected"); ledger.consume(mob.getUUID(),token,f.owner.getUUID());
            var foreign=saved.copy();foreign.putUUID("FamilyOwner",UUID.randomUUID());mob.load(foreign);
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(), "Foreign companion cannot substitute for missing owned one");mob.load(saved);
            mob.setLeashedTo(f.owner,false); h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(),"Leash rejected");mob.dropLeash(false,false);
            mob.startRiding(f.friends.get(0),true);h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(),"Passenger and vehicle rejected");mob.stopRiding();
            mob.commandStay(f.owner,true);h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(),"WAIT rejected");mob.commandStay(f.owner,false);
            mob.beginPortalPose(f.root, f.level.getGameTime()+100);h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(),"Conflicting ritual rejected");mob.endPortalPose(f.root);
            var floor=mob.blockPosition().below();var old=f.level.getBlockState(floor);f.level.setBlock(floor,Blocks.MAGMA_BLOCK.defaultBlockState(),18);
            h.assertTrue(!FamilyJourney.inspect(f.owner,f.root).ready(),"Hazardous source rejected");f.level.setBlock(floor,old,18);
            var remote=new BlockPos(2000000,160,2000000);int loaded=f.level.getChunkSource().getLoadedChunksCount();var forced=Set.copyOf(f.level.getForcedChunks());
            h.assertTrue(FamilyJourney.inspect(f.owner,remote).absent() && !f.level.hasChunkAt(remote)
                    && loaded==f.level.getChunkSource().getLoadedChunksCount() && forced.equals(f.level.getForcedChunks()),"Ordinary family checks never load or force chunks");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_solo")
    public static void explicitSoloBypassesPartialFamilyAndAbsentFamilyKeepsOldEntry(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            var before=snapshots(t);var mob=t.f.friends.get(2);mob.moveTo(mob.position().add(30,0,0));
            t.f.owner.setShiftKeyDown(true);t.f.gateway().interact(t.f.owner);
            h.assertTrue(t.f.owner.serverLevel()==t.arrival.vale,"Shift click explicitly allows solo with partial family");
            for(var id:t.ids)h.assertTrue(t.f.level.getEntity(id)==t.live(id),"Solo leaves every companion at source");
            h.assertTrue(command(t.f.owner,"dudunka return")==1,"Solo emergency return succeeds");
            retained(h,t,before);t.f.owner.setShiftKeyDown(false);
            t.f.friends.forEach(c->c.moveTo(c.position().add(30,0,0)));t.f.gateway().interact(t.f.owner);
            h.assertTrue(t.f.owner.serverLevel()==t.arrival.vale,"No nearby family preserves implicit solo behavior");
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_no_space")
    public static void onePlayerLandingWithoutFamilySpaceRejectsBeforeMovingOrBookmarking(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            // A three-by-three safe island permits the player's hazard halo, but any
            // adjacent companion landing touches magma. No blocks are changed by travel.
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)t.arrival.vale.setBlock(new BlockPos(x,300,z),Blocks.MAGMA_BLOCK.defaultBlockState(),18);
            for(int x=6;x<=8;x++)for(int z=6;z<=8;z++)t.arrival.vale.setBlock(new BlockPos(x,300,z),Blocks.STONE.defaultBlockState(),18);
            var previous=new MushroomValeReturns.ReturnPoint(Level.NETHER.location(),new BlockPos(8,70,8),1,2);
            MushroomValeReturns.get(t.f.owner.server).remember(t.f.owner.getUUID(),previous);
            var before=snapshots(t);t.f.gateway().interact(t.f.owner);
            h.assertTrue(t.f.owner.serverLevel()==t.f.level && previous.equals(MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())),"No family space preserves player and old bookmark");
            retained(h,t,before);for(var id:t.ids)h.assertTrue(t.f.level.getEntity(id)==t.live(id),"No member moved during plan rejection");
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_natural_terrain")
    public static void familyArrivalFindsSeparateLandingsAcrossUnevenVegetatedTerrain(GameTestHelper h) {
        var t = new Trip(h);
        t.ready(h, () -> {
            var vale = t.arrival.vale;
            // Keep the player's deterministic first landing at (8,301,8), while
            // raising the surrounding natural ground beyond the old fixed dy search.
            for (int x=4;x<=12;x++) for (int z=4;z<=12;z++) {
                if (x==8 && z==8) continue;
                int top = 303 + ((x + z) & 1);
                for (int y=300;y<=top;y++) vale.setBlock(new BlockPos(x,y,z), Blocks.DIRT.defaultBlockState(),18);
            }
            // Harmless decoration in companion safety halos must not count as a hazard.
            vale.setBlock(new BlockPos(6,304,8), Blocks.OAK_LEAVES.defaultBlockState(),18);
            vale.setBlock(new BlockPos(10,304,8), Blocks.TALL_GRASS.defaultBlockState(),18);
            h.assertTrue(MushroomVale.safeLanding(vale,new BlockPos(7,304,7),t.f.friends.get(0)),
                    "Non-colliding leaves in the safety halo do not reject a sound landing");
            var before = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (int x=3;x<=13;x++) for (int z=3;z<=13;z++) for (int y=300;y<=305;y++) {
                var pos = new BlockPos(x,y,z); before.put(pos, vale.getBlockState(pos));
            }
            h.assertTrue(FamilyJourney.travel(t.f.owner,t.f.root,false,false)==MushroomVale.TravelResult.ENTERED,
                    "Group planner reaches uneven natural terrain without clearing it");
            h.assertTrue(MushroomVale.safeLanding(vale,t.f.owner.blockPosition(),t.f.owner), "Player remains on the original safe landing");
            for (var id : t.ids) {
                var mob=t.live(id);
                h.assertTrue(MushroomVale.safeLanding(vale,mob.blockPosition(),mob), "Companion has a safe landing on raised natural terrain");
            }
            before.forEach((pos,state)->h.assertTrue(vale.getBlockState(pos).equals(state),"Travel never modifies terrain at "+pos));
            t.unique(h,vale);
        });
    }

    private static void vetoCase(GameTestHelper h, boolean playerVeto, boolean rollbackVeto) {
        var t=new Trip(h);
        t.ready(h,()->{
            var old=snapshots(t);var positions=new HashMap<UUID,Vec3>();t.ids.forEach(id->positions.put(id,t.live(id).position()));
            var previous=new MushroomValeReturns.ReturnPoint(Level.NETHER.location(),new BlockPos(8,70,8),1,2);
            var returns=MushroomValeReturns.get(t.f.owner.server);returns.remember(t.f.owner.getUUID(),previous);
            Consumer<EntityTravelToDimensionEvent> veto=event->{
                boolean forward=event.getDimension().equals(MushroomVale.LEVEL);
                if(forward && (playerVeto ? event.getEntity()==t.f.owner : event.getEntity().getUUID().equals(t.ids.get(1))))event.setCanceled(true);
                if(!forward && rollbackVeto && event.getEntity().getUUID().equals(t.ids.get(0)))event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(veto);
            MushroomVale.TravelResult result;
            try {result=FamilyJourney.travel(t.f.owner,t.f.root,false,false);}
            finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(result==(rollbackVeto?MushroomVale.TravelResult.ROLLBACK_FAILED:MushroomVale.TravelResult.DENIED)
                    && t.f.owner.serverLevel()==t.f.level && previous.equals(returns.point(t.f.owner.getUUID())),"Veto keeps player and bookmark and reports rollback status");
            retained(h,t,old);
            if(rollbackVeto) {
                var stranded=t.live(t.ids.get(0));
                h.assertTrue(stranded.level()==t.arrival.vale && FamilyRegistry.get(t.f.owner.server).member(stranded.getUUID()).dimension().equals(MushroomVale.ID),"Incomplete rollback is indexed at actual location");
                t.f.owner.setOnGround(true);
                h.assertTrue(FamilyRecovery.move(t.f.owner,stranded),"Album recovery infrastructure can recover survivor without recreation");
            } else for(var id:t.ids)h.assertTrue(t.live(id).position().equals(positions.get(id)),"Rollback restores exact departure position");
            t.unique(h,t.f.level);
        });
    }
    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_member_veto")
    public static void secondCompanionForgeVetoRollsBackFirst(GameTestHelper h) {vetoCase(h,false,false);}
    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_player_veto")
    public static void playerForgeVetoRollsBackAllThree(GameTestHelper h) {vetoCase(h,true,false);}
    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_rollback_veto")
    public static void rollbackVetoReportsSurvivorAndAllowsExistingRecovery(GameTestHelper h) {vetoCase(h,false,true);}

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_two_players")
    public static void twoOwnersShareGatewayWithoutMovingEachOthersFamilyOrBookmarks(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            var second=t.f.player();var other=t.f.family(second,false);other.forEach(c->{c.commandStay(second,false);
                c.moveTo(t.f.root.getX()-3.5+c.kind.ordinal()*2,t.f.root.getY(),t.f.root.getZ()+1.5,0,0);});
            var gate=t.f.gateway();gate.interact(second);
            h.assertTrue(second.serverLevel()==t.f.level && !gate.awakened(second.getUUID()),"Visible active gateway grants no other owner access");
            // Persist the second owner's already earned unlock, as in an upgraded saved gateway.
            var tag=gate.saveWithFullMetadata();var row=new CompoundTag();row.putUUID("Owner",second.getUUID());tag.getList("AwakenedOwners",10).add(row);gate.load(tag);
            var ids=other.stream().map(Companion::getUUID).toList();t.enter(h);
            for(var id:ids)h.assertTrue(t.f.level.getEntity(id)!=null,"First journey never moves the other family");
            var first=MushroomValeReturns.get(second.server).point(t.f.owner.getUUID());gate.interact(second);
            h.assertTrue(second.serverLevel()==t.arrival.vale,"Second awakened owner enters independently");
            h.assertTrue(command(second,"dudunka return_family")==1,"Second family returns independently");
            h.assertTrue(first.equals(MushroomValeReturns.get(second.server).point(t.f.owner.getUUID())) && t.f.owner.serverLevel()==t.arrival.vale,"Other return never consumes first bookmark");
            for(var id:ids)h.assertTrue(t.f.level.getEntity(id) instanceof Companion && t.arrival.vale.getEntity(id)==null,"Other family has exactly one instance at home");
            h.assertTrue(command(t.f.owner,"dudunka return_family")==1,"First family still returns");t.unique(h,t.f.level);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_join_veto")
    public static void nativeDestinationInsertionFailureRestoresSameLiveSourceAndRollsBackGroup(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            var original=t.f.friends.get(1); var saved=snapshots(t);
            Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> veto=e->{
                if(e.getLevel()==t.arrival.vale && e.getEntity().getUUID().equals(original.getUUID()))e.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(veto);
            MushroomVale.TravelResult result;
            try { result=FamilyJourney.travel(t.f.owner,t.f.root,false,false); }
            finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(result==MushroomVale.TravelResult.DENIED && t.f.level.getEntity(original.getUUID())==original
                    && !original.isRemoved(),"Canceled destination insertion retains the same live source object");
            h.assertTrue(MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())==null,"Insertion failure never creates a bookmark");
            t.unique(h,t.f.level);retained(h,t,saved);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_return_veto")
    public static void groupReturnVetoRollsBackAllCompanionsAndRetainsBookmarkForRetry(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            t.enter(h);var saved=snapshots(t);var returns=MushroomValeReturns.get(t.f.owner.server);var bookmark=returns.point(t.f.owner.getUUID());
            Consumer<EntityTravelToDimensionEvent> veto=e->{if(e.getEntity()==t.f.owner)e.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(veto);
            try {h.assertTrue(command(t.f.owner,"dudunka return_family")==0,"Veto denies group return");}
            finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(t.f.owner.serverLevel()==t.arrival.vale && bookmark.equals(returns.point(t.f.owner.getUUID())),"Failed return preserves bookmark");
            t.unique(h,t.arrival.vale);retained(h,t,saved);
            h.assertTrue(command(t.f.owner,"dudunka return_family")==1,"Retry succeeds without a new ritual");t.unique(h,t.f.level);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_revalidation")
    public static void eligibilityChangedByForgeListenerIsRevalidatedBeforeNativeTransfer(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            var first=t.f.friends.get(0);
            Consumer<EntityTravelToDimensionEvent> mutate=e->{if(e.getEntity()==first)first.commandStay(t.f.owner,true);};
            MinecraftForge.EVENT_BUS.addListener(mutate);
            try {h.assertTrue(FamilyJourney.travel(t.f.owner,t.f.root,false,false)==MushroomVale.TravelResult.DENIED,"Listener's changed FOLLOW mode aborts movement");}
            finally {MinecraftForge.EVENT_BUS.unregister(mutate);}
            h.assertTrue(first.staying() && t.f.owner.serverLevel()==t.f.level,"Listener's explicit mode choice is respected");t.unique(h,t.f.level);
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_conflicts")
    public static void noncanonicalInstancesRegistryConflictsAndBorderRejectWithoutLoading(GameTestHelper h) {
        try(var t=new Trip(h)) {
            var real=t.f.friends.get(0);var fake=DudunkaMod.TYPES.get(real.kind).get().create(t.f.level);fake.load(real.saveWithoutId(new CompoundTag()));
            var candidates=new ArrayList<>(t.f.friends);candidates.set(0,fake);
            h.assertTrue(!FamilyJourney.evaluate(t.f.owner,t.f.root,candidates).ready(),"Unregistered same-UUID instance cannot substitute for canonical live entity");
            var data=real.saveWithoutId(new CompoundTag());data.putUUID("FamilyOwner",UUID.randomUUID());fake.load(data);FamilyRegistry.get(t.f.owner.server).observe(fake);
            h.assertTrue(!FamilyJourney.inspect(t.f.owner,t.f.root).ready(),"Conflicting registry ownership rejects canonical candidate");FamilyRegistry.get(t.f.owner.server).observe(real);
            var border=t.arrival.vale.getWorldBorder();var size=border.getSize();double x=border.getCenterX(),z=border.getCenterZ();
            try {border.setCenter(10000,10000);border.setSize(1);h.assertTrue(!MushroomVale.safeLanding(t.arrival.vale,new BlockPos(8,301,8),real),"Companion landing respects destination border");}
            finally {border.setCenter(x,z);border.setSize(size);}
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_cold")
    public static void familyTransferWorksIntoFullButNotEntityTickingColdChunk(GameTestHelper h) {
        var t=new Trip(h,false);
        var forced=Set.copyOf(t.arrival.vale.getForcedChunks());
        h.startSequence().thenWaitUntil(()->h.assertTrue(t.ids.stream().allMatch(id->t.f.level.getEntity(id) instanceof Companion),"Source fixture visibility"))
                .thenExecute(()->{
                    // Use an isolated, distant Vale chunk so parallel GameTests that
                    // prepare the shared spawn/arrival chunk cannot make it entity-ticking.
                    var coldChunk=t.arrival.vale.getChunk(32,32);
                    for(int x=512;x<=527;x++)for(int z=512;z<=527;z++) {
                        t.arrival.vale.setBlock(new BlockPos(x,300,z),Blocks.STONE.defaultBlockState(),18);
                        for(int y=301;y<=304;y++)t.arrival.vale.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
                    }
                    var chunk=t.arrival.vale.getChunkSource().getChunkNow(32,32);
                    h.assertTrue(chunk!=null && chunk.getFullStatus()==net.minecraft.server.level.FullChunkStatus.FULL,
                            "Cold destination blocks are available in a FULL chunk");
                    long coldId=new net.minecraft.world.level.ChunkPos(32,32).toLong();
                    boolean entityTicking=t.arrival.vale.areEntitiesLoaded(coldId);
                    h.assertTrue(!entityTicking,
                            "Fresh target chunk is FULL but not ENTITY_TICKING before family transfer");
                    var landing=new BlockPos(520,301,520);
                    java.util.function.Supplier<MushroomVale.TravelResult> retry=()->moveInto(t.f.owner,t.f.friends,
                            t.f.root,t.arrival.vale,landing,t.f.owner.getYRot(),t.f.owner.getXRot());
                    var result=moveInto(t.f.owner,t.f.friends,t.f.root,t.arrival.vale,landing,
                            t.f.owner.getYRot(),t.f.owner.getXRot(),retry);
                    h.assertTrue(result==MushroomVale.TravelResult.PREPARING && t.f.owner.serverLevel()==t.f.level,
                            "Cold family journey waits safely at source while ticket promotes its target");
                    h.assertTrue(forced.equals(t.arrival.vale.getForcedChunks()),"Journey adds no forced target chunks");
                }).thenWaitUntil(()->h.assertTrue(t.f.owner.serverLevel()==t.arrival.vale,
                        "Deferred family journey completes once the chunk reaches ENTITY_TICKING"))
                .thenExecute(()->{
                    try {t.unique(h,t.arrival.vale);h.assertTrue(forced.equals(t.arrival.vale.getForcedChunks()),
                            "Temporary journey ticket does not become a persistent forced chunk");}
                    finally {t.close();}
                }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_cold_emergency_return")
    public static void emergencySoloReturnCancelsPendingColdFamilyReturn(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            t.enter(h);
            var overworld=t.f.level;
            for(int x=512;x<=527;x++)for(int z=512;z<=527;z++) {
                overworld.setBlock(new BlockPos(x,300,z),Blocks.STONE.defaultBlockState(),18);
                for(int y=301;y<=304;y++)overworld.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
            }
            var chunk=overworld.getChunkSource().getChunkNow(32,32);
            long chunkId=new net.minecraft.world.level.ChunkPos(32,32).toLong();
            h.assertTrue(chunk!=null && chunk.getFullStatus()==net.minecraft.server.level.FullChunkStatus.FULL
                    && !overworld.areEntitiesLoaded(chunkId),"Emergency fixture uses a FULL, non-ticking return chunk");
            MushroomValeReturns.get(t.f.owner.server).remember(t.f.owner.getUUID(),
                    new MushroomValeReturns.ReturnPoint(Level.OVERWORLD.location(),new BlockPos(520,301,520),0,0));
            h.assertTrue(FamilyJourney.travel(t.f.owner,t.f.owner.blockPosition(),true,true)
                    ==MushroomVale.TravelResult.PREPARING,"Family return waits for its cold destination");
            h.assertTrue(MushroomVale.returnPlayer(t.f.owner)==MushroomVale.TravelResult.RETURNED
                    && t.f.owner.serverLevel()==overworld,"Emergency solo return cancels the pending group route and succeeds");
            for(var id:t.ids)h.assertTrue(t.live(id).level()==t.arrival.vale,
                    "Emergency solo return leaves companions in their current dimension");
            h.assertTrue(MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())==null,
                    "Emergency return consumes its bookmark only after successful player transfer");
            t.close();
        });
    }

    @GameTest(template="empty", timeoutTicks=1200, batch="isolated_journey_player_landing_change")
    public static void playerLandingChangedInForgeHookTriggersFullRollback(GameTestHelper h) {
        var t=new Trip(h);
        t.ready(h,()->{
            var saved=snapshots(t);var position=t.f.owner.position();
            // The flat fixture's first player landing is (1,301,1). This upper block
            // obstructs the standing player but leaves the shorter companion boxes clear.
            Consumer<EntityTravelToDimensionEvent> mutate=e->{
                if(e.getEntity()==t.f.owner && e.getDimension().equals(MushroomVale.LEVEL))
                    t.arrival.vale.setBlock(new BlockPos(1,302,1),Blocks.STONE.defaultBlockState(),18);
            };
            MinecraftForge.EVENT_BUS.addListener(mutate);
            try {h.assertTrue(FamilyJourney.travel(t.f.owner,t.f.root,false,false)==MushroomVale.TravelResult.DENIED,"Post-hook player collision triggers rollback");}
            finally {MinecraftForge.EVENT_BUS.unregister(mutate);}
            h.assertTrue(t.f.owner.serverLevel()==t.f.level && t.f.owner.position().equals(position)
                    && MushroomValeReturns.get(t.f.owner.server).point(t.f.owner.getUUID())==null,"Player rollback restores exact source and retains bookmark semantics");
            t.unique(h,t.f.level);retained(h,t,saved);
        });
    }
}
