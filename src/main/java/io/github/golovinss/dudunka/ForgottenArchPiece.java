package io.github.golovinss.dudunka;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Generation only. All writes remain in the owning unfinished chunk; no load/startup retrofit. */
public final class ForgottenArchPiece extends StructurePiece {
    public ForgottenArchPiece(BlockPos center) {
        super(DudunkaMod.FORGOTTEN_ARCH_PIECE.get(), 0, new BoundingBox(center.getX()-7, center.getY()-8,
                center.getZ()-7, center.getX()+6, center.getY()+12, center.getZ()+6));
    }
    public ForgottenArchPiece(CompoundTag tag) { super(DudunkaMod.FORGOTTEN_ARCH_PIECE.get(), tag); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

    public static boolean generationAllowed(WorldGenLevel level, ChunkPos chunk) {
        if(!(level instanceof WorldGenRegion) || !level.getLevel().dimension().equals(Level.OVERWORLD))return false;
        var owningChunk=level.getChunk(chunk.x,chunk.z);
        return !owningChunk.isUpgrading() && !owningChunk.getStatus().isOrAfter(ChunkStatus.FEATURES);
    }
    /** Read-only preflight also used by focused terrain-preservation tests. */
    public static boolean clearSite(WorldGenLevel level, BlockPos root, BoundingBox bounds) {
        if (!level.getBiome(root).is(BiomeTags.IS_FOREST)) return false;
        var plan = plan(root);
        for (var p : BlockPos.betweenClosed(root.offset(-2,-1,-1), root.offset(2,3,1))) {
            if (!bounds.isInside(p) || level.isOutsideBuildHeight(p) || !level.ensureCanWrite(p)
                    || !level.getLevel().getWorldBorder().isWithinBounds(p)) return false;
            var state = level.getBlockState(p);
            if (p.getY() == root.getY()-1) {
                if (!state.is(BlockTags.DIRT) || !state.isFaceSturdy(level,p,Direction.UP)
                        || !state.getFluidState().isEmpty()) return false;
            } else if (!state.isAir() && (plan.containsKey(p) || !state.canBeReplaced()
                    || !state.getCollisionShape(level,p).isEmpty() || !state.getFluidState().isEmpty())) return false;
            if (level.getBlockEntity(p) != null) return false;
        }
        for (var p : plan.keySet()) if (!bounds.isInside(p) || !level.ensureCanWrite(p)
                || !level.getBlockState(p).isAir()) return false;
        return true;
    }
    private static Map<BlockPos,BlockState> plan(BlockPos root) {
        var plan = new LinkedHashMap<BlockPos,BlockState>();
        for (int part=0;part<12;part++) plan.put(FamilyGatewayBlock.cell(root,part),
                DudunkaMod.FAMILY_GATEWAY.get().defaultBlockState().setValue(FamilyGatewayBlock.PART,part));
        for (int side : new int[]{-2,2}) {
            plan.put(root.offset(side,0,0),Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            plan.put(root.offset(side,1,0),Blocks.MOSS_CARPET.defaultBlockState());
            plan.put(root.offset(side,0,-1),Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH,true));
        }
        return plan;
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        if (!generationAllowed(level,chunk)) return;
        // At most 64 small, terrain-relative sites, all inside this one unfinished chunk.
        for (int radius=0;radius<=4;radius++) for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int x=chunk.getMiddleBlockX()+dx,z=chunk.getMiddleBlockZ()+dz;
            if ((x&15)<4 || (x&15)>11 || (z&15)<4 || (z&15)>11) continue;
            var root=new BlockPos(x,level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z),z);
            if (!boundingBox.isInside(root) || !clearSite(level,root,boundingBox)) continue;
            var plan=plan(root);
            if (plan.keySet().stream().anyMatch(p->!chunkBounds.isInside(p))) continue;
            plan.forEach((pos,state)->level.setBlock(pos,state,2));
            // WorldGenRegion writes provisional DUMMY NBT for every EntityBlock state. Only
            // the root has an entity; discard the other eleven provisional records before promotion.
            var owningChunk=level.getChunk(chunk.x,chunk.z);
            for(int part=0;part<12;part++)if(part!=FamilyGatewayBlock.ROOT)
                owningChunk.removeBlockEntity(FamilyGatewayBlock.cell(root,part));
            if (level.getBlockEntity(root) instanceof FamilyGatewayEntity gateway) gateway.markForgottenArch();
            return;
        }
    }
}
