package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only native starts from at most 25 already-loaded chunks per half-second/player. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class ValeMusicLandmarkHints {
    public static final int CHECK_TICKS = 10;
    public static final int HINT_RADIUS = 32;
    private static final Map<MinecraftServer, Map<UUID, ValeMusicHints.Publisher>> PUBLISHERS = new WeakHashMap<>();

    public static List<ValeMusicHints.Anchor> nearby(ServerLevel level, Vec3 position) {
        if (!level.dimension().equals(MushroomVale.LEVEL)) return List.of();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structures = new LinkedHashMap<ValeAdventureStructure.Landmark, net.minecraft.world.level.levelgen.structure.Structure>();
        for (var kind : ValeAdventureStructure.Landmark.values()) {
            var structure = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, kind.id));
            if (structure instanceof ValeAdventureStructure nativeStructure && nativeStructure.landmark == kind)
                structures.put(kind, structure);
        }
        var result = new LinkedHashSet<ValeMusicHints.Anchor>();
        var playerPos = BlockPos.containing(position);
        int cx = playerPos.getX() >> 4, cz = playerPos.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            for (var entry : structures.entrySet()) {
                var start = chunk.getStartForStructure(entry.getValue());
                if (start == null || !start.isValid()) continue;
                for (var part : start.getPieces()) {
                    if (!(part instanceof ValeAdventurePiece piece) || piece.landmark() != entry.getKey()) continue;
                    var root = piece.anchor();
                    if ((root.getX() >> 4) != cx + dx || (root.getZ() >> 4) != cz + dz
                            || root.distToCenterSqr(position) > HINT_RADIUS * HINT_RADIUS || !intact(level, piece)) continue;
                    result.add(new ValeMusicHints.Anchor(root.getX(), root.getY(), root.getZ()));
                    if (result.size() == ValeMusicHints.MAX_ANCHORS) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean intact(ServerLevel level, ValeAdventurePiece piece) {
        for (var entry : piece.interactionPlan().entrySet()) {
            var pos = entry.getKey();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return false;
            var actual = chunk.getBlockState(pos);
            // Match the Harp quest's ordinary pane-connection tolerance, including dryness.
            if (entry.getValue().is(Blocks.WHITE_STAINED_GLASS_PANE)) {
                if (!actual.is(Blocks.WHITE_STAINED_GLASS_PANE)
                        || actual.getValue(BlockStateProperties.WATERLOGGED)) return false;
            } else if (!actual.equals(entry.getValue())) return false;
        }
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.serverLevel().dimension().equals(MushroomVale.LEVEL) || !ValeMusicNetwork.supported(player)) {
            clear(player);
            return;
        }
        if (player.tickCount % CHECK_TICKS != 0) return;
        var publisher = PUBLISHERS.computeIfAbsent(player.server, s -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), id -> new ValeMusicHints.Publisher());
        var snapshot = new ValeMusicHints.Snapshot(MushroomVale.ID.toString(),
                player.isAlive() ? nearby(player.serverLevel(), player.position()) : List.of());
        // Entity tickCount restarts on respawn; publication leases use the server clock instead.
        if (publisher.shouldSend(snapshot, player.server.getTickCount())) ValeMusicNetwork.send(player, snapshot);
    }

    private static void clear(ServerPlayer player) {
        var publishers = PUBLISHERS.get(player.server);
        if (publishers != null) publishers.remove(player.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    private ValeMusicLandmarkHints() {}
}
