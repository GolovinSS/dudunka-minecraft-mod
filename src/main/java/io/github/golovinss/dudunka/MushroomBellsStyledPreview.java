package io.github.golovinss.dudunka;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.registries.RegistryObject;

/** Approved static scene shared by the terrain preview and revision-3 native Bells. */
public final class MushroomBellsStyledPreview {
    private record Cell(BlockPos offset, VoxelShape outline, VoxelShape collision, int light, int target, boolean required) {}
    private record Layout(List<Cell> cells, List<BlockPos> moss) {}
    private static final Layout LAYOUT = readLayout();
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, LAYOUT.cells.size() - 1);
    public static final RegistryObject<Block> BLOCK = DudunkaMod.BLOCKS.register("mushroom_bells_styled_part", StyledPart::new);
    private MushroomBellsStyledPreview() {}
    public static void register() {}

    private static BlockPos offset(JsonArray a) {
        var p = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        if (Math.abs(p.getX()) > 6 || Math.abs(p.getZ()) > 6 || p.getY() < 0 || p.getY() > 4)
            throw new IllegalArgumentException("Styled preview escaped reviewed bounds");
        return p;
    }
    private static VoxelShape box(JsonArray a) {
        if (a.size() != 6) throw new IllegalArgumentException("Invalid styled shape");
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) {
            v[i] = a.get(i).getAsDouble();
            if (!Double.isFinite(v[i]) || v[i] < -1e-7 || v[i] > 1 + 1e-7)
                throw new IllegalArgumentException("Styled shape escaped its render cell");
            v[i] = Math.max(0, Math.min(1, v[i]));
        }
        return Shapes.box(v[0], v[1], v[2], v[3], v[4], v[5]);
    }
    private static Layout readLayout() {
        var stream = MushroomBellsStyledPreview.class.getResourceAsStream("/data/dudunka/mushroom_bells_styled/layout.json");
        if (stream == null) throw new IllegalStateException("Missing styled Mushroom Bells layout");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unknown styled layout schema");
            var cells = new ArrayList<Cell>();
            for (var value : root.getAsJsonArray("parts")) {
                var p = value.getAsJsonObject();
                if (p.get("part").getAsInt() != cells.size()) throw new IllegalArgumentException("Noncontiguous styled cells");
                var collision = Shapes.empty();
                for (var b : p.getAsJsonArray("collision")) collision = Shapes.or(collision, box(b.getAsJsonArray()));
                int light = p.get("light").getAsInt();
                if (light < 0 || light > 13) throw new IllegalArgumentException("Unreviewed styled light level");
                // Bind identities to approved source objects, never to their new spatial order.
                var groups = new HashSet<String>();
                for (var g : p.getAsJsonArray("groups")) groups.add(g.getAsString());
                var meshes = new HashSet<String>();
                for (var m : p.getAsJsonArray("meshes")) meshes.add(m.getAsString());
                int target = -2;
                if (groups.contains("03_Teal_Bell_Pointy_Umbrella")) target = 0;
                if (groups.contains("02_Rose_Bell_Broad_Scalloped")) target = 1;
                if (groups.contains("04_Amber_Bell_Round_Lobed")) target = 2;
                boolean stone = meshes.contains("Widened_stone_with_open_carving_panel")
                        || meshes.contains("Tall_central_prismatic_crown")
                        || meshes.contains("Small_luminous_inlay_inside_recess")
                        || meshes.contains("Chiseled_diamond_recess_bevels");
                if (stone) target = -1;
                boolean required = stone || light > 0 || meshes.stream().anyMatch(m -> m.endsWith("elegant_S_curve_stem"));
                cells.add(new Cell(offset(p.getAsJsonArray("offset")), box(p.getAsJsonArray("outline")), collision.optimize(), light, target, required));
            }
            if (cells.size() < 2 || cells.size() > 256) throw new IllegalArgumentException("Unbounded styled cell count");
            var anchors = Map.of(BlockPos.ZERO, -1, new BlockPos(0,2,0), -1,
                    new BlockPos(2,1,-2), 0, new BlockPos(-3,1,-1), 1, new BlockPos(2,1,2), 2);
            anchors.forEach((pos,target) -> {
                if (cells.stream().noneMatch(cell -> cell.offset.equals(pos) && cell.target == target && cell.required))
                    throw new IllegalArgumentException("Missing approved production interaction anchor: " + pos);
            });
            var moss = new ArrayList<BlockPos>();
            for (var value : root.getAsJsonArray("ground_accents")) moss.add(offset(value.getAsJsonArray()));
            return new Layout(List.copyOf(cells), List.copyOf(moss));
        } catch (java.io.IOException | RuntimeException ex) {
            throw new IllegalStateException("Invalid styled Mushroom Bells layout", ex);
        }
    }

    public static Map<BlockPos, BlockState> plan(BlockPos origin) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        for (int i = 0; i < LAYOUT.cells.size(); i++)
            result.put(origin.offset(LAYOUT.cells.get(i).offset), BLOCK.get().defaultBlockState().setValue(PART, i));
        for (var p : LAYOUT.moss)
            if (result.putIfAbsent(origin.offset(p), Blocks.MOSS_CARPET.defaultBlockState()) != null)
                throw new IllegalStateException("Moss overlaps styled mesh cell");
        return Collections.unmodifiableMap(result);
    }
    /** -2 is scenery; -1 listens; 0/1/2 retain the historical note identities. */
    public static int interaction(BlockState state) {
        return state.is(BLOCK.get()) ? LAYOUT.cells.get(state.getValue(PART)).target : -2;
    }
    public static boolean required(BlockState state) {
        return state.is(BLOCK.get()) && LAYOUT.cells.get(state.getValue(PART)).required;
    }
    /** Client-side root check needs no new network field or local structure search. */
    public static boolean musicAnchor(BlockState state) {
        if (state.is(Blocks.CHISELED_STONE_BRICKS)) return true;
        return state.is(BLOCK.get()) && LAYOUT.cells.get(state.getValue(PART)).offset.equals(BlockPos.ZERO);
    }
    public static BoundingBox bounds(BlockPos origin) {
        var offsets = new ArrayList<BlockPos>();
        for (var c : LAYOUT.cells) offsets.add(c.offset);
        offsets.addAll(LAYOUT.moss);
        return new BoundingBox(origin.getX() + offsets.stream().mapToInt(BlockPos::getX).min().orElseThrow(), origin.getY(),
                origin.getZ() + offsets.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),
                origin.getX() + offsets.stream().mapToInt(BlockPos::getX).max().orElseThrow(), origin.getY() + 4,
                origin.getZ() + offsets.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
    }
    /** Full-volume read-only preflight. No flattening, plant clearing, chunk loads or ground writes. */
    public static String siteProblem(ServerLevel level, BlockPos origin, boolean removing) {
        var b = bounds(origin); var expected = plan(origin);
        for (var p : BlockPos.betweenClosed(b.minX(), b.minY() - 1, b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p)
                    || level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null)
                return "Needs loaded chunks inside build height and border: " + p.toShortString();
            var actual = level.getBlockState(p);
            if (p.getY() == origin.getY() - 1) {
                if (!removing && (!(actual.is(BlockTags.DIRT) || actual.is(Blocks.MOSS_BLOCK))
                        || !actual.isFaceSturdy(level, p, Direction.UP) || !actual.getFluidState().isEmpty()
                        || level.getBlockEntity(p) != null))
                    return "Needs an existing level patch of dry natural dirt/grass/moss. Ground will not be flattened.";
            } else {
                var wanted = removing ? expected.getOrDefault(p, Blocks.AIR.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                if (!(wanted.isAir() ? actual.isAir() : actual.equals(wanted)) || !actual.getFluidState().isEmpty()
                        || level.getBlockEntity(p) != null)
                    return "Occupied or changed cell at " + p.toShortString() + ". No blocks changed.";
            }
        }
        var area = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        if (!level.getEntities((Entity) null, area, Entity::isAlive).isEmpty())
            return "Move living occupants outside the styled clearing first. No blocks changed.";
        return null;
    }
    static LiteralArgumentBuilder<CommandSourceStack> command(boolean removing) {
        return Commands.literal(removing ? "clear_terrain" : "terrain")
                .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(c ->
                        change(c.getSource(), BlockPosArgument.getBlockPos(c, "origin"), removing)));
    }
    private static CommandSyntaxException error(String text) {
        return new SimpleCommandExceptionType(Component.literal(text)).create();
    }
    private static int change(CommandSourceStack source, BlockPos origin, boolean removing) throws CommandSyntaxException {
        if (!Boolean.getBoolean(MushroomVale.DEV_PROPERTY) || !source.hasPermission(2)) throw error("Developer previews are disabled.");
        var player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSpectator()) throw error("Preview requires a living player.");
        var level = source.getLevel(); var problem = siteProblem(level, origin, removing);
        if (problem != null) throw error(problem);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var e : plan(origin).entrySet()) {
            before.put(e.getKey(), level.getBlockState(e.getKey()));
            var next = removing ? Blocks.AIR.defaultBlockState() : e.getValue();
            if (!level.setBlock(e.getKey(), next, 18) || !level.getBlockState(e.getKey()).equals(next)) {
                before.forEach((p, s) -> level.setBlock(p, s, 18));
                throw error("Styled preview write failed; restored this attempt's cells.");
            }
        }
        source.sendSuccess(() -> Component.literal((removing ? "Cleared" : "Placed") + " styled Mushroom Bells at "
                + origin.toShortString() + ". Existing ground preserved; visual preview only."), false);
        return 1;
    }

    private static final class StyledPart extends Block {
        StyledPart() {
            super(Properties.of().strength(.5F).sound(SoundType.STONE).noLootTable().noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .lightLevel(s -> LAYOUT.cells.get(s.getValue(PART)).light));
            registerDefaultState(stateDefinition.any().setValue(PART, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(PART); }
        @Override public int getLightBlock(BlockState s, BlockGetter level, BlockPos p) { return 0; }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos p) { return true; }
        @Override public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos p, CollisionContext c) { return LAYOUT.cells.get(s.getValue(PART)).outline; }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos p, CollisionContext c) { return LAYOUT.cells.get(s.getValue(PART)).collision; }
    }
}
