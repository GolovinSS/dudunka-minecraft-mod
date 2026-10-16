package io.github.golovinss.dudunka.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Small development/test utility: native world capture with an explicit authored-position mask. */
public final class NativeStructureExport {
    private NativeStructureExport() {}

    public static BlockPos minimum(BoundingBox bounds) {
        return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
    }

    public static CompoundTag capture(ServerLevel level, BoundingBox bounds, Map<BlockPos, BlockState> authored) {
        if (authored.isEmpty() || authored.keySet().stream().anyMatch(p -> !bounds.isInside(p)))
            throw new IllegalArgumentException("Authored blocks exceed the declared export bounds: " + bounds);
        if (bounds.getXSpan() > 48 || bounds.getYSpan() > 48 || bounds.getZSpan() > 48
                || bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight())
            throw new IllegalArgumentException("Export exceeds native Structure Block size/build limits");
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null)
                    throw new IllegalArgumentException("Export requires already-loaded chunks");
        for (var entry : authored.entrySet()) {
            if (entry.getValue().isAir() || entry.getValue().is(Blocks.STRUCTURE_VOID))
                throw new IllegalArgumentException("Unexpected air/structure void in authored layout: " + entry.getKey());
            if (!level.getBlockState(entry.getKey()).equals(entry.getValue()))
                throw new IllegalArgumentException("World differs from evaluated Java layout at " + entry.getKey());
            if (entry.getValue().hasBlockEntity() && level.getBlockEntity(entry.getKey()) == null)
                throw new IllegalArgumentException("Missing authored block entity at " + entry.getKey());
        }
        var min = minimum(bounds);
        var template = new StructureTemplate();
        template.fillFromWorld(level, min, new net.minecraft.core.Vec3i(bounds.getXSpan(), bounds.getYSpan(), bounds.getZSpan()),
                false, Blocks.STRUCTURE_VOID);
        var captured = template.save(new CompoundTag());
        var sparse = new ListTag();
        for (var value : captured.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value;
            if (authored.containsKey(min.offset(position(block)))) sparse.add(block.copy());
        }
        if (sparse.size() != authored.size()) throw new IllegalStateException("Native capture lost authored blocks");
        captured.put("blocks", sparse);
        // Reload/save through Minecraft to compact the palette after excluding untouched terrain/air.
        // Native block-entity NBT on authored positions is retained without custom serialization.
        return level.getStructureManager().readStructure(captured).save(new CompoundTag());
    }

    public static StructurePlaceSettings exactPlacement() {
        return new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true).setKeepLiquids(false);
    }

    public static BlockPos position(CompoundTag block) {
        var pos = block.getList("pos", Tag.TAG_INT);
        if (pos.size() != 3) throw new IllegalArgumentException("Malformed template block coordinate");
        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    /** Strict decode as well as native load: catch duplicates, stray cells and unused palette entries. */
    public static Map<BlockPos, BlockState> states(ServerLevel level, CompoundTag nbt) {
        if (nbt.contains("palettes") || !nbt.getList("entities", Tag.TAG_COMPOUND).isEmpty())
            throw new IllegalArgumentException("Canonical export must have one palette and no entities");
        var palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        var size = nbt.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IllegalArgumentException("Missing template size");
        var states = new LinkedHashMap<BlockPos, BlockState>();
        var used = new HashSet<Integer>();
        var lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (var value : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value; var pos = position(block); int index = block.getInt("state");
            if (index < 0 || index >= palette.size() || pos.getX() < 0 || pos.getX() >= size.getInt(0)
                    || pos.getY() < 0 || pos.getY() >= size.getInt(1) || pos.getZ() < 0 || pos.getZ() >= size.getInt(2))
                throw new IllegalArgumentException("Template block/palette index out of bounds");
            var state = NbtUtils.readBlockState(lookup, palette.getCompound(index));
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || states.put(pos, state) != null)
                throw new IllegalArgumentException("Unexpected air, structure void or duplicate block");
            used.add(index);
        }
        if (used.size() != palette.size()) throw new IllegalArgumentException("Unrelated unused palette entries");
        return states;
    }

    public static byte[] compressed(CompoundTag nbt) throws IOException {
        var output = new ByteArrayOutputStream(); NbtIo.writeCompressed(nbt, output); return output.toByteArray();
    }
}
