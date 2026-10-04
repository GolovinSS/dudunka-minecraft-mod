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
    private static net.minecraft.server.level.ServerPlayer testOwner(GameTestHelper h,java.util.List<net.minecraft.network.chat.Component> messages) {return testOwner(h,messages,null);}
    private static net.minecraft.server.level.ServerPlayer testOwner(GameTestHelper h,java.util.List<net.minecraft.network.chat.Component> messages,java.util.List<net.minecraft.network.protocol.Packet<?>> packets) {
        // Ordinary ServerPlayer is needed: Forge rejects FakePlayer advancement awards.
        // Register directly in the test level with a no-op listener; there is no real network client.
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DudunkaTest"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packets!=null)packets.add(packet);}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){send(packet);}
        };
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packets!=null)packets.add(packet);
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

    @GameTest(template="empty", timeoutTicks=240)
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
            h.startSequence().thenWaitUntil(()->h.assertTrue(nether.getEntity(id) instanceof Companion,"Released Syusya must become accessible in the target dimension")).thenExecute(()->{
                var released=(Companion)nether.getEntity(id);
                var data=new CompoundTag();released.addAdditionalSaveData(data);
                h.assertTrue(released.level()==nether && data.getString("HomeDimension").equals("minecraft:overworld"),"Transfer must preserve the original home dimension");
                h.assertTrue(released.homePosition()==null,"Home in another dimension must not resolve to a local coordinate");
                released.discard();nether.setBlock(pos.below(),oldFloor,3);nether.setBlock(pos,oldSpace,3);nether.setChunkForced(0,0,false);player.discard();
            }).thenSucceed();
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
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_naturaleggprobeisboundedandcanbedisabled")
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
        var owner=testOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);
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
            h.assertTrue(a.activity()==Activity.CAMP_REST && b.activity()==Activity.CAMP_REST,"Both must really be seated: a="+a.activity()+" pos="+a.position()+" b="+b.activity()+" pos="+b.position()+" active="+CampfireScenes.active(a,scene)+" owner="+owner.position()+" shift="+owner.isShiftKeyDown()+" fire="+CampfireScenes.lit(h.getLevel(),scene.fire()));
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
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_homecatreservesbedandyieldstoplayer")
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

    @GameTest(template="empty",timeoutTicks=40)
    public static void albumFiltersOwnerAndShowsSavedState(GameTestHelper h) {
        var owner=testOwner(h);var own=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(own.getX(),own.getY(),own.getZ(),0,0);
        own.setCustomName(net.minecraft.network.chat.Component.literal("Маруся дома"));
        var data=new CompoundTag();own.addAdditionalSaveData(data);data.putInt("Trust",42);data.putBoolean("Staying",true);data.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);own.readAdditionalSaveData(data);
        create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(3,2,2));create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,2));
        var distant=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(5,2,2));distant.moveTo(owner.getX()+100,owner.getY(),owner.getZ(),0,0);
        var snapshot=FamilyAlbum.collect(owner);h.assertTrue(snapshot.total()==2 && snapshot.entries().size()==2,"Album must only include own loaded members within radius");
        var cat=snapshot.entries().stream().filter(e->e.kind()==Kind.MARUSYA).findFirst().orElseThrow();
        h.assertTrue(cat.stage()==2 && cat.trust()==42 && cat.staying() && cat.name().getString().equals("Маруся дома") && cat.home().state()==FamilyAlbum.HomeState.LEGACY,"Saved age/trust/mode/name/legacy home must be accurate");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumHomeConditionsAreFreshAndDoNotLoadChunks(GameTestHelper h) {
        var owner=testOwner(h);var anchor=sceneHome(h,owner,Kind.DUDUNKA);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(1,2,3));mob.bindHome(anchor);
        h.assertTrue(mob.albumHome().state()==FamilyAlbum.HomeState.READY && mob.albumHome().flags()==31,"Complete home must report all conditions");
        h.getLevel().setBlock(h.absolutePos(new BlockPos(0,2,4)),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(mob.albumHome().state()==FamilyAlbum.HomeState.INCOMPLETE && (mob.albumHome().flags()&16)==0,"Album must report missing cake immediately without waiting for cache");
        h.getLevel().setBlock(anchor,Blocks.AIR.defaultBlockState(),3);h.assertTrue(mob.albumHome().state()==FamilyAlbum.HomeState.MISSING,"Destroyed marker must be distinguished");
        var data=new CompoundTag();mob.addAdditionalSaveData(data);data.putString("HomeDimension","minecraft:the_nether");mob.readAdditionalSaveData(data);
        h.assertTrue(mob.albumHome().state()==FamilyAlbum.HomeState.OTHER_DIMENSION,"Foreign dimension must not resolve local home");
        var unloaded=new BlockPos(20000000,80,20000000);data.putString("HomeDimension",h.getLevel().dimension().location().toString());data.putLong("FamilyHome",unloaded.asLong());mob.readAdditionalSaveData(data);
        h.assertTrue(!h.getLevel().hasChunkAt(unloaded) && mob.albumHome().state()==FamilyAlbum.HomeState.UNLOADED && !h.getLevel().hasChunkAt(unloaded),"Read-only home lookup must never load its chunk");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumCodecRoundTripsAndRejectsOversizedLists(GameTestHelper h) {
        var friend=new FamilyAlbum.Friend(net.minecraft.network.chat.Component.literal("Сюся"),70);
        var entry=new FamilyAlbum.Entry(UUID.randomUUID(),net.minecraft.network.chat.Component.literal("Дюдюнька"),Kind.DUDUNKA,1,25,false,new FamilyAlbum.HomeInfo(FamilyAlbum.HomeState.READY,31),java.util.List.of(friend));
        var original=new FamilyAlbum.Snapshot(1,java.util.List.of(entry));var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {FamilyAlbum.encode(original,buf);h.assertTrue(original.equals(FamilyAlbum.decode(buf)),"Localized snapshot and friendship must round-trip exactly");}
        finally {buf.release();}
        var invalid=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {invalid.writeVarInt(13);invalid.writeVarInt(13);boolean rejected=false;try{FamilyAlbum.decode(invalid);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Client decoder must reject more than 12 entries");}finally{invalid.release();}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumItemSendsOnlyToUserAndKeepsInventory(GameTestHelper h) {
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var owner=testOwner(h,null,packets);var foreignPackets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var stranger=testOwner(h,null,foreignPackets);
        var album=new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get());owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,album);packets.clear();foreignPackets.clear();
        DudunkaMod.ALBUM.get().use(h.getLevel(),owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        long sent=packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket).count();
        h.assertTrue(sent==1 && foreignPackets.isEmpty() && album.getCount()==1 && !album.hasTag(),"Private album: sent="+sent+", packets="+packets.stream().map(p->p.getClass().getName()).toList()+", foreign="+foreignPackets.size()+", count="+album.getCount()+", tag="+album.hasTag());
        DudunkaMod.ALBUM.get().use(h.getLevel(),owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket).count()==1,"Immediate repeated use must respect cooldown");
        owner.getCooldowns().removeCooldown(DudunkaMod.ALBUM.get());
        var own=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(2,2,2));int trust=own.trust();boolean stay=own.staying();
        own.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket).count()==2 && own.trust()==trust && own.staying()==stay,"Clicking own companion with album must open it without changing care/commands");
        owner.discard();stranger.discard();h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void albumCapsPagesAndFriendHistory(GameTestHelper h) {
        var owner=testOwner(h);var members=new java.util.ArrayList<Companion>();
        for(int i=0;i<14;i++)members.add(create(h,Kind.values()[i%3],owner.getUUID(),new BlockPos(2,2,2)));
        var first=members.get(0);owner.moveTo(first.getX(),first.getY(),first.getZ(),0,0);first.setCustomName(net.minecraft.network.chat.Component.literal("AlbumFirst"));
        var ledger=FamilyFriendships.get(h.getLevel().getServer());
        for(int i=1;i<members.size();i++)ledger.observe(owner.getUUID(),first.getUUID(),first.kind,members.get(i).getUUID(),members.get(i).kind,0);
        var snapshot=FamilyAlbum.collect(owner);
        h.assertTrue(snapshot.total()==14 && snapshot.entries().size()==12,"Large family must show capped pages with truthful total");
        var entry=snapshot.entries().stream().filter(e->e.name().getString().equals("AlbumFirst")).findFirst().orElseThrow();
        h.assertTrue(entry.friends().size()==5 && snapshot.entries().stream().allMatch(e->e.friends().size()<=5),"Friend history must be capped without losing ledger entries");
        h.assertTrue(ledger.relations(owner.getUUID(),first.getUUID()).size()==13,"Reading album must not truncate persistent friendship history");owner.discard();h.succeed();
    }

    private static long albums(net.minecraft.server.level.ServerPlayer player){long n=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(DudunkaMod.ALBUM.get()))n+=player.getInventory().getItem(i).getCount();return n;}
    @GameTest(template="empty",timeoutTicks=40)
    public static void guideGiftPersistsOncePerPlayerAndWorld(GameTestHelper h) {
        var owner=testOwner(h);var gifts=new FamilyGuideGifts();
        h.assertTrue(gifts.give(owner) && albums(owner)==1,"First eligible login must give one album");
        var restored=FamilyGuideGifts.load(gifts.save(new CompoundTag()));owner.getInventory().clearContent();
        h.assertTrue(restored.delivered(owner.getUUID()) && !restored.give(owner) && albums(owner)==0,"Saved delivery must survive item loss and not give another on rejoin");
        var other=testOwner(h);h.assertTrue(restored.give(other) && albums(other)==1,"Each different player receives their own guide");owner.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void guideGiftDoesNotDuplicateExistingOrLoseOnFullInventory(GameTestHelper h) {
        var owner=testOwner(h);var gifts=new FamilyGuideGifts();owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        h.assertTrue(!gifts.give(owner) && gifts.delivered(owner.getUUID()) && albums(owner)==1,"Existing offhand album must mark delivery without duplicate");
        var full=testOwner(h);for(int i=0;i<36;i++)full.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        h.assertTrue(!gifts.give(full) && !gifts.delivered(full.getUUID()) && albums(full)==0,"Full inventory must leave delivery pending without dropping a book");
        full.getInventory().setItem(10,net.minecraft.world.item.ItemStack.EMPTY);h.assertTrue(gifts.give(full) && albums(full)==1 && gifts.delivered(full.getUUID()),"Next login with space must complete delivery");
        owner.discard();full.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void guidePagesUseServerAvailabilityAndSnapshotCodec(GameTestHelper h) {
        var base=EggGuide.page(1,EggGuide.LOOT);var expanded=EggGuide.page(1,EggGuide.LOOT|EggGuide.MVS);
        h.assertTrue(expanded.size()==base.size()+1 && EggGuide.page(3,31).size()>EggGuide.page(3,0).size(),"Optional locations must depend on server flags and enabled sources");
        for(int i=0;i<EggGuide.PAGES;i++)h.assertTrue(!EggGuide.page(i,31).isEmpty(),"Every guide page must have content");
        var original=new FamilyAlbum.Snapshot(0,java.util.List.of(),31);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{FamilyAlbum.encode(original,buf);h.assertTrue(original.equals(FamilyAlbum.decode(buf)),"Server availability must round-trip with empty family");}finally{buf.release();}
        var owner=testOwner(h);FamilyGuideGifts.onLogin(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));
        h.assertTrue(albums(owner)==1,"Actual login subscriber must deliver guide through world SavedData");owner.getInventory().clearContent();FamilyGuideGifts.onLogin(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));h.assertTrue(albums(owner)==0,"Repeated login event must not duplicate gift");owner.discard();h.succeed();
    }
    private static net.minecraft.world.level.storage.loot.LootContext eggLootContext(GameTestHelper h,String table,long seed){
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(2,2,2)))).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        return new net.minecraft.world.level.storage.loot.LootContext.Builder(params).withOptionalRandomSeed(seed).withQueriedLootTableId(new net.minecraft.resources.ResourceLocation(table)).create(null);
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_bmceggmodifiersmatchverifiedtableswithoutdependencies")
    public static void bmcEggModifiersMatchVerifiedTablesWithoutDependencies(GameTestHelper h) {
        String[] tables={"mvs:houses_common","mvs:houses_flower","mvs:houses_desert","mvs:abandoned","betterdungeons:skeleton_dungeon/chests/common","betterdungeons:zombie_dungeon/chests/common","mvs:swamps","betterdungeons:small_dungeon/chests/loot_piles"};
        Kind[] kinds={Kind.DUDUNKA,Kind.DUDUNKA,Kind.DUDUNKA,Kind.MARUSYA,Kind.MARUSYA,Kind.MARUSYA,Kind.SYUSYA,Kind.SYUSYA};double setting=DudunkaMod.LOOT_MULTIPLIER.get();
        try{
            DudunkaMod.LOOT_MULTIPLIER.set(10.0);
            for(int i=0;i<tables.length;i++){
                boolean found=false;
                for(int seed=1;seed<=128;seed++){
                    var generated=net.minecraftforge.common.ForgeHooks.modifyLoot(new net.minecraft.resources.ResourceLocation(tables[i]),new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),eggLootContext(h,tables[i],seed*982451653L));
                    final Kind expected=kinds[i];h.assertTrue(generated.stream().filter(item->!(item.getItem() instanceof TrailNoteItem)).allMatch(item->item.is(DudunkaMod.EGG_ITEMS.get(expected).get())),"Table must match only assigned kind: "+tables[i]);if(generated.stream().anyMatch(item->item.is(DudunkaMod.EGG_ITEMS.get(expected).get()))){found=true;break;}
                }
                h.assertTrue(found,"Registered GLM must add an egg for verified ID "+tables[i]);
            }
            var unrelated=net.minecraftforge.common.ForgeHooks.modifyLoot(new net.minecraft.resources.ResourceLocation("mvs:empty"),new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),eggLootContext(h,"mvs:empty",1));h.assertTrue(unrelated.isEmpty(),"Unlisted empty structures must not get eggs");
        }finally{DudunkaMod.LOOT_MULTIPLIER.set(setting);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_savedunopenedchestgetseggbutgeneratedchestdoesnotrefill")
    public static void savedUnopenedChestGetsEggButGeneratedChestDoesNotRefill(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(pos);
        var table=new net.minecraft.resources.ResourceLocation("minecraft:chests/village/village_plains_house");double setting=DudunkaMod.LOOT_MULTIPLIER.get();boolean found=false;
        try {
            DudunkaMod.LOOT_MULTIPLIER.set(10.0);
            for(int seed=1;seed<=128;seed++) {
                chest.clearContent();chest.setLootTable(table,seed);var oldSave=chest.saveWithFullMetadata();
                h.assertTrue(oldSave.contains("LootTable") && !oldSave.contains("Items"),"Unopened saved container must still defer loot generation");
                chest.load(oldSave);chest.unpackLootTable(null);
                for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(DudunkaMod.EGG_ITEMS.get(Kind.DUDUNKA).get()))found=true;
                if(found)break;
            }
            h.assertTrue(found,"Existing unopened vanilla chest must receive egg through real loot generation");
            var formed=chest.saveWithFullMetadata();h.assertTrue(!formed.contains("LootTable"),"Generated chest must consume its loot table");chest.load(formed);chest.unpackLootTable(null);
            h.assertTrue(chest.saveWithFullMetadata().equals(formed),"Reload/reopen must retain identical inventory without another roll");
        }finally{DudunkaMod.LOOT_MULTIPLIER.set(setting);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_egglootpreservesexistinglootanddeduplicatesimportedegg")
    public static void eggLootPreservesExistingLootAndDeduplicatesImportedEgg(GameTestHelper h) {
        var context=eggLootContext(h,"mvs:houses_common",1);var modifier=new EggLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0],"dudunka",1);
        var loot=new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack>();loot.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));modifier.apply(loot,context);modifier.apply(loot,context);
        h.assertTrue(loot.size()==2 && loot.get(0).getCount()==3 && loot.get(1).is(DudunkaMod.EGG_ITEMS.get(Kind.DUDUNKA).get()),"Repeated/imported loot must keep original items and at most one added egg of its kind");
        double setting=DudunkaMod.LOOT_MULTIPLIER.get();try{DudunkaMod.LOOT_MULTIPLIER.set(0.0);var empty=new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack>();modifier.apply(empty,context);h.assertTrue(empty.isEmpty(),"Zero loot multiplier must disable additions");}finally{DudunkaMod.LOOT_MULTIPLIER.set(setting);}h.succeed();
    }

    private static long togetherPreviousTime;
    @BeforeBatch(batch="home_together")
    public static void beforeTogether(net.minecraft.server.level.ServerLevel level){togetherPreviousTime=level.getDayTime();level.setDayTime(6000);}
    @AfterBatch(batch="home_together")
    public static void afterTogether(net.minecraft.server.level.ServerLevel level){level.setDayTime(togetherPreviousTime);}
    private static java.util.List<Companion> togetherFamily(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner) {
        var first=sceneHome(h,owner,Kind.DUDUNKA);var family=new java.util.ArrayList<Companion>();
        BlockPos[] homes={first,h.absolutePos(new BlockPos(3,2,2)),h.absolutePos(new BlockPos(3,2,3))};
        BlockPos[] starts={new BlockPos(1,2,3),new BlockPos(3,2,1),new BlockPos(3,2,4)};
        for(int i=0;i<3;i++) {
            Kind kind=Kind.values()[i];
            if(i>0){h.getLevel().setBlock(homes[i],DudunkaMod.HOMES.get(kind).get().defaultBlockState(),3);((HomeMarkerEntity)h.getLevel().getBlockEntity(homes[i])).claim(owner.getUUID());}
            var mob=create(h,kind,owner.getUUID(),starts[i]);mob.bindHome(homes[i]);mob.setOnGround(true);family.add(mob);
        }
        return family;
    }
    @GameTest(template="empty",batch="home_together",timeoutTicks=40)
    public static void togetherReservesSeparateCoveredPlacesForOwnFamily(GameTestHelper h) {
        var owner=testOwner(h);var family=togetherFamily(h,owner);var foreign=create(h,Kind.MARUSYA,UUID.randomUUID(),new BlockPos(5,2,5));foreign.bindHome(family.get(1).homeAnchor());
        var scene=HomeTogetherScenes.select(family.get(0));h.assertTrue(scene!=null && scene.seats().size()==3,"All three nearby valid homes must offer shared rest");
        h.assertTrue(new java.util.HashSet<>(scene.seats().values()).size()==3 && !scene.seats().containsKey(foreign.getUUID()),"Each own member gets a different seat; foreign member excluded");
        for(var mob:family){var seat=scene.seats().get(mob.getUUID());h.assertTrue(HomeTogetherScenes.active(mob,scene) && !h.getLevel().canSeeSky(seat.above()) && HomeRules.safeStanding(h.getLevel(),seat,mob),"Each reserved place must be covered, safe and active");}
        h.assertTrue(HomeTogetherScenes.select(foreign)==null,"Foreign marker cannot enable gathering");
        var legacy=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,4));h.assertTrue(HomeTogetherScenes.select(legacy)==null,"Egg home without personal marker cannot gather");
        owner.discard();h.succeed();
    }
    @GameTest(template="empty",batch="home_together",timeoutTicks=40)
    public static void togetherHonorsCommandsOwnerDepartureAndDestroyedHome(GameTestHelper h) {
        var owner=testOwner(h);var family=togetherFamily(h,owner);var scene=HomeTogetherScenes.select(family.get(0));h.assertTrue(scene!=null,"Fixture must offer gathering");
        var goal=new HomeTogetherGoal(family.get(0));h.assertTrue(goal.canUse(),"Gather goal must start");goal.start();
        family.get(0).commandStay(owner,true);goal.tick();goal.tick();
        h.assertTrue(family.get(0).staying() && !HomeTogetherScenes.active(family.get(0),scene) && HomeTogetherScenes.active(family.get(1),scene),"Stay cancels its member while remaining pair can rest");
        h.getLevel().setBlock(family.get(2).homeAnchor(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!HomeTogetherScenes.active(family.get(1),scene),"Missing second valid home must end remaining singleton");
        owner.moveTo(owner.getX()+20,owner.getY(),owner.getZ(),0,0);h.assertTrue(HomeTogetherScenes.select(family.get(1))==null,"Owner leaving home cancels selection");
        owner.discard();h.succeed();
    }
    @GameTest(template="empty",batch="home_together",timeoutTicks=100)
    public static void togetherFriendshipRequiresActualSimultaneousSitting(GameTestHelper h) {
        var owner=testOwner(h);var family=togetherFamily(h,owner);var a=family.get(0);var b=family.get(1);family.get(2).discard();
        var scene=HomeTogetherScenes.select(a);h.assertTrue(scene!=null && scene.seats().size()==2,"Pair must have gathering");
        var ga=new HomeTogetherGoal(a);var gb=new HomeTogetherGoal(b);h.assertTrue(ga.canUse() && gb.canUse(),"Both goals select the same gathering");ga.start();gb.start();
        var ledger=FamilyFriendships.get(h.getLevel().getServer());long now=h.getLevel().getServer().overworld().getGameTime();long start=now/20*20-1180;
        for(int i=0;i<=59;i++)ledger.observe(owner.getUUID(),a.getUUID(),a.kind,b.getUUID(),b.kind,start+i*20);
        b.setActivity(Activity.CURIOUS);FamilyFriendships.tickHome(a,scene);FamilyFriendships.tickHome(b,scene);
        h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==0,"Approaching cannot earn a point");
        for(var mob:java.util.List.of(a,b)){var seat=scene.seats().get(mob.getUUID());mob.moveTo(seat.getX()+.5,seat.getY(),seat.getZ()+.5,0,0);mob.setNoGravity(true);mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
        h.onEachTick(()->{ga.tick();gb.tick();});int[] progress={-1};
        h.runAfterDelay(45,()->{
            h.assertTrue(a.activity()==Activity.SIT && b.activity()==Activity.SIT && ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==1,"Real goal ticks must finish seeded minute once for simultaneous sitting");
            owner.setShiftKeyDown(true);ga.tick();gb.tick();progress[0]=ledger.progress(owner.getUUID(),a.getUUID(),b.getUUID());
        });
        h.runAfterDelay(80,()->{
            h.assertTrue(ledger.score(owner.getUUID(),a.getUUID(),b.getUUID())==1 && ledger.progress(owner.getUUID(),a.getUUID(),b.getUUID())==progress[0],"Interrupted rest cannot accrue friendship");
            ga.stop();gb.stop();owner.discard();h.succeed();
        });
    }
    @GameTest(template="empty",batch="home_together",timeoutTicks=40)
    public static void togetherYieldsToSnailThreatAndUnsafeSeat(GameTestHelper h) {
        var owner=testOwner(h);var family=togetherFamily(h,owner);var scene=HomeTogetherScenes.select(family.get(0));h.assertTrue(scene!=null,"Fixture must offer gathering");
        var snail=family.get(2);snail.moveTo(snail.getX()+6,snail.getY(),snail.getZ(),0,0);
        h.assertTrue(HomeTogetherScenes.select(family.get(0))==null && new FamilyBehaviorGoal(family.get(0)).canUse(),"Waiting for distant own snail must take priority");
        snail.moveTo(family.get(0).getX()+1,snail.getY(),family.get(0).getZ(),0,0);
        var monster=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,h.getLevel());monster.moveTo(family.get(1).position());monster.setNoAi(true);h.getLevel().addFreshEntity(monster);
        h.assertTrue(!HomeTogetherScenes.active(family.get(1),scene),"Cat must leave group on nearby threat");monster.discard();
        var seat=scene.seats().get(family.get(0).getUUID());h.getLevel().setBlock(seat,Blocks.WATER.defaultBlockState(),3);
        h.assertTrue(!HomeTogetherScenes.active(family.get(0),scene),"Water replacing seat must invalidate immediately");owner.discard();h.succeed();
    }
    @GameTest(template="empty",batch="home_together",timeoutTicks=150)
    public static void togetherReallyWalksAndSitsWithLiveAI(GameTestHelper h) {
        var owner=testOwner(h);var family=togetherFamily(h,owner);family.get(2).discard();var a=family.get(0);var b=family.get(1);
        // Use two Dudunkas to isolate the gathering from cat independence/evening rules.
        b.discard();b=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(4,2,4));b.bindHome(a.homeAnchor());
        var scene=HomeTogetherScenes.select(a);h.assertTrue(scene!=null && scene.seats().size()==2,"Live fixture must reserve a pair");
        a.moveTo(a.getX(),a.getY(),a.getZ()+1,0,0);b.moveTo(b.getX(),b.getY(),b.getZ()-1,0,0);a.setNoAi(false);b.setNoAi(false);final Companion second=b;
        h.runAfterDelay(100,()->{
            h.assertTrue(a.activity()==Activity.SIT && second.activity()==Activity.SIT && HomeTogetherScenes.active(a,scene),"Live AI must settle in the same gathering: a="+a.activity()+" "+a.position()+", b="+second.activity()+" "+second.position());
            h.assertTrue(a.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(scene.seats().get(a.getUUID())))<=.36 && second.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(scene.seats().get(second.getUUID())))<=.36,"Each must physically arrive at its own seat");
            owner.discard();h.succeed();
        });
    }

    private static AlbumCommands.Open commandAlbum(net.minecraft.server.level.ServerPlayer owner) {
        owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        return AlbumCommands.open(owner);
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void albumCommandsWaitFollowAndPreserveCare(GameTestHelper h) {
        var owner=testOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);
        var data=new CompoundTag();mob.addAdditionalSaveData(data);data.putInt("Trust",48);data.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);mob.readAdditionalSaveData(data);
        var open=commandAlbum(owner);mob.reserveHomeScene(mob.blockPosition());mob.setActivity(Activity.SLEEP);
        var reply=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),true,1));
        h.assertTrue(reply!=null && reply.accepted() && mob.staying() && mob.activity()==Activity.SIT && mob.homeSceneTarget()==null,"Wait command must immediately release the home scene");
        h.assertTrue(reply.snapshot().entries().get(0).id().equals(mob.getUUID()) && reply.snapshot().entries().get(0).staying(),"Response must carry authoritative UUID and mode");
        var saved=new CompoundTag();mob.addAdditionalSaveData(saved);mob.readAdditionalSaveData(saved);h.assertTrue(mob.staying(),"Command must persist in existing NBT");
        h.runAfterDelay(12,()->{
            var follow=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),false,2));
            var after=new CompoundTag();mob.addAdditionalSaveData(after);
            h.assertTrue(follow!=null && follow.accepted() && !mob.staying() && mob.trust()==48 && mob.stage()==2 && after.getLong("FamilyHome")==data.getLong("FamilyHome"),"Follow must preserve trust, stage and home");
            owner.discard();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumCommandsRejectForeignMissingItemAndDistantTargets(GameTestHelper h) {
        var owner=testOwner(h);var stranger=testOwner(h);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);stranger.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);
        var foreign=commandAlbum(stranger);var denied=AlbumCommands.execute(stranger,new AlbumCommands.Command(foreign.session(),mob.getUUID(),true,1));
        h.assertTrue(denied!=null && !denied.accepted() && !mob.commandStay(stranger,true),"Foreign owner must fail both album and shared command");
        var open=commandAlbum(owner);owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        denied=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),true,1));h.assertTrue(denied!=null && !denied.accepted(),"Removing album must reject command");
        open=commandAlbum(owner);mob.moveTo(owner.getX()+65,owner.getY(),owner.getZ(),0,0);
        denied=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),true,1));h.assertTrue(denied!=null && !denied.accepted() && denied.snapshot().entries().isEmpty(),"Leaving radius must reject and remove stale page");
        mob.moveTo(owner.getX(),owner.getY(),owner.getZ(),0,0);open=commandAlbum(owner);owner.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        denied=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),true,1));h.assertTrue(denied!=null && !denied.accepted() && !mob.staying(),"Spectator cannot command");
        owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumSessionsRejectReplaysFloodsAndPreviousOpenings(GameTestHelper h) {
        var owner=testOwner(h);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);var open=commandAlbum(owner);
        var first=new AlbumCommands.Command(open.session(),mob.getUUID(),true,1);h.assertTrue(AlbumCommands.execute(owner,first).accepted(),"First command must succeed immediately after opening");
        h.assertTrue(AlbumCommands.execute(owner,first)==null && AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),false,2))==null && mob.staying(),"Replay/flood must not toggle or reply");
        var next=AlbumCommands.open(owner);h.assertTrue(AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),mob.getUUID(),false,3))==null,"Previous screen session must be invalidated");
        h.assertTrue(AlbumCommands.execute(owner,new AlbumCommands.Command(next.session(),mob.getUUID(),true,1)).accepted() && mob.staying(),"Explicit repeated wait is idempotent");
        h.assertTrue(AlbumCommands.execute(null,first)==null,"Unauthenticated sender must be ignored");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumCommandsRejectHiddenAndRemovedMembers(GameTestHelper h) {
        var owner=testOwner(h);var members=new java.util.ArrayList<Companion>();
        for(int i=0;i<13;i++)members.add(create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(2,2,2)));
        owner.moveTo(members.get(0).getX(),members.get(0).getY(),members.get(0).getZ(),0,0);var open=commandAlbum(owner);
        var ids=open.snapshot().entries().stream().map(FamilyAlbum.Entry::id).toList();var hidden=members.stream().filter(m->!ids.contains(m.getUUID())).findFirst().orElseThrow();
        var denied=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),hidden.getUUID(),true,1));h.assertTrue(!denied.accepted() && !hidden.staying(),"UUID outside displayed twelve pages must be rejected");
        open=AlbumCommands.open(owner);UUID removed=open.snapshot().entries().get(0).id();h.getLevel().getEntity(removed).discard();
        denied=AlbumCommands.execute(owner,new AlbumCommands.Command(open.session(),removed,true,1));h.assertTrue(!denied.accepted() && denied.snapshot().entries().stream().noneMatch(e->e.id().equals(removed)),"Removed target must be revalidated and page refreshed");
        owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void albumCommandCodecAndReplyArePrivate(GameTestHelper h) {
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var foreignPackets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var owner=testOwner(h,null,packets);var stranger=testOwner(h,null,foreignPackets);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX(),mob.getY(),mob.getZ(),0,0);var open=commandAlbum(owner);
        var command=new AlbumCommands.Command(open.session(),mob.getUUID(),true,1);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{AlbumNetwork.encodeCommand(command,buf);h.assertTrue(command.equals(AlbumNetwork.decodeCommand(buf)),"Session, UUID, explicit mode and request must round-trip");}finally{buf.release();}
        packets.clear();foreignPackets.clear();AlbumNetwork.handleCommand(owner,command);
        h.assertTrue(mob.staying() && packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket).count()==1 && foreignPackets.isEmpty(),"Authoritative command response must reach sender only");
        owner.discard();stranger.discard();h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void catchUpCounterNeedsContinuousStallAndKeepsCooldown(GameTestHelper h) {
        var counter=new FamilyTravel.Counter();h.assertTrue(!counter.sample(0,true),"First observation must not credit an offline stall");
        for(int i=1;i<6;i++)h.assertTrue(!counter.sample(i*20,true),"Catch-up must wait six full seconds");
        h.assertTrue(counter.sample(120,true),"Six consecutive stalled seconds permit destination checks");counter.teleported(120);
        for(int i=1;i<=6;i++)h.assertTrue(!counter.sample(120+i*20,true),"Another stall inside twenty-second cooldown must not teleport");
        counter.reset();counter.sample(300,true);for(int i=1;i<=6;i++)h.assertTrue(!counter.sample(300+i*20,true),"Stopping goal must preserve teleport cooldown");
        counter.sample(10000,true);h.assertTrue(!counter.sample(10020,true),"Gap must restart the six-second clock");
        counter.sample(10040,false);for(int i=1;i<=5;i++)h.assertTrue(!counter.sample(10040+i*20,true),"Walking progress must reset the accumulated stall");
        h.assertTrue(counter.sample(10160,true),"Fresh uninterrupted stall after cooldown can catch up");h.succeed();
    }
    private static Companion travelFixture(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner) {
        interestFloor(h);var origin=h.absolutePos(new BlockPos(2,2,2));
        owner.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);owner.setOnGround(true);
        var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(2,2,2));
        mob.moveTo(origin.getX()+24.5,origin.getY(),origin.getZ()+.5,0,0);mob.setOnGround(true);return mob;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void catchUpPreservesIdentityAndAvoidsOccupiedPlaces(GameTestHelper h) {
        var owner=testOwner(h);var mob=travelFixture(h,owner);var before=new CompoundTag();mob.addAdditionalSaveData(before);var id=mob.getUUID();
        var destination=FamilyTravel.destination(mob,owner);h.assertTrue(destination!=null && FamilyTravel.safeDestination(mob,destination),"Fixture must provide a safe loaded landing");
        var occupant=create(h,Kind.MARUSYA,UUID.randomUUID(),h.relativePos(destination));
        h.assertTrue(!FamilyTravel.safeDestination(mob,destination),"A living entity must reserve its physical landing space");
        h.assertTrue(FamilyTravel.teleportNearOwner(mob,owner),"Another free nearby place must permit catch-up");
        var after=new CompoundTag();mob.addAdditionalSaveData(after);
        h.assertTrue(mob.getUUID().equals(id) && after.equals(before) && mob.distanceToSqr(owner)<36 && mob.fallDistance==0,"Catch-up must preserve identity/home/care/mode and reset fall distance");
        occupant.discard();owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_catchuprejectscommandsvehiclesforeignownersandunsafeground")
    public static void catchUpRejectsCommandsVehiclesForeignOwnersAndUnsafeGround(GameTestHelper h) {
        var owner=testOwner(h);var mob=travelFixture(h,owner);var stranger=testOwner(h);
        h.assertTrue(!FamilyTravel.teleportNearOwner(mob,stranger),"Foreign player must not move a companion");
        var data=new CompoundTag();mob.addAdditionalSaveData(data);data.putBoolean("Staying",true);mob.readAdditionalSaveData(data);
        h.assertTrue(!FamilyTravel.teleportNearOwner(mob,owner),"Stay command forbids catch-up");data.putBoolean("Staying",false);mob.readAdditionalSaveData(data);
        mob.setActivity(Activity.WAIT_FOR_SYUSYA);h.assertTrue(!FamilyTravel.teleportNearOwner(mob,owner),"Family waiting must not be bypassed");mob.setActivity(Activity.IDLE);
        owner.setOnGround(false);h.assertTrue(!FamilyTravel.teleportNearOwner(mob,owner),"Airborne owner must not be a teleport target");owner.setOnGround(true);
        var boat=new net.minecraft.world.entity.vehicle.Boat(h.getLevel(),mob.getX(),mob.getY(),mob.getZ());h.getLevel().addFreshEntity(boat);mob.startRiding(boat,true);
        h.assertTrue(!FamilyTravel.teleportNearOwner(mob,owner),"Passengers must not be pulled out of vehicles");mob.stopRiding();boat.discard();
        var safe=FamilyTravel.destination(mob,owner);h.assertTrue(safe!=null,"Fixture must remain usable");
        h.getLevel().setBlock(safe.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),3);h.assertTrue(!FamilyTravel.safeDestination(mob,safe),"Magma floor must be rejected");
        h.getLevel().setBlock(safe.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(safe,Blocks.SWEET_BERRY_BUSH.defaultBlockState(),3);h.assertTrue(!FamilyTravel.safeDestination(mob,safe),"Damaging plant must be rejected");
        h.getLevel().setBlock(safe,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(safe.above(),Blocks.WATER.defaultBlockState(),3);h.assertTrue(!FamilyTravel.safeDestination(mob,safe),"Water/head fluid must be rejected");
        boolean setting=DudunkaMod.FAMILY_CATCH_UP.get();try{DudunkaMod.FAMILY_CATCH_UP.set(false);h.assertTrue(!FamilyTravel.teleportNearOwner(mob,owner),"World configuration must disable catch-up");}finally{DudunkaMod.FAMILY_CATCH_UP.set(setting);}
        owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=210)
    public static void followGoalReallyCatchesUpAfterSixStalledSeconds(GameTestHelper h) {
        var owner=testOwner(h);var mob=travelFixture(h,owner);var goal=new FamilyFollowGoal(mob);
        h.assertTrue(goal.canUse(),"Following must remain selectable beyond old 24-block cutoff");goal.start();
        h.onEachTick(goal::tick);
        h.runAfterDelay(100,()->h.assertTrue(mob.distanceToSqr(owner)>=144,"Unreachable gap must not be crossed before six seconds"));
        h.runAfterDelay(165,()->{
            h.assertTrue(mob.distanceToSqr(owner)<36,"Actual goal ticks must catch up over the unreachable gap");
            goal.stop();owner.discard();h.succeed();
        });
    }

    @GameTest(template="empty",batch="travel_walk",timeoutTicks=150)
    public static void reachableSnailWalksWithoutTeleporting(GameTestHelper h) {
        var level=h.getLevel();
        for(BlockPos p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,1,0)),h.absolutePos(new BlockPos(18,6,5))))
            level.setBlock(p,p.getY()==h.absolutePos(new BlockPos(0,1,0)).getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var owner=testOwner(h);var goalPos=h.absolutePos(new BlockPos(17,2,2));owner.moveTo(goalPos.getX()+.5,goalPos.getY(),goalPos.getZ()+.5,0,0);owner.setOnGround(true);
        var snail=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(1,2,2));snail.setOnGround(true);snail.setNoAi(false);double start=snail.getX();
        h.runAfterDelay(100,()->{
            double moved=snail.getX()-start;
            h.assertTrue(moved>1 && moved<10 && snail.distanceToSqr(owner)>9,"Reachable snail must walk slowly rather than teleport: moved="+moved);
            owner.discard();h.succeed();
        });
    }


    private static net.minecraft.server.level.ServerPlayer recoveryOwner(GameTestHelper h){
        var owner=testOwner(h);var pos=h.absolutePos(new BlockPos(2,2,2));
        for(int x=-2;x<=6;x++)for(int z=-2;z<=6;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,1,z)),Blocks.STONE.defaultBlockState(),3);
        owner.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);owner.setOnGround(true);
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));return owner;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void recoveryRegistryPersistsAndFiltersOwners(GameTestHelper h){
        var owner=recoveryOwner(h);var own=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(4,2,4));var foreign=create(h,Kind.DUDUNKA,UUID.randomUUID(),new BlockPos(1,2,4));
        var registry=FamilyRegistry.get(h.getLevel().getServer());var loaded=FamilyRegistry.load(registry.save(new CompoundTag()));
        h.assertTrue(loaded.owned(owner.getUUID()).size()==1 && loaded.member(own.getUUID()).pos().equals(own.blockPosition()),"Index must persist exact identity/location and filter the owner");
        var snapshot=FamilyAlbum.collect(owner);h.assertTrue(snapshot.recovery().size()==1 && snapshot.recovery().get(0).id().equals(own.getUUID()),"Private recovery snapshot must omit foreign members");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{FamilyAlbum.encode(snapshot,buf);h.assertTrue(snapshot.equals(FamilyAlbum.decode(buf)),"Recovery payload must round trip");}finally{buf.release();}
        own.discard();h.assertTrue(registry.member(own.getUUID())==null,"Discarded live companion must be removed from the index");foreign.discard();owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void lostCarrierRotatesTicketAndRestoresCanonicalState(GameTestHelper h){
        var owner=recoveryOwner(h);var stranger=testOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,4));var state=new CompoundTag();mob.addAdditionalSaveData(state);state.putInt("Trust",43);state.putBoolean("Staying",true);mob.readAdditionalSaveData(state);
        UUID id=mob.getUUID();var original=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());h.assertTrue(SyusyaCarrierItem.capture(mob,owner,original),"Capture must store a server backup");
        var ledger=CarrierLedger.get(owner.server);var saved=CarrierLedger.load(ledger.save(new CompoundTag()));h.assertTrue(saved.backup(id,owner.getUUID()).getInt("Trust")==43,"Full backup must survive SavedData reload");
        h.assertTrue(!ledger.reissue(stranger,id),"Foreign recovery must fail");
        h.assertTrue(ledger.reissue(owner,id),"Lost carrier must be restored into free inventory slot");var recovered=owner.getInventory().getItem(1); // selected album occupies slot 0
        h.assertTrue(SyusyaCarrierItem.filled(recovered) && !recovered.getTag().getUUID("Ticket").equals(original.getTag().getUUID("Ticket")),"Reissue must rotate ticket");
        h.assertTrue(!ledger.reissue(owner,id),"Already held valid carrier must not be issued twice");
        BlockPos pos=h.absolutePos(new BlockPos(5,2,5));h.assertTrue(!SyusyaCarrierItem.release(h.getLevel(),pos,owner,original),"Old found carrier must remain invalid");
        recovered.getTag().getCompound("Companion").putInt("Trust",99);
        h.assertTrue(SyusyaCarrierItem.release(h.getLevel(),pos,owner,recovered),"New carrier must release exactly once");
        var actual=(Companion)h.getLevel().getEntity(id);h.assertTrue(actual!=null && actual.trust()==43 && actual.staying(),"Canonical server backup must override edited item state and preserve waiting");
        actual.discard();owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void carrierRecoveryFullInventoryAndLegacyHaveNoSideEffects(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,4));var stack=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());SyusyaCarrierItem.capture(mob,owner,stack);var token=stack.getTag().getUUID("Ticket");var ledger=CarrierLedger.get(owner.server);
        for(int j=0;j<36;j++)owner.getInventory().setItem(j,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        h.assertTrue(!ledger.reissue(owner,mob.getUUID()) && ledger.matches(mob.getUUID(),token,owner.getUUID()),"Full inventory rejection must retain old ticket");
        owner.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get()));h.assertTrue(ledger.reissue(owner,mob.getUUID()),"Single empty carrier can be filled even with otherwise full inventory");
        var legacy=new CarrierLedger();var id=UUID.randomUUID();legacy.capture(id,UUID.randomUUID(),owner.getUUID());h.assertTrue(!legacy.reissue(owner,id) && legacy.owned(owner.getUUID()).get(0).recoverable()==false,"Old lost carrier without saved data must never create a new snail");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void explicitRecoveryPreservesWaitAndRejectsUnsafeOwner(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(5,2,5));mob.commandStay(owner,true);var home=mob.homeAnchor();UUID id=mob.getUUID();
        owner.setOnGround(false);h.assertTrue(!FamilyRecovery.move(owner,mob),"Airborne owner must reject return");owner.setOnGround(true);
        h.assertTrue(FamilyRecovery.move(owner,mob),"Explicit owner return must allow waiting companion");h.assertTrue(mob.getUUID().equals(id) && mob.staying() && java.util.Objects.equals(mob.homeAnchor(),home),"Return must preserve UUID, home and wait command");
        var other=testOwner(h);other.setOnGround(true);h.assertTrue(!FamilyRecovery.move(other,mob),"Foreign owner must never move family member");mob.discard();owner.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void recoverySessionsRejectForgedAndReplayCarrierRequests(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,4));var stack=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());SyusyaCarrierItem.capture(mob,owner,stack);var open=AlbumCommands.open(owner);var ledger=CarrierLedger.get(owner.server);UUID token=stack.getTag().getUUID("Ticket");
        AlbumCommands.recover(owner,new AlbumCommands.RecoveryCommand(UUID.randomUUID(),mob.getUUID(),1));h.assertTrue(ledger.matches(mob.getUUID(),token,owner.getUUID()),"Forged session must not rotate token");
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);AlbumCommands.recover(owner,new AlbumCommands.RecoveryCommand(open.session(),mob.getUUID(),1));h.assertTrue(ledger.matches(mob.getUUID(),token,owner.getUUID()),"Missing album must reject recovery");
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));open=AlbumCommands.open(owner);var command=new AlbumCommands.RecoveryCommand(open.session(),mob.getUUID(),1);AlbumCommands.recover(owner,command);
        h.assertTrue(!ledger.matches(mob.getUUID(),token,owner.getUUID()),"Authenticated session must reissue carrier");var recovered=owner.getInventory().getItem(1);UUID fresh=recovered.getTag().getUUID("Ticket");AlbumCommands.recover(owner,command);h.assertTrue(ledger.matches(mob.getUUID(),fresh,owner.getUUID()),"Replay must leave new ticket unchanged");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=240,batch="recovery_dimension")
    public static void recoveryMovesExistingEntityAcrossDimensions(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(5,2,5));mob.commandStay(owner,true);UUID id=mob.getUUID();var originalHome=new CompoundTag();mob.addAdditionalSaveData(originalHome);
        var nether=owner.server.getLevel(net.minecraft.world.level.Level.NETHER);BlockPos p=new BlockPos(72,220,72);nether.getChunkAt(p);nether.setChunkForced(4,4,true);
        var old=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=2;y++){var at=p.offset(x,y,z);old.put(at,nether.getBlockState(at));nether.setBlock(at,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
        owner.teleportTo(nether,p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        h.runAfterDelay(60,()->{owner.setOnGround(true);h.assertTrue(FamilyRecovery.move(owner,mob),"Existing companion must transfer into owner's other dimension");h.startSequence().thenWaitUntil(()->h.assertTrue(nether.getEntity(id) instanceof Companion,"Transferred UUID must become accessible")).thenExecute(()->{var actual=(Companion)nether.getEntity(id);var state=new CompoundTag();actual.addAdditionalSaveData(state);h.assertTrue(actual.staying() && state.getLong("FamilyHome")==originalHome.getLong("FamilyHome") && state.getString("HomeDimension").equals(originalHome.getString("HomeDimension")) && h.getLevel().getEntity(id)==null,"Cross-dimension move must preserve wait/home and remove source instance");actual.discard();owner.discard();old.forEach((pos,blockState)->nether.setBlock(pos,blockState,3));nether.setChunkForced(4,4,false);}).thenSucceed();});
    }
    @GameTest(template="empty",timeoutTicks=400,batch="recovery_unload")
    public static void recoveryLoadsOnlyIndexedChunkAndReleasesTicket(GameTestHelper h){
        var owner=recoveryOwner(h);var nether=owner.server.getLevel(net.minecraft.world.level.Level.NETHER);var p=new BlockPos(328,220,328);nether.getChunkAt(p);nether.setChunkForced(20,20,true);
        var old=nether.getBlockState(p.below());nether.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);nether.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        var mob=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(nether);mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);mob.initialize(owner.getUUID(),p);mob.setNoAi(true);UUID id=mob.getUUID();nether.addFreshEntity(mob);
        h.runAfterDelay(50,()->{nether.setChunkForced(20,20,false);h.startSequence().thenWaitUntil(()->h.assertTrue(nether.getEntity(id)==null && !nether.hasChunkAt(p),"Fixture must genuinely unload entity and chunk"))
            .thenExecute(()->{owner.setOnGround(true);var open=AlbumCommands.open(owner);h.assertTrue(open.snapshot().recovery().stream().anyMatch(e->e.id().equals(id)),"Unloaded entity must remain in index");AlbumCommands.recover(owner,new AlbumCommands.RecoveryCommand(open.session(),id,1));h.assertTrue(FamilyRecovery.pending(owner.server,owner.getUUID()),"Return must enqueue bounded chunk search");})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getEntity(id) instanceof Companion,"Real disk entity must return from unloaded chunk"))
            .thenWaitUntil(()->h.assertTrue(!nether.hasChunkAt(p),"Source chunk must unload again after return ticket removal"))
            .thenExecute(()->{h.assertTrue(!FamilyRecovery.pending(owner.server,owner.getUUID()) && !nether.getForcedChunks().contains(new net.minecraft.world.level.ChunkPos(p).toLong()),"Completed return must remove transient ticket without persistent chunk forcing");var actual=(Companion)h.getLevel().getEntity(id);actual.discard();owner.discard();nether.getChunkAt(p);nether.setBlock(p.below(),old,3);}).thenSucceed();});
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void recoveryRejectsBlockedLandingAndOversizedPayload(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(5,2,5));var before=mob.position();
        for(int x=-1;x<=5;x++)for(int z=-1;z<=5;z++)if(Math.max(Math.abs(x-2),Math.abs(z-2))>=2)for(int y=2;y<=5;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!FamilyRecovery.move(owner,mob) && mob.position().equals(before),"No safe landing must preserve source entity and position");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{buf.writeVarInt(0);buf.writeVarInt(0);buf.writeByte(0);buf.writeVarInt(33);buf.writeVarInt(33);boolean rejected=false;try{FamilyAlbum.decode(buf);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Recovery payload must reject more than 32 records");}finally{buf.release();}mob.discard();owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void missingRecoveryTimesOutWithoutRecreation(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(5,2,5));UUID id=mob.getUUID();var registry=FamilyRegistry.get(owner.server);var saved=registry.save(new CompoundTag());mob.discard();
        // Model a stale last-position record: disk has no entity. Index alone must never resurrect one.
        var stale=FamilyRegistry.load(saved);var marker=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(h.getLevel());marker.setUUID(id);marker.initialize(owner.getUUID(),stale.member(id).pos());marker.moveTo(stale.member(id).pos().getX()+.5,stale.member(id).pos().getY(),stale.member(id).pos().getZ()+.5,0,0);registry.observe(marker);
        var open=AlbumCommands.open(owner);h.assertTrue(FamilyRecovery.start(owner,id,open.session(),0),"Stale record may trigger a bounded search");
        h.runAfterDelay(110,()->{h.assertTrue(!FamilyRecovery.pending(owner.server,owner.getUUID()) && h.getLevel().getEntity(id)==null,"Timeout must finish search without recreating a live entity");registry.forget(id);owner.discard();h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void returnReasonsKeepOwnershipAndSafety(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(5,2,5));var before=mob.position();
        owner.setOnGround(false);h.assertTrue(FamilyRecovery.moveResult(owner,mob).code()==RecoveryResult.Code.OWNER_UNSAFE && mob.position().equals(before),"Unsafe owner must get actionable reason without movement");owner.setOnGround(true);
        mob.setLeashedTo(owner,false);h.assertTrue(FamilyRecovery.moveResult(owner,mob).code()==RecoveryResult.Code.TETHERED,"Leash must report tethered");mob.dropLeash(false,false);
        var stranger=recoveryOwner(h);h.assertTrue(FamilyRecovery.moveResult(stranger,mob).code()==RecoveryResult.Code.REJECTED,"Foreign player must not receive private detail");stranger.discard();
        for(int x=-1;x<=5;x++)for(int z=-1;z<=5;z++)if(Math.max(Math.abs(x-2),Math.abs(z-2))>=2)for(int y=2;y<=5;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(FamilyRecovery.moveResult(owner,mob).code()==RecoveryResult.Code.NO_SPACE && mob.position().equals(before),"Blocked landing must explain no space and retain source");mob.discard();owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void carrierReasonsDoNotRotateRejectedTickets(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(4,2,4));var carrier=new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get());UUID id=mob.getUUID();SyusyaCarrierItem.capture(mob,owner,carrier);UUID ticket=carrier.getTag().getUUID("Ticket");var ledger=CarrierLedger.get(owner.server);
        owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,carrier);
        h.assertTrue(ledger.reissueResult(owner,id).code()==RecoveryResult.Code.CARRIER_EXISTS && ledger.matches(id,ticket,owner.getUUID()),"Offhand carrier rejection must not rotate token");owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        h.assertTrue(ledger.reissueResult(owner,id).code()==RecoveryResult.Code.NO_SLOT && ledger.matches(id,ticket,owner.getUUID()),"Full inventory must explain slot without side effects");
        var foreign=testOwner(h);h.assertTrue(ledger.reissueResult(foreign,id).code()==RecoveryResult.Code.REJECTED,"Foreign ticket details must remain private");
        var legacy=new CarrierLedger();UUID old=UUID.randomUUID();legacy.capture(old,UUID.randomUUID(),owner.getUUID());h.assertTrue(legacy.reissueResult(owner,old).code()==RecoveryResult.Code.NO_BACKUP,"Legacy ticket must explain missing copy");
        owner.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(DudunkaMod.CARRIER.get()));h.assertTrue(ledger.reissueResult(owner,id).code()==RecoveryResult.Code.CARRIER_RESTORED && !ledger.matches(id,ticket,owner.getUUID()),"Success must report restoration and invalidate old token");owner.discard();foreign.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void recoveryCooldownReportsRemainingTime(GameTestHelper h){
        var owner=recoveryOwner(h);var mob=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(5,2,5));var open=AlbumCommands.open(owner);
        h.assertTrue(FamilyRecovery.startResult(owner,mob.getUUID(),open.session(),0).accepted(),"Initial valid search should start");
        h.assertTrue(FamilyRecovery.startResult(owner,mob.getUUID(),open.session(),0).code()==RecoveryResult.Code.BUSY,"Concurrent search must report busy");
        AlbumCommands.open(owner); // Old job is cancelled on the next server tick.
        h.runAfterDelay(3,()->{var next=AlbumCommands.open(owner);var result=FamilyRecovery.startResult(owner,mob.getUUID(),next.session(),0);h.assertTrue(result.code()==RecoveryResult.Code.COOLDOWN && result.retrySeconds()>0 && result.retrySeconds()<=10 && !FamilyRecovery.pending(owner.server,owner.getUUID()),"Cancelled attempt must release job and retain truthful cooldown");mob.discard();owner.discard();h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void outcomeAndTrailCodecRoundTripAndRejectInvalidValues(GameTestHelper h){
        var snapshot=new FamilyAlbum.Snapshot(0,java.util.List.of(),63,java.util.List.of(),0,5);
        for(var code:RecoveryResult.Code.values()){
            var original=new AlbumCommands.Reply(UUID.randomUUID(),7,new RecoveryResult(code,code==RecoveryResult.Code.COOLDOWN?7:0),snapshot);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{AlbumNetwork.encodeReply(original,buf);h.assertTrue(original.equals(AlbumNetwork.decodeReply(buf)) && buf.readableBytes()==0,"Every reason, retry delay and private trail mask must round-trip");}finally{buf.release();}
        }
        var invalid=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{FamilyAlbum.encode(snapshot,invalid);invalid.writerIndex(invalid.writerIndex()-1);invalid.writeVarInt(FriendStories.MASK+1);boolean rejected=false;try{FamilyAlbum.decode(invalid);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Invalid trail mask must be rejected");}finally{invalid.release();}
        boolean bad=false;try{new RecoveryResult(RecoveryResult.Code.COOLDOWN,11);}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,"Retry payload must be bounded");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void trailReadsOutOfOrderPersistsAndIsPrivate(GameTestHelper h){
        var owner=testOwner(h);var stranger=testOwner(h);var note=new net.minecraft.world.item.ItemStack(DudunkaMod.TRAIL_ITEMS.get(3).get());owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,note);
        DudunkaMod.TRAIL_ITEMS.get(3).get().use(h.getLevel(),owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        var progress=TrailProgress.get(owner.server);h.assertTrue(progress.mask(owner.getUUID())==4 && TrailProgress.unlocked(4)==0 && note.getCount()==1 && !note.hasTag(),"Reading final note early saves it, keeps the reusable item and hides later story");
        h.assertTrue(!TrailNoteItem.read(owner,3),"Duplicate note must be idempotent");TrailNoteItem.read(owner,1);h.assertTrue(TrailProgress.unlocked(progress.mask(owner.getUUID()))==1,"Only contiguous discovered pages unlock");TrailNoteItem.read(owner,2);
        var saved=TrailProgress.load(progress.save(new CompoundTag()));h.assertTrue(saved.mask(owner.getUUID())==7 && saved.mask(stranger.getUUID())==0 && TrailProgress.unlocked(7)==3,"All discovered pages must survive SavedData reload with owner separation");
        h.assertTrue(FamilyAlbum.collect(owner).trailMask()==7 && FamilyAlbum.collect(stranger).trailMask()==0,"Real snapshot must contain only its reader's progress");
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,note.copy());DudunkaMod.TRAIL_ITEMS.get(3).get().use(h.getLevel(),stranger,net.minecraft.world.InteractionHand.MAIN_HAND);h.assertTrue(progress.mask(stranger.getUUID())==4 && progress.mask(owner.getUUID())==7,"Shared note changes only authenticated reader");
        owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_trailpageshidelockedstoryanduseservermodflags")
    public static void trailPagesHideLockedStoryAndUseServerModFlags(GameTestHelper h){
        var locked=EggGuide.page(8,63,4);h.assertTrue(locked.stream().noneMatch(c->c instanceof net.minecraft.network.chat.MutableComponent m && m.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("trail.dudunka.step.2")),"Out-of-order notes must not reveal locked narrative");
        var mvs=EggGuide.page(8,63,7);var vanilla=EggGuide.page(8,62,7);h.assertTrue(mvs.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith(".mvs")) && vanilla.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith(".vanilla")),"Server MVS flag must select truthful search route");
        boolean setting=DudunkaMod.TRAIL_NOTES.get();try{DudunkaMod.TRAIL_NOTES.set(false);h.assertTrue((EggGuide.flags()&EggGuide.NOTES)==0,"Disabled notes must be conveyed by server flag");}finally{DudunkaMod.TRAIL_NOTES.set(setting);}h.succeed();
    }
    private static net.minecraft.world.level.storage.loot.LootContext trailContext(GameTestHelper h,String table,long seed,int coordinate){
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,new net.minecraft.world.phys.Vec3(coordinate*37,70,coordinate*101)).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        return new net.minecraft.world.level.storage.loot.LootContext.Builder(params).withOptionalRandomSeed(seed).withQueriedLootTableId(new net.minecraft.resources.ResourceLocation(table)).create(null);
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_notelootroutespreserveeggrollsandexistingitems")
    public static void noteLootRoutesPreserveEggRollsAndExistingItems(GameTestHelper h){
        String[] tables={"minecraft:chests/village/village_plains_house","minecraft:chests/village/village_desert_house","minecraft:chests/village/village_savanna_house","minecraft:chests/village/village_snowy_house","minecraft:chests/village/village_taiga_house","mvs:houses_common","mvs:houses_flower"};
        boolean setting=DudunkaMod.TRAIL_NOTES.get();
        try{for(String table:tables){int found=0;for(int i=1;i<=128;i++){
            var baseline=trailContext(h,table,i*982451653L,i);var withNote=trailContext(h,table,i*982451653L,i);
            var sample=new TrailLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0],1,1,false);
            sample.apply(new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),withNote);
            h.assertTrue(baseline.getRandom().nextLong()==withNote.getRandom().nextLong(),"Note rolls must not consume loot RNG: "+table);
            var extra=net.minecraftforge.common.ForgeHooks.modifyLoot(new net.minecraft.resources.ResourceLocation(table),new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),trailContext(h,table,i*982451653L,i));
            for(var stack:extra)if(stack.getItem() instanceof TrailNoteItem){found++;if(table.equals("mvs:houses_common"))h.assertTrue(stack.is(DudunkaMod.TRAIL_ITEMS.get(2).get()),"Common MVS table must only add second note");if(table.equals("mvs:houses_flower"))h.assertTrue(stack.is(DudunkaMod.TRAIL_ITEMS.get(3).get()),"Flower table must only add final note");}
        }h.assertTrue(found>0,"Registered note modifier must match verified table: "+table);}
        var unrelated=net.minecraftforge.common.ForgeHooks.modifyLoot(new net.minecraft.resources.ResourceLocation("mvs:empty"),new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),trailContext(h,"mvs:empty",1,1));h.assertTrue(unrelated.isEmpty(),"Unsupported table must not receive notes");
        var modifier=new TrailLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0],1,1,false);var list=new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack>();list.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));modifier.apply(list,trailContext(h,tables[0],1,1));modifier.apply(list,trailContext(h,tables[0],1,1));h.assertTrue(list.size()==2 && list.get(0).getCount()==3,"Repeated note modifier preserves loot and deduplicates its own note");
        DudunkaMod.TRAIL_NOTES.set(false);var disabled=new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack>();modifier.apply(disabled,trailContext(h,tables[0],1,1));h.assertTrue(disabled.isEmpty(),"Disabled notes must not be added");
        var friends=new FriendNoteLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0]);friends.apply(disabled,trailContext(h,tables[0],1,1));h.assertTrue(disabled.isEmpty(),"Same setting disables friend notes as well");
        }finally{DudunkaMod.TRAIL_NOTES.set(setting);}h.succeed();
    }


    private static AlbumCommands.Reply latestAlbumReply(java.util.List<net.minecraft.network.protocol.Packet<?>> packets){
        AlbumCommands.Reply result=null;
        for(var packet:packets)if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom && custom.getIdentifier().toString().equals("dudunka:album")){
            var buf=new net.minecraft.network.FriendlyByteBuf(custom.getData().copy());try{if(buf.readVarInt()==2)result=AlbumNetwork.decodeReply(buf);}finally{buf.release();}
        }
        return result;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void realRecoveryReplyExplainsLegacyAndKeepsSessionPrivate(GameTestHelper h){
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var owner=testOwner(h,null,packets);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        var foreignPackets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var stranger=testOwner(h,null,foreignPackets);
        UUID id=UUID.randomUUID(),token=UUID.randomUUID();var ledger=CarrierLedger.get(owner.server);ledger.capture(id,token,owner.getUUID());var open=AlbumCommands.open(owner);packets.clear();foreignPackets.clear();
        AlbumCommands.recover(owner,new AlbumCommands.RecoveryCommand(UUID.randomUUID(),id,1));h.assertTrue(latestAlbumReply(packets)==null,"Forged session must remain silent");
        var command=new AlbumCommands.RecoveryCommand(open.session(),id,1);AlbumCommands.recover(owner,command);var reply=latestAlbumReply(packets);
        h.assertTrue(reply!=null && reply.result().code()==RecoveryResult.Code.NO_BACKUP && reply.session().equals(open.session()) && reply.request()==1 && foreignPackets.isEmpty() && ledger.matches(id,token,owner.getUUID()),"Private legacy reply: result="+reply+", foreign packets="+foreignPackets.size()+", ticket="+ledger.matches(id,token,owner.getUUID()));
        int sent=packets.size();AlbumCommands.recover(owner,command);h.assertTrue(packets.size()==sent,"Replay must not create extra replies");owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void realTimeoutReplyDoesNotClaimDeathOrCreateEntity(GameTestHelper h){
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var owner=testOwner(h,null,packets);var p=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);owner.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);owner.setOnGround(true);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        var marker=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(h.getLevel());marker.initialize(owner.getUUID(),p);marker.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);UUID id=marker.getUUID();var registry=FamilyRegistry.get(owner.server);registry.observe(marker);var open=AlbumCommands.open(owner);AlbumCommands.recover(owner,new AlbumCommands.RecoveryCommand(open.session(),id,1));
        h.runAfterDelay(110,()->{var reply=latestAlbumReply(packets);h.assertTrue(reply!=null && reply.result().code()==RecoveryResult.Code.NOT_FOUND && !FamilyRecovery.pending(owner.server,owner.getUUID()) && h.getLevel().getEntity(id)==null,"Timed-out real command must release search and send not-found without resurrection");registry.forget(id);owner.discard();h.succeed();});
    }

    private static Companion furnitureFixture(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,Kind kind){
        var home=sceneHome(h,owner,kind);var mob=create(h,kind,owner.getUUID(),new BlockPos(2,2,3));mob.bindHome(home);
        var pos=h.absolutePos(new BlockPos(2,2,4));h.getLevel().setBlock(pos,DudunkaMod.FURNITURE.get(kind).get().defaultBlockState(),3);
        var f=(FurnitureEntity)h.getLevel().getBlockEntity(pos);f.claim(owner.getUUID());h.assertTrue(f.assign(mob),"Fixture must assign matching furniture");mob.bindFurniture(pos);
        h.assertTrue(mob.commandHome(owner),"Complete owned home must accept home mode");
        // No invented welcome and no delay after fixture setup.
        var data=new CompoundTag();mob.addAdditionalSaveData(data);mob.readAdditionalSaveData(data);mob.setOnGround(true);return mob;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureModesAndBothBindingsSurviveSave(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.DUDUNKA);var tag=new CompoundTag();mob.addAdditionalSaveData(tag);
        var restored=DudunkaMod.TYPES.get(mob.kind).get().create(h.getLevel());restored.setUUID(mob.getUUID());restored.readAdditionalSaveData(tag);
        h.assertTrue(restored.atHomeMode() && !restored.staying() && restored.furniturePosition().equals(mob.furniturePosition()) && restored.homeAnchor().equals(mob.homeAnchor()) && restored.activity()==Activity.IDLE,"Persistent mode and furniture/home must restore without transient scene");
        var f=FurnitureScenes.furniture(mob);var saved=f.saveWithoutMetadata();f.load(saved);h.assertTrue(f.assigned(mob),"Block assignment must survive NBT reload");
        owner.moveTo(mob.getX()+20,mob.getY(),mob.getZ(),0,0);h.assertTrue(!new FamilyFollowGoal(mob).canUse() && !FamilyTravel.eligible(mob,owner),"At home mode must not follow or catch up");
        h.assertTrue(mob.commandStay(owner,true) && !mob.atHomeMode() && mob.staying(),"Wait must replace home mode");h.assertTrue(mob.commandStay(owner,false) && !mob.staying() && !mob.atHomeMode(),"Follow must replace wait");
        tag.remove("HomeMode");tag.remove("Furniture");tag.remove("FurnitureDimension");restored.readAdditionalSaveData(tag);h.assertTrue(!restored.atHomeMode() && restored.furniturePosition()==null,"Pre-furniture save must retain default follow mode");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureOwnershipExclusivityAndRelease(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.MARUSYA);var f=FurnitureScenes.furniture(mob);
        var second=create(h,Kind.MARUSYA,owner.getUUID(),new BlockPos(3,2,3));var foreign=create(h,Kind.MARUSYA,UUID.randomUUID(),new BlockPos(3,2,4));var wrong=create(h,Kind.DUDUNKA,owner.getUUID(),new BlockPos(1,2,3));
        h.assertTrue(!f.assign(second) && !f.assign(foreign) && !f.assign(wrong) && !f.clear(foreign.ownerId()) && f.assigned(mob),"Other member, owner and kind cannot steal or clear furniture");
        var stranger=testOwner(h);stranger.setShiftKeyDown(true);stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()),net.minecraft.core.Direction.UP,f.getBlockPos(),false);
        DudunkaMod.ALBUM.get().useOn(new net.minecraft.world.item.context.UseOnContext(stranger,net.minecraft.world.InteractionHand.MAIN_HAND,hit));h.assertTrue(f.assigned(mob),"Actual secondary item use must not release foreign furniture");stranger.discard();
        owner.setShiftKeyDown(true);owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.ALBUM.get()));
        DudunkaMod.ALBUM.get().useOn(new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(f.memberId()==null && mob.furniturePosition()==null && f.assign(second),"Actual Shift+item use must free the block and companion binding");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureSafetyAndUnloadedStatus(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.SYUSYA);var pos=mob.furniturePosition();
        h.assertTrue(FurnitureScenes.info(mob).state()==FurnitureScenes.State.READY && FurnitureScenes.select(mob)!=null,"Reachable covered furniture must be ready");
        h.getLevel().setBlock(pos.north(),Blocks.STONE.defaultBlockState(),3);h.assertTrue(FurnitureScenes.select(mob)==null && FurnitureScenes.info(mob).state()==FurnitureScenes.State.BLOCKED,"Occupied approach must reject scene");
        h.getLevel().setBlock(pos.north(),Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.assertTrue(FurnitureScenes.info(mob).state()==FurnitureScenes.State.MISSING,"Destroyed furniture must be reported");
        var tag=new CompoundTag();mob.addAdditionalSaveData(tag);var unloaded=new BlockPos(20000000,80,20000000);tag.putLong("Furniture",unloaded.asLong());mob.readAdditionalSaveData(tag);
        h.assertTrue(FurnitureScenes.info(mob).state()==FurnitureScenes.State.UNLOADED && !h.getLevel().hasChunkAt(unloaded),"Status must never load distant furniture");tag.putString("FurnitureDimension","minecraft:the_nether");mob.readAdditionalSaveData(tag);h.assertTrue(FurnitureScenes.info(mob).state()==FurnitureScenes.State.OTHER_DIMENSION,"Foreign dimension cannot resolve local block");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureCommandAuthenticationAndPrivateReply(GameTestHelper h){
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var foreignPackets=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();var owner=testOwner(h,null,packets);var stranger=testOwner(h,null,foreignPackets);
        var mob=furnitureFixture(h,owner,Kind.DUDUNKA);mob.clearFurniture();var open=commandAlbum(owner);var command=new AlbumCommands.FurnitureCommand(open.session(),mob.getUUID(),false,1);packets.clear();foreignPackets.clear();
        h.assertTrue(AlbumCommands.executeFurniture(null,command)==null && AlbumCommands.executeFurniture(stranger,command)==null,"Unauthenticated or foreign session must remain silent");
        AlbumNetwork.handleFurniture(owner,command);var reply=latestAlbumReply(packets);h.assertTrue(reply!=null && reply.accepted() && reply.result().code()==RecoveryResult.Code.FURNITURE_ASSIGNED && mob.furniturePosition()!=null && foreignPackets.isEmpty(),"Actual private handler must assign and acknowledge only sender");
        h.assertTrue(AlbumCommands.executeFurniture(owner,command)==null,"Replay cannot mutate assignment");var next=commandAlbum(owner);owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        h.assertTrue(!AlbumCommands.executeFurniture(owner,new AlbumCommands.FurnitureCommand(next.session(),mob.getUUID(),true,1)).accepted() && mob.furniturePosition()!=null,"Missing held album must not release furniture");owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureAndHomeModePacketsRoundTrip(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.MARUSYA);var open=commandAlbum(owner);
        var command=new AlbumCommands.Command(open.session(),mob.getUUID(),false,1,true);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{AlbumNetwork.encodeCommand(command,buf);h.assertTrue(command.equals(AlbumNetwork.decodeCommand(buf)),"Explicit home mode command must round-trip");buf.clear();var f=new AlbumCommands.FurnitureCommand(open.session(),mob.getUUID(),true,2);AlbumNetwork.encodeFurniture(f,buf);h.assertTrue(f.equals(AlbumNetwork.decodeFurniture(buf)),"Furniture command must round-trip");buf.clear();FamilyAlbum.encode(open.snapshot(),buf);h.assertTrue(open.snapshot().equals(FamilyAlbum.decode(buf)),"Furniture position/status, activity and mode must round-trip");}finally{buf.release();}
        h.assertTrue(AlbumCommands.execute(owner,command).accepted() && mob.atHomeMode(),"Authenticated home mode command must succeed");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureGoalWaitsForArrivalAndCancelsImmediately(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.DUDUNKA);var goal=new FurnitureGoal(mob);var scene=FurnitureScenes.select(mob);
        mob.moveTo(mob.getX()-1,mob.getY(),mob.getZ(),0,0);
        h.assertTrue(scene!=null && goal.canUse(),"Drawing table must be selectable");goal.start();goal.tick();
        h.assertTrue(mob.activity()==Activity.CURIOUS && mob.homeSceneTarget()!=null,"Approaching must not display the drawing activity");h.getLevel().setBlock(mob.furniturePosition(),Blocks.AIR.defaultBlockState(),3);goal.tick();goal.tick();
        h.assertTrue(mob.homeSceneTarget()==null && mob.activity()==Activity.IDLE,"Destroyed furniture must release the actual running goal even on its final tick");
        h.assertTrue(FurnitureScenes.select(mob)==null && !FurnitureScenes.valid(mob,scene),"Destroying the table must invalidate its scene immediately");owner.discard();h.succeed();
    }
    private static void liveFurniture(GameTestHelper h,Kind kind){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,kind);var pos=mob.furniturePosition();var before=new CompoundTag();mob.addAdditionalSaveData(before);
        // Adult dimensions must fit the actual collision shapes.
        before.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);mob.readAdditionalSaveData(before);mob.setNoAi(false);
        var seen=java.util.EnumSet.noneOf(Activity.class);var target=FurnitureScenes.select(mob);h.assertTrue(target!=null,"Adult must fit furniture collision geometry: "+kind);
        h.onEachTick(()->{if(mob.isAlive())seen.add(mob.activity());});
        h.runAfterDelay(360,()->{
            var expected=switch(kind){case DUDUNKA->java.util.Set.of(Activity.DRAW,Activity.SHOW_DRAWING);case MARUSYA->java.util.Set.of(Activity.STRETCH,Activity.SCRATCH,Activity.CURL);case SYUSYA->java.util.Set.of(Activity.RETREAT,Activity.SLEEP,Activity.PEEK,Activity.NIBBLE);};
            h.assertTrue(seen.containsAll(expected),"Live AI must reach furniture and perform all phases for "+kind+": "+seen+" at "+mob.position());
            var after=new CompoundTag();mob.addAdditionalSaveData(after);h.assertTrue(mob.trust()==before.getInt("Trust") && mob.homeAnchor()!=null && pos.equals(mob.furniturePosition()) && after.getBoolean("HomeMode"),"Activity must preserve care, both assignments and mode");
            mob.commandStay(owner,true);
        });
        h.runAfterDelay(365,()->{h.assertTrue(mob.activity()==Activity.SIT && mob.homeSceneTarget()==null,"Wait must immediately end live furniture activity");owner.discard();h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=420)
    public static void dudunkaReallyDrawsAtAssignedTable(GameTestHelper h){liveFurniture(h,Kind.DUDUNKA);}
    @GameTest(template="empty",timeoutTicks=420)
    public static void marusyaReallyUsesScratchingBed(GameTestHelper h){liveFurniture(h,Kind.MARUSYA);}
    @GameTest(template="empty",timeoutTicks=420)
    public static void syusyaReallyEntersAndLeavesLeafHouse(GameTestHelper h){liveFurniture(h,Kind.SYUSYA);}
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureRotationsFitAllAdults(GameTestHelper h){
        var owner=testOwner(h);
        for(Kind kind:Kind.values()){
            var mob=furnitureFixture(h,owner,kind);var data=new CompoundTag();mob.addAdditionalSaveData(data);data.putInt("GrowthTicks",DudunkaMod.GROWTH_SECONDS.get()*40);mob.readAdditionalSaveData(data);var p=mob.furniturePosition();
            for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL){
                h.getLevel().setBlock(p,DudunkaMod.FURNITURE.get(kind).get().defaultBlockState().setValue(FurnitureBlock.FACING,dir),3);
                for(var adjacent:net.minecraft.core.Direction.Plane.HORIZONTAL){var q=p.relative(adjacent);h.getLevel().setBlock(q,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(q.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(q.above(2),Blocks.STONE.defaultBlockState(),3);}
                h.assertTrue(FurnitureScenes.select(mob)!=null,"Every rotated model must fit its adult and have a clear approach: "+kind+" "+dir);
            }
            mob.discard();
        }
        owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void furnitureForeignBreakAndHomeScenePreemption(GameTestHelper h){
        var owner=testOwner(h);var stranger=testOwner(h);var mob=furnitureFixture(h,owner,Kind.DUDUNKA);var p=mob.furniturePosition();
        var denied=new net.minecraftforge.event.level.BlockEvent.BreakEvent(h.getLevel(),p,h.getLevel().getBlockState(p),stranger);FamilyProtection.onBreak(denied);h.assertTrue(denied.isCanceled(),"Foreign survival player must not break assigned furniture");
        var allowed=new net.minecraftforge.event.level.BlockEvent.BreakEvent(h.getLevel(),p,h.getLevel().getBlockState(p),owner);FamilyProtection.onBreak(allowed);h.assertTrue(!allowed.isCanceled(),"Furniture owner may break it");
        var flower=h.absolutePos(new BlockPos(4,2,3));var old=new HomeScenes.Scene(mob.homeAnchor(),flower,mob.position(),Activity.SIT);
        h.assertTrue(!HomeScenes.valid(mob,old) && HomeTogetherScenes.select(mob)==null && CampfireScenes.select(mob)==null,"Explicit furniture mode must preempt former home/camp scenes");owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void friendsNotesRemainIndependentAndPrivate(GameTestHelper h){
        var owner=testOwner(h);var stranger=testOwner(h);var progress=TrailProgress.get(owner.server);
        for(int note:new int[]{6,9,3}){owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.TRAIL_ITEMS.get(note).get()));DudunkaMod.TRAIL_ITEMS.get(note).get().use(h.getLevel(),owner,net.minecraft.world.InteractionHand.MAIN_HAND);}
        h.assertTrue(progress.mask(owner.getUUID())==292 && FriendStories.unlocked(292,Kind.MARUSYA)==0 && FriendStories.unlocked(292,Kind.SYUSYA)==0,"Out-of-order last pages remain saved and independently locked");
        for(int note:new int[]{4,5,7,8,1,2})TrailNoteItem.read(owner,note);
        h.assertTrue(progress.mask(owner.getUUID())==511 && !TrailNoteItem.read(owner,6) && progress.mask(stranger.getUUID())==0,"All nine notes are private and repeat reading is idempotent");
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(DudunkaMod.TRAIL_ITEMS.get(6).get()));DudunkaMod.TRAIL_ITEMS.get(6).get().use(h.getLevel(),stranger,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(progress.mask(stranger.getUUID())==32 && FamilyAlbum.collect(owner).trailMask()==511 && FamilyAlbum.collect(stranger).trailMask()==32,"Sharing a note only changes the authenticated reader");
        var restored=TrailProgress.load(progress.save(new CompoundTag()));h.assertTrue(restored.mask(owner.getUUID())==511 && restored.mask(stranger.getUUID())==32,"Nine-bit progress survives NBT save/load");owner.discard();stranger.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void friendStoriesHideTailsAndKeepOriginalTrail(GameTestHelper h){
        h.assertTrue(EggGuide.PAGES==18,"Six guide pages plus three four-page stories");
        for(Kind kind:new Kind[]{Kind.MARUSYA,Kind.SYUSYA}){
            int first=kind==Kind.MARUSYA?10:14,lastBit=1<<(FriendStories.offset(kind)+2);
            var locked=EggGuide.page(first+3,63,lastBit);
            h.assertTrue(locked.stream().noneMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().contains(".step.3")),"Found last note cannot reveal locked story: "+kind);
            var full=EggGuide.page(first+3,63,511);var vanilla=EggGuide.page(first+3,62,511);
            h.assertTrue(full.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith(".mvs")) && vanilla.stream().noneMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().endsWith(".mvs")),"Server flags gate optional places");
        }
        h.assertTrue(TrailProgress.unlocked(7)==3 && FriendStories.unlocked(7,Kind.MARUSYA)==0 && FriendStories.unlocked(7,Kind.SYUSYA)==0,"Old three-bit progress opens only original story");
        for(int page=0;page<18;page++)h.assertTrue(!EggGuide.page(page,63,511).isEmpty(),"Every guide/story page must exist");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void registeredFriendNoteLootPreservesRandomAndResources(GameTestHelper h){
        String[] tables={"minecraft:chests/village/village_plains_house","minecraft:chests/village/village_desert_house","minecraft:chests/village/village_savanna_house","minecraft:chests/village/village_snowy_house","minecraft:chests/village/village_taiga_house","minecraft:chests/abandoned_mineshaft","minecraft:chests/woodland_mansion","minecraft:chests/simple_dungeon","mvs:abandoned","mvs:swamps","betterdungeons:skeleton_dungeon/chests/common","betterdungeons:zombie_dungeon/chests/common","betterdungeons:small_dungeon/chests/loot_piles"};
        var modifier=new FriendNoteLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0]);
        for(String table:tables){var found=new java.util.HashSet<Integer>();for(int i=1;i<=128;i++){
            var baseline=trailContext(h,table,i,i);var ctx=trailContext(h,table,i,i);var loot=new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack>();loot.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));modifier.apply(loot,ctx);
            h.assertTrue(baseline.getRandom().nextLong()==ctx.getRandom().nextLong() && loot.get(0).getCount()==3,"Stories preserve vanilla/egg RNG and existing loot");int size=loot.size();modifier.apply(loot,ctx);h.assertTrue(loot.size()==size,"Repeated modifier never duplicates its notes");
            var actual=net.minecraftforge.common.ForgeHooks.modifyLoot(new net.minecraft.resources.ResourceLocation(table),new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),trailContext(h,table,i,i));
            for(int note=4;note<=9;note++){final int n=note;if(actual.stream().anyMatch(s->s.is(DudunkaMod.TRAIL_ITEMS.get(n).get()))){h.assertTrue(FriendNoteLootModifier.supports(table,n),"No crossed friend route");found.add(n);}}
        }for(int note=4;note<=9;note++)if(FriendNoteLootModifier.supports(table,note))h.assertTrue(found.contains(note),"Registered table must produce every supported new page: "+table+" "+note);}
        var unrelated=modifier.apply(new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),trailContext(h,"mvs:empty",1,1));h.assertTrue(unrelated.isEmpty(),"Unsupported tables remain unchanged");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void characterVariantsCyclePersistAndMatchTable(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.DUDUNKA);var goal=new FurnitureGoal(mob);h.assertTrue(goal.canUse(),"Fixture must select table");goal.start();
        h.assertTrue(mob.characterVariant()==0 && h.getLevel().getBlockState(mob.furniturePosition()).getValue(FurnitureBlock.PICTURE)==0,"First drawing matches table");goal.stop();
        h.assertTrue(mob.beginFurnitureVariant()==1 && mob.beginFurnitureVariant()==2 && mob.beginFurnitureVariant()==0,"Variants change only per start and wrap after three");var data=new CompoundTag();mob.addAdditionalSaveData(data);var loaded=DudunkaMod.TYPES.get(mob.kind).get().create(h.getLevel());loaded.readAdditionalSaveData(data);
        h.assertTrue(loaded.characterVariant()==0 && loaded.beginFurnitureVariant()==1 && loaded.trust()==mob.trust(),"Variant and next turn survive serialization without care changes");data.putInt("CharacterVariant",999);data.putInt("NextCharacterVariant",-20);loaded.readAdditionalSaveData(data);h.assertTrue(loaded.characterVariant()==2 && loaded.beginFurnitureVariant()==0,"Invalid saved indices are bounded");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void syusyaFeelersReactWithoutEatingOrChangingMode(GameTestHelper h){
        var owner=testOwner(h);var mob=create(h,Kind.SYUSYA,owner.getUUID(),new BlockPos(2,2,2));owner.moveTo(mob.getX()+1,mob.getY(),mob.getZ(),0,0);
        owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LEAVES,3));int trust=mob.trust();mob.updateFeelers();
        h.assertTrue(mob.feelers()==CharacterMoments.Feelers.INTERESTED && owner.getOffhandItem().getCount()==3 && mob.trust()==trust && mob.activity()==Activity.IDLE,"Offhand food interests feelers without consuming food or changing care/activity");
        owner.moveTo(mob.getX()+10,mob.getY(),mob.getZ(),0,0);mob.updateFeelers();h.assertTrue(mob.feelers()==CharacterMoments.Feelers.CALM,"Distant owner's food must not trigger reaction");
        for(Activity activity:new Activity[]{Activity.RETREAT,Activity.SLEEP}){mob.setActivity(activity);mob.updateFeelers();h.assertTrue(mob.feelers()==CharacterMoments.Feelers.RETRACTED,"Rest/hiding retracts feelers");}
        mob.setActivity(Activity.PEEK);mob.updateFeelers();h.assertTrue(mob.feelers()==CharacterMoments.Feelers.INTERESTED,"Peeking explores with feelers");
        var saved=new CompoundTag();mob.addAdditionalSaveData(saved);mob.readAdditionalSaveData(saved);h.assertTrue(mob.feelers()==CharacterMoments.Feelers.CALM,"Transient mood must not invent a saved reaction");owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void characterSnapshotAndNineNotesCodec(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.SYUSYA);mob.beginFurnitureVariant();mob.beginFurnitureVariant();mob.setActivity(Activity.NIBBLE);mob.updateFeelers();for(int n=1;n<=9;n++)TrailNoteItem.read(owner,n);
        var snapshot=FamilyAlbum.collect(owner);var entry=snapshot.entries().get(0);h.assertTrue(entry.variant()==1 && entry.feelers()==CharacterMoments.Feelers.INTERESTED && snapshot.trailMask()==511,"Private snapshot reflects actual character and nine pages");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{FamilyAlbum.encode(snapshot,buf);h.assertTrue(snapshot.equals(FamilyAlbum.decode(buf)) && buf.readableBytes()==0,"Nine-bit progress, mood and variant round-trip");buf.clear();FamilyAlbum.encode(snapshot,buf);buf.writerIndex(buf.writerIndex()-2);buf.writeVarInt(512);boolean rejected=false;try{FamilyAlbum.decode(buf);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"More than nine bits rejected");}finally{buf.release();}owner.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=1600,batch="isolated_livedudunkashowsallthreematchingdrawings")
    public static void liveDudunkaShowsAllThreeMatchingDrawings(GameTestHelper h){
        var owner=testOwner(h);var mob=furnitureFixture(h,owner,Kind.DUDUNKA);mob.setNoAi(false);var seen=new java.util.HashSet<Integer>();
        h.onEachTick(()->{if(mob.activity()==Activity.DRAW || mob.activity()==Activity.SHOW_DRAWING){h.assertTrue(h.getLevel().getBlockState(mob.furniturePosition()).getValue(FurnitureBlock.PICTURE)==mob.characterVariant(),"Live table must match showing hand");if(mob.activity()==Activity.SHOW_DRAWING)seen.add(mob.characterVariant());}});
        h.succeedWhen(()->{h.assertTrue(seen.size()==3,"All three drawings must be shown in successive live activities: seen="+seen+", activity="+mob.activity()+", variant="+mob.characterVariant()+", pos="+mob.position()+", ready="+FurnitureScenes.info(mob));owner.discard();});
    }
}
