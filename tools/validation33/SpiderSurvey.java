package io.github.golovinss.dudunka.test;
import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
/** Opt-in completed engine births and encounter survey; never packaged. */
public class SpiderSurvey {
    private static Runnable restore=()->{};
    public static void cleanup(){var action=restore;restore=()->{};action.run();}
    public static void start(GameTestHelper h) {
        cleanup();measureRoute(h,h.getLevel().getServer().getLevel(MushroomVale.LEVEL),List.of("memory_forest","glowing_mushroom_forest"),0);
    }
    private static void measureRoute(GameTestHelper h,ServerLevel level,List<String> biomes,int index) {
        if(index==biomes.size()){cleanup();h.succeed();return;}
        String biome=biomes.get(index);
        var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        BlockPos chosen=null;
        search: for(int radius=0;radius<=2048;radius+=32)for(int dx=-radius;dx<=radius;dx+=32)for(int dz=-radius;dz<=radius;dz+=32) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=8192+index*4096+dx,z=8192+dz;boolean dry=true;
            for(int along:new int[]{0,176,352}) {
                var b=generator.getBiomeSource().getNoiseBiome((x+along)>>2,20,z>>2,random.sampler());
                if(!b.unwrapKey().orElseThrow().location().getPath().equals(biome)
                        || generator.getBaseHeight(x+along,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random)<=68) {dry=false;break;}
            }
            if(dry){chosen=new BlockPos(x,80,z);break search;}
        }
        h.assertTrue(chosen!=null,"Dry route in requested biome found");var start=chosen;
        var players=level.getServer().getPlayerList();int oldView=players.getViewDistance(),oldSim=players.getSimulationDistance();
        var rules=level.getGameRules();boolean spawn=rules.getBoolean(GameRules.RULE_DOMOBSPAWNING),day=rules.getBoolean(GameRules.RULE_DAYLIGHT);
        var clock=level.getServer().overworld();long time=clock.getDayTime();
        players.setViewDistance(8);players.setSimulationDistance(8);
        var player=ValeFireflyPopulationTests.observer(level);
        var byBiome=new TreeMap<String,Integer>();var seen=new HashMap<MobSpawnType,Set<UUID>>(); int[] checks=new int[1];
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> join=e->{if(e.getLevel()==level && !e.loadedFromDisk() && e.getEntity() instanceof ValeSpider f)
            {seen.computeIfAbsent(f.getSpawnType(),k->new HashSet<>()).add(f.getUUID());
            byBiome.merge(level.getBiome(f.blockPosition()).unwrapKey().orElseThrow().location().getPath()+":"+f.getSpawnType(),1,Integer::sum);}};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck> check=e->{if(e.getLevel()==level && e.getEntity() instanceof ValeSpider && e.getSpawnType()==MobSpawnType.NATURAL)checks[0]++;};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(join);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(check);
        var previous=restore;
        Runnable end=()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(join);net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(check);
            player.discard();players.setViewDistance(oldView);players.setSimulationDistance(oldSim);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(spawn,level.getServer());rules.getRule(GameRules.RULE_DAYLIGHT).set(day,level.getServer());clock.setDayTime(time);};
        restore=()->{end.run();previous.run();};
        move(level,player,start.getX(),start.getZ());level.addNewPlayer(player);
        System.out.println("VALE33_OBSERVER seed="+level.getSeed()+" biome="+biome+" pos="+player.position());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());rules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());clock.setDayTime(6000);
        var competitors=new TreeMap<String,Integer>();int[] totals=new int[14];long beginning=h.getTick();
        h.onEachTick(()->{
            int tick=(int)(h.getTick()-beginning);if(tick<0 || tick>=4800)return;
            if(tick>0 && tick%400==0)move(level,player,start.getX()+tick/400*32,start.getZ());
            if(tick<400 || tick%20!=0)return;
            var mobs=level.getEntitiesOfClass(ValeSpider.class,player.getBoundingBox().inflate(128));int visible=0;
            var snapshot=new TreeMap<String,Integer>();
            for(var mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(128)))
                if(mob.getType().getCategory()==MobCategory.CREATURE && !(mob instanceof ValeSpider))
                    snapshot.merge(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString(),1,Integer::sum);
            snapshot.forEach((k,v)->competitors.merge(k,v,Math::max));
            for(var f:mobs) {
                if(f.distanceToSqr(player)<64*64 && Math.abs(f.getY()-player.getY())<=8) {totals[12]++;if(player.hasLineOfSight(f))visible++;}
                if(f.distanceToSqr(player)<32*32)totals[13]++;
                if(!level.getBlockState(f.blockPosition().below()).is(net.minecraft.tags.BlockTags.DIRT)
                        && !level.getBlockState(f.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK))totals[7]++;
                if(Math.abs(f.getY()-player.getY())>8)totals[8]++;
                totals[9]++;
            }
            totals[0]++;if(visible>0)totals[10]++;totals[11]=Math.max(totals[11],level.getEntitiesOfClass(Pufik.class,player.getBoundingBox().inflate(128)).size());totals[1]+=visible;totals[2]=Math.max(totals[2],visible);totals[3]=Math.max(totals[3],mobs.size());
            totals[4]=Math.max(totals[4],level.getEntitiesOfClass(net.minecraft.world.entity.animal.MushroomCow.class,player.getBoundingBox().inflate(128)).size());
            var state=level.getChunkSource().getLastSpawnState();if(state!=null && state.getMobCategoryCounts().getInt(MobCategory.CREATURE)>=10)totals[5]++;
        });
        h.runAfterDelay(4800,()->{
            var state=level.getChunkSource().getLastSpawnState();
            System.out.println("VALE33_POPULATION seed="+level.getSeed()+" biome="+biome+" start="+start+" ticks=4800 routeBlocks=352 positionChecks="+checks[0]+" births="+seen.entrySet().stream().map(e->e.getKey()+":"+e.getValue().size()).toList()
                +" competitors128Peak="+competitors+" proximity64Samples="+totals[12]+" proximity32Samples="+totals[13]+" birthBiomes="+byBiome+" visible64Mean="+(totals[0]==0?0:(double)totals[1]/totals[0])+" visible64Max="+totals[2]+" visibleSamples="+totals[10]+" pufik128Max="+totals[11]+" population128Max="+totals[3]+" nonSoilSamples="+totals[7]+" highLowSamples="+totals[8]+" spiderSamples="+totals[9]+" mooshrooms128Max="+totals[4]+" capFullSamples="+totals[5]+" samples="+totals[0]+" spawnChunks="+(state==null?0:state.getSpawnableChunkCount())+" caps="+(state==null?"none":state.getMobCategoryCounts()));
            h.assertTrue(state!=null && state.getSpawnableChunkCount()>0,"Actual loaded spawning chunks required");
            h.assertTrue(!seen.isEmpty(),"Completed engine births required, not merely valid spawn predicates");
            end.run();restore=previous;measureRoute(h,level,biomes,index+1);
        });
    }
    private static void move(ServerLevel l,ServerPlayer p,int x,int z){
        l.getChunk(x>>4,z>>4);int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
        for(int cy=y;cy>=y-40;cy--)if(l.getBlockState(new BlockPos(x,cy-1,z)).is(net.minecraft.tags.BlockTags.DIRT) && l.noCollision(new AABB(x+.2,cy,z+.2,x+.8,cy+1.8,z+.8))){y=cy;break;}
        p.moveTo(x+.5,y,z+.5,0,0);if(p.isAddedToWorld())l.getChunkSource().move(p);
    }
}
