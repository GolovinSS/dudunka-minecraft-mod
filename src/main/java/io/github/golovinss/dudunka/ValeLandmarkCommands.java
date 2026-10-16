package io.github.golovinss.dudunka;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.HashSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import static io.github.golovinss.dudunka.ValeAdventureStructure.Landmark;

/** Physical verification for the five exact music IDs; other vanilla locate selectors are unchanged. */
public final class ValeLandmarkCommands {
    private ValeLandmarkCommands() {}

    static void registerLocate(CommandDispatcher<CommandSourceStack> dispatcher) {
        var structures=Commands.literal("structure");
        for(var kind:Landmark.values()) structures.then(Commands.literal("dudunka:"+kind.id)
                .executes(c->locate(c.getSource(),kind)));
        dispatcher.register(Commands.literal("locate").requires(s->s.hasPermission(2)).then(structures));
    }

    private static int locate(CommandSourceStack source,Landmark kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var origin=BlockPos.containing(source.getPosition());
        var piece=findPhysical(source.getLevel(),kind,origin);
        if(piece==null) throw new SimpleCommandExceptionType(Component.literal(
                "No intact dudunka:"+kind.id+" found within the native search radius / 16 physical candidates. "
                +"Try another exploration area; existing sites were not repaired or removed.")).create();
        var root=piece.anchor();
        var coords=Component.translatable("chat.coordinates",root.getX(),"~",root.getZ()).withStyle(style->style
                .withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,"/tp @s "+root.getX()+" ~ "+root.getZ())));
        int distance=(int)Math.sqrt((double)(root.getX()-origin.getX())*(root.getX()-origin.getX())
                +(double)(root.getZ()-origin.getZ())*(root.getZ()-origin.getZ()));
        source.sendSuccess(()->Component.translatable("commands.locate.structure.success","dudunka:"+kind.id,coords,distance),false);
        return distance;
    }

    /** Native set candidates and starts, completed through normal generation before reporting success.
     * Only explicit locate loads chunks; old incomplete starts are skipped without changing their metadata. */
    public static ValeAdventurePiece findPhysical(ServerLevel level,Landmark kind,BlockPos origin) {
        if(!level.dimension().equals(MushroomVale.LEVEL)) return null;
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder=registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID,kind.id)));
        var state=level.getChunkSource().getGeneratorState();
        var placements=state.getPlacementsForStructure(holder);
        var seen=new HashSet<Long>();
        int physicalCandidates=0;
        // Match vanilla's random-spread ring search, with a cap on expensive FULL generation.
        for(int ring=0;ring<=100;ring++) for(var placement:placements) {
            if(!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
                if(Math.abs(dx)!=ring && Math.abs(dz)!=ring) continue;
                var cp=spread.getPotentialStructureChunk(state.getLevelSeed(),
                        (origin.getX()>>4)+spread.spacing()*dx,(origin.getZ()>>4)+spread.spacing()*dz);
                if(!seen.add(cp.toLong()) || level.structureManager().checkStructurePresence(cp,holder.value(),false)
                        ==StructureCheckResult.START_NOT_PRESENT) continue;
                var chunk=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS);
                var start=chunk.getStartForStructure(holder.value());
                if(start==null || !start.isValid()) continue;
                if(++physicalCandidates>16) return null;
                chunk=level.getChunk(cp.x,cp.z); // FEATURES can reject a previously accepted start.
                start=chunk.getStartForStructure(holder.value());
                if(start==null || !start.isValid() || start.getPieces().size()!=1) continue;
                var startBounds=start.getBoundingBox();
                if(start.getPieces().get(0) instanceof ValeAdventurePiece piece && piece.landmark()==kind
                        && new ChunkPos(piece.anchor()).equals(cp)
                        && piece.plan().keySet().stream().allMatch(p->new ChunkPos(p).equals(cp)
                                && piece.getBoundingBox().isInside(p) && startBounds.isInside(p))
                        && piece.intact(level)) return piece;
            }
        }
        return null;
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> inspectCommand() {
        return Commands.literal("inspect_music").then(Commands.argument("x",IntegerArgumentType.integer())
                .then(Commands.argument("z",IntegerArgumentType.integer()).executes(c->inspect(c.getSource(),
                        IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z")))));
    }

    private static int inspect(CommandSourceStack source,int x,int z) {
        var level=source.getLevel();
        // getChunkNow is deliberate: inspecting a failed old site must not generate/modify it.
        var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        report(source,"Vale music inspection: seed="+level.getSeed()+" dimension="+level.dimension().location()
                +" chunk="+new ChunkPos(x>>4,z>>4)+" generator="+level.getChunkSource().getGenerator().getClass().getSimpleName());
        if(chunk==null) { report(source,"Chunk is not loaded at FULL. Travel there, then inspect again; no chunk was loaded."); return 0; }
        int count=0;
        for(var kind:Landmark.values()) {
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID,kind.id));
            var start=chunk.getStartForStructure(structure);
            if(start==null) continue;
            count++;
            report(source,kind.id+": valid="+start.isValid()+" status="+chunk.getStatus()+" pieces="+start.getPieces().size());
            if(!start.isValid()) continue;
            report(source,"start chunk="+start.getChunkPos()+" bounds="+start.getBoundingBox());
            for(var part:start.getPieces()) if(part instanceof ValeAdventurePiece piece) {
                int matches=0,shown=0;var plan=piece.plan();
                report(source,"piece kind="+piece.landmark().id+" anchor="+piece.anchor()+" bounds="+piece.getBoundingBox());
                var floors=piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level))
                        .getIntArray("Ground");
                report(source,"saved floor Y min="+java.util.Arrays.stream(floors).min().orElse(0)
                        +" max="+java.util.Arrays.stream(floors).max().orElse(0));
                for(var entry:plan.entrySet()) {
                    var actual=level.getBlockState(entry.getKey());
                    if(actual.equals(entry.getValue())) matches++;
                    else if(shown++<8) report(source,"mismatch "+entry.getKey()+" expected="+entry.getValue()+" actual="+actual);
                }
                var cp=chunk.getPos();
                var writable=new net.minecraft.world.level.levelgen.structure.BoundingBox(cp.getMinBlockX(),level.getMinBuildHeight()+1,
                        cp.getMinBlockZ(),cp.getMaxBlockX(),level.getMaxBuildHeight()-1,cp.getMaxBlockZ());
                report(source,"blueprint matches="+matches+"/"+plan.size()+" current preflight (not historical)="
                        +(matches==plan.size()?"complete existing blueprint":piece.siteProblem(level,writable)));
                // The root Y is the maximum saved noise floor; compare actual surface independently.
                report(source,"anchor block="+level.getBlockState(piece.anchor())+" current surface Y="
                        +level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,piece.anchor().getX(),piece.anchor().getZ()));
            }
        }
        if(count==0) report(source,"No saved music starts in this loaded chunk.");
        return count;
    }

    private static void report(CommandSourceStack source,String text) {
        source.sendSuccess(()->Component.literal(text),false);
        com.mojang.logging.LogUtils.getLogger().info("Vale music inspection: {}",text);
    }
}
