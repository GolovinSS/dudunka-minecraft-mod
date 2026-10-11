package io.github.golovinss.dudunka;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent one-run-per-owner guard for the opt-in gateway test-family command. */
final class FamilyGatewayDevRuns extends SavedData {
    private final Set<UUID> preparedOwners=new HashSet<>();
    static FamilyGatewayDevRuns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FamilyGatewayDevRuns::load,FamilyGatewayDevRuns::new,"dudunka_gateway_dev_runs");
    }
    boolean prepared(UUID owner){return preparedOwners.contains(owner);}
    void markPrepared(UUID owner){if(preparedOwners.add(owner))setDirty();}
    static FamilyGatewayDevRuns load(CompoundTag tag) {
        var data=new FamilyGatewayDevRuns();var owners=tag.getList("Owners",Tag.TAG_COMPOUND);
        for(int i=0;i<owners.size();i++){int[] id=owners.getCompound(i).getIntArray("Id");if(id.length==4)data.preparedOwners.add(new UUID((long)id[0]<<32|(id[1]&0xffffffffL),(long)id[2]<<32|(id[3]&0xffffffffL)));}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){var owners=new ListTag();for(var id:preparedOwners){var row=new CompoundTag();row.putIntArray("Id",new int[]{(int)(id.getMostSignificantBits()>>>32),(int)id.getMostSignificantBits(),(int)(id.getLeastSignificantBits()>>>32),(int)id.getLeastSignificantBits()});owners.add(row);}tag.put("Owners",owners);return tag;}
}
