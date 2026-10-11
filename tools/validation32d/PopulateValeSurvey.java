package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.phys.AABB;

/** Opt-in instrumentation only: absent from normal compilation and the shipped JAR. */
public class PopulateValeSurvey {
    private static final Map<String, Map<String,Integer>> counts = new TreeMap<>();
    private static final Map<MushroomValeDecorFeature.Blueprint,String> names = new HashMap<>();
    private static final Set<Long> area = new HashSet<>();
    private static Runnable restore = () -> {};
    private static boolean active, population;
    public static void cleanup() { active=false; population=false; var r=restore;restore=()->{};r.run(); }
    private static synchronized void add(String biome,String key) { counts.computeIfAbsent(biome,b->new TreeMap<>()).merge(key,1,Integer::sum); }
    public static boolean reject(MushroomValeDecorFeature.Blueprint b,BlockPos origin,String reason) {
        // All rejects are also included in attempt-success totals; separate site histogram below.
        if(active && area.contains(new ChunkPos(origin).toLong())) add("site_checks",names.getOrDefault(b,"unknown")+":"+reason);
        return false;
    }
    public static void placement(FeaturePlaceContext<MushroomValeDecorFeature.Blueprint> c,boolean success) {
        if(!active || !area.contains(new ChunkPos(c.origin()).toLong()))return;
        var biome=c.level().getBiome(c.origin()).unwrapKey().orElseThrow().location().getPath();
        var name=names.get(c.config()); add(biome,name+":attempt");
        if(success) { add(biome,name+":success");

        }
    }
    public static void siteSuccess(MushroomValeDecorFeature.Blueprint b,BlockPos origin,WorldGenLevel l) {
        if(active && area.contains(new ChunkPos(origin).toLong()) && names.getOrDefault(b, "").endsWith("cottage"))
            System.out.println("VALE32D_COTTAGE seed="+l.getSeed()+" feature="+names.get(b)+" pos="+origin);
    }
    public static void spawn(ServerLevelAccessor l,MobSpawnType reason,BlockPos pos,boolean habitat,boolean safe) {
        if(!active || (!population && !area.contains(new ChunkPos(pos).toLong())))return;
        add("pufik_rules",reason+":attempt");
        add("pufik_rules",reason+":"+(!habitat?"habitat_reject":safe?"pass":"ground_reject"));
        if(habitat && !safe) {
            var floor=l.getBlockState(pos.below());String detail="adjacent_drop_or_hazard";
            if(!floor.isFaceSturdy(l,pos.below(),Direction.UP))detail="unsupported_floor";
            else if(!floor.getFluidState().isEmpty() || !l.getFluidState(pos).isEmpty())detail="fluid";
            else if(!l.getBlockState(pos).getCollisionShape(l,pos).isEmpty() || !l.getBlockState(pos.above()).getCollisionShape(l,pos.above()).isEmpty())detail="body_or_head_collision";
            add("pufik_ground",reason+":"+detail);
        }
    }
    public static void start(GameTestHelper h) {
        cleanup();counts.clear();names.clear();area.clear();
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        h.assertTrue(level!=null,"Vale loaded");
        var registry=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        registry.entrySet().forEach(e->{ if(e.getValue().config() instanceof MushroomValeDecorFeature.Blueprint b) names.put(b,e.getKey().location().getPath()); });
        var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var targets=new TreeMap<String,BlockPos>();
        for(int radius=1024;radius<=4096 && targets.size()<3;radius+=128)
            for(int x=-radius;x<=radius;x+=128)for(int z=-radius;z<=radius;z+=128){
                if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
                String biome=generator.getBiomeSource().getNoiseBiome(x>>2,20,z>>2,random.sampler()).unwrapKey().orElseThrow().location().getPath();
                if(targets.containsKey(biome))continue;
                final int xx=x,zz=z;
                if(targets.values().stream().anyMatch(v->Math.max(Math.abs(v.getX()-xx),Math.abs(v.getZ()-zz))<640))continue;
                int y=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                if(y>66)targets.put(biome,new BlockPos(x,y,z));
            }
        h.assertTrue(targets.size()==3,"All dry biome patches located");
        for(var p:targets.values())for(int dx=-12;dx<12;dx++)for(int dz=-12;dz<12;dz++)area.add(ChunkPos.asLong((p.getX()>>4)+dx,(p.getZ()>>4)+dz));
        active=true;
        var births=new HashMap<String,Integer>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> join=e->{
            if(e.getLevel()==level && !e.loadedFromDisk() && e.getEntity() instanceof Pufik f && area.contains(new ChunkPos(f.blockPosition()).toLong())) {
                String reason=String.valueOf(f.getSpawnType()); births.merge(reason,1,Integer::sum);
                var p=f.blockPosition();
                String biome=generator.getBiomeSource().getNoiseBiome(p.getX()>>2,p.getY()>>2,p.getZ()>>2,random.sampler()).unwrapKey().orElseThrow().location().getPath();
                add(biome,"pufik_birth:"+reason);
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(join);
        restore=()->net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(join);
        for(var target:targets.entrySet()){
            var center=target.getValue();System.out.println("VALE32D_PATCH seed="+level.getSeed()+" biome="+target.getKey()+" center="+center+" chunks=576");
            for(int dx=-12;dx<12;dx++)for(int dz=-12;dz<12;dz++)level.getChunk((center.getX()>>4)+dx,(center.getZ()>>4)+dz);
        }
        var columns=new TreeMap<String,Integer>();
        for(long packed:area){var c=new ChunkPos(packed); for(int x=c.getMinBlockX();x<=c.getMaxBlockX();x++)for(int z=c.getMinBlockZ();z<=c.getMaxBlockZ();z++)
            columns.merge(level.getBiome(new BlockPos(x,80,z)).unwrapKey().orElseThrow().location().getPath(),1,Integer::sum);}
        counts.forEach((b,m)->System.out.println("VALE32D_GENERATION seed="+level.getSeed()+" biome="+b+" columns="+columns.getOrDefault(b,0)+" counts="+m));
        System.out.println("VALE32D_AREA seed="+level.getSeed()+" chunks="+area.size()+" columns="+columns+" births="+births);
        // Use separate untouched route starts, preserving normally generated animals and competition.
        population=true;
        measureRoute(h,level,new ArrayList<>(targets.keySet()),0);
    }
    private static void measureRoute(GameTestHelper h,ServerLevel level,List<String> biomes,int index) {
        if(index==biomes.size()){cleanup();h.succeed();return;}
        String biome=biomes.get(index);
        var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        BlockPos chosen=null;
        search: for(int radius=0;radius<=2048;radius+=32)for(int dx=-radius;dx<=radius;dx+=32)for(int dz=-radius;dz<=radius;dz+=32) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=8192+index*4096+dx,z=8192+dz;boolean dry=true;
            for(int along:new int[]{0,80,160}) {
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
        var seen=new HashMap<MobSpawnType,Set<UUID>>(); int[] checks=new int[1];
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> join=e->{if(e.getLevel()==level && !e.loadedFromDisk() && e.getEntity() instanceof Pufik f)
            seen.computeIfAbsent(f.getSpawnType(),k->new HashSet<>()).add(f.getUUID());};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck> check=e->{if(e.getLevel()==level && e.getEntity() instanceof Pufik && e.getSpawnType()==MobSpawnType.NATURAL)checks[0]++;};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(join);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(check);
        var previous=restore;
        Runnable end=()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(join);net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(check);
            player.discard();players.setViewDistance(oldView);players.setSimulationDistance(oldSim);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(spawn,level.getServer());rules.getRule(GameRules.RULE_DAYLIGHT).set(day,level.getServer());clock.setDayTime(time);};
        restore=()->{end.run();previous.run();};
        move(level,player,start.getX(),start.getZ());level.addNewPlayer(player);
        System.out.println("VALE32D_OBSERVER seed="+level.getSeed()+" biome="+biome+" pos="+player.position());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());rules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());clock.setDayTime(6000);
        var before=new TreeMap<>(counts.getOrDefault("pufik_rules",Map.of()));
        int[] totals=new int[10];long beginning=h.getTick();
        h.onEachTick(()->{
            int tick=(int)(h.getTick()-beginning);if(tick<0 || tick>=2400)return;
            if(tick>0 && tick%400==0)move(level,player,start.getX()+tick/400*32,start.getZ());
            if(tick<400 || tick%20!=0)return;
            var mobs=level.getEntitiesOfClass(Pufik.class,player.getBoundingBox().inflate(128));int visible=0;
            for(var f:mobs) {
                if(f.distanceToSqr(player)<64*64 && Math.abs(f.getY()-player.getY())<=8 && player.hasLineOfSight(f))visible++;
                if(!level.getBlockState(f.blockPosition().below()).is(net.minecraft.tags.BlockTags.DIRT)
                        && !level.getBlockState(f.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK))totals[7]++;
                if(Math.abs(f.getY()-player.getY())>8)totals[8]++;
                totals[9]++;
            }
            totals[0]++;totals[1]+=visible;totals[2]=Math.max(totals[2],visible);totals[3]=Math.max(totals[3],mobs.size());
            totals[4]=Math.max(totals[4],level.getEntitiesOfClass(net.minecraft.world.entity.animal.MushroomCow.class,player.getBoundingBox().inflate(128)).size());
            var state=level.getChunkSource().getLastSpawnState();if(state!=null && state.getMobCategoryCounts().getInt(MobCategory.CREATURE)>=10)totals[5]++;
        });
        h.runAfterDelay(2400,()->{
            var delta=new TreeMap<>(counts.getOrDefault("pufik_rules",Map.of()));before.forEach((k,v)->delta.merge(k,-v,Integer::sum));
            var state=level.getChunkSource().getLastSpawnState();
            System.out.println("VALE32D_POPULATION seed="+level.getSeed()+" biome="+biome+" start="+start+" ticks=2400 routeBlocks=160 rules="+delta+" positionChecks="+checks[0]+" births="+seen.entrySet().stream().map(e->e.getKey()+":"+e.getValue().size()).toList()
                +" visible64Mean="+(totals[0]==0?0:(double)totals[1]/totals[0])+" visible64Max="+totals[2]+" population128Max="+totals[3]+" nonSoilSamples="+totals[7]+" highLowSamples="+totals[8]+" pufikSamples="+totals[9]+" mooshrooms128Max="+totals[4]+" capFullSamples="+totals[5]+" samples="+totals[0]+" spawnChunks="+(state==null?0:state.getSpawnableChunkCount())+" caps="+(state==null?"none":state.getMobCategoryCounts()));
            h.assertTrue(state!=null && state.getSpawnableChunkCount()>0,"Actual loaded spawning chunks required");
            end.run();restore=previous;measureRoute(h,level,biomes,index+1);
        });
    }
    private static void move(ServerLevel l,ServerPlayer p,int x,int z){
        l.getChunk(x>>4,z>>4);int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
        for(int cy=y;cy>=y-40;cy--)if(l.getBlockState(new BlockPos(x,cy-1,z)).is(net.minecraft.tags.BlockTags.DIRT) && l.noCollision(new AABB(x+.2,cy,z+.2,x+.8,cy+1.8,z+.8))){y=cy;break;}
        p.moveTo(x+.5,y,z+.5,0,0);if(p.isAddedToWorld())l.getChunkSource().move(p);
    }
}
