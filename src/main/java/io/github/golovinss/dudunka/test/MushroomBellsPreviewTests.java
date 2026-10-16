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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class MushroomBellsPreviewTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void registeredStaticPartsHaveBoundedShapesAndWalkableApproach(GameTestHelper h) {
        var id = ResourceLocation.fromNamespaceAndPath("dudunka", "mushroom_bells_preview_part");
        var block = MushroomBellsPreview.BLOCK.get();
        h.assertTrue(ForgeRegistries.BLOCKS.getValue(id) == block, "Additive preview block is registered");
        h.assertTrue(!ForgeRegistries.ITEMS.containsKey(id), "Preview has no survival/creative inventory item");
        var origin = new BlockPos(8, 80, 8);
        var plan = MushroomBellsPreview.plan(origin);
        h.assertTrue(plan.size() == block.getStateDefinition().getPossibleStates().size(), "One exact state per static cell");
        h.assertTrue(plan.size() < 512, "Bounded mesh assembly");
        h.assertTrue(plan.keySet().stream().allMatch(p -> new ChunkPos(p).equals(new ChunkPos(origin))
                && MushroomBellsPreview.bounds(origin).isInside(p)), "Reviewed footprint fits the native owning chunk at its center");
        var crossing = MushroomBellsPreview.plan(new BlockPos(-1, 80, -1));
        h.assertTrue(crossing.keySet().stream().map(ChunkPos::new).distinct().count() == 4,
                "Layout retains all cells across negative neighboring chunks");
        int luminous = 0, decorative = 0;
        for (var entry : plan.entrySet()) {
            var s = entry.getValue(); var p = entry.getKey();
            h.assertTrue(!s.hasBlockEntity() && s.getRenderShape() == RenderShape.MODEL && !s.canOcclude(),
                    "Ordinary static baked models, without block entities or full-cell occlusion");
            var shape = s.getShape(h.getLevel(), p, CollisionContext.empty());
            for (var box : shape.toAabbs()) h.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                    && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "Selectable geometry stays within each chunk cell");
            if (s.getCollisionShape(h.getLevel(), p).isEmpty()) decorative++;
            if (s.getLightEmission() > 0) luminous++;
            h.assertTrue(s.getLightBlock(h.getLevel(), p) == 0, "Open cells do not absorb sky/block light");
        }
        h.assertTrue(decorative > 0 && luminous > 0, "Passable decorative parts and restrained real accent lighting");
        var path = origin.offset(0, 0, 3);
        h.assertTrue(!plan.get(path).getCollisionShape(h.getLevel(), path).isEmpty(), "Ground supports walking");
        for (int y = 1; y <= 2; y++) {
            var p = path.above(y); var s = plan.getOrDefault(p, Blocks.AIR.defaultBlockState());
            h.assertTrue(s.getCollisionShape(h.getLevel(), p).isEmpty(), "Player-height approach to the central stone remains open");
        }
        h.succeed();
    }

    private static CommandDispatcher<CommandSourceStack> commands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        MushroomVale.registerCommands(dispatcher);
        return dispatcher;
    }
    private static void rejected(GameTestHelper h, CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandSourceStack source, String command) {
        boolean rejected = false;
        try { dispatcher.execute(command, source); }
        catch (CommandSyntaxException expected) { rejected = true; }
        h.assertTrue(rejected, "Unsafe/unauthorized preview must reject: " + command);
    }

    @GameTest(template="empty", timeoutTicks=80, batch="isolated_blockbench_preview")
    public static void previewCommandsProtectWorldAndNeverBindQuestProgress(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(BlockPos.ZERO);
        // Intentionally straddle all four chunks. Only this test fixture explicitly loads them.
        var origin = new BlockPos((base.getX() >> 4) * 16 + 15, 160, (base.getZ() >> 4) * 16 + 15);
        var bounds = MushroomBellsPreview.bounds(origin);
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()))
            saved.put(p.immutable(), level.getBlockState(p));
        String oldProperty = System.getProperty(MushroomVale.DEV_PROPERTY);
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "BellsPreview"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.moveTo(origin.getX() + 9, origin.getY() + 1, origin.getZ() + 9, 0, 0);
        level.addNewPlayer(player);
        var source = player.createCommandSourceStack().withPermission(2);
        String place = "dudunka_dev mushroom_vale bells_preview place " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
        String clear = place.replace(" place ", " clear ");
        try {
            saved.keySet().forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 18));
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            h.assertTrue(commands().getRoot().getChild("dudunka_dev") == null, "No opt-in means no developer route");
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var dispatcher = commands();
            rejected(h, dispatcher, source.withPermission(0), place);
            rejected(h, dispatcher, level.getServer().createCommandSourceStack().withLevel(level), place);
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            rejected(h, dispatcher, source, place);
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var progress = ValeMusicProgress.get(level.getServer()).save(new CompoundTag());
            var forced = new HashSet<>(level.getForcedChunks());
            var obstruction = origin.offset(2, 2, 0);
            level.setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 18);
            var before = new LinkedHashMap<BlockPos, BlockState>();
            saved.keySet().forEach(p -> before.put(p, level.getBlockState(p)));
            rejected(h, dispatcher, source, place);
            h.assertTrue(before.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),
                    "Preflight leaves every existing block unchanged, including a block entity in the gaps");
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 18);
            player.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
            rejected(h, dispatcher, source, place);
            player.moveTo(origin.getX() + 9, origin.getY() + 1, origin.getZ() + 9, 0, 0);
            h.assertTrue(dispatcher.execute(place, source) == 1, "Explicit preview places across the four loaded chunks");
            var plan = MushroomBellsPreview.plan(origin);
            h.assertTrue(plan.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())), "Exact complete preview assembly");
            rejected(h, dispatcher, source, place);
            for (var p : plan.keySet()) h.assertTrue(ValeMusicQuest.resolve(level, p) == null, "Preview cannot impersonate a native music quest");
            var first = plan.keySet().iterator().next();
            level.setBlock(first, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
            rejected(h, dispatcher, source, clear);
            h.assertTrue(level.getBlockState(first).is(Blocks.DIAMOND_BLOCK), "Clear preserves changed player blocks");
            level.setBlock(first, plan.get(first), 18);
            h.assertTrue(dispatcher.execute(clear, source) == 1 && saved.keySet().stream().allMatch(level::isEmptyBlock),
                    "Matching explicit clear removes only the exact preview");
            h.assertTrue(ValeMusicProgress.get(level.getServer()).save(new CompoundTag()).equals(progress)
                    && level.getForcedChunks().equals(forced), "No saved progression or forced chunk tickets change");
            h.assertTrue(MushroomBellsPreview.siteProblem(level, origin.offset(1000000, 0, 1000000), false) != null,
                    "Unloaded neighbors reject without loading/generating a remote site");
        } catch (CommandSyntaxException ex) {
            throw new AssertionError("Authorized safe preview command failed", ex);
        } finally {
            player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            saved.forEach((p, s) -> level.setBlock(p, s, 18));
            if (oldProperty == null) System.clearProperty(MushroomVale.DEV_PROPERTY);
            else System.setProperty(MushroomVale.DEV_PROPERTY, oldProperty);
        }
        h.succeed();
    }
}
