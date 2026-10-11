package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

/** Measures completed engine spawns on unmodified generated terrain, with a moving real player. */
@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class ValeFireflyPopulationTests {
    private static Runnable fixtureCleanup=()->{};
    public static void cleanup() {var action=fixtureCleanup;fixtureCleanup=()->{};action.run();}
    @AfterBatch(batch="isolated_firefly_population_glowing") public static void afterGlowing(ServerLevel level) {cleanup();}
    @AfterBatch(batch="isolated_firefly_population_pink") public static void afterPink(ServerLevel level) {cleanup();}
    @AfterBatch(batch="isolated_firefly_population_memory") public static void afterMemory(ServerLevel level) {cleanup();}
    @GameTest(template="empty", timeoutTicks=1500, batch="isolated_firefly_population_glowing")
    public static void glowingForestPopulation(GameTestHelper h) { measure(h,"glowing_mushroom_forest"); }
    @GameTest(template="empty", timeoutTicks=1500, batch="isolated_firefly_population_pink")
    public static void pinkMeadowsPopulation(GameTestHelper h) { measure(h,"pink_meadows"); }
    @GameTest(template="empty", timeoutTicks=1500, batch="isolated_firefly_population_memory")
    public static void memoryForestPopulation(GameTestHelper h) { measure(h,"memory_forest"); }

    public static void measure(GameTestHelper h,String biome) {
        cleanup();
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var clock=h.getLevel().getServer().overworld();
        var rules=level.getGameRules();
        var players=level.getServer().getPlayerList();int oldView=players.getViewDistance(),oldSimulation=players.getSimulationDistance();
        // GameTestServer leaves both distances at zero; that produces unticked
        // age-zero entities and is not representative of a Minecraft client.
        boolean spawning=rules.getBoolean(GameRules.RULE_DOMOBSPAWNING),cycle=rules.getBoolean(GameRules.RULE_DAYLIGHT);
        long time=clock.getDayTime();
        var found=level.findClosestBiome3d(b->b.unwrapKey().orElseThrow().location().getPath().equals(biome),
                new BlockPos(1024,160,1024),4096,32,64);
        h.assertTrue(found!=null,"Population fixture must find biome "+biome);
        var start=found.getFirst();
        players.setViewDistance(8);players.setSimulationDistance(8);
        var player=observer(level);
        var seen=new HashSet<UUID>();
        var completedGroups=new HashMap<ValeFirefly.FireflySpawnGroup,Map<UUID,Integer>>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> completed=event->{
            if(event.getLevel()==level && event.getEntity() instanceof ValeFirefly f && f.getSpawnType()==MobSpawnType.NATURAL) {
                seen.add(f.getUUID());completedGroups.computeIfAbsent(f.flightGroup(),g->new HashMap<>()).put(f.getUUID(),f.variant());
            }
        };
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck> isolate=event->{
            if(event.getLevel()!=level && event.getSpawnType()==MobSpawnType.NATURAL)
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(isolate);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,completed);
        fixtureCleanup=()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(isolate);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(completed);
            player.discard();
            for(var entity:level.getEntitiesOfClass(Mob.class,new AABB(start).inflate(320)))entity.discard();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());
            rules.getRule(GameRules.RULE_DAYLIGHT).set(cycle,level.getServer());clock.setDayTime(time);
            players.setViewDistance(oldView);players.setSimulationDistance(oldSimulation);
        };
        moveObserver(level,player,start.getX(),start.getZ());
        level.addNewPlayer(player);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());clock.setDayTime(18000);
        int[] totals=new int[10]; // samples, visible sum/max, population max, bats max, airborne, observed, groups sum/max, mixed max
        h.onEachTick(()->{
            int tick=(int)h.getTick();
            if(tick>=1200)return;
            if(tick>0 && tick%200==0 && tick<1200) moveObserver(level,player,start.getX()+tick/200*16,start.getZ());
            if(tick%20!=0 || tick<200)return;
            var flies=level.getEntitiesOfClass(ValeFirefly.class,player.getBoundingBox().inflate(128));
            int visible=0;
            var visibleFlies=new ArrayList<ValeFirefly>();
            for(var f:flies) {
                h.assertTrue(f.getSpawnType()==MobSpawnType.NATURAL,"Population must come from engine natural spawning");
                if(f.distanceToSqr(player)<=48*48 && Math.abs(f.getY()-player.getY())<=8 && player.hasLineOfSight(f)) {
                    visible++;totals[6]++;visibleFlies.add(f);
                    if(!f.onGround() && level.noCollision(f.getBoundingBox().move(0,-.5,0)))totals[5]++;
                }
            }
            totals[0]++;totals[1]+=visible;totals[2]=Math.max(totals[2],visible);totals[3]=Math.max(totals[3],flies.size());
            totals[4]=Math.max(totals[4],level.getEntitiesOfClass(net.minecraft.world.entity.ambient.Bat.class,player.getBoundingBox().inflate(128)).size());
            var visiblePacks=new HashMap<ValeFirefly.FireflySpawnGroup,List<ValeFirefly>>();
            for(var f:visibleFlies)visiblePacks.computeIfAbsent(f.flightGroup(),g->new ArrayList<>()).add(f);
            int groups=0,mixed=0;
            for(var pack:visiblePacks.values())if(pack.size()>=2) {groups++;if(pack.stream().map(ValeFirefly::variant).distinct().count()>1)mixed++;}
            totals[7]+=groups;totals[8]=Math.max(totals[8],groups);totals[9]=Math.max(totals[9],mixed);
        });
        h.runAfterDelay(1200,()->{
            var state=level.getChunkSource().getLastSpawnState();
            var sizes=new TreeMap<Integer,Integer>();
            for(var pack:completedGroups.values())sizes.merge(pack.size(),1,Integer::sum);
            System.out.println("FIREFLY_POPULATION seed="+level.getSeed()+" biome="+biome+" start="+start
                    +" uniqueNatural="+seen.size()+" visible48Mean="+(totals[0]==0?0:(double)totals[1]/totals[0])
                    +" visible48Max="+totals[2]+" population128Max="+totals[3]+" bats128Max="+totals[4]
                    +" airborneSamples="+totals[5]+"/"+totals[6]+" spawnChunks="+(state==null?0:state.getSpawnableChunkCount())
                    +" visibleGroupsMean="+(totals[0]==0?0:(double)totals[7]/totals[0])+" visibleGroupsMax="+totals[8]+" mixedGroupsMax="+totals[9]
                    +" completedPacks="+sizes
                    +" caps="+(state==null?"none":state.getMobCategoryCounts()));
            cleanup();
            h.assertTrue(state!=null && state.getSpawnableChunkCount()>0,"Real player must drive loaded natural-spawn chunks");
            h.assertTrue(!seen.isEmpty() && totals[9]>=1,"Completed natural spawns form visible mixed-color groups");
            h.assertTrue(totals[3]<=25,"Single-player population remains bounded by vanilla ambient spawning and small-pack overshoot: "+totals[3]);
            for(var pack:completedGroups.values())h.assertTrue(pack.size()<=5 && (pack.size()<2 || new HashSet<>(pack.values()).size()>=2),
                    "Actual completed natural packs stay small and mixed when at least two members succeed: "+pack.values());
            h.succeed();
        });
    }
    public static ServerPlayer observer(ServerLevel level) {
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"FireflyObserver"));
        var connection=new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet,PacketSendListener listener) {}
        };
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet,PacketSendListener listener) {}
        };
        player.setGameMode(GameType.CREATIVE);player.setNoGravity(true);player.setInvulnerable(true);return player;
    }
    private static void moveObserver(ServerLevel level,ServerPlayer player,int x,int z) {
        level.getChunk(x>>4,z>>4); // Test fixture only: normal production code never requests chunks.
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
        // Observe from actual walkable soil, not from a giant mushroom's cap.
        for(int candidate=y-1;candidate>=y-40;candidate--) {
            var ground=new BlockPos(x,candidate-1,z);
            if(level.getBlockState(ground).is(net.minecraft.tags.BlockTags.DIRT)
                    && level.noCollision(new AABB(x+.2,candidate,z+.2,x+.8,candidate+1.8,z+.8))) {y=candidate;break;}
        }
        player.moveTo(x+.5,y,z+.5,0,0);if(player.isAddedToWorld())level.getChunkSource().move(player);
    }
}
