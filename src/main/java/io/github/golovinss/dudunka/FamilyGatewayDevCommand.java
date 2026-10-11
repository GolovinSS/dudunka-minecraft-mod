package io.github.golovinss.dudunka;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Explicit opt-in setup for locally exercising Stage 3.7 gateway awakening. */
final class FamilyGatewayDevCommand {
    private FamilyGatewayDevCommand() {}

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("prepare_family").executes(context -> prepare(context.getSource()));
    }

    private static int prepare(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!Boolean.getBoolean(MushroomVale.DEV_PROPERTY)||!source.hasPermission(2))throw error("message.dudunka.dev_family.disabled");
        ServerPlayer player=source.getPlayerOrException();
        if(!player.isAlive()||player.isSpectator()||player.level().dimension()!=Level.OVERWORLD)
            throw error("message.dudunka.dev_family.player");
        ServerLevel level=player.serverLevel();var runs=FamilyGatewayDevRuns.get(player.server);
        if(runs.prepared(player.getUUID()))throw error("message.dudunka.dev_family.repeated");
        var existing=level.getEntitiesOfClass(Companion.class,new AABB(player.blockPosition()).inflate(16),mob->player.getUUID().equals(mob.ownerId())&&mob.isAlive());
        if(!existing.isEmpty())throw error("message.dudunka.dev_family.existing");

        var created=new EnumMap<Kind,Companion>(Kind.class);
        var positions=findSpawnPositions(level,player);
        if(positions.size()<Kind.values().length)throw error("message.dudunka.dev_family.no_space");
        int index=0;
        for(Kind kind:Kind.values()) {
            Companion mob=DudunkaMod.TYPES.get(kind).get().create(level);
            if(mob==null)throw error("message.dudunka.dev_family.failed");
            BlockPos pos=positions.get(index++);
            mob.initialize(player.getUUID(),pos);
            mob.prepareFamilyGatewayDeveloperTest(FamilyGatewayRules.TRUST);
            mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,player.getYRot(),0);
            created.put(kind,mob);
        }
        var added=new ArrayList<Companion>();
        for(var mob:created.values()) {
            if(!level.addFreshEntity(mob)){added.forEach(Companion::discard);created.values().stream().filter(e->!e.isAddedToWorld()).forEach(Companion::discard);throw error("message.dudunka.dev_family.failed");}
            added.add(mob);
        }
        var family=new ArrayList<>(created.values());var ledger=FamilyFriendships.get(player.server);
        for(int a=0;a<family.size();a++)for(int b=a+1;b<family.size();b++) {
            var first=family.get(a);var second=family.get(b);
            ledger.ensureScoreForDeveloper(player.getUUID(),first.getUUID(),first.kind,second.getUUID(),second.kind,FamilyGatewayRules.FRIENDSHIP);
        }
        runs.markPrepared(player.getUUID());
        source.sendSuccess(()->Component.translatable("message.dudunka.dev_family.success"),false);
        return 1;
    }

    private static List<BlockPos> findSpawnPositions(ServerLevel level,ServerPlayer player) {
        var origin=player.blockPosition();var offsets=List.of(new int[]{3,0},new int[]{0,3},new int[]{-3,0},new int[]{0,-3},new int[]{3,3},new int[]{-3,3},new int[]{-3,-3},new int[]{3,-3},new int[]{5,0},new int[]{0,5},new int[]{-5,0},new int[]{0,-5});
        var result=new ArrayList<BlockPos>();var occupied=new ArrayList<AABB>();
        for(var offset:offsets) {
            BlockPos column=origin.offset(offset[0],0,offset[1]);if(!level.hasChunkAt(column))continue;
            BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column);
            if(Vec3.atBottomCenterOf(pos).distanceToSqr(Vec3.atBottomCenterOf(origin))>FamilyGatewayRules.RADIUS*FamilyGatewayRules.RADIUS)continue;
            if(!safeGround(level,pos))continue;
            boolean fit=true;
            for(Kind kind:Kind.values()) {
                var type=DudunkaMod.TYPES.get(kind).get();var dimensions=type.getDimensions().scale(kind==Kind.SYUSYA?.935f:kind==Kind.MARUSYA?.99f:.88f);
                var box=dimensions.makeBoundingBox(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                if(!level.noCollision(null,box)||level.getEntities((net.minecraft.world.entity.Entity)null,box.inflate(.1),entity->entity.isAlive()).size()>0||occupied.stream().anyMatch(box::intersects)){fit=false;break;}
            }
            if(fit){result.add(pos.immutable());occupied.add(new AABB(pos.getX()+.1,pos.getY(),pos.getZ()+.1,pos.getX()+.9,pos.getY()+2,pos.getZ()+.9));if(result.size()==Kind.values().length)break;}
        }
        return result;
    }

    private static boolean safeGround(ServerLevel level,BlockPos feet) {
        if(feet.getY()<=level.getMinBuildHeight()||feet.getY()+1>=level.getMaxBuildHeight()||!level.hasChunkAt(feet)||!level.hasChunkAt(feet.above(1)))return false;
        BlockState floor=level.getBlockState(feet.below());
        if(!floor.isFaceSturdy(level,feet.below(),net.minecraft.core.Direction.UP)||!floor.getFluidState().isEmpty())return false;
        for(int y=0;y<=1;y++){BlockPos p=feet.above(y);var state=level.getBlockState(p);if(!state.isAir()||!state.getFluidState().isEmpty()||!state.getCollisionShape(level,p).isEmpty())return false;}
        return true;
    }

    private static com.mojang.brigadier.exceptions.CommandSyntaxException error(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
}
