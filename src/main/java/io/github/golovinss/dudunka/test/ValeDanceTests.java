package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import static io.github.golovinss.dudunka.ValeMusicProgress.DanceResult;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeDanceTests {
    private static final BlockPos ROOT=new BlockPos(8,160,8);
    private static final int[] PATTERN={0,0,1,2,2,3};
    private static void first(ValeMusicProgress data,UUID owner) { for(int i=0;i<3;i++) data.strike(owner,ROOT,i); }
    private static void dance(ValeMusicProgress data,UUID owner) { for(int flower:PATTERN) data.dance(owner,ROOT,flower); }
    private static BlockPos flower(BlockPos root,int id) {
        return root.offset(id==1?5:id==3?-5:0,0,id==0?-5:id==2?5:0);
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void legacyAndPolishedTargetsSurviveNativeReloadAndStayAccessible(GameTestHelper h) {
        for(boolean legacy:new boolean[]{true,false}) {
            try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.JUMPER_DANCE,legacy)) {
                var reloaded=(ValeAdventurePiece)site.chunk.getStartForStructure(site.structure).getPieces().get(0);
                h.assertTrue(reloaded.plan().equals(site.piece.plan()) && reloaded.intact(site.level),"Native reload retains full dance structure");
                for(int i=0;i<4;i++) {
                    var pos=flower(site.root,i); var target=ValeDanceQuest.resolve(site.level,pos);
                    h.assertTrue(target!=null && target.flower()==i,"Old and new cardinal flowers bind after native reload");
                    if(!legacy) h.assertTrue(site.level.getBlockState(pos).getValue(ValeDanceFlower.BLOOM)==i,"Four clue directions have four recognizable blooms");
                    var approach=pos.offset(i==1?-1:i==3?1:0,0,i==0?1:i==2?-1:0);
                    h.assertTrue(!site.piece.plan().containsKey(approach) && !site.piece.plan().containsKey(approach.above()),"Inside approach has standing/head space");
                    h.assertTrue(site.level.getBlockState(pos).getCollisionShape(site.level,pos).isEmpty()
                            && !site.level.getBlockState(pos).getShape(site.level,pos).isEmpty(),"Bloom can be clicked and walked through");
                }
                h.assertTrue(ValeDanceQuest.resolve(site.level,site.root).flower()==-1,"Central clue remains accessible");
                int voice=0;
                for(var entry:site.piece.plan().entrySet()) {
                    var target=ValeDanceQuest.resolve(site.level,entry.getKey());
                    if(target!=null && target.flower()>=0) h.assertTrue(target.voice()==voice++,"Sequential ring voices survive reload");
                }
                h.assertTrue(voice>4,"Whole ring stays interactive");
                if(!legacy) {
                    var pos=flower(site.root,0); var expected=site.level.getBlockState(pos);
                    site.level.setBlock(pos,expected.setValue(ValeDanceFlower.BLOOM,1),18);
                    h.assertTrue(ValeDanceQuest.resolve(site.level,pos)==null,"Wrong bloom variant cannot impersonate target");
                    site.level.setBlock(pos,expected,18);
                }
            }
            try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MUSIC_BELLS,legacy)) {
                var reloaded=(ValeAdventurePiece)site.chunk.getStartForStructure(site.structure).getPieces().get(0);
                h.assertTrue(reloaded.intact(site.level),"Native reload retains full bell structure");
                for(int i=0;i<3;i++) {
                    var pos=site.bell(i); var target=ValeMusicQuest.resolve(site.level,pos);
                    h.assertTrue(target!=null && target.bell()==i,"Each low stem remains the same bell interaction");
                    var approach=pos.north();
                    h.assertTrue(!site.piece.plan().containsKey(approach) && !site.piece.plan().containsKey(approach.above()),"Each stem has a clear nearby standing position");
                }
                h.assertTrue(ValeMusicQuest.resolve(site.level,site.root.offset(0,1,-3)).bell()==-1,"Crystal clue stays interactive");
                for(var entry:site.piece.plan().entrySet()) for(int i=0;i<3;i++) {
                    if(!entry.getValue().is(MushroomValeLandmarks.BLOCKS.get(new String[]{"turquoise","violet","golden"}[i]+"_landmark_cap").get())
                            && !entry.getValue().is(MushroomValeLandmarks.MUSIC_BELLS.get(new String[]{"turquoise","violet","golden"}[i]).get())) continue;
                    var target=ValeMusicQuest.resolve(site.level,entry.getKey());
                    h.assertTrue(target!=null && target.bell()==i,"Every crown/clapper binds to its own bell");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void allPatternsAndFreeRetry(GameTestHelper h) {
        // All 4^6 combinations; only the repeated tap groups complete.
        for(int encoded=0;encoded<4096;encoded++) {
            var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); first(data,owner);
            int code=encoded; boolean expected=true;
            for(int step=0;step<6;step++) { int value=code%4; code/=4; expected &= value==PATTERN[step]; data.dance(owner,ROOT,value); }
            h.assertTrue(data.danceCompleted(owner)==expected,"Only north/north/east/south/south/west completes");
        }
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); first(data,owner);
        data.dance(owner,ROOT,0); h.assertTrue(data.dance(owner,ROOT,4)==DanceResult.RETRY,"Other flowers allow a gentle retry");
        dance(data,owner); h.assertTrue(data.danceCompleted(owner),"Immediate retry works"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void chapterOrderAndIndependentAttempts(GameTestHelper h) {
        var data=new ValeMusicProgress(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        for(int f:PATTERN) h.assertTrue(data.dance(a,ROOT,f)==DanceResult.NEED_FIRST_NOTE,"Chapter one is required");
        h.assertTrue(!data.danceCompleted(a),"No early completion"); first(data,a); first(data,b);
        data.dance(a,ROOT,0); data.dance(b,ROOT,0); data.resetDanceAttempt(b);
        for(int i=1;i<6;i++) data.dance(a,ROOT,PATTERN[i]);
        h.assertTrue(data.danceCompleted(a) && !data.danceCompleted(b),"Reset cannot affect another UUID");
        data.dance(b,ROOT,0); data.dance(b,ROOT,0);
        h.assertTrue(data.dance(b,ROOT.east(160),1)==DanceResult.RETRY,"Landmarks cannot share partial rhythm");
        var c=UUID.randomUUID(); data.strike(c,ROOT,0); data.resetDanceAttempt(c);
        h.assertTrue(data.strike(c,ROOT,1)==ValeMusicProgress.Result.CORRECT,"Dance reset preserves bell attempt"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void savedProgressReplayAndDuplicateRows(GameTestHelper h) throws Exception {
        var data=new ValeMusicProgress(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); first(data,a); first(data,b);
        dance(data,a); data.dance(b,ROOT,0);
        var out=new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(data.save(new CompoundTag()),out);
        var restored=ValeMusicProgress.load(NbtIo.readCompressed(new java.io.ByteArrayInputStream(out.toByteArray())));
        h.assertTrue(restored.completed(a) && restored.danceCompleted(a) && !restored.danceCompleted(b),"Both chapters persist by UUID");
        restored.dance(b,ROOT,0);
        h.assertTrue(restored.dance(b,ROOT,1)==DanceResult.RETRY,"Partial rhythm was not serialized");
        var before=restored.save(new CompoundTag()); restored.setDirty(false);
        for(int f:PATTERN) h.assertTrue(restored.dance(a,ROOT,f)==DanceResult.ALREADY_COMPLETE,"Replay is idempotent");
        h.assertTrue(!restored.isDirty() && before.equals(restored.save(new CompoundTag())),"Replay gives no duplicate progress or reward");
        var rows=before.getList("Owners",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) if(rows.getCompound(i).getUUID("Owner").equals(a)) {
            var duplicate=rows.getCompound(i).copy(); duplicate.getCompound("Chapters").getCompound(ValeMusicProgress.DANCE).putBoolean("Completed",false);
            rows.add(duplicate); break;
        }
        h.assertTrue(ValeMusicProgress.load(before).danceCompleted(a),"Duplicate rows cannot revoke dance"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void nativeCirclePrivateFeedbackAndNoEntityRequirement(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.JUMPER_DANCE)) {
            var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server); first(data,a.getUUID()); first(data,b.getUUID());
            var before=site.piece.plan(); var forced=new HashSet<>(site.level.getForcedChunks()); var mystery=MysteryAdventure.get(a.server).save(new CompoundTag());
            site.click(b,flower(site.root,0),InteractionHand.MAIN_HAND);
            site.packets.get(b.getUUID()).clear();
            for(int f:PATTERN) h.assertTrue(site.click(a,flower(site.root,f),InteractionHand.MAIN_HAND).isCanceled(),"Existing flowers are interactive");
            h.assertTrue(data.danceCompleted(a.getUUID()) && !data.danceCompleted(b.getUUID()),"No Marusya or jumpers needed; progress is private");
            h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"No feedback leaks to other player");
            h.assertTrue(site.packets.get(a.getUUID()).stream().anyMatch(p->p instanceof ClientboundLevelParticlesPacket),"Visible feedback");
            h.assertTrue(before.entrySet().stream().allMatch(e->site.level.getBlockState(e.getKey()).equals(e.getValue()))
                    && forced.equals(site.level.getForcedChunks()) && mystery.equals(MysteryAdventure.get(a.server).save(new CompoundTag())),"Blocks, tickets and Mystery unchanged");
            // Listening by A cannot reset B's partial dance.
            site.click(a,site.root,InteractionHand.MAIN_HAND);
            for(int i=1;i<6;i++) site.click(b,flower(site.root,PATTERN[i]),InteractionHand.MAIN_HAND);
            h.assertTrue(data.danceCompleted(b.getUUID()),"Another player's clue cannot reset progress");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void clueShowsTapGroupsAndEveryFlowerHasDistinctVoice(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.JUMPER_DANCE)) {
            var p=site.player(); site.click(p,site.root,InteractionHand.MAIN_HAND);
            for(int tick=0;tick<100;tick++) ValeDanceQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
            var pitches=site.packets.get(p.getUUID()).stream().filter(x->x instanceof ClientboundSoundPacket).map(x->((ClientboundSoundPacket)x).getPitch()).toList();
            h.assertTrue(pitches.size()==6 && pitches.get(0).equals(pitches.get(1)) && pitches.get(3).equals(pitches.get(4))
                    && new HashSet<>(pitches).size()==4,"Clue demonstrates two/one/two/one spatial groups");
            var voices=new HashSet<Integer>(); int count=0;
            for(var pos:site.piece.plan().keySet()) { var target=ValeDanceQuest.resolve(site.level,pos);
                if(target!=null && target.flower()>=0) { count++; voices.add(target.voice()); }
            }
            h.assertTrue(count>4 && voices.size()==count,"Every ring flower has a distinct note");
            h.assertTrue(!ValeMusicProgress.get(p.server).danceCompleted(p.getUUID()),"Listening cannot grant completion");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void rejectsCounterfeitsAndResetsOnlyTransientState(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.JUMPER_DANCE)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server); first(data,p.getUUID()); var pos=flower(site.root,0);
            h.assertTrue(!site.click(p,pos,InteractionHand.OFF_HAND).isCanceled(),"Offhand cannot double-tap");
            var start=site.chunk.getStartForStructure(site.structure); site.chunk.setStartForStructure(site.structure,net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
            h.assertTrue(!ValeDanceQuest.interact(p,pos),"Decorative rings without native starts rejected"); site.chunk.setStartForStructure(site.structure,start);
            site.level.setBlock(pos,Blocks.STONE.defaultBlockState(),18); h.assertTrue(!ValeDanceQuest.interact(p,pos),"Replaced flower rejected");
            site.level.setBlock(pos,site.piece.plan().get(pos),18);
            site.click(p,pos,InteractionHand.MAIN_HAND); ValeDanceQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            for(int i=1;i<6;i++) site.click(p,flower(site.root,PATTERN[i]),InteractionHand.MAIN_HAND);
            h.assertTrue(!data.danceCompleted(p.getUUID()) && data.completed(p.getUUID()),"Logout resets rhythm, preserves first chapter");
            for(int f:PATTERN) site.click(p,flower(site.root,f),InteractionHand.MAIN_HAND);
            ValeDanceQuest.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p,MushroomVale.LEVEL,net.minecraft.world.level.Level.OVERWORLD));
            h.assertTrue(data.danceCompleted(p.getUUID()),"Dimension change preserves completion");
            site.level.setBlock(site.root,Blocks.AIR.defaultBlockState(),18); h.assertTrue(!ValeDanceQuest.interact(p,pos),"Missing root disables circle");
            var remote=new BlockPos(29000000,160,29000000);
            h.assertTrue(ValeDanceQuest.resolve(site.level,remote)==null && site.level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null,"No forced loading");
        } h.succeed();
    }
}
