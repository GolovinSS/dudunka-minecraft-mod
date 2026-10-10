package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in real finished-chunk census; compiled only by the survey init script. */
@GameTestHolder("dudunka_survey") @PrefixGameTestTemplate(false)
public class ValeDistributionSurvey {
    @GameTest(template="empty", templateNamespace="dudunka_survey", timeoutTicks=200)
    public static void census(GameTestHelper h) {
        var vale=h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var generator=vale.getChunkSource().getGenerator();
        var random=vale.getChunkSource().randomState();
        var targets=new TreeMap<String,BlockPos>();
        // Select disjoint remote dry biome centers before generating any survey chunks.
        for(int radius=1024;radius<=4096 && targets.size()<3;radius+=128)
            for(int x=-radius;x<=radius;x+=128)for(int z=-radius;z<=radius;z+=128){
                if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
                var biome=generator.getBiomeSource().getNoiseBiome(x>>2,20,z>>2,random.sampler());
                String id=biome.unwrapKey().orElseThrow().location().getPath();
                final int biomeX=x,biomeZ=z;
                if(targets.containsKey(id) || targets.values().stream().anyMatch(p->Math.max(Math.abs(p.getX()-biomeX),Math.abs(p.getZ()-biomeZ))<512))continue;
                int height=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,vale,random);
                if(height>66)targets.put(id,new BlockPos(x,height,z));
            }
        h.assertTrue(targets.size()==3,"All three dry biome patches needed");
        var counts=new TreeMap<String,Map<String,Integer>>();
        var columns=new TreeMap<String,Integer>();
        var seen=new HashSet<Long>();
        var first=new HashSet<String>();
        var pos=new BlockPos.MutableBlockPos();
        int chunks=0;
        for(var target:targets.entrySet()){
            System.out.println("SURVEY patch seed="+vale.getSeed()+" biome="+target.getKey()+" center="+target.getValue());
            int cx=target.getValue().getX()>>4,cz=target.getValue().getZ()>>4;
            for(int dx=-8;dx<8;dx++)for(int dz=-8;dz<8;dz++){
                int chunkX=cx+dx,chunkZ=cz+dz;
                if(!seen.add(net.minecraft.world.level.ChunkPos.asLong(chunkX,chunkZ)))continue;
                var chunk=vale.getChunk(chunkX,chunkZ);chunks++;
                for(int x=chunkX*16;x<chunkX*16+16;x++)for(int z=chunkZ*16;z<chunkZ*16+16;z++){
                    String biome=vale.getBiome(pos.set(x,80,z)).unwrapKey().orElseThrow().location().getPath();
                    columns.merge(biome,1,Integer::sum);
                    for(int y=60;y<=115;y++){
                        pos.set(x,y,z);var state=chunk.getBlockState(pos);
                        if(state.isAir())continue;
                        var id=ForgeRegistries.BLOCKS.getKey(state.getBlock());String name=id.getPath(),category=null;
                        if(state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM) || name.startsWith("small_"))category=name;
                        else if(name.equals("vale_bench")){
                            category="bench_seats";
                            var along=state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getClockWise();
                            if(vale.getBlockState(pos.relative(along)).equals(state) && vale.getBlockState(pos.relative(along.getOpposite())).equals(state)){
                                String actualBiome=vale.getBiome(pos).unwrapKey().orElseThrow().location().getPath();
                                counts.computeIfAbsent(actualBiome,b->new TreeMap<>()).merge("standalone_benches",1,Integer::sum);
                            }
                        }
                        else if(name.equals("vale_planter"))category="planters";
                        else if(name.equals("vale_landmark_stem") && state.getValue(MushroomValeLandmarks.PROFILE)==0){
                            // Both size classes have exactly one root, with their crown at root+6/8.
                            for(int up=3;up<=10;up++){
                                var cap=vale.getBlockState(pos.above(up));var capId=ForgeRegistries.BLOCKS.getKey(cap.getBlock()).getPath();
                                if(capId.endsWith("landmark_cap")){
                                    int maxY=up;while(ForgeRegistries.BLOCKS.getKey(vale.getBlockState(pos.above(maxY+1)).getBlock()).getPath().endsWith("landmark_cap"))maxY++;
                                    category=capId.replace("_landmark_cap",maxY>=8?"_large":"_detailed");break;
                                }
                            }
                        } else if((name.equals("mushroom_stem") || name.equals("vale_mushroom_stem"))
                                && !vale.getBlockState(pos.below()).is(state.getBlock())){
                            // Vanilla huge mushrooms have a single vertical trunk and cap over its root.
                            for(int up=1;up<=14;up++){
                                var capId=ForgeRegistries.BLOCKS.getKey(vale.getBlockState(pos.above(up)).getBlock()).getPath();
                                if(capId.endsWith("mushroom_cap") || capId.equals("red_mushroom_block") || capId.equals("brown_mushroom_block")){
                                    category="huge_"+capId.replace("_mushroom_block","").replace("_mushroom_cap","");break;
                                }
                            }
                        }
                        if(category!=null){
                            String actualBiome=vale.getBiome(pos).unwrapKey().orElseThrow().location().getPath();
                            counts.computeIfAbsent(actualBiome,b->new TreeMap<>()).merge(category,1,Integer::sum);
                            if(first.add(actualBiome+":"+category))System.out.println("SURVEY found seed="+vale.getSeed()+" biome="+actualBiome+" variant="+category+" pos="+pos);
                        }
                    }
                }
            }
        }
        for(var entry:columns.entrySet())System.out.println("SURVEY seed="+vale.getSeed()+" biome="+entry.getKey()+" columns="+entry.getValue()+" chunkEquivalent="+(entry.getValue()/256.0)+" counts="+counts.get(entry.getKey()));
        System.out.println("SURVEY seed="+vale.getSeed()+" finishedChunks="+chunks);
        h.succeed();
    }
}
