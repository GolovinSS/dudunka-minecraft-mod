package io.github.golovinss.dudunka;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.*;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import java.util.*;

@Mod(DudunkaMod.ID)
public class DudunkaMod {
    public static final String ID="dudunka";
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT=DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,ID);
    public static final Map<Kind,RegistryObject<EntityType<Companion>>> TYPES=new EnumMap<>(Kind.class);
    public static final Map<Kind,RegistryObject<Block>> EGGS=new EnumMap<>(Kind.class);
    public static final Map<Kind,RegistryObject<Item>> EGG_ITEMS=new EnumMap<>(Kind.class);
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue HATCH_SECONDS,GROWTH_SECONDS;
    public static final ForgeConfigSpec.DoubleValue LOOT_MULTIPLIER;
    static {
        var b=new ForgeConfigSpec.Builder();
        HATCH_SECONDS=b.comment("Loaded seconds in valid conditions before hatching.").defineInRange("hatchSeconds",300,1,86400);
        GROWTH_SECONDS=b.comment("Loaded seconds per growth stage; two stages to adult.").defineInRange("growthStageSeconds",1200,1,86400);
        LOOT_MULTIPLIER=b.comment("Multiplier for egg chest chance; 0 disables loot.").defineInRange("eggLootMultiplier",1.0,0.0,10.0);
        CONFIG=b.build();
        for (Kind k:Kind.values()) {
            TYPES.put(k,ENTITIES.register(k.id,()->EntityType.Builder.<Companion>of((t,l)->new Companion(t,l,k),MobCategory.CREATURE).sized(k==Kind.SYUSYA?.35f:.4f,k.height).clientTrackingRange(8).build(ID+":"+k.id)));
            EGGS.put(k,BLOCKS.register(k.id+"_egg",()->new EggBlock(k,BlockBehaviour.Properties.of().strength(.3f).noOcclusion().lightLevel(s->k==Kind.DUDUNKA?5:0))));
            EGG_ITEMS.put(k,ITEMS.register(k.id+"_egg",()->new BlockItem(EGGS.get(k).get(),new Item.Properties().stacksTo(16))));
        }
    }
    public static final RegistryObject<BlockEntityType<EggEntity>> EGG_BE=BLOCK_ENTITIES.register("egg",()->BlockEntityType.Builder.of(EggEntity::new,EGGS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<Codec<EggLootModifier>> EGG_LOOT=LOOT.register("egg",()->EggLootModifier.CODEC);
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("family",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("tab.dudunka")).icon(()->new ItemStack(EGG_ITEMS.get(Kind.DUDUNKA).get())).displayItems((p,out)->EGG_ITEMS.values().forEach(v->out.accept(v.get()))).build());
    public DudunkaMod() {
        IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus); LOOT.register(bus);
        bus.addListener(this::attributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG);
    }
    private void attributes(EntityAttributeCreationEvent e) {
        TYPES.forEach((k,v)->e.put(v.get(),Companion.attributes(k).build()));
    }
}
