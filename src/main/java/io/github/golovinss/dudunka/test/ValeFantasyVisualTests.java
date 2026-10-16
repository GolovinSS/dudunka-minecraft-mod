package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeFantasyVisualTests {
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_fantasy_visual_targets")
    public static void allRevisionsKeepTargetsAndIgnoreFragileDecorations(GameTestHelper h) {
        for(int revision:new int[]{0,1,2}) for(var kind:List.of(ValeAdventureStructure.Landmark.MUSIC_BELLS,ValeAdventureStructure.Landmark.JUMPER_DANCE)) {
            try(var site=new ValeMusicTests.Site(h,kind,revision)) {
                var context=StructurePieceSerializationContext.fromLevel(site.level);
                var restored=new ValeAdventurePiece(site.piece.createTag(context));
                h.assertTrue(restored.visualRevision()==revision && restored.plan().equals(site.piece.plan()),"Every saved revision keeps its exact layout");
                for(var entry:site.piece.plan().entrySet()) if(!site.piece.interactionPlan().containsKey(entry.getKey()))
                    site.level.setBlock(entry.getKey(),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(site.piece.intact(site.level),"Missing decorative ground/plants do not invalidate playable targets");
                var player=site.player();
                h.assertTrue(site.click(player,site.root,InteractionHand.MAIN_HAND).isCanceled(),"Original central clue survives decoration loss");
                if(kind==ValeAdventureStructure.Landmark.MUSIC_BELLS) {
                    h.assertTrue(site.click(player,site.root.offset(0,1,-3),InteractionHand.MAIN_HAND).isCanceled(),"Original listening crystal remains the same clue");
                    for(int bell=0;bell<3;bell++) h.assertTrue(site.click(player,site.bell(bell),InteractionHand.MAIN_HAND).isCanceled(),"Original low stem coordinates remain clickable");
                    h.assertTrue(ValeMusicProgress.get(player.server).completed(player.getUUID()),"Three identities still complete bells");
                } else {
                    var data=ValeMusicProgress.get(player.server);
                    for(int bell=0;bell<3;bell++) data.strike(player.getUUID(),site.root,bell);
                    for(int direction:new int[]{0,0,1,2,2,3}) {
                        var pos=site.root.offset(direction==1?5:direction==3?-5:0,0,direction==0?-5:direction==2?5:0);
                        h.assertTrue(site.click(player,pos,InteractionHand.MAIN_HAND).isCanceled(),"Six original taps stay playable");
                    }
                    h.assertTrue(data.danceCompleted(player.getUUID()),"Six taps retain completion");
                }
                site.level.setBlock(site.root,Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!site.piece.intact(site.level),"Missing real interaction anchor still fails closed");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void compactCrownsRealLightAndNaturalClearing(GameTestHelper h) {
        int[] ground=new int[169]; Arrays.fill(ground,80); var root=new BlockPos(8,80,8);
        var tag=new ValeAdventurePiece(ValeAdventureStructure.Landmark.MUSIC_BELLS,root,ground)
                .createTag(StructurePieceSerializationContext.fromLevel(h.getLevel()));
        tag.putInt("VisualRevision",2);
        var bells=new ValeAdventurePiece(tag);
        long crowns=bells.plan().values().stream().filter(s->s.getBlock() instanceof ValeMusicBell && s.getValue(ValeMusicBell.SEGMENT)==1).count();
        h.assertTrue(crowns==3 && bells.getBoundingBox().maxY()==85,"Three compact modeled crowns fit reduced native bounds");
        h.assertTrue(bells.plan().values().stream().noneMatch(s->s.is(Blocks.MUSHROOM_STEM)),"Thin sculpted stems replace broad white columns");
        var dance=new ValeAdventurePiece(ValeAdventureStructure.Landmark.JUMPER_DANCE,root,ground);
        for(int x:new int[]{-3,3}) for(int z:new int[]{-3,3}) {
            h.assertTrue(!dance.plan().get(root.offset(x,0,z)).is(Blocks.MUSHROOM_STEM)
                    && !dance.plan().containsKey(root.offset(x,1,z)),"No bare corner posts remain");
        }
        for(var state:dance.plan().values()) if(state.is(MushroomValeLandmarks.DANCE_FLOWER.get()))
            h.assertTrue(state.getLightEmission()==10,"Flower illuminates nearby blocks independently of shaders");
        h.succeed();
    }
}
