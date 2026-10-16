package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;

/** Native starts are selected against dry noise terrain, before vegetation decoration. */
public final class ValeAdventureStructure extends Structure {
    public enum Landmark implements StringRepresentable {
        MUSIC_BELLS("music_bells", "glowing_mushroom_forest"),
        JUMPER_DANCE("jumper_dance", "pink_meadows"),
        MEMORY_STONE("memory_stone", "memory_forest"),
        SILVER_WEB_HARP("silver_web_harp", "memory_forest"),
        SINGING_MUSHROOM("singing_mushroom", "glowing_mushroom_forest");
        public static final Codec<Landmark> CODEC = StringRepresentable.fromEnum(Landmark::values);
        public final String id, biome;
        Landmark(String id, String biome) { this.id = id; this.biome = biome; }
        @Override public String getSerializedName() { return id; }
    }
    public static final Codec<ValeAdventureStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i), Landmark.CODEC.fieldOf("landmark").forGetter(s -> s.landmark))
            .apply(i, ValeAdventureStructure::new));
    public final Landmark landmark;
    public ValeAdventureStructure(StructureSettings settings, Landmark landmark) {
        super(settings); this.landmark = landmark;
    }
    public static boolean eligible(Landmark kind, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, kind.biome));
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext c) {
        // GenerationContext has no dimension key. The Vale's exclusive noise-settings key
        // gates starts; placement additionally requires the actual dimension key.
        if (!(c.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !noise.stable(ResourceKey.create(Registries.NOISE_SETTINGS, MushroomVale.ID))
                || c.heightAccessor() instanceof ChunkAccess chunk && chunk.isUpgrading()) return Optional.empty();
        int x = c.chunkPos().getMiddleBlockX(), z = c.chunkPos().getMiddleBlockZ();
        int[] ground = new int[169];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int y = noise.getFirstFreeHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG,
                    c.heightAccessor(), c.randomState());
            int clearance = landmark == Landmark.MUSIC_BELLS ? 6 : landmark == Landmark.JUMPER_DANCE ? 4 : 12;
            if (y <= noise.getSeaLevel() || y + clearance >= c.heightAccessor().getMaxBuildHeight()) return Optional.empty();
            var column = noise.getBaseColumn(x + dx, z + dz, c.heightAccessor(), c.randomState());
            if (!column.getBlock(y - 1).getFluidState().isEmpty() || column.getBlock(y - 1).isAir()) return Optional.empty();
            var biome = c.biomeSource().getNoiseBiome(QuartPos.fromBlock(x + dx), QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z + dz), c.randomState().sampler());
            if (!eligible(landmark, biome) || !c.validBiome().test(biome)) return Optional.empty();
            ground[(dx + 6) * 13 + dz + 6] = y;
            min = Math.min(min, y); max = Math.max(max, y);
            if (max - min > 2) return Optional.empty();
        }
        var root = new BlockPos(x, max, z);
        if (landmark == Landmark.MUSIC_BELLS && !ValeAdventurePiece.styledGround(root, ground)) return Optional.empty();
        return Optional.of(new GenerationStub(root, b -> b.addPiece(new ValeAdventurePiece(landmark, root, ground))));
    }
    @Override public StructureType<?> type() { return DudunkaMod.VALE_ADVENTURE_TYPE.get(); }
}
