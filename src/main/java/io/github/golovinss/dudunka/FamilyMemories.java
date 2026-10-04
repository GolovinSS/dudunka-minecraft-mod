package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** First observed milestones per owner and species. No inferred history or entity references. */
public final class FamilyMemories extends SavedData {
    public enum Event { HATCHED, TEEN, ADULT, MET_DUDUNKA, MET_MARUSYA, MET_SYUSYA, SHARED_SCENE }
    public record Memory(Kind kind,Event event,long tick) {}
    public static final int LIMIT=18;
    private final Map<UUID,Map<Integer,Memory>> entries=new HashMap<>();
    public static FamilyMemories get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FamilyMemories::load,FamilyMemories::new,"dudunka_family_memories");}
    public static boolean valid(Kind kind,Event event){return kind!=null && event!=null && event!=Event.values()[Event.MET_DUDUNKA.ordinal()+kind.ordinal()];}
    private static int key(Kind kind,Event event){return kind.ordinal()*Event.values().length+event.ordinal();}
    public boolean record(UUID owner,Kind kind,Event event,long tick){
        if(owner==null || !valid(kind,event) || tick<0)return false;
        var family=entries.computeIfAbsent(owner,id->new HashMap<>());
        if(family.putIfAbsent(key(kind,event),new Memory(kind,event,tick))!=null)return false;
        setDirty();return true;
    }
    public List<Memory> owned(UUID owner){return entries.getOrDefault(owner,Map.of()).values().stream().sorted(Comparator.comparingLong(Memory::tick).thenComparing(m->m.kind().ordinal()).thenComparing(m->m.event().ordinal())).toList();}
    public static void record(Companion mob,Event event){if(mob.level() instanceof ServerLevel level)get(level.getServer()).record(mob.ownerId(),mob.kind,event,level.getServer().overworld().getGameTime());}
    public static void together(MinecraftServer server,UUID owner,Kind a,Kind b,long now){
        var ledger=get(server);if(a!=b){ledger.record(owner,a,Event.values()[Event.MET_DUDUNKA.ordinal()+b.ordinal()],now);ledger.record(owner,b,Event.values()[Event.MET_DUDUNKA.ordinal()+a.ordinal()],now);}
        ledger.record(owner,a,Event.SHARED_SCENE,now);ledger.record(owner,b,Event.SHARED_SCENE,now);
    }
    public static void observeNearby(Companion mob){
        if(!(mob.level() instanceof ServerLevel level) || mob.ownerId()==null)return;
        var ledger=get(level.getServer());long now=level.getServer().overworld().getGameTime();
        for(var other:level.getEntitiesOfClass(Companion.class,mob.getBoundingBox().inflate(6),c->c.isAlive() && c.kind!=mob.kind && mob.sameFamily(c) && mob.distanceToSqr(c)<=36)){
            if(!mob.hasLineOfSight(other))continue;
            ledger.record(mob.ownerId(),mob.kind,Event.values()[Event.MET_DUDUNKA.ordinal()+other.kind.ordinal()],now);
            ledger.record(mob.ownerId(),other.kind,Event.values()[Event.MET_DUDUNKA.ordinal()+mob.kind.ordinal()],now);
        }
    }
    public static FamilyMemories load(CompoundTag tag){
        var result=new FamilyMemories();var rows=tag.getList("Memories",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);if(!row.hasUUID("Owner") || row.getLong("Tick")<0)continue;
            try{var kind=Kind.valueOf(row.getString("Kind"));var event=Event.valueOf(row.getString("Event"));if(!valid(kind,event))continue;
                result.entries.computeIfAbsent(row.getUUID("Owner"),id->new HashMap<>()).putIfAbsent(key(kind,event),new Memory(kind,event,row.getLong("Tick")));
            }catch(IllegalArgumentException ignored){}
        }return result;
    }
    @Override public CompoundTag save(CompoundTag tag){var rows=new ListTag();entries.forEach((owner,family)->family.values().forEach(m->{var row=new CompoundTag();row.putUUID("Owner",owner);row.putString("Kind",m.kind().name());row.putString("Event",m.event().name());row.putLong("Tick",m.tick());rows.add(row);}));tag.put("Memories",rows);return tag;}
}
