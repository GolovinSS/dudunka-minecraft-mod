package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Last known positions only: live entities are never recreated from this index. */
public final class FamilyRegistry extends SavedData {
    public record Member(UUID id,UUID owner,Kind kind,String name,ResourceLocation dimension,BlockPos pos) {}
    private final Map<UUID,Member> members=new HashMap<>();
    public static FamilyRegistry get(MinecraftServer server){return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(FamilyRegistry::load,FamilyRegistry::new,"dudunka_family_registry");}
    public void observe(Companion mob){
        if(!(mob.level() instanceof ServerLevel level) || mob.ownerId()==null)return;
        var custom=mob.getCustomName();String name=custom==null || custom.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents && contents.getKey().equals("entity.dudunka."+mob.kind.id)?"":custom.getString();name=name.substring(0,Math.min(80,name.length()));
        var next=new Member(mob.getUUID(),mob.ownerId(),mob.kind,name,level.dimension().location(),mob.blockPosition().immutable());
        if(!next.equals(members.put(next.id(),next)))setDirty();
    }
    public Member member(UUID id){return members.get(id);}
    public List<Member> owned(UUID owner){return members.values().stream().filter(m->m.owner().equals(owner)).sorted(Comparator.comparing(m->m.id().toString())).toList();}
    public void forget(UUID id){if(members.remove(id)!=null)setDirty();}
    public static FamilyRegistry load(CompoundTag tag){
        var result=new FamilyRegistry();var list=tag.getList("Members",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){var t=list.getCompound(i);var dimension=ResourceLocation.tryParse(t.getString("Dimension"));int kind=t.getInt("Kind");
            if(!t.hasUUID("Id") || !t.hasUUID("Owner") || dimension==null || kind<0 || kind>=Kind.values().length)continue;
            String name=t.getString("Name");name=name.substring(0,Math.min(80,name.length()));UUID id=t.getUUID("Id");
            result.members.put(id,new Member(id,t.getUUID("Owner"),Kind.values()[kind],name,dimension,BlockPos.of(t.getLong("Pos"))));
        }return result;
    }
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();for(var m:members.values()){var t=new CompoundTag();t.putUUID("Id",m.id());t.putUUID("Owner",m.owner());t.putInt("Kind",m.kind().ordinal());t.putString("Name",m.name());t.putString("Dimension",m.dimension().toString());t.putLong("Pos",m.pos().asLong());list.add(t);}tag.put("Members",list);return tag;}
}
