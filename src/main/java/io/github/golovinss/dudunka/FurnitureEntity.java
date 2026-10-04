package io.github.golovinss.dudunka;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FurnitureEntity extends BlockEntity {
    private UUID owner,member;
    public FurnitureEntity(BlockPos p,BlockState s){super(DudunkaMod.FURNITURE_BE.get(),p,s);}
    public UUID ownerId(){return owner;}
    public UUID memberId(){return member;}
    public Kind kind(){return ((FurnitureBlock)getBlockState().getBlock()).kind;}
    public void claim(UUID id){if(owner==null){owner=id;setChanged();}}
    public boolean available(Companion mob){return (owner==null || owner.equals(mob.ownerId())) && (member==null || member.equals(mob.getUUID())) && kind()==mob.kind;}
    public boolean assigned(Companion mob){return mob.ownerId()!=null && mob.ownerId().equals(owner) && mob.getUUID().equals(member) && mob.kind==kind();}
    public boolean assign(Companion mob){if(mob.ownerId()==null || !available(mob))return false;owner=mob.ownerId();member=mob.getUUID();setChanged();return true;}
    public boolean clear(java.util.UUID player){if(!player.equals(owner))return false;member=null;setChanged();return true;}
    public void release(Companion mob){if(assigned(mob)){member=null;setChanged();}}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;member=t.hasUUID("Member")?t.getUUID("Member"):null;}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);if(member!=null)t.putUUID("Member",member);}
}
