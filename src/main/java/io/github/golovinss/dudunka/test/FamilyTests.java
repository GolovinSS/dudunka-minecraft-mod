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

}
