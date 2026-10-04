package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-authoritative ticket per transported entity, shared between dimensions. */
public final class CarrierLedger extends SavedData {
    private record Ticket(UUID token,UUID owner,boolean carried,CompoundTag backup) {}
    private final Map<UUID,Ticket> tickets=new HashMap<>();
    public static CarrierLedger get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(CarrierLedger::load,CarrierLedger::new,"dudunka_carriers");
    }
    public boolean capture(UUID entity,UUID token,UUID owner) {
        return capture(entity,token,owner,null);
    }
    public boolean capture(UUID entity,UUID token,UUID owner,CompoundTag backup) {
        var previous=tickets.get(entity);
        if (previous!=null && (previous.carried() || !previous.owner().equals(owner))) return false;
        tickets.put(entity,new Ticket(token,owner,true,backup==null?null:backup.copy()));setDirty();return true;
    }
    public boolean matches(UUID entity,UUID token,UUID owner) {
        var t=tickets.get(entity);return t!=null && t.carried() && t.token().equals(token) && t.owner().equals(owner);
    }
    public boolean consume(UUID entity,UUID token,UUID owner) {
        if (!matches(entity,token,owner)) return false;
        tickets.put(entity,new Ticket(token,owner,false,tickets.get(entity).backup()));setDirty();return true;
    }
    public void restore(UUID entity,UUID token,UUID owner) { var old=tickets.get(entity);tickets.put(entity,new Ticket(token,owner,true,old==null?null:old.backup()));setDirty(); }
    public boolean carried(UUID entity) { var t=tickets.get(entity);return t!=null&&t.carried(); }
    public record Carried(UUID id,boolean recoverable) {}
    public List<Carried> owned(UUID owner){return tickets.entrySet().stream().filter(e->e.getValue().carried() && e.getValue().owner().equals(owner)).sorted(Map.Entry.comparingByKey()).map(e->new Carried(e.getKey(),e.getValue().backup()!=null)).toList();}
    public CompoundTag backup(UUID id,UUID owner){var t=tickets.get(id);return t!=null && t.owner().equals(owner) && t.backup()!=null?t.backup().copy():null;}
    public boolean adopt(UUID id,UUID token,UUID owner,CompoundTag data){
        if(!matches(id,token,owner) || !data.hasUUID("UUID") || !id.equals(data.getUUID("UUID")) || !data.hasUUID("FamilyOwner") || !owner.equals(data.getUUID("FamilyOwner")) || !"dudunka:syusya".equals(data.getString("id")))return false;
        var t=tickets.get(id);if(t.backup()==null){tickets.put(id,new Ticket(token,owner,true,data.copy()));setDirty();}return true;
    }
    public boolean hasCarrier(net.minecraft.server.level.ServerPlayer player,UUID id){
        for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(!stack.is(DudunkaMod.CARRIER.get()) || !SyusyaCarrierItem.filled(stack))continue;var root=stack.getTag();var data=root.getCompound("Companion");if(data.hasUUID("UUID") && id.equals(data.getUUID("UUID")) && root.hasUUID("Ticket") && matches(id,root.getUUID("Ticket"),player.getUUID()))return true;}return false;
    }
    public boolean reissue(net.minecraft.server.level.ServerPlayer player,UUID id){return reissueResult(player,id).accepted();}
    public RecoveryResult reissueResult(net.minecraft.server.level.ServerPlayer player,UUID id){
        var t=tickets.get(id);if(t==null || !t.carried() || !t.owner().equals(player.getUUID()))return RecoveryResult.of(RecoveryResult.Code.REJECTED);
        if(t.backup()==null)return RecoveryResult.of(RecoveryResult.Code.NO_BACKUP);
        for(var world:player.server.getAllLevels())if(world.getEntity(id)!=null)return RecoveryResult.of(RecoveryResult.Code.REJECTED);
        if(hasCarrier(player,id))return RecoveryResult.of(RecoveryResult.Code.CARRIER_EXISTS);
        int slot=-1;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);
            if(i<36 && stack.is(DudunkaMod.CARRIER.get()) && stack.getCount()==1 && !SyusyaCarrierItem.filled(stack))slot=i;
        }
        if(slot<0)slot=player.getInventory().getFreeSlot();if(slot<0)return RecoveryResult.of(RecoveryResult.Code.NO_SLOT);
        UUID token=UUID.randomUUID();var stack=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());stack.getOrCreateTag().put("Companion",t.backup().copy());stack.getOrCreateTag().putUUID("Ticket",token);
        tickets.put(id,new Ticket(token,t.owner(),true,t.backup()));setDirty();player.getInventory().setItem(slot,stack);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();return RecoveryResult.of(RecoveryResult.Code.CARRIER_RESTORED);
    }
    public static CarrierLedger load(CompoundTag tag) {
        var result=new CarrierLedger();var entries=tag.getList("Tickets",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i);
            if(entry.hasUUID("Entity")&&entry.hasUUID("Token")&&entry.hasUUID("Owner"))result.tickets.put(entry.getUUID("Entity"),new Ticket(entry.getUUID("Token"),entry.getUUID("Owner"),entry.getBoolean("Carried"),entry.contains("Backup",10)?entry.getCompound("Backup").copy():null));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var entries=new ListTag();tickets.forEach((entity,t)->{var row=new CompoundTag();row.putUUID("Entity",entity);row.putUUID("Token",t.token());row.putUUID("Owner",t.owner());row.putBoolean("Carried",t.carried());if(t.backup()!=null)row.put("Backup",t.backup().copy());entries.add(row);});tag.put("Tickets",entries);return tag;
    }
}
