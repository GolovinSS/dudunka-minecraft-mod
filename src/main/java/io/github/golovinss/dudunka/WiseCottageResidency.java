package io.github.golovinss.dudunka;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-thread discovery of loaded block blueprints; never called by world generation. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class WiseCottageResidency extends SavedData {
    private static final String FILE = "dudunka_wise_cottages";
    public record Cottage(BlockPos origin, Rotation rotation, UUID resident) {
        public BlockPos home() { return origin.offset(new BlockPos(0, 1, -1).rotate(rotation)); }
    }
    private final Map<BlockPos, Cottage> cottages = new HashMap<>();
    private final Map<BlockPos, Cottage> pufikCottages = new HashMap<>();

    public static WiseCottageResidency get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                WiseCottageResidency::load, WiseCottageResidency::new, FILE);
    }
    public Cottage cottage(BlockPos origin) { return cottages.get(origin); }
    public Cottage pufikCottage(BlockPos origin) { return pufikCottages.get(origin); }
    public static WiseCottageResidency load(CompoundTag tag) {
        var data = new WiseCottageResidency();
        var rows = tag.getList("Cottages", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            int rotation = row.getInt("Rotation");
            if (!row.contains("Origin", Tag.TAG_LONG) || !row.hasUUID("Resident") || rotation < 0 || rotation > 3) continue;
            BlockPos origin = BlockPos.of(row.getLong("Origin"));
            if (Math.abs(origin.getX()) > 29999984 || Math.abs(origin.getZ()) > 29999984) continue;
            data.cottages.putIfAbsent(origin, new Cottage(origin, Rotation.values()[rotation], row.getUUID("Resident")));
        }
        rows = tag.getList("PufikCottages", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i); int rotation = row.getInt("Rotation");
            if (!row.contains("Origin", Tag.TAG_LONG) || !row.hasUUID("Resident") || rotation < 0 || rotation > 3) continue;
            BlockPos origin = BlockPos.of(row.getLong("Origin"));
            if (Math.abs(origin.getX()) <= 29999984 && Math.abs(origin.getZ()) <= 29999984)
                data.pufikCottages.putIfAbsent(origin, new Cottage(origin, Rotation.values()[rotation], row.getUUID("Resident")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var rows = new ListTag();
        cottages.values().forEach(cottage -> {
            var row = new CompoundTag();
            row.putLong("Origin", cottage.origin().asLong()); row.putInt("Rotation", cottage.rotation().ordinal());
            row.putUUID("Resident", cottage.resident()); rows.add(row);
        });
        tag.put("Cottages", rows);
        var pufikRows = new ListTag();
        pufikCottages.values().forEach(cottage -> {
            var row = new CompoundTag(); row.putLong("Origin", cottage.origin().asLong());
            row.putInt("Rotation", cottage.rotation().ordinal()); row.putUUID("Resident", cottage.resident()); pufikRows.add(row);
        });
        tag.put("PufikCottages", pufikRows); return tag;
    }

    /** Durable issuance BEFORE entity creation. Absence of the entity never clears this tombstone. */
    private void flushIssuance(ServerLevel level) throws IOException {
        Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/" + FILE + ".dat");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), FILE, ".tmp");
        try {
            var root = new CompoundTag(); root.put("data", save(new CompoundTag())); NbtUtils.addCurrentDataVersion(root);
            NbtIo.writeCompressed(root, temp.toFile());
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    /** Recognizes the existing furnished blueprint, ignoring door/book/redstone state and movable props. */
    public static boolean matches(ServerLevel level, BlockPos origin, Rotation rotation) {
        if (!level.dimension().equals(MushroomVale.LEVEL) || !WiseFlyAgaric.habitat(level, origin)
                || !loadedSquare(level, origin, 7)) return false;
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(
                ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/wise_fly_agaric_cottage"));
        if (feature == null || !(feature.config() instanceof MushroomValeDecorFeature.Blueprint blueprint)) return false;
        for (var piece : blueprint.blocks()) {
            var block = piece.state().getBlock();
            // Structural fingerprint: floor, walls, dome, library and lectern. No terrain or block writes.
            if (block != Blocks.BIRCH_PLANKS && block != Blocks.STRIPPED_BIRCH_WOOD
                    && block != Blocks.RED_MUSHROOM_BLOCK && block != Blocks.BOOKSHELF && block != Blocks.LECTERN) continue;
            if (!level.getBlockState(origin.offset(piece.offset().rotate(rotation))).is(block)) return false;
        }
        return true;
    }
    public static boolean matchesPufik(ServerLevel level, BlockPos origin, Rotation rotation) {
        if (!level.dimension().equals(MushroomVale.LEVEL) || !level.getBiome(origin).unwrapKey().map(k -> k.location().equals(
                ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "pink_meadows"))).orElse(false)
                || !loadedSquare(level, origin, 6)) return false;
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(
                ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/pufik_cottage"));
        if (feature == null || !(feature.config() instanceof MushroomValeDecorFeature.Blueprint blueprint)) return false;
        for (var piece : blueprint.blocks()) {
            var block = piece.state().getBlock();
            if (block != Blocks.BIRCH_PLANKS && block != Blocks.STRIPPED_BIRCH_WOOD
                    && block != Blocks.RED_MUSHROOM_BLOCK && block != Blocks.SPRUCE_DOOR) continue;
            if (!level.getBlockState(origin.offset(piece.offset().rotate(rotation))).is(block)) return false;
        }
        return true;
    }
    public static boolean loadedSquare(ServerLevel level, BlockPos center, int radius) {
        for (int x = (center.getX()-radius)>>4; x <= (center.getX()+radius)>>4; x++)
            for (int z = (center.getZ()-radius)>>4; z <= (center.getZ()+radius)>>4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    /** Idempotent even if invoked twice by different visitors in the same tick. */
    public Cottage activate(ServerLevel level, BlockPos origin, Rotation rotation) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Residency requires the server thread");
        if (!level.dimension().equals(MushroomVale.LEVEL)) return null;
        var known = cottage(origin);
        if (known != null) return known; // Never use getEntity(uuid)==null as evidence of death or absence.
        if (!matches(level, origin, rotation)) return null;
        var entry = new Cottage(origin.immutable(), rotation, UUID.randomUUID());
        if (!WiseFlyAgaric.safeCottageNode(level, entry.home())) return null;
        cottages.put(entry.origin(), entry);
        try { flushIssuance(level); }
        catch (IOException failure) {
            // Keep the in-memory reservation too: fail closed, with no entity and no retry in this session.
            LogUtils.getLogger().error("Could not durably issue Wise cottage resident at {}. No entity created.", origin, failure);
            return entry;
        }
        var resident = DudunkaMod.WISE_FLY_AGARIC.get().create(level);
        if (resident != null) {
            resident.setUUID(entry.resident()); resident.setCottage(entry.origin(), entry.rotation());
            resident.moveTo(entry.home().getX()+.5, entry.home().getY(), entry.home().getZ()+.5, 0, 0);
            // A cancelled entity join also consumes issuance: administrators can inspect the ledger.
            level.addFreshEntity(resident);
        }
        return entry;
    }

    public Cottage activatePufik(ServerLevel level, BlockPos origin, Rotation rotation) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Residency requires the server thread");
        if (!level.dimension().equals(MushroomVale.LEVEL)) return null;
        var known = pufikCottage(origin); if (known != null) return known;
        if (!matchesPufik(level, origin, rotation)) return null;
        var home = origin.offset(new BlockPos(0, 1, -1).rotate(rotation));
        if (!Pufik.safeResidentNode(level, home)) return null;
        var entry = new Cottage(origin.immutable(), rotation, UUID.randomUUID());
        pufikCottages.put(entry.origin(), entry);
        try { flushIssuance(level); }
        catch (IOException failure) {
            LogUtils.getLogger().error("Could not durably issue Pufik cottage resident at {}. No entity created.", origin, failure);
            return entry;
        }
        var resident = DudunkaMod.PUFIK.get().create(level);
        if (resident != null) {
            resident.setUUID(entry.resident()); resident.setCottage(entry.origin(), entry.rotation());
            resident.moveTo(home.getX()+.5, home.getY(), home.getZ()+.5, 0, 0);
            level.addFreshEntity(resident);
        }
        return entry;
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(MushroomVale.LEVEL) || level.getGameTime()%20 != 0) return;
        var players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        long round = level.getGameTime()/20;
        var player = players.get((int)((round/9)%players.size()));
        int slot = (int)(round%9);
        var chunk = level.getChunkSource().getChunkNow((player.blockPosition().getX()>>4)+slot%3-1,
                (player.blockPosition().getZ()>>4)+slot/3-1);
        if (chunk == null) return;
        int examined = 0;
        // Pufik cottages have no block entities. Inspect only this already-loaded visitor chunk.
        int minY=Math.max(level.getMinBuildHeight(),player.blockPosition().getY()-12);
        int maxY=Math.min(level.getMaxBuildHeight(),player.blockPosition().getY()+13);
        int doorCandidates=0;
        scanPufik: for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = minY; y < maxY; y++) {
            var doorPos = new BlockPos((chunk.getPos().x << 4) + x, y, (chunk.getPos().z << 4) + z);
            var door = level.getBlockState(doorPos);
            if (!door.is(Blocks.SPRUCE_DOOR) || !door.hasProperty(DoorBlock.FACING)) continue;
            if (++doorCandidates > 16) break scanPufik;
            var rotation = switch (door.getValue(DoorBlock.FACING)) {
                case NORTH -> Rotation.NONE; case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180; case WEST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            var origin = doorPos.subtract(new BlockPos(0, 1, -2).rotate(rotation));
            if (player.distanceToSqr(origin.getX()+.5, origin.getY(), origin.getZ()+.5) > 48*48) continue;
            var data = get(level);
            if (data.pufikCottage(origin) == null && data.activatePufik(level, origin, rotation) != null) return;
        }
        for (var be : chunk.getBlockEntities().values()) {
            if (++examined > 64) break;
            if (!(be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity)) continue;
            for (var rotation : Rotation.values()) {
                var origin = be.getBlockPos().subtract(new BlockPos(1, 1, 1).rotate(rotation));
                if (player.distanceToSqr(origin.getX()+.5, origin.getY(), origin.getZ()+.5) > 48*48) continue;
                var data = get(level);
                if (data.cottage(origin) != null) break;
                if (data.activate(level, origin, rotation) != null) return; // At most one issuance per second.
            }
        }
    }
}
