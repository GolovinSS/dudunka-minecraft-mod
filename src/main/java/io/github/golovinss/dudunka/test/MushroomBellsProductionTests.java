package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class MushroomBellsProductionTests {
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void everyLayoutKeepsCluesNotesPrivateAudioAndProgress(GameTestHelper h) {
        for (int revision : new int[]{0,1,2,3}) {
            try (var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MUSIC_BELLS,revision)) {
                var player=site.player(); var observer=site.player();
                var data=ValeMusicProgress.get(player.server);
                var context=StructurePieceSerializationContext.fromLevel(site.level);
                var restored=new ValeAdventurePiece(site.piece.createTag(context));
                h.assertTrue(restored.visualRevision()==revision && restored.plan().equals(site.piece.plan()),
                        "Every old/new saved revision retains its exact blueprint");
                var crystal=revision==3?site.root.above(2):site.root.offset(0,1,-3);
                for (var clue : List.of(site.root,crystal)) {
                    player.moveTo(clue.getX()+.5,clue.getY()+1,clue.getZ()+2.5,0,0);
                    var target=ValeMusicQuest.resolve(site.level,clue);
                    h.assertTrue(target!=null && target.bell()==-1,"Stone body and crystal resolve to the same listening action");
                    site.packets.get(player.getUUID()).clear();
                    h.assertTrue(site.click(player,clue,InteractionHand.MAIN_HAND).isCanceled(),"Listening interaction is consumed");
                    for (int tick=0;tick<24;tick++) ValeMusicQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
                    var pitches=site.packets.get(player.getUUID()).stream().filter(ClientboundSoundPacket.class::isInstance)
                            .map(p->((ClientboundSoundPacket)p).getPitch()).toList();
                    h.assertTrue(pitches.equals(List.of(.75F,1F,1.25F)),"Both clues demonstrate the unchanged three pitches");
                }
                for (int bell=0;bell<3;bell++) {
                    var pos=revision==3?site.piece.bellPosition(bell):site.bell(bell);
                    player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5,0,0);
                    var target=ValeMusicQuest.resolve(site.level,pos);
                    h.assertTrue(target!=null && target.bell()==bell,"Teal=0, rose/violet=1, amber/golden=2");
                    h.assertTrue(site.click(player,pos,InteractionHand.MAIN_HAND).isCanceled(),"All three bells remain interactive");
                }
                h.assertTrue(data.completed(player.getUUID()) && !data.completed(observer.getUUID()),"Puzzle completion remains private");
                h.assertTrue(site.packets.get(observer.getUUID()).isEmpty(),"Another player receives no clue or notes");
                var saved=data.save(new net.minecraft.nbt.CompoundTag());
                h.assertTrue(ValeMusicProgress.load(saved).completed(player.getUUID()),"Old/new completion survives persistence");
                site.click(player,crystal,InteractionHand.MAIN_HAND);
                h.assertTrue(saved.equals(data.save(new net.minecraft.nbt.CompoundTag())),"Replaying cannot revoke progress or add rewards");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void styledIntegrityHintsAndClientAnchorIgnoreOnlyScenery(GameTestHelper h) {
        try (var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MUSIC_BELLS,3)) {
            var center=net.minecraft.world.phys.Vec3.atCenterOf(site.root);
            var anchor=new ValeMusicHints.Anchor(site.root.getX(),site.root.getY(),site.root.getZ());
            var forced=new HashSet<>(site.level.getForcedChunks());
            h.assertTrue(site.piece.anchorPresent(site.level) && site.piece.intact(site.level),"Native styled root and required objects exist");
            var context=StructurePieceSerializationContext.fromLevel(site.level);
            var savedStart=site.chunk.getStartForStructure(site.structure).createTag(context,new ChunkPos(site.root));
            site.piece.postProcess(site.level,site.level.structureManager(),site.level.getChunkSource().getGenerator(),
                    net.minecraft.util.RandomSource.create(1),site.piece.getBoundingBox(),new ChunkPos(site.root),site.root);
            h.assertTrue(savedStart.equals(site.chunk.getStartForStructure(site.structure).createTag(context,new ChunkPos(site.root)))
                    && site.piece.plan().entrySet().stream().allMatch(e->site.level.getBlockState(e.getKey()).equals(e.getValue())),
                    "Finished chunks never retrofit blocks or rewrite saved starts");
            h.assertTrue(MushroomBellsStyledPreview.musicAnchor(site.level.getBlockState(site.root))
                    && MushroomBellsStyledPreview.musicAnchor(Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
                    "The unchanged hint payload recognizes old and new client roots");
            h.assertTrue(!MushroomBellsStyledPreview.musicAnchor(site.level.getBlockState(site.piece.bellPosition(0))),
                    "An arbitrary styled part cannot masquerade as the client root");
            h.assertTrue(ValeMusicLandmarkHints.nearby(site.level,center).contains(anchor),"Styled native start provides ducking hints");
            for (var e:site.piece.plan().entrySet()) if (!site.piece.interactionPlan().containsKey(e.getKey()))
                site.level.setBlock(e.getKey(),Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(site.piece.intact(site.level) && ValeMusicLandmarkHints.nearby(site.level,center).contains(anchor),
                    "Ground, ring, cap ornament and plants are optional for playable integrity and ducking");
            for (int bell=0;bell<3;bell++) h.assertTrue(ValeMusicQuest.resolve(site.level,site.piece.bellPosition(bell)).bell()==bell,
                    "Loss of scenery keeps each required bell playable");
            for (var pos:List.of(site.root,site.root.above(2),site.piece.bellPosition(0),site.piece.bellPosition(1),site.piece.bellPosition(2))) {
                var state=site.level.getBlockState(pos);
                site.level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!site.piece.intact(site.level) && !ValeMusicLandmarkHints.nearby(site.level,center).contains(anchor),
                        "A missing required stone/crystal/bell fails integrity and removes ducking hints");
                h.assertTrue(ValeMusicQuest.resolve(site.level,pos)==null,"Changed cells never bind an interaction");
                site.level.setBlock(pos,state,18);
            }
            h.assertTrue(forced.equals(site.level.getForcedChunks()),"Read-only interaction and hints install no chunk tickets");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void approvedSceneLightsShallowGradeAndSignedChunkBoundsPersist(GameTestHelper h) {
        var context=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for (int sign:new int[]{-1,1}) for (int grade:new int[]{0,1}) {
            var root=new BlockPos(sign*16+8,80,sign*16+8);
            int[] ground=new int[169];
            for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) ground[(x+6)*13+z+6]=80-(x<0?grade:0);
            var piece=new ValeAdventurePiece(ValeAdventureStructure.Landmark.MUSIC_BELLS,root,ground);
            h.assertTrue(piece.visualRevision()==3 && ValeAdventurePiece.styledGround(root,ground),"New generation accepts level and one-block grade");
            var approved=MushroomBellsStyledPreview.plan(root);
            h.assertTrue(approved.entrySet().stream().allMatch(e->e.getValue().equals(piece.plan().get(e.getKey()))),
                    "All accepted geometry, moss accents and light states are reused exactly");
            h.assertTrue(piece.plan().values().stream().noneMatch(s->s.is(Blocks.CHISELED_STONE_BRICKS)),"One modeled centerpiece replaces both old pillars");
            var lights=piece.plan().values().stream().map(s->s.getLightEmission()).filter(n->n>0).sorted().toList();
            h.assertTrue(lights.equals(List.of(10,10,10,13)),"Exactly the four accepted lights survive production");
            if (grade==0) h.assertTrue(piece.plan().equals(approved),"Level sites retain the accepted preview's original ground exactly");
            for (int x=-5;x<=5;x++) for (int z=-4;z<=4;z++) {
                var support=piece.plan().get(root.offset(x,-1,z));
                h.assertTrue(x<0 && grade==1 ? support!=null && support.is(Blocks.MOSS_BLOCK) : support==null,
                        "Only lower columns are raised; existing level support is preserved");
            }
            h.assertTrue(piece.plan().keySet().stream().allMatch(p->piece.getBoundingBox().isInside(p)
                    && new ChunkPos(p).equals(new ChunkPos(root))),"Every cell/support stays in its signed owning chunk");
            var restored=new ValeAdventurePiece(piece.createTag(context));
            h.assertTrue(restored.plan().equals(piece.plan()) && restored.interactionPlan().equals(piece.interactionPlan()),
                    "Reload preserves graded support, scene and bindings");
            ground[0]=78;
            h.assertTrue(!ValeAdventurePiece.styledGround(root,ground),"Two-block grade is rejected before new starts are created");
            ground[0]=81;
            h.assertTrue(!ValeAdventurePiece.styledGround(root,ground),"No excavation of higher terrain is permitted");
        }
        h.succeed();
    }
}
