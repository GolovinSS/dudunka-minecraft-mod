package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.FluffyJumper;
import io.github.golovinss.dudunka.MushroomVale;
import io.github.golovinss.dudunka.Pufik;
import io.github.golovinss.dudunka.ValeSpider;
import io.github.golovinss.dudunka.ValeWildlifePopulation;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class ValeWildlifePopulationTests {
    @GameTest(template="empty", timeoutTicks=100, batch="isolated_vale_wildlife_lists")
    public static void exclusiveCreatureListsAndIntendedHabitats(GameTestHelper h) {
        ServerLevel vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var biomes=vale.registryAccess().registryOrThrow(Registries.BIOME);
        for(String id:List.of("glowing_mushroom_forest","memory_forest","pink_meadows")) {
            var key=net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dudunka",id));
            var entries=biomes.get(key).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
            h.assertTrue(entries.stream().noneMatch(e->net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.type).getNamespace().equals("minecraft")),
                    "Vale biome has no vanilla creature-category competitors: "+id);
            h.assertTrue(entries.stream().noneMatch(e->e.type==DudunkaMod.PUFIK.get())==!id.equals("glowing_mushroom_forest"),
                    "Pufik remains restricted to Glowing Mushroom Forest");
            h.assertTrue(entries.stream().noneMatch(e->e.type==DudunkaMod.VALE_SPIDER.get())==id.equals("pink_meadows"),
                    "Vale Spider remains restricted to both forests");
            h.assertTrue(entries.stream().noneMatch(e->e.type==DudunkaMod.FLUFFY_JUMPER.get())==!id.equals("pink_meadows"),
                    "Fluffy Jumper remains restricted to Pink Meadows");
            h.assertTrue(entries.stream().noneMatch(e->e.type==DudunkaMod.VALE_FIREFLY.get()),
                    "Fireflies remain in the independent AMBIENT category");
        }
        var glowing=biomes.get(Pufik.HABITAT).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
        var memory=biomes.get(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dudunka","memory_forest")))
                .getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
        var pink=biomes.get(FluffyJumper.HABITAT).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
        h.assertTrue(glowing.stream().anyMatch(e->e.type==DudunkaMod.PUFIK.get() && e.getWeight().asInt()==40 && e.minCount==1 && e.maxCount==2),
                "Glowing Forest keeps frequent 1–2 Pufik groups");
        h.assertTrue(glowing.stream().anyMatch(e->e.type==DudunkaMod.VALE_SPIDER.get() && e.getWeight().asInt()==20 && e.minCount==1 && e.maxCount==2),
                "Glowing Forest offers occasional friendly spider groups");
        h.assertTrue(memory.stream().anyMatch(e->e.type==DudunkaMod.VALE_SPIDER.get() && e.getWeight().asInt()==44 && e.minCount==1 && e.maxCount==2),
                "Memory Forest remains the spider's primary habitat");
        h.assertTrue(memory.stream().anyMatch(e->e.type==DudunkaMod.WISE_FLY_AGARIC.get() && e.getWeight().asInt()==10 && e.minCount==1 && e.maxCount==1),
                "Wise Fly Agaric's existing Memory Forest population remains intact");
        h.assertTrue(pink.stream().anyMatch(e->e.type==DudunkaMod.FLUFFY_JUMPER.get() && e.getWeight().asInt()==24 && e.minCount==2 && e.maxCount==3),
                "Pink Meadows has frequent playful 2–3 Jumper groups");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100, batch="isolated_vale_wildlife_policy")
    public static void independentBudgetCooldownAndGameRule(GameTestHelper h) {
        ServerLevel vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        boolean oldSpawning=vale.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        var oldRule=vale.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
        var player=ValeFireflyPopulationTests.observer(vale);
        var second=ValeFireflyPopulationTests.observer(vale);
        net.minecraft.server.level.ServerPlayer overworldPlayer=null;
        player.moveTo(1024,160,1024,0,0);
        second.moveTo(1027,160,1024,0,0);
        vale.getChunk(64,64);
        vale.addNewPlayer(player);
        vale.addNewPlayer(second);
        var existingAnimal=EntityType.MOOSHROOM.create(vale);
        existingAnimal.moveTo(1025,160,1026,0,0);
        vale.addFreshEntity(existingAnimal);
        var protectedPufik=DudunkaMod.PUFIK.get().create(vale);
        protectedPufik.moveTo(1026,160,1026,0,0);
        protectedPufik.setCustomName(net.minecraft.network.chat.Component.literal("Saved friend"));
        protectedPufik.setPersistenceRequired();
        vale.addFreshEntity(protectedPufik);
        var before=vale.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(64)).size();
        try {
            oldRule.set(true,h.getLevel().getServer());
            h.assertTrue(ValeWildlifePopulation.mayAssist(vale),"Server-side Vale assistance is enabled with normal mob spawning");
            h.assertTrue(ValeWildlifePopulation.nearAnyPlayer(vale,new net.minecraft.core.BlockPos(1030,160,1024),8),
                    "Two overlapping player areas protect the same nearby candidate position");
            int[] empty={0,0,0,0};
            h.assertTrue(ValeWildlifePopulation.underPopulationLimits(empty,DudunkaMod.PUFIK.get()),
                    "Independent local wildlife budget remains available when vanilla's creature category is full");
            h.assertTrue(ValeWildlifePopulation.groupSize(DudunkaMod.FLUFFY_JUMPER.get(),empty,vale.random)>=2
                            && ValeWildlifePopulation.groupSize(DudunkaMod.FLUFFY_JUMPER.get(),empty,vale.random)<=3,
                    "Assisted Jumper groups stay playful and small");
            h.assertTrue(ValeWildlifePopulation.groupSize(DudunkaMod.PUFIK.get(),new int[]{3,0,0,11},vale.random)<=1,
                    "Species and total remaining capacity clamp a group");
            h.assertTrue(ValeWildlifePopulation.groupSize(DudunkaMod.PUFIK.get(),new int[]{4,0,0,4},vale.random)==0
                            && ValeWildlifePopulation.groupSize(DudunkaMod.VALE_SPIDER.get(),new int[]{0,4,0,4},vale.random)==0
                            && ValeWildlifePopulation.groupSize(DudunkaMod.FLUFFY_JUMPER.get(),new int[]{0,0,6,6},vale.random)==0,
                    "Each species stops at its nearby limit");
            h.assertTrue(ValeWildlifePopulation.cooldownElapsed(Long.MIN_VALUE,0)
                            && !ValeWildlifePopulation.cooldownElapsed(100,499)
                            && ValeWildlifePopulation.cooldownElapsed(100,500),
                    "A shared Vale-level cooldown prevents overlapping players from triggering repeated groups");

            oldRule.set(false,h.getLevel().getServer());
            h.assertTrue(!ValeWildlifePopulation.mayAssist(vale),"doMobSpawning=false disables the assistance path");
            ValeWildlifePopulation.assistNear(vale,player,vale.random);
            h.assertTrue(vale.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(64)).size()==before,
                    "Disabled spawning creates no assisted wildlife in an existing Vale world");
            h.assertTrue(existingAnimal.isAlive() && protectedPufik.isAlive()
                            && protectedPufik.isPersistenceRequired()
                            && protectedPufik.getCustomName().getString().equals("Saved friend"),
                    "Existing animals and named persistent wildlife remain unchanged");
            h.assertTrue(!ValeWildlifePopulation.mayAssist(h.getLevel()),"Other dimensions never use Vale population assistance");
            overworldPlayer=ValeFireflyPopulationTests.observer(h.getLevel());
            overworldPlayer.moveTo(8,64,8,0,0);
            int overworldBefore=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,overworldPlayer.getBoundingBox().inflate(64)).size();
            ValeWildlifePopulation.assistNear(h.getLevel(),overworldPlayer,h.getLevel().random);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,overworldPlayer.getBoundingBox().inflate(64)).size()==overworldBefore,
                    "An Overworld tick cannot add Vale wildlife");
            overworldPlayer.discard();
        } finally {
            oldRule.set(oldSpawning,h.getLevel().getServer());
            player.discard();
            second.discard();
            existingAnimal.discard();
            protectedPufik.discard();
            if(overworldPlayer!=null)overworldPlayer.discard();
        }
        h.succeed();
    }
}
