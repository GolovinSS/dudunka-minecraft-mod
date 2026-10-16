package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeMusicHintTests {
    private static ValeMusicHints.Anchor anchor(ValeMusicTests.Site site) {
        return new ValeMusicHints.Anchor(site.root.getX(), site.root.getY(), site.root.getZ());
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void audioHintCodecRoundTripsAndRejectsUnboundedCounts(GameTestHelper h) {
        var anchors = new ArrayList<ValeMusicHints.Anchor>();
        for (int i = 0; i < ValeMusicHints.MAX_ANCHORS; i++) anchors.add(new ValeMusicHints.Anchor(-8-i, -64+i, 24+i));
        for (var list : List.of(List.<ValeMusicHints.Anchor>of(), anchors)) {
            var original = new ValeMusicHints.Snapshot(MushroomVale.ID.toString(), list);
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ValeMusicNetwork.encode(original, buf);
                h.assertTrue(ValeMusicNetwork.decode(buf).equals(original) && !buf.isReadable(),
                        "Bounded dimension/anchor payload round-trips exactly");
            } finally { buf.release(); }
        }
        for (int count : new int[]{-1, ValeMusicHints.MAX_ANCHORS+1, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeResourceLocation(MushroomVale.ID); buf.writeVarInt(count);
                boolean rejected = false;
                try { ValeMusicNetwork.decode(buf); } catch (IllegalArgumentException expected) { rejected = true; }
                h.assertTrue(rejected, "Reject malformed count before allocating or reading roots");
            } finally { buf.release(); }
        }
        var truncated = new FriendlyByteBuf(Unpooled.buffer());
        try {
            truncated.writeResourceLocation(MushroomVale.ID); truncated.writeVarInt(1);
            boolean rejected = false;
            try { ValeMusicNetwork.decode(truncated); } catch (IndexOutOfBoundsException expected) { rejected = true; }
            h.assertTrue(rejected, "Reject truncated root data without creating a usable snapshot");
        } finally { truncated.release(); }
        var snapshot = new ValeMusicHints.Snapshot(MushroomVale.ID.toString(), anchors);
        anchors.clear();
        h.assertTrue(snapshot.anchors().size() == ValeMusicHints.MAX_ANCHORS, "Snapshot is a defensive copy");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60, batch="isolated_music_interaction")
    public static void hintsRequireIntactLoadedNativeStartsForEachOfTheFiveLandmarks(GameTestHelper h) {
        for (var kind : ValeAdventureStructure.Landmark.values()) {
            try (var site = new ValeMusicTests.Site(h, kind)) {
                var center = Vec3.atCenterOf(site.root);
                var root = anchor(site);
                var forced = new HashSet<>(site.level.getForcedChunks());
                h.assertTrue(ValeMusicLandmarkHints.nearby(site.level, center).contains(root), "Intact native landmark is hinted");
                h.assertTrue(ValeMusicLandmarkHints.nearby(site.level, center.add(32,0,0)).contains(root),
                        "Bounded lookup reaches a loaded start two chunks away");
                h.assertTrue(!ValeMusicLandmarkHints.nearby(site.level, center.add(32.01,0,0)).contains(root),
                        "Do not send distant landmarks");
                var start = site.chunk.getStartForStructure(site.structure);
                site.chunk.setStartForStructure(site.structure, StructureStart.INVALID_START);
                h.assertTrue(!ValeMusicLandmarkHints.nearby(site.level, center).contains(root),
                        "Identical decorative blocks without a native start are ignored");
                site.chunk.setStartForStructure(site.structure, start);
                site.level.setBlock(site.root, Blocks.AIR.defaultBlockState(), 18);
                h.assertTrue(!ValeMusicLandmarkHints.nearby(site.level, center).contains(root), "Missing native root is ignored");
                site.level.setBlock(site.root, site.piece.plan().get(site.root), 18);
                var other = site.piece.plan().keySet().stream().filter(pos -> !pos.equals(site.root)).findFirst().orElseThrow();
                site.level.setBlock(other, Blocks.AIR.defaultBlockState(), 18);
                h.assertTrue(!ValeMusicLandmarkHints.nearby(site.level, center).contains(root),
                        "Blueprint damage removes a hint without changing quest mechanics or repairing blocks");
                site.level.setBlock(other, site.piece.plan().get(other), 18);
                if (kind == ValeAdventureStructure.Landmark.SILVER_WEB_HARP) {
                    var pane = site.piece.plan().entrySet().stream()
                            .filter(e -> e.getValue().is(Blocks.WHITE_STAINED_GLASS_PANE)).findFirst().orElseThrow();
                    site.level.setBlock(pane.getKey(), pane.getValue().setValue(BlockStateProperties.NORTH, true), 18);
                    h.assertTrue(ValeMusicLandmarkHints.nearby(site.level, center).contains(root),
                            "Ordinary dry pane connections remain valid");
                    site.level.setBlock(pane.getKey(), pane.getValue().setValue(BlockStateProperties.WATERLOGGED, true), 18);
                    h.assertTrue(!ValeMusicLandmarkHints.nearby(site.level, center).contains(root), "Waterlogged harp is invalid");
                }
                h.assertTrue(forced.equals(site.level.getForcedChunks()), "No chunk tickets or persistent mutations from detection");
                h.assertTrue(ValeMusicLandmarkHints.nearby(h.getLevel(), center).isEmpty(), "No hints in other dimensions");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40, batch="isolated_music_interaction")
    public static void remoteLookupAndAbsentOptionalChannelDoNotLoadOrSend(GameTestHelper h) {
        try (var site = new ValeMusicTests.Site(h)) {
            var remote = new Vec3(29000008.5,160.5,29000008.5);
            int cx = 29000008 >> 4, cz = 29000008 >> 4;
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++)
                h.assertTrue(site.level.getChunkSource().getChunkNow(cx+dx,cz+dz) == null, "Remote fixture starts unloaded");
            h.assertTrue(ValeMusicLandmarkHints.nearby(site.level, remote).isEmpty(), "Unloaded area yields no hint");
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++)
                h.assertTrue(site.level.getChunkSource().getChunkNow(cx+dx,cz+dz) == null, "Detection cannot load remote chunks");
            var p = site.player();
            h.assertTrue(!ValeMusicNetwork.supported(p), "No negotiated audio channel on a synthetic disconnected client");
            ValeMusicNetwork.send(p, new ValeMusicHints.Snapshot(MushroomVale.ID.toString(), List.of(anchor(site))));
            h.assertTrue(site.packets.get(p.getUUID()).isEmpty(), "Absent audio channel produces no packets");
        }
        h.succeed();
    }
}
