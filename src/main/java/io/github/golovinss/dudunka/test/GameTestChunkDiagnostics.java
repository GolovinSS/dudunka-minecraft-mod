package io.github.golovinss.dudunka.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Read-only fixture diagnostics: never request a chunk while explaining readiness. */
final class GameTestChunkDiagnostics {
    static String describe(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var list = level.getServer().getPlayerList();
        return "dimension=" + level.dimension().location() + ", pos=" + pos + ", chunk=" + new ChunkPos(pos)
                + ", fullStatus=" + (chunk == null ? "unloaded" : chunk.getFullStatus())
                + ", entitiesLoaded=" + level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                + ", entityTicking=" + level.isPositionEntityTicking(pos)
                + ", forced=" + level.getForcedChunks().contains(new ChunkPos(pos).toLong())
                + ", view=" + list.getViewDistance() + ", simulation=" + list.getSimulationDistance()
                + ", players=" + level.players().stream().map(p -> p.getUUID() + "@" + p.position()
                        + ":spectator=" + p.isSpectator()).toList();
    }
    private GameTestChunkDiagnostics() {}
}
