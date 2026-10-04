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
    public static final Map<Kind,RegistryObject<Block>> HOMES=new EnumMap<>(Kind.class);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES=DeferredRegister.create(ForgeRegistries.FEATURES,ID);
    public static final RegistryObject<SnailEggFeature> SNAIL_EGG_FEATURE=FEATURES.register("snail_egg",SnailEggFeature::new);
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue NATURAL_EGGS,FAMILY_CATCH_UP,TRAIL_NOTES;
    public static final Map<Integer,RegistryObject<Item>> TRAIL_ITEMS=new TreeMap<>();
    public static final ForgeConfigSpec.IntValue HATCH_SECONDS,GROWTH_SECONDS;
    public static final ForgeConfigSpec.DoubleValue LOOT_MULTIPLIER;
    static {
        var b=new ForgeConfigSpec.Builder();
        HATCH_SECONDS=b.comment("Loaded seconds in valid conditions before hatching.").defineInRange("hatchSeconds",300,1,86400);
        GROWTH_SECONDS=b.comment("Loaded seconds per growth stage; two stages to adult.").defineInRange("growthStageSeconds",1200,1,86400);
        LOOT_MULTIPLIER=b.comment("Multiplier for egg chest chance; 0 disables loot.").defineInRange("eggLootMultiplier",1.0,0.0,10.0);
        NATURAL_EGGS=b.comment("Allow rare natural Syusya eggs in newly generated swamp and lush-cave chunks.").define("naturalEggGeneration",true);
        FAMILY_CATCH_UP=b.comment("Allow safe same-dimension catch-up after six stalled following seconds, within 64 loaded blocks.").define("familyCatchUp",true);
        TRAIL_NOTES=b.comment("Add reusable exploration notes to supported, not-yet-generated chest loot.").define("trailNotes",true);
        CONFIG=b.build();
        for(int page=1;page<=3;page++){final int n=page;TRAIL_ITEMS.put(n,ITEMS.register("trail_note_"+n,()->new TrailNoteItem(n,new Item.Properties().stacksTo(1))));}
        for (Kind k:Kind.values()) {
            TYPES.put(k,ENTITIES.register(k.id,()->EntityType.Builder.<Companion>of((t,l)->new Companion(t,l,k),MobCategory.CREATURE).sized(k==Kind.SYUSYA?.35f:.4f,k.height).clientTrackingRange(8).build(ID+":"+k.id)));
            EGGS.put(k,BLOCKS.register(k.id+"_egg",()->new EggBlock(k,BlockBehaviour.Properties.of().strength(.3f,3600000f).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion().lightLevel(s->k==Kind.DUDUNKA?5:0))));
            HOMES.put(k,BLOCKS.register(k.id+"_home",()->new HomeMarkerBlock(k,BlockBehaviour.Properties.of().strength(.8f,3600000f).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))));
            ITEMS.register(k.id+"_home",()->new BlockItem(HOMES.get(k).get(),new Item.Properties()));
            EGG_ITEMS.put(k,ITEMS.register(k.id+"_egg",()->new BlockItem(EGGS.get(k).get(),new Item.Properties().stacksTo(16))));
        }
    }
    public static final RegistryObject<BlockEntityType<EggEntity>> EGG_BE=BLOCK_ENTITIES.register("egg",()->BlockEntityType.Builder.of(EggEntity::new,EGGS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<HomeMarkerEntity>> HOME_BE=BLOCK_ENTITIES.register("home",()->BlockEntityType.Builder.of(HomeMarkerEntity::new,HOMES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<Item> ALBUM=ITEMS.register("family_album",()->new FamilyAlbumItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CARRIER=ITEMS.register("syusya_carrier",()->new SyusyaCarrierItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<Codec<TrailLootModifier>> TRAIL_LOOT=LOOT.register("trail_note",()->TrailLootModifier.CODEC);
    public static final RegistryObject<Codec<EggLootModifier>> EGG_LOOT=LOOT.register("egg",()->EggLootModifier.CODEC);
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("family",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("tab.dudunka")).icon(()->new ItemStack(EGG_ITEMS.get(Kind.DUDUNKA).get())).displayItems((p,out)->{ EGG_ITEMS.values().forEach(v->out.accept(v.get())); HOMES.values().forEach(v->out.accept(v.get())); out.accept(CARRIER.get()); out.accept(ALBUM.get()); TRAIL_ITEMS.values().forEach(v->out.accept(v.get())); }).build());
    public DudunkaMod() {
        IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus); LOOT.register(bus); FEATURES.register(bus);
        bus.addListener(this::attributes);
        AlbumNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG);
    }
    private void attributes(EntityAttributeCreationEvent e) {
        TYPES.forEach((k,v)->e.put(v.get(),Companion.attributes(k).build()));
    }
}
