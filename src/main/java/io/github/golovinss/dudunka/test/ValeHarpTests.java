package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import static io.github.golovinss.dudunka.ValeMusicProgress.HarpResult;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeHarpTests {
    private static final BlockPos ROOT = new BlockPos(8,160,8);
    private static final int[] HARMONY = {0,2,1};
    private static void prerequisites(ValeMusicProgress data, UUID owner) {
        for (int bell=0; bell<3; bell++) data.strike(owner,ROOT,bell);
        for (int flower : new int[]{0,0,1,2,2,3}) data.dance(owner,ROOT,flower);
        data.listenToMemory(owner,ROOT); data.interpretMemory(owner,ROOT,2);
    }
    private static void tune(ValeMusicProgress data, UUID owner, BlockPos root, int string, int tone) {
        while (data.harpTone(owner,root,string)!=tone) data.tuneHarp(owner,root,string);
    }
    private static void harmony(ValeMusicProgress data, UUID owner, BlockPos root) {
        for (int string : new int[]{2,0,1}) tune(data,owner,root,string,HARMONY[string]);
    }
    private static BlockPos string(BlockPos root, int string) { return root.offset((string-1)*2,1,0); }
    private static Map<BlockPos,BlockState> snapshot(ValeMusicTests.Site site) {
        var blocks = new LinkedHashMap<BlockPos,BlockState>();
        BlockPos.betweenClosed(site.root.offset(-6,-2,-6),site.root.offset(6,11,6))
                .forEach(pos -> blocks.put(pos.immutable(),site.level.getBlockState(pos)));
        return blocks;
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void allToneCombinationsNeedExplicitHarmonyCheck(GameTestHelper h) {
        for (int a=0;a<3;a++) for (int b=0;b<3;b++) for (int c=0;c<3;c++) {
            var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); prerequisites(data,owner);
            // Reverse order deliberately: this is tuning, not sequence repetition.
            tune(data,owner,ROOT,2,c); tune(data,owner,ROOT,1,b); tune(data,owner,ROOT,0,a);
            h.assertTrue(!data.harpCompleted(owner),"Tuning alone never completes the chapter");
            var result=data.playHarp(owner,ROOT);
            h.assertTrue(result==(a==0 && b==2 && c==1 ? HarpResult.COMPLETED : HarpResult.RETRY),
                    "Only the harmonious combination restores the voice");
        } h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void allThreeChaptersGateBothTuningAndPlaying(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID();
        for (var gate : new HarpResult[]{HarpResult.NEED_FIRST_NOTE,HarpResult.NEED_DANCE,HarpResult.NEED_MEMORY}) {
            h.assertTrue(data.tuneHarp(owner,ROOT,0)==gate && data.playHarp(owner,ROOT)==gate
                    && data.harpTone(owner,ROOT,0)==1 && !data.harpCompleted(owner),"Locked harp cannot tune or complete");
            if (gate==HarpResult.NEED_FIRST_NOTE) for(int bell=0;bell<3;bell++) data.strike(owner,ROOT,bell);
            if (gate==HarpResult.NEED_DANCE) for(int flower:new int[]{0,0,1,2,2,3}) data.dance(owner,ROOT,flower);
        }
        data.listenToMemory(owner,ROOT); data.interpretMemory(owner,ROOT,2);
        h.assertTrue(data.tuneHarp(owner,ROOT,0)==HarpResult.TUNED,"All previous melodies unlock the harp"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void incorrectChordPreservesEveryToneAndAllowsImmediateRetry(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); prerequisites(data,owner);
        tune(data,owner,ROOT,0,0);
        for(int retry=0;retry<5;retry++) h.assertTrue(data.playHarp(owner,ROOT)==HarpResult.RETRY
                && data.harpTone(owner,ROOT,0)==0 && data.harpTone(owner,ROOT,1)==1
                && data.harpTone(owner,ROOT,2)==1,"Wrong chord keeps all independent choices without penalty");
        data.tuneHarp(owner,ROOT,1);
        h.assertTrue(data.playHarp(owner,ROOT)==HarpResult.COMPLETED,"Change only the remaining string to succeed"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void interleavedPlayersAndNativeAnchorsHavePrivateTuning(GameTestHelper h) {
        var data=new ValeMusicProgress(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); prerequisites(data,a); prerequisites(data,b);
        tune(data,a,ROOT,0,0); tune(data,b,ROOT,1,2);
        h.assertTrue(data.playHarp(a,ROOT)==HarpResult.RETRY && data.playHarp(b,ROOT)==HarpResult.RETRY,
                "Players cannot combine their settings on one shared harp");
        tune(data,a,ROOT,1,2);
        h.assertTrue(data.playHarp(a,ROOT)==HarpResult.COMPLETED && !data.harpCompleted(b),"Completion belongs only to the performer");
        tune(data,b,ROOT.east(160),0,0);
        h.assertTrue(data.playHarp(b,ROOT.east(160))==HarpResult.RETRY
                && data.harpTone(b,ROOT.east(160),1)==1,"Cannot splice strings from different native landmarks"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void compressedPersistenceAndCompletedRetuningAreIdempotent(GameTestHelper h) throws Exception {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var other=UUID.randomUUID(); prerequisites(data,owner); prerequisites(data,other);
        harmony(data,owner,ROOT); data.playHarp(owner,ROOT); harmony(data,other,ROOT);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(data.save(new CompoundTag()),bytes);
        var restored=ValeMusicProgress.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
        h.assertTrue(restored.harpCompleted(owner) && !restored.harpCompleted(other),"Only UUID-scoped completion survives disk codec");
        h.assertTrue(restored.playHarp(other,ROOT)==HarpResult.RETRY,"Unfinished tuning is transient");
        var before=restored.save(new CompoundTag()); restored.setDirty(false);
        harmony(restored,owner,ROOT);
        h.assertTrue(restored.playHarp(owner,ROOT)==HarpResult.ALREADY_COMPLETE,"Correct replay grants no duplicate completion");
        restored.tuneHarp(owner,ROOT,0);
        h.assertTrue(restored.playHarp(owner,ROOT)==HarpResult.RETRY && restored.harpCompleted(owner),"Completed players can freely retune without losing completion");
        h.assertTrue(!restored.isDirty() && before.equals(restored.save(new CompoundTag())),"Replay never mutates persistent progress"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void duplicateAndFutureRowsPreserveHarpCompletion(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); prerequisites(data,owner); harmony(data,owner,ROOT); data.playHarp(owner,ROOT);
        var saved=data.save(new CompoundTag()); saved.putString("FutureRoot","kept"); saved.putInt("Version",42);
        var rows=saved.getList("Owners",Tag.TAG_COMPOUND); var row=rows.getCompound(0);
        row.getCompound("Chapters").getCompound(ValeMusicProgress.HARP).putString("FutureHarp","kept");
        var duplicate=row.copy(); duplicate.getCompound("Chapters").getCompound(ValeMusicProgress.HARP).putString("Completed","true"); rows.add(duplicate);
        var restored=ValeMusicProgress.load(saved); var result=restored.save(new CompoundTag());
        h.assertTrue(restored.harpCompleted(owner) && result.getInt("Version")==42 && result.getString("FutureRoot").equals("kept")
                && result.getList("Owners",Tag.TAG_COMPOUND).getCompound(0).getCompound("Chapters").getCompound(ValeMusicProgress.HARP)
                    .getString("FutureHarp").equals("kept"),"Duplicate rows cannot revoke completion or erase future metadata");
        var malformed=new CompoundTag(); malformed.put("Owners",new ListTag());
        var bad=row.copy(); bad.getCompound("Chapters").getCompound(ValeMusicProgress.HARP).putString("Completed","true");
        malformed.getList("Owners",Tag.TAG_COMPOUND).add(bad);
        h.assertTrue(!ValeMusicProgress.load(malformed).harpCompleted(owner),"String completion cannot unlock chapter 4"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_harp_interaction")
    public static void nativeInteractionsArePrivateAndChangeNoSharedWorldState(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SILVER_WEB_HARP)) {
            var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server); prerequisites(data,a.getUUID()); prerequisites(data,b.getUUID());
            var before=snapshot(site); var forced=new HashSet<>(site.level.getForcedChunks()); var mystery=MysteryAdventure.get(a.server).save(new CompoundTag());
            site.click(a,site.root,InteractionHand.MAIN_HAND);
            h.assertTrue(!data.harpCompleted(a.getUUID()),"Initial middle tones produce a gentle retry");
            site.click(a,string(site.root,1),InteractionHand.MAIN_HAND);
            site.click(b,string(site.root,0),InteractionHand.MAIN_HAND);
            site.click(a,string(site.root,0),InteractionHand.MAIN_HAND); site.click(a,string(site.root,0),InteractionHand.MAIN_HAND);
            site.packets.get(b.getUUID()).clear();
            h.assertTrue(!data.harpCompleted(a.getUUID()),"Explicit center playback remains required");
            h.assertTrue(site.click(a,site.root,InteractionHand.MAIN_HAND).isCanceled() && data.harpCompleted(a.getUUID())
                    && !data.harpCompleted(b.getUUID()),"Actual native subscriber completes without any NPC");
            h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"Another player receives no private chord, hint or particles");
            h.assertTrue(site.packets.get(a.getUUID()).stream().anyMatch(ClientboundSoundPacket.class::isInstance)
                    && site.packets.get(a.getUUID()).stream().anyMatch(ClientboundLevelParticlesPacket.class::isInstance)
                    && site.packets.get(a.getUUID()).stream().anyMatch(ClientboundSystemChatPacket.class::isInstance),"Text, sound and countable particles are sent to the performer");
            h.assertTrue(before.equals(snapshot(site)) && forced.equals(site.level.getForcedChunks())
                    && mystery.equals(MysteryAdventure.get(a.server).save(new CompoundTag())),"Entire landmark volume, forced chunks and Mystery data stay unchanged");
            var saved=data.save(new CompoundTag()); data.setDirty(false); site.click(a,site.root,InteractionHand.MAIN_HAND);
            h.assertTrue(!data.isDirty() && saved.equals(data.save(new CompoundTag())),"Native replay is idempotent");
        } h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_harp_interaction")
    public static void everyDamagedBlueprintBlockDisablesHarpWithoutRepair(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SILVER_WEB_HARP)) {
            var p=site.player(); prerequisites(ValeMusicProgress.get(p.server),p.getUUID());
            for(var entry:site.piece.plan().entrySet()) {
                site.level.setBlock(entry.getKey(),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!ValeHarpQuest.interact(p,site.root) && site.level.getBlockState(entry.getKey()).isAir(),
                        "Any missing blueprint block disables interaction without automatic repair");
                site.level.setBlock(entry.getKey(),entry.getValue(),18);
            }
            var pane=string(site.root,0); var paneState=site.piece.plan().get(pane);
            site.level.setBlock(pane,paneState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true),18);
            h.assertTrue(ValeHarpQuest.resolve(site.level,site.root)!=null,"Ordinary pane connection changes do not disable the harp");
            site.level.setBlock(pane,paneState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),18);
            h.assertTrue(ValeHarpQuest.resolve(site.level,site.root)==null,"Waterlogged strings disable the harp safely");
            site.level.setBlock(string(site.root,0),Blocks.COBWEB.defaultBlockState(),18);
            h.assertTrue(!ValeHarpQuest.interact(p,string(site.root,0)),"A replacement cobweb is not a tuning anchor");
            h.assertTrue(!ValeMusicProgress.get(p.server).harpCompleted(p.getUUID()),"Damaged sites never grant completion");
        } h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_harp_interaction")
    public static void decorativeCopiesOffhandSpectatorsAndRemotePositionsAreInert(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SILVER_WEB_HARP)) {
            var p=site.player(); var start=site.chunk.getStartForStructure(site.structure);
            site.chunk.setStartForStructure(site.structure,StructureStart.INVALID_START);
            h.assertTrue(!ValeHarpQuest.interact(p,site.root),"Blueprint without saved native start is decorative");
            site.chunk.setStartForStructure(site.structure,start);
            h.assertTrue(!site.click(p,string(site.root,0),InteractionHand.OFF_HAND).isCanceled(),"Offhand never cycles a string");
            h.assertTrue(ValeHarpQuest.resolve(site.level,site.root.offset(-1,1,0))==null,"Other glass columns remain decorative");
            h.assertTrue(ValeHarpQuest.resolve(h.getLevel(),site.root)==null,"Wrong dimension cannot bind harp");
            h.assertTrue(ValeHarpQuest.resolve(site.level,new BlockPos(29000000,160,29000000))==null
                    && site.level.getChunkSource().getChunkNow(29000000>>4,29000000>>4)==null,"Distant resolver never loads chunks");
            p.moveTo(site.root.getX()+30,160,site.root.getZ(),0,0);
            h.assertTrue(!ValeHarpQuest.interact(p,site.root),"Remote interaction is rejected");
            p.moveTo(site.root.getX()+.5,161,site.root.getZ()+.5,0,0); p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            h.assertTrue(!ValeHarpQuest.interact(p,site.root),"Spectator cannot tune or complete");
        } h.succeed();
    }

    private static Runnable spiderCleanup = () -> {};
    @AfterBatch(batch="isolated_harp_spiders")
    public static void cleanupSpiderScene(net.minecraft.server.level.ServerLevel level) {
        var cleanup=spiderCleanup; spiderCleanup=()->{}; cleanup.run();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_harp_spiders")
    public static void optionalRealSpidersReactPrivatelyAndLogoutClearsOnlyTransientTuning(GameTestHelper h) {
        var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.SILVER_WEB_HARP);
        spiderCleanup=site::close;
        var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server); prerequisites(data,a.getUUID());
        // A real observer makes this chunk's entity sections accessible, as in normal gameplay.
        a.moveTo(site.root.getX()+.5,161,site.root.getZ()-1.5,0,0); site.level.addNewPlayer(a);
        var spider=DudunkaMod.VALE_SPIDER.get().create(site.level);
        h.assertTrue(spider!=null,"Peaceful mushroom spider type exists");
        spider.setNoAi(true); spider.setNoGravity(true);
        spider.moveTo(site.root.getX()+3,161,site.root.getZ()+2,0,0);
        h.assertTrue(site.level.addFreshEntity(spider),"Real spider fixture is accepted by the level");
        spiderCleanup=()->{spider.discard();site.close();};
        h.startSequence().thenWaitUntil(()->h.assertTrue(site.level.getEntitiesOfClass(ValeSpider.class,
                new net.minecraft.world.phys.AABB(site.root).inflate(8)).contains(spider),
                "Real spider fixture must become visible in loaded entity sections"))
            .thenExecute(()->{
                int emote=spider.emote(); harmony(data,a.getUUID(),site.root);
                site.click(a,site.root,InteractionHand.MAIN_HAND);
                h.assertTrue(data.harpCompleted(a.getUUID()) && spider.emote()==emote && spider.getTarget()==null,
                        "Optional applause never changes shared spider AI or social state");
                h.assertTrue(site.packets.get(a.getUUID()).stream().filter(ClientboundSystemChatPacket.class::isInstance)
                        .map(ClientboundSystemChatPacket.class::cast).anyMatch(packet->packet.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                                && text.getKey().equals("message.dudunka.harp.spiders")),
                        "Present real spiders receive a narrative acknowledgement");
                h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"Optional spider feedback stays private");
                var saved=data.save(new CompoundTag());
                ValeHarpQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(a));
                h.assertTrue(data.harpTone(a.getUUID(),site.root,0)==1 && data.harpCompleted(a.getUUID())
                        && saved.equals(data.save(new CompoundTag())),"Logout clears transient tones while retaining persistent completion");
                cleanupSpiderScene(site.level);
            }).thenSucceed();
    }
}
