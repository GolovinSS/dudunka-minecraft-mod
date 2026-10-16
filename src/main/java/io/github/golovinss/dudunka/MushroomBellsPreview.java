package io.github.golovinss.dudunka;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/** Static mesh cells for explicit developer inspection. Never a native landmark or quest target. */
public final class MushroomBellsPreview {
    private record Cell(BlockPos offset, VoxelShape outline, VoxelShape collision, int light) {}
    private static final List<Cell> CELLS = readCells();
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, CELLS.size() - 1);
    public static final RegistryObject<Block> BLOCK = DudunkaMod.BLOCKS.register("mushroom_bells_preview_part", PartBlock::new);

    private MushroomBellsPreview() {}
    public static void register() {} // Initialize before the existing DeferredRegister is attached.

    private static VoxelShape box(JsonArray a) {
        if (a.size() != 6) throw new IllegalArgumentException("Invalid preview box");
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) {
            v[i] = a.get(i).getAsDouble();
            if (!Double.isFinite(v[i]) || v[i] < -1e-7 || v[i] > 1 + 1e-7)
                throw new IllegalArgumentException("Preview shape escaped its block cell");
            v[i] = Math.max(0, Math.min(1, v[i]));
        }
        return Shapes.box(v[0], v[1], v[2], v[3], v[4], v[5]);
    }
    private static List<Cell> readCells() {
        var stream = MushroomBellsPreview.class.getResourceAsStream("/data/dudunka/mushroom_bells_preview/layout.json");
        if (stream == null) throw new IllegalStateException("Missing generated Mushroom Bells preview layout");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unknown preview layout schema");
            var cells = new ArrayList<Cell>();
            for (var value : root.getAsJsonArray("parts")) {
                var part = value.getAsJsonObject();
                if (part.get("part").getAsInt() != cells.size()) throw new IllegalArgumentException("Noncontiguous preview parts");
                var offset = part.getAsJsonArray("offset");
                var pos = new BlockPos(offset.get(0).getAsInt(), offset.get(1).getAsInt(), offset.get(2).getAsInt());
                if (Math.abs(pos.getX()) > 6 || Math.abs(pos.getZ()) > 6 || pos.getY() < -1 || pos.getY() > 4)
                    throw new IllegalArgumentException("Preview exceeds the reviewed landmark footprint");
                var collision = Shapes.empty();
                for (var bounds : part.getAsJsonArray("collision")) collision = Shapes.or(collision, box(bounds.getAsJsonArray()));
                cells.add(new Cell(pos, box(part.getAsJsonArray("outline")), collision.optimize(), part.get("light").getAsInt()));
            }
            if (cells.size() < 2 || cells.size() > 512) throw new IllegalArgumentException("Unbounded preview layout");
            return List.copyOf(cells);
        } catch (java.io.IOException | RuntimeException ex) {
            throw new IllegalStateException("Invalid generated Mushroom Bells preview layout", ex);
        }
    }

    public static Map<BlockPos, BlockState> plan(BlockPos origin) {
        var plan = new LinkedHashMap<BlockPos, BlockState>();
        for (int i = 0; i < CELLS.size(); i++)
            plan.put(origin.offset(CELLS.get(i).offset), BLOCK.get().defaultBlockState().setValue(PART, i));
        return Collections.unmodifiableMap(plan);
    }
    public static BoundingBox bounds(BlockPos origin) {
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (var cell : CELLS) {
            var p = cell.offset;
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new BoundingBox(origin.getX() + minX, origin.getY() + minY, origin.getZ() + minZ,
                origin.getX() + maxX, origin.getY() + maxY, origin.getZ() + maxZ);
    }
    /** Read-only; examines loaded chunks only and protects even the gaps between mesh cells. */
    public static String siteProblem(ServerLevel level, BlockPos origin, boolean removing) {
        var expected = plan(origin);
        var b = bounds(origin);
        for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
                return "Needs loaded chunks inside the build height and world border: " + pos.toShortString();
            var actual = level.getBlockState(pos);
            var wanted = removing && expected.containsKey(pos) ? expected.get(pos) : Blocks.AIR.defaultBlockState();
            if (!(wanted.isAir() ? actual.isAir() : actual.equals(wanted)) || !actual.getFluidState().isEmpty()
                    || level.getBlockEntity(pos) != null)
                return "Occupied or changed cell at " + pos.toShortString() + ". No blocks changed.";
        }
        var volume = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        if (!level.getEntities((Entity) null, volume, Entity::isAlive).isEmpty())
            return "Move living occupants outside the preview volume first. No blocks changed.";
        return null;
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var root = Commands.literal("bells_preview");
        root.then(MushroomBellsStyledPreview.command(false));
        root.then(MushroomBellsStyledPreview.command(true));
        for (boolean removing : new boolean[]{false, true})
            root.then(Commands.literal(removing ? "clear" : "place")
                    .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(c ->
                            change(c.getSource(), BlockPosArgument.getBlockPos(c, "origin"), removing))));
        return root;
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
    private static int change(CommandSourceStack source, BlockPos origin, boolean removing) throws CommandSyntaxException {
        if (!Boolean.getBoolean(MushroomVale.DEV_PROPERTY) || !source.hasPermission(2))
            throw error("Developer previews are disabled.");
        var player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSpectator()) throw error("Preview requires a living player.");
        var level = source.getLevel();
        var problem = siteProblem(level, origin, removing);
        if (problem != null) throw error(problem);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var entry : plan(origin).entrySet()) {
            var pos = entry.getKey();
            before.put(pos, level.getBlockState(pos));
            var next = removing ? Blocks.AIR.defaultBlockState() : entry.getValue();
            // Static parts need client updates, without triggering neighbors in a developer fixture.
            if (!level.setBlock(pos, next, 18) || !level.getBlockState(pos).equals(next)) {
                before.forEach((p, state) -> level.setBlock(p, state, 18));
                throw error("Preview write failed; restored the cells touched by this attempt.");
            }
        }
        source.sendSuccess(() -> Component.literal((removing ? "Cleared" : "Placed") + " Mushroom Bells visual preview at "
                + origin.toShortString() + ". Visual inspection only; no quest or native structure start."), false);
        return 1;
    }

    private static final class PartBlock extends Block {
        PartBlock() {
            super(Properties.of().strength(.5F).sound(SoundType.STONE).noLootTable().noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .lightLevel(s -> CELLS.get(s.getValue(PART)).light));
            registerDefaultState(stateDefinition.any().setValue(PART, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART); }
        @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }
        @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return CELLS.get(state.getValue(PART)).outline;
        }
        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return CELLS.get(state.getValue(PART)).collision;
        }
    }
}
