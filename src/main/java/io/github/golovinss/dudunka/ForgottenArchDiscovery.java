package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;

/** A local magical echo, never a prediction of ungenerated structures or a global /locate call. */
public final class ForgottenArchDiscovery {
    public static final int CHUNK_RADIUS=6,ROOT_LIMIT=64;
    public enum State { LOCKED, NOT_SEARCHED, FOUND, NO_ECHO, OTHER_DIMENSION }
    public record Echo(State state,BlockPos target) {
        public Echo {
            if(state==null || (state==State.FOUND)!=(target!=null))throw new IllegalArgumentException("Invalid arch echo");
        }
        public static Echo of(State state) { return new Echo(state,null); }
    }
    private ForgottenArchDiscovery() {}
    public static Echo find(ServerPlayer player) {
        if(MysteryAdventure.get(player.server).chapters(player.getUUID())<3)return Echo.of(State.LOCKED);
        var level=player.serverLevel();
        if(!level.dimension().equals(Level.OVERWORLD))return Echo.of(State.OTHER_DIMENSION);
        var origin=new ChunkPos(player.blockPosition());BlockPos closest=null;double distance=Double.MAX_VALUE;
        for(int dx=-CHUNK_RADIUS;dx<=CHUNK_RADIUS;dx++)for(int dz=-CHUNK_RADIUS;dz<=CHUNK_RADIUS;dz++) {
            var chunk=level.getChunkSource().getChunkNow(origin.x+dx,origin.z+dz);
            if(chunk==null)continue;
            int checked=0;
            for(var pos:chunk.getBlockEntitiesPos()) {
                if(++checked>ROOT_LIMIT)break;
                var state=chunk.getBlockState(pos);
                if(!state.is(DudunkaMod.FAMILY_GATEWAY.get()) || state.getValue(FamilyGatewayBlock.PART)!=FamilyGatewayBlock.ROOT
                        || !(chunk.getBlockEntity(pos) instanceof FamilyGatewayEntity gate) || !gate.forgottenArch()
                        || !FamilyGatewayBlock.complete(level,pos) || !level.getWorldBorder().isWithinBounds(pos))continue;
                double next=pos.distSqr(player.blockPosition());
                if(next<distance || next==distance && (closest==null || pos.asLong()<closest.asLong())) {
                    closest=pos.immutable();distance=next;
                }
            }
        }
        return closest==null?Echo.of(State.NO_ECHO):new Echo(State.FOUND,closest);
    }
}
