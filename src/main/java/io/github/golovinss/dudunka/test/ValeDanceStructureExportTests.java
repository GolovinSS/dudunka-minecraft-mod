package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.MushroomVale;
import io.github.golovinss.dudunka.MushroomValeLandmarks;
import io.github.golovinss.dudunka.ValeDanceClearingPreview;
import io.github.golovinss.dudunka.ValeDanceFlower;
import io.github.golovinss.dudunka.ValeMusicProgress;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_structure_export") @PrefixGameTestTemplate(false)
public final class ValeDanceStructureExportTests {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("dudunka", "previews/jumper_dance_43a5a");
    static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath("dudunka", "structures/previews/jumper_dance_43a5a.nbt");
    static final Vec3i SIZE = new Vec3i(25, 6, 25);

    /** Only disposable GameTest fixtures load chunks/write blocks; no normal server lifecycle hook. */
    static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos root;
        final BoundingBox source, destination;
        final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        Site(GameTestHelper h) {
            level = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
            if (!(level.getServer() instanceof GameTestServer)) throw new IllegalStateException("Headless export requires GameTestServer");
            var base = h.absolutePos(BlockPos.ZERO);
            root = new BlockPos((base.getX() >> 4) * 16 + 15, 240, (base.getZ() >> 4) * 16 + 15);
            source = ValeDanceClearingPreview.bounds(root);
            destination = ValeDanceClearingPreview.bounds(root.east(64));
            for (var b : List.of(source, destination)) {
                for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++)
                    for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) level.getChunk(x, z);
                for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                    if (level.getBlockEntity(pos) != null) throw new IllegalStateException("Fixture overlaps an existing block entity");
                    saved.put(pos.immutable(), level.getBlockState(pos));
                }
            }
        }
        void fill(BoundingBox b, BlockState state) {
            for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) level.setBlock(pos, state, 18);
        }
        Map<BlockPos, BlockState> generate() {
            var plan = ValeDanceClearingPreview.plan(root);
            if (source.getXSpan() != 25 || source.getYSpan() != 6 || source.getZSpan() != 25
                    || plan.keySet().stream().anyMatch(p -> !source.isInside(p)))
                throw new IllegalStateException("Approved generator no longer fits 25 x 6 x 25; review/correct declared bounds before export");
            fill(source, Blocks.DIAMOND_BLOCK.defaultBlockState());
            plan.forEach((p, state) -> level.setBlock(p, state, 18));
            return plan;
        }
        @Override public void close() {
            saved.forEach((p, state) -> {
                net.minecraft.world.Clearable.tryClear(level.getBlockEntity(p));
                level.setBlock(p, state, 18);
            });
        }
    }

    static Map<BlockPos, BlockState> local(Map<BlockPos, BlockState> plan, BoundingBox bounds) {
        var states = new LinkedHashMap<BlockPos, BlockState>(); var min = NativeStructureExport.minimum(bounds);
        plan.forEach((pos, state) -> states.put(pos.subtract(min), state)); return states;
    }
    static void verify(GameTestHelper h, Site site, CompoundTag nbt, Map<BlockPos, BlockState> expected) {
        var states = NativeStructureExport.states(site.level, nbt);
        h.assertTrue(states.equals(expected), "Exact authored block IDs/properties; no missing blocks, air or captured terrain");
        h.assertTrue(nbt.getInt("DataVersion") == net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion(), "Native current DataVersion");
        h.assertTrue(states.get(new BlockPos(12, 1, 12)).is(Blocks.CHISELED_STONE_BRICKS), "Center remains at local 12,1,12");
        for (int bloom = 0; bloom < 4; bloom++) {
            var pos = ValeDanceClearingPreview.station(site.root, bloom).subtract(NativeStructureExport.minimum(site.source));
            h.assertTrue(states.get(pos).is(MushroomValeLandmarks.DANCE_FLOWER.get())
                    && states.get(pos).getValue(ValeDanceFlower.BLOOM) == bloom, "Exact directional station and bloom " + bloom);
        }
        StructureTemplate template = site.level.getStructureManager().readStructure(nbt.copy());
        h.assertTrue(template.getSize().equals(SIZE), "Native template retains 25 x 6 x 25 volume");
        var min = NativeStructureExport.minimum(site.destination);
        var box = template.getBoundingBox(NativeStructureExport.exactPlacement(), min);
        h.assertTrue(box.minX() == site.destination.minX() && box.maxX() == site.destination.maxX()
                && box.minY() == site.destination.minY() && box.maxY() == site.destination.maxY()
                && box.minZ() == site.destination.minZ() && box.maxZ() == site.destination.maxZ(), "Native bounding box matches export dimensions");
        // Alternating air/solid sentinels prove omitted cells are not written in either kind of gap.
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            site.level.setBlock(pos, sentinel(pos), 18);
        h.assertTrue(template.placeInWorld(site.level, min, min, NativeStructureExport.exactPlacement(), RandomSource.create(0), 18), "Native template placement succeeds");
        int compared = 0, untouched = 0;
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var wanted = expected.get(pos.subtract(min));
            h.assertTrue(site.level.getBlockState(pos).equals(wanted == null ? sentinel(pos) : wanted), "Round-trip mismatch at " + pos);
            if (wanted == null) untouched++; else compared++;
        }
        h.assertTrue(compared == expected.size() && untouched == 3750 - expected.size(), "Every authored/untouched cell compared");
        com.mojang.logging.LogUtils.getLogger().info("DANCE_STRUCTURE_ROUND_TRIP id={} size=25x6x25 authored={} compared={} untouched={} mismatches=0 entities=0 air=0",
                ID, expected.size(), compared, untouched);
    }
    private static BlockState sentinel(BlockPos pos) {
        return ((pos.getX() + pos.getY() + pos.getZ()) & 1) == 0 ? Blocks.EMERALD_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_dance_structure_export")
    public static void canonicalNativeExportRoundTripsAndRepeatsExactly(GameTestHelper h) throws Exception {
        try (var site = new Site(h)) {
            var progress = ValeMusicProgress.get(site.level.getServer()).save(new CompoundTag());
            var forced = new HashSet<>(site.level.getForcedChunks());
            var plan = site.generate(); var expected = local(plan, site.source);
            var occupant = new net.minecraft.world.entity.item.ItemEntity(site.level, site.root.getX() + .5,
                    site.root.getY() + 2, site.root.getZ() + .5, new ItemStack(Items.DIAMOND));
            h.assertTrue(site.level.addFreshEntity(occupant), "Fixture includes a real entity to exclude from capture");
            CompoundTag nbt;
            try { nbt = NativeStructureExport.capture(site.level, site.source, plan); }
            finally { occupant.discard(); }
            byte[] bytes = NativeStructureExport.compressed(nbt);
            var disk = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
            verify(h, site, disk, expected);
            // Re-evaluate the actual generator and capture again, not just serialize the same tag twice.
            var repeatPlan = site.generate();
            var repeat = NativeStructureExport.capture(site.level, site.source, repeatPlan);
            h.assertTrue(nbt.equals(repeat) && Arrays.equals(bytes, NativeStructureExport.compressed(repeat)), "Repeat exports have identical NBT and compressed bytes");
            h.assertTrue(progress.equals(ValeMusicProgress.get(site.level.getServer()).save(new CompoundTag()))
                    && forced.equals(site.level.getForcedChunks()), "No progression or forced chunk changes");
            var outside = new LinkedHashMap<>(plan); outside.put(site.root.east(13), Blocks.STONE.defaultBlockState());
            boolean rejected = false;
            try { NativeStructureExport.capture(site.level, site.source, outside); } catch (IllegalArgumentException expectedError) { rejected = true; }
            h.assertTrue(rejected, "Bounds drift reports an error instead of truncating the build");
            String exportDir = System.getenv("DUDUNKA_DANCE_EXPORT_DIR");
            if (exportDir != null) {
                var path = Path.of(exportDir).resolve("jumper_dance_43a5a.nbt");
                Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                String report = "{\"template\":\"" + ID + "\",\"size\":[25,6,25],\"authored\":" + expected.size()
                        + ",\"compared\":" + expected.size() + ",\"untouched\":" + (3750 - expected.size())
                        + ",\"mismatches\":0,\"entities\":0,\"air\":0,\"deterministic\":true,\"sha256\":\"" + hash + "\"}\n";
                Files.writeString(Path.of(exportDir).resolve("report.json"), report, StandardOpenOption.CREATE_NEW);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "isolated_native_export_block_entity")
    public static void reusableNativeCaptureRetainsAuthoredBlockEntityData(GameTestHelper h) {
        try (var site = new Site(h)) {
            site.fill(site.source, Blocks.AIR.defaultBlockState());
            var pos = site.root; var state = Blocks.CHEST.defaultBlockState(); site.level.setBlock(pos, state, 18);
            var chest = (ChestBlockEntity) site.level.getBlockEntity(pos);
            chest.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 7));
            chest.setCustomName(net.minecraft.network.chat.Component.literal("Export fixture"));
            var nbt = NativeStructureExport.capture(site.level, site.source, Map.of(pos, state));
            h.assertTrue(nbt.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).contains("nbt"), "Native authored block-entity NBT retained");
            var min = NativeStructureExport.minimum(site.destination); site.fill(site.destination, Blocks.AIR.defaultBlockState());
            var template = site.level.getStructureManager().readStructure(nbt);
            h.assertTrue(template.placeInWorld(site.level, min, min, NativeStructureExport.exactPlacement(), RandomSource.create(0), 18), "Block entity template placed");
            var restored = (ChestBlockEntity) site.level.getBlockEntity(min.offset(pos.subtract(NativeStructureExport.minimum(site.source))));
            h.assertTrue(restored != null && restored.getItem(0).is(Items.AMETHYST_SHARD) && restored.getItem(0).getCount() == 7
                    && restored.getDisplayName().getString().equals("Export fixture"), "Container contents/name survive native round-trip");
        }
        h.succeed();
    }
}
