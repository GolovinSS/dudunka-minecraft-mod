package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.UUID;

public class Companion extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STAGE=SynchedEntityData.defineId(Companion.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STAY=SynchedEntityData.defineId(Companion.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(Companion.class, EntityDataSerializers.INT);
    public final Kind kind;
    private UUID owner;
    private BlockPos home;
    private String homeDimension;
    private boolean homeIsMarker;
    private int growthTicks,trust,feedCooldown,recoveryTicks;
    public Companion(EntityType<? extends Companion> type,Level l,Kind kind) {
        super(type,l); this.kind=kind; setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes(Kind k) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,k.speed).add(Attributes.FOLLOW_RANGE,24);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(STAGE,0); entityData.define(STAY,false); entityData.define(ACTIVITY, Activity.IDLE.ordinal()); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new FamilyBehaviorGoal(this));
        goalSelector.addGoal(2,new FollowOwner(this));
        goalSelector.addGoal(4,new WaterAvoidingRandomStrollGoal(this, .8) {
            @Override public boolean canUse() { return !staying()&&super.canUse(); }
        });
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
    }
    public void initialize(UUID owner,BlockPos home) { this.owner=owner; this.home=home.immutable(); this.homeDimension=level().dimension().location().toString(); setCustomName(Component.translatable("entity.dudunka."+kind.id)); }
    public int stage() { return entityData.get(STAGE); }
    public boolean staying() { return entityData.get(STAY); }
    public UUID ownerId() { return owner; }
    public Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }
    public boolean sameFamily(Companion other) { return owner != null && owner.equals(other.ownerId()); }
    public int trust() { return trust; }
    public void bindHome(BlockPos anchor) { home=anchor.immutable(); homeDimension=level().dimension().location().toString(); homeIsMarker=true; }
    public BlockPos homePosition() {
        if(home==null || !level().dimension().location().toString().equals(homeDimension) || !level().hasChunkAt(home)) return null;
        if(homeIsMarker && (!(level().getBlockEntity(home) instanceof HomeMarkerEntity marker) || !marker.validFor(this))) return null;
        return HomeRules.nearbyStanding(level(),home,this);
    }
    public Activity activity() {
        if (staying()) return Activity.SIT;
        return Activity.values()[Math.max(0, Math.min(Activity.values().length - 1, entityData.get(ACTIVITY)))];
    }
    public void setActivity(Activity activity) { entityData.set(ACTIVITY, activity.ordinal()); }
    public boolean willingToFollow() {
        // A stable ten-second interval avoids re-rolling every AI tick.
        return kind != Kind.MARUSYA || Math.floorMod(level().getGameTime() / 200 + getUUID().hashCode(), 5) != 0;
    }
    public float growthScale() { return stage()==0?.55f:stage()==1?.78f:1f; }
    @Override public EntityDimensions getDimensions(Pose p) { return super.getDimensions(p).scale(growthScale()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if(STAGE.equals(key))refreshDimensions(); }
    @Override public void aiStep() {
        super.aiStep();
        if(level().isClientSide) return;
        if(feedCooldown>0)feedCooldown--;
        if(recoveryTicks>0)recoveryTicks--;
        if(staying())getNavigation().stop();
        if(stage()<2) { growthTicks++; updateStage(); }
        if(owner != null && tickCount % 200 == 0) FamilyAchievements.checkFamily(this);
        // Home radius bounds idle exploration without forcing chunk loads.
        Player p=owner==null?null:level().getPlayerByUUID(owner);
        if(activity()==Activity.IDLE && !staying() && (p==null||distanceToSqr(p)>400) && tickCount%100==0) {
            BlockPos target=homePosition();
            if(target!=null && blockPosition().distSqr(target)>144)getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,1);
        }
    }
    private void updateStage() {
        int next=Math.min(2,growthTicks/(DudunkaMod.GROWTH_SECONDS.get()*20));
        if(next!=stage()){entityData.set(STAGE,next);refreshDimensions(); if(next == 2 && !level().isClientSide) FamilyAchievements.award(this, "grown_" + kind.id);}
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level().isClientSide) return InteractionResult.SUCCESS;
        if(owner!=null&&!owner.equals(player.getUUID())) { player.displayClientMessage(Component.translatable("message.dudunka.not_owner"),true); return InteractionResult.CONSUME; }
        if(owner==null) { player.displayClientMessage(Component.translatable("message.dudunka.hatch_first"),true); return InteractionResult.CONSUME; }
        var food=player.getItemInHand(hand);
        if(food.is(DudunkaMod.CARRIER.get())) { SyusyaCarrierItem.capture(this,player,food); return InteractionResult.CONSUME; }
        if(kind.likes(food)) {
            if(feedCooldown>0){player.displayClientMessage(Component.translatable("message.dudunka.full"),true);return InteractionResult.CONSUME;}
            boolean cookie = food.is(net.minecraft.world.item.Items.COOKIE);
            if(!player.getAbilities().instabuild)food.shrink(1);
            feedCooldown=600; trust=Math.min(100,trust+5); heal(4);
            if(kind == Kind.DUDUNKA && cookie) FamilyAchievements.award(this, "not_nonsense");
            if(stage()<2){growthTicks=Math.min(DudunkaMod.GROWTH_SECONDS.get()*40,growthTicks+600);updateStage();}
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+getBbHeight(),getZ(),3,.2,.1,.2,0);
        } else if(player.isShiftKeyDown()&&food.isEmpty()) {
            entityData.set(STAY,!staying()); getNavigation().stop();
        }
        player.displayClientMessage(Component.translatable("message.dudunka.companion_status",Component.translatable("stage.dudunka."+stage()),trust,Component.translatable(staying()?"mode.dudunka.stay":"mode.dudunka.follow")),true);
        return InteractionResult.CONSUME;
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide)return false;
        if(source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))return super.hurt(source,amount); // /kill remains administrative.
        if(recoveryTicks>0)return false;
        if(getHealth()-amount<=0) {
            setHealth(getMaxHealth()); recoveryTicks=200; getNavigation().stop(); returnHomeSafely(); return false;
        }
        return super.hurt(source,amount);
    }
    private void returnHomeSafely() {
        BlockPos p=homePosition();
        if(p!=null){teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);fallDistance=0;}
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); if(owner!=null)t.putUUID("FamilyOwner",owner); if(home!=null)t.putLong("FamilyHome",home.asLong()); if(homeDimension!=null)t.putString("HomeDimension",homeDimension);
        t.putBoolean("HomeMarker",homeIsMarker);t.putInt("GrowthTicks",growthTicks);t.putInt("Trust",trust);t.putInt("FeedCooldown",feedCooldown);t.putInt("RecoveryTicks",recoveryTicks);t.putBoolean("Staying",staying());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);owner=t.hasUUID("FamilyOwner")?t.getUUID("FamilyOwner"):null;home=t.contains("FamilyHome")?BlockPos.of(t.getLong("FamilyHome")):null;
        homeDimension=t.getString("HomeDimension");homeIsMarker=t.getBoolean("HomeMarker");
        growthTicks=Math.max(0,t.getInt("GrowthTicks"));trust=Math.max(0,Math.min(100,t.getInt("Trust")));feedCooldown=t.getInt("FeedCooldown");recoveryTicks=t.getInt("RecoveryTicks");entityData.set(STAY,t.getBoolean("Staying"));updateStage();
    }
    private static class FollowOwner extends Goal {
        private final Companion mob; private Player player;
        FollowOwner(Companion mob){this.mob=mob;setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){player=mob.owner==null?null:mob.level().getPlayerByUUID(mob.owner);return mob.willingToFollow() && !mob.staying()&&player!=null&&!player.isSpectator()&&mob.distanceToSqr(player)>9&&mob.distanceToSqr(player)<576;}
        @Override public boolean canContinueToUse(){return mob.willingToFollow() && !mob.staying()&&player!=null&&player.isAlive()&&mob.distanceToSqr(player)>4&&mob.distanceToSqr(player)<576;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){mob.getLookControl().setLookAt(player,10,30);if(mob.tickCount%10==0)mob.getNavigation().moveTo(player,1.1);}
        @Override public void stop(){mob.getNavigation().stop();player=null;}
    }
}
