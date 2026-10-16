package io.github.golovinss.dudunka;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import static io.github.golovinss.dudunka.ValeAdventureStructure.Landmark;

/** One atomic, terrain-following landmark entirely within its owning new chunk. */
public final class ValeAdventurePiece extends StructurePiece {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Landmark landmark;
    private final BlockPos root;
    private final int[] ground;
    // Absent in pre-4.3a saves: keep their exact blueprint and interaction binding.
    private final int visualRevision;
    private Map<BlockPos,BlockState> styledPlan, styledInteractions;
    public ValeAdventurePiece(Landmark landmark, BlockPos root, int[] ground) {
        super(DudunkaMod.VALE_ADVENTURE_PIECE.get(), 0,
                new BoundingBox(root.getX()-6, root.getY()-(landmark == Landmark.MUSIC_BELLS || landmark == Landmark.JUMPER_DANCE ? 3 : 2), root.getZ()-6,
                        root.getX()+6, root.getY()+(landmark == Landmark.MUSIC_BELLS ? 5 : landmark == Landmark.JUMPER_DANCE ? 3 : 11), root.getZ()+6));
        this.landmark = landmark; this.root = root.immutable(); this.ground = ground.clone();
        visualRevision = landmark == Landmark.MUSIC_BELLS ? 3 : landmark == Landmark.JUMPER_DANCE ? 2 : 0;
    }
    public ValeAdventurePiece(CompoundTag tag) {
        super(DudunkaMod.VALE_ADVENTURE_PIECE.get(), tag);
        landmark = Landmark.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("Landmark")).result().orElseThrow();
        root = BlockPos.of(tag.getLong("Anchor")); ground = tag.getIntArray("Ground");
        visualRevision = tag.getInt("VisualRevision");
        if (ground.length != 169) throw new IllegalArgumentException("Invalid Vale landmark ground profile");
    }
    public BlockPos anchor() { return root; }
    public Landmark landmark() { return landmark; }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c, CompoundTag tag) {
        tag.putString("Landmark", landmark.id); tag.putLong("Anchor", root.asLong()); tag.putIntArray("Ground", ground);
        if (visualRevision != 0) tag.putInt("VisualRevision", visualRevision);
    }
    private int floor(int dx, int dz) { return ground[(dx+6)*13+dz+6]; }
    public static boolean generationAllowed(WorldGenLevel level, ChunkPos chunk) {
        if (!(level instanceof WorldGenRegion region) || !region.getCenter().equals(chunk)
                || !level.getLevel().dimension().equals(MushroomVale.LEVEL)) return false;
        var owning = level.getChunk(chunk.x, chunk.z);
        return !owning.isUpgrading() && !owning.getStatus().isOrAfter(ChunkStatus.FEATURES);
    }
    /** Read-only full-volume preflight; no terrain, trees or player blocks are cleared. */
    public boolean clearSite(WorldGenLevel level, BoundingBox writable) {
        return siteProblem(level, writable) == null;
    }
    /** First rejection only, so developer evidence stays bounded and identifies the actual column. */
    public String siteProblem(WorldGenLevel level, BoundingBox writable) {
        if (styledBells() && !styledGround(root, ground)) return "styled clearing requires a natural grade of at most one block";
        for (int dx=-6;dx<=6;dx++) for (int dz=-6;dz<=6;dz++) {
            int y = floor(dx,dz);
            var support = new BlockPos(root.getX()+dx,y-1,root.getZ()+dz);
            if (!ValeAdventureStructure.eligible(landmark,level.getBiome(support.above())))
                return "biome at " + support.above() + ": " + level.getBiome(support.above()).unwrapKey();
            for (int py=y-1;py<=boundingBox.maxY();py++) {
                var p = new BlockPos(support.getX(),py,support.getZ());
                if (!level.hasChunkAt(p) || level.isOutsideBuildHeight(p) || !writable.isInside(p)
                        || !level.ensureCanWrite(p) || !level.getLevel().getWorldBorder().isWithinBounds(p))
                    return "unwritable at " + p + " bounds=" + writable;
                var state=level.getBlockState(p);
                if (!state.getFluidState().isEmpty() || level.getBlockEntity(p)!=null)
                    return "fluid/block entity at " + p + ": " + state;
                if (py==y-1) {
                    if (!(state.is(BlockTags.DIRT) || styledBells() && state.is(Blocks.MOSS_BLOCK))
                            || !state.isFaceSturdy(level,p,Direction.UP))
                        return "support at " + p + ": expected sturdy dirt, actual=" + state;
                } else if (!state.isAir() && !(state.getBlock() instanceof BushBlock))
                    return "obstruction at " + p + ": expected air/plant above saved floor=" + y + ", actual=" + state;
            }
        }
        return null;
    }
    public int visualRevision() { return visualRevision; }
    public boolean styledBells() { return landmark == Landmark.MUSIC_BELLS && visualRevision == 3; }
    /** Predicted and actual placement use the same bounded grading contract. No excavation. */
    public static boolean styledGround(BlockPos root, int[] ground) {
        if (ground.length != 169) return false;
        return java.util.Arrays.stream(ground).allMatch(y -> y >= root.getY()-1 && y <= root.getY());
    }
    public boolean anchorPresent(LevelReader level) {
        var expected = plan().get(root);
        return expected != null && level.hasChunkAt(root) && level.getBlockState(root).equals(expected);
    }
    public BlockPos bellPosition(int bell) {
        if (styledBells()) return root.offset(bell==1?-3:2,1,bell==0?-2:bell==1?-1:2);
        return root.offset(bell==0?-3:bell==1?0:3,visualRevision>=2?(bell==1?3:2):(bell==0?4:bell==1?6:5),bell==0?0:bell==1?2:-1);
    }
    /** Decorations never decide whether these two puzzles remain playable. */
    public Map<BlockPos,BlockState> interactionPlan() {
        var all=plan();
        if (styledBells()) {
            if (styledInteractions == null) {
                var required = new LinkedHashMap<BlockPos,BlockState>();
                all.forEach((pos,state) -> { if (MushroomBellsStyledPreview.required(state)) required.put(pos,state); });
                styledInteractions = java.util.Collections.unmodifiableMap(required);
            }
            return styledInteractions;
        }
        if(landmark!=Landmark.MUSIC_BELLS && landmark!=Landmark.JUMPER_DANCE) return all;
        var required=new LinkedHashMap<BlockPos,BlockState>();
        all.forEach((pos,state)-> {
            int x=pos.getX()-root.getX(),z=pos.getZ()-root.getZ();
            boolean target=pos.equals(root);
            if(landmark==Landmark.JUMPER_DANCE)
                target |= state.is(Blocks.ALLIUM) || state.is(Blocks.PINK_TULIP) || state.is(MushroomValeLandmarks.DANCE_FLOWER.get());
            else {
                target |= x==0 && z==-3;
                target |= (x==-3 && z==0 || x==0 && z==2 || x==3 && z==-1)
                        && (state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.SHROOMLIGHT)
                        || state.getBlock() instanceof ValeMusicBell);
                for(String color:new String[]{"turquoise","violet","golden"})
                    target |= state.is(MushroomValeLandmarks.BLOCKS.get(color+"_landmark_cap").get());
            }
            if(target) required.put(pos,state);
        });
        return java.util.Collections.unmodifiableMap(required);
    }
    /** Checks only already available blocks; interaction callers never load or repair chunks. */
    public boolean intact(LevelReader level) {
        return interactionPlan().entrySet().stream().allMatch(e -> level.hasChunkAt(e.getKey())
                && level.getBlockState(e.getKey()).equals(e.getValue()));
    }
    private void put(Map<BlockPos,BlockState> plan,int x,int y,int z,Block block) {
        plan.put(root.offset(x,y,z),block.defaultBlockState());
    }
    private void pillar(Map<BlockPos,BlockState> p,int x,int z,int height,Block block) {
        for(int y=floor(x,z)-root.getY();y<height;y++) put(p,x,y,z,block);
    }
    private void bell(Map<BlockPos,BlockState> p,int x,int z,int height,int radius,String color) {
        pillar(p,x,z,height,Blocks.MUSHROOM_STEM);
        Block cap=MushroomValeLandmarks.BLOCKS.get(color+"_landmark_cap").get();
        for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(dx*dx+dz*dz>radius*radius+1) continue;
            int y=height+(Math.abs(dx)+Math.abs(dz)<radius ? 1 : 0);
            put(p,x+dx,y,z+dz,cap);
            if(Math.abs(dx)==radius || Math.abs(dz)==radius) put(p,x+dx,y-1,z+dz,cap);
        }
        put(p,x,height-1,z,Blocks.SHROOMLIGHT);
    }
    /** Existing cap profiles provide gills, domes and hanging luminous clappers. */
    private void musicalBell(Map<BlockPos,BlockState> p,int x,int z,int height,String color,int style) {
        pillar(p,x,z,height,Blocks.MUSHROOM_STEM);
        var cap=MushroomValeLandmarks.BLOCKS.get(color+"_landmark_cap").get().defaultBlockState();
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            boolean included = switch(style) {
                case 0 -> dx*dx+dz*dz<=5; // Broad scalloped parasol.
                case 1 -> Math.abs(dx)+Math.abs(dz)<=2; // Pointed diamond bell.
                default -> Math.abs(dx)<=2 && Math.abs(dz)<=1; // Low oval sun bell.
            };
            if(!included) continue;
            p.put(root.offset(x+dx,height,z+dz),cap.setValue(MushroomValeLandmarks.PROFILE,0));
            if(Math.abs(dx)+Math.abs(dz)<2)
                p.put(root.offset(x+dx,height+1,z+dz),cap.setValue(MushroomValeLandmarks.PROFILE,style==2?3:2));
        }
        if(style==1) p.put(root.offset(x,height+2,z),cap.setValue(MushroomValeLandmarks.PROFILE,2));
        // One static hanging clapper per bell; no ticking blocks or ambient particles.
        p.put(root.offset(x+1,height,z),cap.setValue(MushroomValeLandmarks.PROFILE,4));
        p.put(root.offset(x,height,z),cap.setValue(MushroomValeLandmarks.PROFILE,1));
        put(p,x,height-1,z,Blocks.SHROOMLIGHT);
    }
    private void refinedBell(Map<BlockPos,BlockState> p,int x,int z,int height,String color) {
        Block block=MushroomValeLandmarks.MUSIC_BELLS.get(color).get();
        pillar(p,x,z,height,block);
        p.put(root.offset(x,height,z),block.defaultBlockState().setValue(ValeMusicBell.SEGMENT,1));
    }
    private void refineClearing(Map<BlockPos,BlockState> p) {
        if(landmark==Landmark.MUSIC_BELLS) {
            // Both historical clue targets remain: one connected listening alcove.
            for(int z=-2;z<=-1;z++)
                p.put(new BlockPos(root.getX(),floor(0,z)-1,root.getZ()+z),Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            for(int[] c:new int[][]{{-4,2},{-3,3},{2,3},{4,3},{-4,-3},{4,-3}})
                groundDetail(p,c[0],c[1],MushroomValeVegetation.BLOCKS.get("small_golden_mushroom").get());
        } else {
            // Remove every cell of the former posts, including buried slope extensions.
            for(int x:new int[]{-3,3}) for(int z:new int[]{-3,3}) {
                for(int y=floor(x,z)-root.getY();y<=1;y++) p.remove(root.offset(x,y,z));
                groundDetail(p,x,z,MushroomValeVegetation.BLOCKS.get("small_violet_mushroom").get());
                groundDetail(p,x-Integer.signum(x),z,Blocks.LARGE_AMETHYST_BUD);
            }
            for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
                int d=x*x+z*z;
                if(d>2 && d<=18) p.put(new BlockPos(root.getX()+x,floor(x,z)-1,root.getZ()+z),
                        (d>=13 && (x+z)%3==0 ? Blocks.CALCITE : d<8 ? Blocks.ROOTED_DIRT : Blocks.MOSS_BLOCK).defaultBlockState());
            }
            // Low petals around the unchanged carved clue; never obstruct cardinal paths.
            for(int[] c:new int[][]{{-1,-1},{1,-1},{-1,1},{1,1}})
                p.put(new BlockPos(root.getX()+c[0],floor(c[0],c[1]),root.getZ()+c[1]),Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        }
    }
    private void groundDetail(Map<BlockPos,BlockState> p,int x,int z,Block block) {
        var pos=new BlockPos(root.getX()+x,floor(x,z),root.getZ()+z);
        if(p.containsKey(pos)) return;
        p.put(pos.below(),Blocks.MOSS_BLOCK.defaultBlockState());
        p.put(pos,block.defaultBlockState());
    }
    private void polishClearing(Map<BlockPos,BlockState> p) {
        // Ground-only moss inlays never raise the walking surface or cover the clue stone.
        for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
            int d=x*x+z*z;
            if((landmark==Landmark.MUSIC_BELLS && d<=27) || (landmark==Landmark.JUMPER_DANCE && d<=10))
                p.put(new BlockPos(root.getX()+x,floor(x,z)-1,root.getZ()+z),
                        (landmark==Landmark.JUMPER_DANCE && d>0 && d<=2 ? Blocks.SMOOTH_QUARTZ : Blocks.MOSS_BLOCK).defaultBlockState());
        }
        if(landmark==Landmark.JUMPER_DANCE) {
            // Replacing values retains the original flower order, voice count and pitch mapping.
            p.replaceAll((pos,state)-> {
                if(!state.is(Blocks.ALLIUM) && !state.is(Blocks.PINK_TULIP)) return state;
                int x=pos.getX()-root.getX(), z=pos.getZ()-root.getZ();
                int bloom=Math.abs(z)>=Math.abs(x) ? (z<0?0:2) : (x>0?1:3);
                return MushroomValeLandmarks.DANCE_FLOWER.get().defaultBlockState().setValue(ValeDanceFlower.BLOOM,bloom);
            });
            for(int x:new int[]{-3,3}) for(int z:new int[]{-3,3}) {
                put(p,x,0,z,Blocks.MOSS_BLOCK);
                put(p,x,1,z,MushroomValeVegetation.BLOCKS.get("small_golden_mushroom").get());
            }
            for(int[] c:new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}})
                groundDetail(p,c[0],c[1],MushroomValeVegetation.BLOCKS.get("glowing_moss").get());
        } else {
            for(int[] c:new int[][]{{-5,-3},{-4,3},{2,4},{5,2}})
                groundDetail(p,c[0],c[1],MushroomValeVegetation.BLOCKS.get("spore_bush").get());
            for(int[] c:new int[][]{{-4,-2},{-2,3},{4,2}})
                groundDetail(p,c[0],c[1],Blocks.SMALL_AMETHYST_BUD);
            String[] colors={"turquoise","violet","golden"};
            int[][] clusters={{-5,2},{1,4},{5,-3}};
            for(int i=0;i<clusters.length;i++)
                groundDetail(p,clusters[i][0],clusters[i][1],MushroomValeVegetation.BLOCKS.get("small_"+colors[i]+"_mushroom").get());
        }
    }
    /** Stable root and insertion order also preserve interaction identities and flower voices. */
    public Map<BlockPos,BlockState> plan() {
        if (styledBells()) {
            if (styledPlan == null) styledPlan = styledClearing();
            return styledPlan;
        }
        var p=new LinkedHashMap<BlockPos,BlockState>();
        switch(landmark) {
            case MUSIC_BELLS -> {
                if (visualRevision == 0) {
                    bell(p,-3,0,4,2,"turquoise"); bell(p,0,2,6,2,"violet"); bell(p,3,-1,5,2,"golden");
                } else if(visualRevision>=2) {
                    refinedBell(p,-3,0,2,"turquoise");
                    refinedBell(p,0,2,3,"violet");
                    refinedBell(p,3,-1,2,"golden");
                } else {
                    musicalBell(p,-3,0,4,"turquoise",0);
                    musicalBell(p,0,2,6,"violet",1);
                    musicalBell(p,3,-1,5,"golden",2);
                }
                pillar(p,0,-3,1,Blocks.CHISELED_STONE_BRICKS);
                put(p,0,1,-3,Blocks.AMETHYST_CLUSTER);
            }
            case JUMPER_DANCE -> {
                for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
                    int d=x*x+z*z;
                    if(d>=20 && d<=29) p.put(new BlockPos(root.getX()+x,floor(x,z),root.getZ()+z),
                            ((x+z)%2==0 ? Blocks.ALLIUM : Blocks.PINK_TULIP).defaultBlockState());
                }
                pillar(p,0,0,1,Blocks.CHISELED_QUARTZ_BLOCK);
                for(int x:new int[]{-3,3}) for(int z:new int[]{-3,3}) {
                    pillar(p,x,z,1,Blocks.MUSHROOM_STEM); put(p,x,1,z,Blocks.SHROOMLIGHT);
                }
            }
            case MEMORY_STONE -> {
                for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) pillar(p,x,z,1,Blocks.MOSSY_STONE_BRICKS);
                for(int y=1;y<=5;y++) for(int x=-1;x<=1;x++) put(p,x,y,0,Blocks.CRYING_OBSIDIAN);
                put(p,0,3,-1,Blocks.AMETHYST_BLOCK); put(p,0,3,1,Blocks.AMETHYST_BLOCK);
                put(p,0,6,0,Blocks.AMETHYST_CLUSTER);
                for(int x:new int[]{-4,4}) for(int z:new int[]{-3,3}) {
                    pillar(p,x,z,1,Blocks.MOSSY_COBBLESTONE); put(p,x,1,z,Blocks.SMALL_AMETHYST_BUD);
                }
            }
            case SILVER_WEB_HARP -> {
                // Walkable silver web sculpture: luminous glass strings, no trapping cobwebs.
                for(int x=-4;x<=4;x++) {
                    pillar(p,x,0,1,Blocks.SMOOTH_QUARTZ);
                    put(p,x,8-Math.abs(x)/2,0,Blocks.QUARTZ_BLOCK);
                }
                pillar(p,-4,0,7,Blocks.QUARTZ_PILLAR); pillar(p,4,0,7,Blocks.QUARTZ_PILLAR);
                for(int x=-3;x<=3;x++) for(int y=1;y<8-Math.abs(x)/2;y++) {
                    put(p,x,y,0,(y==4 || y==2+Math.abs(x)) ? Blocks.AMETHYST_BLOCK : Blocks.WHITE_STAINED_GLASS_PANE);
                }
                put(p,0,8,0,Blocks.SEA_LANTERN);
            }
            case SINGING_MUSHROOM -> {
                bell(p,0,1,8,4,"golden");
                // Low crescent of seats faces the giant bell; central approach stays open.
                for(int x:new int[]{-4,-2,2,4}) {
                    pillar(p,x,-4,1,Blocks.MOSSY_STONE_BRICKS);
                    put(p,x,1,-4,Blocks.SMOOTH_STONE_SLAB);
                }
                pillar(p,0,-3,1,Blocks.CHISELED_STONE_BRICKS);
                put(p,0,1,-3,Blocks.AMETHYST_CLUSTER);
            }
        }
        if (visualRevision > 0 && (landmark == Landmark.MUSIC_BELLS || landmark == Landmark.JUMPER_DANCE))
            polishClearing(p);
        if(visualRevision>=2) refineClearing(p);
        pillar(p,0,0,1,Blocks.CHISELED_STONE_BRICKS);
        return java.util.Collections.unmodifiableMap(p);
    }
    private Map<BlockPos,BlockState> styledClearing() {
        var p = new LinkedHashMap<BlockPos,BlockState>();
        // One shallow natural terrace under the rigid approved scene, with a terrain-following
        // moss fringe. The whole footprint is preflighted before any plant or ground is touched.
        for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) {
            int floor = floor(x,z);
            boolean core = Math.abs(x)<=5 && Math.abs(z)<=4;
            if (core) {
                for (int y=floor;y<root.getY();y++)
                    p.put(new BlockPos(root.getX()+x,y,root.getZ()+z),Blocks.MOSS_BLOCK.defaultBlockState());
            } else {
                // Only feather next to a raised edge. A level site retains all original
                // ground, exactly as in the accepted terrain preview.
                boolean edge = Math.abs(x)==6 && Math.abs(z)<=4 || Math.abs(z)==5 && Math.abs(x)<=5;
                int nx=Math.max(-5,Math.min(5,x)), nz=Math.max(-4,Math.min(4,z));
                if (edge && floor<root.getY() && floor(nx,nz)<root.getY() && (x*x+z*z)%3!=0)
                    p.put(new BlockPos(root.getX()+x,floor-1,root.getZ()+z),Blocks.MOSS_BLOCK.defaultBlockState());
            }
        }
        p.putAll(MushroomBellsStyledPreview.plan(root));
        return java.util.Collections.unmodifiableMap(p);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox writable,ChunkPos chunk,BlockPos pivot) {
        // A FULL/saved player chunk must remain untouched, including its start metadata.
        if(!generationAllowed(level,chunk) || !new ChunkPos(root).equals(chunk)) return;
        var problem=siteProblem(level,writable);
        if(problem!=null) { reject(level,chunk,problem); return; }
        var plan=new LinkedHashMap<>(plan());
        if (styledBells()) {
            // Clear only preflight-approved plants, including gaps. Air is not saved as
            // landmark geometry: later ordinary ground vegetation is allowed to regrow.
            for (int x=-5;x<=5;x++) for (int z=-4;z<=4;z++)
                for (int y=root.getY();y<=boundingBox.maxY();y++)
                    plan.putIfAbsent(new BlockPos(root.getX()+x,y,root.getZ()+z),Blocks.AIR.defaultBlockState());
        }
        if(plan.keySet().stream().anyMatch(p->!writable.isInside(p) || !boundingBox.isInside(p))) {
            reject(level,chunk,"blueprint outside piece/writable bounds"); return;
        }
        var before=new LinkedHashMap<BlockPos,BlockState>();
        for(var entry:plan.entrySet()) {
            before.put(entry.getKey(),level.getBlockState(entry.getKey()));
            boolean unchanged = styledBells() && level.getBlockState(entry.getKey()).equals(entry.getValue());
            if(!unchanged && (!level.setBlock(entry.getKey(),entry.getValue(),2)
                    || !level.getBlockState(entry.getKey()).equals(entry.getValue()))) {
                // Restore only this attempt's cells in this unfinished chunk.
                before.forEach((pos,state)->level.setBlock(pos,state,2));
                reject(level,chunk,"block write failed at "+entry.getKey()); return;
            }
        }
    }
    private void reject(WorldGenLevel level,ChunkPos chunk,String reason) {
        var owning=level.getChunk(chunk.x,chunk.z);
        var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID,landmark.id));
        var start=owning.getStartForStructure(structure);
        if(start!=null && start.getPieces().contains(this)) {
            owning.setStartForStructure(structure,StructureStart.INVALID_START);
            // /locate also caches presence separately from ChunkAccess. Refresh both.
            level.getLevel().onStructureStartsAvailable(owning);
        }
        LOGGER.warn("Vale landmark rejected: id={} seed={} chunk={} anchor={} bounds={} reason={}",
                landmark.id,level.getSeed(),chunk,root,boundingBox,reason);
    }
}
