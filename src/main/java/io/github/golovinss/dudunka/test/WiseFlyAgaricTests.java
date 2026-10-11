package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class WiseFlyAgaricTests {
    private static Runnable socialCleanup=()->{};
    private static Runnable groundCleanup=()->{};
    @AfterBatch(batch="isolated_wise_movement") public static void cleanupGround(net.minecraft.server.level.ServerLevel level) {
        var action=groundCleanup;groundCleanup=()->{};action.run();
    }
    @GameTest(template="empty",timeoutTicks=850,batch="isolated_wise_movement")
    public static void safeActiveMovement(GameTestHelper h) {
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(3,4,3));
        var saved=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(center.offset(-5,-2,-5),center.offset(5,2,5))) {
            saved.put(p.immutable(),level.getBlockState(p));
            int edge=Math.max(Math.abs(p.getX()-center.getX()),Math.abs(p.getZ()-center.getZ()));
            level.setBlock(p,p.getY()<center.getY() && edge<5?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        // Half the island edge has water; the other half is a two-block cliff.
        for(int x=-5;x<=5;x++)level.setBlock(center.offset(x,-1,-5),Blocks.WATER.defaultBlockState(),18);
        var elder=DudunkaMod.WISE_FLY_AGARIC.get().create(level);elder.moveTo(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);
        elder.setPersistenceRequired();elder.getRandom().setSeed(340);level.addFreshEntity(elder);
        groundCleanup=()->{elder.discard();saved.forEach((p,state)->level.setBlock(p,state,18));};
        double[] distance={0};var start=elder.position();
        h.onEachTick(()->{
            h.assertTrue(!elder.isInWaterOrBubble() && !elder.isOnFire() && elder.getY()>=center.getY()-.1,"Active AI remains on safe dry island");
            distance[0]=Math.max(distance[0],elder.position().distanceToSqr(start));
        });
        h.runAfterDelay(800,()->{
            h.assertTrue(distance[0]>.1,"Stroll goal actually moves, rather than only rejecting destinations");
            var action=groundCleanup;groundCleanup=()->{};action.run();h.succeed();
        });
    }
    @AfterBatch(batch="isolated_wise_social") public static void cleanupSocial(net.minecraft.server.level.ServerLevel level) {
        var action=socialCleanup;socialCleanup=()->{};action.run();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void registrationAndAssetReferences(GameTestHelper h) {
        var type=DudunkaMod.WISE_FLY_AGARIC.get(); var elder=type.create(h.getLevel());
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals("dudunka:wise_fly_agaric")
                && type.getCategory()==MobCategory.CREATURE && type.getWidth()==.72f && type.getHeight()==1.15f,"Independent small creature registry");
        h.assertTrue(SpawnPlacements.getPlacementType(type)==SpawnPlacements.Type.ON_GROUND
                && elder.getAttributeValue(Attributes.MAX_HEALTH)==12,"Attributes and ground placement registered");
        h.assertTrue(Kind.values().length==3 && DudunkaMod.TYPES.size()==3
                && !DudunkaMod.TYPES.containsValue(DudunkaMod.WISE_FLY_AGARIC),"Family identities remain separate");
        // Dedicated-server safe: actual baking/renderer wiring is also checked by verifyWiseFlyAgaric.
        h.assertTrue(h.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("dudunka",
                "structures/empty.nbt")).isPresent(),"Mod resources available");
        try (var texture=WiseFlyAgaricTests.class.getResourceAsStream("/assets/dudunka/textures/entity/wise_fly_agaric.png")) {
            h.assertTrue(texture!=null && java.util.Arrays.equals(texture.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Original texture reference resolves to PNG");
        } catch(java.io.IOException e) { throw new RuntimeException(e); }
        elder.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_wise_ground")
    public static void habitatAndSafeSpawnRestrictions(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        for(String biome:List.of("memory_forest","glowing_mushroom_forest","pink_meadows")) {
            var key=ResourceLocation.fromNamespaceAndPath("dudunka",biome);
            var entries=level.registryAccess().registryOrThrow(Registries.BIOME).get(key).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
            int weight=biome.equals("memory_forest")?10:0;
            h.assertTrue(entries.stream().filter(e->e.type==DudunkaMod.WISE_FLY_AGARIC.get()).allMatch(e->e.getWeight().asInt()==weight && e.minCount==1 && e.maxCount==1)
                    && entries.stream().anyMatch(e->e.type==DudunkaMod.WISE_FLY_AGARIC.get())==(weight>0),"Additive habitat weights and single group: "+biome);
            h.assertTrue(entries.stream().noneMatch(e ->
                    "minecraft".equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                            .getKey(e.type).getNamespace())),
                    "Vanilla passive creatures do not naturally spawn in Vale");
            BlockPos site=null;
            search:for(int radius=0;radius<=4096;radius+=64)for(int x=-radius;x<=radius;x+=64)for(int z=-radius;z<=radius;z+=64) {
                if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
                var p=new BlockPos(x,160,z);
                if(level.getBiome(p).is(key)){site=p;break search;}
            }
            h.assertTrue(site!=null,"Requested biome found");level.getChunk(site.getX()>>4,site.getZ()>>4);
            var saved=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            try {
                for(var p:BlockPos.betweenClosed(site.offset(-1,-2,-1),site.offset(1,1,1))) {
                    saved.put(p.immutable(),level.getBlockState(p));
                    level.setBlock(p,p.getY()<site.getY()?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
                }
                var type=DudunkaMod.WISE_FLY_AGARIC.get();
                h.assertTrue(SpawnPlacements.checkSpawnRules(type,level,MobSpawnType.NATURAL,site,level.random)==(weight>0),"Actual registered predicate enforces habitat");
                if(weight==0)continue;
                for(var hazard:List.of(Blocks.WATER,Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.POWDER_SNOW,Blocks.CACTUS,Blocks.OAK_LEAVES)) {
                    level.setBlock(site.below(),hazard.defaultBlockState(),18);
                    h.assertTrue(!WiseFlyAgaric.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe floor refused: "+hazard);
                }
                level.setBlock(site.below(),Blocks.MYCELIUM.defaultBlockState(),18);
                for(var obstruction:List.of(Blocks.STONE,Blocks.WATER,Blocks.FIRE,Blocks.SWEET_BERRY_BUSH)) {
                    level.setBlock(site,obstruction.defaultBlockState(),18);
                    h.assertTrue(!WiseFlyAgaric.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe body refused");
                }
                level.setBlock(site,Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.above(),Blocks.STONE.defaultBlockState(),18);
                h.assertTrue(!WiseFlyAgaric.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Head clearance required");
                level.setBlock(site.above(),Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.east().below(),Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.east().below(2),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!WiseFlyAgaric.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Cliffs refused");
            } finally {saved.forEach((p,s)->level.setBlock(p,s,18));}
        }
        h.assertTrue(!WiseFlyAgaric.canSpawn(DudunkaMod.WISE_FLY_AGARIC.get(),h.getLevel(),MobSpawnType.NATURAL,h.absolutePos(new BlockPos(2,2,2)),h.getLevel().random),"Other dimensions refused");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void greetingAndVanillaSaveReload(GameTestHelper h) {
        var level=h.getLevel();var elder=DudunkaMod.WISE_FLY_AGARIC.get().create(level);
        final Component[] message={null}; final int[] messages={0};
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WiseGreeting")) {
            @Override public void displayClientMessage(Component text,boolean overlay) {message[0]=text;messages[0]++;}
        };
        h.assertTrue(elder.interact(player,InteractionHand.MAIN_HAND).consumesAction() && elder.emote()==WiseFlyAgaric.GREETING
                && message[0]!=null && message[0].getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().matches("message\\.dudunka\\.wise_fly_agaric\\.[0-5]"),"Right click sends localized friendly greeting and waves");
        elder.interact(player,InteractionHand.MAIN_HAND);
        h.assertTrue(messages[0]==1,"Server cooldown suppresses repeated dialogue");
        elder.setCustomName(Component.literal("Little friend"));elder.setHealth(4);
        var tag=elder.saveWithoutId(new CompoundTag());var restored=DudunkaMod.WISE_FLY_AGARIC.get().create(level);restored.load(tag);
        h.assertTrue(restored.getUUID().equals(elder.getUUID()) && restored.getHealth()==4 && restored.getCustomName().equals(elder.getCustomName())
                && restored.emote()==0,"Vanilla identity/name/health survive reload; transient emote resets");
        for(String key:List.of("FamilyOwner","Trust","GrowthTicks"))h.assertTrue(!tag.contains(key),"No family progression tags");
        elder.discard();restored.discard();player.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,batch="isolated_wise_social")
    public static void peacefulEvenWhenSurprised(GameTestHelper h) {
        var level=h.getLevel();var elder=h.spawn(DudunkaMod.WISE_FLY_AGARIC.get(),new BlockPos(2,2,2));
        var player=ValeFireflyPopulationTests.observer(level);player.moveTo(elder.getX()+1,elder.getY(),elder.getZ(),0,0);
        player.setSprinting(false);level.addNewPlayer(player);
        var original=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var feet=elder.blockPosition();
        for(var p:BlockPos.betweenClosed(feet.offset(-3,-1,-3),feet.offset(3,3,3))) {
            original.put(p.immutable(),level.getBlockState(p));
            level.setBlock(p,p.getY()==feet.getY()-1 || p.getY()==feet.getY()+3?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        socialCleanup=()->{player.discard();elder.discard();original.forEach((p,state)->level.setBlock(p,state,18));};
        float health=player.getHealth();elder.setTarget(player);
        h.assertTrue(elder.getTarget()==null && !elder.doHurtTarget(player) && elder.getAttribute(Attributes.ATTACK_DAMAGE)==null,"No targets, attacks or attack attribute");
        h.assertTrue(!elder.canTrample(Blocks.FARMLAND.defaultBlockState(),elder.blockPosition().below(),10),"Never tramples cultivated blocks");
        var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(elder.blockPosition().offset(-3,-1,-3),elder.blockPosition().offset(3,3,3)))blocks.put(p.immutable(),level.getBlockState(p));
        elder.interact(player,InteractionHand.MAIN_HAND);
        h.runAfterDelay(15,()->h.assertTrue(elder.emote()==WiseFlyAgaric.GREETING,"Interaction greets without attacking"));
        h.runAfterDelay(75,()->{
            h.assertTrue(player.getHealth()==health && elder.getTarget()==null && elder.emoteStrength(0)==0,"Surprise subsides without harming player");
            h.assertTrue(blocks.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue())),"No webs or changed blocks: "+blocks.entrySet().stream().filter(e->!level.getBlockState(e.getKey()).equals(e.getValue())).limit(8).map(e->e.getKey()+" expected="+e.getValue()+" actual="+level.getBlockState(e.getKey())).toList());
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100,batch="isolated_wise_cap")
    public static void sharedCreatureCapAndOrdinaryDespawn(GameTestHelper h) throws Exception {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);var pos=new BlockPos(2048,160,2048);var chunk=level.getChunk(128,128);
        var mobs=new ArrayList<Entity>();
        for(int i=0;i<10;i++){var mob=i<2?DudunkaMod.WISE_FLY_AGARIC.get().create(level):i<4?DudunkaMod.PUFIK.get().create(level):EntityType.MOOSHROOM.create(level);mob.moveTo(pos.getX(),160,pos.getZ(),0,0);mobs.add(mob);}
        var state=NaturalSpawner.createState(289,mobs,(key,consumer)->consumer.accept(chunk),new LocalMobCapCalculator(level.getChunkSource().chunkMap));
        var canSpawn=state.getClass().getDeclaredMethod("canSpawnForCategory",MobCategory.class,ChunkPos.class);canSpawn.setAccessible(true);
        h.assertTrue(MobCategory.CREATURE.getMaxInstancesPerChunk()==10 && !(boolean)canSpawn.invoke(state,MobCategory.CREATURE,chunk.getPos()),"Elders, Pufiks and animals share unchanged cap");
        var player=ValeFireflyPopulationTests.observer(level);player.moveTo(pos.getX(),160,pos.getZ(),0,0);level.addNewPlayer(player);
        var ordinary=DudunkaMod.WISE_FLY_AGARIC.get().create(level);var named=DudunkaMod.WISE_FLY_AGARIC.get().create(level);
        var persistent=DudunkaMod.WISE_FLY_AGARIC.get().create(level);var vetoed=DudunkaMod.WISE_FLY_AGARIC.get().create(level);
        named.setCustomName(Component.literal("Friend"));persistent.setPersistenceRequired();
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.AllowDespawn> veto=e->{if(e.getEntity()==vetoed)e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            for(var mob:List.of(ordinary,named,persistent,vetoed)){mob.moveTo(player.getX()+129,160,player.getZ(),0,0);mob.checkDespawn();}
            h.assertTrue(ordinary.isRemoved() && !named.isRemoved() && !persistent.isRemoved() && !vetoed.isRemoved(),"Ordinary turnover respects names, persistence and Forge veto");
        } finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);player.discard();mobs.forEach(Entity::discard);for(var mob:List.of(ordinary,named,persistent,vetoed))mob.discard();}
        h.succeed();
    }
}
