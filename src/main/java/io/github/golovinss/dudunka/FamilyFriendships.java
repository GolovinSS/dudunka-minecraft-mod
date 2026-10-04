package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Server-wide, owner-scoped relationships. Only sampled, simultaneous seated rest earns time. */
public final class FamilyFriendships extends SavedData {
    public static final int POINT_TICKS=1200;
    private record Pair(UUID owner,UUID first,UUID second) {
        static Pair of(UUID owner,UUID a,UUID b){
            if(owner==null || a==null || b==null || a.equals(b))return null;
            return a.compareTo(b)<0?new Pair(owner,a,b):new Pair(owner,b,a);
        }
    }
    private static final class Bond {
        final Kind firstKind,secondKind;
        int score,progress;
        Bond(Kind first,Kind second,int score,int progress){firstKind=first;secondKind=second;this.score=score;this.progress=progress;}
    }
    public record Relation(UUID partner,Kind kind,int score,int progress) {}
    private final Map<Pair,Bond> bonds=new HashMap<>();
    // Not saved: a restart, unload or interrupted scene cannot retroactively credit elapsed time.
    private final Map<Pair,Long> samples=new HashMap<>();
    public static FamilyFriendships get(MinecraftServer server){
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(FamilyFriendships::load,FamilyFriendships::new,"dudunka_friendships");
    }
    public int score(UUID owner,UUID a,UUID b){var bond=bonds.get(Pair.of(owner,a,b));return bond==null?0:bond.score;}
    public int progress(UUID owner,UUID a,UUID b){var bond=bonds.get(Pair.of(owner,a,b));return bond==null?0:bond.progress;}
    /** One observation per pair and second. Callers must first validate the live scene/participants. */
    public boolean observe(UUID owner,UUID a,Kind aKind,UUID b,Kind bKind,long now){
        Pair pair=Pair.of(owner,a,b);if(pair==null || aKind==null || bKind==null)return false;
        Bond bond=bonds.get(pair);
        if(bond==null){
            boolean ordered=pair.first().equals(a);
            bond=new Bond(ordered?aKind:bKind,ordered?bKind:aKind,0,0);bonds.put(pair,bond);setDirty();
        }
        if(bond.score>=100){samples.remove(pair);return false;}
        Long previous=samples.put(pair,now);
        if(samples.size()>512)samples.entrySet().removeIf(entry->now-entry.getValue()>40);
        if(previous==null || now-previous!=20)return false;
        bond.progress+=20;boolean grew=bond.progress>=POINT_TICKS;
        if(grew){bond.score=Math.min(100,bond.score+1);bond.progress=bond.score==100?0:bond.progress-POINT_TICKS;}
        setDirty();return grew;
    }
    public void pause(UUID owner,UUID member){samples.keySet().removeIf(pair->pair.owner().equals(owner) && (pair.first().equals(member)||pair.second().equals(member)));}
    private void pausePair(UUID owner,UUID a,UUID b){samples.remove(Pair.of(owner,a,b));}
    public List<Relation> relations(UUID owner,UUID member){
        var result=new ArrayList<Relation>();
        bonds.forEach((pair,bond)->{
            if(!pair.owner().equals(owner))return;
            if(pair.first().equals(member))result.add(new Relation(pair.second(),bond.secondKind,bond.score,bond.progress));
            else if(pair.second().equals(member))result.add(new Relation(pair.first(),bond.firstKind,bond.score,bond.progress));
        });
        result.sort(Comparator.comparingInt(Relation::score).reversed().thenComparing(Comparator.comparingInt(Relation::progress).reversed()).thenComparing(Relation::partner));
        return List.copyOf(result);
    }
    public BlockPos friendSeat(UUID owner,UUID member,Map<UUID,BlockPos> assigned){
        int best=9;BlockPos target=null;
        for(var entry:assigned.entrySet()){
            int strength=score(owner,member,entry.getKey());
            if(strength>best){best=strength;target=entry.getValue();}
        }
        return target;
    }
    public static void pause(Companion mob){
        if(mob.level() instanceof ServerLevel level && mob.ownerId()!=null)get(level.getServer()).pause(mob.ownerId(),mob.getUUID());
    }
    private static boolean seated(Companion mob,CampfireScenes.Scene scene){
        BlockPos seat=scene.seats().get(mob.getUUID());
        return mob.activity()==Activity.CAMP_REST && CampfireScenes.active(mob,scene) && seat!=null
            && mob.distanceToSqr(Vec3.atBottomCenterOf(seat))<=.36;
    }
    public static void tick(Companion mob,CampfireScenes.Scene scene){
        if(!(mob.level() instanceof ServerLevel level) || scene==null)return;
        long now=level.getServer().overworld().getGameTime();if(now%20!=0)return;
        var ledger=get(level.getServer());
        if(!seated(mob,scene)){ledger.pause(mob.ownerId(),mob.getUUID());return;}
        for(UUID id:scene.seats().keySet()){
            if(id.equals(mob.getUUID()))continue;
            var entity=level.getEntity(id);
            if(entity instanceof Companion other && seated(other,scene))
                ledger.observe(scene.owner(),mob.getUUID(),mob.kind,other.getUUID(),other.kind,now);
            else ledger.pausePair(scene.owner(),mob.getUUID(),id);
        }
    }
    private static boolean homeSeated(Companion mob,HomeTogetherScenes.Scene scene){
        BlockPos seat=scene.seats().get(mob.getUUID());
        return mob.activity()==Activity.SIT && HomeTogetherScenes.active(mob,scene) && seat!=null
            && mob.distanceToSqr(Vec3.atBottomCenterOf(seat))<=.36;
    }
    public static void tickHome(Companion mob,HomeTogetherScenes.Scene scene){
        if(!(mob.level() instanceof ServerLevel level) || scene==null)return;
        long now=level.getServer().overworld().getGameTime();if(now%20!=0)return;
        var ledger=get(level.getServer());
        if(!homeSeated(mob,scene)){ledger.pause(mob.ownerId(),mob.getUUID());return;}
        for(UUID id:scene.seats().keySet()){
            if(id.equals(mob.getUUID()))continue;
            var entity=level.getEntity(id);
            if(entity instanceof Companion other && homeSeated(other,scene))
                ledger.observe(scene.owner(),mob.getUUID(),mob.kind,other.getUUID(),other.kind,now);
            else ledger.pausePair(scene.owner(),mob.getUUID(),id);
        }
    }
    public static boolean show(Player viewer,Companion mob){
        if(!(mob.level() instanceof ServerLevel level) || !viewer.getUUID().equals(mob.ownerId()))return false;
        var relations=get(level.getServer()).relations(mob.ownerId(),mob.getUUID());
        viewer.displayClientMessage(Component.translatable("message.dudunka.friendship_header",mob.getDisplayName()),false);
        if(relations.isEmpty())viewer.displayClientMessage(Component.translatable("message.dudunka.friendship_empty"),false);
        for(int i=0;i<Math.min(5,relations.size());i++){
            Relation relation=relations.get(i);Component name=Component.translatable("entity.dudunka."+relation.kind().id);
            var entity=level.getEntity(relation.partner());
            if(entity instanceof Companion other && viewer.getUUID().equals(other.ownerId()))name=other.getDisplayName();
            String tier=relation.score()>=70?"family":relation.score()>=30?"close":relation.score()>=10?"friends":"acquainted";
            viewer.displayClientMessage(Component.translatable("message.dudunka.friendship_row",name,relation.score(),Component.translatable("friendship.dudunka."+tier)),false);
        }
        if(relations.size()>5)viewer.displayClientMessage(Component.translatable("message.dudunka.friendship_more",relations.size()-5),false);
        return true;
    }
    private static Kind kind(String id){for(Kind kind:Kind.values())if(kind.id.equals(id))return kind;return null;}
    public static FamilyFriendships load(CompoundTag tag){
        var ledger=new FamilyFriendships();var rows=tag.getList("Bonds",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);
            if(!row.hasUUID("Owner") || !row.hasUUID("First") || !row.hasUUID("Second"))continue;
            UUID owner=row.getUUID("Owner"),a=row.getUUID("First"),b=row.getUUID("Second");Pair pair=Pair.of(owner,a,b);
            Kind ak=kind(row.getString("FirstKind")),bk=kind(row.getString("SecondKind"));if(pair==null || ak==null || bk==null)continue;
            int score=Math.max(0,Math.min(100,row.getInt("Score"))),progress=score==100?0:Math.max(0,Math.min(POINT_TICKS-1,row.getInt("Progress")));
            boolean ordered=pair.first().equals(a);ledger.bonds.put(pair,new Bond(ordered?ak:bk,ordered?bk:ak,score,progress));
        }
        return ledger;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var rows=new ListTag();bonds.forEach((pair,bond)->{
            var row=new CompoundTag();row.putUUID("Owner",pair.owner());row.putUUID("First",pair.first());row.putUUID("Second",pair.second());
            row.putString("FirstKind",bond.firstKind.id);row.putString("SecondKind",bond.secondKind.id);row.putInt("Score",bond.score);row.putInt("Progress",bond.progress);rows.add(row);
        });tag.put("Bonds",rows);return tag;
    }
}
