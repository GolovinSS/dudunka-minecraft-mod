package io.github.golovinss.dudunka;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Direction-checked messages; all mutations run on the authenticated sender's server thread. */
public final class AlbumNetwork {
    private static final String VERSION="9";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(DudunkaMod.ID,"album"),()->VERSION,VERSION::equals,VERSION::equals);
    private AlbumNetwork() {}
    public static void register(){
        CHANNEL.registerMessage(0,AlbumCommands.Open.class,(message,buf)->{buf.writeUUID(message.session());FamilyAlbum.encode(message.snapshot(),buf);},
            buf->new AlbumCommands.Open(buf.readUUID(),FamilyAlbum.decode(buf)),(message,context)->{
                var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->io.github.golovinss.dudunka.client.FamilyAlbumScreen.open(message)));ctx.setPacketHandled(true);
            },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,AlbumCommands.Command.class,AlbumNetwork::encodeCommand,AlbumNetwork::decodeCommand,(message,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{
                handleCommand(ctx.getSender(),message);
            });ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,AlbumCommands.Reply.class,AlbumNetwork::encodeReply,AlbumNetwork::decodeReply,(message,context)->{
                var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->io.github.golovinss.dudunka.client.FamilyAlbumScreen.update(message)));ctx.setPacketHandled(true);
            },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,AlbumCommands.RecoveryCommand.class,(m,b)->{b.writeUUID(m.session());b.writeUUID(m.member());b.writeVarInt(m.request());},b->new AlbumCommands.RecoveryCommand(b.readUUID(),b.readUUID(),b.readVarInt()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->AlbumCommands.recover(ctx.getSender(),m));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4,AlbumCommands.FurnitureCommand.class,AlbumNetwork::encodeFurniture,AlbumNetwork::decodeFurniture,(m,c)->{var ctx=c.get();ctx.enqueueWork(()->handleFurniture(ctx.getSender(),m));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5,AlbumCommands.RequestCommand.class,AlbumNetwork::encodeRequest,AlbumNetwork::decodeRequest,(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{var reply=AlbumCommands.executeRequest(ctx.getSender(),m);if(reply!=null)reply(ctx.getSender(),reply);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void request(AlbumCommands.RequestCommand m){CHANNEL.sendToServer(m);}
    public static void encodeRequest(AlbumCommands.RequestCommand m,FriendlyByteBuf b){b.writeUUID(m.session());b.writeUUID(m.member());b.writeEnum(m.action());b.writeVarInt(m.request());}
    public static AlbumCommands.RequestCommand decodeRequest(FriendlyByteBuf b){return new AlbumCommands.RequestCommand(b.readUUID(),b.readUUID(),b.readEnum(FriendRequests.Action.class),b.readVarInt());}
    public static void furniture(AlbumCommands.FurnitureCommand message){CHANNEL.sendToServer(message);}
    public static void handleFurniture(ServerPlayer player,AlbumCommands.FurnitureCommand message){var reply=AlbumCommands.executeFurniture(player,message);if(reply!=null)reply(player,reply);}
    public static void encodeFurniture(AlbumCommands.FurnitureCommand m,FriendlyByteBuf b){b.writeUUID(m.session());b.writeUUID(m.member());b.writeBoolean(m.clear());b.writeVarInt(m.request());}
    public static AlbumCommands.FurnitureCommand decodeFurniture(FriendlyByteBuf b){return new AlbumCommands.FurnitureCommand(b.readUUID(),b.readUUID(),b.readBoolean(),b.readVarInt());}
    public static void encodeReply(AlbumCommands.Reply reply,FriendlyByteBuf buf){buf.writeUUID(reply.session());buf.writeVarInt(reply.request());buf.writeEnum(reply.result().code());buf.writeVarInt(reply.result().retrySeconds());FamilyAlbum.encode(reply.snapshot(),buf);}
    public static AlbumCommands.Reply decodeReply(FriendlyByteBuf buf){return new AlbumCommands.Reply(buf.readUUID(),buf.readVarInt(),new RecoveryResult(buf.readEnum(RecoveryResult.Code.class),buf.readVarInt()),FamilyAlbum.decode(buf));}
    public static void reply(ServerPlayer p,AlbumCommands.Reply message){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),message);}
    public static void recover(AlbumCommands.RecoveryCommand message){CHANNEL.sendToServer(message);}
    public static void handleCommand(ServerPlayer player,AlbumCommands.Command message){
        var reply=AlbumCommands.execute(player,message);
        if(reply!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);
    }
    public static void encodeCommand(AlbumCommands.Command message,FriendlyByteBuf buf){buf.writeUUID(message.session());buf.writeUUID(message.member());buf.writeBoolean(message.stay());buf.writeVarInt(message.request());buf.writeBoolean(message.home());}
    public static AlbumCommands.Command decodeCommand(FriendlyByteBuf buf){return new AlbumCommands.Command(buf.readUUID(),buf.readUUID(),buf.readBoolean(),buf.readVarInt(),buf.readBoolean());}
    public static void open(ServerPlayer player){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),AlbumCommands.open(player));}
    public static void command(AlbumCommands.Command message){CHANNEL.sendToServer(message);}
}
