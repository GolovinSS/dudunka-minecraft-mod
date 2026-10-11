package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class ValeFireflyTests {
    private static Runnable flightCleanup=()->{};
    public static void cleanupFlightFixture(){var action=flightCleanup;flightCleanup=()->{};action.run();}
    @AfterBatch(batch="isolated_firefly_takeoff") public static void afterTakeoff(net.minecraft.server.level.ServerLevel level){cleanupFlightFixture();}
    @GameTest(template="empty")
    public static void registrationVariantsSaveAndCompatibility(GameTestHelper h) {
        var type=DudunkaMod.VALE_FIREFLY.get();
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals("dudunka:vale_firefly")
                && type.getCategory()==MobCategory.AMBIENT && type.getWidth()==.2f && type.getHeight()==.2f,
                "Independent small ambient registration");
        h.assertTrue(SpawnPlacements.getPlacementType(type)==SpawnPlacements.Type.NO_RESTRICTIONS, "Flying spawn placement");
        for(int variant=0;variant<3;variant++) {
            var entity=type.create(h.getLevel());var requested=new CompoundTag();requested.putInt("ValeVariant",variant);entity.load(requested);
            entity.finalizeSpawn(h.getLevel(),h.getLevel().getCurrentDifficultyAt(h.absolutePos(BlockPos.ZERO)),MobSpawnType.COMMAND,null,requested);
            h.assertTrue(entity.variant()==variant,"Explicit summon NBT survives command initialization");
            entity.moveTo(h.absolutePos(new BlockPos(2,3,2)),0,0);
            entity.setCustomName(Component.literal("Firefly"));
            var tag=entity.saveWithoutId(new CompoundTag());
            var restored=type.create(h.getLevel()); restored.load(tag);
            h.assertTrue(restored.variant()==variant && restored.getUUID().equals(entity.getUUID())
                    && restored.hasCustomName() && !restored.removeWhenFarAway(100000) && restored.isNoGravity(),
                    "Variant/name/UUID/gravity survive ordinary entity NBT round trip");
            h.assertTrue(!tag.contains("FamilyOwner") && !tag.contains("GrowthTicks") && !tag.contains("Trust"), "No family state");
            entity.discard(); restored.discard();
        }
        var ordinary=type.create(h.getLevel());
        h.assertTrue(!ordinary.isPersistenceRequired() && ordinary.removeWhenFarAway(100000),"Ordinary ambient despawning");
        ordinary.setVariant(99); h.assertTrue(ordinary.variant()==2,"Invalid variants clamp");
        ordinary.load(new CompoundTag()); h.assertTrue(ordinary.variant()==0,"Missing variant defaults golden");
        h.assertTrue(Kind.values().length==3 && DudunkaMod.TYPES.size()==3
                && !DudunkaMod.TYPES.containsValue(DudunkaMod.VALE_FIREFLY), "Family identities unchanged");
        h.assertTrue(ordinary.getAttribute(Attributes.ATTACK_DAMAGE)==null && ordinary.getTarget()==null && !ordinary.canPickUpLoot(),"No attack attribute/target or item pickup");
        ordinary.setTarget(h.makeMockPlayer());
        h.assertTrue(ordinary.getTarget()==null && !ordinary.doHurtTarget(h.makeMockPlayer()),"Cannot acquire targets or attack");
        ordinary.discard(); h.succeed();
    }

    @GameTest(template="empty")
    public static void mixedGroupPalettesAcrossSeeds(GameTestHelper h) {
        for(String biome:new String[]{"glowing_mushroom_forest","pink_meadows","memory_forest"}) {
            int[] colors=new int[3];
            for(long seed=0;seed<256;seed++) {
                var group=new ValeFirefly.FireflySpawnGroup(biome);
                int size=3+(int)(seed%3); boolean[] seen=new boolean[3];
                for(int member=0;member<size;member++) {
                    int color=group.next(net.minecraft.util.RandomSource.create(seed*31+member));
                    colors[color]++; seen[color]=true;
                }
                h.assertTrue(group.spawnedCount()==size && size>=3 && size<=5
                        && (seen[0]?1:0)+(seen[1]?1:0)+(seen[2]?1:0)>=2,
                        "3–5 member group has at least two colors across seeds: "+biome+"/"+seed);
            }
            int total=colors[0]+colors[1]+colors[2];
            double golden=(double)colors[ValeFirefly.GOLDEN]/total;
            double turquoise=(double)colors[ValeFirefly.TURQUOISE]/total;
            double violet=(double)colors[ValeFirefly.VIOLET]/total;
            boolean paletteMatches=switch (biome) {
                case "glowing_mushroom_forest" -> golden>.40 && golden<.70 && turquoise>.20 && violet>.07;
                case "pink_meadows" -> golden>.55 && golden<.82 && turquoise>.10 && violet>.05;
                default -> violet>.55 && violet<.82 && turquoise>.10 && golden>.05;
            };
            h.assertTrue(paletteMatches, "Weighted color shares include the biome primary and accents: "+biome
                    +" [gold="+golden+", turquoise="+turquoise+", violet="+violet+"]");
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="isolated_firefly_cap")
    public static void localDespawnHonorsNamesPersistenceForgeAndSharedCap(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var base=new BlockPos(2000,130,2000);var chunk=level.getChunk(base.getX()>>4,base.getZ()>>4);
        var player=ValeFireflyPopulationTests.observer(level);player.moveTo(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);level.addNewPlayer(player);
        var ordinary=DudunkaMod.VALE_FIREFLY.get().create(level);ordinary.moveTo(player.getX()+ValeFirefly.NATURAL_PLAYER_RANGE+1,player.getY(),player.getZ(),0,0);
        var named=DudunkaMod.VALE_FIREFLY.get().create(level);named.moveTo(ordinary.position());named.setCustomName(Component.literal("Kept"));
        var persistent=DudunkaMod.VALE_FIREFLY.get().create(level);persistent.moveTo(ordinary.position());persistent.setPersistenceRequired();
        var vetoed=DudunkaMod.VALE_FIREFLY.get().create(level);vetoed.moveTo(ordinary.position());
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.AllowDespawn> veto=event->{
            if(event.getEntity()==vetoed)event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        var bats=new java.util.ArrayList<net.minecraft.world.entity.ambient.Bat>();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            ordinary.checkDespawn();named.checkDespawn();persistent.checkDespawn();vetoed.checkDespawn();
            h.assertTrue(ordinary.isRemoved() && named.isAlive() && persistent.isAlive() && vetoed.isAlive(),"Local turnover preserves named/persistent flies and Forge despawn vetoes");
            for(int i=0;i<15;i++) {
                var bat=net.minecraft.world.entity.EntityType.BAT.create(level);bat.moveTo(base.getX()+2.5,base.getY()+2,base.getZ()+2.5,0,0);
                bat.setNoAi(true);level.addFreshEntity(bat);bats.add(bat);
            }
            var state=net.minecraft.world.level.NaturalSpawner.createState(289,level.getAllEntities(),(key,consumer)->{
                var loaded=level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key));
                if(loaded!=null)consumer.accept(loaded);
            },new net.minecraft.world.level.LocalMobCapCalculator(level.getChunkSource().chunkMap));
            h.assertTrue(MobCategory.AMBIENT.getMaxInstancesPerChunk()==15 && state.getMobCategoryCounts().getInt(MobCategory.AMBIENT)>=15,"Bats still consume the unchanged shared ambient cap");
            for(int i=0;i<100;i++)net.minecraft.world.level.NaturalSpawner.spawnForChunk(level,chunk,state,true,false,false);
            h.assertTrue(level.getEntitiesOfClass(ValeFirefly.class,new AABB(base).inflate(16)).isEmpty(),"Completed vanilla spawning cannot bypass a full ambient cap");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);player.discard();bats.forEach(Entity::discard);
            ordinary.discard();named.discard();persistent.discard();vetoed.discard();
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100, batch="isolated_firefly_spawn")
    public static void naturalSpawnsRequireValeBiomeNightAndDrySurface(GameTestHelper h) {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var registry=vale.registryAccess().registryOrThrow(Registries.BIOME);
        for(String name:new String[]{"glowing_mushroom_forest","pink_meadows","memory_forest"}) {
            var biome=registry.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dudunka",name));
            h.assertTrue(biome.getMobSettings().getMobs(MobCategory.AMBIENT).unwrap().stream().anyMatch(e ->
                    e.type==DudunkaMod.VALE_FIREFLY.get() && e.minCount==3 && e.maxCount==5 && e.getWeight().asInt()==2), "Conservative biome spawn entry: "+name);
        }
        h.assertTrue(!ValeFirefly.habitat(h.getLevel(),h.absolutePos(BlockPos.ZERO)),"Overworld refused");
        var clock=h.getLevel().getServer().overworld();
        long time=clock.getDayTime();
        // Explicit test fixture chunk; production flight/spawning never requests chunks.
        BlockPos pos=new BlockPos(1000,171,1000); vale.getChunk(62,62);
        var saved=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var observer=ValeFireflyPopulationTests.observer(vale);observer.moveTo(pos.getX()+32.5,pos.getY(),pos.getZ()+.5,0,0);vale.addNewPlayer(observer);
        try {
            for(var p:BlockPos.betweenClosed(pos.offset(-1,-2,-1),pos.offset(1,6,1))) {
                saved.put(p.immutable(),vale.getBlockState(p));
                vale.setBlock(p,p.getY()==169?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            }
            clock.setDayTime(18000);
            h.assertTrue(ValeFirefly.habitat(vale,pos) && SpawnPlacements.checkSpawnRules(DudunkaMod.VALE_FIREFLY.get(),vale,
                    MobSpawnType.NATURAL,pos,vale.random),"Loaded dry Vale surface at night permits spawn: biome="+vale.getBiome(pos).unwrapKey()
                            +", habitat="+ValeFirefly.habitat(vale,pos)+", time="+vale.getDayTime()
                            +", surface="+vale.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())
                            +", volume="+ValeFirefly.safeVolume(vale,DudunkaMod.VALE_FIREFLY.get().getAABB(pos.getX()+.5,pos.getY(),pos.getZ()+.5).inflate(.15)));
            observer.moveTo(pos.getX()+ValeFirefly.NATURAL_PLAYER_RANGE+1.5,pos.getY(),pos.getZ()+.5,0,0);
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Natural spawning stays within the firefly's local player range");
            observer.moveTo(pos.getX()+32.5,pos.getY(),pos.getZ()+.5,0,0);
            vale.setBlock(pos.above(5),Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(),18);
            h.assertTrue(ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Safe understory below a giant mushroom canopy can spawn");
            vale.setBlock(pos.above(5),Blocks.AIR.defaultBlockState(),18);
            clock.setDayTime(6000);
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Daytime refused");
            clock.setDayTime(18000);
            vale.setBlock(pos,Blocks.WATER.defaultBlockState(),18);
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Water refused");
            vale.setBlock(pos,Blocks.LAVA.defaultBlockState(),18);
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Lava refused");
            vale.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos,vale.random),"Solid obstruction refused");
            h.assertTrue(!ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos.below(3),vale.random)
                    && !ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,MobSpawnType.NATURAL,pos.above(12),vale.random),"Caves/high air refused");
        } finally { observer.discard();saved.forEach((p,s)->vale.setBlock(p,s,18)); clock.setDayTime(time); }
        h.assertTrue(!ValeFirefly.activeTime(11999) && ValeFirefly.activeTime(12000) && ValeFirefly.activeTime(22999)
                && !ValeFirefly.activeTime(23000),"Evening/night boundary");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100, batch="isolated_firefly_frequency")
    public static void naturalSpawnOpportunitiesAcrossSeeds(GameTestHelper h) {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var clock=h.getLevel().getServer().overworld();
        long time=clock.getDayTime();
        BlockPos pos=new BlockPos(1000,171,1000);
        vale.getChunk(62,62);
        var saved=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try {
            for(var p:BlockPos.betweenClosed(pos.offset(-1,-2,-1),pos.offset(1,6,1))) {
                saved.put(p.immutable(),vale.getBlockState(p));
                vale.setBlock(p,p.getY()==169?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            }
            clock.setDayTime(18000);
            h.assertTrue(ValeFirefly.habitat(vale,pos),"Frequency fixture uses a loaded Vale habitat");
            int surface=vale.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
            int upper=vale.getHeight(Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ())+1;
            long oldOpportunities=0,newOpportunities=0,oldPacks=0,newPacks=0;
            var entries=vale.getBiome(pos).value().getMobSettings().getMobs(MobCategory.AMBIENT);
            for(long seed=0;seed<12;seed++) {
                var random=net.minecraft.util.RandomSource.create(seed*7919+17);
                for(int sample=0;sample<50000;sample++) {
                    // NaturalSpawner.getRandomPosWithin samples this same inclusive vertical range.
                    int y=vale.getMinBuildHeight()+random.nextInt(upper-vale.getMinBuildHeight()+1);
                    if(y<surface || y>surface+4) continue;
                    boolean accepted=ValeFirefly.canSpawn(DudunkaMod.VALE_FIREFLY.get(),vale,
                            MobSpawnType.NATURAL,new BlockPos(pos.getX(),y,pos.getZ()),random);
                    if(accepted) {
                        newOpportunities++;
                        boolean oldAccepted=y>surface;
                        if(oldAccepted) oldOpportunities++;
                        var choiceRandom=net.minecraft.util.RandomSource.create(seed*1000003+sample*31L+5);
                        var selected=entries.getRandom(choiceRandom).orElse(null);
                        if(selected!=null && selected.type==DudunkaMod.VALE_FIREFLY.get()) {
                            newPacks++;
                            if(oldAccepted) oldPacks++;
                        }
                    }
                }
            }
            double ratio=(double)newPacks/oldPacks;
            System.out.println("FIREFLY_NATURAL_RATE seeds=12 candidates/seed=50000 oldPositions="+oldOpportunities
                    +" newPositions="+newOpportunities+" oldWeightedPacks="+oldPacks+" newWeightedPacks="+newPacks+" ratio="+ratio);
            h.assertTrue(oldOpportunities>200 && oldPacks>30 && ratio>1.65 && ratio<2.35,
                    "NaturalSpawner vertical sampling plus the actual biome weighted spawn list increases firefly pack selections by about 2x across 12 seeded streams; old="
                            +oldPacks+", new="+newPacks+", ratio="+ratio+", surface="+surface+", upper="+upper);
        } finally { saved.forEach((p,s)->vale.setBlock(p,s,18)); clock.setDayTime(time); }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=440, batch="isolated_firefly_flight")
    public static void boundedSafeFlightAndHomeReload(GameTestHelper h) {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(3,10,3));
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) level.getChunk((origin.getX()>>4)+x,(origin.getZ()>>4)+z);
        var saved=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(origin.offset(-7,-4,-7),origin.offset(7,4,7))) {
            saved.put(p.immutable(),level.getBlockState(p));
            boolean basin=p.getY()==origin.getY()-4 || p.getY()==origin.getY()-3
                    && (Math.abs(p.getX()-origin.getX())==7 || Math.abs(p.getZ()-origin.getZ())==7);
            level.setBlock(p,p.getX()==origin.getX()+3 || basin?Blocks.STONE.defaultBlockState():
                    p.getY()==origin.getY()-3?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        var firefly=DudunkaMod.VALE_FIREFLY.get().create(level);firefly.setPersistenceRequired();
        firefly.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0); level.addFreshEntity(firefly);
        Vec3 start=firefly.position();
        checkFlight(h,firefly,start,saved,1);
    }

    @GameTest(template="empty", timeoutTicks=640, batch="isolated_firefly_takeoff")
    public static void groundTakeoffAndSustainedHover(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,18,3));
        var saved=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(origin.offset(-7,-2,-7),origin.offset(7,6,7))) {
            level.getChunk(p.getX()>>4,p.getZ()>>4);
            saved.put(p.immutable(),level.getBlockState(p));
            level.setBlock(p,p.getY()<origin.getY()?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        // A wall and fluid strip obstruct some destinations, but leave room to hover and turn.
        for(var p:BlockPos.betweenClosed(origin.offset(3,0,-3),origin.offset(3,4,3)))level.setBlock(p,Blocks.STONE.defaultBlockState(),18);
        for(var p:BlockPos.betweenClosed(origin.offset(-5,-1,-5),origin.offset(-4,-1,5)))level.setBlock(p,Blocks.WATER.defaultBlockState(),18);
        var clock=level.getServer().overworld();long time=clock.getDayTime();clock.setDayTime(18000);
        var f=DudunkaMod.VALE_FIREFLY.get().create(level);f.getRandom().setSeed(32);f.setPersistenceRequired();
        f.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);level.addFreshEntity(f);
        var paused=DudunkaMod.VALE_FIREFLY.get().create(level);paused.setNoAi(true);paused.setPersistenceRequired();
        paused.moveTo(origin.getX()+1.5,origin.getY()+1,origin.getZ()+.5,0,0);level.addFreshEntity(paused);Vec3 held=paused.position();
        boolean[] finished={false};
        flightCleanup=()->{finished[0]=true;f.discard();paused.discard();saved.forEach((p,s)->level.setBlock(p,s,18));clock.setDayTime(time);};
        Vec3 start=f.position();double[] extrema={Double.MAX_VALUE,-Double.MAX_VALUE,0};
        h.onEachTick(()->{
            if(finished[0] || h.getTick()<1)return;
            h.assertTrue(f.isAlive() && f.isNoGravity() && f.withinHome(f.position()),"Flight stays alive, gravity-free and locally bounded: tick="+h.getTick()+", age="+f.tickCount+", alive="+f.isAlive()+", gravityFree="+f.isNoGravity()+", pos="+f.position()+", home="+f.flightHome());
            if (!ValeFirefly.safeVolume(level,f.getBoundingBox())) {
                boolean loaded=true;
                for (var p : BlockPos.betweenClosed(BlockPos.containing(f.getBoundingBox().minX,f.getBoundingBox().minY,f.getBoundingBox().minZ),
                        BlockPos.containing(f.getBoundingBox().maxX,f.getBoundingBox().maxY,f.getBoundingBox().maxZ))) loaded &= level.hasChunkAt(p);
                System.out.println("FIREFLY_COLLISION_DIAGNOSTIC uuid="+f.getUUID()+" noCollision="+(loaded?level.noCollision(f.getBoundingBox()):"unloaded")
                        +" border="+level.getWorldBorder().isWithinBounds(f.getBoundingBox()));
                for (var p : BlockPos.betweenClosed(BlockPos.containing(f.getBoundingBox().minX,f.getBoundingBox().minY,f.getBoundingBox().minZ),
                        BlockPos.containing(f.getBoundingBox().maxX,f.getBoundingBox().maxY,f.getBoundingBox().maxZ)))
                    System.out.println("FIREFLY_COLLISION_BLOCK pos="+p+" loaded="+level.hasChunkAt(p)
                            +" state="+(level.hasChunkAt(p)?level.getBlockState(p):"unloaded"));
            }
            h.assertTrue(ValeFirefly.safeVolume(level,f.getBoundingBox()),"Swept flight avoids wall/fluid: tick="+h.getTick()+", pos="+f.position()+", motion="+f.getDeltaMovement()+", home="+f.flightHome()+", box="+f.getBoundingBox());
            h.assertTrue(f.getDeltaMovement().length()<=.056,"Server velocity stays gentle");
            h.assertTrue(paused.position().equals(held) && paused.getDeltaMovement().equals(Vec3.ZERO),"NoAI server fixture holds position without gravity while normal AI flies");
            if(h.getTick()>=160) {
                double altitude=f.getY()-origin.getY();
                h.assertTrue(altitude>=.75 && altitude<=3.25,"Ground-spawned firefly must take off and sustain low hover: tick="+h.getTick()+", altitude="+altitude+", velocity="+f.getDeltaMovement());
                extrema[0]=Math.min(extrema[0],altitude);extrema[1]=Math.max(extrema[1],altitude);
                extrema[2]=Math.max(extrema[2],f.position().subtract(start).horizontalDistanceSqr());
            }
        });
        h.runAfterDelay(320,()->clock.setDayTime(6000));
        h.runAfterDelay(600,()->{
            cleanupFlightFixture();
            h.assertTrue(extrema[1]-extrema[0]>.25 && extrema[2]>1,"Flight changes height and horizontal position through night and daytime idle");h.succeed();
        });
    }
    private static void checkFlight(GameTestHelper h,ValeFirefly f,Vec3 start,
            java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> saved,int tick) {
        h.runAtTickTime(tick,()->{
            h.assertTrue(f.isAlive() && f.withinHome(f.position()),"Flight remains in bounded home volume");
            h.assertTrue(ValeFirefly.safeVolume(h.getLevel(),f.getBoundingBox()),"Never enters water or wall");
            h.assertTrue(f.getTarget()==null && f.getDeltaMovement().length()<=.056,"Non-hostile, bounded gentle speed");
            if(tick<400) checkFlight(h,f,start,saved,tick+1);
            else {
                h.assertTrue(f.position().distanceToSqr(start)>.001,"Actually flies with AI enabled");
                var restored=DudunkaMod.VALE_FIREFLY.get().create(h.getLevel()); restored.load(f.saveWithoutId(new CompoundTag()));
                h.assertTrue(restored.flightHome().equals(f.flightHome()) && restored.withinHome(f.position()),"Home persists on reload");
                BlockPos missing=new BlockPos(20000000,100,20000000);
                h.assertTrue(!h.getLevel().hasChunkAt(missing) && !ValeFirefly.safeVolume(h.getLevel(),new AABB(missing))
                        && !h.getLevel().hasChunkAt(missing),"Unloaded flight volume refused without chunk loading");
                f.discard(); restored.discard(); saved.forEach((p,s)->h.getLevel().setBlock(p,s,18)); h.succeed();
            }
        });
    }
}
