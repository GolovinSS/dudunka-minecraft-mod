package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.golovinss.dudunka.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeDanceClearingPreviewTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blockSketchHasBoundedDeterministicNativePaletteAndCardinalTargets(GameTestHelper h) {
        var root = new BlockPos(-1, 160, -1);
        var plan = ValeDanceClearingPreview.plan(root);
        var b = ValeDanceClearingPreview.bounds(root);
        h.assertTrue(b.getXSpan() == 25 && b.getZSpan() == 25 && b.getYSpan() == 6, "25 x 25 x 6 export box");
        h.assertTrue(plan.size() < 900 && plan.keySet().stream().allMatch(b::isInside), "All writes fit a bounded sketch");
        h.assertTrue(plan.equals(ValeDanceClearingPreview.plan(root)), "Repeated placement has identical block states");
        var shifted = ValeDanceClearingPreview.plan(root.offset(23, 7, -41));
        h.assertTrue(plan.entrySet().stream().allMatch(e -> e.getValue().equals(shifted.get(e.getKey().offset(23, 7, -41)))),
                "Negative coordinates and cross-chunk translation preserve geometry");
        h.assertTrue(plan.keySet().stream().map(ChunkPos::new).distinct().count() == 4, "Sketch crosses four chunks without clipping");
        h.assertTrue(plan.get(root).is(Blocks.CHISELED_STONE_BRICKS), "Recognizable existing activation stone");
        var expected = new BlockPos[]{root.north(5), root.east(5), root.south(5), root.west(5)};
        for (int i = 0; i < expected.length; i++) {
            var station = ValeDanceClearingPreview.station(root, i);
            h.assertTrue(station.equals(expected[i]) && plan.get(station).getValue(ValeDanceFlower.BLOOM) == i,
                    "N/E/S/W keep their original positions and distinct bloom variants");
            h.assertTrue(plan.get(station.below()).is(Blocks.MOSS_BLOCK), "Bloom has soil support in its low pedestal");
        }
        h.assertTrue(plan.values().stream().filter(s -> s.is(MushroomValeLandmarks.DANCE_FLOWER.get())).count() == 4,
                "Only four prominent directional stations");
        for (var state : plan.values()) {
            var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            h.assertTrue(!state.hasBlockEntity() && (id.getNamespace().equals("minecraft")
                    || state.is(MushroomValeLandmarks.DANCE_FLOWER.get())
                    || MushroomValeVegetation.BLOCKS.values().stream().anyMatch(block -> state.is(block.get()))),
                    "Vanilla construction and existing plants only; no preview meshes or registrations");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void playerBodyCanWalkFromRimToAllStationsAndEverySideOfCenter(GameTestHelper h) {
        var root = new BlockPos(0, 160, 0); var plan = ValeDanceClearingPreview.plan(root);
        var visited = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>();
        var entrance = root.south(12); visited.add(entrance); queue.add(entrance);
        while (!queue.isEmpty()) {
            var current = queue.remove();
            for (var direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                var next = current.relative(direction);
                if (Math.abs(next.getX()) > 12 || Math.abs(next.getZ()) > 12 || visited.contains(next)) continue;
                // A full player body (0.6 wide, 1.8 tall), not merely a point at feet height.
                var body = new net.minecraft.world.phys.AABB(next.getX() + .2, next.getY(), next.getZ() + .2,
                        next.getX() + .8, next.getY() + 1.8, next.getZ() + .8);
                boolean clear = true;
                for (int y = 0; y <= 1; y++) {
                    var cell = next.above(y); var state = plan.getOrDefault(cell, Blocks.AIR.defaultBlockState());
                    for (var box : state.getCollisionShape(h.getLevel(), cell).toAabbs())
                        if (body.intersects(box.move(cell.getX(), cell.getY(), cell.getZ()))) clear = false;
                }
                var floor = next.below(); var ground = plan.getOrDefault(floor, Blocks.GRASS_BLOCK.defaultBlockState());
                if (clear && Block.isShapeFullBlock(ground.getCollisionShape(h.getLevel(), floor))) {
                    visited.add(next); queue.add(next);
                }
            }
        }
        var directions = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < directions.length; i++) {
            h.assertTrue(visited.contains(root.relative(directions[i])), "Every side of the center has a connected standing cell");
            var flower = ValeDanceClearingPreview.station(root, i);
            h.assertTrue(visited.contains(flower.relative(directions[i].getOpposite())), "Every bloom has a clear inner click approach");
            h.assertTrue(visited.contains(flower.relative(directions[i])), "Every bloom also has an outer approach");
            h.assertTrue(visited.contains(root.relative(directions[i], 12)), "Four open rim entrances");
        }
        h.succeed();
    }

    /** Only this fixture generates chunks and supplies flat ground in an unused GameTest world. */
    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos root;
        final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        final Map<LevelChunk, Map<Structure, StructureStart>> starts = new LinkedHashMap<>();
        final Map<LevelChunk, Map<Structure, it.unimi.dsi.fastutil.longs.LongSet>> references = new LinkedHashMap<>();
        final List<ServerPlayer> players = new ArrayList<>();
        Site(GameTestHelper h) {
            level = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
            var base = h.absolutePos(BlockPos.ZERO);
            root = new BlockPos((base.getX() >> 4) * 16 + 15, 200, (base.getZ() >> 4) * 16 + 15);
            var b = ValeDanceClearingPreview.safetyBounds(root);
            for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) {
                var chunk = level.getChunk(x, z);
                starts.put(chunk, new HashMap<>(chunk.getAllStarts()));
                references.put(chunk, new HashMap<>(chunk.getAllReferences()));
                chunk.setAllStarts(Map.of()); chunk.setAllReferences(Map.of());
            }
            for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                saved.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, pos.getY() < root.getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
        }
        ServerPlayer player() {
            var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "DanceSketch"));
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            player.setGameMode(GameType.CREATIVE);
            player.moveTo(root.getX() + 16.5, root.getY(), root.getZ() + .5, 0, 0);
            level.addNewPlayer(player); players.add(player); return player;
        }
        Map<BlockPos, BlockState> current() {
            var out = new LinkedHashMap<BlockPos, BlockState>(); saved.keySet().forEach(p -> out.put(p, level.getBlockState(p))); return out;
        }
        @Override public void close() {
            players.forEach(p -> p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
            saved.forEach((p, s) -> level.setBlock(p, s, 18));
            starts.forEach(LevelChunk::setAllStarts); references.forEach(LevelChunk::setAllReferences);
        }
    }
    private static CommandDispatcher<CommandSourceStack> commands() {
        var d = new CommandDispatcher<CommandSourceStack>(); MushroomVale.registerCommands(d); return d;
    }
    private static void rejected(GameTestHelper h, CommandDispatcher<CommandSourceStack> d, CommandSourceStack s, String command) {
        boolean refused = false;
        try { d.execute(command, s); } catch (CommandSyntaxException expected) { refused = true; }
        h.assertTrue(refused, "Unsafe or unauthorized command must refuse: " + command);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "isolated_dance_preview_terrain")
    public static void wholeVolumePreflightRejectsGapsFluidsBuildsEntitiesAndUnloadedChunks(GameTestHelper h) {
        try (var site = new Site(h)) {
            var level = site.level; var root = site.root; var baseline = site.current();
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root) == null, "Dry supported flat test site accepted");
            var cells = new BlockPos[]{root.offset(12, 4, 12), root.east(4), root.offset(12, -3, 12),
                    root.offset(13, -1, 0), root.below(), root.offset(5, 0, 3)};
            var blocks = new Block[]{Blocks.CHEST, Blocks.WATER, Blocks.AIR, Blocks.AIR, Blocks.STONE_BRICKS, Blocks.OAK_LOG};
            for (int i = 0; i < cells.length; i++) {
                level.setBlock(cells[i], blocks[i].defaultBlockState(), 18); var before = site.current();
                h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root) != null, "Reject unsafe cell in volume, deep support or rim");
                h.assertTrue(before.equals(site.current()), "Rejected preflight is read-only");
                level.setBlock(cells[i], baseline.get(cells[i]), 18);
            }
            var player = site.player(); player.moveTo(root.getX(), root.getY(), root.getZ(), 0, 0);
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root) != null, "Occupants prevent placement");
            var remote = new BlockPos(29000000, 200, 29000000);
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, remote) != null
                    && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Unloaded site rejected without generating chunks");
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root.atY(level.getMaxBuildHeight() - 3)) != null, "Reject build-height overflow");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "isolated_dance_preview_commands")
    public static void explicitCreativeDevCommandsRestoreTerrainAndCannotAdvancePuzzles(GameTestHelper h) throws CommandSyntaxException {
        String oldProperty = System.getProperty(MushroomVale.DEV_PROPERTY);
        try (var site = new Site(h)) {
            var player = site.player(); var level = site.level; var root = site.root;
            var source = player.createCommandSourceStack().withPermission(2);
            String place = "dudunka_dev mushroom_vale dance_preview place " + root.getX() + " " + root.getY() + " " + root.getZ() + " disposable";
            String restore = place.replace(" place ", " restore ");
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            h.assertTrue(commands().getRoot().getChild("dudunka_dev") == null, "Developer route absent by default");
            System.setProperty(MushroomVale.DEV_PROPERTY, "true"); var dispatcher = commands();
            rejected(h, dispatcher, source.withPermission(0), place);
            rejected(h, dispatcher, level.getServer().createCommandSourceStack().withLevel(level).withPermission(2), place);
            rejected(h, dispatcher, source, place.replace(" disposable", ""));
            player.setGameMode(GameType.SURVIVAL); rejected(h, dispatcher, source, place); player.setGameMode(GameType.CREATIVE);
            System.clearProperty(MushroomVale.DEV_PROPERTY); rejected(h, dispatcher, source, place);
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            level.setBlock(root.offset(2, 0, 2), Blocks.FERN.defaultBlockState(), 18);
            var baseline = site.current();
            var progress = ValeMusicProgress.get(level.getServer()).save(new CompoundTag());
            var mystery = MysteryAdventure.get(level.getServer()).save(new CompoundTag());
            var forced = new HashSet<>(level.getForcedChunks());
            level.setBlock(root.offset(10, 3, 10), Blocks.CHEST.defaultBlockState(), 18);
            var obstruction = site.current(); rejected(h, dispatcher, source, place);
            h.assertTrue(obstruction.equals(site.current()), "Command rejection preserves all blocks and chest");
            level.setBlock(root.offset(10, 3, 10), Blocks.AIR.defaultBlockState(), 18);
            h.assertTrue(dispatcher.execute(place, source) == 1, "Explicit disposable preview places");
            var plan = ValeDanceClearingPreview.plan(root);
            for (var entry : plan.entrySet()) {
                h.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()), "All planned blocks were placed");
                h.assertTrue(entry.getValue().canSurvive(level, entry.getKey()), "Plants, carpets and lanterns have valid support");
                h.assertTrue(ValeDanceQuest.resolve(level, entry.getKey()) == null, "Preview blocks never bind to native dance quest");
            }
            player.moveTo(root.getX() + .5, root.getY(), root.getZ() + 2.5, 0, 0);
            h.assertTrue(!ValeDanceQuest.interact(player, root), "Central sketch stone has no quest action");
            for (int id : new int[]{0, 0, 1, 2, 2, 3})
                h.assertTrue(!ValeDanceQuest.interact(player, ValeDanceClearingPreview.station(root, id)), "Six taps cannot advance preview progress");
            player.moveTo(root.getX() + 16.5, root.getY(), root.getZ() + .5, 0, 0);
            rejected(h, dispatcher, source, place);
            var edit = root.offset(11, 2, 11); level.setBlock(edit, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
            var edited = site.current(); rejected(h, dispatcher, source, restore);
            h.assertTrue(edited.equals(site.current()), "Restore preserves manual edits, including originally empty cells");
            level.setBlock(edit, Blocks.AIR.defaultBlockState(), 18);
            h.assertTrue(dispatcher.execute(restore, source) == 1 && baseline.equals(site.current()), "Restore recovers all original terrain and vegetation exactly");
            rejected(h, dispatcher, source, restore);
            h.assertTrue(progress.equals(ValeMusicProgress.get(level.getServer()).save(new CompoundTag()))
                    && mystery.equals(MysteryAdventure.get(level.getServer()).save(new CompoundTag()))
                    && forced.equals(level.getForcedChunks()), "No progress, rewards or forced chunk tickets change");
            h.assertTrue(site.starts.keySet().stream().allMatch(c -> c.getAllStarts().isEmpty() && c.getAllReferences().isEmpty()),
                    "Preview never creates or modifies native structure metadata");
        } finally {
            if (oldProperty == null) System.clearProperty(MushroomVale.DEV_PROPERTY);
            else System.setProperty(MushroomVale.DEV_PROPERTY, oldProperty);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "isolated_dance_preview_native")
    public static void nativeDanceStartsAndNeighborReferencesAreProtected(GameTestHelper h) {
        try (var site = new Site(h)) {
            var level = site.level; var root = site.root;
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .get(ResourceLocation.fromNamespaceAndPath("dudunka", "jumper_dance"));
            int[] ground = new int[169]; Arrays.fill(ground, root.getY());
            var piece = new ValeAdventurePiece(ValeAdventureStructure.Landmark.JUMPER_DANCE, root, ground);
            var plan = piece.plan(); var chunk = level.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4);
            var start = new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(piece)));
            chunk.setStartForStructure(structure, start); var before = site.current();
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root) != null, "Even an unplaced native start is protected");
            h.assertTrue(before.equals(site.current()) && piece.plan().equals(plan) && chunk.getStartForStructure(structure) == start,
                    "Native blueprint, blocks and metadata remain unchanged");
            chunk.setAllStarts(Map.of());
            chunk.setAllReferences(Map.of(structure, new LongOpenHashSet(new long[]{new ChunkPos(root.west(16)).toLong()})));
            h.assertTrue(ValeDanceClearingPreview.siteProblem(level, root) != null && before.equals(site.current()),
                    "References to neighboring starts reject without loading or changing their chunks");
        }
        h.succeed();
    }
}
