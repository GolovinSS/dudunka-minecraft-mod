package io.github.golovinss.dudunka.test;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.MushroomVale;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Disposable dedicated integration fixture. Inert in ordinary servers and GameTestServer.
 * Cold generation has its own bounded setup phase, outside every 500-tick GameTest.
 * Tickets request FULL, never entity ticking. No getChunk/getChunkFuture/join/managedBlock.
 */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class FamilyTeleportFixture {
    private static final String OPT_IN="dudunka.testTeleportFixtures";
    private static final TicketType<UUID> FULL=TicketType.create("dudunka_test_tp_runtime_full",Comparator.comparing(UUID::toString));
    private static Session session;

    @Mod.EventBusSubscriber(modid=DudunkaMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
            if(Boolean.getBoolean(OPT_IN))event.register(FamilyTeleportRuntimeTests.class);
        }
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean(OPT_IN))return;
        event.getDispatcher().register(Commands.literal("dudunka_test_teleport")
                .requires(s->s.hasPermission(4) && s.getEntity()==null)
                .then(Commands.literal("prepare").executes(c->{
                    var server=c.getSource().getServer();
                    try {
                        disposable(server);
                        if(session!=null)throw new IllegalStateException("Warmup may run only once per fresh server");
                        session=new Session(server);
                        LogUtils.getLogger().info("FAMILY_TP_WARMUP_REGISTERED {}",session.describe());
                        return 1;
                    } catch(Exception failure) {failed("prepare",failure);return 0;}
                }))
                .then(Commands.literal("run").then(Commands.argument("test",StringArgumentType.word()).executes(c->{
                    try {
                        var s=requireReady(c.getSource().getServer());
                        s.run(StringArgumentType.getString(c,"test"));return 1;
                    } catch(Exception failure) {failed("run",failure);return 0;}
                }))));
    }
    private static void disposable(MinecraftServer server) throws Exception {
        if(!Boolean.getBoolean(OPT_IN) || !(server instanceof DedicatedServer))
            throw new IllegalStateException("Cold runtime fixtures require the explicit dedicated integration runner");
        var world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var work=world.getParent();
        if(!world.getFileName().toString().equals("world") || work==null
                || !work.getFileName().toString().startsWith("dudunka-teleport-server-")
                || !Files.readString(work.resolve(".dudunka-teleport-fixture")).equals("disposable-teleport-validation\n"))
            throw new IllegalStateException("Refusing fixtures outside a marked disposable validation world");
    }
    static Session requireReady(MinecraftServer server) {
        if(session==null || session.server!=server || !session.ready || session.failed)
            throw new IllegalStateException("Explicit asynchronous dedicated fixture warmup has not completed");
        return session;
    }
    private static void failed(String phase,Throwable failure) {
        LogUtils.getLogger().error("FAMILY_TP_INTEGRATION_FAILED phase={}",phase,failure);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || session==null || event.getServer()!=session.server
                || session.ready || session.failed)return;
        try {session.tick();}
        catch(Exception failure) {
            session.failed=true;
            failed("warmup "+session.describe()+", pending="+session.pending(),failure);
            session.release();
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        if(session==null || session.server!=event.getServer())return;
        try {FamilyTeleportRuntimeTests.cleanup();}
        finally {session.release();session=null;}
    }

    private record Request(ServerLevel level,ChunkPos pos) {
        boolean full(){return level.getChunkSource().getChunkNow(pos.x,pos.z)!=null;}
        boolean entities(){return level.areEntitiesLoaded(pos.toLong());}
        boolean ready(){return full() && entities();}
        String describe(){return level.dimension().location()+"@"+pos+":full="+full()+",entityData="+entities()
                +",holder="+level.getChunkSource().getChunkDebugData(pos).replace('\n',' ');}
    }
    static final class Session {
        // One finite queue, four tickets/tick, no per-site queues or capacity gates.
        // 75 operational chunks +25 template/spawn chunks, at most nine extra spawn-search chunks.
        private static final int MAX_REQUESTS=109, REQUESTS_PER_TICK=4, MAX_SETUP_TICKS=3600;
        private static final long MAX_SETUP_NANOS=180_000_000_000L;
        final MinecraftServer server;
        final ServerLevel source,vale;
        final BlockPos anchor,departure,landing;
        final List<Request> required=new ArrayList<>();
        final Set<Request> initiallyCold=new HashSet<>();
        final UUID ticket=UUID.randomUUID();
        final int initialCold;
        final boolean initialSourceCold;
        final long started=System.nanoTime();
        final Set<String> completed=new HashSet<>();
        int issued,cycles,platforms;
        long lastReport;
        boolean ready,failed,active,released;
        Session(MinecraftServer server) {
            this.server=server;source=server.overworld();vale=server.getLevel(MushroomVale.LEVEL);
            if(vale==null)throw new IllegalStateException("Missing Mushroom Vale test level");
            if(server.getPlayerList().getViewDistance()!=2 || server.getPlayerList().getSimulationDistance()!=2)
                throw new IllegalStateException("Fixture requires native view/simulation distance 2");
            var spawn=source.getSharedSpawnPos();var spawnChunk=new ChunkPos(spawn);
            anchor=new BlockPos(spawnChunk.getMinBlockX()+8,160,spawnChunk.getMinBlockZ()+8);
            departure=anchor.offset(1024,0,-1024);landing=departure.east(128);
            halo(source,departure,-2,2);halo(source,landing,-2,2);halo(vale,landing,-2,2);
            // Native StructureUtils.forceLoadChunks uses offsets -1..3, NOT -2..2.
            // Prepare that entire area before spawnStructure can write any blocks.
            halo(source,anchor,-1,3);
            int radius=Math.max(1,server.getSpawnRadius(source))+1;
            if(radius>32)throw new IllegalStateException("Bounded native player spawn search radius must be <=31");
            for(int x=(spawn.getX()-radius)>>4;x<=(spawn.getX()+radius)>>4;x++)
                for(int z=(spawn.getZ()-radius)>>4;z<=(spawn.getZ()+radius)>>4;z++)add(source,new ChunkPos(x,z));
            initialSourceCold=source.getChunkSource().getChunkNow(departure.getX()>>4,departure.getZ()>>4)==null;
            required.stream().filter(r->!r.full()).forEach(initiallyCold::add);
            initialCold=initiallyCold.size();
            if(initialCold==0 || !initialSourceCold)throw new IllegalStateException("Fresh-world cold-chunk probe requires missing FULL chunks");
            LogUtils.getLogger().info("FAMILY_TP_WARMUP_GEOMETRY source={}, sameDestination={}, crossDestination={}, template={}, requests={}",
                    new ChunkPos(departure),new ChunkPos(landing),vale.dimension().location()+"@"+new ChunkPos(landing),new ChunkPos(anchor),required.size());
            for(var r:required)LogUtils.getLogger().debug("FAMILY_TP_WARMUP_REQUEST_REGISTERED {}",r.describe());
        }
        private void halo(ServerLevel level,BlockPos center,int min,int max) {
            var c=new ChunkPos(center);
            for(int x=c.x+min;x<=c.x+max;x++)for(int z=c.z+min;z<=c.z+max;z++)add(level,new ChunkPos(x,z));
        }
        private void add(ServerLevel level,ChunkPos pos) {
            var r=new Request(level,pos);if(required.contains(r))return;
            if(required.size()>=MAX_REQUESTS)throw new IllegalStateException("Fixture footprint exceeded bounded 109 chunks");
            required.add(r);
        }
        void tick() {
            cycles++;
            if(cycles==1)LogUtils.getLogger().info("FAMILY_TP_WARMUP_DISPATCHER_ACTIVE server={}",server.getClass().getSimpleName());
            for(int n=0;n<REQUESTS_PER_TICK && issued<required.size();n++) {
                var r=required.get(issued);
                r.level.getChunkSource().addRegionTicket(FULL,r.pos,0,ticket);
                issued++;
            }
            long elapsed=System.nanoTime()-started;
            if(cycles==1 || elapsed-lastReport>=5_000_000_000L) {
                lastReport=elapsed;
                LogUtils.getLogger().info("FAMILY_TP_WARMUP_PROGRESS {}, pending={}",describe(),pending());
            }
            if(elapsed>MAX_SETUP_NANOS || cycles>MAX_SETUP_TICKS)
                throw new IllegalStateException("Bounded warmup timeout: "+describe()+", pending="+pending());
            if(issued!=required.size() || required.stream().anyMatch(r->!r.ready()))return;
            // Bounded edits, one single-chunk platform per tick, only after ALL
            // halos and native constructor/template areas are FULL with entity data.
            if(platforms<3) {
                var level=platforms==2?vale:source;var center=platforms==0?departure:landing;
                platform(level,center);platforms++;
                LogUtils.getLogger().info("FAMILY_TP_WARMUP_PLATFORM_READY dimension={}, chunk={}, {}",level.dimension().location(),new ChunkPos(center),describe());
                return;
            }
            assertPrepared();ready=true;
            LogUtils.getLogger().info("FAMILY_TP_WARMUP_READY {}",describe());
        }
        void assertPrepared() {
            if(issued!=required.size() || required.stream().anyMatch(r->!r.ready()))
                throw new IllegalStateException("Prepared FULL/entity data disappeared: "+describe()+", pending="+pending());
        }
        private void platform(ServerLevel level,BlockPos center) {
            var r=new Request(level,new ChunkPos(center));
            if(!r.ready())throw new IllegalStateException("No cold platform writes: "+r.describe());
            for(var pos:BlockPos.betweenClosed(center.offset(-5,-1,-5),center.offset(5,4,5)))
                level.setBlock(pos,pos.getY()==center.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        String describe() {
            return "requested="+required.size()+", issued="+issued+", queued="+(required.size()-issued)
                    +", inFlight="+required.subList(0,issued).stream().filter(r->!r.ready()).count()
                    +", full="+required.stream().filter(Request::full).count()
                    +", generatedFull="+initiallyCold.stream().filter(Request::full).count()
                    +", entityData="+required.stream().filter(Request::entities).count()+", initialCold="+initialCold
                    +", initialSourceCold="+initialSourceCold
                    +", platforms="+platforms+", dispatcherCycles="+cycles+", elapsedMs="+(System.nanoTime()-started)/1_000_000;
        }
        String pending(){return required.stream().filter(r->!r.ready()).limit(4).map(Request::describe).toList().toString();}
        void run(String name) {
            if(active || completed.contains(name))throw new IllegalStateException("Duplicate/concurrent runtime scenario: "+name);
            boolean known=Arrays.stream(FamilyTeleportRuntimeTests.class.getDeclaredMethods())
                    .anyMatch(m->m.isAnnotationPresent(GameTest.class) && m.getName().toLowerCase(Locale.ROOT).equals(name));
            if(!known)throw new IllegalArgumentException("Not a required runtime scenario: "+name);
            assertPrepared();active=true;
            var info=new GameTestInfo(GameTestRegistry.getTestFunction(name),Rotation.NONE,source);
            info.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo test) {}
                public void testPassed(GameTestInfo test) {
                    finish(test,true);
                }
                public void testFailed(GameTestInfo test) {
                    finish(test,false);
                }
                private void finish(GameTestInfo test,boolean passed) {
                    try {
                        FamilyTeleportRuntimeTests.cleanup();
                        if(!passed)throw new IllegalStateException("Native GameTest failed: "+name,test.getError());
                        completed.add(name);
                        LogUtils.getLogger().info("FAMILY_TP_TEST_PASS name={}",name);
                    } catch(Exception failure) {failed=true;FamilyTeleportFixture.failed("scenario "+name,failure);}
                    finally {active=false;}
                }
            });
            LogUtils.getLogger().info("FAMILY_TP_TEST_START name={}, timeoutTicks=500",name);
            GameTestRunner.runTest(info,anchor,GameTestTicker.SINGLETON);
        }
        void release() {
            if(released)return;released=true;
            for(var r:required.subList(0,issued))r.level.getChunkSource().removeRegionTicket(FULL,r.pos,0,ticket);
        }
    }
    private FamilyTeleportFixture() {}
}
