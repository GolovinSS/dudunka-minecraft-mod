package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Nine discovered notes per player, retaining the original three low bits. */
public final class TrailProgress extends SavedData {
    private final Map<UUID,Integer> pages=new HashMap<>();
    public static TrailProgress get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TrailProgress::load,TrailProgress::new,"dudunka_trail");}
    public int mask(UUID player){return pages.getOrDefault(player,0);}
    public static int unlocked(int mask){int count=0;while(count<3 && (mask&(1<<count))!=0)count++;return count;}
    public boolean read(UUID player,int page){
        if(page<1 || page>FriendStories.NOTES)throw new IllegalArgumentException("Invalid trail page");
        int old=mask(player),next=old|(1<<(page-1));if(old==next)return false;
        pages.put(player,next);setDirty();return true;
    }
    public static TrailProgress load(CompoundTag tag){var result=new TrailProgress();var list=tag.getList("Pages",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var row=list.getCompound(i);if(row.hasUUID("Player"))result.pages.merge(row.getUUID("Player"),row.getInt("Mask")&FriendStories.MASK,(a,b)->a|b);}return result;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();pages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var row=new CompoundTag();row.putUUID("Player",e.getKey());row.putInt("Mask",e.getValue());list.add(row);});tag.put("Pages",list);return tag;}
}
