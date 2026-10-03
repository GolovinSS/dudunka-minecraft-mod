package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-authoritative ticket per transported entity, shared between dimensions. */
public final class CarrierLedger extends SavedData {
    private record Ticket(UUID token,UUID owner,boolean carried) {}
    private final Map<UUID,Ticket> tickets=new HashMap<>();
    public static CarrierLedger get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(CarrierLedger::load,CarrierLedger::new,"dudunka_carriers");
    }
    public boolean capture(UUID entity,UUID token,UUID owner) {
        var previous=tickets.get(entity);
        if (previous!=null && (previous.carried() || !previous.owner().equals(owner))) return false;
        tickets.put(entity,new Ticket(token,owner,true));setDirty();return true;
    }
    public boolean matches(UUID entity,UUID token,UUID owner) {
        var t=tickets.get(entity);return t!=null && t.carried() && t.token().equals(token) && t.owner().equals(owner);
    }
    public boolean consume(UUID entity,UUID token,UUID owner) {
        if (!matches(entity,token,owner)) return false;
        tickets.put(entity,new Ticket(token,owner,false));setDirty();return true;
    }
    public void restore(UUID entity,UUID token,UUID owner) { tickets.put(entity,new Ticket(token,owner,true));setDirty(); }
    public boolean carried(UUID entity) { var t=tickets.get(entity);return t!=null&&t.carried(); }
    public static CarrierLedger load(CompoundTag tag) {
        var result=new CarrierLedger();var entries=tag.getList("Tickets",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i);
            if(entry.hasUUID("Entity")&&entry.hasUUID("Token")&&entry.hasUUID("Owner"))result.tickets.put(entry.getUUID("Entity"),new Ticket(entry.getUUID("Token"),entry.getUUID("Owner"),entry.getBoolean("Carried")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var entries=new ListTag();tickets.forEach((entity,t)->{var row=new CompoundTag();row.putUUID("Entity",entity);row.putUUID("Token",t.token());row.putUUID("Owner",t.owner());row.putBoolean("Carried",t.carried());entries.add(row);});tag.put("Tickets",entries);return tag;
    }
}
