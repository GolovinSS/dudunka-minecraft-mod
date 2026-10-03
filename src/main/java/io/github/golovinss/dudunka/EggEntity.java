package io.github.golovinss.dudunka;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.particles.ParticleTypes;
import java.util.UUID;

public class EggEntity extends BlockEntity {
    private int offerings,progress;
    private UUID owner;
    public EggEntity(BlockPos p,BlockState s) { super(DudunkaMod.EGG_BE.get(),p,s); }
    private Kind kind() { return ((EggBlock)getBlockState().getBlock()).kind; }
    public void interact(Player p,InteractionHand hand) {
        if(owner!=null&&!owner.equals(p.getUUID())) { p.displayClientMessage(Component.translatable("message.dudunka.egg_owned"),true); return; }
        var stack=p.getItemInHand(hand); int bit=kind().offering(stack);
        if(bit!=0&&(offerings&bit)==0) {
            if(owner==null) owner=p.getUUID(); offerings|=bit;
            if(!p.getAbilities().instabuild) stack.shrink(1);
            setChanged();
        }
        boolean ready=offerings==kind().requiredOfferings()&&conditions();
        p.displayClientMessage(Component.translatable("message.dudunka.egg_status",Integer.bitCount(offerings),Integer.bitCount(kind().requiredOfferings()),progress/20,DudunkaMod.HATCH_SECONDS.get(),Component.translatable(ready?"message.dudunka.ready":"message.dudunka.conditions_"+kind().id)),true);
    }
    private boolean conditions() {
        if(level==null) return false;
        BlockState floor=level.getBlockState(worldPosition.below());
        if(kind()==Kind.SYUSYA && !(floor.is(Blocks.MOSS_BLOCK)||floor.is(Blocks.GRASS_BLOCK))) return false;
        boolean bed=false,light=false,water=false,wool=false;
        for(BlockPos q:BlockPos.betweenClosed(worldPosition.offset(-2,-1,-2),worldPosition.offset(2,2,2))) {
            if(!level.hasChunkAt(q)) continue;
            BlockState s=level.getBlockState(q);
            bed|=s.is(BlockTags.BEDS); light|=s.getLightEmission(level,q)>0;
            water|=level.getFluidState(q).is(FluidTags.WATER); wool|=s.is(BlockTags.WOOL)||s.is(BlockTags.WOOL_CARPETS);
        }
        return switch(kind()) {
            case DUDUNKA -> bed&&light;
            case MARUSYA -> wool&&level.getMaxLocalRawBrightness(worldPosition)<=7;
            case SYUSYA -> water;
        };
    }
    public static void tick(Level l,BlockPos p,BlockState s,EggEntity e) {
        if(l.getGameTime()%20!=0) return;
        if(e.offerings!=e.kind().requiredOfferings()||!e.conditions()) return;
        e.progress+=20; e.setChanged();
        ((ServerLevel)l).sendParticles(ParticleTypes.HAPPY_VILLAGER,p.getX()+.5,p.getY()+.8,p.getZ()+.5,1,.15,.1,.15,0);
        if(e.progress<DudunkaMod.HATCH_SECONDS.get()*20) return;
        Companion mob=DudunkaMod.TYPES.get(e.kind()).get().create(l);
        if(mob==null) return;
        mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        // The egg occupies the spawn position. Test clearance above it before removing it.
        if(!l.noCollision(mob,mob.getBoundingBox().move(0,1,0))) return;
        mob.initialize(e.owner,p);
        l.removeBlock(p,false);
        if(!l.addFreshEntity(mob)) { l.setBlock(p,s,3); if(l.getBlockEntity(p) instanceof EggEntity restored) { restored.owner=e.owner; restored.offerings=e.offerings; restored.progress=e.progress; restored.setChanged(); } }
    }
    @Override public void load(CompoundTag t) { super.load(t); offerings=t.getInt("Offerings"); progress=t.getInt("Progress"); owner=t.hasUUID("Owner")?t.getUUID("Owner"):null; }
    @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); t.putInt("Offerings",offerings); t.putInt("Progress",progress); if(owner!=null)t.putUUID("Owner",owner); }
}
