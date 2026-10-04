package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Optional owner/kind errands: no deadlines, penalties, entity references or exploration-note unlocks. */
public final class FriendRequests extends SavedData {
    public enum Task { FLOWER, COZY, TREAT }
    public enum Action { ACCEPT, DELIVER, SKIP }
    public record Info(Kind kind,Task task,boolean accepted,int memories,int completed,int retrySeconds) {}
    private record Key(UUID owner,Kind kind) {}
    private static final class Progress { int next,memories,completed;boolean accepted;long readyAt; }
    private final Map<Key,Progress> progress=new HashMap<>();
    public static final int COOLDOWN=12000;
    private FriendRequests(){}
    public static FriendRequests get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FriendRequests::load,FriendRequests::new,"dudunka_requests");}
    public static Task task(Kind kind,int next){return kind==Kind.SYUSYA?Task.TREAT:kind==Kind.MARUSYA?(next%2==0?Task.COZY:Task.TREAT):(next%2==0?Task.FLOWER:Task.TREAT);}
    public Info info(UUID owner,Kind kind,long now){var p=progress.get(new Key(owner,kind));if(p==null)return new Info(kind,task(kind,0),false,0,0,0);return new Info(kind,task(kind,p.next),p.accepted,p.memories,p.completed,(int)Math.min(600,Math.max(0,(p.readyAt-now+19)/20)));}
    private static boolean eligible(ServerPlayer player,Companion mob){return player!=null && mob!=null && player.isAlive() && !player.isSpectator() && mob.isAlive()
        && player.level()==mob.level() && player.getUUID().equals(mob.ownerId()) && mob.distanceToSqr(player)<=64 && mob.hasLineOfSight(player);}
    public boolean apply(ServerPlayer player,Companion mob,Action action){
        if(!eligible(player,mob))return false;long now=player.server.overworld().getGameTime();var key=new Key(player.getUUID(),mob.kind);var p=progress.computeIfAbsent(key,k->new Progress());
        if(now<p.readyAt)return false;
        if(action==Action.ACCEPT){if(p.accepted)return false;p.accepted=true;setDirty();return true;}
        if(action==Action.SKIP){p.accepted=false;p.next=(p.next+1)%2;p.readyAt=now+COOLDOWN;setDirty();return true;}
        if(!p.accepted)return false;Task task=task(mob.kind,p.next);
        if(task==Task.COZY){if(mob.albumHome().state()!=FamilyAlbum.HomeState.READY || FurnitureScenes.info(mob).state()!=FurnitureScenes.State.READY)return false;complete(player,mob,p,task);return true;}
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++){
            var stack=inventory.getItem(slot);if(stack.isEmpty())continue;
            if(task==Task.FLOWER && stack.is(ItemTags.SMALL_FLOWERS) && !stack.is(net.minecraft.world.item.Items.WITHER_ROSE)){if(!player.getAbilities().instabuild)stack.shrink(1);inventory.setChanged();complete(player,mob,p,task);return true;}
            if(task==Task.TREAT && mob.kind.likes(stack))return mob.feed(player,stack);
        }
        return false;
    }
    /** Called only after the normal feeding checks have consumed one liked item. */
    public void fed(ServerPlayer player,Companion mob){
        if(!eligible(player,mob))return;var p=progress.get(new Key(player.getUUID(),mob.kind));
        if(p!=null && p.accepted && task(mob.kind,p.next)==Task.TREAT && player.server.overworld().getGameTime()>=p.readyAt)complete(player,mob,p,Task.TREAT);
    }
    private void complete(ServerPlayer player,Companion mob,Progress p,Task task){
        // Commit before delivery: another click/feed cannot claim the same reward twice.
        p.accepted=false;p.memories|=1<<task.ordinal();p.completed=Math.min(1000000,p.completed+1);p.next=(p.next+1)%2;p.readyAt=player.server.overworld().getGameTime()+COOLDOWN;setDirty();mob.rewardRequest();
        var reward=new ItemStack(DudunkaMod.KEEPSAKE.get());reward.getOrCreateTag().putString("Friend",mob.kind.id);reward.getOrCreateTag().putString("Moment",task.name());
        if(!player.getInventory().add(reward))player.drop(reward,false);
        player.displayClientMessage(Component.translatable("request.dudunka.thanks."+mob.kind.id),false);
    }
    public static FriendRequests load(CompoundTag tag){var data=new FriendRequests();var rows=tag.getList("Requests",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);if(!row.hasUUID("Owner"))continue;Kind kind=null;for(var k:Kind.values())if(k.id.equals(row.getString("Kind")))kind=k;if(kind==null)continue;
            var p=new Progress();p.next=Math.floorMod(row.getInt("Next"),2);p.accepted=row.getBoolean("Accepted");p.memories=row.getInt("Memories")&7;p.completed=Math.max(0,Math.min(1000000,row.getInt("Completed")));p.readyAt=Math.max(0,row.getLong("ReadyAt"));data.progress.put(new Key(row.getUUID("Owner"),kind),p);
        }return data;
    }
    @Override public CompoundTag save(CompoundTag tag){var rows=new ListTag();progress.forEach((key,p)->{var row=new CompoundTag();row.putUUID("Owner",key.owner());row.putString("Kind",key.kind().id);row.putInt("Next",p.next);row.putBoolean("Accepted",p.accepted);row.putInt("Memories",p.memories);row.putInt("Completed",p.completed);row.putLong("ReadyAt",p.readyAt);rows.add(row);});tag.put("Requests",rows);return tag;}
}
