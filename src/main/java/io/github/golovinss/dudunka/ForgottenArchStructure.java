package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;

/** A single-chunk surface ruin, participating in vanilla structure starts/references. */
public final class ForgottenArchStructure extends Structure {
    public static final Codec<ForgottenArchStructure> CODEC = simpleCodec(ForgottenArchStructure::new);
    public ForgottenArchStructure(StructureSettings settings) { super(settings); }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder ->
                builder.addPiece(new ForgottenArchPiece(new BlockPos(x, y, z)))));
    }
    @Override public StructureType<?> type() { return DudunkaMod.FORGOTTEN_ARCH_TYPE.get(); }
}
