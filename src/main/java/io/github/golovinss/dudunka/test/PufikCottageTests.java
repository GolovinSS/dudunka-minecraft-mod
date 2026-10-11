package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class PufikCottageTests {
    private static Runnable cleanup=()->{};
    private static void clean(ServerLevel level) { var action=cleanup; cleanup=()->{}; action.run(); }
    private record Fixture(ServerLevel level, BlockPos origin, MushroomValeDecorFeature.Blueprint blueprint) {}
    private static Fixture fixture(GameTestHelper h,int n) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var origin=new BlockPos(16008+n*128,160,16008);
        var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("dudunka","pink_meadows")));
        var chunks=new ArrayList<ChunkPos>();
        var forcedBefore=new HashSet<ChunkPos>();
        var biomesBefore=new HashMap<ChunkPos,Map<BlockPos,net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>>();
        var saved=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            var p=new ChunkPos((origin.getX()>>4)+x,(origin.getZ()>>4)+z);chunks.add(p);
            if(level.getForcedChunks().contains(p.toLong()))forcedBefore.add(p);
            level.setChunkForced(p.x,p.z,true);var chunk=level.getChunk(p.x,p.z);
            var old=new HashMap<BlockPos,net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>();
            for(int qx=p.x*4;qx<p.x*4+4;qx++)for(int qz=p.z*4;qz<p.z*4+4;qz++)
                for(int qy=level.getMinBuildHeight()>>2;qy<level.getMaxBuildHeight()>>2;qy++)old.put(new BlockPos(qx,qy,qz),chunk.getNoiseBiome(qx,qy,qz));
            biomesBefore.put(p,old);
            chunk.fillBiomesFromNoise((a,b,c,sampler)->biome,level.getChunkSource().randomState().sampler());
        }
        for(var p:BlockPos.betweenClosed(origin.offset(-12,-2,-12),origin.offset(12,10,12))) {
            saved.put(p.immutable(),level.getBlockState(p));
            level.setBlock(p,p.getY()<origin.getY()?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        var configured=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.fromNamespaceAndPath("dudunka","mushroom_vale/pufik_cottage"));
        var blueprint=(MushroomValeDecorFeature.Blueprint)configured.config();
        cleanup=()-> {
            level.getEntitiesOfClass(Pufik.class,new AABB(origin).inflate(24)).forEach(Entity::discard);
            saved.forEach((p,state)->level.setBlock(p,state,18));
            for(var p:chunks) {
                var old=biomesBefore.get(p);
                level.getChunk(p.x,p.z).fillBiomesFromNoise((a,b,c,sampler)->old.get(new BlockPos(a,b,c)),level.getChunkSource().randomState().sampler());
                level.setChunkForced(p.x,p.z,forcedBefore.contains(p));
            }
        };
        return new Fixture(level,origin,blueprint);
    }
    @GameTest(template="empty",timeoutTicks=160,batch="isolated_pufik_cottage_0")
    public static void enrollmentPersistenceRemovalAndWildIndependence(GameTestHelper h) throws Exception {
        var f=fixture(h,0);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin),"Cottage fixture ticks"))
        .thenExecute(()-> {
            h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.CLOCKWISE_90),"Pufik cottage fixture places without entity side effects");
            var data=WiseCottageResidency.get(level);
            h.assertTrue(WiseCottageResidency.matchesPufik(level,origin,Rotation.CLOCKWISE_90),"Previously generated Pink Meadows cottage fingerprint is recognized");
            var entry=data.activatePufik(level,origin,Rotation.CLOCKWISE_90);
            h.assertTrue(entry!=null,"Valid cottage enrolls");
            var resident=(Pufik)level.getEntity(entry.resident());
            h.assertTrue(resident!=null && resident.cottageOrigin().equals(origin) && resident.isPersistenceRequired(),"Resident identity, home and persistence are attached");
            for(int i=0;i<12;i++) h.assertTrue(data.activatePufik(level,origin,Rotation.CLOCKWISE_90).resident().equals(entry.resident()),"Enrollment is idempotent");
            net.minecraft.nbt.CompoundTag disk;
            try {
                disk = NbtIo.readCompressed(
                    level.getServer().getWorldPath(LevelResource.ROOT)
                        .resolve("data/dudunka_wise_cottages.dat").toFile()
                );
            } catch (java.io.IOException e) {
                throw new AssertionError("Failed to read Pufik cottage ledger", e);
            }
            h.assertTrue(WiseCottageResidency.load(disk.getCompound("data")).pufikCottage(origin).resident().equals(entry.resident()),"Flushed ledger reload preserves the resident UUID");
            h.assertTrue(level.getEntitiesOfClass(Pufik.class,new AABB(origin).inflate(12)).size()==1,"Only the designated resident exists in the fixture");
            var saved=resident.saveWithoutId(new CompoundTag()); resident.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(data.activatePufik(level,origin,Rotation.CLOCKWISE_90).resident().equals(entry.resident()) && level.getEntity(entry.resident())==null,"Unloaded or removed residents are never recreated");
            var restored=DudunkaMod.PUFIK.get().create(level);restored.load(saved);
            h.assertTrue(restored.getUUID().equals(entry.resident()) && restored.cottageOrigin().equals(origin) && restored.isPersistenceRequired(),"Save reload preserves cottage UUID and association");restored.discard();
            var wild=DudunkaMod.PUFIK.get().create(level);h.assertTrue(wild.removeWhenFarAway(100000) && wild.cottageOrigin()==null,"Wild Pufik retains ordinary despawn and no cottage association");wild.discard();
            h.assertTrue(WiseCottageResidency.load(new CompoundTag()).pufikCottage(origin)==null,"Old saves without a Pufik ledger load safely");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=220,batch="isolated_pufik_cottage_1")
    public static void concurrentActivationIsUnique(GameTestHelper h) {
        var f=fixture(h,1);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin),"Cottage fixture ticks"))
        .thenExecute(()-> {
            h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.NONE),"Concurrent cottage fixture places");
            var first=ValeFireflyPopulationTests.observer(level);var second=ValeFireflyPopulationTests.observer(level);
            first.moveTo(origin.getX()+8.5,origin.getY(),origin.getZ(),0,0);second.moveTo(origin.getX()+9.5,origin.getY(),origin.getZ(),0,0);
            level.addNewPlayer(first);level.addNewPlayer(second);var prior=cleanup;cleanup=()->{first.discard();second.discard();prior.run();};
            var tasks=new ArrayList<java.util.concurrent.CompletableFuture<WiseCottageResidency.Cottage>>();
            for(int i=0;i<12;i++) tasks.add(java.util.concurrent.CompletableFuture.supplyAsync(()->WiseCottageResidency.get(level).activatePufik(level,origin,Rotation.NONE),level.getServer()));
            h.runAfterDelay(2,()-> {
                var entry=WiseCottageResidency.get(level).pufikCottage(origin);
                h.assertTrue(entry!=null && tasks.stream().allMatch(t->t.isDone() && t.join().resident().equals(entry.resident())),"Concurrent server requests share one durable UUID");
                h.assertTrue(level.getEntitiesOfClass(Pufik.class,new AABB(origin).inflate(12)).size()==1,"Concurrent activation creates exactly one resident");h.succeed();
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=1800,batch="isolated_pufik_cottage_2")
    public static void residentWalksOutAndReturnsWithoutTeleporting(GameTestHelper h) {
        movement(h, Rotation.NONE, 2);
    }
    @GameTest(template="empty",timeoutTicks=1800,batch="isolated_pufik_cottage_east")
    public static void residentWalksEastDoorAndReturns(GameTestHelper h) { movement(h,Rotation.CLOCKWISE_90,4); }
    @GameTest(template="empty",timeoutTicks=1800,batch="isolated_pufik_cottage_south")
    public static void residentWalksSouthDoorAndReturns(GameTestHelper h) { movement(h,Rotation.CLOCKWISE_180,5); }
    @GameTest(template="empty",timeoutTicks=1800,batch="isolated_pufik_cottage_west")
    public static void residentWalksWestDoorAndReturns(GameTestHelper h) { movement(h,Rotation.COUNTERCLOCKWISE_90,6); }
    private static void movement(GameTestHelper h,Rotation rotation,int slot) {
        var f=fixture(h,slot);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin),"Cottage fixture ticks"))
        .thenExecute(()-> {
            h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),rotation),"Navigation fixture places");
            var entry=WiseCottageResidency.get(level).activatePufik(level,origin,rotation);h.assertTrue(entry!=null,"Navigation cottage enrolls");
            var pufik=(Pufik)level.getEntity(entry.resident());pufik.getRandom().setSeed(3304);
            var previous=cleanup;cleanup=()->{pufik.discard();previous.run();};
            boolean[] visited={false,false};
            var outward=rotation.rotate(net.minecraft.core.Direction.NORTH);var last=new net.minecraft.world.phys.Vec3[]{pufik.position()};
            h.onEachTick(()-> {
                h.assertTrue(pufik.isAlive() && !pufik.isInWaterOrBubble() && !pufik.isOnFire(),"Resident remains safe");
                h.assertTrue(pufik.position().distanceToSqr(last[0])<.3,"Resident has no teleport movement");last[0]=pufik.position();
                h.assertTrue(pufik.blockPosition().distSqr(entry.home())<=65,"Excursion remains in the cottage area");
                if((pufik.getX()-origin.getX()-.5)*outward.getStepX()+(pufik.getZ()-origin.getZ()-.5)*outward.getStepZ()>4) visited[0]=true;
                if(visited[0] && pufik.blockPosition().distSqr(entry.home())<=3) visited[1]=true;
            });
            h.runAfterDelay(1700,()-> { h.assertTrue(visited[0] && visited[1],"Pufik walks through the door, explores outside and returns home: "+Arrays.toString(visited)); h.succeed(); });
        });
    }
    @GameTest(template="empty",timeoutTicks=500,batch="isolated_pufik_cottage_3")
    public static void chunkReloadKeepsOriginalResident(GameTestHelper h) {
        var f=fixture(h,3);var level=f.level();var origin=f.origin();
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin),"Cottage fixture ticks"))
        .thenExecute(()-> {
            h.assertTrue(MushroomValeDecorFeature.placeBlueprint(level,origin,f.blueprint(),Rotation.NONE),"Reload fixture places");
            var entry=WiseCottageResidency.get(level).activatePufik(level,origin,Rotation.NONE);h.assertTrue(entry!=null,"Reload cottage enrolls");
            var resident=(Pufik)level.getEntity(entry.resident());resident.setNoAi(true);
            h.runAfterDelay(50,()-> {
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,false);
                h.startSequence().thenWaitUntil(()->h.assertTrue(!level.hasChunkAt(origin) && level.getEntity(entry.resident())==null,"Entity and cottage chunk unload"))
                .thenExecute(()-> {
                    var known=WiseCottageResidency.get(level).activatePufik(level,origin,Rotation.NONE);
                    h.assertTrue(known.resident().equals(entry.resident()) && !level.hasChunkAt(origin),"Unloaded inspection returns ledger UUID without loading chunks");
                    level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);
                })
                .thenWaitUntil(()->h.assertTrue(level.getEntity(entry.resident()) instanceof Pufik,"Vanilla chunk storage reloads the original UUID"))
                .thenExecute(()-> { var loaded=(Pufik)level.getEntity(entry.resident());h.assertTrue(loaded.cottageOrigin().equals(origin) && loaded.isPersistenceRequired(),"Reloaded Pufik retains cottage association");h.succeed(); });
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void wiseLedgerBackwardCompatibility(GameTestHelper h) {
        var origin=h.absolutePos(BlockPos.ZERO);var id=UUID.randomUUID();var tag=new CompoundTag();var rows=new net.minecraft.nbt.ListTag();var row=new CompoundTag();row.putLong("Origin",origin.asLong());row.putInt("Rotation",0);row.putUUID("Resident",id);rows.add(row);tag.put("Cottages",rows);
        var loaded=WiseCottageResidency.load(tag);
        h.assertTrue(loaded.cottage(origin)!=null && loaded.cottage(origin).resident().equals(id) && loaded.pufikCottage(origin)==null,"Legacy Wise-only ledger remains readable");h.succeed();
    }
    @AfterBatch(batch="isolated_pufik_cottage_east") public static void cleanupEast(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_south") public static void cleanupSouth(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_west") public static void cleanupWest(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_0") public static void cleanup0(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_1") public static void cleanup1(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_2") public static void cleanup2(ServerLevel level) { clean(level); }
    @AfterBatch(batch="isolated_pufik_cottage_3") public static void cleanup3(ServerLevel level) { clean(level); }
}
