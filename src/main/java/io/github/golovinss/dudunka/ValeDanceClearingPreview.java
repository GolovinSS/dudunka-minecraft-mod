package io.github.golovinss.dudunka;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/** Explicit, disposable-world block sketch. No registration, native start, quest or worldgen hook. */
public final class ValeDanceClearingPreview {
    public static final int RADIUS = 12;
    private static final Direction[] CARDINALS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private record Snapshot(Map<BlockPos, BlockState> before, Map<BlockPos, BlockState> placed) {}
    // Session-only, bounded undo. Weak level keys do not retain stopped servers or old dimensions.
    private static final Map<ServerLevel, Map<BlockPos, Snapshot>> UNDO = new WeakHashMap<>();
    private ValeDanceClearingPreview() {}

    /** Origin is the activation block, at player feet height; ground lies at Y - 1. */
    public static BoundingBox bounds(BlockPos origin) {
        return new BoundingBox(origin.getX() - RADIUS, origin.getY() - 1, origin.getZ() - RADIUS,
                origin.getX() + RADIUS, origin.getY() + 4, origin.getZ() + RADIUS);
    }
    /** One extra block around the sketch and three solid layers beneath every column. */
    public static BoundingBox safetyBounds(BlockPos origin) {
        return new BoundingBox(origin.getX() - 13, origin.getY() - 3, origin.getZ() - 13,
                origin.getX() + 13, origin.getY() + 5, origin.getZ() + 13);
    }
    public static BlockPos station(BlockPos origin, int bloom) {
        if (bloom < 0 || bloom >= CARDINALS.length) throw new IllegalArgumentException("Unknown cardinal bloom");
        return origin.relative(CARDINALS[bloom], 5);
    }
    private static void put(Map<BlockPos, BlockState> plan, BlockPos root, int x, int y, int z, Block block) {
        plan.put(root.offset(x, y, z), block.defaultBlockState());
    }
    private static BlockState stair(Direction facing) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }
    private static BlockState leaves() {
        return Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
    }
    private static void plant(Map<BlockPos, BlockState> p, BlockPos root, int x, int z, Block plant) {
        // Podzol also keeps vanilla mushrooms alive in daylight.
        put(p, root, x, -1, z, plant == Blocks.RED_MUSHROOM || plant == Blocks.BROWN_MUSHROOM ? Blocks.PODZOL : Blocks.MOSS_BLOCK);
        put(p, root, x, 0, z, plant);
    }

    public static Map<BlockPos, BlockState> plan(BlockPos root) {
        var p = new LinkedHashMap<BlockPos, BlockState>();
        Block[] soil = {Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK};
        Block[] stone = {Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSS_BLOCK};
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            int d = x * x + z * z;
            int noise = Math.floorMod(x * 31 + z * 17 + x * z * 3, 23);
            // Uneven feathered edge: retain unplanned original ground between the moss patches.
            if (d > 144 || d > 104 && noise > 8) continue;
            Block ground = soil[noise % soil.length];
            boolean spoke = Math.abs(x) <= 1 || Math.abs(z) <= 1;
            if (d >= 43 && d <= 70 || spoke && d > 4 && d < 110)
                ground = stone[noise % stone.length];
            if (d < 5) ground = noise % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSS_BLOCK;
            put(p, root, x, -1, z, ground);
            // Short raised fragments on the outside lip; the broad inner ring stays level.
            if (!spoke && d >= 65 && d <= 82 && noise < 3)
                put(p, root, x, 0, z, noise == 0 ? Blocks.MOSS_CARPET : Blocks.STONE_BRICK_SLAB);
            else if (!spoke && d > 88 && noise == 4) put(p, root, x, 0, z, Blocks.MOSS_CARPET);
        }

        // Low inset pedestal: all four cardinal neighbors remain clear at feet/head height.
        put(p, root, 0, -1, 0, Blocks.MOSSY_STONE_BRICKS);
        put(p, root, 0, 0, 0, Blocks.CHISELED_STONE_BRICKS);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            put(p, root, x, -1, z, Blocks.MOSSY_COBBLESTONE);
            put(p, root, x, 0, z, Blocks.STONE_BRICK_SLAB);
        }
        for (int i = 0; i < CARDINALS.length; i++) {
            Direction outward = CARDINALS[i], tangent = outward.getClockWise();
            BlockPos flower = station(root, i);
            p.put(flower.below(), Blocks.MOSS_BLOCK.defaultBlockState());
            p.put(flower, MushroomValeLandmarks.DANCE_FLOWER.get().defaultBlockState().setValue(ValeDanceFlower.BLOOM, i));
            // A miniature ruined garden alcove, open on both radial approaches.
            for (int side : new int[]{-1, 1}) {
                BlockPos flank = flower.relative(tangent, side);
                p.put(flank.below(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                p.put(flank, stair(side < 0 ? tangent : tangent.getOpposite()));
                BlockPos back = flank.relative(outward);
                p.put(back.below(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                p.put(back, side < 0 ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
                        : Blocks.STONE_BRICK_SLAB.defaultBlockState());
                if (side < 0) p.put(back.above(), Blocks.MOSS_CARPET.defaultBlockState());
            }
            // A flush cobbled approach separates each bloom from ordinary background flowers.
            p.put(flower.relative(outward.getOpposite()).below(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }

        // Two different low mushroom silhouettes, deliberately off the cardinal sight lines.
        mushroom(p, root, -8, -7, true);
        mushroom(p, root, 8, 6, false);
        // Fallen spruce trunk with an exposed end, root tuft and leaf shoulder.
        for (int z = 3; z <= 6; z++) p.put(root.offset(-9, 0, z), Blocks.SPRUCE_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        put(p, root, -9, -1, 3, Blocks.ROOTED_DIRT);
        p.put(root.offset(-10, 0, 5), leaves()); p.put(root.offset(-10, 1, 5), leaves());
        p.put(root.offset(-10, 0, 6), leaves());
        plant(p, root, -8, 3, Blocks.FERN); plant(p, root, -8, 7, Blocks.BROWN_MUSHROOM);
        // Broken knee-high ruin and a small fence lantern, with manually connected wall states.
        for (int x = 5; x <= 7; x++) {
            put(p, root, x, -1, -9, Blocks.MOSSY_COBBLESTONE);
            p.put(root.offset(x, 0, -9), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState()
                    .setValue(BlockStateProperties.EAST_WALL, x < 7 ? WallSide.LOW : WallSide.NONE)
                    .setValue(BlockStateProperties.WEST_WALL, x > 5 ? WallSide.LOW : WallSide.NONE));
        }
        put(p, root, 5, 1, -9, Blocks.LANTERN);
        put(p, root, -6, 0, 9, Blocks.SPRUCE_FENCE);
        put(p, root, -6, 1, 9, Blocks.LANTERN);
        p.put(root.offset(7, 0, -10), leaves()); p.put(root.offset(8, 0, -10), leaves());
        put(p, root, 6, 1, -9, Blocks.MOSS_CARPET);
        // Unequal woodland pockets; low plants keep the scene readable at player height.
        int[][] pockets = {{-5,-9},{-4,-9},{-6,-8},{9,-4},{10,-4},{9,-3},{4,9},{3,10},{4,10},{-5,8},{-4,9}};
        Block[] plants = {Blocks.FERN, Blocks.RED_MUSHROOM, Blocks.MOSS_CARPET, Blocks.BROWN_MUSHROOM, Blocks.OXEYE_DAISY};
        for (int i = 0; i < pockets.length; i++) plant(p, root, pockets[i][0], pockets[i][1], plants[i % plants.length]);
        plant(p, root, 9, -5, MushroomValeVegetation.BLOCKS.get("small_golden_mushroom").get());
        plant(p, root, -5, 9, MushroomValeVegetation.BLOCKS.get("small_violet_mushroom").get());
        plant(p, root, -4, -8, MushroomValeVegetation.BLOCKS.get("glowing_moss").get());
        return Collections.unmodifiableMap(p);
    }
    private static void mushroom(Map<BlockPos, BlockState> p, BlockPos root, int x, int z, boolean red) {
        put(p, root, x, -1, z, Blocks.ROOTED_DIRT);
        put(p, root, x, 0, z, Blocks.MUSHROOM_STEM);
        if (red) put(p, root, x, 1, z, Blocks.MUSHROOM_STEM);
        int height = red ? 2 : 1;
        Block cap = red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (red && dx == -1 && dz == -1) continue;
            p.put(root.offset(x + dx, height, z + dz), cap.defaultBlockState().setValue(BlockStateProperties.DOWN, false));
        }
        put(p, root, x, height + 1, z, Blocks.MOSS_CARPET);
        put(p, root, x, -1, z + 2, Blocks.MOSS_BLOCK);
        put(p, root, x, 0, z + 2, Blocks.FERN);
    }

    private static boolean soil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK);
    }
    private static boolean smallPlant(BlockState state) {
        return state.is(Blocks.GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DANDELION) || state.is(Blocks.POPPY)
                || state.is(Blocks.OXEYE_DAISY) || state.is(Blocks.ALLIUM) || state.is(Blocks.MOSS_CARPET);
    }
    private static String loadedProblem(ServerLevel level, BoundingBox b) {
        if (b.minY() < level.getMinBuildHeight() || b.maxY() >= level.getMaxBuildHeight()) return "Outside build height.";
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return "All surrounding chunks must already be loaded. Travel there first.";
        for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            if (!level.getWorldBorder().isWithinBounds(pos)) return "Outside the world border.";
        return null;
    }
    private static String occupantsProblem(ServerLevel level, BoundingBox b) {
        if (!level.getEntities((Entity) null, new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1), Entity::isAlive).isEmpty())
            return "Move players, mobs and dropped items outside the safety volume first.";
        return null;
    }
    /** Entire read-only preflight, including gaps, the terrain rim and subsurface support. */
    public static String siteProblem(ServerLevel level, BlockPos root) {
        var b = safetyBounds(root);
        String problem = loadedProblem(level, b);
        if (problem != null) return problem;
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) {
            var chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk.getAllStarts().values().stream().anyMatch(s -> s.isValid() && s.getBoundingBox().intersects(b))
                    || chunk.getAllReferences().values().stream().anyMatch(refs -> !refs.isEmpty()))
                return "Site overlaps or neighbors native structure metadata. Choose an empty test area.";
        }
        for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return "Fluid or block entity at " + pos.toShortString();
            if (pos.getY() < root.getY()) {
                if (!soil(state) || !state.isFaceSturdy(level, pos, Direction.UP))
                    return "Needs flat natural soil with three solid layers, including the rim: " + pos.toShortString();
            } else if (!state.isAir() && !smallPlant(state)) return "Occupied by a tree, build or protected decoration: " + pos.toShortString();
        }
        return occupantsProblem(level, b);
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var root = Commands.literal("dance_preview");
        for (boolean restoring : new boolean[]{false, true})
            root.then(Commands.literal(restoring ? "restore" : "place")
                    .then(Commands.argument("origin", BlockPosArgument.blockPos())
                            .then(Commands.literal("disposable").executes(c -> change(c.getSource(),
                                    BlockPosArgument.getBlockPos(c, "origin"), restoring)))));
        return root;
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message + " No preview changes applied.")).create();
    }
    private static Map<BlockPos, BlockState> read(ServerLevel level, BoundingBox b) {
        var states = new LinkedHashMap<BlockPos, BlockState>();
        for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            states.put(pos.immutable(), level.getBlockState(pos));
        return states;
    }
    private static int change(CommandSourceStack source, BlockPos origin, boolean restoring) throws CommandSyntaxException {
        if (!Boolean.getBoolean(MushroomVale.DEV_PROPERTY) || !source.hasPermission(2)) throw error("Developer preview is disabled.");
        var player = source.getPlayerOrException();
        if (!player.isAlive() || !player.isCreative() || player.isSpectator()) throw error("Requires a living Creative operator in a disposable world.");
        var level = source.getLevel(); var b = bounds(origin);
        var history = UNDO.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        Map<BlockPos, BlockState> next;
        if (restoring) {
            var snapshot = history.get(origin);
            if (snapshot == null) throw error("No undo for this origin in this server session. Use the disposable world's backup.");
            String problem = loadedProblem(level, safetyBounds(origin));
            if (problem == null) problem = occupantsProblem(level, safetyBounds(origin));
            if (problem != null) throw error(problem);
            if (!read(level, b).equals(snapshot.placed)) throw error("Preview was edited or changed. Export your work; restore refuses to erase it.");
            next = snapshot.before;
        } else {
            if (history.containsKey(origin) || history.size() >= 8) throw error("Restore previous previews first (maximum eight per dimension/session).");
            String problem = siteProblem(level, origin);
            if (problem != null) throw error(problem);
            next = new LinkedHashMap<>(read(level, b));
            // Remove only preflight-approved small plants in the edit volume, never in the rim.
            next.replaceAll((pos, state) -> pos.getY() >= origin.getY() ? Blocks.AIR.defaultBlockState() : state);
            next.putAll(plan(origin));
        }
        var before = read(level, b);
        var touched = new LinkedHashMap<BlockPos, BlockState>();
        for (var entry : next.entrySet()) {
            var pos = entry.getKey(); var wanted = entry.getValue();
            if (before.get(pos).equals(wanted)) continue;
            touched.put(pos, before.get(pos));
            // Existing preview flags: client updates; suppress neighbor-induced changes outside the sketch.
            if (!level.setBlock(pos, wanted, 18) || !level.getBlockState(pos).equals(wanted)) {
                boolean rolledBack = true;
                for (var old : touched.entrySet()) {
                    level.setBlock(old.getKey(), old.getValue(), 18);
                    rolledBack &= level.getBlockState(old.getKey()).equals(old.getValue());
                }
                throw new SimpleCommandExceptionType(Component.literal(rolledBack ? "Write failed; all touched cells restored."
                        : "Write and rollback failed. Inspect this disposable world and recover its backup.")).create();
            }
        }
        if (restoring) history.remove(origin.immutable());
        else history.put(origin.immutable(), new Snapshot(Collections.unmodifiableMap(before), Collections.unmodifiableMap(next)));
        source.sendSuccess(() -> Component.literal((restoring ? "Restored terrain" : "Placed block-built dance sketch")
                + " at " + origin.toShortString() + ". Disposable-world visual preview only; no quest binding. Undo ends on editing or server restart."), false);
        return 1;
    }
}
