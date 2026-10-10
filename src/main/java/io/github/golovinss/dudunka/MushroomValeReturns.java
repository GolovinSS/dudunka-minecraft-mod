package io.github.golovinss.dudunka;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Developer travel bookmarks, separate from player and companion NBT. */
public final class MushroomValeReturns extends SavedData {
    public record ReturnPoint(ResourceLocation dimension, BlockPos pos, float yaw, float pitch) {}
    private final Map<UUID, ReturnPoint> entries = new HashMap<>();

    public static MushroomValeReturns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MushroomValeReturns::load, MushroomValeReturns::new, "dudunka_mushroom_vale_returns");
    }

    public ReturnPoint point(UUID player) { return entries.get(player); }
    public void remember(UUID player, ReturnPoint point) { entries.put(player, point); setDirty(); }
    public void forget(UUID player) { if (entries.remove(player) != null) setDirty(); }

    public static MushroomValeReturns load(CompoundTag tag) {
        var result = new MushroomValeReturns();
        var rows = tag.getList("Returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
            if (!row.hasUUID("Player") || dimension == null || dimension.equals(MushroomVale.ID)
                    || !row.contains("Pos", Tag.TAG_LONG) || !row.contains("Yaw", Tag.TAG_FLOAT)
                    || !row.contains("Pitch", Tag.TAG_FLOAT)) continue;
            var pos = BlockPos.of(row.getLong("Pos"));
            float yaw = row.getFloat("Yaw"), pitch = row.getFloat("Pitch");
            if (Math.abs(pos.getX()) > 29999984 || Math.abs(pos.getZ()) > 29999984
                    || pos.getY() < -2048 || pos.getY() > 2047
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) continue;
            result.entries.putIfAbsent(row.getUUID("Player"), new ReturnPoint(dimension, pos, yaw, pitch));
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        var rows = new ListTag();
        entries.forEach((player, point) -> {
            var row = new CompoundTag();
            row.putUUID("Player", player);
            row.putString("Dimension", point.dimension().toString());
            row.putLong("Pos", point.pos().asLong());
            row.putFloat("Yaw", point.yaw());
            row.putFloat("Pitch", point.pitch());
            rows.add(row);
        });
        tag.put("Returns", rows);
        return tag;
    }
}
