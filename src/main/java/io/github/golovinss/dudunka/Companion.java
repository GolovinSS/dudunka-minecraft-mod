package io.github.golovinss.dudunka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
    private static final EntityDataAccessor<Integer> CHARACTER_VARIANT=SynchedEntityData.defineId(Companion.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FEELERS=SynchedEntityData.defineId(Companion.class,EntityDataSerializers.INT);
    private int nextCharacterVariant;
    private static final UUID TRUST_SPEED_ID=UUID.fromString("c70ba864-9e01-4ae1-b6d5-d22257b79212");
    public final Kind kind;
    private UUID owner;
    private BlockPos home;
    private String homeDimension;
    private boolean homeIsMarker,homeMode;
    private BlockPos furniture;
    private String furnitureDimension;
    private BlockPos openedChest;
    private BlockPos homeSceneTarget;
    private final Homecoming homecoming=new Homecoming();
    private boolean homeWelcomeRunning;
    private long chestNoticeUntil,chestNoticeNext,homeSceneCooldownUntil;
    private int growthTicks,trust,feedCooldown,recoveryTicks,petCooldown,pettingTicks;
    public Companion(EntityType<? extends Companion> type,Level l,Kind kind) {
        super(type,l); this.kind=kind; setPersistenceRequired(); refreshDimensions();
    }
    public static AttributeSupplier.Builder attributes(Kind k) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,k.speed).add(Attributes.FOLLOW_RANGE,24);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(STAGE,0); entityData.define(STAY,false); entityData.define(ACTIVITY, Activity.IDLE.ordinal());entityData.define(CHARACTER_VARIANT,0);entityData.define(FEELERS,CharacterMoments.Feelers.CALM.ordinal()); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new PettingGoal(this));
        goalSelector.addGoal(2,new FamilyBehaviorGoal(this));
        goalSelector.addGoal(3,new CampfireRestGoal(this));
        goalSelector.addGoal(4,new ChestCuriosityGoal(this));
        goalSelector.addGoal(5,new FurnitureGoal(this));
        goalSelector.addGoal(6,new HomeTogetherGoal(this));
        goalSelector.addGoal(7,new HomeSceneGoal(this));
        goalSelector.addGoal(8,new StayHomeGoal(this));
        goalSelector.addGoal(9,new FamilyFollowGoal(this));
        goalSelector.addGoal(10,new WaterAvoidingRandomStrollGoal(this, .8) {
            @Override public boolean canUse() { if(staying() || homeMode && (homeAnchor()==null || blockPosition().distSqr(homeAnchor())>36))return false;
                if(!super.canUse())return false;
                return !homeMode || homeAnchor()!=null && new BlockPos((int)Math.floor(wantedX),(int)Math.floor(wantedY),(int)Math.floor(wantedZ)).distSqr(homeAnchor())<=36; }
        });
        goalSelector.addGoal(11,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(12,new RandomLookAroundGoal(this));
    }
    public void initialize(UUID owner,BlockPos home) { this.owner=owner; this.home=home.immutable(); this.homeDimension=level().dimension().location().toString(); setCustomName(Component.translatable("entity.dudunka."+kind.id)); }
    public int stage() { return entityData.get(STAGE); }
    public boolean staying() { return entityData.get(STAY); }
    /** Explicit, idempotent mode command shared by nearby interaction and album. */
    public boolean commandStay(Player player,boolean stay) {
        if(level().isClientSide || !isAlive() || !player.isAlive() || player.isSpectator()
            || player.level()!=level() || owner==null || !owner.equals(player.getUUID()) || distanceToSqr(player)>4096)return false;
        if(staying()!=stay || homeMode) {
            homeMode=false;
            entityData.set(STAY,stay);getNavigation().stop();pettingTicks=0;openedChest=null;
            homeSceneTarget=null;homecoming.reset();homeWelcomeRunning=false;setActivity(Activity.IDLE);
        }
        return true;
    }
    public boolean atHomeMode(){return homeMode;}
    public boolean commandHome(Player player){
        if(homeAnchor()==null || homePosition()==null || !commandStay(player,false))return false;homeMode=true;prepareRecovery();return true;
    }
    public BlockPos furniturePosition(){return furniture;}
    public String furnitureDimension(){return furnitureDimension;}
    public void bindFurniture(BlockPos p){furniture=p.immutable();furnitureDimension=level().dimension().location().toString();prepareRecovery();}
    public void clearFurniture(){var f=FurnitureScenes.furniture(this);if(f!=null)f.release(this);furniture=null;furnitureDimension=null;prepareRecovery();}
    public UUID ownerId() { return owner; }
    public Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }
    public boolean sameFamily(Companion other) { return owner != null && owner.equals(other.ownerId()); }
    public int trust() { return trust; }
    private void changeTrust(int amount) { trust=Math.max(0,Math.min(100,trust+amount)); refreshTrustSpeed(); }
    private void refreshTrustSpeed() {
        if(kind!=Kind.SYUSYA) return;
        var speed=getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed==null)return;
        // A stable, transient modifier preserves other mods' base values and modifiers.
        speed.removeModifier(TRUST_SPEED_ID);
        if(trust>0)speed.addTransientModifier(new AttributeModifier(TRUST_SPEED_ID,"Syusya family trust",trust*.005,AttributeModifier.Operation.MULTIPLY_BASE));
    }
    public boolean noticeOpenedChest(Player player,BlockPos pos) {
        long now=level().getGameTime();
        if(level().isClientSide || kind!=Kind.DUDUNKA || staying() || owner==null || !owner.equals(player.getUUID())
                || player.level()!=level() || now<chestNoticeNext || !ChestCuriosity.isViewing(player,pos)
                || homeMode || distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>64)return false;
        openedChest=pos.immutable();chestNoticeUntil=now+100;chestNoticeNext=now+400;return true;
    }
    public void forgetOpenedChest(Player player){if(owner!=null && owner.equals(player.getUUID()))openedChest=null;}
    public BlockPos openChestTarget() {
        Player player=ownerPlayer();
        if(openedChest==null || staying() || level().getGameTime()>=chestNoticeUntil || player==null
                || !ChestCuriosity.isViewing(player,openedChest) || distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(openedChest))>100){openedChest=null;return null;}
        return openedChest;
    }
    public void bindHome(BlockPos anchor) { home=anchor.immutable(); homeDimension=level().dimension().location().toString(); homeIsMarker=true;homecoming.reset(); }
    public boolean homeWelcomePending() { return homecoming.pending(level().getGameTime()); }
    public boolean homeWelcomeRunning() { return homeWelcomeRunning; }
    public void beginHomeWelcome() { homecoming.consume(level().getGameTime());homeWelcomeRunning=true; }
    public void endHomeWelcome() { homeWelcomeRunning=false; }
    public void observeHomecoming() {
        if(level().isClientSide || kind!=Kind.DUDUNKA)return;
        var anchor=homeAnchor();var player=ownerPlayer();
        if(anchor==null || trust<25 || staying() || player==null || !player.isAlive() || player.isSpectator() || player.isSleeping()) {homecoming.reset();return;}
        homecoming.observe(level().getGameTime(),player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(anchor)));
    }
    public boolean homeTogetherReady() { return homeSceneReady() && pettingTicks==0 && homeSceneTarget==null && !homeWelcomeRunning && !homeWelcomePending() && openChestTarget()==null; }
    public boolean homeSceneReady() { return level().getGameTime()>=homeSceneCooldownUntil; }
    public void pauseHomeScenes() { homeSceneCooldownUntil=level().getGameTime()+200; }
    public BlockPos homeSceneTarget() { return homeSceneTarget; }
    public void reserveHomeScene(BlockPos target) { homeSceneTarget=target; }
    /** Scenes require a real, currently valid personal marker in this dimension. */
    public BlockPos homeAnchor() {
        if(!homeIsMarker || home==null || !level().dimension().location().toString().equals(homeDimension) || !level().hasChunkAt(home))return null;
        return level().getBlockEntity(home) instanceof HomeMarkerEntity marker && marker.validFor(this)?home:null;
    }
    public FamilyAlbum.HomeInfo albumHome() {
        var state=FamilyAlbum.HomeState.NONE;int flags=0;
        if(home!=null) {
            if(!level().dimension().location().toString().equals(homeDimension))state=FamilyAlbum.HomeState.OTHER_DIMENSION;
            else if(!level().hasChunkAt(home))state=FamilyAlbum.HomeState.UNLOADED;
            else if(!homeIsMarker)state=FamilyAlbum.HomeState.LEGACY;
            else if(!(level().getBlockEntity(home) instanceof HomeMarkerEntity marker) || !java.util.Objects.equals(owner,marker.ownerId()) || marker.kind()!=kind)state=FamilyAlbum.HomeState.MISSING;
            else {
                var c=marker.conditions(true);flags=(c.roof()?1:0)|(c.bed()?2:0)|(c.chest()?4:0)|(c.light()?8:0)|(c.food()?16:0);
                state=!c.ready()?FamilyAlbum.HomeState.INCOMPLETE:HomeRules.nearbyStanding(level(),home,this)==null?FamilyAlbum.HomeState.UNSAFE:FamilyAlbum.HomeState.READY;
            }
        }
        return new FamilyAlbum.HomeInfo(state,flags);
    }
    public BlockPos homePosition() {
        if(home==null || !level().dimension().location().toString().equals(homeDimension) || !level().hasChunkAt(home)) return null;
        if(homeIsMarker && (!(level().getBlockEntity(home) instanceof HomeMarkerEntity marker) || !marker.validFor(this))) return null;
        return HomeRules.nearbyStanding(level(),home,this);
    }
    public Activity activity() {
        if (staying()) return Activity.SIT;
        return Activity.values()[Math.max(0, Math.min(Activity.values().length - 1, entityData.get(ACTIVITY)))];
    }
    public int characterVariant(){return CharacterMoments.bounded(entityData.get(CHARACTER_VARIANT));}
    public int beginFurnitureVariant(){int next=nextCharacterVariant;entityData.set(CHARACTER_VARIANT,next);nextCharacterVariant=(next+1)%CharacterMoments.VARIANTS;return next;}
    public CharacterMoments.Feelers feelers(){return CharacterMoments.Feelers.values()[CharacterMoments.bounded(entityData.get(FEELERS))];}
    public void updateFeelers(){if(!level().isClientSide)entityData.set(FEELERS,CharacterMoments.feelers(this).ordinal());}
    public void setActivity(Activity activity) { entityData.set(ACTIVITY, activity.ordinal()); }
    public boolean willingToFollow() {
        // A stable ten-second interval avoids re-rolling every AI tick.
        return kind != Kind.MARUSYA || Math.floorMod(level().getGameTime() / 200 + getUUID().hashCode(), 5) != 0;
    }
    public float growthScale() { return (kind==Kind.SYUSYA || kind==Kind.MARUSYA)?.55f:kind==Kind.DUDUNKA?(stage()==0?.55f:stage()==1?.715f:.88f):(stage()==0?.55f:stage()==1?.78f:1f); }
    @Override public EntityDimensions getDimensions(Pose p) { return super.getDimensions(p).scale(kind==Kind.SYUSYA?(stage()==0?.55f:stage()==1?.715f:.935f):kind==Kind.MARUSYA?(stage()==0?.55f:stage()==1?.7425f:.99f):growthScale()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if(STAGE.equals(key))refreshDimensions(); }
    @Override public void aiStep() {
        super.aiStep();
        if(level().isClientSide) return;
        if(kind==Kind.SYUSYA && tickCount%10==0)updateFeelers();
        if(tickCount%20==0)observeHomecoming();
        if(tickCount%100==0)FamilyRegistry.get(((ServerLevel)level()).getServer()).observe(this);
        if(feedCooldown>0)feedCooldown--;
        if(petCooldown>0)petCooldown--;
        if(pettingTicks>0)pettingTicks--;
        if(recoveryTicks>0)recoveryTicks--;
        if(staying())getNavigation().stop();
        if(stage()<2) { growthTicks++; updateStage(); }
        if(owner != null && tickCount % 200 == 0) FamilyAchievements.checkFamily(this);
        // Home radius bounds idle exploration without forcing chunk loads.
        Player p=owner==null?null:level().getPlayerByUUID(owner);
        if(activity()==Activity.IDLE && !staying() && !homeMode && (p==null||distanceToSqr(p)>400) && tickCount%100==0) {
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
        if(food.is(DudunkaMod.ALBUM.get())){food.getItem().use(level(),player,hand);return InteractionResult.CONSUME;}
        if(food.is(net.minecraft.world.item.Items.BOOK)){FamilyFriendships.show(player,this);return InteractionResult.CONSUME;}
        if(food.is(DudunkaMod.CARRIER.get())) { SyusyaCarrierItem.capture(this,player,food); return InteractionResult.CONSUME; }
        if(kind.likes(food)) {
            if(feedCooldown>0){player.displayClientMessage(Component.translatable("message.dudunka.full"),true);return InteractionResult.CONSUME;}
            boolean cookie = food.is(net.minecraft.world.item.Items.COOKIE);
            if(!player.getAbilities().instabuild)food.shrink(1);
            feedCooldown=600; changeTrust(5); heal(4);
            if(kind == Kind.DUDUNKA && cookie) FamilyAchievements.award(this, "not_nonsense");
            if(stage()<2){growthTicks=Math.min(DudunkaMod.GROWTH_SECONDS.get()*40,growthTicks+600);updateStage();}
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+getBbHeight(),getZ(),3,.2,.1,.2,0);
        } else if(kind==Kind.MARUSYA && food.isEmpty() && !player.isShiftKeyDown()) {
            if(petCooldown>0) {
                player.displayClientMessage(Component.translatable("message.dudunka.pet_cooldown",(petCooldown+19)/20),true);
                return InteractionResult.CONSUME;
            }
            petCooldown=600; changeTrust(2); pettingTicks=staying()?0:40;
            var serverLevel=(net.minecraft.server.level.ServerLevel)level();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+getBbHeight(),getZ(),3,.2,.1,.2,0);
            serverLevel.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.CAT_PURR,net.minecraft.sounds.SoundSource.NEUTRAL,.6f,1f);
            player.displayClientMessage(Component.translatable("message.dudunka.petted",trust),true);
            return InteractionResult.CONSUME;
        } else if(player.isShiftKeyDown()&&food.isEmpty()) {
            commandStay(player,!staying());
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

    public void prepareRecovery(){getNavigation().stop();pettingTicks=0;openedChest=null;homeSceneTarget=null;homecoming.reset();homeWelcomeRunning=false;pauseHomeScenes();setActivity(Activity.IDLE);setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);fallDistance=0;}
    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason){
        if(level() instanceof ServerLevel server && owner!=null){
            var registry=FamilyRegistry.get(server.getServer());
            if(reason==net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK || reason==net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION)registry.observe(this);
            else if(!CarrierLedger.get(server.getServer()).carried(getUUID()))registry.forget(getUUID());
        }
        super.remove(reason);
    }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); if(owner!=null)t.putUUID("FamilyOwner",owner); if(home!=null)t.putLong("FamilyHome",home.asLong()); if(homeDimension!=null)t.putString("HomeDimension",homeDimension);
        t.putInt("CharacterVariant",characterVariant());t.putInt("NextCharacterVariant",nextCharacterVariant);
        t.putBoolean("HomeMode",homeMode);if(furniture!=null){t.putLong("Furniture",furniture.asLong());if(furnitureDimension!=null)t.putString("FurnitureDimension",furnitureDimension);}
        t.putInt("PetCooldown",petCooldown);t.putBoolean("HomeMarker",homeIsMarker);t.putInt("GrowthTicks",growthTicks);t.putInt("Trust",trust);t.putInt("FeedCooldown",feedCooldown);t.putInt("RecoveryTicks",recoveryTicks);t.putBoolean("Staying",staying());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);owner=t.hasUUID("FamilyOwner")?t.getUUID("FamilyOwner"):null;home=t.contains("FamilyHome")?BlockPos.of(t.getLong("FamilyHome")):null;
        homeDimension=t.getString("HomeDimension");homeIsMarker=t.getBoolean("HomeMarker");
        entityData.set(CHARACTER_VARIANT,CharacterMoments.bounded(t.getInt("CharacterVariant")));nextCharacterVariant=CharacterMoments.bounded(t.getInt("NextCharacterVariant"));entityData.set(FEELERS,CharacterMoments.Feelers.CALM.ordinal());
        homeMode=t.getBoolean("HomeMode") && !t.getBoolean("Staying");furniture=t.contains("Furniture")?BlockPos.of(t.getLong("Furniture")):null;furnitureDimension=t.getString("FurnitureDimension");
        growthTicks=Math.max(0,t.getInt("GrowthTicks"));trust=Math.max(0,Math.min(100,t.getInt("Trust")));feedCooldown=t.getInt("FeedCooldown");recoveryTicks=t.getInt("RecoveryTicks");entityData.set(STAY,t.getBoolean("Staying"));petCooldown=Math.max(0,Math.min(600,t.getInt("PetCooldown")));pettingTicks=0;homecoming.reset();homeWelcomeRunning=false;homeSceneTarget=null;homeSceneCooldownUntil=0;openedChest=null;chestNoticeUntil=0;chestNoticeNext=0;refreshTrustSpeed();updateStage();
    }
    private static class PettingGoal extends Goal {
        private final Companion mob;
        PettingGoal(Companion mob){this.mob=mob;setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return mob.kind==Kind.MARUSYA && mob.pettingTicks>0 && !mob.staying();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){mob.getNavigation().stop();mob.setActivity(Activity.PURR);}
        @Override public void tick(){
            mob.getNavigation().stop();
            Player player=mob.ownerPlayer();if(player!=null && mob.distanceToSqr(player)<36)mob.getLookControl().setLookAt(player,15,25);
        }
        @Override public void stop(){mob.setActivity(Activity.IDLE);}
    }
}
