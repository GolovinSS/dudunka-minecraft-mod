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
    public record Entry(Component name,Kind kind,int stage,int trust,boolean staying,HomeInfo home,List<Friend> friends) {}
    public record Snapshot(int total,List<Entry> entries) {}
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
            entries.add(new Entry(name(mob),mob.kind,mob.stage(),mob.trust(),mob.staying(),mob.albumHome(),List.copyOf(friends)));
        }
        return new Snapshot(members.size(),List.copyOf(entries));
    }
    private static Component name(Companion mob) {
        var custom=mob.getCustomName();
        if(custom==null || custom.getContents() instanceof TranslatableContents contents
            && contents.getKey().equals("entity.dudunka."+mob.kind.id))return Component.translatable("entity.dudunka."+mob.kind.id);
        String text=custom.getString();return Component.literal(text.substring(0,Math.min(80,text.length())));
    }
    public static void encode(Snapshot snapshot,FriendlyByteBuf buf) {
        buf.writeVarInt(snapshot.total());buf.writeVarInt(snapshot.entries().size());
        for(var entry:snapshot.entries()) {
            buf.writeComponent(entry.name());buf.writeEnum(entry.kind());buf.writeVarInt(entry.stage());buf.writeVarInt(entry.trust());buf.writeBoolean(entry.staying());
            buf.writeEnum(entry.home().state());buf.writeByte(entry.home().flags());buf.writeVarInt(entry.friends().size());
            for(var friend:entry.friends()){buf.writeComponent(friend.name());buf.writeVarInt(friend.score());}
        }
    }
    private static int bounded(FriendlyByteBuf buf,int min,int max){int n=buf.readVarInt();if(n<min || n>max)throw new IllegalArgumentException("Invalid album value");return n;}
    public static Snapshot decode(FriendlyByteBuf buf) {
        int total=bounded(buf,0,Integer.MAX_VALUE),count=bounded(buf,0,12);if(total<count)throw new IllegalArgumentException("Invalid album count");
        var entries=new ArrayList<Entry>();
        for(int i=0;i<count;i++){
            Component name=buf.readComponent();Kind kind=buf.readEnum(Kind.class);int stage=bounded(buf,0,2),trust=bounded(buf,0,100);boolean staying=buf.readBoolean();
            var home=new HomeInfo(buf.readEnum(HomeState.class),buf.readUnsignedByte()&31);int size=bounded(buf,0,5);var friends=new ArrayList<Friend>();
            for(int j=0;j<size;j++)friends.add(new Friend(buf.readComponent(),bounded(buf,0,100)));
            entries.add(new Entry(name,kind,stage,trust,staying,home,List.copyOf(friends)));
        }
        return new Snapshot(total,List.copyOf(entries));
    }
}
