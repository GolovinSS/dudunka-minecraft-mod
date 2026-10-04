package io.github.golovinss.dudunka;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Local, throttled decisions. Never reads blocks in an unloaded chunk. */
public final class FamilyBehaviorGoal extends Goal {
    private final Companion mob;
    private Activity task = Activity.IDLE;
    private Entity targetEntity;
    private BlockPos targetBlock,approachBlock;
    private long nextDecision, until;
    private int waitingTicks;

    public FamilyBehaviorGoal(Companion mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canUse() {
        long now = mob.level().getGameTime();
        if (mob.staying() || mob.ownerId() == null || now < nextDecision) return false;
        nextDecision = now + 20;
        task = Activity.IDLE;
        targetEntity = null;
        targetBlock = null;
        approachBlock = null;

        if (mob.kind == Kind.MARUSYA) {
            var threats = mob.level().getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(10), Entity::isAlive);
            if (!threats.isEmpty()) {
                targetEntity = threats.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElseThrow();
                task = Activity.ALERT;
                return true;
            }
        }
        if(HomeAtmosphereScenes.select(mob)!=null)return false;
        if(mob.atHomeMode())return false;
        boolean walking=WalkScenes.current(mob)!=null;
        if (!walking && mob.kind != Kind.SYUSYA) {
            var snails = mob.level().getEntitiesOfClass(Companion.class, mob.getBoundingBox().inflate(12),
                    other -> other.kind == Kind.SYUSYA && mob.sameFamily(other) && !other.staying());
            var snail = snails.stream().filter(other -> mob.distanceToSqr(other) > 16)
                    .min(Comparator.comparingDouble(mob::distanceToSqr));
            if (snail.isPresent()) {
                targetEntity = snail.get();
                task = Activity.WAIT_FOR_SYUSYA;
                return true;
            }
        }

        if (mob.level().isRainingAt(mob.blockPosition())) {
            targetBlock = findBlock(6, p -> safeStanding(p) && !mob.level().canSeeSky(p));
            if (targetBlock != null) { task = Activity.SIT; return true; }
        }
        if(WalkScenes.select(mob)!=null)return false;
        if(CampfireScenes.select(mob)!=null)return false; // Explicit family rest yields only after urgent/weather tasks.
        if(mob.homeWelcomeRunning() || HomeTogetherScenes.select(mob)!=null || HomeScenes.select(mob)!=null)return false;
        if (mob.level().isNight()) {
            // A marked cat uses the reserved scene; do not bypass its cooldown or occupied-bed checks.
            if(mob.kind==Kind.MARUSYA && mob.homeAnchor()!=null)return false;
            if (mob.kind == Kind.MARUSYA && mob.homeAnchor()==null) {
                targetBlock = findBlock(5, p -> mob.level().getBlockState(p).is(BlockTags.BEDS)
                        || mob.level().getBlockState(p).getBlock() instanceof ChestBlock);
                if (targetBlock != null) { targetBlock = targetBlock.above(); task = Activity.SLEEP; return true; }
            }
            BlockPos home = mob.homePosition();
            if (home != null && !mob.level().canSeeSky(home) && safeStanding(home)) {
                targetBlock = home;
                task = Activity.SLEEP;
                return true;
            }
        }

        if (mob.kind == Kind.DUDUNKA) {
            if(mob.openChestTarget()!=null)return false; // Let the reactive chest goal run after safety/family tasks.
            var apples = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(5),
                    item -> item.isAlive() && item.getItem().is(Items.APPLE) && !item.hasPickUpDelay());
            if (!apples.isEmpty()) {
                targetEntity = apples.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElseThrow();
                task = Activity.CURIOUS;
                return true;
            }
            targetBlock=findBlock(6,p->mob.level().getBlockState(p).is(Blocks.CAKE));
            if(targetBlock!=null) {
                approachBlock=InterestTargets.approach(mob,targetBlock);
                if(approachBlock!=null){task=Activity.CAKE_RUN;return true;}
                targetBlock=null;
            }
            if (mob.getRandom().nextInt(4) == 0) {
                targetBlock = findBlock(4, p -> {
                    BlockState state = mob.level().getBlockState(p);
                    return state.is(BlockTags.FLOWERS) || state.getBlock() instanceof ChestBlock;
                });
                if (targetBlock != null) { task = Activity.CURIOUS; return true; }
            }
            var owner = mob.ownerPlayer();
            if (owner != null && mob.trust() >= 25 && mob.distanceToSqr(owner) < 36 && mob.getRandom().nextInt(10) == 0) {
                targetEntity = owner; task = Activity.WAVE; return true;
            }
            if (mob.getRandom().nextInt(15) == 0) { task = Activity.ADJUST_GLASSES; return true; }
        }
        if (mob.kind == Kind.MARUSYA && mob.getRandom().nextInt(4) == 0) {
            var fish = mob.level().getEntitiesOfClass(AbstractFish.class, mob.getBoundingBox().inflate(6), Entity::isAlive);
            if (!fish.isEmpty()) { targetEntity = fish.get(0); task = Activity.CURIOUS; return true; }
        }
        if (mob.kind == Kind.SYUSYA && mob.getRandom().nextInt(8) == 0) {
            targetBlock = findBlock(6, p -> mob.distanceToSqr(Vec3.atCenterOf(p)) > 4 && (
                    mob.level().getBlockState(p).is(BlockTags.FLOWERS)
                    || mob.level().getBlockState(p).is(Blocks.FLOWERING_AZALEA)
                    || mob.level().getBlockState(p).is(Blocks.SPORE_BLOSSOM)
                    || mob.level().getBlockState(p).is(Blocks.MOSS_BLOCK)));
            if (targetBlock != null) { task = Activity.SEEK_PLANT; return true; }
        }
        return false;
    }

    @Override public void start() {
        until = mob.level().getGameTime() + (task == Activity.WAIT_FOR_SYUSYA ? 240 : task == Activity.SLEEP ? 400 : 100);
        waitingTicks = 0;
        mob.getNavigation().stop();
        if (task == Activity.ALERT && mob.ownerPlayer() != null)
            mob.ownerPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dudunka.marusya_alert"), true);
        // Walking toward a bed or shelter uses the normal walk pose until arrival.
        mob.setActivity(targetBlock != null && (task == Activity.SIT || task == Activity.SLEEP) ? Activity.CURIOUS : task);
    }

    @Override public boolean canContinueToUse() {
        if (mob.staying() || mob.atHomeMode() && task!=Activity.ALERT || mob.level().getGameTime() >= until || (targetEntity != null && (!targetEntity.isAlive() || targetEntity.level() != mob.level()))) return false;
        if(mob.homeWelcomePending() && (task==Activity.CURIOUS || task==Activity.CAKE_RUN || task==Activity.WAVE
                || task==Activity.ADJUST_GLASSES || task==Activity.SLEEP))return false;
        if((task==Activity.CURIOUS || task==Activity.CAKE_RUN || task==Activity.WAVE || task==Activity.ADJUST_GLASSES || task==Activity.SLEEP) && CampfireScenes.select(mob)!=null)return false;
        if(task==Activity.CAKE_RUN && (targetBlock==null || !mob.level().hasChunkAt(targetBlock)
                || !mob.level().getBlockState(targetBlock).is(Blocks.CAKE) || approachBlock==null
                || !HomeRules.safeStanding(mob.level(),approachBlock,mob)))return false;
        if(HomeTogetherScenes.select(mob)!=null && (task==Activity.CURIOUS || task==Activity.CAKE_RUN || task==Activity.WAVE || task==Activity.ADJUST_GLASSES || task==Activity.SLEEP))return false;
        if(mob.openChestTarget()!=null && (task==Activity.CURIOUS || task==Activity.CAKE_RUN || task==Activity.WAVE || task==Activity.ADJUST_GLASSES))return false;
        if (mob.kind == Kind.MARUSYA && task != Activity.ALERT && mob.tickCount % 20 == 0
                && !mob.level().getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(10), Entity::isAlive).isEmpty()) return false;
        if(task!=Activity.ALERT && HomeAtmosphereScenes.current(mob)!=null)return false;
        if(task!=Activity.ALERT && task!=Activity.SIT && WalkScenes.current(mob)!=null)return false;
        if (task == Activity.WAIT_FOR_SYUSYA) return targetEntity instanceof Companion snail && mob.sameFamily(snail)
                && !snail.staying() && mob.distanceToSqr(snail) > 6.25 && mob.distanceToSqr(snail) < 225;
        if (task == Activity.ALERT) return mob.distanceToSqr(targetEntity) < 144;
        if (targetBlock != null && !mob.level().hasChunkAt(targetBlock)) return false;
        if (task == Activity.SLEEP) return mob.level().isNight();
        if (task == Activity.SIT) return mob.level().isRaining();
        return true;
    }

    @Override public void tick() {
        if(!canContinueToUse()){stop();return;}
        if(task==Activity.CAKE_RUN && !canContinueToUse()) {
            until=0;targetBlock=null;approachBlock=null;mob.getNavigation().stop();mob.setActivity(Activity.IDLE);return;
        }
        if (task == Activity.WAIT_FOR_SYUSYA || task == Activity.ALERT || task == Activity.WAVE || task == Activity.ADJUST_GLASSES
                || (task == Activity.CURIOUS && mob.kind == Kind.MARUSYA)) {
            mob.getNavigation().stop();
            if (targetEntity != null) mob.getLookControl().setLookAt(targetEntity, 15, 25);
            if (task == Activity.WAIT_FOR_SYUSYA && ++waitingTicks == 120) FamilyAchievements.award(mob, "wait_for_syusya");
            return;
        }
        // GoalSelector can tick a running goal again before its next continuation check.
        // Eating an apple clears the entity target immediately; there is no destination left.
        if (targetEntity == null && targetBlock == null) {
            until = 0;
            mob.setActivity(Activity.IDLE);
            return;
        }
        Vec3 target = approachBlock!=null?Vec3.atBottomCenterOf(approachBlock):targetEntity == null ? Vec3.atCenterOf(targetBlock) : targetEntity.position();
        if (targetEntity != null) mob.getLookControl().setLookAt(targetEntity, 15, 25);
        if(targetBlock!=null){Vec3 look=Vec3.atCenterOf(targetBlock);mob.getLookControl().setLookAt(look.x,look.y,look.z,15,25);}
        boolean resting = targetBlock != null && (task == Activity.SLEEP || task == Activity.SIT);
        if (task == Activity.SLEEP && mob.kind == Kind.MARUSYA && targetBlock != null) {
            BlockPos support = targetBlock.below();
            var shape = mob.level().getBlockState(support).getCollisionShape(mob.level(), support);
            if (!shape.isEmpty()) target = new Vec3(targetBlock.getX() + .5, support.getY() + shape.bounds().maxY, targetBlock.getZ() + .5);
        }
        double distance = mob.distanceToSqr(target);
        if (distance > (resting || task==Activity.CAKE_RUN ? .36 : 2.25)) {
            if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(target.x, target.y, target.z, task==Activity.CAKE_RUN?1.35:1);
        } else {
            mob.getNavigation().stop();
            mob.setActivity(task==Activity.CAKE_RUN?Activity.CURIOUS:task);
            if (task == Activity.SLEEP && mob.kind == Kind.MARUSYA && targetBlock != null
                    && mob.level().getBlockState(targetBlock.below()).is(BlockTags.BEDS)) FamilyAchievements.award(mob, "occupied_bed");
            if (targetEntity instanceof ItemEntity item && mob.kind == Kind.DUDUNKA && takeApple(mob, item)) {
                targetEntity = null;
                until = 0;
                // Eat one fallen apple, then take a few steps away. No inventory or resource production.
                double angle = mob.getRandom().nextDouble() * Math.PI * 2;
                mob.getNavigation().moveTo(mob.getX() + Math.cos(angle) * 3, mob.getY(), mob.getZ() + Math.sin(angle) * 3, 1.2);
            }
        }
    }

    @Override public void stop() {
        mob.setActivity(Activity.IDLE);
        if (!(task == Activity.CURIOUS && targetEntity == null && targetBlock == null)) mob.getNavigation().stop();
        nextDecision = mob.level().getGameTime() + (task == Activity.WAVE || task == Activity.ADJUST_GLASSES ? 400 : 60);
        targetEntity = null;
        targetBlock = null;
        approachBlock = null;
    }

    /** Server-only, revalidates the item immediately before consuming it. */
    public static boolean takeApple(Companion mob, ItemEntity item) {
        if (mob.level().isClientSide || mob.kind != Kind.DUDUNKA || mob.ownerId() == null || !item.isAlive()
                || mob.distanceToSqr(item) > 2.25 || !item.getItem().is(Items.APPLE) || item.hasPickUpDelay()) return false;
        var stack = item.getItem().copy();
        stack.shrink(1);
        if (stack.isEmpty()) item.discard(); else item.setItem(stack);
        mob.heal(1);
        return true;
    }

    private BlockPos findBlock(int radius, Predicate<BlockPos> test) {
        BlockPos origin = mob.blockPosition(), result = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!mob.level().hasChunkAt(p) || !mob.level().hasChunkAt(p.below()) || !test.test(p)) continue;
            double distance = origin.distSqr(p);
            if (distance < nearest) { nearest = distance; result = p.immutable(); }
        }
        return result;
    }
    private boolean safeStanding(BlockPos p) {
        var floor = mob.level().getBlockState(p.below());
        if (!floor.isSolid() || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || !mob.level().getFluidState(p).isEmpty()) return false;
        var box = mob.getBoundingBox().move(p.getX() + .5 - mob.getX(), p.getY() - mob.getY(), p.getZ() + .5 - mob.getZ());
        return mob.level().noCollision(mob, box);
    }
}
