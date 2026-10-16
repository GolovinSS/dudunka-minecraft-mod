package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class MushroomBellsStyleTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void styledPartsAreSeparateStaticCellsWithSparseRealLight(GameTestHelper h) {
        var id = ResourceLocation.fromNamespaceAndPath("dudunka", "mushroom_bells_styled_part");
        var block = MushroomBellsStyledPreview.BLOCK.get();
        h.assertTrue(ForgeRegistries.BLOCKS.getValue(id) == block && block != MushroomBellsPreview.BLOCK.get(),
                "Styled geometry uses an additive registry ID, preserving existing preview states");
        h.assertTrue(!ForgeRegistries.ITEMS.containsKey(id), "No new survival/creative item");
        var origin = new BlockPos(8, 80, 8); var plan = MushroomBellsStyledPreview.plan(origin);
        long meshes = plan.values().stream().filter(s -> s.is(block)).count();
        h.assertTrue(meshes == block.getStateDefinition().getPossibleStates().size() && meshes < 256,
                "Bounded state count matches exact styled mesh assembly");
        h.assertTrue(plan.values().stream().filter(s -> s.is(Blocks.MOSS_CARPET)).count() >= 5, "Native moss softens the block-ground transition");
        h.assertTrue(plan.keySet().stream().allMatch(p -> p.getY() >= origin.getY() && new ChunkPos(p).equals(new ChunkPos(origin))),
                "No platform or supporting ground is included in the write plan");
        h.assertTrue(MushroomBellsStyledPreview.plan(new BlockPos(-1, 80, -1)).keySet().stream().map(ChunkPos::new).distinct().count() == 4,
                "Cell layout remains correct across negative chunk boundaries");
        int lights = 0;
        for (var e : plan.entrySet()) {
            var state = e.getValue(); var pos = e.getKey();
            h.assertTrue(!state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL, "Static baked models without block entities");
            if (state.getLightEmission() > 0) lights++;
            h.assertTrue(state.getLightEmission() <= 13, "Restrained actual world light");
            if (state.is(block)) {
                h.assertTrue(!state.canOcclude() && state.getLightBlock(h.getLevel(), pos) == 0, "Partial mesh cells preserve scene light");
                for (var b : state.getShape(h.getLevel(), pos).toAabbs()) h.assertTrue(b.minX >= 0 && b.minY >= 0 && b.minZ >= 0
                        && b.maxX <= 1 && b.maxY <= 1 && b.maxZ <= 1, "No geometry-sized selectable shape escapes its render cell");
            }
        }
        h.assertTrue(lights == 4, "Exactly the crystal and three bell light anchors emit block light");
        h.assertTrue(plan.get(origin.offset(0, 1, 0)).getLightEmission() == 13, "Crystal centerpiece emits level 13");
        for (var offset : new BlockPos[]{new BlockPos(-3, 1, -1), new BlockPos(2, 1, -2), new BlockPos(2, 1, 2)}) {
            h.assertTrue(plan.get(origin.offset(offset)).getLightEmission() == 10, "Each bell clapper emits level 10");
        }
        h.assertTrue(MushroomBellsPreview.plan(origin).size() == 347, "Original preview layout remains available without migration");
        h.succeed();
    }

    private static CommandDispatcher<CommandSourceStack> commands() {
        var d = new CommandDispatcher<CommandSourceStack>(); MushroomVale.registerCommands(d); return d;
    }
    private static void rejected(GameTestHelper h, CommandDispatcher<CommandSourceStack> d, CommandSourceStack s, String command) {
        boolean rejected = false;
        try { d.execute(command, s); } catch (CommandSyntaxException expected) { rejected = true; }
        h.assertTrue(rejected, "Unsafe or unauthorized terrain preview must refuse: " + command);
    }
    @GameTest(template="empty", timeoutTicks=80, batch="isolated_mushroom_style_preview")
    public static void terrainCommandsPreserveGroundBuildsProgressAndOldCommands(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(BlockPos.ZERO);
        var origin = new BlockPos((base.getX() >> 4) * 16 + 15, 160, (base.getZ() >> 4) * 16 + 15);
        var b = MushroomBellsStyledPreview.bounds(origin);
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) level.getChunk(x, z);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(b.minX(), origin.getY() - 1, b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            saved.put(p.immutable(), level.getBlockState(p));
            level.setBlock(p, p.getY() < origin.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "StylePreview"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> p) {}
            @Override public void send(Packet<?> p, PacketSendListener listener) {}
        };
        player.moveTo(origin.getX() + 8, origin.getY(), origin.getZ() + 8, 0, 0); level.addNewPlayer(player);
        String property = System.getProperty(MushroomVale.DEV_PROPERTY);
        String place = "dudunka_dev mushroom_vale bells_preview terrain " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
        String clear = place.replace(" terrain ", " clear_terrain ");
        try {
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            h.assertTrue(commands().getRoot().getChild("dudunka_dev") == null, "No commands without JVM opt-in");
            System.setProperty(MushroomVale.DEV_PROPERTY, "true"); var d = commands();
            var route = d.getRoot().getChild("dudunka_dev").getChild("mushroom_vale").getChild("bells_preview");
            h.assertTrue(route.getChild("place") != null && route.getChild("clear") != null && route.getChild("terrain") != null
                    && route.getChild("clear_terrain") != null, "Original command routes coexist with the styled terrain routes");
            var source = player.createCommandSourceStack().withPermission(2);
            rejected(h, d, source.withPermission(0), place);
            rejected(h, d, level.getServer().createCommandSourceStack().withLevel(level), place);
            System.clearProperty(MushroomVale.DEV_PROPERTY); rejected(h, d, source, place);
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var progress = ValeMusicProgress.get(level.getServer()).save(new CompoundTag());
            var forced = new HashSet<>(level.getForcedChunks());
            level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 18);
            rejected(h, d, source, place);
            h.assertTrue(level.getBlockState(origin.below()).is(Blocks.STONE), "Refusal never flattens or replaces supporting terrain");
            level.setBlock(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 18);
            var obstacle = origin.above(3); level.setBlock(obstacle, Blocks.CHEST.defaultBlockState(), 18);
            rejected(h, d, source, place);
            h.assertTrue(level.getBlockState(obstacle).is(Blocks.CHEST), "Existing build/block entity is preserved");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 18);
            player.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
            rejected(h, d, source, place);
            player.moveTo(origin.getX() + 8, origin.getY(), origin.getZ() + 8, 0, 0);
            h.assertTrue(d.execute(place, source) == 1, "Place styled meshes across four already-loaded chunks");
            var plan = MushroomBellsStyledPreview.plan(origin);
            h.assertTrue(plan.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())), "Exact terrain preview including native moss");
            for (var p : BlockPos.betweenClosed(b.minX(), origin.getY() - 1, b.minZ(), b.maxX(), origin.getY() - 1, b.maxZ()))
                h.assertTrue(level.getBlockState(p).is(Blocks.GRASS_BLOCK), "Every ground block remains unchanged");
            rejected(h, d, source, place);
            var changed = plan.keySet().iterator().next(); level.setBlock(changed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
            rejected(h, d, source, clear);
            h.assertTrue(level.getBlockState(changed).is(Blocks.DIAMOND_BLOCK), "Cleanup refuses changed player blocks");
            level.setBlock(changed, plan.get(changed), 18);
            h.assertTrue(d.execute(clear, source) == 1, "Clear only the matching styled meshes and carpets");
            for (var p : BlockPos.betweenClosed(b.minX(), origin.getY() - 1, b.minZ(), b.maxX(), origin.getY() - 1, b.maxZ()))
                h.assertTrue(level.getBlockState(p).is(Blocks.GRASS_BLOCK), "Cleanup also preserves every ground block");
            h.assertTrue(plan.keySet().stream().allMatch(level::isEmptyBlock), "No styled cells or carpets remain after exact cleanup");
            h.assertTrue(ValeMusicProgress.get(level.getServer()).save(new CompoundTag()).equals(progress) && level.getForcedChunks().equals(forced),
                    "No quest progress or forced tickets are changed");
            h.assertTrue(MushroomBellsStyledPreview.siteProblem(level, origin.offset(1000000, 0, 1000000), false) != null,
                    "An unloaded site is rejected without generation");
        } catch (CommandSyntaxException ex) { throw new AssertionError("Safe authorized styled preview failed", ex); }
        finally {
            player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); saved.forEach((p, s) -> level.setBlock(p, s, 18));
            if (property == null) System.clearProperty(MushroomVale.DEV_PROPERTY); else System.setProperty(MushroomVale.DEV_PROPERTY, property);
        }
        h.succeed();
    }
}
