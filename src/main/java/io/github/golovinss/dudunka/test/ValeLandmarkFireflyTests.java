package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeLandmarkFireflyTests {
    private static final net.minecraft.server.level.TicketType<UUID> FIXTURE = net.minecraft.server.level.TicketType.create(
            "dudunka_test_landmark_fireflies",Comparator.comparing(UUID::toString));
    private static Runnable fixtureCleanup=()->{};
    private static void cleanup() { var action=fixtureCleanup;fixtureCleanup=()->{};action.run(); }
    @AfterBatch(batch="isolated_landmark_firefly_bells") public static void afterBells(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_landmark_firefly_styled_bells") public static void afterStyledBells(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @AfterBatch(batch="isolated_landmark_firefly_dance") public static void afterDance(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_landmark_firefly_bells")
    public static void bellAssistanceRespectsGatesVetoesReloadAndDeparture(GameTestHelper h) {
        checkAssistance(h,ValeAdventureStructure.Landmark.MUSIC_BELLS);
    }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_landmark_firefly_dance")
    public static void danceAssistanceRespectsGatesVetoesReloadAndDeparture(GameTestHelper h) {
        checkAssistance(h,ValeAdventureStructure.Landmark.JUMPER_DANCE);
    }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_landmark_firefly_styled_bells")
    public static void styledBellAssistanceRespectsGatesVetoesReloadAndDeparture(GameTestHelper h) {
        checkAssistance(h,ValeAdventureStructure.Landmark.MUSIC_BELLS,3);
    }
    private static void checkAssistance(GameTestHelper h,ValeAdventureStructure.Landmark kind) {
        checkAssistance(h,kind,2);
    }
    private static void checkAssistance(GameTestHelper h,ValeAdventureStructure.Landmark kind,int revision) {
        cleanup();
        var site=new ValeMusicTests.Site(h,kind,revision);
        var level=site.level; var rules=level.getGameRules(); var clock=level.getServer().overworld();
        if (revision==3) {
            // Production preserves level natural ground. Supply that prerequisite explicitly
            // for this high-altitude synthetic fixture and restore it with the site's blocks.
            for (int x=-5;x<=5;x++) for (int z=-4;z<=4;z++) {
                var pos=site.root.offset(x,-1,z);
                site.blocks.putIfAbsent(pos,level.getBlockState(pos));
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),18);
            }
        }
        long time=clock.getDayTime(); boolean spawning=rules.getBoolean(GameRules.RULE_DOMOBSPAWNING);
        var area=new AABB(site.root).inflate(ValeLandmarkFireflies.COUNT_RADIUS);
        var initial=new HashSet<UUID>(); level.getEntitiesOfClass(ValeFirefly.class,area).forEach(f->initial.add(f.getUUID()));
        var forced=new HashSet<>(level.getForcedChunks());
        var player=site.player();
        var chunk=new net.minecraft.world.level.ChunkPos(site.root);
        player.setNoGravity(true);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        java.util.function.Consumer<MobSpawnEvent.PositionCheck> denyPosition=e->{
            if(e.getLevel()==level && e.getEntity() instanceof ValeFirefly && e.getSpawnType()==MobSpawnType.EVENT) e.setResult(Event.Result.DENY);
        };
        java.util.function.Consumer<EntityJoinLevelEvent> denyJoin=e->{
            if(e.getLevel()==level && e.getEntity() instanceof ValeFirefly) e.setCanceled(true);
        };
        Runnable cleanup=()-> {
            MinecraftForge.EVENT_BUS.unregister(denyPosition);MinecraftForge.EVENT_BUS.unregister(denyJoin);
            level.getEntitiesOfClass(ValeFirefly.class,area,f->!initial.contains(f.getUUID())).forEach(f->f.discard());
            site.close();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());clock.setDayTime(time);
            level.getChunkSource().removeRegionTicket(FIXTURE,chunk,2,player.getUUID());
        };
        fixtureCleanup=cleanup;
        try {
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());clock.setDayTime(18000);
            h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Unregistered observer does not activate a landmark");
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            // A synthetic landmark needs explicit simulation preparation. A FULL
            // block chunk or an elapsed 80 ticks cannot establish this prerequisite.
            // Scope this ticket to the fixture; leave global player distances alone.
            // Native ENTITY_TICKING promotion waits for a radius-two FULL halo.
            // Full-suite sites can lie beyond earlier fixtures' loaded terrain;
            // finish that bounded preparation before the accelerated readiness wait.
            for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++)
                level.getChunk(chunk.x+dx,chunk.z+dz);
            level.getChunkSource().addRegionTicket(FIXTURE,chunk,2,player.getUUID());level.addNewPlayer(player);
        } catch(Throwable failure) { cleanup(); throw failure; }
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(site.root)
                &&level.areEntitiesLoaded(chunk.toLong()) &&level.players().contains(player),
                "Assistance fixture is genuinely entity-ticking: "+GameTestChunkDiagnostics.describe(level,site.root)))
                .thenExecute(()-> {
            try {
                System.out.println("LANDMARK_FIREFLY_READY "+kind+" "+GameTestChunkDiagnostics.describe(level,site.root));
                h.assertTrue(initial.isEmpty(),"High-altitude synthetic fixture starts without ambient flies");
                h.assertTrue(ValeLandmarkFireflies.nearby(level).stream().anyMatch(p->p.anchor().equals(site.root)),"Loaded native music start discovered near actual player");
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
                clock.setDayTime(6000);h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"No daytime replenishment");
                clock.setDayTime(18000);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"doMobSpawning respected");
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Spectator does not activate assistance");
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                MinecraftForge.EVENT_BUS.addListener(denyPosition);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Forge position veto respected");
                MinecraftForge.EVENT_BUS.unregister(denyPosition);MinecraftForge.EVENT_BUS.addListener(denyJoin);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Forge entity-join veto respected");
                MinecraftForge.EVENT_BUS.unregister(denyJoin);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==3,"Cap-independent assistance adds only three real flies");
                var flies=level.getEntitiesOfClass(ValeFirefly.class,area);
                h.assertTrue(flies.size()==3 && flies.stream().map(ValeFirefly::variant).distinct().count()==3,"All three existing color variants reused");
                for(var fly:flies) h.assertTrue(!fly.isPersistenceRequired() && ValeFirefly.safeVolume(level,fly.getBoundingBox()),"Safe, naturally despawnable spawn");
                for(int i=0;i<10;i++) h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Repeated passes never accumulate duplicates");
                var original=flies.get(0);var saved=new net.minecraft.nbt.CompoundTag();original.saveWithoutId(saved);original.discard();
                var loaded=DudunkaMod.VALE_FIREFLY.get().create(level);loaded.load(saved);level.addFreshEntity(loaded);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Saved/reloaded entity counts toward the same bound");
                var extra=DudunkaMod.VALE_FIREFLY.get().create(level);extra.moveTo(site.root.getX()+1,site.root.getY()+2,site.root.getZ()+1,0,0);level.addFreshEntity(extra);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Existing natural/command flies count too; no culling");
                player.moveTo(site.root.getX()+100,site.root.getY()+1,site.root.getZ(),0,0);
                h.assertTrue(ValeLandmarkFireflies.assist(level,site.piece)==0,"Departure immediately disables replenishment");
                loaded.checkDespawn();h.assertTrue(loaded.isRemoved(),"Existing natural despawn releases assisted fly when players leave");
                var remote=new BlockPos(29000000,160,29000000);int[] ground=new int[169];Arrays.fill(ground,160);
                h.assertTrue(ValeLandmarkFireflies.assist(level,new ValeAdventurePiece(ValeAdventureStructure.Landmark.MUSIC_BELLS,remote,ground))==0
                        && level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null
                        && forced.equals(level.getForcedChunks()),"No distant chunks loaded or forced tickets added");
            } finally { cleanup(); }
        }).thenSucceed();
    }
}
