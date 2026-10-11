package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class FluffyJumperTests {
    private static final Map<String, Runnable> CLEANUP = new HashMap<>();
    private static void clean(String name) { var action=CLEANUP.remove(name); if(action!=null)action.run(); }
    private static FluffyJumper island(GameTestHelper h,String name) {
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(4,5,4));
        var saved=new HashMap<BlockPos,BlockState>();
        for(var p:BlockPos.betweenClosed(center.offset(-7,-3,-7),center.offset(7,4,7))) {
            saved.put(p.immutable(),level.getBlockState(p));
            int edge=Math.max(Math.abs(p.getX()-center.getX()),Math.abs(p.getZ()-center.getZ()));
            level.setBlock(p,p.getY()<center.getY() && edge<6?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        }
        for(int x=-6;x<=6;x++)level.setBlock(center.offset(x,-1,-6),Blocks.WATER.defaultBlockState(),18);
        for(int z=-5;z<=5;z++)level.setBlock(center.offset(6,-1,z),Blocks.MAGMA_BLOCK.defaultBlockState(),18);
        var jumper=DudunkaMod.FLUFFY_JUMPER.get().create(level);
        jumper.moveTo(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);jumper.setPersistenceRequired();
        jumper.getRandom().setSeed(350);level.addFreshEntity(jumper);
        CLEANUP.put(name,()->{jumper.discard();saved.forEach((p,state)->level.setBlock(p,state,18));});
        return jumper;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void registrationAttributesResourcesAndIndependence(GameTestHelper h) throws Exception {
        var type=DudunkaMod.FLUFFY_JUMPER.get();var jumper=type.create(h.getLevel());
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals("dudunka:fluffy_jumper")
                && type.getCategory()==MobCategory.CREATURE && type.getWidth()==.5f && type.getHeight()==.5f,"Stable independent small creature registration");
        h.assertTrue(SpawnPlacements.getPlacementType(type)==SpawnPlacements.Type.ON_GROUND
                && jumper.getMaxHealth()==6 && jumper.getAttributeValue(Attributes.MOVEMENT_SPEED)==.12
                && jumper.getAttribute(Attributes.ATTACK_DAMAGE)==null,"Registered peaceful attributes and placement");
        h.assertTrue(Kind.values().length==3 && DudunkaMod.TYPES.size()==3
                && !DudunkaMod.TYPES.containsValue(DudunkaMod.FLUFFY_JUMPER),"No family companion registration");
        for(int i=0;i<3;i++)try(var in=FluffyJumperTests.class.getResourceAsStream("/assets/dudunka/textures/entity/fluffy_jumper_"+i+".png")) {
            h.assertTrue(in!=null && Arrays.equals(in.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Original variant PNG available on dedicated server");
        }
        jumper.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180,batch="isolated_fluffy_spawn")
    public static void habitatPopulationAndUnsafeSurfaces(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);var type=DudunkaMod.FLUFFY_JUMPER.get();
        var generator=level.getChunkSource().getGenerator();
        for(String biome:List.of("pink_meadows","glowing_mushroom_forest","memory_forest")) {
            var key=ResourceLocation.fromNamespaceAndPath("dudunka",biome);
            var entries=level.registryAccess().registryOrThrow(Registries.BIOME).get(key).getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
            h.assertTrue(entries.stream().filter(e->e.type==type).count()==(biome.equals("pink_meadows")?1:0),"Only Pink Meadows adds this creature");
            if(biome.equals("pink_meadows"))h.assertTrue(entries.stream().anyMatch(e->e.type==type && e.getWeight().asInt()==24 && e.minCount==2 && e.maxCount==3),"Regular weight, normal creature cap, playful 2–3 group");
            h.assertTrue(entries.stream().noneMatch(e->net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.type).getNamespace().equals("minecraft")),"No vanilla passive creature competes in Vale");
            BlockPos site=null;
            search:for(int r=0;r<=4096;r+=64)for(int x=-r;x<=r;x+=64)for(int z=-r;z<=r;z+=64) {
                if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
                if(generator.getBiomeSource().getNoiseBiome(x>>2,40,z>>2,level.getChunkSource().randomState().sampler()).is(key)) {
                    site=new BlockPos(x,160,z);break search;
                }
            }
            h.assertTrue(site!=null,"Biome found from noise without generating a survey");
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((site.getX()>>4)+dx,(site.getZ()>>4)+dz);
            var saved=new HashMap<BlockPos,BlockState>();
            try {
                for(var p:BlockPos.betweenClosed(site.offset(-2,-2,-2),site.offset(2,3,2))) {
                    saved.put(p.immutable(),level.getBlockState(p));level.setBlock(p,p.getY()<160?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
                }
                h.assertTrue(SpawnPlacements.checkSpawnRules(type,level,MobSpawnType.NATURAL,site,level.random)==biome.equals("pink_meadows"),"Actual registered habitat predicate");
                if(!biome.equals("pink_meadows"))continue;
                for(var floor:List.of(Blocks.WATER,Blocks.LAVA,Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.CACTUS,Blocks.OAK_LEAVES,Blocks.BIRCH_SLAB,Blocks.STONE)) {
                    level.setBlock(site.below(),floor.defaultBlockState(),18);
                    h.assertTrue(!FluffyJumper.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe/nonsoil floor refused: "+floor);
                }
                level.setBlock(site.below(),Blocks.GRASS_BLOCK.defaultBlockState(),18);
                for(var body:List.of(Blocks.STONE,Blocks.WATER,Blocks.LAVA,Blocks.FIRE,Blocks.WITHER_ROSE,Blocks.POWDER_SNOW)) {
                    level.setBlock(site,body.defaultBlockState(),18);
                    h.assertTrue(!FluffyJumper.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Unsafe body refused: "+body);
                }
                level.setBlock(site,Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.above(),Blocks.STONE.defaultBlockState(),18);
                h.assertTrue(!FluffyJumper.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Headroom required");
                level.setBlock(site.above(),Blocks.AIR.defaultBlockState(),18);
                level.setBlock(site.east().below(),Blocks.AIR.defaultBlockState(),18);
                h.assertTrue(!FluffyJumper.canSpawn(type,level,MobSpawnType.NATURAL,site,level.random),"Even one-step ledge refused");
            } finally {saved.forEach((p,state)->level.setBlock(p,state,18));}
        }
        h.assertTrue(!FluffyJumper.canSpawn(type,h.getLevel(),MobSpawnType.NATURAL,h.absolutePos(new BlockPos(2,2,2)),h.getLevel().random),"Overworld natural spawning refused");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void greetingCooldownSaveReloadAndNaturalDespawn(GameTestHelper h) {
        var level=h.getLevel();var jumper=DudunkaMod.FLUFFY_JUMPER.get().create(level);
        final Component[] message={null};final int[] count={0};
        var player=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FluffyGreeting")) {
            @Override public void displayClientMessage(Component text,boolean overlay) {message[0]=text;count[0]++;}
        };
        var item=player.getMainHandItem().copy();
        h.assertTrue(jumper.interact(player,InteractionHand.OFF_HAND).consumesAction() && count[0]==0,"Offhand has no duplicate server effect");
        h.assertTrue(jumper.interact(player,InteractionHand.MAIN_HAND).consumesAction() && message[0]!=null
                && message[0].getContents() instanceof TranslatableContents text && text.getKey().matches("message\\.dudunka\\.fluffy_jumper\\.[0-2]"),"Localized friendly right click");
        jumper.interact(player,InteractionHand.MAIN_HAND);
        h.assertTrue(count[0]==1 && jumper.greetingStrength(10)>.8 && net.minecraft.world.item.ItemStack.matches(item,player.getMainHandItem()),"Cheerful animation, server cooldown, no item consumption");
        h.assertTrue(!jumper.isPersistenceRequired() && jumper.removeWhenFarAway(100000) && jumper.getMaxSpawnClusterSize()==4,"Wildlife uses normal natural despawn and cluster limits");
        jumper.setHealth(4);jumper.setCustomName(Component.literal("Fluff"));
        var saved=jumper.saveWithoutId(new CompoundTag());saved.putInt("FluffyColor",2);
        var restored=DudunkaMod.FLUFFY_JUMPER.get().create(level);restored.load(saved);
        h.assertTrue(restored.getUUID().equals(jumper.getUUID()) && restored.getHealth()==4 && restored.getCustomName().equals(jumper.getCustomName())
                && restored.colorVariant()==2 && restored.greetingStrength(10)==0 && !restored.removeWhenFarAway(100000),"Vanilla identity/health/name and color reload; transient animation resets");
        saved.putInt("FluffyColor",999);restored.load(saved);h.assertTrue(restored.colorVariant()==2,"Corrupt palette value is bounded");
        for(String key:List.of("FamilyOwner","Trust","GrowthTicks","PufikCottageOrigin"))h.assertTrue(!saved.contains(key),"No family or residency tags");
        restored.load(new CompoundTag());h.assertTrue(restored.colorVariant()==0,"Missing additive data has a safe default");
        jumper.discard();restored.discard();player.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=700,batch="isolated_fluffy_physics")
    public static void safeHoppingLandingRestAndNoTeleportation(GameTestHelper h) {
        var jumper=island(h,"physics");double y=jumper.getY();var start=jumper.position();var last=new Vec3[]{start};
        int[] hops={0},landings={0},rests={0};boolean[] airborne={false};double[] maxHeight={0},distance={0};
        h.onEachTick(()->{
            h.assertTrue(jumper.isAlive() && jumper.getHealth()==6 && !jumper.isInWaterOrBubble() && !jumper.isOnFire()
                    && jumper.getY()>=y-.01,"AI stays on dry supported terrain without damage: pos="+jumper.position()+" health="+jumper.getHealth()+" alive="+jumper.isAlive()+" water="+jumper.isInWaterOrBubble()+" fire="+jumper.isOnFire()+" floor="+h.getLevel().getBlockState(jumper.blockPosition().below()));
            var motion=jumper.position().subtract(last[0]);last[0]=jumper.position();
            h.assertTrue(motion.horizontalDistance()<.2 && Math.abs(motion.y)<.5,"Every tick follows bounded physics without teleportation");
            h.assertTrue(h.getLevel().noCollision(jumper,jumper.getBoundingBox().deflate(.001)),"No suffocation or clipping");
            if(!jumper.onGround() && !airborne[0])hops[0]++;
            if(jumper.onGround() && airborne[0])landings[0]++;
            if(jumper.onGround() && motion.horizontalDistance()<.005)rests[0]++;
            airborne[0]=!jumper.onGround();maxHeight[0]=Math.max(maxHeight[0],jumper.getY()-y);distance[0]=Math.max(distance[0],jumper.position().distanceToSqr(start));
        });
        h.runAfterDelay(650,()->{
            h.assertTrue(hops[0]>=5 && landings[0]>=5 && rests[0]>=50 && maxHeight[0]>.3 && maxHeight[0]<1.5 && distance[0]>.2,
                    "Real repeated hops, safe landings, rests and displacement: hops="+hops[0]+" landings="+landings[0]+" rest="+rests[0]+" height="+maxHeight[0]+" distance="+distance[0]);
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160,batch="isolated_fluffy_blocked")
    public static void refusesUnsafeFlightCorridor(GameTestHelper h) {
        var jumper=island(h,"blocked");var level=h.getLevel();var feet=jumper.blockPosition();
        h.runAfterDelay(4,()->{
            var impulse=new Vec3(.1,.42,0);
            h.assertTrue(jumper.safeHop(impulse),"Open supported excited hop is available");
            var ceiling=feet.above(1);var state=level.getBlockState(ceiling);level.setBlock(ceiling,Blocks.STONE.defaultBlockState(),18);
            h.assertTrue(!jumper.safeHop(impulse),"Swept overhead collision refuses an excited hop");level.setBlock(ceiling,state,18);
            for(var hazard:List.of(Blocks.WATER,Blocks.MAGMA_BLOCK,Blocks.FIRE)) {
                var pos=hazard==Blocks.MAGMA_BLOCK?feet.east().below():feet.east();var old=level.getBlockState(pos);
                level.setBlock(pos,hazard.defaultBlockState(),18);h.assertTrue(!jumper.safeHop(impulse),"Unsafe corridor refused: "+hazard);level.setBlock(pos,old,18);
            }
            var floor=feet.east().below();var old=level.getBlockState(floor);level.setBlock(floor,Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(!jumper.safeHop(impulse),"Cliff within corridor refused");level.setBlock(floor,old,18);
            h.assertTrue(!jumper.safeHop(new Vec3(2,.42,0)),"Unbounded impulse refused");clean("blocked");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=240,batch="isolated_fluffy_multiplayer")
    public static void peacefulMultiplayerProximityAndLooseGroupJumps(GameTestHelper h) {
        var jumper=island(h,"multiplayer");var level=h.getLevel();var center=jumper.position();
        var friend=DudunkaMod.FLUFFY_JUMPER.get().create(level);friend.moveTo(center.x+1,center.y,center.z+1,0,0);friend.setPersistenceRequired();friend.getRandom().setSeed(351);level.addFreshEntity(friend);
        var first=ValeFireflyPopulationTests.observer(level);var second=ValeFireflyPopulationTests.observer(level);
        first.moveTo(center.x+2,center.y,center.z,0,0);second.moveTo(center.x-2,center.y,center.z,0,0);level.addNewPlayer(first);level.addNewPlayer(second);
        var bystander=EntityType.CHICKEN.create(level);bystander.moveTo(center.x-1,center.y,center.z+1,0,0);bystander.setNoAi(true);level.addFreshEntity(bystander);
        var prior=CLEANUP.get("multiplayer");CLEANUP.put("multiplayer",()->{bystander.discard();friend.discard();first.discard();second.discard();prior.run();});
        first.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        first.setInvulnerable(false);second.setInvulnerable(false);float health1=first.getHealth(),health2=second.getHealth(),bystanderHealth=bystander.getHealth();
        jumper.setTarget(first);friend.setTarget(second);
        h.assertTrue(jumper.getTarget()==null && friend.getTarget()==null && !jumper.doHurtTarget(first) && !friend.doHurtTarget(second)
                && !jumper.canTrample(Blocks.FARMLAND.defaultBlockState(),jumper.blockPosition().below(),10),"No attacks, retaliation or farmland damage");
        h.assertTrue(!jumper.doHurtTarget(bystander),"Other creatures cannot be attacked");
        h.assertTrue(jumper.hurt(level.damageSources().playerAttack(first),1),"Real player damage reaches the wildlife");
        jumper.interact(first,InteractionHand.MAIN_HAND);jumper.interact(second,InteractionHand.MAIN_HAND);
        h.assertTrue(!jumper.isPassenger() && first.getPassengers().isEmpty() && second.getPassengers().isEmpty(),"No mounting or player ownership");
        int[] together={0};double[] peak={0};
        h.onEachTick(()->{
            h.assertTrue(first.getHealth()==health1 && second.getHealth()==health2 && bystander.getHealth()==bystanderHealth
                    && jumper.getTarget()==null && friend.getTarget()==null,"Both vulnerable players and the bystander stay unharmed, including after a real hit");
            if(!jumper.onGround() && !friend.onGround())together[0]++;
            peak[0]=Math.max(peak[0],jumper.getY()-center.y);
        });
        h.runAfterDelay(200,()->{
            h.assertTrue(together[0]>0 && peak[0]>.9,"Nearby wildlife sometimes overlaps hops and performs higher excited jumps");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=80,batch="isolated_fluffy_noai")
    public static void disabledAiAndActualNaturalDespawn(GameTestHelper h) {
        var jumper=island(h,"noai");jumper.setNoAi(true);var start=jumper.position();
        h.runAfterDelay(40,()->{
            h.assertTrue(jumper.position().distanceToSqr(start)<.001,"Disabled AI does not launch impulses");
            // Exercise vanilla checkDespawn with a real registered distant non-spectator player.
            var player=ValeFireflyPopulationTests.observer(h.getLevel());player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.moveTo(start.x+256,start.y,start.z,0,0);h.getLevel().addNewPlayer(player);
            var wild=DudunkaMod.FLUFFY_JUMPER.get().create(h.getLevel());wild.moveTo(start.x,start.y,start.z,0,0);h.getLevel().addFreshEntity(wild);
            try {wild.checkDespawn();h.assertTrue(wild.isRemoved(),"Unnamed wild entity actually despawns beyond the creature distance");}
            finally {wild.discard();player.discard();clean("noai");}
            h.succeed();
        });
    }
    @AfterBatch(batch="isolated_fluffy_physics") public static void cleanupPhysics(ServerLevel level) {clean("physics");}
    @AfterBatch(batch="isolated_fluffy_blocked") public static void cleanupBlocked(ServerLevel level) {clean("blocked");}
    @AfterBatch(batch="isolated_fluffy_multiplayer") public static void cleanupMultiplayer(ServerLevel level) {clean("multiplayer");}
    @AfterBatch(batch="isolated_fluffy_noai") public static void cleanupNoAi(ServerLevel level) {clean("noai");}
}
