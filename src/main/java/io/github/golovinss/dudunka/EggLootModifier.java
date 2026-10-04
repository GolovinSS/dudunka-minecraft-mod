package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import org.jetbrains.annotations.NotNull;

public class EggLootModifier extends LootModifier {
    public static final Codec<EggLootModifier> CODEC=RecordCodecBuilder.create(i->codecStart(i).and(i.group(Codec.STRING.fieldOf("kind").forGetter(m->m.kind.id),Codec.DOUBLE.fieldOf("chance").forGetter(m->m.chance))).apply(i,EggLootModifier::new));
    private final Kind kind; private final double chance;
    public EggLootModifier(LootItemCondition[] conditions,String kind,double chance){super(conditions);this.kind=Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));this.chance=chance;}
    @Override protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context) {
        if(loot.stream().anyMatch(stack->stack.is(DudunkaMod.EGG_ITEMS.get(kind).get())))return loot;
        if(context.getRandom().nextFloat()<Math.min(1,chance*DudunkaMod.LOOT_MULTIPLIER.get()))loot.add(new ItemStack(DudunkaMod.EGG_ITEMS.get(kind).get()));
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec(){return CODEC;}
}
