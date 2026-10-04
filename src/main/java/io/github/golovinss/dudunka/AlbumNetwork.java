package io.github.golovinss.dudunka;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** One server-to-client message; the client cannot submit family data or commands. */
public final class AlbumNetwork {
    private static final String VERSION="1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(DudunkaMod.ID,"album"),()->VERSION,VERSION::equals,VERSION::equals);
    private AlbumNetwork() {}
    public static void register(){
        CHANNEL.registerMessage(0,FamilyAlbum.Snapshot.class,FamilyAlbum::encode,FamilyAlbum::decode,(snapshot,context)->{
            var ctx=context.get();
            ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->io.github.golovinss.dudunka.client.FamilyAlbumScreen.open(snapshot)));
            ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,FamilyAlbum.Snapshot snapshot){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),snapshot);}
}
