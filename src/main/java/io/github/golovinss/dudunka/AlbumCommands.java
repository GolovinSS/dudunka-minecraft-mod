package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread-only sessions. Weak player keys, no retained entities/worlds, no chunk loading. */
public final class AlbumCommands {
    public record Open(UUID session,FamilyAlbum.Snapshot snapshot) {}
    public record Command(UUID session,UUID member,boolean stay,int request) {}
    public record Reply(UUID session,int request,boolean accepted,FamilyAlbum.Snapshot snapshot) {}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final class Session {
        final UUID id=UUID.randomUUID();final ResourceLocation dimension;
        Set<UUID> members;long expires,lastCommand=Long.MIN_VALUE;int request;
        Session(ServerPlayer player,FamilyAlbum.Snapshot snapshot){dimension=player.level().dimension().location();refresh(player,snapshot);}
        void refresh(ServerPlayer player,FamilyAlbum.Snapshot snapshot){
            members=new HashSet<>();for(var entry:snapshot.entries())members.add(entry.id());
            expires=player.level().getGameTime()+1200;
        }
    }
    private AlbumCommands() {}
    public static Open open(ServerPlayer player) {
        var snapshot=FamilyAlbum.collect(player);var session=new Session(player,snapshot);SESSIONS.put(player,session);
        return new Open(session.id,snapshot);
    }
    public static Reply execute(ServerPlayer player,Command command) {
        if(player==null)return null;
        var session=SESSIONS.get(player);long now=player.level().getGameTime();
        if(session==null || !session.id.equals(command.session()) || command.request()<=session.request
            || command.request()<=0)return null;
        if(now>session.expires || !session.dimension.equals(player.level().dimension().location())){SESSIONS.remove(player);return null;}
        session.request=command.request();
        // Drop floods before collecting home/friend data or replying. Client can retry after timeout.
        if(session.lastCommand!=Long.MIN_VALUE && now-session.lastCommand<10)return null;
        session.lastCommand=now;
        boolean held=player.getMainHandItem().is(DudunkaMod.ALBUM.get()) || player.getOffhandItem().is(DudunkaMod.ALBUM.get());
        boolean accepted=false;
        if(held && player.isAlive() && !player.isSpectator() && session.members.contains(command.member())) {
            var target=player.serverLevel().getEntity(command.member());
            if(target instanceof Companion mob)accepted=mob.commandStay(player,command.stay());
        }
        var snapshot=FamilyAlbum.collect(player);session.refresh(player,snapshot);
        return new Reply(session.id,command.request(),accepted,snapshot);
    }
}
