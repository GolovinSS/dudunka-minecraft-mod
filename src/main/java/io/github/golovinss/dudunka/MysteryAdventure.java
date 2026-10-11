package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Five owner-specific chapters in a separate additive ledger. Existing memories are never rewritten. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
public final class MysteryAdventure extends SavedData {
    public static final int CHAPTERS=5;
    private final Map<UUID,Integer> progress=new HashMap<>();
    public static MysteryAdventure get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MysteryAdventure::load,MysteryAdventure::new,"dudunka_vale_mystery");
    }
    public int chapters(UUID owner) { return progress.getOrDefault(owner,0); }
    /** Later real events retain the introductory chapters too, including the crafted-gateway path. */
    public void unlockThrough(UUID owner,int chapter) {
        if(owner==null || chapter<1 || chapter>CHAPTERS || chapter<=chapters(owner))return;
        progress.put(owner,chapter);setDirty();
    }
    public static void observe(ServerPlayer player) {
        if(!player.isAlive() || player.isSpectator())return;
        var data=get(player.server);var level=player.serverLevel();
        if(!level.dimension().equals(Level.OVERWORLD))return;
        var friends=level.getEntitiesOfClass(Companion.class,player.getBoundingBox().inflate(32),
                c->c.isAlive() && player.getUUID().equals(c.ownerId()) && c.distanceToSqr(player)<=1024);
        if(friends.stream().anyMatch(c->c.kind==Kind.DUDUNKA))data.unlockThrough(player.getUUID(),1);
        if(Arrays.stream(Kind.values()).allMatch(k->friends.stream().anyMatch(c->c.kind==k && c.stage()==2)))
            data.unlockThrough(player.getUUID(),2);
        if(data.chapters(player.getUUID())>=2 && level.getBiome(player.blockPosition()).is(BiomeTags.IS_FOREST))
            data.unlockThrough(player.getUUID(),3);
    }
    @SubscribeEvent public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount%100==0)
            observe(player);
    }
    @SubscribeEvent public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator() && event.getTo().equals(MushroomVale.LEVEL))
            get(player.server).unlockThrough(player.getUUID(),CHAPTERS);
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator() && player.serverLevel().dimension().equals(MushroomVale.LEVEL))
            get(player.server).unlockThrough(player.getUUID(),CHAPTERS);
    }
    public static MysteryAdventure load(CompoundTag tag) {
        var data=new MysteryAdventure();var rows=tag.getList("Owners",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) {
            var row=rows.getCompound(i);int chapter=row.getInt("Chapters");
            if(row.hasUUID("Owner") && chapter>=1 && chapter<=CHAPTERS)
                data.progress.merge(row.getUUID("Owner"),chapter,Math::max);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var rows=new ListTag();progress.forEach((owner,chapter)->{var row=new CompoundTag();row.putUUID("Owner",owner);row.putInt("Chapters",chapter);rows.add(row);});
        tag.put("Owners",rows);return tag;
    }
}
