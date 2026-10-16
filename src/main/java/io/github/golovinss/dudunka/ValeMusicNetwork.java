package io.github.golovinss.dudunka;

import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Optional audio-only S2C channel. Album protocol and message IDs are untouched. */
public final class ValeMusicNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "vale_music_hints"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    public static void register() {
        CHANNEL.registerMessage(0, ValeMusicHints.Snapshot.class, ValeMusicNetwork::encode, ValeMusicNetwork::decode,
                (message, context) -> {
                    var ctx = context.get();
                    var connection = ctx.getNetworkManager();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> io.github.golovinss.dudunka.client.MushroomValeMusic.hint(connection, message)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static boolean supported(ServerPlayer player) {
        return player.connection != null && player.connection.connection.isConnected()
                && CHANNEL.isRemotePresent(player.connection.connection);
    }
    public static void send(ServerPlayer player, ValeMusicHints.Snapshot snapshot) {
        if (supported(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    public static void encode(ValeMusicHints.Snapshot snapshot, FriendlyByteBuf buf) {
        buf.writeResourceLocation(new ResourceLocation(snapshot.dimension()));
        buf.writeVarInt(snapshot.anchors().size());
        for (var anchor : snapshot.anchors()) buf.writeBlockPos(new BlockPos(anchor.x(), anchor.y(), anchor.z()));
    }
    public static ValeMusicHints.Snapshot decode(FriendlyByteBuf buf) {
        var dimension = buf.readResourceLocation().toString();
        int count = buf.readVarInt();
        if (count < 0 || count > ValeMusicHints.MAX_ANCHORS)
            throw new IllegalArgumentException("Invalid music landmark count");
        var anchors = new ArrayList<ValeMusicHints.Anchor>(count);
        for (int i = 0; i < count; i++) {
            var pos = buf.readBlockPos();
            anchors.add(new ValeMusicHints.Anchor(pos.getX(), pos.getY(), pos.getZ()));
        }
        return new ValeMusicHints.Snapshot(dimension, anchors);
    }
    private ValeMusicNetwork() {}
}
