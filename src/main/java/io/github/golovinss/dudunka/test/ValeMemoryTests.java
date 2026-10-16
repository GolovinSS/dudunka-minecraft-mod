package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeMemoryTests {
    private static final int[] DANCE={0,0,1,2,2,3};
    private static void prerequisites(ValeMusicProgress data, UUID owner) {
        for(int bell=0;bell<3;bell++) data.strike(owner,new BlockPos(8,160,8),bell);
        for(int flower:DANCE) data.dance(owner,new BlockPos(8,160,8),flower);
    }
    private static BlockPos symbol(BlockPos root,int index) {
        return switch(index) {
            case 0 -> root.offset(0,6,0);
            case 1 -> root.offset(0,3,-1);
            default -> root.offset(-4,1,-3);
        };
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_memory_interaction")
    public static void exposedListeningBlockIsReachableAndSeparateFromEveryChoice(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MEMORY_STONE)) {
            var listening=site.root.above(); var plan=site.piece.plan(); var p=site.player();
            var data=ValeMusicProgress.get(p.server); prerequisites(data,p.getUUID());
            h.assertTrue(plan.get(listening).is(Blocks.CRYING_OBSIDIAN),"Listening uses the existing bottom-center crying obsidian");
            var target=ValeMemoryQuest.resolve(site.level,listening);
            h.assertTrue(target!=null && target.symbol()==-1 && target.piece().anchor().equals(site.root),
                    "Exposed listening block belongs to the exact saved native monument");
            var choices=Map.of(symbol(site.root,0),0,symbol(site.root,1),1,site.root.offset(0,3,1),1,
                    site.root.offset(-4,1,-3),2,site.root.offset(-4,1,3),2,site.root.offset(4,1,-3),2,site.root.offset(4,1,3),2);
            for(var entry:choices.entrySet()) {
                var choice=ValeMemoryQuest.resolve(site.level,entry.getKey());
                h.assertTrue(!entry.getKey().equals(listening) && choice!=null && choice.symbol()==entry.getValue(),
                        "Top, both side crystals and all four corner lights retain distinct choice symbols");
            }
            h.assertTrue(ValeMemoryQuest.resolve(site.level,site.root)==null
                    && ValeMemoryQuest.resolve(site.level,listening.east())==null,"Hidden root and neighboring monument blocks are not listening targets");
            // Reproduce the open approach required by native clearSite(), preserving every blueprint block.
            for(var pos:BlockPos.betweenClosed(site.root.offset(-1,0,-3),site.root.offset(1,2,3))) if(!plan.containsKey(pos)) {
                site.blocks.putIfAbsent(pos.immutable(),site.level.getBlockState(pos)); site.level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
            }
            for(int side:new int[]{-1,1}) {
                var approach=site.root.offset(0,0,side*3); var floor=approach.below();
                site.blocks.putIfAbsent(floor,site.level.getBlockState(floor)); site.level.setBlock(floor,Blocks.MOSS_BLOCK.defaultBlockState(),18);
                p.moveTo(Vec3.atBottomCenterOf(approach));
                h.assertTrue(site.level.noCollision(p,p.getBoundingBox()),"Player can stand outside the monument");
                var hit=site.level.clip(new ClipContext(p.getEyePosition(),Vec3.atCenterOf(listening),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                h.assertTrue(hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(listening)
                        && hit.getDirection()==(side<0?Direction.NORTH:Direction.SOUTH) && p.getEyePosition().distanceTo(hit.getLocation())<4.5,
                        "Ordinary player reach and a clear sight line hit the exposed listening face from either side");
                var event=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,listening,hit); ValeMemoryQuest.onUse(event);
                h.assertTrue(event.isCanceled() && data.interpretMemory(p.getUUID(),site.root,0)==ValeMusicProgress.MemoryResult.RETRY
                        && !data.memoryCompleted(p.getUUID()),"Reachable face starts listening without selecting an interpretation");
            }
            var decorative=site.root.offset(3,1,0); site.blocks.putIfAbsent(decorative,site.level.getBlockState(decorative));
            site.level.setBlock(decorative,Blocks.CRYING_OBSIDIAN.defaultBlockState(),18);
            h.assertTrue(ValeMemoryQuest.resolve(site.level,decorative)==null,"Nearby matching blocks cannot masquerade as the listening target");
            h.assertTrue(plan.entrySet().stream().allMatch(e->site.level.getBlockState(e.getKey()).equals(e.getValue())),
                    "Listening leaves every native blueprint block intact");
        } h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void gatesChoiceOnBothPreviousChaptersAndExplicitListening(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var root=new BlockPos(8,160,8);
        h.assertTrue(data.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.NEED_FIRST_NOTE,"Chapter 1 is required");
        for(int bell=0;bell<3;bell++) data.strike(owner,root,bell);
        h.assertTrue(data.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.NEED_DANCE,"Chapter 2 is required");
        for(int flower:DANCE) data.dance(owner,root,flower);
        h.assertTrue(data.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.LISTEN_FIRST,"Player must explicitly hear the memory");
        data.listenToMemory(owner,root);
        h.assertTrue(data.interpretMemory(owner,root.east(),2)==ValeMusicProgress.MemoryResult.LISTEN_FIRST,"Listening is bound to one saved anchor");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void incorrectInterpretationsHintAndCorrectChoiceCanBeRetried(GameTestHelper h) {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var root=new BlockPos(8,160,8); prerequisites(data,owner);
        data.listenToMemory(owner,root);
        h.assertTrue(data.interpretMemory(owner,root,0)==ValeMusicProgress.MemoryResult.RETRY && !data.memoryCompleted(owner),"A bright but solitary moment is a gentle retry");
        h.assertTrue(data.interpretMemory(owner,root,1)==ValeMusicProgress.MemoryResult.RETRY,"Unlimited retries remain available after a wrong symbol");
        h.assertTrue(data.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.COMPLETED && data.memoryCompleted(owner),"Shared light restores the memory");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void memoryListeningAndCompletionArePrivatePerPlayer(GameTestHelper h) {
        var data=new ValeMusicProgress(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var root=new BlockPos(8,160,8);
        prerequisites(data,a); prerequisites(data,b); data.listenToMemory(a,root);
        h.assertTrue(data.interpretMemory(b,root,2)==ValeMusicProgress.MemoryResult.LISTEN_FIRST,"One player's listening does not arm another");
        data.listenToMemory(b,root);
        h.assertTrue(data.interpretMemory(a,root,0)==ValeMusicProgress.MemoryResult.RETRY,"Wrong choice is local to player A");
        h.assertTrue(data.interpretMemory(b,root,2)==ValeMusicProgress.MemoryResult.COMPLETED && !data.memoryCompleted(a),"Player B can complete independently");
        data.listenToMemory(a,root);
        h.assertTrue(data.interpretMemory(a,root,2)==ValeMusicProgress.MemoryResult.COMPLETED,"Player A retains their own retry"); h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void completionPersistsAndReplayIsIdempotent(GameTestHelper h) throws Exception {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var other=UUID.randomUUID(); var root=new BlockPos(8,160,8);
        prerequisites(data,owner); prerequisites(data,other); data.listenToMemory(owner,root); data.listenToMemory(other,root);
        data.interpretMemory(owner,root,2); data.interpretMemory(other,root,0);
        var out=new ByteArrayOutputStream(); NbtIo.writeCompressed(data.save(new CompoundTag()),out);
        var restored=ValeMusicProgress.load(NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray())));
        h.assertTrue(restored.memoryCompleted(owner) && !restored.memoryCompleted(other),"SavedData retains completion for only its UUID");
        h.assertTrue(restored.interpretMemory(owner,root,1)==ValeMusicProgress.MemoryResult.ALREADY_COMPLETE,
                "Saved completion remains valid regardless of the previously accepted interpretation");
        h.assertTrue(restored.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.ALREADY_COMPLETE,"Replay never grants a second completion");
        restored.listenToMemory(owner,root);
        var before=restored.save(new CompoundTag()); restored.setDirty(false);
        h.assertTrue(restored.interpretMemory(owner,root,2)==ValeMusicProgress.MemoryResult.ALREADY_COMPLETE
                && !restored.isDirty() && before.equals(restored.save(new CompoundTag())),"Listening/replaying completed memory does not dirty progress");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_memory_interaction")
    public static void nativeStoneStoryAndChoiceFeedbackStayPrivate(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MEMORY_STONE)) {
            var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server); prerequisites(data,a.getUUID()); prerequisites(data,b.getUUID());
            h.assertTrue(site.click(a,site.root.above(),InteractionHand.MAIN_HAND).isCanceled(),"Only the saved native Memory Stone offers listening");
            for(int tick=0;tick<60;tick++) ValeMemoryQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,a));
            h.assertTrue(site.packets.get(a.getUUID()).stream().filter(ClientboundSoundPacket.class::isInstance).count()>=4,"Memory scene sends soft private chimes");
            h.assertTrue(site.packets.get(a.getUUID()).stream().anyMatch(ClientboundLevelParticlesPacket.class::isInstance),"Firefly-like visual clues are private particles");
            h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"Nearby player receives no private scene feedback");
            site.click(a,symbol(site.root,0),InteractionHand.MAIN_HAND);
            h.assertTrue(!data.memoryCompleted(a.getUUID()),"Incorrect reading does not complete the story");
            site.click(a,site.root.above(),InteractionHand.MAIN_HAND);
            site.click(a,symbol(site.root,2),InteractionHand.MAIN_HAND);
            h.assertTrue(data.memoryCompleted(a.getUUID()) && !data.memoryCompleted(b.getUUID()),"Shared-light interpretation completes only its listener");
        } h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="isolated_memory_interaction")
    public static void damagedOrDecorativeMemorySitesFailClosed(GameTestHelper h) {
        try(var site=new ValeMusicTests.Site(h,ValeAdventureStructure.Landmark.MEMORY_STONE)) {
            var p=site.player(); var choice=symbol(site.root,2); var listening=site.root.above();
            h.assertTrue(ValeMemoryQuest.interact(p,choice) && !ValeMusicProgress.get(p.server).memoryCompleted(p.getUUID()),
                    "A native choice before listening is handled gently but cannot complete the chapter");
            var start=site.chunk.getStartForStructure(site.structure); site.chunk.setStartForStructure(site.structure,net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
            h.assertTrue(!ValeMemoryQuest.interact(p,listening),"Decorative copy without native saved start is inert");
            site.chunk.setStartForStructure(site.structure,start);
            site.level.setBlock(site.root.offset(0,3,1),Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!ValeMemoryQuest.interact(p,listening),"Missing piece disables the complete site without repair");
            site.level.setBlock(site.root.offset(0,3,1),site.piece.plan().get(site.root.offset(0,3,1)),18);
            site.level.setBlock(listening,Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!ValeMemoryQuest.interact(p,listening) && !ValeMemoryQuest.interact(p,choice),
                    "Damaging the exposed listening block disables both story and choices");
            site.level.setBlock(listening,site.piece.plan().get(listening),18);
            site.click(p,listening,InteractionHand.MAIN_HAND);
            site.level.setBlock(choice,Blocks.STONE.defaultBlockState(),18);
            h.assertTrue(!ValeMemoryQuest.interact(p,choice),"Changed symbol block cannot masquerade as native choice");
            site.level.setBlock(choice,site.piece.plan().get(choice),18);
            h.assertTrue(!site.click(p,choice,InteractionHand.OFF_HAND).isCanceled(),"Offhand cannot make a second choice");
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            h.assertTrue(!ValeMemoryQuest.interact(p,choice),"Spectator cannot advance the story");
            h.assertTrue(ValeMemoryQuest.resolve(site.level, new BlockPos(29000000,160,29000000))==null,"Unloaded distant sites are not searched or forced");
        } h.succeed();
    }
}
