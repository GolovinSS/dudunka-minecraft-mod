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
}
