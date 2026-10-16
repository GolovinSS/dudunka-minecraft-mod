package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small cap-independent assistance, only in currently simulated, occupied music clearings. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class ValeLandmarkFireflies {
    public static final int CHECK_TICKS=100, TARGET=3, PLAYER_RADIUS=24, COUNT_RADIUS=16;
    private static boolean supported(ValeAdventurePiece piece) {
        return piece.landmark()==ValeAdventureStructure.Landmark.MUSIC_BELLS
                || piece.landmark()==ValeAdventureStructure.Landmark.JUMPER_DANCE;
    }
    public static boolean enabled(ServerLevel level) {
        return level.dimension().equals(MushroomVale.LEVEL)
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                && level.getServer().isSpawningAnimals() && ValeFirefly.activeTime(level.getDayTime());
    }
    private static boolean occupied(ServerLevel level,BlockPos root) {
        return level.players().stream().anyMatch(p->p.isAlive() && !p.isSpectator()
                && root.distToCenterSqr(p.position())<=PLAYER_RADIUS*PLAYER_RADIUS);
    }
    /** No cached anchors: unloaded/distant pieces disappear from consideration immediately. */
    public static List<ValeAdventurePiece> nearby(ServerLevel level) {
        var found=new LinkedHashMap<BlockPos,ValeAdventurePiece>();
        var inspected=new HashSet<Long>();
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(var player:level.players()) {
            if(!player.isAlive() || player.isSpectator()) continue;
            int cx=player.blockPosition().getX()>>4,cz=player.blockPosition().getZ()>>4;
            for(int x=cx-2;x<=cx+2;x++) for(int z=cz-2;z<=cz+2;z++) {
                if(!inspected.add(net.minecraft.world.level.ChunkPos.asLong(x,z))) continue;
                var chunk=level.getChunkSource().getChunkNow(x,z);
                if(chunk==null) continue;
                for(var kind:List.of(ValeAdventureStructure.Landmark.MUSIC_BELLS,ValeAdventureStructure.Landmark.JUMPER_DANCE)) {
                    var structure=registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID,kind.id));
                    if(structure==null) continue;
                    var start=chunk.getStartForStructure(structure);
                    if(start==null || !start.isValid()) continue;
                    for(var part:start.getPieces()) if(part instanceof ValeAdventurePiece piece
                            && piece.landmark()==kind && occupied(level,piece.anchor())) found.putIfAbsent(piece.anchor(),piece);
                }
            }
        }
        return List.copyOf(found.values());
    }
    /** Shared by the scheduler and focused tests. Rechecks all gates on each invocation. */
    public static int assist(ServerLevel level,ValeAdventurePiece piece) {
        var root=piece.anchor();
        if(!enabled(level) || !supported(piece) || !occupied(level,root)
                || !level.isPositionEntityTicking(root) || !piece.intact(level)) return 0;
        // Require the persisted native start, not a lookalike or a stale cached piece.
        var nativePiece=ValeMusicQuest.pieceAt(level,root,piece.landmark());
        if(nativePiece==null || !nativePiece.anchor().equals(root) || !nativePiece.plan().equals(piece.plan())) return 0;
        var area=new AABB(root).inflate(COUNT_RADIUS);
        int count=level.getEntitiesOfClass(ValeFirefly.class,area,f->f.isAlive()).size();
        if(count>=TARGET) return 0;
        int added=0;
        for(int attempt=0;attempt<12 && count<TARGET;attempt++) {
            double angle=(attempt+level.getRandom().nextDouble()*.3)*Math.PI/3;
            Vec3 column=Vec3.atCenterOf(root).add(Math.cos(angle)*(2+attempt%3),3,Math.sin(angle)*(2+attempt%3));
            var pos=BlockPos.containing(column);
            if(!level.isPositionEntityTicking(pos) || !level.hasChunkAt(pos)) continue;
            double ground=ValeFirefly.groundBelow(level,column,8);
            if(!Double.isFinite(ground)) continue;
            Vec3 spot=new Vec3(column.x,ground+1.5,column.z);
            var fly=DudunkaMod.VALE_FIREFLY.get().create(level);
            if(fly==null) break;
            fly.moveTo(spot.x,spot.y,spot.z,0,0);
            if(!ValeFirefly.safeVolume(level,fly.getBoundingBox().inflate(.2))) continue;
            // EVENT distinguishes assistance from natural packs; Forge position/finalize/join vetoes apply.
            if(!ForgeEventFactory.checkSpawnPosition(fly,level,MobSpawnType.EVENT)) { fly.discard(); break; }
            ForgeEventFactory.onFinalizeSpawn(fly,level,level.getCurrentDifficultyAt(fly.blockPosition()),MobSpawnType.EVENT,null,null);
            if(fly.isSpawnCancelled() || !level.isPositionEntityTicking(fly.blockPosition())
                    || root.distToCenterSqr(fly.position())>64
                    || !ValeFirefly.safeVolume(level,fly.getBoundingBox().inflate(.2))) { fly.discard(); break; }
            fly.setVariant(count%3);
            if(!level.addFreshEntity(fly)) { fly.discard(); break; }
            count++;added++;
            // No persistence flag or tickets: existing flight bounds, saves and despawn vetoes apply.
        }
        return added;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime()%CHECK_TICKS!=0 || !event.haveTime() || !enabled(level)) return;
        for(var piece:nearby(level)) assist(level,piece);
    }
    private ValeLandmarkFireflies() {}
}
