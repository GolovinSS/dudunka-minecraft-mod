package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import static io.github.golovinss.dudunka.ValeMusicProgress.ConcertResult;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeConcertTests {
    private static final BlockPos ROOT = new BlockPos(8,160,8);
    private static void chapter(ValeMusicProgress data, UUID owner, int chapter) {
        switch(chapter) {
            case 0 -> { for(int bell=0;bell<3;bell++) data.strike(owner,ROOT,bell); }
            case 1 -> { for(int flower:new int[]{0,0,1,2,2,3}) data.dance(owner,ROOT,flower); }
            case 2 -> { data.listenToMemory(owner,ROOT); data.interpretMemory(owner,ROOT,2); }
            case 3 -> {
                data.tuneHarp(owner,ROOT,0); data.tuneHarp(owner,ROOT,0); data.tuneHarp(owner,ROOT,1);
                data.playHarp(owner,ROOT);
            }
        }
    }
    private static void ready(ValeMusicProgress data, UUID owner) { for(int i=0;i<4;i++) chapter(data,owner,i); }
    private static void ticks(ServerPlayer p, int count) {
        for(int i=0;i<count;i++) ValeConcertQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
    }
    private static List<String> texts(ValeMusicTests.Site site, ServerPlayer p) {
        return site.packets.get(p.getUUID()).stream().filter(ClientboundSystemChatPacket.class::isInstance)
                .map(ClientboundSystemChatPacket.class::cast).map(packet->packet.content().getContents())
                .filter(TranslatableContents.class::isInstance).map(TranslatableContents.class::cast)
                .map(TranslatableContents::getKey).filter(key->key.startsWith("message.dudunka.concert.")).toList();
    }
    private static Map<BlockPos,BlockState> snapshot(ValeMusicTests.Site site) {
        var blocks=new LinkedHashMap<BlockPos,BlockState>();
        BlockPos.betweenClosed(site.root.offset(-6,-2,-6),site.root.offset(6,11,6))
                .forEach(pos->blocks.put(pos.immutable(),site.level.getBlockState(pos)));
        return blocks;
    }
    private static Set<UUID> entities(ValeMusicTests.Site site) {
        var ids=new HashSet<UUID>(); site.level.getAllEntities().forEach(e->ids.add(e.getUUID())); return ids;
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void allFourChaptersGateFinaleAndCannotBeBypassedByCompletion(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID();
        var gates=new ConcertResult[]{ConcertResult.NEED_FIRST_NOTE,ConcertResult.NEED_DANCE,ConcertResult.NEED_MEMORY,ConcertResult.NEED_HARP};
        for(int i=0;i<4;i++) {
            h.assertTrue(data.concertPrerequisite(owner)==gates[i] && data.finishConcert(owner)==gates[i]
                    && !data.concertCompleted(owner),"Each earlier chapter gates the finale");
            chapter(data,owner,i);
        }
        h.assertTrue(data.concertPrerequisite(owner)==ConcertResult.READY && !data.concertCompleted(owner),
                "Earlier chapters alone never complete the finale"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void diskRoundTripAndReplayPreserveProgressAndUnknownFields(GameTestHelper h) throws Exception {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var other=UUID.randomUUID(); ready(data,owner);
        h.assertTrue(data.finishConcert(owner)==ConcertResult.COMPLETED,"First completion is recorded");
        var saved=data.save(new CompoundTag()); saved.putInt("Version",42); saved.putString("FutureRoot","kept");
        var row=saved.getList("Owners",Tag.TAG_COMPOUND).getCompound(0);
        row.getCompound("Chapters").getCompound(ValeMusicProgress.CONCERT).putString("FutureConcert","kept");
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(saved,bytes);
        var restored=ValeMusicProgress.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
        h.assertTrue(restored.concertCompleted(owner) && !restored.concertCompleted(other)
                && restored.completed(owner) && restored.danceCompleted(owner) && restored.memoryCompleted(owner) && restored.harpCompleted(owner),
                "UUID completion and all earlier music chapters survive disk encoding");
        restored.setDirty(false); var before=restored.save(new CompoundTag());
        for(int i=0;i<5;i++) h.assertTrue(restored.finishConcert(owner)==ConcertResult.ALREADY_COMPLETE,"Replay is idempotent");
        h.assertTrue(!restored.isDirty() && before.equals(restored.save(new CompoundTag())) && before.equals(saved),
                "Replay never mutates progress, version or future fields"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void malformedAndDuplicateFinaleRowsCannotInventOrRevokeCompletion(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); ready(data,owner); data.finishConcert(owner);
        var saved=data.save(new CompoundTag()); var rows=saved.getList("Owners",Tag.TAG_COMPOUND);
        var bad=rows.getCompound(0).copy(); bad.getCompound("Chapters").getCompound(ValeMusicProgress.CONCERT).putString("Completed","true");
        rows.add(bad);
        h.assertTrue(ValeMusicProgress.load(saved).concertCompleted(owner),"Malformed duplicate cannot revoke completion");
        var malformed=new CompoundTag(); var onlyBad=new ListTag(); onlyBad.add(bad); malformed.put("Owners",onlyBad);
        h.assertTrue(!ValeMusicProgress.load(malformed).concertCompleted(owner),"String completion cannot invent a finale"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void nativeActivationNeedsAllChaptersAndAnExplicitClick(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server);
            for(int i=0;i<4;i++) {
                h.assertTrue(site.click(p,site.root,InteractionHand.MAIN_HAND).isCanceled(),"Locked anchor explains missing chapter");
                ticks(p,450); h.assertTrue(!data.concertCompleted(p.getUUID()),"Locked click cannot start playback");
                chapter(data,p.getUUID(),i);
            }
            ticks(p,450); h.assertTrue(!data.concertCompleted(p.getUUID()),"Discovery never starts a concert automatically");
            site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,439);
            h.assertTrue(!data.concertCompleted(p.getUUID()),"Full score must finish before completion"); ticks(p,1);
            h.assertTrue(data.concertCompleted(p.getUUID()),"Explicit native activation completes without any NPC");
            h.assertTrue(texts(site,p).containsAll(List.of("message.dudunka.concert.bells","message.dudunka.concert.dance",
                    "message.dudunka.concert.memory","message.dudunka.concert.harp","message.dudunka.concert.together","message.dudunka.concert.complete")),
                    "All four motifs, reunion and restored Valley receive readable feedback");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void interleavedPlayersFinishIndependentlyWithPrivateEffects(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server); ready(data,a.getUUID()); ready(data,b.getUUID());
            site.click(a,site.root,InteractionHand.MAIN_HAND); ticks(a,200);
            h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"Other player receives no concert packets");
            site.click(b,site.root,InteractionHand.MAIN_HAND); ticks(b,100); ticks(a,240);
            h.assertTrue(data.concertCompleted(a.getUUID()) && !data.concertCompleted(b.getUUID()),"Independent start times and completion");
            var aPackets=site.packets.get(a.getUUID()).size(); ticks(b,340);
            h.assertTrue(data.concertCompleted(b.getUUID()) && site.packets.get(a.getUUID()).size()==aPackets,"Second player's concert cannot affect first");
            h.assertTrue(site.packets.get(a.getUUID()).stream().anyMatch(ClientboundSoundPacket.class::isInstance)
                    && site.packets.get(a.getUUID()).stream().anyMatch(ClientboundLevelParticlesPacket.class::isInstance),"Private sounds and particles accompany the score");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void repeatedClicksNeverStackSkipOrDuplicateRewardsOnReplay(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server); ready(data,p.getUUID());
            site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,200);
            int packets=site.packets.get(p.getUUID()).size();
            for(int i=0;i<10;i++) site.click(p,site.root,InteractionHand.MAIN_HAND);
            h.assertTrue(site.packets.get(p.getUUID()).size()==packets && !data.concertCompleted(p.getUUID()),"Repeated interaction neither stacks nor skips playback");
            ticks(p,240); h.assertTrue(data.concertCompleted(p.getUUID()),"Repeated clicks do not restart the score");
            var before=data.save(new CompoundTag()); data.setDirty(false); var score=site.packets.get(p.getUUID()).stream()
                    .filter(ClientboundSoundPacket.class::isInstance).map(ClientboundSoundPacket.class::cast).map(ClientboundSoundPacket::getPitch).toList();
            site.packets.get(p.getUUID()).clear(); site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,440);
            var replay=site.packets.get(p.getUUID()).stream().filter(ClientboundSoundPacket.class::isInstance)
                    .map(ClientboundSoundPacket.class::cast).map(ClientboundSoundPacket::getPitch).toList();
            h.assertTrue(score.equals(replay) && !data.isDirty() && before.equals(data.save(new CompoundTag())),"Replay gives the entire same concert with no repeated save reward");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void departureLogoutDimensionSpectatorAndDeathCancelSafely(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var data=ValeMusicProgress.get(site.level.getServer());
            for(int mode=0;mode<5;mode++) {
                var p=site.player(); ready(data,p.getUUID()); site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,200);
                switch(mode) {
                    case 0 -> p.moveTo(site.root.getX()+30,161,site.root.getZ(),0,0);
                    case 1 -> ValeConcertQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
                    case 2 -> ValeConcertQuest.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p,MushroomVale.LEVEL,net.minecraft.world.level.Level.OVERWORLD));
                    case 3 -> p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    case 4 -> p.setHealth(0);
                }
                ticks(p,450); h.assertTrue(!data.concertCompleted(p.getUUID()) && data.harpCompleted(p.getUUID()),"Cancellation preserves prior chapters without awarding finale");
                if(mode<3) {
                    p.moveTo(site.root.getX()+.5,161,site.root.getZ()+.5,0,0); site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,440);
                    h.assertTrue(data.concertCompleted(p.getUUID()),"Safe cancellation allows a fresh full concert");
                }
            }
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void everyDamagedBlueprintPieceRejectsActivationAndMidConcertDamageCancels(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server); ready(data,p.getUUID());
            for(var entry:site.piece.plan().entrySet()) {
                site.level.setBlock(entry.getKey(),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!ValeConcertQuest.interact(p,site.root) && site.level.getBlockState(entry.getKey()).isAir(),"Any missing piece fails closed without repair");
                site.level.setBlock(entry.getKey(),entry.getValue(),18);
            }
            site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,439);
            var cap=site.root.offset(0,9,1); var original=site.level.getBlockState(cap); site.level.setBlock(cap,Blocks.AIR.defaultBlockState(),18);
            ticks(p,1); site.level.setBlock(cap,original,18); ticks(p,450);
            h.assertTrue(!data.concertCompleted(p.getUUID()),"Damage at final tick cancels and repair never resumes implicitly");
            site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,440);
            h.assertTrue(data.concertCompleted(p.getUUID()),"Repaired native site can be activated anew");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void decorativeStartsOffhandDistanceSpectatorsAndUnloadedPositionsAreRejected(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var p=site.player(); var start=site.chunk.getStartForStructure(site.structure);
            site.chunk.setStartForStructure(site.structure,StructureStart.INVALID_START);
            h.assertTrue(!ValeConcertQuest.interact(p,site.root),"Decorative copy without a native start is inert"); site.chunk.setStartForStructure(site.structure,start);
            h.assertTrue(!site.click(p,site.root,InteractionHand.OFF_HAND).isCanceled(),"Offhand cannot activate");
            h.assertTrue(ValeConcertQuest.resolve(site.level,site.root.offset(0,0,-3))==null,"Listening decoration is not the activation anchor");
            h.assertTrue(ValeConcertQuest.resolve(h.getLevel(),site.root)==null,"Wrong dimension is rejected");
            var remote=new BlockPos(29000000,160,29000000);
            h.assertTrue(ValeConcertQuest.resolve(site.level,remote)==null && site.level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null,"Resolver never loads remote chunks");
            p.moveTo(site.root.getX()+30,161,site.root.getZ(),0,0); h.assertTrue(!ValeConcertQuest.interact(p,site.root),"Remote activation rejected");
            p.moveTo(site.root.getX()+.5,161,site.root.getZ()+.5,0,0); p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            h.assertTrue(!ValeConcertQuest.interact(p,site.root),"Spectator cannot activate");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_interaction")
    public static void fullConcertAndReplayNeverCreateEntitiesOrChangeTerrainMysteryOrForcedChunks(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server); ready(data,p.getUUID());
            var before=snapshot(site); var ids=entities(site); var forced=new HashSet<>(site.level.getForcedChunks());
            var mystery=MysteryAdventure.get(p.server).save(new CompoundTag());
            for(int i=0;i<2;i++) { site.click(p,site.root,InteractionHand.MAIN_HAND); ticks(p,440); }
            h.assertTrue(before.equals(snapshot(site)) && ids.equals(entities(site)) && forced.equals(site.level.getForcedChunks())
                    && mystery.equals(MysteryAdventure.get(p.server).save(new CompoundTag())),"No entity duplication, terrain change, forced chunks or Mystery mutation");
        } h.succeed();
    }
    private static Runnable friendCleanup = () -> {};
    @AfterBatch(batch="isolated_concert_friends")
    public static void cleanupFriends(net.minecraft.server.level.ServerLevel level) {
        var cleanup=friendCleanup; friendCleanup=()->{}; cleanup.run();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_concert_friends")
    public static void existingFamilyAndInhabitantsReceiveOnlyPrivateReadOnlyApplause(GameTestHelper h) {
        var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SINGING_MUSHROOM);
        friendCleanup=site::close;
        var a=site.player(); var b=site.player(); a.moveTo(site.root.getX()+.5,161,site.root.getZ()-1.5,0,0); site.level.addNewPlayer(a);
        var friends=new ArrayList<net.minecraft.world.entity.Mob>();
        friendCleanup=()->{friends.forEach(net.minecraft.world.entity.Entity::discard);site.close();};
        for(var kind:Kind.values()) {
            var companion=DudunkaMod.TYPES.get(kind).get().create(site.level);
            companion.initialize(a.getUUID(),site.root.offset(40,0,40)); friends.add(companion);
        }
        friends.add(DudunkaMod.PUFIK.get().create(site.level));
        friends.add(DudunkaMod.WISE_FLY_AGARIC.get().create(site.level));
        friends.add(DudunkaMod.FLUFFY_JUMPER.get().create(site.level));
        friends.add(DudunkaMod.VALE_FIREFLY.get().create(site.level));
        friends.add(DudunkaMod.VALE_SPIDER.get().create(site.level));
        for(var friend:friends) {
            friend.setNoAi(true); friend.setNoGravity(true); friend.moveTo(site.root.getX()+3,161,site.root.getZ()-2,0,0);
            h.assertTrue(site.level.addFreshEntity(friend),"Existing friend fixture enters the level");
        }
        h.startSequence().thenWaitUntil(()->h.assertTrue(site.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(site.root).inflate(12)).containsAll(friends),"Real friends become queryable"))
            .thenExecute(()->{
                var before=new HashMap<UUID,CompoundTag>(); friends.forEach(f->before.put(f.getUUID(),f.saveWithoutId(new CompoundTag())));
                var data=ValeMusicProgress.get(a.server); ready(data,a.getUUID()); site.packets.get(b.getUUID()).clear();
                site.click(a,site.root,InteractionHand.MAIN_HAND); ticks(a,440);
                h.assertTrue(data.concertCompleted(a.getUUID()) && texts(site,a).contains("message.dudunka.concert.nearby"),"Existing friends are acknowledged at the finale");
                h.assertTrue(friends.stream().allMatch(f->before.get(f.getUUID()).equals(f.saveWithoutId(new CompoundTag())))
                        && site.packets.get(b.getUUID()).isEmpty(),"Identity, owners, homes, AI and all saved friend state remain unchanged; applause is private");
                cleanupFriends(site.level);
            }).thenSucceed();
    }

}
