package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import static io.github.golovinss.dudunka.ValeAdventureStructure.Landmark;

@GameTestHolder("dudunka_landmarks") @PrefixGameTestTemplate(false)
public final class ValeAdventureTests {
    private static ResourceLocation id(String path) { return new ResourceLocation(DudunkaMod.ID,path); }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_landmark_registry")
    public static void registryBiomesPlacementAndPiecePersistence(GameTestHelper h) {
        var level=h.getLevel();var access=level.registryAccess();
        var registry=access.registryOrThrow(Registries.STRUCTURE);
        var set=access.registryOrThrow(Registries.STRUCTURE_SET).get(id("vale_adventure_landmarks"));
        h.assertTrue(set!=null && set.structures().size()==5,"All five IDs participate in a native StructureSet");
        var placement=(RandomSpreadStructurePlacement)set.placement();
        h.assertTrue(placement.spacing()==10 && placement.separation()==4,"Bounded exploration spacing");
        for(var kind:Landmark.values()) {
            var structure=registry.get(id(kind.id));
            h.assertTrue(structure instanceof ValeAdventureStructure s && s.landmark==kind
                    && structure.terrainAdaptation()==TerrainAdjustment.NONE,"Native kind and no terrain adaptation: "+kind);
            h.assertTrue(structure.biomes().size()==1 && structure.biomes().stream().allMatch(b->ValeAdventureStructure.eligible(kind,b)),"Exact eligible biome: "+kind);
            for(String biome:List.of("glowing_mushroom_forest","pink_meadows","memory_forest")) {
                var holder=access.registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,id(biome)));
                h.assertTrue(structure.biomes().contains(holder)==kind.biome.equals(biome),"Wrong biomes excluded");
            }
            // The same native generation entry point used by /locate rejects Overworld terrain.
            for(var dimension:List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
                var other=level.getServer().getLevel(dimension);var source=other.getChunkSource();
                var start=structure.generate(access,source.getGenerator(),source.getGenerator().getBiomeSource(),source.randomState(),
                        other.getStructureManager(),other.getSeed(),new ChunkPos(2000,2000),0,other,bm->true);
                h.assertTrue(!start.isValid(),"Permissive biome predicate cannot create a start in "+dimension.location());
                h.assertTrue(!ValeAdventurePiece.generationAllowed(other,new ChunkPos(2000,2000)),"Other dimensions refuse placement");
            }
            int[] floor=new int[169];Arrays.fill(floor,80);
            var piece=new ValeAdventurePiece(kind,new BlockPos(8,80,8),floor);
            var context=StructurePieceSerializationContext.fromLevel(level);
            var tag=piece.createTag(context);var restored=(ValeAdventurePiece)DudunkaMod.VALE_ADVENTURE_PIECE.get().load(context,tag);
            h.assertTrue(restored.anchor().equals(piece.anchor()) && restored.landmark()==kind
                    && restored.plan().equals(piece.plan()) && restored.getBoundingBox().equals(piece.getBoundingBox()),"Native piece save/reload preserves exact geometry and anchor");
            h.assertTrue(piece.plan().size()>=35 && piece.plan().keySet().stream().allMatch(p->piece.getBoundingBox().isInside(p)
                    && new ChunkPos(p).equals(new ChunkPos(0,0))),"Meaningful geometry fits one chunk: "+kind+" blocks="+piece.plan().size()+" bounds="+piece.getBoundingBox()+" invalid="+piece.plan().keySet().stream().filter(p->!piece.getBoundingBox().isInside(p) || !new ChunkPos(p).equals(new ChunkPos(0,0))).toList());
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_landmark_registry")
    public static void visualRevisionKeepsLegacyBlueprintsAndSlopedBounds(GameTestHelper h) {
        var context=StructurePieceSerializationContext.fromLevel(h.getLevel());
        var root=new BlockPos(8,80,8);
        for(var kind:Landmark.values()) {
            int[] ground=new int[169];
            for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++) ground[(x+6)*13+z+6]=80-Math.floorMod(x+z,3);
            var fresh=new ValeAdventurePiece(kind,root,ground);
            var tag=fresh.createTag(context);
            boolean polished=kind==Landmark.MUSIC_BELLS || kind==Landmark.JUMPER_DANCE;
            h.assertTrue(tag.getInt("VisualRevision")== (kind==Landmark.MUSIC_BELLS?3:polished?2:0),"Only the first two landmarks opt in");
            var restored=new ValeAdventurePiece(tag);
            h.assertTrue(restored.plan().equals(fresh.plan()),"Saved visual revision retains exact sloped geometry");
            h.assertTrue(fresh.plan().keySet().stream().allMatch(pos->fresh.getBoundingBox().isInside(pos)
                    && new ChunkPos(pos).equals(new ChunkPos(root))),"Sloped decoration and supports stay inside piece/chunk");
            tag.remove("VisualRevision");
            var legacy=new ValeAdventurePiece(tag);
            var savedAgain=legacy.createTag(context);
            h.assertTrue(!savedAgain.contains("VisualRevision") && new ValeAdventurePiece(savedAgain).plan().equals(legacy.plan()),
                    "Absent revision remains legacy across repeated saves");
            if(!polished) h.assertTrue(legacy.plan().equals(fresh.plan()),"Other three landmarks are unchanged");
            if(kind==Landmark.JUMPER_DANCE) {
                var oldFlowers=legacy.plan().entrySet().stream().filter(e->e.getValue().is(Blocks.ALLIUM) || e.getValue().is(Blocks.PINK_TULIP)).map(Map.Entry::getKey).toList();
                var newFlowers=fresh.plan().entrySet().stream().filter(e->e.getValue().is(MushroomValeLandmarks.DANCE_FLOWER.get())).map(Map.Entry::getKey).toList();
                h.assertTrue(oldFlowers.size()>4 && oldFlowers.equals(newFlowers),"Every flower position and voice order is preserved");
                h.assertTrue(fresh.plan().values().stream().noneMatch(state->state.is(Blocks.ALLIUM) || state.is(Blocks.PINK_TULIP)),"New ring has no ordinary tulips/alliums");
            }
            if(kind==Landmark.MUSIC_BELLS) {
                h.assertTrue(legacy.plan().get(root.offset(-5,3,0)).is(MushroomValeLandmarks.BLOCKS.get("turquoise_landmark_cap").get()),
                        "Legacy hanging cap silhouette stays available");
                h.assertTrue(!legacy.plan().equals(fresh.plan()),"New bells use a separate blueprint");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80,batch="isolated_landmark_safety")
    public static void atomicSafetyAndFinishedChunkProtection(GameTestHelper h) {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        // Synthetic clear site in a real eligible biome, remote from native locate fixtures.
        var generator=vale.getChunkSource().getGenerator();var random=vale.getChunkSource().randomState();
        BlockPos root=null;
        for(int i=0;i<128;i++) {
            var p=new BlockPos(16008+i*16,180,16008);
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(p.getX()),QuartPos.fromBlock(p.getY()),QuartPos.fromBlock(p.getZ()),random.sampler());
            boolean eligible=ValeAdventureStructure.eligible(Landmark.JUMPER_DANCE,biome);
            for(int dx=-6;dx<=6 && eligible;dx++) for(int dz=-6;dz<=6 && eligible;dz++)
                eligible=ValeAdventureStructure.eligible(Landmark.JUMPER_DANCE,generator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(p.getX()+dx),QuartPos.fromBlock(p.getY()),QuartPos.fromBlock(p.getZ()+dz),random.sampler()));
            if(eligible){root=p;break;}
        }
        h.assertTrue(root!=null,"Eligible meadow fixture exists");
        var saved=new LinkedHashMap<BlockPos,BlockState>();
        var chunk=new ChunkPos(root);vale.getChunk(chunk.x,chunk.z);
        var bounds=new BoundingBox(root.getX()-6,179,root.getZ()-6,root.getX()+6,191,root.getZ()+6);
        for(var pos:BlockPos.betweenClosed(new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ()),new BlockPos(bounds.maxX(),bounds.maxY(),bounds.maxZ()))) {
            saved.put(pos.immutable(),vale.getBlockState(pos));vale.setBlock(pos,pos.getY()==179?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        try {
            int[] floor=new int[169];Arrays.fill(floor,180);
            var piece=new ValeAdventurePiece(Landmark.JUMPER_DANCE,root,floor);
            h.assertTrue(piece.clearSite(vale,bounds),"Dry unobstructed eligible fixture accepted");
            var clipped=new BoundingBox(bounds.minX()+1,bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());
            h.assertTrue(!piece.clearSite(vale,clipped),"Chunk-boundary clipping rejects whole site");
            for(var block:List.of(Blocks.WATER,Blocks.LAVA,Blocks.OAK_LOG,Blocks.OAK_LEAVES,Blocks.STONE_BRICKS,Blocks.CHEST)) {
                var p=root.offset(5,2,5);vale.setBlock(p,block.defaultBlockState(),18);
                var before=new LinkedHashMap<BlockPos,BlockState>();saved.keySet().forEach(k->before.put(k,vale.getBlockState(k)));
                h.assertTrue(!piece.clearSite(vale,bounds),"Reject water, trees and builds: "+block);
                piece.postProcess(vale,vale.structureManager(),generator,RandomSource.create(1),bounds,chunk,root);
                h.assertTrue(before.entrySet().stream().allMatch(e->e.getValue().equals(vale.getBlockState(e.getKey()))),"Rejected/full chunk unchanged");
                vale.setBlock(p,Blocks.AIR.defaultBlockState(),18);
            }
            h.assertTrue(!ValeAdventurePiece.generationAllowed(vale,chunk)
                    && !ValeAdventurePiece.generationAllowed(h.getLevel(),chunk),"No retrofit and no other-dimension placement");
            piece.postProcess(vale,vale.structureManager(),generator,RandomSource.create(1),bounds,chunk,root);
            h.assertTrue(vale.getBlockState(root).isAir(),"Even a clear FULL site is never retrofitted");
            vale.setBlock(root.below(),Blocks.WATER.defaultBlockState(),18);
            h.assertTrue(!piece.clearSite(vale,bounds),"Wet support rejected");
            vale.setBlock(root.below(),Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!piece.clearSite(vale,bounds),"Unsupported site rejected");
        } finally {saved.forEach((p,s)->vale.setBlock(p,s,18));}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=1200,batch="isolated_landmark_locate")
    public static void actualLocateFindsFiveVisibleNativeStartsAndReloadsThem(GameTestHelper h) throws Exception {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var registry=vale.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var forced=new HashSet<>(vale.getForcedChunks());
        for(var kind:Landmark.values()) {
            final BlockPos[] located={null};
            CommandSource output=new CommandSource() {
                @Override public void sendSystemMessage(Component message) {
                    if(message.getContents() instanceof TranslatableContents t && t.getKey().equals("commands.locate.structure.success")) {
                        var coords=(Component)t.getArgs()[1];
                        var command=coords.getStyle().getClickEvent().getValue().split(" ");
                        located[0]=new BlockPos(Integer.parseInt(command[2]),0,Integer.parseInt(command[4]));
                    }
                }
                @Override public boolean acceptsSuccess(){return true;}
                @Override public boolean acceptsFailure(){return true;}
                @Override public boolean shouldInformAdmins(){return false;}
            };
            var source=vale.getServer().createCommandSourceStack().withLevel(vale).withPosition(new Vec3(40008,80,40008)).withPermission(4).withSource(output);
            vale.getServer().getCommands().getDispatcher().execute("locate structure dudunka:"+kind.id,source);
            h.assertTrue(located[0]!=null,"Physical /locate command returns coordinates: "+kind.id);
            var cp=new ChunkPos(located[0]);var chunk=vale.getChunk(cp.x,cp.z);
            var structure=registry.get(id(kind.id));var start=chunk.getStartForStructure(structure);
            h.assertTrue(start!=null && start.isValid() && start.getPieces().size()==1,"Located native start exists");
            var piece=(ValeAdventurePiece)start.getPieces().get(0);
            for(var e:piece.plan().entrySet()) h.assertTrue(vale.getBlockState(e.getKey()).equals(e.getValue()),
                    "Located landmark must be complete and visible, not an empty/rejected start: "+kind.id+" at "+e.getKey());
            h.assertTrue(piece.anchorPresent(vale),"Exact saved interaction anchor exists");
            h.assertTrue(located[0].getX()==piece.anchor().getX() && located[0].getZ()==piece.anchor().getZ(),
                    "Physical locate reports the saved anchor's exact X/Z");
            assertResolvers(h,vale,piece);
            if (kind==Landmark.MUSIC_BELLS) {
                h.assertTrue(piece.visualRevision()==3 && piece.styledBells(),"New native Bells use approved styled revision");
                for (int bell=0;bell<3;bell++) {
                    var target=ValeMusicQuest.resolve(vale,piece.bellPosition(bell));
                    h.assertTrue(target!=null && target.bell()==bell,"Native styled bell keeps its note identity");
                }
                h.assertTrue(ValeMusicQuest.resolve(vale,piece.anchor().above(2)).bell()==-1,
                        "Native crown replays the same listening clue as the stone");
            }
            if (kind==Landmark.SINGING_MUSHROOM) h.assertTrue(ValeConcertQuest.resolve(vale,piece.anchor())!=null,
                    "Chapter 5 resolves the actual naturally generated concert anchor");
            if (kind==Landmark.SILVER_WEB_HARP) {
                h.assertTrue(ValeHarpQuest.resolve(vale,piece.anchor())!=null,
                        "Chapter 4 resolves the actual naturally generated harp center");
                for(int x:new int[]{-2,0,2}) h.assertTrue(ValeHarpQuest.resolve(vale,piece.anchor().offset(x,1,0))!=null,
                        "All three actual generated string anchors resolve");
            }
            var context=StructurePieceSerializationContext.fromLevel(vale);
            var restored=StructureStart.loadStaticStart(context,start.createTag(context,cp),vale.getSeed());
            h.assertTrue(restored!=null && restored.getStructure()==structure && restored.getBoundingBox().equals(start.getBoundingBox())
                    && ((ValeAdventurePiece)restored.getPieces().get(0)).plan().equals(piece.plan()),"Native start NBT round trip preserves ID/geometry");
            assertChunkDiskRoundTrip(h,vale,chunk,structure,piece);
            System.out.println("Stage 4.1 /locate "+kind.id+" anchor="+piece.anchor()+" blocks="+piece.plan().size());
        }
        h.assertTrue(forced.equals(vale.getForcedChunks()),"Locate/exploration does not install persistent forced chunks");
        h.succeed();
    }

    private static void assertResolvers(GameTestHelper h,net.minecraft.server.level.ServerLevel vale,ValeAdventurePiece piece) {
        boolean resolved=switch(piece.landmark()) {
            case MUSIC_BELLS -> ValeMusicQuest.resolve(vale,piece.anchor())!=null;
            case JUMPER_DANCE -> ValeDanceQuest.resolve(vale,piece.anchor())!=null;
            case MEMORY_STONE -> ValeMemoryQuest.resolve(vale,piece.anchor().above())!=null;
            case SILVER_WEB_HARP -> ValeHarpQuest.resolve(vale,piece.anchor())!=null;
            case SINGING_MUSHROOM -> ValeConcertQuest.resolve(vale,piece.anchor())!=null;
        };
        h.assertTrue(resolved,"Quest resolves actual generated "+piece.landmark().id+" at "+piece.anchor());
    }

    /** Uses Minecraft's entire chunk codec and real disk bytes, including actual block sections.
     * Does not insert a fabricated chunk/start into the live world or rerun placement. */
    private static void assertChunkDiskRoundTrip(GameTestHelper h,net.minecraft.server.level.ServerLevel vale,
            ChunkAccess chunk,Structure structure,ValeAdventurePiece original) throws Exception {
        var file=java.nio.file.Files.createTempFile("dudunka-native-landmark-",".nbt");
        try {
            net.minecraft.nbt.NbtIo.writeCompressed(ChunkSerializer.write(vale,chunk),file.toFile());
            var restored=ChunkSerializer.read(vale,vale.getChunkSource().getPoiManager(),chunk.getPos(),
                    net.minecraft.nbt.NbtIo.readCompressed(file.toFile()));
            var before=chunk.getStartForStructure(structure);var after=restored.getStartForStructure(structure);
            h.assertTrue(after!=null && before.isValid()==after.isValid(),"Chunk disk reload preserves start validity");
            if(before.isValid()) {
                var piece=(ValeAdventurePiece)after.getPieces().get(0);
                h.assertTrue(piece.anchor().equals(original.anchor()) && piece.plan().equals(original.plan())
                        && after.getBoundingBox().equals(before.getBoundingBox()),"Chunk reload preserves native anchor, terrain and bounds");
            }
            for(var pos:original.plan().keySet()) h.assertTrue(restored.getBlockState(pos).equals(chunk.getBlockState(pos)),
                    "Actual generated blocks survive chunk disk save/reload at "+pos);
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

    private static com.mojang.datafixers.util.Pair<BlockPos,net.minecraft.core.Holder<Structure>> nativeCandidate(
            net.minecraft.server.level.ServerLevel vale,Landmark kind,BlockPos origin) {
        var holder=vale.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,id(kind.id)));
        // Bypass the physical command layer: this is the unmodified native start-only search.
        return vale.getChunkSource().getGenerator().findNearestMapStructure(vale,HolderSet.direct(holder),origin,100,false);
    }

    @GameTest(template="empty",timeoutTicks=2400,batch="isolated_landmark_native_boundaries")
    public static void nativeStartsWithNeighborsFirstAgreeWithBlocksAcrossSignedChunksAndHeights(GameTestHelper h) throws Exception {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var forced=new HashSet<>(vale.getForcedChunks());var heights=new HashSet<Integer>();
        for(int sign:new int[]{-1,1}) for(var kind:Landmark.values()) {
            var origin=new BlockPos(sign*80008,80,sign*80008);
            var candidate=nativeCandidate(vale,kind,origin);
            h.assertTrue(candidate!=null,"Native candidate exists for "+kind.id);
            var cp=new ChunkPos(candidate.getFirst());var structure=candidate.getSecond().value();
            var partial=vale.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS);
            var pending=partial.getStartForStructure(structure);
            h.assertTrue(pending!=null && pending.isValid(),"Native start exists before FEATURES");
            var piece=(ValeAdventurePiece)pending.getPieces().get(0);
            h.assertTrue(piece.anchor().getX()==cp.getMiddleBlockX() && piece.anchor().getZ()==cp.getMiddleBlockZ(),
                    "Native anchor uses signed chunk center");
            // Adjacent features may write into the target before its own FEATURES task runs.
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
                if(dx!=0 || dz!=0) vale.getChunk(cp.x+dx,cp.z+dz);
            var chunk=vale.getChunk(cp.x,cp.z);var start=chunk.getStartForStructure(structure);
            h.assertTrue(start!=null,"Completed native attempt retains an explicit start result");
            if(start.isValid()) {
                h.assertTrue(piece.intact(vale),"Every valid completed native start has every blueprint block: "+kind.id+" "+cp);
                assertResolvers(h,vale,piece);
            } else h.assertTrue(!piece.intact(vale),"Rejected native attempt is not reported as a physical landmark");
            assertChunkDiskRoundTrip(h,vale,chunk,structure,piece);
            // Exact public /locate must still find a complete playable site, including after rejection.
            var visible=ValeLandmarkCommands.findPhysical(vale,kind,origin);
            h.assertTrue(visible!=null && visible.intact(vale),"Locate skips rejected native sites: "+kind.id);
            heights.add(visible.anchor().getY());assertResolvers(h,vale,visible);
            h.assertTrue(visible.plan().keySet().stream().allMatch(p->new ChunkPos(p).equals(new ChunkPos(visible.anchor()))
                    && visible.getBoundingBox().isInside(p)),"Natural terrain-following blueprint is never clipped at signed chunk boundaries");
            System.out.println("Native landmark boundary case: "+kind.id+" sign="+sign+" candidate="+cp
                    +" accepted="+start.isValid()+" physical="+visible.anchor());
        }
        h.assertTrue(heights.size()>1,"Native fixtures exercise multiple absolute terrain heights");
        h.assertTrue(forced.equals(vale.getForcedChunks()),"Physical locate adds no persistent forced chunk tickets");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=1200,batch="isolated_landmark_native_rejection")
    public static void obstructionAfterNativeStartCannotLeaveAPlayableOrLocatedGhost(GameTestHelper h) throws Exception {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var candidate=nativeCandidate(vale,Landmark.MUSIC_BELLS,new BlockPos(120008,80,120008));
        h.assertTrue(candidate!=null,"Native bells candidate exists");
        var cp=new ChunkPos(candidate.getFirst());var structure=candidate.getSecond().value();
        var chunk=vale.getChunk(cp.x,cp.z,ChunkStatus.CARVERS);
        h.assertTrue(!chunk.getStatus().isOrAfter(ChunkStatus.FEATURES),"Inject obstruction before actual native decoration");
        var start=chunk.getStartForStructure(structure);
        h.assertTrue(start!=null && start.isValid(),"Start is valid before physical preflight");
        var piece=(ValeAdventurePiece)start.getPieces().get(0);var blocker=piece.anchor().offset(5,2,5);
        chunk.setBlockState(blocker,Blocks.OAK_LOG.defaultBlockState(),false);
        var full=vale.getChunk(cp.x,cp.z);
        h.assertTrue(!full.getStartForStructure(structure).isValid(),"Rejected placement invalidates its owning native start");
        h.assertTrue(vale.getBlockState(blocker).is(Blocks.OAK_LOG) && !piece.intact(vale),
                "Native rejection preserves obstruction and does not install a blueprint");
        h.assertTrue(ValeMusicQuest.resolve(vale,piece.anchor())==null,"Rejected start is never a playable quest landmark");
        assertChunkDiskRoundTrip(h,vale,full,structure,piece);
        var visible=ValeLandmarkCommands.findPhysical(vale,Landmark.MUSIC_BELLS,piece.anchor());
        h.assertTrue(visible!=null && !visible.anchor().equals(piece.anchor()) && visible.intact(vale),
                "Locate finds another real native structure after rejection");
        h.runAfterDelay(1,()->{
            h.assertTrue(vale.structureManager().checkStructurePresence(cp,structure,false)==StructureCheckResult.START_NOT_PRESENT,
                    "Vanilla locate's separate presence cache forgets the rejected start");
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=1200,batch="isolated_landmark_legacy_ghost")
    public static void oldSavedMissingNativeSiteIsSkippedWithoutRepairOrProgressChanges(GameTestHelper h) throws Exception {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var piece=ValeLandmarkCommands.findPhysical(vale,Landmark.MUSIC_BELLS,new BlockPos(160008,80,160008));
        h.assertTrue(piece!=null,"Real generated bells fixture exists");
        var cp=new ChunkPos(piece.anchor());var chunk=vale.getChunk(cp.x,cp.z);
        var structure=vale.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("music_bells"));
        var context=StructurePieceSerializationContext.fromLevel(vale);
        var savedStart=chunk.getStartForStructure(structure).createTag(context,cp);
        var progress=ValeMusicProgress.get(vale.getServer()).save(new net.minecraft.nbt.CompoundTag());
        // Model a saved empty legacy site using a genuinely generated start, not a fabricated piece.
        piece.plan().keySet().forEach(pos->vale.setBlock(pos,Blocks.AIR.defaultBlockState(),18));
        var other=ValeLandmarkCommands.findPhysical(vale,Landmark.MUSIC_BELLS,piece.anchor());
        h.assertTrue(other!=null && !other.anchor().equals(piece.anchor()) && other.intact(vale),
                "An empty legacy native start cannot become a locate success");
        h.assertTrue(savedStart.equals(chunk.getStartForStructure(structure).createTag(context,cp)),
                "Inspecting/skipping an old missing site does not rewrite its saved start");
        h.assertTrue(piece.plan().keySet().stream().allMatch(pos->vale.getBlockState(pos).isAir()),
                "No automatic reconstruction of old player terrain");
        h.assertTrue(progress.equals(ValeMusicProgress.get(vale.getServer()).save(new net.minecraft.nbt.CompoundTag())),
                "Physical discovery never resets existing chapter progress");
        h.assertTrue(ValeMusicQuest.resolve(vale,piece.anchor())==null,"Missing old site is not playable");
        assertChunkDiskRoundTrip(h,vale,chunk,structure,piece);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=1200,batch="isolated_landmark_styled_terrain_rejection")
    public static void styledTerrainSupportFailurePersistsAsInvalidAndLocateSkipsIt(GameTestHelper h) throws Exception {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var candidate=nativeCandidate(vale,Landmark.MUSIC_BELLS,new BlockPos(-200008,80,-200008));
        h.assertTrue(candidate!=null,"Native styled terrain candidate exists");
        var cp=new ChunkPos(candidate.getFirst()); var structure=candidate.getSecond().value();
        var chunk=vale.getChunk(cp.x,cp.z,ChunkStatus.CARVERS);
        h.assertTrue(!chunk.getStatus().isOrAfter(ChunkStatus.FEATURES),"Terrain fault is injected before placement");
        var start=chunk.getStartForStructure(structure);
        h.assertTrue(start!=null && start.isValid(),"Predicted terrain first creates a native start");
        var piece=(ValeAdventurePiece)start.getPieces().get(0);
        h.assertTrue(piece.styledBells(),"New native start selects styled production geometry");
        var ground=piece.createTag(StructurePieceSerializationContext.fromLevel(vale)).getIntArray("Ground");
        var support=new BlockPos(piece.anchor().getX(),ground[6*13+6]-1,piece.anchor().getZ());
        chunk.setBlockState(support,Blocks.WATER.defaultBlockState(),false);
        var full=vale.getChunk(cp.x,cp.z);
        h.assertTrue(!full.getStartForStructure(structure).isValid(),"Actual wet support cannot leave a valid saved start");
        h.assertTrue(vale.getBlockState(support).is(Blocks.WATER) && !piece.intact(vale),
                "Placement rejects the whole scene and preserves failed terrain");
        h.assertTrue(ValeMusicQuest.resolve(vale,piece.anchor())==null,"Rejected terrain is never an interactive landmark");
        assertChunkDiskRoundTrip(h,vale,full,structure,piece);
        var other=ValeLandmarkCommands.findPhysical(vale,Landmark.MUSIC_BELLS,piece.anchor());
        h.assertTrue(other!=null && !other.anchor().equals(piece.anchor()) && other.intact(vale),
                "Physical locate skips terrain failures and reports an intact native scene");
        h.runAfterDelay(1,()-> {
            h.assertTrue(vale.structureManager().checkStructurePresence(cp,structure,false)==StructureCheckResult.START_NOT_PRESENT,
                    "Rejected terrain also clears vanilla's structure-presence cache");
            h.succeed();
        });
    }

}
