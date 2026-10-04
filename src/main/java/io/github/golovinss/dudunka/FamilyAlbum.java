package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

/** Owner-filtered, bounded snapshot. No chunk loading or persistent entity references. */
public final class FamilyAlbum {
    public enum HomeState { NONE, LEGACY, OTHER_DIMENSION, UNLOADED, MISSING, INCOMPLETE, READY, UNSAFE }
    public record HomeInfo(HomeState state,int flags) {}
    public record Friend(Component name,int score) {}
    public record Entry(UUID id,Component name,Kind kind,int stage,int trust,boolean staying,HomeInfo home,List<Friend> friends) {}
    public enum RecoveryState { LIVE, CARRIED, LEGACY_CARRIED }
    public record RecoveryEntry(UUID id,Component name,Kind kind,String dimension,net.minecraft.core.BlockPos pos,RecoveryState state) {}
    public record Snapshot(int total,List<Entry> entries,int guideFlags,List<RecoveryEntry> recovery,int recoveryTotal) {
        public Snapshot(int total,List<Entry> entries,int guideFlags){this(total,entries,guideFlags,List.of(),0);}
        public Snapshot(int total,List<Entry> entries){this(total,entries,0);}
    }
    private FamilyAlbum() {}
    public static Snapshot collect(ServerPlayer player) {
        var level=player.serverLevel();
        var members=level.getEntitiesOfClass(Companion.class,player.getBoundingBox().inflate(64),
            mob->mob.isAlive() && player.getUUID().equals(mob.ownerId()) && mob.distanceToSqr(player)<=4096);
        members.sort(Comparator.comparing((Companion mob)->mob.kind.ordinal()).thenComparing(mob->mob.getUUID().toString()));
        var entries=new ArrayList<Entry>();var ledger=FamilyFriendships.get(level.getServer());
        for(Companion mob:members.subList(0,Math.min(12,members.size()))) {
            var friends=new ArrayList<Friend>();
            for(var relation:ledger.relations(player.getUUID(),mob.getUUID()).stream().limit(5).toList()) {
                Component name=Component.translatable("entity.dudunka."+relation.kind().id);
                var other=level.getEntity(relation.partner());
                if(other instanceof Companion companion && player.getUUID().equals(companion.ownerId()))name=name(companion);
                friends.add(new Friend(name,relation.score()));
            }
            entries.add(new Entry(mob.getUUID(),name(mob),mob.kind,mob.stage(),mob.trust(),mob.staying(),mob.albumHome(),List.copyOf(friends)));
        }
        var recovery=new TreeMap<UUID,RecoveryEntry>();var registry=FamilyRegistry.get(level.getServer());
        for(var member:registry.owned(player.getUUID()))recovery.put(member.id(),new RecoveryEntry(member.id(),member.name().isEmpty()?Component.translatable("entity.dudunka."+member.kind().id):Component.literal(member.name()),member.kind(),member.dimension().toString(),member.pos(),RecoveryState.LIVE));
        for(var carried:CarrierLedger.get(level.getServer()).owned(player.getUUID())){
            var old=recovery.get(carried.id());recovery.put(carried.id(),new RecoveryEntry(carried.id(),old==null?Component.translatable("entity.dudunka.syusya"):old.name(),Kind.SYUSYA,old==null?"":old.dimension(),old==null?net.minecraft.core.BlockPos.ZERO:old.pos(),carried.recoverable()?RecoveryState.CARRIED:RecoveryState.LEGACY_CARRIED));
        }
        return new Snapshot(members.size(),List.copyOf(entries),EggGuide.flags(),recovery.values().stream().limit(32).toList(),recovery.size());
    }
    private static Component name(Companion mob) {
        var custom=mob.getCustomName();
        if(custom==null || custom.getContents() instanceof TranslatableContents contents
            && contents.getKey().equals("entity.dudunka."+mob.kind.id))return Component.translatable("entity.dudunka."+mob.kind.id);
        String text=custom.getString();return Component.literal(text.substring(0,Math.min(80,text.length())));
    }
    public static void encode(Snapshot snapshot,FriendlyByteBuf buf) {
        buf.writeVarInt(snapshot.total());buf.writeVarInt(snapshot.entries().size());buf.writeByte(snapshot.guideFlags());
        for(var entry:snapshot.entries()) {
            buf.writeUUID(entry.id());buf.writeComponent(entry.name());buf.writeEnum(entry.kind());buf.writeVarInt(entry.stage());buf.writeVarInt(entry.trust());buf.writeBoolean(entry.staying());
            buf.writeEnum(entry.home().state());buf.writeByte(entry.home().flags());buf.writeVarInt(entry.friends().size());
            for(var friend:entry.friends()){buf.writeComponent(friend.name());buf.writeVarInt(friend.score());}
        }
        encodeRecovery(snapshot,buf);
    }
    private static void encodeRecovery(Snapshot snapshot,FriendlyByteBuf buf){buf.writeVarInt(snapshot.recoveryTotal());buf.writeVarInt(snapshot.recovery().size());for(var e:snapshot.recovery()){buf.writeUUID(e.id());buf.writeComponent(e.name());buf.writeEnum(e.kind());buf.writeUtf(e.dimension(),256);buf.writeBlockPos(e.pos());buf.writeEnum(e.state());}}
    private static int bounded(FriendlyByteBuf buf,int min,int max){int n=buf.readVarInt();if(n<min || n>max)throw new IllegalArgumentException("Invalid album value");return n;}
    public static Snapshot decode(FriendlyByteBuf buf) {
        int total=bounded(buf,0,Integer.MAX_VALUE),count=bounded(buf,0,12);if(total<count)throw new IllegalArgumentException("Invalid album count");
        int flags=buf.readUnsignedByte()&31;var entries=new ArrayList<Entry>();
        for(int i=0;i<count;i++){
            UUID id=buf.readUUID();Component name=buf.readComponent();Kind kind=buf.readEnum(Kind.class);int stage=bounded(buf,0,2),trust=bounded(buf,0,100);boolean staying=buf.readBoolean();
            var home=new HomeInfo(buf.readEnum(HomeState.class),buf.readUnsignedByte()&31);int size=bounded(buf,0,5);var friends=new ArrayList<Friend>();
            for(int j=0;j<size;j++)friends.add(new Friend(buf.readComponent(),bounded(buf,0,100)));
            entries.add(new Entry(id,name,kind,stage,trust,staying,home,List.copyOf(friends)));
        }
        int recoveryTotal=bounded(buf,0,Integer.MAX_VALUE),size=bounded(buf,0,32);if(recoveryTotal<size)throw new IllegalArgumentException("Invalid recovery count");var recovery=new ArrayList<RecoveryEntry>();
        for(int i=0;i<size;i++)recovery.add(new RecoveryEntry(buf.readUUID(),buf.readComponent(),buf.readEnum(Kind.class),buf.readUtf(256),buf.readBlockPos(),buf.readEnum(RecoveryState.class)));
        return new Snapshot(total,List.copyOf(entries),flags,List.copyOf(recovery),recoveryTotal);
    }
}
