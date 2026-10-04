package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.fml.ModList;

/** Independent note rolls never change egg odds or replace other loot. */
public final class TrailLootModifier extends LootModifier {
    public static final Codec<TrailLootModifier> CODEC=RecordCodecBuilder.create(i->codecStart(i).and(i.group(Codec.intRange(1,3).fieldOf("page").forGetter(m->m.page),Codec.doubleRange(0,1).fieldOf("chance").forGetter(m->m.chance),Codec.BOOL.optionalFieldOf("without_mvs",false).forGetter(m->m.withoutMvs))).apply(i,TrailLootModifier::new));
    private final int page;private final double chance;private final boolean withoutMvs;
    public TrailLootModifier(LootItemCondition[] conditions,int page,double chance,boolean withoutMvs){super(conditions);this.page=page;this.chance=chance;this.withoutMvs=withoutMvs;}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){
        if(!DudunkaMod.TRAIL_NOTES.get() || withoutMvs && ModList.get().isLoaded("mvs"))return loot;
        var item=DudunkaMod.TRAIL_ITEMS.get(page).get();if(loot.stream().anyMatch(s->s.is(item)))return loot;
        // Separate deterministic random stream: note rolls must not consume the egg modifier's RNG.
        var origin=context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);if(origin==null)return loot;
        long seed=net.minecraft.util.Mth.getSeed(net.minecraft.core.BlockPos.containing(origin)) ^ context.getLevel().getSeed() ^ (0x9E3779B97F4A7C15L*page);
        if(net.minecraft.util.RandomSource.create(seed).nextDouble()<chance)loot.add(new ItemStack(item));return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec(){return CODEC;}
}
