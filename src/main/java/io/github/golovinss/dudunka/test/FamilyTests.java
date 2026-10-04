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

    private static net.minecraft.server.level.ServerPlayer testOwner(GameTestHelper h) {return testOwner(h,null);}
    private static net.minecraft.server.level.ServerPlayer testOwner(GameTestHelper h,java.util.List<net.minecraft.network.chat.Component> messages) {
        // Ordinary ServerPlayer is needed: Forge rejects FakePlayer advancement awards.
        // Register directly in the test level with a no-op listener; there is no real network client.
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DudunkaTest"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(messages!=null && packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat)messages.add(chat.content());
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {send(packet);}
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

    private static void interestFloor(GameTestHelper h) {
        for(BlockPos p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,1,0)),h.absolutePos(new BlockPos(5,1,5))))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void actualChestOpenRespectsOwnerAndClose(GameTestHelper h) {
        interestFloor(h);var player=testOwner(h);var other=testOwner(h);var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(3,2,3));level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
        var mob=create(h,Kind.DUDUNKA,player.getUUID(),new BlockPos(1,2,2));
        var foreign=create(h,Kind.DUDUNKA,other.getUUID(),new BlockPos(2,2,2));
        var cat=create(h,Kind.MARUSYA,player.getUUID(),new BlockPos(1,2,1));
        var staying=create(h,Kind.DUDUNKA,player.getUUID(),new BlockPos(2,2,1));var data=new CompoundTag();staying.addAdditionalSaveData(data);data.putBoolean("Staying",true);staying.readAdditionalSaveData(data);
        player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5,0,0);other.moveTo(player.position());
        h.assertTrue(player.openMenu(chest).isPresent(),"Actual server chest menu must open");
        h.assertTrue(pos.equals(mob.openChestTarget()),"Opening event must notify owner's Dudunka");
        h.assertTrue(foreign.openChestTarget()==null && cat.openChestTarget()==null && staying.openChestTarget()==null,"Foreign family, cat and explicit stay must ignore the event");
        mob.setOnGround(true);var goal=new ChestCuriosityGoal(mob);h.assertTrue(goal.canUse(),"Opened chest must have a reachable nearby standing position");goal.start();goal.tick();
        h.assertTrue(mob.activity()==Activity.CURIOUS && chest.getItem(0).getCount()==3,"Reaction must not change chest contents");
        player.closeContainer();h.assertTrue(!goal.canContinueToUse() && mob.openChestTarget()==null,"Closing the menu must end the reaction");goal.tick();goal.stop();
        player.openMenu(chest);h.assertTrue(mob.openChestTarget()==null,"Reopening immediately must respect reaction cooldown");player.closeContainer();
        other.openMenu(chest);h.assertTrue(foreign.openChestTarget()!=null && mob.openChestTarget()==null,"Another owner may notify only their own Dudunka");
        other.closeContainer();player.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void doubleChestUsesContainerIdentity(GameTestHelper h) {
        interestFloor(h);var player=testOwner(h);var level=h.getLevel();
        var a=h.absolutePos(new BlockPos(3,2,3));var b=a.east();
        level.setBlock(a,Blocks.CHEST.defaultBlockState(),3);level.setBlock(b,Blocks.CHEST.defaultBlockState(),3);
        var left=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(a);var right=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(b);
        var compound=new net.minecraft.world.CompoundContainer(left,right);
        var mob=create(h,Kind.DUDUNKA,player.getUUID(),new BlockPos(1,2,2));player.moveTo(a.getX(),a.getY(),a.getZ()+1,0,0);
        player.containerMenu=net.minecraft.world.inventory.ChestMenu.sixRows(55,player.getInventory(),compound);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player,player.containerMenu));
        h.assertTrue(ChestCuriosity.isViewing(player,a) && ChestCuriosity.isViewing(player,b) && mob.openChestTarget()!=null,"Both halves of a compound chest must resolve by identity");
        player.closeContainer();
        player.containerMenu=net.minecraft.world.inventory.ChestMenu.threeRows(56,player.getInventory(),new net.minecraft.world.SimpleContainer(27));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player,player.containerMenu));
        h.assertTrue(mob.openChestTarget()==null,"Generic menu without a world chest must not trigger interest");
        player.closeContainer();player.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void cakeRunStopsWhenCakeDisappears(GameTestHelper h) {
        interestFloor(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,2));level.setBlock(pos,Blocks.CAKE.defaultBlockState(),3);
        var cake=level.getBlockState(pos);var mob=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(1,2,2));
        mob.setOnGround(true);var goal=new FamilyBehaviorGoal(mob);h.assertTrue(goal.canUse(),"Reachable cake must deterministically attract Dudunka");goal.start();
        h.assertTrue(mob.activity()==Activity.CAKE_RUN,"Cake approach must synchronize the running activity");goal.tick();
        h.assertTrue(mob.getNavigation().getPath()!=null && level.getBlockState(pos).equals(cake),"Cake approach must start navigation without consuming the cake");
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!goal.canContinueToUse(),"Removing cake must end the goal");goal.tick();goal.tick();
        h.assertTrue(mob.activity()==Activity.IDLE && mob.getNavigation().isDone(),"Extra ticks after cake removal must safely clear navigation");goal.stop();
        level.setBlock(pos,cake,3);var saved=new CompoundTag();mob.addAdditionalSaveData(saved);saved.putBoolean("Staying",true);mob.readAdditionalSaveData(saved);
        h.assertTrue(!new FamilyBehaviorGoal(mob).canUse(),"Stay must override cake interest");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=140)
    public static void chestReactionExpiresAndWaitsForFamily(GameTestHelper h) {
        interestFloor(h);var player=testOwner(h);var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(2,2,3));level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
        var mob=create(h,Kind.DUDUNKA,player.getUUID(),new BlockPos(0,2,2));mob.setOnGround(true);
        var snail=create(h,Kind.SYUSYA,player.getUUID(),new BlockPos(5,2,2));
        player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5,0,0);player.openMenu(chest);
        h.assertTrue(mob.openChestTarget()!=null,"Open chest must create a short-lived request");
        var family=new FamilyBehaviorGoal(mob);h.assertTrue(family.canUse(),"Family waiting must remain selectable during a chest request");family.start();
        h.assertTrue(mob.activity()==Activity.WAIT_FOR_SYUSYA,"Waiting for own Syusya must precede chest curiosity");family.stop();snail.discard();
        h.assertTrue(!new FamilyBehaviorGoal(mob).canUse() && new ChestCuriosityGoal(mob).canUse(),"Without safety tasks, ambient curiosity must yield to the chest reaction");
        h.runAfterDelay(110,()->{
            h.assertTrue(mob.openChestTarget()==null,"Reaction request must expire even if the chest stays open");
            player.closeContainer();player.discard();h.succeed();
        });
    }

    private static boolean placeNaturalEgg(GameTestHelper h,BlockPos origin) {
        var configured=h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .get(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID,"snail_egg"));
        h.assertTrue(configured!=null,"Configured natural egg feature must load from datapack");
        return configured.place(h.getLevel(),h.getLevel().getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(42),origin);
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void naturalEggNeedsCareAndCanBeClaimed(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlock(pos.below(),Blocks.MOSS_BLOCK.defaultBlockState(),3);level.setBlock(pos.east(),Blocks.WATER.defaultBlockState(),3);
        h.assertTrue(placeNaturalEgg(h,pos),"Moss, water and free space must permit one natural egg");
        var egg=(EggEntity)level.getBlockEntity(pos);var saved=egg.saveWithoutMetadata();
        h.assertTrue(egg.ownerId()==null && saved.getInt("Offerings")==0 && saved.getInt("Progress")==0,"Natural egg must be unclaimed and start with no care/progress");
        h.assertTrue(!placeNaturalEgg(h,pos) && level.getBlockEntity(pos)==egg,"Repeated attempt must not overwrite an existing egg");
        var player=testOwner(h);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LEAVES));
        egg.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getUUID().equals(egg.ownerId()) && player.getMainHandItem().isEmpty(),"First offering must claim a natural egg normally");
        var data=egg.saveWithoutMetadata();h.assertTrue(data.getInt("Offerings")==1 && data.getInt("Progress")==0,"One offering must not start automatic hatching");
        player.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void naturalEggRejectsUnsafeOrOccupiedSites(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos),"Dry grass must not generate an egg");
        level.setBlock(pos.east(),Blocks.WATER.defaultBlockState(),3);level.setBlock(pos.west(),Blocks.LAVA.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos),"Nearby lava must reject generation even with water");
        level.setBlock(pos.west(),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos) && level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK),"Generation must not replace existing blocks");
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos),"Stone floor must not qualify");
        level.setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos),"Low solid ceiling must reject an egg");
        level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(placeNaturalEgg(h,pos),"Safe wet grass must qualify after restoring conditions");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void naturalEggProbeIsBoundedAndCanBeDisabled(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlock(pos.below(),Blocks.MOSS_BLOCK.defaultBlockState(),3);level.setBlock(pos.east(),Blocks.WATER.defaultBlockState(),3);
        for(int y=0;y<=24;y++)level.setBlock(pos.above(y),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!placeNaturalEgg(h,pos.above(24)),"Column probe must stop after 24 positions");
        boolean enabled=DudunkaMod.NATURAL_EGGS.get();
        try {
            DudunkaMod.NATURAL_EGGS.set(false);
            h.assertTrue(!placeNaturalEgg(h,pos),"Disabled natural generation must not place an egg");
        } finally {DudunkaMod.NATURAL_EGGS.set(enabled);}
        h.assertTrue(placeNaturalEgg(h,pos.above(23)),"Column probe must find a suitable floor within its bound");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void naturalEggBiomeWiringIsLimited(GameTestHelper h) {
        var access=h.getLevel().registryAccess();var features=access.registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        var surface=features.get(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID,"snail_egg_surface"));
        var cave=features.get(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID,"snail_egg_cave"));
        h.assertTrue(surface!=null && cave!=null,"Both placed features must decode from worldgen JSON");
        var biomes=access.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        h.assertTrue(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SWAMP).value().getGenerationSettings().hasFeature(surface),"Swamp must include the surface feature");
        h.assertTrue(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP).value().getGenerationSettings().hasFeature(surface),"Mangrove swamp must include the surface feature");
        h.assertTrue(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.LUSH_CAVES).value().getGenerationSettings().hasFeature(cave),"Lush caves must include the cave feature");
        h.assertTrue(!biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value().getGenerationSettings().hasFeature(surface)
                && !biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.LUSH_CAVES).value().getGenerationSettings().hasFeature(surface),"Unlisted biomes and cave/surface variants must remain separate");h.succeed();
    }

    private static BlockPos restFire(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner) {
        interestFloor(h);var fire=h.absolutePos(new BlockPos(2,2,2));
        h.getLevel().setBlock(fire,Blocks.CAMPFIRE.defaultBlockState(),3);
        owner.moveTo(fire.getX()+.5,fire.getY(),fire.getZ()+1.5,0,0);owner.setShiftKeyDown(true);return fire;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void campfireSeatsAndCancellation(GameTestHelper h) {
        var owner=testOwner(h);var fire=restFire(h,owner);
        var a=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(0,2,2));a.setOnGround(true);
        var b=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,2));b.setOnGround(true);
        var foreign=create(h,Kind.MARUSYA,UUID.randomUUID(),new BlockPos(4,2,4));foreign.setOnGround(true);
        var scene=CampfireScenes.select(a);h.assertTrue(scene!=null,"Two own companions must form a scene");
        h.assertTrue(scene==CampfireScenes.select(b) && scene.seats().size()==2 && !scene.seats().containsKey(foreign.getUUID()),"Family must share distinct reservations, excluding foreign companions");
        h.assertTrue(!scene.seats().get(a.getUUID()).equals(scene.seats().get(b.getUUID())),"Seats must be different");
        for(var mob:java.util.List.of(a,b)) {
            var seat=scene.seats().get(mob.getUUID());
            h.assertTrue(HomeRules.safeStanding(h.getLevel(),seat,mob) && CampfireScenes.safePath(mob,mob.getNavigation().createPath(seat,0)),"Seats and routes must avoid the fire");
        }
        var goal=new CampfireRestGoal(a);h.assertTrue(goal.canUse(),"Rest goal must be selectable");goal.start();goal.tick();
        var seat=scene.seats().get(a.getUUID());a.moveTo(seat.getX()+.5,seat.getY(),seat.getZ()+.5,0,0);goal.tick();
        h.assertTrue(a.activity()==Activity.CAMP_REST && !a.staying(),"Arrival must rest without setting permanent stay");
        owner.setShiftKeyDown(false);h.assertTrue(!goal.canContinueToUse(),"Standing owner must cancel rest");goal.tick();goal.tick();goal.stop();
        h.assertTrue(a.activity()==Activity.IDLE && a.getNavigation().isDone(),"Extra ticks must safely stop the scene");
        h.assertTrue(CampfireScenes.select(a)==null,"Standing owner must clear reservations");
        owner.setShiftKeyDown(true);h.getLevel().setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);
        var renewed=CampfireScenes.select(a);h.assertTrue(renewed!=null && renewed!=scene,"Soul fire must support a fresh scene");
        h.getLevel().setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT,false),3);
        h.assertTrue(!CampfireScenes.active(a,renewed),"Extinguishing fire must cancel the scene");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void campfireNeedsOwnFamilyAndHonorsStay(GameTestHelper h) {
        var owner=testOwner(h);var fire=restFire(h,owner);
        var a=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(0,2,2));a.setOnGround(true);
        var foreign=create(h,Kind.MARUSYA,UUID.randomUUID(),new BlockPos(4,2,2));foreign.setOnGround(true);
        h.assertTrue(CampfireScenes.select(a)==null,"Foreign companion cannot complete the family");
        var b=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,4));b.setOnGround(true);
        var scene=CampfireScenes.select(a);h.assertTrue(scene!=null,"Own second member must enable rest");
        var saved=new CompoundTag();b.addAdditionalSaveData(saved);saved.putBoolean("Staying",true);b.readAdditionalSaveData(saved);
        h.assertTrue(CampfireScenes.select(b)==null && !CampfireScenes.active(a,scene),"Stay must exclude a member and stop an undersized scene");
        saved.putBoolean("Staying",false);b.readAdditionalSaveData(saved);scene=CampfireScenes.select(a);
        h.assertTrue(scene!=null,"Returning member must allow replanning");
        owner.moveTo(fire.getX()+10,fire.getY(),fire.getZ(),0,0);
        h.assertTrue(!CampfireScenes.active(a,scene),"Leaving the fire must stop rest");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void campfireYieldsToWaitingForSnail(GameTestHelper h) {
        var owner=testOwner(h);restFire(h,owner);
        var a=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(0,2,2));a.setOnGround(true);
        var b=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,2));b.setOnGround(true);
        var snail=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(5,2,4));snail.setOnGround(true);
        h.assertTrue(CampfireScenes.select(a)!=null,"Fixture must offer a campfire scene");
        var family=new FamilyBehaviorGoal(a);h.assertTrue(family.canUse(),"Waiting must remain available");family.start();
        h.assertTrue(a.activity()==Activity.WAIT_FOR_SYUSYA,"Waiting for own snail must precede campfire rest");family.stop();
        snail.discard();h.assertTrue(!new FamilyBehaviorGoal(a).canUse(),"Ambient family tasks must yield to owner's campfire scene");
        owner.discard();h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void dudunkaAgeDimensionsFollowReferenceRatios(GameTestHelper h) {
        var mob=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(2,2,2));
        float baby=mob.getBbHeight();var saved=new CompoundTag();mob.addAdditionalSaveData(saved);
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*20+1);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==1 && Math.abs(mob.getBbHeight()/baby-1.3f)<.001f,"Teen hitbox must follow 1.3x baby height");
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==2 && Math.abs(mob.getBbHeight()/baby-1.6f)<.001f,"Adult hitbox must follow 1.6x baby height");
        var roundtrip=new CompoundTag();mob.addAdditionalSaveData(roundtrip);mob.readAdditionalSaveData(roundtrip);
        h.assertTrue(mob.stage()==2 && Math.abs(mob.growthScale()-.88f)<.001f,"Age and new dimensions must survive NBT reload");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void syusyaAgeFootprintAndCarrierPersist(GameTestHelper h) {
        var mob=create(h,Kind.SYUSYA,UUID.randomUUID(),new BlockPos(2,2,2));
        float baby=mob.getBbWidth();var saved=new CompoundTag();mob.addAdditionalSaveData(saved);
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*20+1);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==1 && Math.abs(mob.getBbWidth()/baby-1.3f)<.001f,"Teen footprint must grow 1.3x");
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);saved.putInt("Trust",60);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==2 && Math.abs(mob.getBbWidth()/baby-1.7f)<.001f,"Adult footprint must grow 1.7x");
        h.assertTrue(Math.abs(mob.growthScale()-.55f)<.001f,"Render scale must not double-count age geometry");
        var tag=new CompoundTag();mob.save(tag);var restored=net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,h.getLevel(),entity->entity);
        h.assertTrue(restored instanceof Companion,"Carrier-style full entity NBT must reconstruct companion");var copy=(Companion)restored;
        h.assertTrue(copy.stage()==2 && copy.ownerId().equals(mob.ownerId()) && copy.trust()==60 && Math.abs(copy.getBbWidth()/baby-1.7f)<.001f,"Carrier NBT must preserve age, owner, trust and footprint");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void marusyaAgeDimensionsAndPettingPersist(GameTestHelper h) {
        var owner=testOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(2,2,2));
        float baby=mob.getBbWidth();var saved=new CompoundTag();mob.addAdditionalSaveData(saved);
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*20+1);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==1 && Math.abs(mob.getBbWidth()/baby-1.35f)<.001f,"Teen footprint must grow 1.35x");
        saved.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);mob.readAdditionalSaveData(saved);
        h.assertTrue(mob.stage()==2 && Math.abs(mob.getBbWidth()/baby-1.8f)<.001f && Math.abs(mob.growthScale()-.55f)<.001f,"Adult footprint grows without double render scaling");
        owner.setShiftKeyDown(false);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        mob.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        var after=new CompoundTag();mob.addAdditionalSaveData(after);
        h.assertTrue(mob.trust()==2 && after.getInt("PetCooldown")==600,"Adult cat must still accept normal petting");
        mob.readAdditionalSaveData(after);
        h.assertTrue(mob.stage()==2 && mob.trust()==2 && Math.abs(mob.getBbWidth()/baby-1.8f)<.001f,"Age, trust and footprint must survive reload");
        owner.setShiftKeyDown(true);mob.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(mob.staying(),"Shift interaction must retain stay command");owner.discard();h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void friendshipLedgerDeduplicatesPersistsAndCaps(GameTestHelper h) {
        var ledger=new FamilyFriendships();UUID owner=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        for(int i=0;i<=59;i++){
            ledger.observe(owner,a,Kind.DUDUNKA,b,Kind.MARUSYA,i*20);
            ledger.observe(owner,b,Kind.MARUSYA,a,Kind.DUDUNKA,i*20);
        }
        h.assertTrue(ledger.score(owner,a,b)==0 && ledger.progress(owner,a,b)==1180,"Two reporters must not double-count the same pair/second");
        var saved=ledger.save(new CompoundTag());var restored=FamilyFriendships.load(saved);
        restored.observe(owner,a,Kind.DUDUNKA,b,Kind.MARUSYA,100000);
        h.assertTrue(restored.score(owner,a,b)==0 && restored.progress(owner,a,b)==1180,"First observation after reload must not credit offline time");
        restored.observe(owner,b,Kind.MARUSYA,a,Kind.DUDUNKA,100020);
        h.assertTrue(restored.score(owner,a,b)==1 && restored.progress(owner,a,b)==0,"Persisted partial minute must finish after one valid second");
        restored.observe(owner,a,Kind.DUDUNKA,b,Kind.MARUSYA,200000);
        h.assertTrue(restored.score(owner,a,b)==1,"Long gap must not grant retroactive points");
        restored.pause(owner,a);restored.observe(owner,a,Kind.DUDUNKA,b,Kind.MARUSYA,200020);
        h.assertTrue(restored.progress(owner,a,b)==0,"Pause must clear the continuity sample");
        for(int sample=1;sample<=6100;sample++)restored.observe(owner,a,Kind.DUDUNKA,b,Kind.MARUSYA,200020+sample*20);
        h.assertTrue(restored.score(owner,a,b)==100 && restored.progress(owner,a,b)==0,"Friendship must cap at 100");
        UUID stranger=UUID.randomUUID();h.assertTrue(restored.score(stranger,a,b)==0 && restored.relations(stranger,a).isEmpty(),"Owners must have isolated relationships");
        h.assertTrue(!restored.observe(owner,a,Kind.DUDUNKA,a,Kind.DUDUNKA,0),"Self friendship must be rejected");
        var relation=restored.relations(owner,a).get(0);h.assertTrue(relation.partner().equals(b) && relation.kind()==Kind.MARUSYA,"Symmetric pair ordering must preserve the partner's kind");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void friendshipAccruesOnlyDuringSeatedCampfireRest(GameTestHelper h) {
        var owner=testOwner(h);restFire(h,owner);
        var a=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(0,2,2));a.setOnGround(true);
        var b=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,2));b.setOnGround(true);
        var foreign=create(h,Kind.MARUSYA,UUID.randomUUID(),new BlockPos(4,2,4));foreign.setOnGround(true);
        var scene=CampfireScenes.select(a);h.assertTrue(scene!=null,"Fixture must form own campfire scene");
        for(var mob:java.util.List.of(a,b)){
            var seat=scene.seats().get(mob.getUUID());mob.moveTo(seat.getX()+.5,seat.getY(),seat.getZ()+.5,0,0);
        }
        var ledger=FamilyFriendships.get(h.getLevel().getServer());long now=h.getLevel().getServer().overworld().getGameTime();
        long start=now/20*20-1180;
        for(int sample=0;sample<=59;sample++)ledger.observe(owner.getUUID(),a.getUUID(),a.kind,b.getUUID(),b.kind,start+sample*20);
        var ga=new CampfireRestGoal(a);var gb=new CampfireRestGoal(b);
        h.assertTrue(ga.canUse() && gb.canUse(),"Both rest goals must start");ga.start();gb.start();
        h.onEachTick(()->{ga.tick();gb.tick();FamilyFriendships.tick(foreign,scene);});
        int[] paused={-1};
        h.runAfterDelay(45,()->{
            h.assertTrue(a.activity()==Activity.CAMP_REST && b.activity()==Activity.CAMP_REST,"Both must really be seated in the scene");
            h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==1,"Goal ticks must finish the seeded minute once, without duplicate credit");
            h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),foreign.getUUID())==0,"Foreign character must not earn friendship");
            owner.setShiftKeyDown(false);ga.tick();gb.tick();paused[0]=ledger.progress(owner.getUUID(),a.getUUID(),b.getUUID());
        });
        h.runAfterDelay(85,()->{
            h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==1 && ledger.progress(owner.getUUID(),a.getUUID(),b.getUUID())==paused[0],"Standing owner must stop accumulation");
            ga.stop();gb.stop();owner.discard();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void establishedFriendsPreferNearbySafeSeats(GameTestHelper h) {
        var messages=new java.util.ArrayList<net.minecraft.network.chat.Component>();var owner=testOwner(h,messages);restFire(h,owner);
        var a=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(0,2,2));a.setOnGround(true);
        var b=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,2));b.setOnGround(true);
        var ledger=FamilyFriendships.get(h.getLevel().getServer());
        for(int sample=0;sample<=600;sample++)ledger.observe(owner.getUUID(),a.getUUID(),a.kind,b.getUUID(),b.kind,sample*20);
        ledger.pause(owner.getUUID(),a.getUUID());
        h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==10,"Fixture must reach friends tier");
        var scene=CampfireScenes.select(a);h.assertTrue(scene!=null,"Friend scene must remain reachable");
        var first=scene.seats().get(a.getUUID());var second=scene.seats().get(b.getUUID());
        h.assertTrue(!first.equals(second) && first.distSqr(second)<=4,"Established friends must reserve adjacent distinct ring places");
        h.assertTrue(HomeRules.safeStanding(h.getLevel(),second,b) && CampfireScenes.safePath(b,b.getNavigation().createPath(second,0)),"Preference must still require safe standing and reachable path");
        var book=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,book);
        int trust=b.trust();boolean stay=b.staying();messages.clear();b.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(messages.stream().anyMatch(message->message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().equals("message.dudunka.friendship_row") && text.getArgs()[1].equals(10)),"Book click must actually send the relationship score to its owner");
        h.assertTrue(book.getCount()==1 && b.trust()==trust && b.staying()==stay,"Book interaction must not consume item, pet cat or change stay");
        var foreignMessages=new java.util.ArrayList<net.minecraft.network.chat.Component>();var stranger=testOwner(h,foreignMessages);
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
        b.interact(stranger,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(foreignMessages.stream().noneMatch(message->message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().startsWith("message.dudunka.friendship_")) && !FamilyFriendships.show(stranger,b),"Foreign viewer must not receive relationship packets");
        owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void friendshipLoadRejectsMalformedRows(GameTestHelper h) {
        UUID owner=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();var rows=new net.minecraft.nbt.ListTag();
        var valid=new CompoundTag();valid.putUUID("Owner",owner);valid.putUUID("First",a);valid.putUUID("Second",b);
        valid.putString("FirstKind","dudunka");valid.putString("SecondKind","syusya");valid.putInt("Score",500);valid.putInt("Progress",999999);rows.add(valid);
        var self=valid.copy();self.putUUID("Second",a);rows.add(self);var unknown=valid.copy();unknown.putString("FirstKind","missing");rows.add(unknown);rows.add(new CompoundTag());
        var tag=new CompoundTag();tag.put("Bonds",rows);var ledger=FamilyFriendships.load(tag);
        h.assertTrue(ledger.relations(owner,a).size()==1 && ledger.score(owner,a,b)==100 && ledger.progress(owner,a,b)==0,"Load must reject invalid rows and clamp valid values");h.succeed();
    }

    private static BlockPos sceneHome(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,Kind kind) {
        interestFloor(h);var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(2,2,2));
        for(BlockPos p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,4,0)),h.absolutePos(new BlockPos(5,4,5))))level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(anchor,DudunkaMod.HOMES.get(kind).get().defaultBlockState(),3);
        ((HomeMarkerEntity)level.getBlockEntity(anchor)).claim(owner.getUUID());
        level.setBlock(h.absolutePos(new BlockPos(0,2,0)),Blocks.CHEST.defaultBlockState(),3);
        level.setBlock(h.absolutePos(new BlockPos(0,2,4)),Blocks.CAKE.defaultBlockState(),3);
        level.setBlock(h.absolutePos(new BlockPos(1,2,0)),Blocks.TORCH.defaultBlockState(),3);
        var bed=Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.NORTH);
        level.setBlock(h.absolutePos(new BlockPos(4,2,1)),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        level.setBlock(h.absolutePos(new BlockPos(4,2,2)),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
        level.setBlock(h.absolutePos(new BlockPos(4,2,3)),Blocks.DANDELION.defaultBlockState(),3);
        var position=h.absolutePos(new BlockPos(1,2,1));owner.moveTo(position.getX()+.5,position.getY(),position.getZ()+.5,0,0);
        h.assertTrue(((HomeMarkerEntity)level.getBlockEntity(anchor)).conditions(true).ready(),"Fixture must be a complete marked house");return anchor;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void homeCatReservesBedAndYieldsToPlayer(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.MARUSYA);long previous=h.getLevel().getDayTime();
        h.getLevel().setDayTime(12500);
        try {
            var cat=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(3,2,1));cat.bindHome(anchor);cat.setOnGround(true);
            var goal=new HomeSceneGoal(cat);h.assertTrue(goal.canUse(),"Evening cat must select reachable free house bed");
            var scene=HomeScenes.select(cat);h.assertTrue(scene!=null && scene.activity()==Activity.SLEEP,"Scene must choose bed sleep");
            goal.start();cat.moveTo(scene.destination().x,scene.destination().y,scene.destination().z,0,0);goal.tick();
            h.assertTrue(cat.activity()==Activity.SLEEP && scene.focus().equals(cat.homeSceneTarget()),"Only arrival must set sleep and reserve bed");
            var other=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(3,2,3));other.bindHome(anchor);other.setOnGround(true);
            h.assertTrue(HomeScenes.select(other)==null,"Second cat must not reserve the same bed");
            var bed=h.getLevel().getBlockState(scene.focus());h.getLevel().setBlock(scene.focus(),bed.setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED,true),3);
            goal.tick();goal.tick();h.assertTrue(cat.activity()==Activity.IDLE && cat.homeSceneTarget()==null,"Player occupation must end scene, including extra tick");
            h.getLevel().setBlock(scene.focus(),bed,3);h.getLevel().setDayTime(6000);
            h.assertTrue(HomeScenes.select(cat)==null,"Daytime must not trigger before-bed scene");
            owner.discard();h.succeed();
        } finally {h.getLevel().setDayTime(previous);}
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void homePlantsRequireOwnerAndRevalidate(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.SYUSYA);
        var snail=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(3,2,3));snail.setOnGround(true);
        h.assertTrue(HomeScenes.select(snail)==null,"Legacy egg home must not start marked house scenes");
        snail.bindHome(anchor);var scene=HomeScenes.select(snail);h.assertTrue(scene!=null && scene.activity()==Activity.SEEK_PLANT,"Snail must select reachable house flower");
        var goal=new HomeSceneGoal(snail);h.assertTrue(goal.canUse(),"Plant scene must start");goal.start();
        snail.moveTo(scene.destination().x,scene.destination().y,scene.destination().z,0,0);goal.tick();
        h.assertTrue(snail.activity()==Activity.SEEK_PLANT,"Arrived snail must watch plant");
        h.getLevel().setBlock(scene.focus(),Blocks.AIR.defaultBlockState(),3);goal.tick();goal.tick();
        h.assertTrue(snail.activity()==Activity.IDLE && snail.homeSceneTarget()==null,"Destroyed plant must release scene without stale target");
        h.getLevel().setBlock(scene.focus(),Blocks.DANDELION.defaultBlockState(),3);
        owner.moveTo(owner.getX()+20,owner.getY(),owner.getZ(),0,0);h.assertTrue(HomeScenes.select(snail)==null,"Leaving home must end selection");
        owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void homeSceneHonorsStayAndHomeOwnership(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.DUDUNKA);
        var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(3,2,3));mob.bindHome(anchor);mob.setOnGround(true);
        var scene=HomeScenes.select(mob);h.assertTrue(scene!=null && scene.activity()==Activity.SIT,"Dudunka must select quiet flower seat");
        var goal=new HomeSceneGoal(mob);h.assertTrue(goal.canUse(),"Dudunka scene starts");goal.start();
        owner.setShiftKeyDown(true);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);mob.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);goal.tick();
        h.assertTrue(mob.staying() && mob.homeSceneTarget()==null,"Stay command must release temporary scene");
        var foreign=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(3,2,1));foreign.bindHome(anchor);foreign.setOnGround(true);
        h.assertTrue(HomeScenes.select(foreign)==null,"Foreign marker must not support a scene");
        mob.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);owner.setShiftKeyDown(false);
        h.assertTrue(HomeScenes.select(mob)==null && !mob.homeSceneReady(),"Home scene pause must also let ambient goals run");
        var ambient=new FamilyBehaviorGoal(mob);h.assertTrue(ambient.canUse(),"Cake interest must become available between home scenes");ambient.start();
        h.assertTrue(mob.activity()==Activity.CAKE_RUN,"Existing cake behavior must resume during home scene cooldown");ambient.stop();
        h.getLevel().setBlock(anchor,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(HomeScenes.select(mob)==null && mob.homeAnchor()==null,"Removed marker must invalidate house");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void homeDudunkaActuallyWalksToFlower(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.DUDUNKA);
        var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(1,2,3));mob.bindHome(anchor);mob.setOnGround(true);mob.setNoAi(false);
        h.runAfterDelay(100,()->{
            h.assertTrue(mob.activity()==Activity.SIT && mob.homeSceneTarget()!=null,"Live navigation: activity="+mob.activity()+", position="+mob.position()+", target="+mob.homeSceneTarget()+", path="+mob.getNavigation().getPath()+", scene="+HomeScenes.select(mob));
            h.assertTrue(mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(anchor))<=100,"Home scene remains local");
            owner.discard();h.succeed();
        });
    }

    @GameTest(template="empty",batch="home_evening",timeoutTicks=160)
    public static void adultHomeCatActuallyClimbsBedAndYieldsToSleep(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.MARUSYA);long previous=h.getLevel().getDayTime();
        h.getLevel().setDayTime(12500);
        var cat=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(2,2,1));cat.bindHome(anchor);cat.setOnGround(true);
        var data=new CompoundTag();cat.addAdditionalSaveData(data);data.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);cat.readAdditionalSaveData(data);cat.setNoAi(false);
        h.runAfterDelay(110,()->{
            try {
                var bed=h.absolutePos(new BlockPos(4,2,1));
                var expected=new net.minecraft.world.phys.Vec3(bed.getX()+.5,bed.getY()+.5625,bed.getZ()+.5);
                h.assertTrue(cat.stage()==2 && cat.activity()==Activity.SLEEP && cat.distanceToSqr(expected)<=.36,
                    "Adult cat must really climb and sleep on bed: "+cat.position()+", "+cat.activity());
                owner.startSleeping(bed);
            } catch(RuntimeException error) {h.getLevel().setDayTime(previous);throw error;}
        });
        h.runAfterDelay(135,()->{
            try {
                h.assertTrue(cat.homeSceneTarget()==null && cat.activity()!=Activity.SLEEP,"Actual sleeping owner must end the cat scene");
                h.succeed();
            } finally {owner.stopSleepInBed(true,true);owner.discard();h.getLevel().setDayTime(previous);}
        });
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void homecomingNeedsRealExcursionAndDoesNotSpam(GameTestHelper h) {
        var arrival=new Homecoming();arrival.observe(0,0);
        for(int i=1;i<=9;i++)arrival.observe(i*20,400);arrival.observe(200,0);
        h.assertTrue(!arrival.pending(200),"Short trip must not trigger welcome");
        for(int i=1;i<=10;i++)arrival.observe(200+i*20,400);arrival.observe(420,0);
        h.assertTrue(arrival.pending(420) && arrival.consume(420) && !arrival.consume(420),"Ten loaded seconds away and return must give one welcome");
        for(int i=1;i<=10;i++)arrival.observe(420+i*20,400);arrival.observe(640,0);
        h.assertTrue(!arrival.pending(640),"Second trip inside one-minute cooldown must not spam");
        arrival.observe(1800,0);for(int i=1;i<=10;i++)arrival.observe(1800+i*20,400);arrival.observe(2020,0);
        h.assertTrue(arrival.pending(2020) && !arrival.pending(2420),"New excursion after cooldown works, but stale welcome expires");
        var gap=new Homecoming();gap.observe(0,0);gap.observe(20,400);gap.observe(20000,400);
        for(int i=1;i<=10;i++)gap.observe(20000+i*20,400);gap.observe(20220,0);
        h.assertTrue(!gap.pending(20220),"Chunk/offline gaps must never turn into a trip");
        var doorway=new Homecoming();doorway.observe(0,0);for(int i=1;i<=10;i++)doorway.observe(i*20,100);doorway.observe(220,0);
        h.assertTrue(!doorway.pending(220),"Eight-to-twelve-block hysteresis band must not count as leaving home");
        var walking=new Homecoming();walking.observe(0,0);for(int i=1;i<=10;i++)walking.observe(i*20,400);
        walking.observe(220,100);walking.observe(240,100);walking.observe(260,0);
        h.assertTrue(walking.pending(260),"A completed excursion must survive walking back through the hysteresis band");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=330)
    public static void returningOwnerGetsOneHomeWaveAndCanInterruptIt(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.DUDUNKA);
        var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(1,2,3));mob.bindHome(anchor);
        var data=new CompoundTag();mob.addAdditionalSaveData(data);data.putInt("Trust",25);mob.readAdditionalSaveData(data);
        h.runAfterDelay(25,()->owner.moveTo(owner.getX()+20,owner.getY(),owner.getZ(),0,0));
        h.runAfterDelay(250,()->{var p=h.absolutePos(new BlockPos(1,2,1));owner.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);});
        h.runAfterDelay(275,()->{
            h.assertTrue(mob.homeWelcomePending(),"Actual loaded AI ticks must register owner's trip and return");
            var scene=HomeScenes.select(mob);h.assertTrue(scene!=null && scene.activity()==Activity.WAVE,"Return must prefer welcome over flowers");
            var goal=new HomeSceneGoal(mob);h.assertTrue(goal.canUse(),"Home welcome goal must start");goal.start();goal.tick();
            h.assertTrue(mob.activity()==Activity.WAVE && mob.homeWelcomeRunning() && !mob.homeWelcomePending(),"Welcome must wave and consume the pending return once");
            h.assertTrue(!new FamilyBehaviorGoal(mob).canUse(),"Ambient cake/gestures must not preempt an active welcome");
            owner.setShiftKeyDown(true);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);mob.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);goal.tick();goal.tick();
            h.assertTrue(mob.staying() && !mob.homeWelcomeRunning() && mob.homeSceneTarget()==null && mob.trust()==25,"Stay must interrupt wave without trust rewards or stale targets");
            owner.discard();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void homeWelcomeDoesNotStartOnLoadOrWithoutTrust(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.DUDUNKA);
        var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(1,2,3));mob.bindHome(anchor);mob.observeHomecoming();
        h.assertTrue(!mob.homeWelcomePending(),"Low trust and first presence must not greet");
        var saved=new CompoundTag();mob.addAdditionalSaveData(saved);saved.putInt("Trust",25);
        var loaded=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(h.getLevel());loaded.readAdditionalSaveData(saved);loaded.observeHomecoming();
        h.assertTrue(!loaded.homeWelcomePending() && !loaded.homeWelcomeRunning(),"Loading an owned home must establish a baseline, not invent a return");
        owner.discard();h.succeed();
    }

}
