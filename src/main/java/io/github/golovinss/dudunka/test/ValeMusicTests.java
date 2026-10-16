package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.gametest.*;
import static io.github.golovinss.dudunka.ValeMusicProgress.Result;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeMusicTests {
    private static final BlockPos ROOT = new BlockPos(8, 160, 8);
    @GameTest(template="empty", timeoutTicks=40)
    public static void validatesAllOrdersAndImmediateRetry(GameTestHelper h) {
        for (int a=0; a<3; a++) for (int b=0; b<3; b++) for (int c=0; c<3; c++) {
            var data=new ValeMusicProgress(); var owner=UUID.randomUUID();
            data.strike(owner,ROOT,a); data.strike(owner,ROOT,b); data.strike(owner,ROOT,c);
            h.assertTrue(data.completed(owner)==(a==0 && b==1 && c==2),"Only the three-note melody completes");
        }
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID();
        h.assertTrue(data.strike(owner,ROOT,0)==Result.CORRECT && data.strike(owner,ROOT,2)==Result.RETRY,
                "Wrong second note resets gently");
        for (int bell=0; bell<3; bell++) data.strike(owner,ROOT,bell);
        h.assertTrue(data.completed(owner),"Immediate retry needs no timer, item or punishment"); h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void multiplayerAndLandmarksHaveIndependentAttempts(GameTestHelper h) {
        var data=new ValeMusicProgress(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        data.strike(a,ROOT,0); data.strike(b,ROOT,0); data.strike(a,ROOT,1);
        h.assertTrue(data.strike(b,ROOT,2)==Result.RETRY && data.strike(a,ROOT,2)==Result.COMPLETED,
                "One player's mistake cannot reset another player's tune");
        h.assertTrue(!data.completed(b),"Completion belongs only to the performer");
        data.strike(b,ROOT,0);
        h.assertTrue(data.strike(b,ROOT.east(160),1)==Result.RETRY,"Cannot splice notes from different landmarks");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void completionPersistsThroughCompressedNbtAndIsIdempotent(GameTestHelper h) throws Exception {
        var data=new ValeMusicProgress(); var owner=UUID.randomUUID(); var other=UUID.randomUUID();
        for (int bell=0; bell<3; bell++) data.strike(owner,ROOT,bell);
        data.strike(other,ROOT,0);
        var bytes=new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(data.save(new CompoundTag()),bytes);
        var restored=ValeMusicProgress.load(NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        h.assertTrue(restored.completed(owner) && !restored.completed(other),"Disk codec preserves UUID-specific completion");
        h.assertTrue(restored.strike(other,ROOT,1)==Result.RETRY,"An incomplete attempt is never persisted");
        var before=restored.save(new CompoundTag()); restored.setDirty(false);
        for (int bell=0; bell<3; bell++) h.assertTrue(restored.strike(owner,ROOT,bell)==Result.ALREADY_COMPLETE,"Completion is idempotent");
        h.assertTrue(!restored.isDirty() && before.equals(restored.save(new CompoundTag())),"Replay creates no extra reward or save mutation");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void futureChaptersAndUnknownFieldsSurviveAndMalformedRowsCannotComplete(GameTestHelper h) {
        var owner=UUID.randomUUID(); var root=new CompoundTag(); root.putInt("Version",42); root.putString("FutureRoot","kept");
        var rows=new ListTag(); var row=new CompoundTag(); row.putUUID("Owner",owner); row.putString("FutureOwner","kept");
        var chapters=new CompoundTag(); var first=new CompoundTag(); first.putString("Completed","true");
        chapters.put(ValeMusicProgress.FIRST_NOTE,first); var future=new CompoundTag(); future.putInt("FuturePuzzle",17);
        chapters.put("dudunka:singing_mushroom",future); row.put("Chapters",chapters); rows.add(row);
        var unknown=new CompoundTag(); unknown.putString("FutureIdentity","kept"); rows.add(unknown); root.put("Owners",rows);
        var data=ValeMusicProgress.load(root); h.assertTrue(!data.completed(owner),"Wrong completion type never unlocks the chapter");
        for(int bell=0;bell<3;bell++) data.strike(owner,ROOT,bell);
        var saved=data.save(new CompoundTag()); var savedRow=saved.getList("Owners",Tag.TAG_COMPOUND).getCompound(0);
        h.assertTrue(saved.getInt("Version")==42 && saved.getString("FutureRoot").equals("kept")
                && savedRow.getString("FutureOwner").equals("kept")
                && savedRow.getCompound("Chapters").getCompound("dudunka:singing_mushroom").equals(future)
                && saved.getList("Owners",Tag.TAG_COMPOUND).contains(unknown),"Unknown chapter/owner/root fields and future version survive");
        var duplicate=savedRow.copy(); duplicate.getCompound("Chapters").getCompound(ValeMusicProgress.FIRST_NOTE).putBoolean("Completed",false);
        saved.getList("Owners",Tag.TAG_COMPOUND).add(duplicate);
        h.assertTrue(ValeMusicProgress.load(saved).completed(owner),"Duplicate rows cannot revoke completion"); h.succeed();
    }

    /** Synthetic native start and Stage 4.1 blueprint, installed only in an isolated test world. */
    static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos root;
        final ValeAdventurePiece piece;
        final net.minecraft.world.level.chunk.LevelChunk chunk;
        final Structure structure;
        final StructureStart oldStart;
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final List<ServerPlayer> players=new ArrayList<>();
        final Map<UUID,List<Packet<?>>> packets=new HashMap<>();
        Site(GameTestHelper h) { this(h, ValeAdventureStructure.Landmark.MUSIC_BELLS); }
        Site(GameTestHelper h, ValeAdventureStructure.Landmark landmark) { this(h,landmark,false); }
        Site(GameTestHelper h, ValeAdventureStructure.Landmark landmark, boolean legacy) { this(h,landmark,legacy?0:2); }
        Site(GameTestHelper h, ValeAdventureStructure.Landmark landmark, int revision) {
            level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
            var p=h.absolutePos(BlockPos.ZERO);
            root=new BlockPos((p.getX()>>4)*16+8,160,(p.getZ()>>4)*16+8);
            chunk=level.getChunk(root.getX()>>4,root.getZ()>>4);
            structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("dudunka",landmark.id));
            oldStart=chunk.getStartForStructure(structure);
            int[] ground=new int[169]; Arrays.fill(ground,root.getY());
            var generated=new ValeAdventurePiece(landmark,root,ground);
            if(revision!=generated.visualRevision()) {
                var tag=generated.createTag(StructurePieceSerializationContext.fromLevel(level));
                if(revision==0) tag.remove("VisualRevision"); else tag.putInt("VisualRevision",revision);
                if (revision<2) {
                    var bounds=tag.getIntArray("BB"); bounds[4]=root.getY()+11; tag.putIntArray("BB",bounds);
                }
                piece=new ValeAdventurePiece(tag);
            } else piece=generated;
            piece.plan().forEach((pos,state)->{ blocks.put(pos,level.getBlockState(pos)); level.setBlock(pos,state,18); });
            var start=new StructureStart(structure,new ChunkPos(root),0,new PiecesContainer(List.of(piece)));
            // Exercise the actual persisted native format before interaction.
            var context=StructurePieceSerializationContext.fromLevel(level);
            chunk.setStartForStructure(structure,StructureStart.loadStaticStart(context,start.createTag(context,new ChunkPos(root)),level.getSeed()));
        }
        ServerPlayer player() {
            var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"MusicTest"));
            var out=new ArrayList<Packet<?>>(); packets.put(player.getUUID(),out);
            player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player) {
                @Override public void send(Packet<?> p) { out.add(p); }
                @Override public void send(Packet<?> p, PacketSendListener listener) { send(p); }
            };
            player.moveTo(root.getX()+.5,root.getY()+1,root.getZ()+.5,0,0);
            players.add(player); return player;
        }
        BlockPos bell(int bell) { return root.offset(bell==0?-3:bell==1?0:3,1,bell==0?0:bell==1?2:-1); }
        PlayerInteractEvent.RightClickBlock click(ServerPlayer p,BlockPos pos,InteractionHand hand) {
            var event=new PlayerInteractEvent.RightClickBlock(p,hand,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            ValeMusicQuest.onUse(event); ValeDanceQuest.onUse(event); ValeMemoryQuest.onUse(event); ValeHarpQuest.onUse(event); ValeConcertQuest.onUse(event); return event;
        }
        @Override public void close() {
            for(var p:players) { ValeMusicQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p)); ValeDanceQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p)); ValeMemoryQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p)); ValeHarpQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p)); ValeConcertQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p)); p.discard(); }
            blocks.forEach((p,s)->level.setBlock(p,s,18)); chunk.setStartForStructure(structure,oldStart==null?StructureStart.INVALID_START:oldStart);
        }
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void nativeStemInteractionsCompletePrivatelyWithoutPufikOrFamily(GameTestHelper h) {
        try(var site=new Site(h)) {
            var a=site.player(); var b=site.player(); var data=ValeMusicProgress.get(a.server);
            var before=site.piece.plan(); var forced=new HashSet<>(site.level.getForcedChunks());
            var mystery=MysteryAdventure.get(a.server).save(new CompoundTag());
            h.assertTrue(site.click(a,site.root,InteractionHand.MAIN_HAND).isCanceled(),"Central carved stone discovers clue");
            for(int bell=0;bell<3;bell++) h.assertTrue(site.click(a,site.bell(bell),InteractionHand.MAIN_HAND).isCanceled(),"Native stem is interactive");
            h.assertTrue(data.completed(a.getUUID()) && !data.completed(b.getUUID()),"Actual subscriber completes only performer, with no NPC prerequisite");
            h.assertTrue(site.packets.get(b.getUUID()).isEmpty(),"Other listener receives no quest feedback or sounds");
            h.assertTrue(site.packets.get(a.getUUID()).stream().anyMatch(p->p instanceof ClientboundSoundPacket)
                    && site.packets.get(a.getUUID()).stream().anyMatch(p->p instanceof ClientboundLevelParticlesPacket),"Gentle sound and particles sent");
            h.assertTrue(before.entrySet().stream().allMatch(e->site.level.getBlockState(e.getKey()).equals(e.getValue()))
                    && forced.equals(site.level.getForcedChunks()) && mystery.equals(MysteryAdventure.get(a.server).save(new CompoundTag())),
                    "No shared blocks, forced chunks or Mystery/album progress change");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void markerReplaysThreePitchesAndLogoutResetsOnlyAttempt(GameTestHelper h) {
        try(var site=new Site(h)) {
            var p=site.player(); var data=ValeMusicProgress.get(p.server);
            site.click(p,site.root.offset(0,1,-3),InteractionHand.MAIN_HAND);
            for(int tick=0;tick<24;tick++) ValeMusicQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
            var sounds=site.packets.get(p.getUUID()).stream().filter(packet->packet instanceof ClientboundSoundPacket)
                    .map(packet->((ClientboundSoundPacket)packet).getPitch()).toList();
            h.assertTrue(sounds.equals(List.of(.75F,1F,1.25F)),"Clue audibly demonstrates three ordered distinct notes");
            site.click(p,site.bell(0),InteractionHand.MAIN_HAND);
            ValeMusicQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            site.click(p,site.bell(1),InteractionHand.MAIN_HAND); site.click(p,site.bell(2),InteractionHand.MAIN_HAND);
            h.assertTrue(!data.completed(p.getUUID()),"Logout discards only partial tune");
            for(int bell=0;bell<3;bell++) site.click(p,site.bell(bell),InteractionHand.MAIN_HAND);
            ValeMusicQuest.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(ValeMusicProgress.load(data.save(new CompoundTag())).completed(p.getUUID()),"Logout/reload keeps completed chapter");
        } h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60,batch="isolated_music_interaction")
    public static void rejectsDecorationsMissingStartsChangedBlocksOffhandAndSpectators(GameTestHelper h) {
        try(var site=new Site(h)) {
            var p=site.player(); var pos=site.bell(0);
            h.assertTrue(!site.click(p,pos,InteractionHand.OFF_HAND).isCanceled(),"Offhand never double-advances sequence");
            var start=site.chunk.getStartForStructure(site.structure); site.chunk.setStartForStructure(site.structure,StructureStart.INVALID_START);
            h.assertTrue(!ValeMusicQuest.interact(p,pos),"Identical decorative mushrooms without a native start do nothing");
            site.chunk.setStartForStructure(site.structure,start);
            site.level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
            h.assertTrue(!ValeMusicQuest.interact(p,pos),"Changed block cannot masquerade as a bell");
            site.level.setBlock(pos,site.piece.plan().get(pos),18);
            site.level.setBlock(site.root,Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!ValeMusicQuest.interact(p,pos),"Rejected/absent landmark anchor is inactive");
            site.level.setBlock(site.root,site.piece.plan().get(site.root),18);
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            h.assertTrue(!ValeMusicQuest.interact(p,pos),"Spectator cannot progress");
            h.assertTrue(ValeMusicQuest.resolve(h.getLevel(),pos)==null,"Other dimensions do not bind bells");
            h.assertTrue(ValeMusicQuest.resolve(site.level,new BlockPos(29000000,160,29000000))==null,"Unloaded positions are ignored");
            h.assertTrue(site.level.getChunkSource().getChunkNow(29000000>>4,29000000>>4)==null,"Resolving never loads remote chunks");
        } h.succeed();
    }
}
