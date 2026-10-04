package io.github.golovinss.dudunka;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;

/** Additional stories use only already supported tables and a separate per-note random stream. */
public final class FriendNoteLootModifier extends LootModifier {
    public static final Codec<FriendNoteLootModifier> CODEC=RecordCodecBuilder.create(i->codecStart(i).apply(i,FriendNoteLootModifier::new));
    private static final Set<String> VILLAGES=Set.of("minecraft:chests/village/village_plains_house","minecraft:chests/village/village_desert_house","minecraft:chests/village/village_savanna_house","minecraft:chests/village/village_snowy_house","minecraft:chests/village/village_taiga_house");
    private static final Set<String> CAT=Set.of("minecraft:chests/abandoned_mineshaft","minecraft:chests/woodland_mansion","mvs:abandoned","betterdungeons:skeleton_dungeon/chests/common","betterdungeons:zombie_dungeon/chests/common");
    private static final Set<String> SNAIL=Set.of("minecraft:chests/simple_dungeon","mvs:swamps","betterdungeons:small_dungeon/chests/loot_piles");
    public FriendNoteLootModifier(LootItemCondition[] conditions){super(conditions);}
    public static boolean supports(String table,int note){return note>=4 && note<=9 && (VILLAGES.contains(table) || (note<=6?CAT:SNAIL).contains(table));}
    public static double chance(int note){return switch(FriendStories.page(note)){case 1->.20;case 2->.15;default->.10;};}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){
        if(!DudunkaMod.TRAIL_NOTES.get())return loot;
        var origin=context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);if(origin==null)return loot;
        var tableId=context.getQueriedLootTableId();if(tableId==null)return loot;var table=tableId.toString();
        for(int note=4;note<=9;note++){
            if(!supports(table,note))continue;var item=DudunkaMod.TRAIL_ITEMS.get(note).get();if(loot.stream().anyMatch(s->s.is(item)))continue;
            long seed=net.minecraft.util.Mth.getSeed(net.minecraft.core.BlockPos.containing(origin)) ^ context.getLevel().getSeed() ^ (0x9E3779B97F4A7C15L*note);
            if(net.minecraft.util.RandomSource.create(seed).nextDouble()<chance(note))loot.add(new ItemStack(item));
        }
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec(){return CODEC;}
}
