package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread-only sessions with weak player keys; recovery delegates bounded loading to FamilyRecovery. */
public final class AlbumCommands {
    public record Open(UUID session,FamilyAlbum.Snapshot snapshot) {}
    public record Command(UUID session,UUID member,boolean stay,int request) {}
    public record RecoveryCommand(UUID session,UUID member,int request) {}
    public record Reply(UUID session,int request,RecoveryResult result,FamilyAlbum.Snapshot snapshot) {
        public Reply(UUID session,int request,boolean accepted,FamilyAlbum.Snapshot snapshot){this(session,request,RecoveryResult.of(accepted?RecoveryResult.Code.APPLIED:RecoveryResult.Code.REJECTED),snapshot);}
        public boolean accepted(){return result.accepted();}
    }
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final class Session {
        final UUID id=UUID.randomUUID();final ResourceLocation dimension;
        Set<UUID> members,recovery;long expires,lastCommand=Long.MIN_VALUE;int request;
        Session(ServerPlayer player,FamilyAlbum.Snapshot snapshot){dimension=player.level().dimension().location();refresh(player,snapshot);}
        void refresh(ServerPlayer player,FamilyAlbum.Snapshot snapshot){
            members=new HashSet<>();for(var entry:snapshot.entries())members.add(entry.id());
            recovery=new HashSet<>();for(var entry:snapshot.recovery())recovery.add(entry.id());
            expires=player.level().getGameTime()+1200;
        }
    }
    private static boolean held(ServerPlayer p){return p.getMainHandItem().is(DudunkaMod.ALBUM.get()) || p.getOffhandItem().is(DudunkaMod.ALBUM.get());}
    public static boolean recoveryValid(ServerPlayer p,UUID id,int request){var s=SESSIONS.get(p);return s!=null && s.id.equals(id) && s.request==request && p.level().getGameTime()<=s.expires && s.dimension.equals(p.level().dimension().location()) && held(p) && p.isAlive() && !p.isSpectator();}
    public static void finishRecovery(ServerPlayer p,UUID id,int request,RecoveryResult result){if(!recoveryValid(p,id,request))return;var snapshot=FamilyAlbum.collect(p);SESSIONS.get(p).refresh(p,snapshot);AlbumNetwork.reply(p,new Reply(id,request,result,snapshot));}
    public static void recover(ServerPlayer p,RecoveryCommand command){
        if(p==null)return;var s=SESSIONS.get(p);long now=p.level().getGameTime();
        if(s==null || !s.id.equals(command.session()) || command.request()<=s.request || command.request()<=0)return;
        if(now>s.expires || !s.dimension.equals(p.level().dimension().location())){SESSIONS.remove(p);return;}
        // Keep the original job's request/session valid; a busy reply must not replace it.
        if(FamilyRecovery.pending(p.server,p.getUUID())){
            if(!recoveryValid(p,s.id,s.request) || s.lastCommand!=Long.MIN_VALUE && now-s.lastCommand<10)return;
            s.lastCommand=now;AlbumNetwork.reply(p,new Reply(s.id,command.request(),RecoveryResult.of(RecoveryResult.Code.BUSY),FamilyAlbum.collect(p)));return;
        }
        s.request=command.request();if(s.lastCommand!=Long.MIN_VALUE && now-s.lastCommand<10)return;s.lastCommand=now;
        if(!recoveryValid(p,s.id,s.request) || !s.recovery.contains(command.member())){finishRecovery(p,s.id,s.request,RecoveryResult.of(RecoveryResult.Code.REJECTED));return;}
        var ledger=CarrierLedger.get(p.server);
        if(ledger.carried(command.member())){finishRecovery(p,s.id,s.request,ledger.reissueResult(p,command.member()));return;}
        var result=FamilyRecovery.startResult(p,command.member(),s.id,s.request);
        if(!result.accepted())finishRecovery(p,s.id,s.request,result);
    }
    private AlbumCommands() {}
    public static Open open(ServerPlayer player) {
        var snapshot=FamilyAlbum.collect(player);var session=new Session(player,snapshot);SESSIONS.put(player,session);
        return new Open(session.id,snapshot);
    }
    public static Reply execute(ServerPlayer player,Command command) {
        if(player==null || FamilyRecovery.pending(player.server,player.getUUID()))return null;
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
