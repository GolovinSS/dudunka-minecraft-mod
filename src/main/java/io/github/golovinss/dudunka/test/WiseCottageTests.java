package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class WiseCottageTests {
    private static Runnable cleanup = () -> {};
    public static void after(ServerLevel level) {
        var action=cleanup;cleanup=()->{};action.run();
    }
    private record Fixture(ServerLevel level, BlockPos origin, MushroomValeDecorFeature.Blueprint blueprint) {}
    private static Fixture fixture(GameTestHelper h, int number) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var origin=new BlockPos(12008+number*128,160,12008);
        var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("dudunka","memory_forest")));
        var chunks=new ArrayList<ChunkPos>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            var pos=new ChunkPos((origin.getX()>>4)+x,(origin.getZ()>>4)+z);chunks.add(pos);
            level.setChunkForced(pos.x,pos.z,true); // Test fixture only; production never requests tickets.
            level.getChunk(pos.x,pos.z).fillBiomesFromNoise((a,b,c,sampler)->biome,level.getChunkSource().randomState().sampler());
        }
        for(var p:BlockPos.betweenClosed(origin.offset(-12,-2,-12),origin.offset(12,10,12)))
            level.setBlock(p,p.getY()<origin.getY()?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        var configured=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(
                ResourceLocation.fromNamespaceAndPath("dudunka","mushroom_vale/wise_fly_agaric_cottage"));
        var blueprint=(MushroomValeDecorFeature.Blueprint)configured.config();
        cleanup=()-> {
            level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(24)).forEach(Entity::discard);
            for(var pos:chunks)level.setChunkForced(pos.x,pos.z,false);
        };
        return new Fixture(level,origin,blueprint);
    }
    @GameTest(template="empty",timeoutTicks=100,batch="isolated_wise_cottage_0")
    public static void recognitionUniquenessAndDurableIssuance(GameTestHelper h) throws Exception {
        var f=fixture(h,0);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(new ChunkPos(origin).toLong())
                && level.isPositionEntityTicking(origin),"Fixture entity section must be ticking before activation"))
            .thenExecute(()-> {
                try {
                h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.CLOCKWISE_90),"Existing blueprint places without residency side effects");
                h.assertTrue(level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(12)).isEmpty(),"Placement/worldgen creates no entities");
                var data=WiseCottageResidency.get(level);
                h.assertTrue(!WiseCottageResidency.matches(level,origin,Rotation.NONE),"Rotation-specific structural recognition");
                var first=data.activate(level,origin,Rotation.CLOCKWISE_90);
                h.assertTrue(first!=null,"Intact previously generated furnished cottage recognized");
                var elder=(WiseFlyAgaric)level.getEntity(first.resident());
                h.assertTrue(elder!=null && elder.isPersistenceRequired() && elder.cottageHome().equals(first.home()),"Resident created once at home with persistent association");
                for(int i=0;i<20;i++)h.assertTrue(data.activate(level,origin,Rotation.CLOCKWISE_90).resident().equals(first.resident()),"Repeated visitor activation returns same UUID");
                h.assertTrue(level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(12)).size()==1,"At most one designated resident");
                var disk=NbtIo.readCompressed(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data/dudunka_wise_cottages.dat").toFile());
                var restart=WiseCottageResidency.load(disk.getCompound("data"));
                var tag=elder.saveWithoutId(new CompoundTag());
                elder.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                restart.activate(level,origin,Rotation.CLOCKWISE_90);
                h.assertTrue(level.getEntity(first.resident())==null,"Unloaded entity never causes replacement, including a fresh ledger loaded from disk");
                var restored=DudunkaMod.WISE_FLY_AGARIC.get().create(level);restored.load(tag);level.addFreshEntity(restored);
                h.assertTrue(restored.getUUID().equals(first.resident()) && restored.cottageOrigin().equals(origin)
                        && restored.cottageRotation()==Rotation.CLOCKWISE_90 && restored.isPersistenceRequired(),"Entity NBT and ledger reload preserve identity/home/rotation/persistence");
                restored.discard();restart.activate(level,origin,Rotation.CLOCKWISE_90);
                h.assertTrue(level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(12)).isEmpty(),"Deliberate removal has no automatic replacement");
                // Interrupted issuance: saved record exists but there is no entity NBT to restore.
                h.assertTrue(WiseCottageResidency.load(disk.getCompound("data")).activate(level,origin,Rotation.CLOCKWISE_90).resident().equals(first.resident()),"Interrupted issuance fails closed");
                h.succeed();
                } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            });
    }
    @GameTest(template="empty",timeoutTicks=250,batch="isolated_wise_cottage_1")
    public static void nearbyMultiplayerActivation(GameTestHelper h) {
        var f=fixture(h,1);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(new ChunkPos(origin).toLong())
                && level.isPositionEntityTicking(origin),"Fixture entity section must be ticking before activation"))
            .thenExecute(()-> {
                h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.NONE),"Legacy unmarked cottage fixture placed");
                var a=ValeFireflyPopulationTests.observer(level);var b=ValeFireflyPopulationTests.observer(level);
                a.moveTo(origin.getX()+9.5,origin.getY(),origin.getZ(),0,0);
                b.moveTo(origin.getX()+10.5,origin.getY(),origin.getZ(),0,0);
                level.addNewPlayer(a);level.addNewPlayer(b);
                var previous=cleanup;cleanup=()->{a.discard();b.discard();previous.run();};
                h.startSequence().thenWaitUntil(()->h.assertTrue(WiseCottageResidency.get(level).cottage(origin)!=null,
                        "Actual nearby-player discovery enrolls the unmarked cottage within its nine-slot scan"))
            .thenExecute(()-> {
                        var requests=new ArrayList<java.util.concurrent.CompletableFuture<WiseCottageResidency.Cottage>>();
                        for(int i=0;i<16;i++)requests.add(java.util.concurrent.CompletableFuture.supplyAsync(
                                ()->WiseCottageResidency.get(level).activate(level,origin,Rotation.NONE),level.getServer()));
                        h.runAfterDelay(2,()-> {
                            var entry=WiseCottageResidency.get(level).cottage(origin);
                            h.assertTrue(requests.stream().allMatch(r->r.isDone() && r.join().resident().equals(entry.resident())),"Concurrent visitor requests and automatic discovery agree on one UUID");
                            h.assertTrue(level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(16)).size()==1,"Concurrent activation stays unique");
                            h.succeed();
                        });
            });
            });
    }
    @GameTest(template="empty",timeoutTicks=120,batch="isolated_wise_cottage_2")
    public static void existingWorldAndUnloadedBoundaries(GameTestHelper h) {
        var f=fixture(h,2);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(new ChunkPos(origin).toLong())
                && level.isPositionEntityTicking(origin),"Fixture entity section must be ticking before activation"))
            .thenExecute(()-> {
                h.assertTrue(WiseCottageResidency.load(new CompoundTag()).cottage(origin)==null,"Old saves without residency data load empty");
                h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.COUNTERCLOCKWISE_90),"Unmarked existing cottage placed");
                var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
                for(var p:BlockPos.betweenClosed(origin.offset(-7,0,-7),origin.offset(7,9,7)))before.put(p.immutable(),level.getBlockState(p));
                var entry=WiseCottageResidency.get(level).activate(level,origin,Rotation.COUNTERCLOCKWISE_90);
                h.assertTrue(entry!=null && before.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue())),"Migration preserves all cottage blocks");
                var far=new BlockPos(20000000,160,20000000);
                h.assertTrue(!WiseCottageResidency.loadedSquare(level,far,16) && !WiseCottageResidency.matches(level,far,Rotation.NONE)
                        && WiseCottageResidency.get(level).activate(level,far,Rotation.NONE)==null && !level.hasChunkAt(far),"Unloaded candidates refused without loading chunks");
                level.setBlock(origin,Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!WiseCottageResidency.matches(level,origin,Rotation.COUNTERCLOCKWISE_90),"Damaged shell/floor does not enroll");
                h.assertTrue(WiseCottageResidency.get(h.getLevel()).activate(h.getLevel(),h.absolutePos(BlockPos.ZERO),Rotation.NONE)==null,"Wrong dimension refused");
                h.succeed();
            });
    }
    @GameTest(template="empty",timeoutTicks=2200,batch="isolated_wise_cottage_3")
    public static void liveCottageNavigationAndHomePauses(GameTestHelper h) { liveNavigation(h,Rotation.NONE,3); }
    @GameTest(template="empty",timeoutTicks=2200,batch="isolated_wise_cottage_5")
    public static void liveEastNavigation(GameTestHelper h) { liveNavigation(h,Rotation.CLOCKWISE_90,5); }
    @GameTest(template="empty",timeoutTicks=2200,batch="isolated_wise_cottage_6")
    public static void liveSouthNavigation(GameTestHelper h) { liveNavigation(h,Rotation.CLOCKWISE_180,6); }
    @GameTest(template="empty",timeoutTicks=2200,batch="isolated_wise_cottage_7")
    public static void liveWestNavigation(GameTestHelper h) { liveNavigation(h,Rotation.COUNTERCLOCKWISE_90,7); }
    private static void liveNavigation(GameTestHelper h, Rotation rotation, int number) {
        var f=fixture(h,number);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(new ChunkPos(origin).toLong())
                && level.isPositionEntityTicking(origin),"Fixture entity section must be ticking before activation"))
            .thenExecute(()-> {
                h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),rotation),"Actual closed-door cottage places");
                var entry=WiseCottageResidency.get(level).activate(level,origin,rotation);
                h.assertTrue(entry!=null,"Cottage activates");
                var elder=(WiseFlyAgaric)level.getEntity(entry.resident());elder.getRandom().setSeed(3401);
                var previous=cleanup;cleanup=()->{elder.discard();previous.run();};
                // A nearby pond, damaging ground and a cliff must never be chosen as destinations.
                for(int z=-10;z<=-6;z++) {
                    level.setBlock(origin.offset(new BlockPos(-5,-1,z).rotate(rotation)),Blocks.WATER.defaultBlockState(),18);
                    level.setBlock(origin.offset(new BlockPos(5,-1,z).rotate(rotation)),Blocks.MAGMA_BLOCK.defaultBlockState(),18);
                    level.setBlock(origin.offset(new BlockPos(6,-1,z).rotate(rotation)),Blocks.AIR.defaultBlockState(),18);
                    level.setBlock(origin.offset(new BlockPos(6,-2,z).rotate(rotation)),Blocks.AIR.defaultBlockState(),18);
                }
                boolean[] seen={false,false,false};var last=new net.minecraft.world.phys.Vec3[]{elder.position()};
                h.onEachTick(()-> {
                    h.assertTrue(elder.isAlive() && !elder.isInWaterOrBubble() && !elder.isOnFire() && elder.getHealth()==12,"Resident avoids pond, cliff and hazards");
                    h.assertTrue(elder.position().distanceToSqr(last[0])<.3,"Natural walking has no teleport jumps");last[0]=elder.position();
                    h.assertTrue(elder.blockPosition().distSqr(entry.home())<=101,"Excursions remain local");
                    var outward=new BlockPos(0,0,-1).rotate(rotation);
                    if((elder.getX()-origin.getX()-.5)*outward.getX()+(elder.getZ()-origin.getZ()-.5)*outward.getZ()>5.5)seen[0]=true;
                    if(seen[0] && elder.blockPosition().distSqr(entry.home())<=4)seen[1]=true;
                    if(elder.blockPosition().distSqr(entry.home())<=4 && (elder.emote()==WiseFlyAgaric.READ || elder.emote()==WiseFlyAgaric.THINK) && elder.emoteStrength(0)>0)seen[2]=true;
                });
                h.runAfterDelay(2100,()-> {
                    h.assertTrue(seen[0] && seen[1] && seen[2],"Actual cottage route exits door/porch, returns and thinks/reads at home: "+rotation+" "+Arrays.toString(seen));
                    h.succeed();
                });
            });
    }
    @GameTest(template="empty",timeoutTicks=500,batch="isolated_wise_cottage_4")
    public static void actualChunkUnloadAndDiskReload(GameTestHelper h) {
        var f=fixture(h,4);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(new ChunkPos(origin).toLong())
                && level.isPositionEntityTicking(origin),"Fixture entity section must be ticking before activation"))
            .thenExecute(()-> {
                h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.CLOCKWISE_180),"Unload fixture places");
                var entry=WiseCottageResidency.get(level).activate(level,origin,Rotation.CLOCKWISE_180);
                h.assertTrue(entry!=null,"Unload fixture activates");
                var elder=(WiseFlyAgaric)level.getEntity(entry.resident());elder.setNoAi(true);
                h.runAfterDelay(50,()-> {
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,false);
                    h.startSequence().thenWaitUntil(()->h.assertTrue(!level.hasChunkAt(origin) && level.getEntity(entry.resident())==null,"Actual entity and cottage chunk must unload"))
                        .thenExecute(()-> {
                            var known=WiseCottageResidency.get(level).activate(level,origin,Rotation.CLOCKWISE_180);
                            h.assertTrue(known.resident().equals(entry.resident()) && !level.hasChunkAt(origin),"Unloaded activation uses ledger only and does not reload chunks");
                            level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);
                        })
                        .thenWaitUntil(()->h.assertTrue(level.getEntity(entry.resident()) instanceof WiseFlyAgaric,"Vanilla entity storage reloads original UUID"))
                        .thenExecute(()-> {
                            var restored=(WiseFlyAgaric)level.getEntity(entry.resident());
                            h.assertTrue(restored.cottageOrigin().equals(origin) && restored.cottageRotation()==Rotation.CLOCKWISE_180 && restored.isPersistenceRequired(),"Real chunk disk reload preserves residency");
                            for(int i=0;i<10;i++)WiseCottageResidency.get(level).activate(level,origin,Rotation.CLOCKWISE_180);
                            h.assertTrue(level.getEntitiesOfClass(WiseFlyAgaric.class,new AABB(origin).inflate(12)).size()==1,"Reload followed by repeated visits never duplicates");
                        }).thenSucceed();
                });
            });
    }
    @AfterBatch(batch="isolated_wise_cottage_0") public static void cleanup0(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_1") public static void cleanup1(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_2") public static void cleanup2(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_3") public static void cleanup3(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_5") public static void cleanup5(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_6") public static void cleanup6(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_7") public static void cleanup7(ServerLevel level) { after(level); }
    @AfterBatch(batch="isolated_wise_cottage_4") public static void cleanup4(ServerLevel level) { after(level); }

}
