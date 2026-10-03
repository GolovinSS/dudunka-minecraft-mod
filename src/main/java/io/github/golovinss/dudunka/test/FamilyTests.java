package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class FamilyTests {
    @GameTest(template="empty",timeoutTicks=80)
    public static void syusyaHatchesOnlyWhenReady(GameTestHelper h) {
        var l=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(2,2,2));
        l.setBlock(p.below(),Blocks.MOSS_BLOCK.defaultBlockState(),3);
        l.setBlock(p,DudunkaMod.EGGS.get(Kind.SYUSYA).get().defaultBlockState(),3);
        var egg=(EggEntity)l.getBlockEntity(p);
        var state=egg.saveWithoutMetadata();state.putInt("Offerings",3);state.putInt("Progress",DudunkaMod.HATCH_SECONDS.get()*20-20);state.putUUID("Owner",UUID.randomUUID());egg.load(state);
        h.runAfterDelay(25,()->{
            h.assertTrue(l.getBlockState(p).getBlock() instanceof EggBlock,"No water must pause hatching");
            var saved=egg.saveWithoutMetadata();h.assertTrue(saved.getInt("Progress")==state.getInt("Progress"),"Paused timer must retain progress");
            l.setBlock(p.east(),Blocks.WATER.defaultBlockState(),3);
        });
        h.runAfterDelay(55,()->{
            h.assertTrue(!(l.getBlockState(p).getBlock() instanceof EggBlock),"Ready egg should hatch");
            var mobs=l.getEntitiesOfClass(Companion.class,new net.minecraft.world.phys.AABB(p).inflate(2));
            h.assertTrue(mobs.size()==1,"Exactly one companion should hatch");
            h.assertTrue(mobs.get(0).kind==Kind.SYUSYA&&mobs.get(0).stage()==0,"Hatched companion must be baby Syusya");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void persistenceAndLethalRecovery(GameTestHelper h) {
        var l=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(2,2,2));
        l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
        var m=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(l);UUID owner=UUID.randomUUID();
        m.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);m.initialize(owner,p);
        CompoundTag t=new CompoundTag();m.addAdditionalSaveData(t);t.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*20+1);t.putInt("Trust",35);t.putBoolean("Staying",true);m.readAdditionalSaveData(t);
        CompoundTag saved=new CompoundTag();m.addAdditionalSaveData(saved);
        h.assertTrue(owner.equals(saved.getUUID("FamilyOwner")),"Owner must survive serialization");
        h.assertTrue(saved.getInt("Trust")==35&&m.staying()&&m.stage()==1,"Trust, waiting and growth must survive serialization");
        l.addFreshEntity(m);m.setHealth(1);m.hurt(l.damageSources().generic(),10);
        h.assertTrue(m.isAlive()&&m.getHealth()==m.getMaxHealth(),"Lethal ordinary damage must not kill family member");h.succeed();
    }
    private static Companion create(GameTestHelper h, Kind kind, UUID owner, BlockPos relative) {
        var mob = DudunkaMod.TYPES.get(kind).get().create(h.getLevel());
        var pos = h.absolutePos(relative);
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mob.initialize(owner, pos);
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
    private static Activity decide(Companion mob) {
        var goal = new FamilyBehaviorGoal(mob);
        if (!goal.canUse()) return Activity.IDLE;
        goal.start();
        Activity result = mob.activity();
        goal.stop();
        return result;
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void waitingRespectsFamilyAndStay(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        var dudunka = create(h, Kind.DUDUNKA, owner, new BlockPos(0,2,2));
        var snail = create(h, Kind.SYUSYA, owner, new BlockPos(5,2,2));
        h.assertTrue(decide(dudunka) == Activity.WAIT_FOR_SYUSYA, "Dudunka must wait for her own snail");
        snail.discard();
        create(h, Kind.SYUSYA, UUID.randomUUID(), new BlockPos(5,2,2));
        h.assertTrue(decide(dudunka) != Activity.WAIT_FOR_SYUSYA, "Another player's snail must not stop the family");
        var saved = new CompoundTag(); dudunka.addAdditionalSaveData(saved); saved.putBoolean("Staying", true); dudunka.readAdditionalSaveData(saved);
        h.assertTrue(!new FamilyBehaviorGoal(dudunka).canUse() && dudunka.activity() == Activity.SIT, "Explicit stay must override autonomous activities");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void appleConsumptionIsBounded(GameTestHelper h) {
        var mob = create(h, Kind.DUDUNKA, UUID.randomUUID(), new BlockPos(2,2,2));
        var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), mob.getX(), mob.getY(), mob.getZ(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 2));
        h.getLevel().addFreshEntity(item); item.setPickUpDelay(20);
        h.assertTrue(!FamilyBehaviorGoal.takeApple(mob, item) && item.getItem().getCount() == 2, "Pickup delay must be respected");
        item.setNoPickUpDelay();
        h.assertTrue(FamilyBehaviorGoal.takeApple(mob, item) && item.isAlive() && item.getItem().getCount() == 1, "Only one apple may be consumed per action");
        var cat = create(h, Kind.MARUSYA, mob.ownerId(), new BlockPos(2,2,2));
        h.assertTrue(!FamilyBehaviorGoal.takeApple(cat, item), "Marusya must not eat Dudunka's apple");
        h.assertTrue(FamilyBehaviorGoal.takeApple(mob, item) && !item.isAlive(), "Last apple must remove the item entity without duplicate drops");
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void marusyaWarnsWithoutAttacking(GameTestHelper h) {
        var cat = create(h, Kind.MARUSYA, UUID.randomUUID(), new BlockPos(2,2,2));
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        zombie.moveTo(cat.getX()+2, cat.getY(), cat.getZ(), 0, 0); zombie.setNoAi(true); h.getLevel().addFreshEntity(zombie);
        h.assertTrue(decide(cat) == Activity.ALERT, "Marusya must notice a nearby monster");
        h.assertTrue(cat.getTarget() == null && zombie.getHealth() == zombie.getMaxHealth(), "Warning must not become an attack");
        h.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer testOwner(GameTestHelper h) {
        // Ordinary ServerPlayer is needed: Forge rejects FakePlayer advancement awards.
        // Register directly in the test level with a no-op listener; there is no real network client.
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DudunkaTest"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.getLevel().addNewPlayer(player);
        return player;
    }
    @GameTest(template="empty", timeoutTicks=60)
    public static void cookieAwardsAndForeignPlayersCannotControl(GameTestHelper h) {
        var player = testOwner(h);
        var pos = h.absolutePos(new BlockPos(2,2,2));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        var mob = create(h, Kind.DUDUNKA, player.getUUID(), new BlockPos(2,2,2));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
        mob.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        var achievement = player.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID, "not_nonsense"));
        h.assertTrue(mob.trust() == 5, "Feeding must run before checking its achievement; trust=" + mob.trust());
        h.assertTrue(mob.ownerPlayer() == player, "Owner must be resolvable in the current level");
        h.assertTrue(achievement != null, "Cookie advancement resource must load");
        h.assertTrue(player.getAdvancements().getOrStartProgress(achievement).isDone(), "Last cookie in a stack must award the achievement");
        h.assertTrue(mob.trust() == 5, "Successful feeding must increase trust once");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
        mob.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(mob.trust() == 5 && player.getMainHandItem().getCount() == 1, "Cooldown must prevent extra trust and consumption");
        var stranger = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StrangerTest"));
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
        mob.interact(stranger, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(mob.trust() == 5 && stranger.getMainHandItem().getCount() == 1, "Foreign player must not feed the companion");
        create(h, Kind.MARUSYA, player.getUUID(), new BlockPos(3,2,2));
        create(h, Kind.SYUSYA, player.getUUID(), new BlockPos(4,2,2));
        FamilyAchievements.checkFamily(mob);
        var allHome = player.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID, "all_home"));
        h.assertTrue(player.getAdvancements().getOrStartProgress(allHome).isDone(), "All three owned companions must award Everyone Is Home");
        var grown = new CompoundTag(); mob.addAdditionalSaveData(grown); grown.putInt("GrowthTicks", DudunkaMod.GROWTH_SECONDS.get() * 40); mob.readAdditionalSaveData(grown);
        var adulthood = player.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID, "grown_dudunka"));
        h.assertTrue(player.getAdvancements().getOrStartProgress(adulthood).isDone(), "Reaching adult stage must award the growth achievement");
        player.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=180)
    public static void waitingAwardsAfterSixSeconds(GameTestHelper h) {
        var player = testOwner(h);
        var mob = create(h, Kind.DUDUNKA, player.getUUID(), new BlockPos(0,2,2));
        var snail = create(h, Kind.SYUSYA, player.getUUID(), new BlockPos(5,2,2));
        player.moveTo(mob.getX(), mob.getY(), mob.getZ(), 0, 0);
        mob.setNoAi(false);
        var achievement = player.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID, "wait_for_syusya"));
        h.runAfterDelay(100, () -> h.assertTrue(!player.getAdvancements().getOrStartProgress(achievement).isDone(), "Waiting achievement must not be early"));
        h.runAfterDelay(150, () -> {
            h.assertTrue(player.getAdvancements().getOrStartProgress(achievement).isDone(), "Real goal ticks must award the waiting achievement after six seconds");
            player.discard(); h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void homeConditionsBindingAndProtection(GameTestHelper h) {
        var level=h.getLevel(); var player=testOwner(h);
        BlockPos anchor=h.absolutePos(new BlockPos(2,2,2));
        for(BlockPos p:BlockPos.betweenClosed(anchor.offset(-2,-1,-2),anchor.offset(2,-1,2)))level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(anchor,DudunkaMod.HOMES.get(Kind.DUDUNKA).get().defaultBlockState(),3);
        var marker=(HomeMarkerEntity)level.getBlockEntity(anchor);marker.claim(player.getUUID());
        level.setBlock(anchor.above(3),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(anchor.east(2),Blocks.RED_BED.defaultBlockState(),3);
        level.setBlock(anchor.west(2),Blocks.CHEST.defaultBlockState(),3);
        level.setBlock(anchor.south(2),Blocks.TORCH.defaultBlockState(),3);
        var mob=create(h,Kind.DUDUNKA,player.getUUID(),new BlockPos(3,2,3));
        var foreign=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(3,2,2));
        h.assertTrue(!marker.conditions(true).ready() && marker.bindNearby(player)==0,"Missing food must prevent home binding");
        level.setBlock(anchor.north(2),Blocks.CAKE.defaultBlockState(),3);
        h.assertTrue(marker.conditions(true).ready(),"Roof, bed, chest, torch and cake must qualify");
        h.assertTrue(marker.bindNearby(player)==1,"Only the matching owned companion may bind");
        var tag=new CompoundTag();mob.addAdditionalSaveData(tag);
        h.assertTrue(tag.getBoolean("HomeMarker") && tag.getLong("FamilyHome")==anchor.asLong(),"Home anchor must persist");
        var restored=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(level);restored.readAdditionalSaveData(tag);
        h.assertTrue(restored.homePosition()!=null,"Restored home must resolve a safe standing location");
        var other=testOwner(h);
        h.assertTrue(marker.bindNearby(other)==0,"Foreign players must not rebind the home");
        var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,anchor,level.getBlockState(anchor),other);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled(),"Foreign player breaking a claimed home must be canceled");
        var allowed=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,anchor,level.getBlockState(anchor),player);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(allowed);
        h.assertTrue(!allowed.isCanceled(),"Owner must be allowed to break a home");
        BlockPos eggPos=anchor.above();level.setBlock(eggPos,DudunkaMod.EGGS.get(Kind.SYUSYA).get().defaultBlockState(),3);
        var egg=(EggEntity)level.getBlockEntity(eggPos);var eggData=egg.saveWithoutMetadata();eggData.putUUID("Owner",player.getUUID());egg.load(eggData);
        var eggEvent=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,eggPos,level.getBlockState(eggPos),other);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(eggEvent);
        h.assertTrue(eggEvent.isCanceled(),"Claimed eggs must reject foreign breaking");
        level.setBlock(anchor.north(2),Blocks.AIR.defaultBlockState(),3);marker.conditions(true);
        h.assertTrue(mob.homePosition()==null,"An incomplete home must not be used for recovery");
        var saved=marker.saveWithoutMetadata();marker.load(saved);
        h.assertTrue(player.getUUID().equals(marker.ownerId()),"Home owner must persist");
        player.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void carrierPreservesStateAndRejectsDuplicates(GameTestHelper h) {
        var level=h.getLevel();var player=testOwner(h);var other=testOwner(h);
        var mob=create(h,Kind.SYUSYA,player.getUUID(),new BlockPos(2,2,2));
        var saved=new CompoundTag();mob.addAdditionalSaveData(saved);saved.putInt("Trust",45);saved.putBoolean("Staying",true);saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*20+1);mob.readAdditionalSaveData(saved);
        UUID id=mob.getUUID();var stack=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());
        h.assertTrue(!SyusyaCarrierItem.capture(mob,other,stack),"Foreign capture must fail");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);mob.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(mob.isRemoved() && SyusyaCarrierItem.filled(stack),"Owner interaction must capture Syusya");
        stack=net.minecraft.world.item.ItemStack.of(stack.save(new CompoundTag()));var copy=stack.copy();
        var ledger=CarrierLedger.get(level.getServer());
        var loaded=CarrierLedger.load(ledger.save(new CompoundTag()));
        h.assertTrue(loaded.matches(id,stack.getTag().getUUID("Ticket"),player.getUUID()),"Ticket ledger must survive serialization");
        var duplicate=DudunkaMod.TYPES.get(Kind.SYUSYA).get().create(level);duplicate.setUUID(id);
        h.assertTrue(!level.addFreshEntity(duplicate),"Entity join guard must reject an entity still in a carrier");
        BlockPos release=h.absolutePos(new BlockPos(4,2,4));level.setBlock(release.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(release,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!SyusyaCarrierItem.release(level,release,player,stack) && SyusyaCarrierItem.filled(stack),"Blocked release must preserve the carrier");
        level.setBlock(release,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!SyusyaCarrierItem.release(level,release,other,stack),"Foreign release must fail");
        h.assertTrue(SyusyaCarrierItem.release(level,release,player,stack),"Valid release must succeed");
        var released=(Companion)level.getEntity(id);
        h.assertTrue(released!=null && released.trust()==45 && released.stage()==1 && released.staying(),"Identity, trust, growth and mode must survive transport");
        h.assertTrue(Math.abs(released.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-.098)<1e-8,"Carrier release must restore the trust speed bonus once");
        h.assertTrue(!SyusyaCarrierItem.release(level,release,player,copy),"Copied filled carrier must not duplicate Syusya");
        h.assertTrue(SyusyaCarrierItem.capture(released,player,stack),"Released Syusya may be captured again");
        h.assertTrue(!SyusyaCarrierItem.release(level,release,player,copy),"Old ticket must remain invalid after recapture");
        h.assertTrue(SyusyaCarrierItem.release(level,release,player,stack),"New ticket must release successfully");
        h.assertTrue(!CarrierLedger.load(ledger.save(new CompoundTag())).carried(id),"Consumed ticket state must persist");
        player.discard();other.discard();h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=180)
    public static void carrierCanReleaseInAnotherDimension(GameTestHelper h) {
        var player=testOwner(h);var mob=create(h,Kind.SYUSYA,player.getUUID(),new BlockPos(2,2,2));
        mob.bindHome(h.absolutePos(new BlockPos(2,2,2)));
        UUID id=mob.getUUID();var stack=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());
        h.assertTrue(SyusyaCarrierItem.capture(mob,player,stack),"Capture before dimension transfer must succeed");
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos pos=new BlockPos(8,200,8);nether.getChunkAt(pos);
        var oldFloor=nether.getBlockState(pos.below());var oldSpace=nether.getBlockState(pos);
        nether.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);nether.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        nether.setChunkForced(0,0,true); // Test fixture only; production code never adds chunk tickets.
        player.teleportTo(nether,8.5,200,8.5,0,0);
        h.runAfterDelay(60,()->{
            h.assertTrue(SyusyaCarrierItem.release(nether,pos,player,stack),"Global ticket must allow release in Nether");
            h.runAfterDelay(20,()->{
                var released=(Companion)nether.getEntity(id);
                h.assertTrue(released!=null,"Released Syusya must become accessible in the target dimension");
                var data=new CompoundTag();released.addAdditionalSaveData(data);
                h.assertTrue(released.level()==nether && data.getString("HomeDimension").equals("minecraft:overworld"),"Transfer must preserve the original home dimension");
                h.assertTrue(released.homePosition()==null,"Home in another dimension must not resolve to a local coordinate");
                released.discard();nether.setBlock(pos.below(),oldFloor,3);nether.setBlock(pos,oldSpace,3);nether.setChunkForced(0,0,false);player.discard();h.succeed();
            });
        });
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void appleGoalToleratesTicksAfterConsumption(GameTestHelper h) {
        var mob=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(2,2,2));
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),mob.getX(),mob.getY(),mob.getZ(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE,2));
        item.setNoPickUpDelay();h.getLevel().addFreshEntity(item);
        var goal=new FamilyBehaviorGoal(mob);
        h.assertTrue(goal.canUse(),"Nearby apple must start a behavior goal");goal.start();goal.tick();
        h.assertTrue(item.isAlive() && item.getItem().getCount()==1,"First goal tick must consume exactly one apple");
        // Minecraft checks continuation less often than it ticks running goals.
        for(int i=0;i<3;i++)goal.tick();
        h.assertTrue(item.getItem().getCount()==1,"Extra ticks after clearing the target must not consume another apple");
        h.assertTrue(!goal.canContinueToUse() && mob.activity()==Activity.IDLE,"Completed goal must stop safely");
        goal.stop();goal.tick();
        h.assertTrue(item.getItem().getCount()==1,"Tick with no active target must be harmless");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void pettingRespectsOwnerCooldownAndStay(GameTestHelper h) {
        var player=testOwner(h);var other=testOwner(h);
        var cat=create(h,Kind.MARUSYA,player.getUUID(),new BlockPos(2,2,2));
        player.moveTo(cat.getX(),cat.getY(),cat.getZ(),0,0);
        cat.interact(other,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.trust()==0,"Foreign player must not pet Marusya");
        cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.trust()==2 && !cat.staying(),"Owner empty-hand pet must add two trust without changing mode");
        cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.trust()==2,"Repeated pet during cooldown must not add trust");
        var data=new CompoundTag();cat.addAdditionalSaveData(data);
        h.assertTrue(data.getInt("PetCooldown")==600,"Pet cooldown must be saved");
        var restored=DudunkaMod.TYPES.get(Kind.MARUSYA).get().create(h.getLevel());restored.readAdditionalSaveData(data);
        restored.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(restored.trust()==2,"Reload must not bypass pet cooldown");
        player.setShiftKeyDown(true);cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.staying() && cat.trust()==2,"Shift empty hand must still toggle stay even during pet cooldown");
        player.setShiftKeyDown(false);data.putInt("PetCooldown",0);data.putBoolean("Staying",true);cat.readAdditionalSaveData(data);
        cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.staying() && cat.trust()==4,"Petting a waiting cat must preserve stay");
        data.putInt("Trust",99);data.putInt("PetCooldown",0);cat.readAdditionalSaveData(data);cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(cat.trust()==100,"Pet trust must cap at 100");
        player.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=650)
    public static void pettingSceneAndCooldownUseRealTicks(GameTestHelper h) {
        var player=testOwner(h);var cat=create(h,Kind.MARUSYA,player.getUUID(),new BlockPos(2,2,2));
        player.moveTo(cat.getX()+1,cat.getY(),cat.getZ(),0,0);
        cat.setNoAi(false);cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.runAfterDelay(10,()->h.assertTrue(cat.activity()==Activity.PURR,"Petting must start a synchronized purring scene"));
        h.runAfterDelay(50,()->{
            h.assertTrue(cat.activity()!=Activity.PURR,"Purring scene must finish after two seconds");
            cat.setNoAi(true);cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(cat.trust()==2,"Scene ending must not end the thirty-second cooldown");
        });
        h.runAfterDelay(610,()->{
            cat.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(cat.trust()==4,"Cooldown must expire after thirty loaded seconds");
            player.discard();h.succeed();
        });
    }
    @GameTest(template="empty", timeoutTicks=40)
    public static void snailTrustSpeedPreservesOtherModifiers(GameTestHelper h) {
        var player=testOwner(h);var snail=create(h,Kind.SYUSYA,player.getUUID(),new BlockPos(2,2,2));
        var speed=snail.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        h.assertTrue(Math.abs(speed.getValue()-.08)<1e-8,"Zero trust must retain normal snail speed");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE));
        snail.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(snail.trust()==5 && Math.abs(speed.getValue()-.082)<1e-8,"Feeding must immediately apply the trust speed bonus");
        UUID external=UUID.randomUUID();speed.setBaseValue(.1);
        speed.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(external,"Other mod",.2,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        var data=new CompoundTag();snail.addAdditionalSaveData(data);data.putInt("Trust",100);
        for(int repeat=0;repeat<5;repeat++)snail.readAdditionalSaveData(data);
        h.assertTrue(Math.abs(speed.getValue()-.17)<1e-8 && speed.getModifiers().size()==2,"Trust bonus must not stack on repeated loading");
        h.assertTrue(Math.abs(speed.getBaseValue()-.1)<1e-8 && speed.getModifier(external)!=null,"Other mods' base and modifiers must survive");
        data.putInt("Trust",0);snail.readAdditionalSaveData(data);
        h.assertTrue(Math.abs(speed.getValue()-.12)<1e-8 && speed.getModifiers().size()==1,"Zero trust must remove only the family modifier");
        data.putInt("Trust",500);snail.readAdditionalSaveData(data);
        h.assertTrue(snail.trust()==100 && Math.abs(speed.getValue()-.17)<1e-8,"Malformed high trust must cap the bonus");
        var cat=create(h,Kind.MARUSYA,player.getUUID(),new BlockPos(4,2,4));cat.readAdditionalSaveData(data);
        h.assertTrue(cat.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifiers().size()==1,"Cat must not receive the snail trust modifier");
        player.discard();h.succeed();
    }

}
