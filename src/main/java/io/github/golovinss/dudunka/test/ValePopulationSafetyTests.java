package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class ValePopulationSafetyTests {
    @GameTest(template="empty", timeoutTicks=200, batch="isolated_vale_fitted_ground")
    public static void fittedFoundationsPreserveSlopesAndRejectUnsafeSites(GameTestHelper h) {
        var l=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var origin=new BlockPos(40,160,40);
        var saved=new HashMap<BlockPos,BlockState>();
        for(var p:BlockPos.betweenClosed(origin.offset(-8,-2,-8),origin.offset(8,10,8)))saved.put(p.immutable(),l.getBlockState(p));
        Runnable clear=()->saved.keySet().forEach(p->l.setBlock(p,p.getY()<160?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState(),18));
        try {
            for(String name:List.of("pufik_cottage","wise_fly_agaric_cottage","lantern_post","bench")) {
                var f=l.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.fromNamespaceAndPath("dudunka","mushroom_vale/"+name));
                var b=(MushroomValeDecorFeature.Blueprint)f.config();
                for(var rotation:Rotation.values()) {
                    clear.run();int extent=b.radius()+1;
                    var rise=origin.offset(extent,0,extent);var drop=origin.offset(-extent,-1,-extent);
                    l.setBlock(rise,Blocks.MYCELIUM.defaultBlockState(),18);l.setBlock(drop,Blocks.AIR.defaultBlockState(),18);
                    h.assertTrue(MushroomValeDecorFeature.placeBlueprint(l,origin,b,rotation),"Dry one-step exterior slope permits "+name+" "+rotation);
                    h.assertTrue(l.getBlockState(rise).is(Blocks.MYCELIUM) && l.isEmptyBlock(drop),"Slopes are never leveled or filled");
                    var written=new HashSet<BlockPos>();for(var piece:b.blocks())written.add(origin.offset(piece.offset().rotate(rotation)));
                    for(var p:saved.keySet())if(!written.contains(p)) {
                        var expected=p.equals(rise)?Blocks.MYCELIUM.defaultBlockState():p.equals(drop)?Blocks.AIR.defaultBlockState():p.getY()<160?Blocks.MYCELIUM.defaultBlockState():Blocks.AIR.defaultBlockState();
                        h.assertTrue(l.getBlockState(p).equals(expected),"Only approved blueprint positions change: "+p);
                    }
                    for(int failure=0;failure<6;failure++) {
                        clear.run();
                        var support=origin.offset(b.blocks().stream().filter(piece->piece.offset().getY()==0).findFirst().orElseThrow().offset().rotate(rotation)).below();
                        switch(failure) {
                            case 0 -> l.setBlock(support,Blocks.AIR.defaultBlockState(),18);
                            case 1 -> l.setBlock(support,Blocks.WATER.defaultBlockState(),18);
                            case 2 -> l.setBlock(origin.above(),Blocks.OAK_LOG.defaultBlockState(),18);
                            case 3 -> l.setBlock(origin.offset(extent,1,extent),Blocks.OAK_LEAVES.defaultBlockState(),18);
                            case 4 -> {l.setBlock(drop,Blocks.AIR.defaultBlockState(),18);l.setBlock(drop.below(),Blocks.AIR.defaultBlockState(),18);}
                            default -> {
                                if(name.endsWith("cottage")) {
                                    var front=b.blocks().stream().filter(piece->piece.offset().getY()==0 && piece.state().getBlock() instanceof SlabBlock)
                                            .min(java.util.Comparator.comparingInt(piece->piece.offset().getZ())).orElseThrow();
                                    var approach=origin.offset(front.offset().rotate(rotation)).relative(rotation.rotate(Direction.NORTH)).below();
                                    l.setBlock(approach,Blocks.AIR.defaultBlockState(),18);
                                } else l.setBlock(support,Blocks.AIR.defaultBlockState(),18);
                            }
                        }
                        // Existing bench rule only requires support under its seats.
                        if(failure==4 && name.equals("bench"))continue;
                        var before=new HashMap<BlockPos,BlockState>();saved.keySet().forEach(p->before.put(p,l.getBlockState(p)));
                        h.assertTrue(!MushroomValeDecorFeature.placeBlueprint(l,origin,b,rotation),"Unsafe site rejected before writing "+name+" "+failure);
                        h.assertTrue(before.entrySet().stream().allMatch(e->l.getBlockState(e.getKey()).equals(e.getValue())),"Rejected site is unchanged");
                    }
                }
                clear.run();
                var duplicate=new ArrayList<>(b.blocks());duplicate.add(b.blocks().get(0));
                h.assertTrue(!MushroomValeDecorFeature.placeBlueprint(l,origin,new MushroomValeDecorFeature.Blueprint(b.radius(),b.height(),duplicate),Rotation.NONE),"Duplicate blueprint offsets remain invalid");
            }
        } finally {saved.forEach((p,s)->l.setBlock(p,s,18));}
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100, batch="isolated_vale_creature_cap")
    public static void pufiksShareUnchangedCreatureCapWithMooshrooms(GameTestHelper h) throws Exception {
        var l=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var pos=new BlockPos(48,160,48);var chunk=l.getChunk(pos.getX()>>4,pos.getZ()>>4);
        var mobs=new ArrayList<net.minecraft.world.entity.Entity>();
        for(int i=0;i<10;i++) {
            var mob=i<2?DudunkaMod.PUFIK.get().create(l):EntityType.MOOSHROOM.create(l);
            mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);mobs.add(mob);
        }
        var local=new LocalMobCapCalculator(l.getChunkSource().chunkMap);
        var state=NaturalSpawner.createState(289,mobs,(key,consumer)->consumer.accept(chunk),local);
        h.assertTrue(MobCategory.CREATURE.getMaxInstancesPerChunk()==10 && state.getMobCategoryCounts().getInt(MobCategory.CREATURE)==10,
                "Both populations count against the unchanged creature cap of ten");
        h.assertTrue(ValeWildlifePopulation.underPopulationLimits(new int[]{0,0,0,0},DudunkaMod.PUFIK.get()),
                "Vale assistance retains independent local capacity even when the shared vanilla creature cap is full");
        var canSpawn=state.getClass().getDeclaredMethod("canSpawnForCategory",MobCategory.class,ChunkPos.class);canSpawn.setAccessible(true);
        h.assertTrue(!(boolean)canSpawn.invoke(state,MobCategory.CREATURE,chunk.getPos()),"A full cap rejects further creature spawning");
        mobs.forEach(net.minecraft.world.entity.Entity::discard);
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=100, batch="isolated_pufik_turnover")
    public static void ordinaryPufikTurnoverPreservesNamesPersistenceAndForgeVeto(GameTestHelper h) {
        var l=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var player=ValeFireflyPopulationTests.observer(l);
        player.moveTo(2048.5,160,2048.5,0,0);l.getChunk(128,128);l.addNewPlayer(player);
        var ordinary=DudunkaMod.PUFIK.get().create(l);var named=DudunkaMod.PUFIK.get().create(l);
        var persistent=DudunkaMod.PUFIK.get().create(l);var vetoed=DudunkaMod.PUFIK.get().create(l);
        var near=DudunkaMod.PUFIK.get().create(l);
        for(var mob:List.of(ordinary,named,persistent,vetoed))mob.moveTo(player.getX()+129,160,player.getZ(),0,0);
        near.moveTo(player.getX()+32,160,player.getZ(),0,0);
        named.setCustomName(net.minecraft.network.chat.Component.literal("Kept"));persistent.setPersistenceRequired();
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobSpawnEvent.AllowDespawn> veto=e->{if(e.getEntity()==vetoed)e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            for(var mob:List.of(ordinary,named,persistent,vetoed,near))mob.checkDespawn();
            h.assertTrue(ordinary.isRemoved() && named.isAlive() && persistent.isAlive() && vetoed.isAlive() && near.isAlive(),
                    "Vanilla distance turnover keeps nearby, named, persistent and vetoed Pufiks");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);player.discard();
            for(var mob:List.of(ordinary,named,persistent,vetoed,near))mob.discard();
        }
        h.succeed();
    }

}
