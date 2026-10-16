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
public class ValeSpiderTests {
    private static Runnable socialCleanup=()->{};
    @AfterBatch(batch="isolated_spider_social") public static void cleanupSocial(net.minecraft.server.level.ServerLevel level) {
        var action=socialCleanup;socialCleanup=()->{};action.run();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void registrationAndAssetReferences(GameTestHelper h) {
        var type=DudunkaMod.VALE_SPIDER.get(); var spider=type.create(h.getLevel());
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals("dudunka:vale_spider")
                && type.getCategory()==MobCategory.CREATURE && type.getWidth()==.8f && type.getHeight()==.5f,"Independent small creature registry");
        h.assertTrue(SpawnPlacements.getPlacementType(type)==SpawnPlacements.Type.ON_GROUND
                && spider.getAttributeValue(Attributes.MAX_HEALTH)==6,"Attributes and ground placement registered");
        h.assertTrue(Kind.values().length==3 && DudunkaMod.TYPES.size()==3
                && !DudunkaMod.TYPES.containsValue(DudunkaMod.VALE_SPIDER),"Family identities remain separate");
        // Dedicated-server safe: actual baking/renderer wiring is also checked by verifyValeSpider.
        h.assertTrue(h.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("dudunka",
                "structures/empty.nbt")).isPresent(),"Mod resources available");
        try (var texture=ValeSpiderTests.class.getResourceAsStream("/assets/dudunka/textures/entity/vale_spider.png")) {
            h.assertTrue(texture!=null && java.util.Arrays.equals(texture.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Original texture reference resolves to PNG");
        } catch(java.io.IOException e) { throw new RuntimeException(e); }
        spider.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_spider_ground")
    public static void habitatAndSafeSpawnRestrictions(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        for(String biome:List.of("memory_forest","glowing_mushroom_forest","pink_meadows")) {
            var key=ResourceLocation.fromNamespaceAndPath("dudunka",biome);
            var entries=level.registryAccess().registryOrThrow(Registries.BIOME).get(key).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
            int weight=biome.equals("memory_forest")?44:biome.equals("glowing_mushroom_forest")?20:0;
            h.assertTrue(entries.stream().filter(e->e.type==DudunkaMod.VALE_SPIDER.get()).allMatch(e->e.getWeight().asInt()==weight && e.minCount==1 && e.maxCount==2)
                    && entries.stream().anyMatch(e->e.type==DudunkaMod.VALE_SPIDER.get())==(weight>0),"Additive habitat weights and 1–2 group: "+biome);
            h.assertTrue(entries.stream().noneMatch(e->net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.type).getNamespace().equals("minecraft")),"No vanilla passive creature competes in Vale");
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
                var type=DudunkaMod.VALE_SPIDER.get();
                h.assertTrue(SpawnPlacements.checkSpawnRules(type,level,MobSpawnType.NATURAL,site,level.random)==(weight>0),"Actual registered predicate enforces habitat");
                if(weight==0)continue;
                for(var hazard:List.of(Blocks.WATER,Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.POWDER_SNOW,Blocks.CACTUS,Blocks.OAK_LEAVES)) {
                    level.setBlock(site.below(),hazard.defaultBlockState(),18);
                    h.assertTrue(!ValeSpider.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe floor refused: "+hazard);
                }
                level.setBlock(site.below(),Blocks.MYCELIUM.defaultBlockState(),18);
                for(var obstruction:List.of(Blocks.STONE,Blocks.WATER,Blocks.FIRE,Blocks.SWEET_BERRY_BUSH)) {
                    level.setBlock(site,obstruction.defaultBlockState(),18);
                    h.assertTrue(!ValeSpider.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe body refused");
                }
                level.setBlock(site,Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.above(),Blocks.STONE.defaultBlockState(),18);
                h.assertTrue(!ValeSpider.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Head clearance required");
                level.setBlock(site.above(),Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.east().below(),Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.east().below(2),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!ValeSpider.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Cliffs refused");
            } finally {saved.forEach((p,s)->level.setBlock(p,s,18));}
        }
        h.assertTrue(!ValeSpider.canSpawn(DudunkaMod.VALE_SPIDER.get(),h.getLevel(),MobSpawnType.NATURAL,h.absolutePos(new BlockPos(2,2,2)),h.getLevel().random),"Other dimensions refused");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void greetingAndVanillaSaveReload(GameTestHelper h) {
        var level=h.getLevel();var spider=DudunkaMod.VALE_SPIDER.get().create(level);
        final Component[] message={null};
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SpiderGreeting")) {
            @Override public void displayClientMessage(Component text,boolean overlay) {message[0]=text;}
        };
        h.assertTrue(spider.interact(player,InteractionHand.MAIN_HAND).consumesAction() && spider.emote()==ValeSpider.GREETING
                && message[0]!=null && message[0].getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("message.dudunka.vale_spider_greeting"),"Right click sends localized friendly greeting and waves");
        spider.setCustomName(Component.literal("Little friend"));spider.setHealth(4);
        var tag=spider.saveWithoutId(new CompoundTag());var restored=DudunkaMod.VALE_SPIDER.get().create(level);restored.load(tag);
        h.assertTrue(restored.getUUID().equals(spider.getUUID()) && restored.getHealth()==4 && restored.getCustomName().equals(spider.getCustomName())
                && restored.emote()==0,"Vanilla identity/name/health survive reload; transient emote resets");
        for(String key:List.of("FamilyOwner","Trust","GrowthTicks"))h.assertTrue(!tag.contains(key),"No family progression tags");
        spider.discard();restored.discard();player.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,batch="isolated_spider_social")
    public static void peacefulEvenWhenSurprised(GameTestHelper h) {
        var level=h.getLevel();
        // The 6x5x6 air template does not contain the old seven-block-wide snapshot.
        // Use a dry controlled volume so outside terrain/fluids and harness markers cannot change it.
        var origin=h.absolutePos(new BlockPos(2,2,2));var site=new BlockPos(origin.getX(),160,origin.getZ());
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var pos:BlockPos.betweenClosed(site.offset(-4,-1,-4),site.offset(4,3,4))) {
            saved.put(pos.immutable(),level.getBlockState(pos));
            level.setBlock(pos,pos.getY()==159?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        var spider=DudunkaMod.VALE_SPIDER.get().create(level);
        spider.moveTo(site.getX()+.5,site.getY(),site.getZ()+.5,0,0);level.addFreshEntity(spider);
        var player=ValeFireflyPopulationTests.observer(level);player.moveTo(spider.getX()+1,spider.getY(),spider.getZ(),0,0);
        player.setSprinting(true);level.addNewPlayer(player);
        socialCleanup=()->{player.discard();spider.discard();saved.forEach((pos,state)->level.setBlock(pos,state,18));};
        float health=player.getHealth();spider.setTarget(player);
        h.assertTrue(spider.getTarget()==null && !spider.doHurtTarget(player) && spider.getAttribute(Attributes.ATTACK_DAMAGE)==null,"No targets, attacks or attack attribute");
        h.assertTrue(!spider.canTrample(Blocks.FARMLAND.defaultBlockState(),spider.blockPosition().below(),10),"Never tramples cultivated blocks");
        var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(spider.blockPosition().offset(-3,-1,-3),spider.blockPosition().offset(3,2,3)))blocks.put(p.immutable(),level.getBlockState(p));
        h.runAfterDelay(15,()->h.assertTrue(spider.emote()==ValeSpider.SHY,"Close sprinting player triggers brief shy pose"));
        h.runAfterDelay(75,()->{
            h.assertTrue(player.getHealth()==health && spider.getTarget()==null && spider.emoteStrength(0)==0,"Surprise subsides without harming player");
            h.assertTrue(blocks.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue())),"No webs or changed blocks");
            cleanupSocial(level);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100,batch="isolated_spider_cap")
    public static void sharedCreatureCapAndOrdinaryDespawn(GameTestHelper h) throws Exception {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);var pos=new BlockPos(2048,160,2048);var chunk=level.getChunk(128,128);
        var mobs=new ArrayList<Entity>();
        for(int i=0;i<10;i++){var mob=i<2?DudunkaMod.VALE_SPIDER.get().create(level):i<4?DudunkaMod.PUFIK.get().create(level):EntityType.MOOSHROOM.create(level);mob.moveTo(pos.getX(),160,pos.getZ(),0,0);mobs.add(mob);}
        var state=NaturalSpawner.createState(289,mobs,(key,consumer)->consumer.accept(chunk),new LocalMobCapCalculator(level.getChunkSource().chunkMap));
        var canSpawn=state.getClass().getDeclaredMethod("canSpawnForCategory",MobCategory.class,ChunkPos.class);canSpawn.setAccessible(true);
        h.assertTrue(MobCategory.CREATURE.getMaxInstancesPerChunk()==10 && !(boolean)canSpawn.invoke(state,MobCategory.CREATURE,chunk.getPos()),"Spiders, Pufiks and animals share unchanged cap");
        var player=ValeFireflyPopulationTests.observer(level);player.moveTo(pos.getX(),160,pos.getZ(),0,0);level.addNewPlayer(player);
        var ordinary=DudunkaMod.VALE_SPIDER.get().create(level);var named=DudunkaMod.VALE_SPIDER.get().create(level);
        var persistent=DudunkaMod.VALE_SPIDER.get().create(level);var vetoed=DudunkaMod.VALE_SPIDER.get().create(level);
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
