package io.github.golovinss.dudunka;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** One shared exit in the loaded arrival chunk. The chunk itself persists the gate; destinations stay per player. */
public final class MushroomValeReturnGateway {
    private static final ChunkPos ARRIVAL_CHUNK = new ChunkPos(MushroomVale.ENTRY);
    private MushroomValeReturnGateway() {}

    /** Inspects only block entities in one already loaded chunk, including gates saved by earlier sessions. */
    public static BlockPos find(ServerLevel level) {
        if (!level.dimension().equals(MushroomVale.LEVEL)) return null;
        var chunk = level.getChunkSource().getChunkNow(ARRIVAL_CHUNK.x, ARRIVAL_CHUNK.z);
        if (chunk == null) return null;
        return roots(chunk).stream()
                .filter(root -> FamilyGatewayBlock.complete(level, root))
                .min(Comparator.comparingLong(BlockPos::asLong)).orElse(null);
    }

    private static List<BlockPos> roots(LevelChunk chunk) {
        // Include deferred saved NBT as well as live block entities. Reading states instantiates neither.
        return chunk.getBlockEntitiesPos().stream().filter(pos -> {
            var state = chunk.getBlockState(pos);
            return state.is(DudunkaMod.FAMILY_GATEWAY.get()) && state.getValue(FamilyGatewayBlock.PART) == FamilyGatewayBlock.ROOT;
        }).map(BlockPos::immutable).toList();
    }

    static BlockPos landing(ServerLevel level, BlockPos root, ServerPlayer player) {
        for (int dz : new int[]{-2, 2}) for (int dx : new int[]{0, -1, 1}) {
            var column = root.offset(dx, 0, dz);
            if (!interior(column)) continue;
            var pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (MushroomVale.safeLanding(level, pos, player)) return pos;
        }
        return null;
    }

    /** Called only after successful physical gateway entry. Failure leaves travel and its bookmark intact. */
    public static BlockPos ensure(ServerPlayer player) {
        var level = player.serverLevel();
        if (!level.dimension().equals(MushroomVale.LEVEL)
                || !new ChunkPos(player.blockPosition()).equals(ARRIVAL_CHUNK)) return null;
        var chunk = level.getChunkSource().getChunkNow(ARRIVAL_CHUNK.x, ARRIVAL_CHUNK.z);
        if (chunk == null) {
            LogUtils.getLogger().warn("Return gateway unavailable: owner={}, reason=arrival chunk not FULL", player.getUUID());
            return null;
        }
        var existing = find(level);
        if (existing != null) return existing;
        // Do not create a second frame beside an incomplete saved gate. The command remains available.
        if (!roots(chunk).isEmpty()) {
            LogUtils.getLogger().warn("Return gateway unavailable: owner={}, reason=incomplete existing frame", player.getUUID());
            return null;
        }
        var anchor = player.blockPosition();
        var rejected = new LinkedHashMap<String, Integer>();
        var samples = new ArrayList<String>();
        int examined = 0;
        // Search only the already-loaded arrival chunk. Terrain can fall several blocks
        // between the player's landing ridge and the nearest level frame site.
        for (int radius = 0; radius <= 15; radius++)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var column = anchor.offset(dx, 0, dz);
                if (!interior(column)) { reject(rejected, samples, column, "chunk edge clearance"); examined++; continue; }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ());
                BlockPos root = column.atY(surfaceY);
                // Reserve the whole frame, surrounding clearance and both approach lanes inside this chunk.
                examined++;
                if (!interior(root.offset(-1, 0, -2)) || !interior(root.offset(1, 0, 2))) {
                    reject(rejected, samples, root, "frame/approach crosses chunk edge"); continue;
                }
                if (!FamilyGatewayItem.canPlaceReturnStructure(level, root, player)) {
                    reject(rejected, samples, root, "frame clearance or placement protection"); continue;
                }
                if (!MushroomVale.safeLanding(level, root, player)) {
                    reject(rejected, samples, root, "unsafe frame approach"); continue;
                }
                if (landing(level, root, player) == null) {
                    reject(rejected, samples, root, "no safe approach lane"); continue;
                }
                // A protection veto ends this attempt, rather than probing more territory with placement events.
                if (FamilyGatewayItem.placeStructure(level, root, player, true)) return root;
                reject(rejected, samples, root, "placement event veto or block write failure");
                LogUtils.getLogger().warn("Return gateway placement failed: owner={}, examined={}, rejected={}, samples={}",
                        player.getUUID(), examined, rejected, samples);
                return null;
            }
        LogUtils.getLogger().warn("Return gateway site unavailable: owner={}, examined={}, rejected={}, samples={}",
                player.getUUID(), examined, rejected, samples);
        return null;
    }

    private static void reject(Map<String, Integer> counts, List<String> samples, BlockPos pos, String reason) {
        counts.merge(reason, 1, Integer::sum);
        if (samples.size() < 4) samples.add(pos.toShortString() + "=" + reason);
    }

    private static boolean interior(BlockPos pos) {
        return new ChunkPos(pos).equals(ARRIVAL_CHUNK)
                && (pos.getX() & 15) > 0 && (pos.getX() & 15) < 15
                && (pos.getZ() & 15) > 0 && (pos.getZ() & 15) < 15;
    }
}
