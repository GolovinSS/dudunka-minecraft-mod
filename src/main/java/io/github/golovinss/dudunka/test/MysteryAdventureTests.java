package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public final class MysteryAdventureTests {
    private static final ResourceLocation ARCH=new ResourceLocation(DudunkaMod.ID,"forgotten_mushroom_arch");
    private static final ResourceLocation SET=new ResourceLocation(DudunkaMod.ID,"forgotten_mushroom_arches");
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_registry")
    public static void archUsesForestTagRareDeterministicPlacementAndSerializableNativePiece(GameTestHelper h) {
        var level=h.getLevel();var registry=level.registryAccess();
        var structure=registry.registryOrThrow(Registries.STRUCTURE).get(ARCH);
        var set=registry.registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        h.assertTrue(structure instanceof ForgottenArchStructure && set!=null,"Native datapack structure and set registered");
        h.assertTrue(structure.biomes().stream().allMatch(b->b.is(BiomeTags.IS_FOREST))
                && structure.biomes().stream().anyMatch(b->b.is(net.minecraft.world.level.biome.Biomes.FOREST)),"Overworld forests eligible");
        h.assertTrue(!structure.biomes().contains(registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)),"Plains excluded");
        h.assertTrue(structure.terrainAdaptation()==TerrainAdjustment.NONE,"No terrain adaptation or flattening");
        var placement=(RandomSpreadStructurePlacement)set.placement();
        h.assertTrue(placement.spacing()==24 && placement.separation()==8,"One potential start per 24x24 chunk region, separation 8");
        for(int x=-48;x<=48;x+=24)for(int z=-48;z<=48;z+=24) {
            var a=placement.getPotentialStructureChunk(level.getSeed(),x,z);
            var b=placement.getPotentialStructureChunk(level.getSeed(),x,z);
            h.assertTrue(a.equals(b) && a.x>=x && a.x<x+16 && a.z>=z && a.z<z+16,"Stable seed and bounded random spread");
        }
        var piece=new ForgottenArchPiece(new BlockPos(8,70,8));var context=StructurePieceSerializationContext.fromLevel(level);
        var tag=piece.createTag(context);var restored=DudunkaMod.FORGOTTEN_ARCH_PIECE.get().load(context,tag);
        h.assertTrue(tag.getString("id").equals(ARCH.toString()) && piece.getBoundingBox().equals(restored.getBoundingBox()),"Native piece NBT round trip");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=1200,batch="isolated_mystery_native_generation")
    public static void newChunksGenerateUnownedArchAndFamilyCanAwakenTravelAndReturn(GameTestHelper h) {
        var level=h.getLevel();var source=level.getChunkSource();var generator=source.getGenerator();var randomState=source.randomState();
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ARCH);
        var placement=(RandomSpreadStructurePlacement)level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET).placement();
        BlockPos found=null;int generated=0;
        // Bounded pure biome probes, followed by at most six fresh native generation sites. Never a player save.
        for(int i=0;i<128 && found==null && generated<6;i++) {
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),(32+i%16)*24,(32+i/16)*24);
            int x=candidate.getMiddleBlockX(),z=candidate.getMiddleBlockZ();
            int y=generator.getFirstFreeHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,randomState);
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z),randomState.sampler());
            if(!structure.biomes().contains(biome))continue;
            h.assertTrue(source.getChunkNow(candidate.x,candidate.z)==null,"Candidate is an unexplored chunk of this already running world");
            var chunk=level.getChunk(candidate.x,candidate.z,ChunkStatus.FULL,true);generated++;
            for(var pos:chunk.getBlockEntitiesPos())if(level.getBlockEntity(pos) instanceof FamilyGatewayEntity gate
                    && gate.forgottenArch() && FamilyGatewayBlock.complete(level,pos)) {found=pos.immutable();break;}
        }
        h.assertTrue(found!=null,"Fresh native forest generation must place a physical arch within six eligible starts; generated="+generated);
        final var root=found;var gate=(FamilyGatewayEntity)level.getBlockEntity(root);var initial=gate.saveWithFullMetadata();
        h.assertTrue(!gate.getBlockState().getValue(FamilyGatewayBlock.ACTIVE) && initial.getList("AwakenedOwners",Tag.TAG_COMPOUND).isEmpty(),"Generated arch is dormant and has no pre-existing owner");
        h.assertTrue(level.getBlockState(root.east(2)).is(Blocks.MOSSY_COBBLESTONE)
                && level.getBlockState(root.east(2).above()).is(Blocks.MOSS_CARPET)
                && level.getBlockState(root.east(2).north()).is(Blocks.VINE),"Ancient arch has moss-covered overgrown ruins");
        var states=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int part=0;part<12;part++){var p=FamilyGatewayBlock.cell(root,part);states.put(p,level.getBlockState(p));}
        // Calling a piece against a FULL server chunk is a no-op, including an existing player container.
        var container=root.east(4);var old=level.getBlockState(container);level.setBlock(container,Blocks.CHEST.defaultBlockState(),18);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(container)).setItem(0,new ItemStack(net.minecraft.world.item.Items.DIAMOND));
        var chest=level.getBlockEntity(container).saveWithFullMetadata();
        var beforeRetrofit=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var naturalChunk=new ChunkPos(root);
        for(var p:BlockPos.betweenClosed(new BlockPos(naturalChunk.getMinBlockX(),root.getY()-8,naturalChunk.getMinBlockZ()),
                new BlockPos(naturalChunk.getMaxBlockX(),root.getY()+12,naturalChunk.getMaxBlockZ())))
            beforeRetrofit.put(p.immutable(),level.getBlockState(p));
        new ForgottenArchPiece(root).postProcess(level,level.structureManager(),generator,RandomSource.create(1),
                new BoundingBox(root.getX()-8,level.getMinBuildHeight(),root.getZ()-8,root.getX()+8,level.getMaxBuildHeight()-1,root.getZ()+8),new ChunkPos(root),root);
        h.assertTrue(!ForgottenArchPiece.generationAllowed(level,new ChunkPos(root))
                && chest.equals(level.getBlockEntity(container).saveWithFullMetadata())
                && beforeRetrofit.entrySet().stream().allMatch(e->e.getValue().equals(level.getBlockState(e.getKey()))),"No retroactive mutation in generated chunks or player containers");
        level.setBlock(container,old,18);
        // Entity ticking needs all neighboring FULL chunks, as in the existing recovery_dimension fixture.
        // Complete this bounded 3x3 test neighborhood synchronously before waiting on entity sections.
        var tickingChunk=new ChunkPos(root);
        for(int cx=tickingChunk.x-1;cx<=tickingChunk.x+1;cx++)
            for(int cz=tickingChunk.z-1;cz<=tickingChunk.z+1;cz++)level.getChunk(cx,cz);
        var f=new FamilyGatewayTests.Fixture(h,true,1190);var arrival=new FamilyGatewayTests.Arrival(f.owner);
        boolean wasForced=level.getForcedChunks().contains(new ChunkPos(root).toLong());
        Runnable cleanup=()->{gate.cancel();states.forEach((p,s)->level.setBlock(p,s,18));
            if(level.getBlockEntity(root) instanceof FamilyGatewayEntity current)current.load(initial);
            level.setChunkForced(new ChunkPos(root).x,new ChunkPos(root).z,wasForced);arrival.close();f.close();};
        h.runAtTickTime(1190,cleanup);
        try {
            // Stay in the root's biome quart: its southern neighbor may already be plains.
            int ownerSide=Math.floorMod(root.getZ(),4)==0?1:-1;
            f.owner.teleportTo(level,root.getX()+.5,root.getY(),root.getZ()+.5+ownerSide,0,0);
            var stranger=f.player();stranger.teleportTo(level,root.getX()+.5,root.getY(),root.getZ()+2.5,0,0);
            // Ordinary GameTest tickets cover its template, not this remote generation site.
            // Use the existing remote-entity test convention: a temporary forced test chunk,
            // restored in cleanup. A normal portal ticket does not reliably tick this isolated
            // GameTest site without a real connected client. Production code never forces chunks.
            level.setChunkForced(new ChunkPos(root).x,new ChunkPos(root).z,true);
            // Entity-section disk IO is asynchronous. GameTest ticks can outrun that IO in
            // the full suite; allow the same 1200-tick setup budget as recovery_dimension.
            // The actual awakening is still asserted exactly 62 ticks after starting it.
            var family=new ArrayList<Companion>();
            h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(root)
                    && level.areEntitiesLoaded(new ChunkPos(root).toLong()),"Remote fixture has loaded ticking entity sections"))
                .thenExecute(()->{
                try {
                    com.mojang.logging.LogUtils.getLogger().info("Stage 3.8 native fixture ready at test tick {}, root {}",h.getTick(),root);
                    family.addAll(f.family(f.owner,true));
                    for(var friend:family){
                        friend.moveTo(root.getX()-.5+friend.kind.ordinal(),root.getY(),root.getZ()+.5-ownerSide,0,0);
                        h.assertTrue(level.getEntity(friend.getUUID())==friend,"Moved test companion stays tracked: "+friend.kind+" added="+friend.isAddedToWorld());
                    }
                    MysteryAdventure.observe(f.owner);
                    h.assertTrue(MysteryAdventure.get(f.owner.server).chapters(f.owner.getUUID())==3,"Real forest visit unlocks the progressive exploration chapter; progress="
                            +MysteryAdventure.get(f.owner.server).chapters(f.owner.getUUID())+", forest="+level.getBiome(f.owner.blockPosition()).is(BiomeTags.IS_FOREST));
                    gate.interact(stranger);
                    h.assertTrue(!gate.awakening() && !gate.awakened(stranger.getUUID()),"Another owner's family cannot awaken a natural arch");
                    var eligibility=FamilyGatewayRules.inspect(level,root,f.owner.getUUID());
                    h.assertTrue(eligibility.ready(),"Natural family loaded/ready: "+(eligibility.missing()==null?"ready":eligibility.missing().getString()));
                    gate.interact(f.owner);h.assertTrue(gate.awakening(),"Legitimate family starts unowned natural arch ritual");
                } catch(RuntimeException | Error e) {cleanup.run();throw e;}
            }).thenExecuteAfter(62,()->{
                try {
                    h.assertTrue(gate.awakened(f.owner.getUUID()) && !gate.awakened(stranger.getUUID()),"Natural arch awakens per owner after the existing three-second scene");
                    var data=MysteryAdventure.get(f.owner.server);
                    h.assertTrue(data.chapters(f.owner.getUUID())==4 && data.chapters(stranger.getUUID())==0,"Awakening memory is private");
                    var saved=gate.saveWithFullMetadata();level.removeBlockEntity(root);
                    var restored=new FamilyGatewayEntity(root,level.getBlockState(root));restored.load(saved);level.setBlockEntity(restored);
                    h.assertTrue(restored.forgottenArch() && restored.awakened(f.owner.getUUID()) && !restored.awakened(stranger.getUUID()),"Chunk BE reload retains natural origin and private unlock");
                    restored.interact(f.owner);
                    h.assertTrue(f.owner.level()==arrival.vale && data.chapters(f.owner.getUUID())==5,"First successful journey unlocks the final illustrated chapter");
                    var exit=MushroomValeReturnGateway.find(arrival.vale);h.assertTrue(exit!=null,"Natural arch supplies the same physical return gateway");
                    f.owner.teleportTo(arrival.vale,exit.getX()+.5,exit.getY(),exit.getZ()+2.5,0,0);
                    ((FamilyGatewayEntity)arrival.vale.getBlockEntity(exit)).interact(f.owner);
                    h.assertTrue(f.owner.level()==level && MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())==null,"Safe physical return consumes only this owner's bookmark");
                    for(var friend:family)h.assertTrue(level.getEntity(friend.getUUID())==friend && friend.ownerId().equals(f.owner.getUUID()),"Original companion identities stay in Overworld");
                } finally {cleanup.run();}
            }).thenSucceed();
        } catch(RuntimeException | Error e) {cleanup.run();throw e;}
    }

    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_progress")
    public static void storyObservesOwnedFriendsAndPreservesExistingMemories(GameTestHelper h) {
        try(var f=new FamilyGatewayTests.Fixture(h,true)) {
            var ledger=MysteryAdventure.get(f.owner.server);var oldMemories=FamilyMemories.get(f.owner.server).save(new CompoundTag());
            var stranger=f.player();f.friend(Kind.DUDUNKA,stranger);MysteryAdventure.observe(f.owner);
            h.assertTrue(ledger.chapters(f.owner.getUUID())==0,"Other owner's Dudunka cannot begin your legend");
            f.friend(Kind.DUDUNKA,f.owner);MysteryAdventure.observe(f.owner);
            h.assertTrue(ledger.chapters(f.owner.getUUID())==1,"Dudunka's real presence begins the legend without spoilers");
            f.friend(Kind.MARUSYA,f.owner);var syusya=f.friend(Kind.SYUSYA,f.owner);
            var adult=syusya.saveWithoutId(new CompoundTag());var young=adult.copy();young.putInt("GrowthTicks",0);syusya.readAdditionalSaveData(young);
            MysteryAdventure.observe(f.owner);h.assertTrue(ledger.chapters(f.owner.getUUID())==1,"A young friend cannot award the grown-up cooperation chapter");
            syusya.readAdditionalSaveData(adult);MysteryAdventure.observe(f.owner);
            h.assertTrue(ledger.chapters(f.owner.getUUID())>=2 && ledger.chapters(f.owner.getUUID())<=3,"Three adults teach cooperation without awarding awakening or journey");
            var snapshot=FamilyAlbum.collect(f.owner);
            h.assertTrue(snapshot.mysteryChapters()==ledger.chapters(f.owner.getUUID()) && oldMemories.equals(FamilyMemories.get(f.owner.server).save(new CompoundTag())),"Album preserves earlier memories and server-authored progress");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_save")
    public static void chaptersPersistMonotonicallyPerOwnerAndIgnoreMalformedRows(GameTestHelper h) {
        var data=new MysteryAdventure();var owner=UUID.randomUUID();var stranger=UUID.randomUUID();
        data.unlockThrough(owner,3);data.unlockThrough(owner,1);data.unlockThrough(stranger,1);
        var tag=data.save(new CompoundTag());var rows=tag.getList("Owners",Tag.TAG_COMPOUND);
        var bad=new CompoundTag();bad.putUUID("Owner",owner);bad.putInt("Chapters",999);rows.add(bad);
        var missing=new CompoundTag();missing.putInt("Chapters",5);rows.add(missing);
        var duplicate=new CompoundTag();duplicate.putUUID("Owner",owner);duplicate.putInt("Chapters",2);rows.add(duplicate);
        var restored=MysteryAdventure.load(tag);
        h.assertTrue(restored.chapters(owner)==3 && restored.chapters(stranger)==1 && restored.chapters(UUID.randomUUID())==0,"Reload keeps unlocked chapters and independent owners; malformed rows cannot unlock later events");
        restored.unlockThrough(owner,5);var again=MysteryAdventure.load(restored.save(new CompoundTag()));
        h.assertTrue(again.chapters(owner)==5 && again.chapters(stranger)==1,"First journey and all previous chapters remain available");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_locator")
    public static void locatorUsesOnlyIntactNaturalLoadedArchesAndNeverLoadsChunks(GameTestHelper h) {
        try(var f=new FamilyGatewayTests.Fixture(h,true)) {
            var data=MysteryAdventure.get(f.owner.server);f.place();
            int loaded=f.level.getChunkSource().getLoadedChunksCount();var forced=new HashSet<>(f.level.getForcedChunks());
            h.assertTrue(ForgottenArchDiscovery.find(f.owner).state()==ForgottenArchDiscovery.State.LOCKED,"Locator waits for forest chapter");
            data.unlockThrough(f.owner.getUUID(),3);
            h.assertTrue(ForgottenArchDiscovery.find(f.owner).state()==ForgottenArchDiscovery.State.NO_ECHO,"A crafted gateway is not misreported as a forgotten arch");
            f.gateway().markForgottenArch();var echo=ForgottenArchDiscovery.find(f.owner);
            h.assertTrue(echo.state()==ForgottenArchDiscovery.State.FOUND && echo.target().equals(f.root),"Coordinates established from the live complete natural frame");
            var remote=new BlockPos(2000000,160,2000000);
            var remotePlayer=new net.minecraft.server.level.ServerPlayer(f.owner.server,f.level,
                    new com.mojang.authlib.GameProfile(f.owner.getUUID(),"RemoteArchProbe"));
            remotePlayer.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(f.owner.server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),remotePlayer) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
            };
            remotePlayer.moveTo(remote,0,0);
            int beforeRemote=f.level.getChunkSource().getLoadedChunksCount();
            h.assertTrue(ForgottenArchDiscovery.find(remotePlayer).state()==ForgottenArchDiscovery.State.NO_ECHO
                    && beforeRemote==f.level.getChunkSource().getLoadedChunksCount()
                    && f.level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null,
                    "Detached unloaded-range probe cannot reveal or generate a target");
            f.level.destroyBlock(f.root.east().above(),false);
            h.assertTrue(ForgottenArchDiscovery.find(f.owner).state()==ForgottenArchDiscovery.State.NO_ECHO,"Broken arch cannot remain a stale locator target");
            h.assertTrue(loaded==f.level.getChunkSource().getLoadedChunksCount() && forced.equals(new HashSet<>(f.level.getForcedChunks())),"Bounded discovery adds no chunks or forced tickets");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=150,batch="isolated_mystery_crafted")
    public static void craftedGatewayStillUnlocksTheSameAdventureAndDeniedTravelCannotUnlockJourney(GameTestHelper h) {
        var f=new FamilyGatewayTests.Fixture(h,true);var arrival=new FamilyGatewayTests.Arrival(f.owner);
        f.place();f.family(f.owner,true);var gate=f.gateway();gate.interact(f.owner);
        h.runAtTickTime(140,()->{arrival.close();f.close();});
        h.runAfterDelay(62,()->{
            try {
                var data=MysteryAdventure.get(f.owner.server);
                h.assertTrue(!gate.forgottenArch() && gate.awakened(f.owner.getUUID()) && data.chapters(f.owner.getUUID())==4,"Crafting remains a complete alternative to natural discovery");
                arrival.unsafe();gate.interact(f.owner);
                h.assertTrue(f.owner.level()==f.level && data.chapters(f.owner.getUUID())==4,"Unsafe arrival never awards First Journey");
                h.succeed();
            } finally {arrival.close();f.close();}
        });
    }

    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_sessions")
    public static void locatorRequiresHeldAlbumAuthenticSessionAndRejectsReplay(GameTestHelper h) {
        try(var f=new FamilyGatewayTests.Fixture(h,true)) {
            MysteryAdventure.get(f.owner.server).unlockThrough(f.owner.getUUID(),3);
            f.owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(DudunkaMod.ALBUM.get()));
            var open=AlbumCommands.open(f.owner);var stranger=f.player();
            h.assertTrue(AlbumCommands.executeArch(stranger,new AlbumCommands.ArchCommand(open.session(),1))==null,"Foreign session rejected");
            h.assertTrue(AlbumCommands.executeArch(f.owner,new AlbumCommands.ArchCommand(UUID.randomUUID(),1))==null,"Forged session rejected");
            var reply=AlbumCommands.executeArch(f.owner,new AlbumCommands.ArchCommand(open.session(),1));
            h.assertTrue(reply!=null && reply.snapshot().archEcho().state()==ForgottenArchDiscovery.State.NO_ECHO,"Held album obtains private bounded guidance");
            h.assertTrue(AlbumCommands.executeArch(f.owner,new AlbumCommands.ArchCommand(open.session(),1))==null,"Replay rejected");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var second=AlbumCommands.open(f.owner);
            h.assertTrue(AlbumCommands.executeArch(f.owner,new AlbumCommands.ArchCommand(second.session(),1))==null,"Dropping album invalidates locator command");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="isolated_mystery_codec")
    public static void mysterySnapshotCodecKeepsOldMemoriesAndBoundsProgressAndTargets(GameTestHelper h) {
        var memory=new FamilyMemories.Memory(Kind.DUDUNKA,FamilyMemories.Event.ADULT,24000);
        var snapshot=new FamilyAlbum.Snapshot(0,List.of(),0,List.of(),0,0,List.of(),List.of(memory),5,
                new ForgottenArchDiscovery.Echo(ForgottenArchDiscovery.State.FOUND,new BlockPos(45,70,12)));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            FamilyAlbum.encode(snapshot,buf);h.assertTrue(snapshot.equals(FamilyAlbum.decode(buf)) && buf.readableBytes()==0,"Five chapters, old memories and real echo round trip");
            buf.clear();FamilyAlbum.encode(new FamilyAlbum.Snapshot(0,List.of()),buf);buf.setByte(buf.writerIndex()-2,6);
            boolean rejected=false;try{FamilyAlbum.decode(buf);}catch(IllegalArgumentException e){rejected=true;}
            h.assertTrue(rejected,"More than five chapters rejected");
            buf.clear();FamilyAlbum.encode(new FamilyAlbum.Snapshot(0,List.of()),buf);buf.setByte(buf.writerIndex()-1,ForgottenArchDiscovery.State.FOUND.ordinal());
            rejected=false;try{FamilyAlbum.decode(buf);}catch(IllegalArgumentException e){rejected=true;}
            h.assertTrue(rejected,"Locked story cannot receive later locator coordinates");
        } finally {buf.release();}
        h.succeed();
    }
}
