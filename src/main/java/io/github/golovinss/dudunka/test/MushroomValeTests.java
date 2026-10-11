package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class MushroomValeTests {
    private static final String ENTER = "dudunka_dev mushroom_vale enter";
    private static final String RETURN = "dudunka_dev mushroom_vale return";
    private static final List<String> TRAVEL_STABLE_COMPANION_TAGS = List.of(
            "FamilyOwner", "FamilyHome", "HomeDimension", "HomeMarker", "HomeMode",
            "Furniture", "FurnitureDimension", "Staying", "Trust", "CharacterVariant",
            "NextCharacterVariant", "PersistenceRequired", "CustomName", "Health", "Attributes");

    private record CompanionTravelSnapshot(UUID owner, int stage, int trust,
            net.minecraft.world.phys.Vec3 position, CompoundTag persistentData) {}

    private static CompanionTravelSnapshot companionTravelSnapshot(Companion companion) {
        var saved=companion.saveWithoutId(new CompoundTag());var persistent=new CompoundTag();
        for(var key:TRAVEL_STABLE_COMPANION_TAGS)if(saved.contains(key))persistent.put(key,saved.get(key).copy());
        return new CompanionTravelSnapshot(companion.ownerId(),companion.stage(),companion.trust(),companion.position(),persistent);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "isolated_vale_light")
    public static void valeLanternPropagatesBlockLightAndRemovalClearsIt(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 12, 8));
        var saved = new HashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
            saved.put(p.immutable(), level.getBlockState(p));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
        }
        var sources=new ArrayList<BlockState>();
        sources.add(MushroomValeDecor.BLOCKS.get("vale_lantern").get().defaultBlockState());
        for(String color:List.of("turquoise","violet","golden"))
            sources.add(MushroomValeLandmarks.BLOCKS.get(color+"_landmark_cap").get().defaultBlockState()
                .setValue(MushroomValeLandmarks.PROFILE,4));
        // A dark enclosed room with real post obstruction and ground samples.
        for(var p:saved.keySet())if(Math.abs(p.getX()-pos.getX())==4 || Math.abs(p.getY()-pos.getY())==4 || Math.abs(p.getZ()-pos.getZ())==4)
            level.setBlock(p,Blocks.STONE.defaultBlockState(),18);
        level.setBlock(pos.below(),Blocks.SPRUCE_FENCE.defaultBlockState(),18);
        level.setBlock(pos.below(2),Blocks.SPRUCE_FENCE.defaultBlockState(),18);
        level.setBlock(pos.below(3),Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),18);
        checkValeLightSource(h,pos,sources,0,saved);
    }

    private static void checkValeLightSource(GameTestHelper h,BlockPos pos,List<BlockState> sources,int index,
                                            Map<BlockPos,BlockState> saved){
        var level=h.getLevel();var source=sources.get(index);
        h.assertTrue(source.getLightEmission(level,pos)==15 && source.getLightBlock(level,pos)==0,
            "Lantern and visible hanging bulbs emit maximum real block light without obstruction");
        level.setBlockAndUpdate(pos,source);
        var engine=level.getChunkSource().getLightEngine();
        waitForLightThread(h,engine.lightChunk(level.getChunkAt(pos),true),()->{
            var ground=pos.east(3).below(2);
            h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.SKY,ground)==0,
                "Roofed fixture excludes daylight at any time of day");
            h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos.east())==14
                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos.east(2))==13
                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,ground)==10,
                "Real post/ground propagation must be 14/13/10 for "+source+"; ground="
                    +level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,ground));
            System.out.println("Vale dark-room light="+source+" adjacent=14/13 ground=10 sky=0");
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            waitForLightThread(h,engine.lightChunk(level.getChunkAt(pos),true),()->{
                h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos.east())==0
                    && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,ground)==0,
                    "Removing the visible source clears its light, with no orphan blocks");
                if(index+1<sources.size())checkValeLightSource(h,pos,sources,index+1,saved);
                else {saved.forEach((p,state)->level.setBlock(p,state,18));h.succeed();}
            });
        });
    }

    private static void waitForLightThread(GameTestHelper h, java.util.concurrent.CompletableFuture<?> barrier, Runnable check) {
        if (barrier.isDone()) check.run();
        else h.runAfterDelay(1, () -> waitForLightThread(h, barrier, check));
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_vale_music")
    public static void valeBiomesDecodeTheRegisteredNativeMusic(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String name : List.of("glowing_mushroom_forest", "memory_forest", "pink_meadows")) {
            var music = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, name)).getBackgroundMusic().orElseThrow();
            h.assertTrue(music.getEvent().value() == DudunkaMod.MUSHROOM_VALE_THEME.get()
                            && music.getMinDelay() == 1200 && music.getMaxDelay() == 3600 && music.replaceCurrentMusic(),
                    "Every loaded biome must use registered native music with 1–3 minute delays and replacement");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "isolated_vale_natural_benches")
    public static void valeFinishedChunksContainNaturallyGeneratedBenches(GameTestHelper h) {
        var vale = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var bench = MushroomValeDecor.BLOCKS.get("vale_bench").get();
        int chunks = 0;
        // Remote from all synthetic Vale fixtures. Generate normally with the real
        // rarity/heightmap/biome pipeline; never invoke a configured preview here.
        for (int cx = 64; cx < 96; cx++) for (int cz = 64; cz < 96; cz++) {
            var chunk = vale.getChunk(cx, cz);
            chunks++;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 63; y <= 110; y++) {
                var p = new BlockPos(cx * 16 + x, y, cz * 16 + z);
                if (chunk.getBlockState(p).is(bench)) {
                    h.assertTrue(vale.getBlockState(p.below()).isFaceSturdy(vale, p.below(), net.minecraft.core.Direction.UP),
                            "Generated bench needs real ground support");
                    System.out.println("Vale natural bench=" + p + " chunks examined=" + chunks);
                    var naturalPos=p.immutable();var rider=player(h);
                    boolean wasForced=vale.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(cx,cz));
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)vale.getChunk(cx+dx,cz+dz);
                    vale.setChunkForced(cx,cz,true);
                    h.runAtTickTime(239,()->{cleanup(rider);if(!wasForced)vale.setChunkForced(naturalPos.getX()>>4,naturalPos.getZ()>>4,false);});
                    rider.teleportTo(vale,p.getX()+.5,p.getY()+2,p.getZ()+.5,0,0);
                    checkNaturalBenchSeating(h,vale,rider,naturalPos,wasForced);
                    return;
                }
            }
        }
        h.fail("No natural bench in 1024 fresh finished chunks");
    }

    private static void checkNaturalBenchSeating(GameTestHelper h, net.minecraft.server.level.ServerLevel level,
                                                  ServerPlayer rider, BlockPos pos, boolean wasForced) {
        if(!level.isPositionEntityTicking(pos)) {
            h.runAfterDelay(1,()->checkNaturalBenchSeating(h,level,rider,pos,wasForced));
            return;
        }
        try {
            clickBench(rider,pos,level.getBlockState(pos));
            h.assertTrue(rider.getVehicle() instanceof ValeBenchSeat,"Naturally generated bench must support real sitting");
            var seat=(ValeBenchSeat)rider.getVehicle();
            rider.stopRiding();seat.tick();
            h.assertTrue(seat.isRemoved(),"Natural bench dismount removes the transient mount");
            h.succeed();
        } finally { cleanup(rider);if(!wasForced)level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,false); }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_vegetation")
    public static void valeSignatureFeaturesPlaceWithLightClearanceAndSurvival(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(8, 10, 8));
        var saved = new HashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(origin.offset(-6, -1, -6), origin.offset(6, 14, 6)))
            saved.put(p.immutable(), level.getBlockState(p));
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
            for (String name : List.of("giant_red", "giant_brown", "giant_turquoise", "giant_violet", "giant_golden",
                    "turquoise_landmark", "violet_landmark", "golden_landmark")) {
                saved.keySet().forEach(p -> level.setBlockAndUpdate(p,
                        p.getY() == origin.getY() - 1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState()));
                var feature = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/" + name));
                h.assertTrue(feature != null && feature.place(level, level.getChunkSource().getGenerator(),
                        net.minecraft.util.RandomSource.create(42), origin), "Loaded mushroom feature must place: " + name);
                int caps = 0, stems = 0, width = 0;
                for (var p : saved.keySet()) {
                    var state = level.getBlockState(p);
                    var blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    if ((blockId.getPath().endsWith("mushroom_cap") || blockId.getPath().endsWith("landmark_cap")) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                            || state.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
                        caps++;
                        width = Math.max(width, Math.abs(p.getX() - origin.getX()));
                        if (blockId.getNamespace().equals(DudunkaMod.ID))
                            h.assertTrue(state.getLightEmission(level, p) >= 6, "Magic caps must emit actual block light");
                        if (name.endsWith("landmark")) h.assertTrue(p.getY() >= origin.getY() + 4,
                                "Broad landmark caps must leave standing clearance underneath");
                    }
                    if (state.is(Blocks.MUSHROOM_STEM) || (blockId.getPath().equals("vale_mushroom_stem") || blockId.getPath().equals("vale_landmark_stem"))) stems++;
                }
                h.assertTrue(caps > 8 && stems >= 4 && stems <= 12, "Complete cap and slender vanilla-height stem: " + name);
                if (name.endsWith("landmark")) h.assertTrue(width == 4, "Landmarks need their nine-block-wide canopy");
                System.out.println("Vale configured mushroom=" + name + " caps=" + caps + " stem=" + stems + " radius=" + width);
            }
            // Reject roots, occupied canopies and fluids before any write.
            var blocked = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/turquoise_landmark"));
            for (int scenario = 0; scenario < 3; scenario++) {
                saved.keySet().forEach(p -> level.setBlock(p,
                        p.getY() == origin.getY() - 1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18));
                var obstacle = scenario == 0 ? origin : origin.offset(3, 5, 0);
                level.setBlock(obstacle, scenario == 2 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState(), 18);
                var before = new HashMap<BlockPos, BlockState>();
                saved.keySet().forEach(p -> before.put(p, level.getBlockState(p)));
                h.assertTrue(!blocked.place(level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(42), origin),
                        "Landmarks must refuse blocked roots/canopies and fluids");
                h.assertTrue(before.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),
                        "Rejected landmarks must not partially place or consume obstacles");
            }
            for (var entry : MushroomValeVegetation.BLOCKS.entrySet()) {
                if (entry.getKey().endsWith("_cap") || entry.getKey().endsWith("_stem")) continue;
                var plant = entry.getValue().get().defaultBlockState();
                for (var floor : List.of(Blocks.MYCELIUM, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK)) {
                    level.setBlockAndUpdate(origin.below(), floor.defaultBlockState());
                    h.assertTrue(plant.canSurvive(level, origin), "Decorations survive on Vale ground: " + entry.getKey());
                }
                h.assertTrue(plant.getCollisionShape(level, origin).isEmpty()
                                && plant.isPathfindable(level, origin, net.minecraft.world.level.pathfinder.PathComputationType.LAND)
                                && plant.getLightEmission(level, origin) > 0,
                        "Ground flora must emit light and permit companion navigation: " + entry.getKey());
                level.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
                h.assertTrue(!plant.canSurvive(level, origin), "Flora must not survive on unsuitable ground");
                level.setBlockAndUpdate(origin.below(), Blocks.WATER.defaultBlockState());
                h.assertTrue(!plant.canSurvive(level, origin), "Flora must not survive on water");
            }
            for (String name : List.of("glowing_moss", "spore_bushes", "glowing_small_mushrooms", "memory_small_mushrooms",
                    "pink_small_mushrooms", "glowing_flowers", "memory_flowers", "pink_flowers")) {
                saved.keySet().forEach(p -> level.setBlockAndUpdate(p,
                        p.getY() == origin.getY() - 1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState()));
                var patch = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/" + name));
                h.assertTrue(patch != null && patch.place(level, level.getChunkSource().getGenerator(),
                        net.minecraft.util.RandomSource.create(42), origin), "Loaded ground patch must place: " + name);
                int plants = 0;
                for (var p : saved.keySet()) {
                    var state = level.getBlockState(p);
                    if (net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace().equals(DudunkaMod.ID)) {
                        plants++;
                        h.assertTrue(state.canSurvive(level, p) && state.getCollisionShape(level, p).isEmpty(),
                                "Every placed patch plant needs support and empty collision: " + name);
                    }
                }
                h.assertTrue(plants > 0, "Patch must contain actual custom plants: " + name);
                System.out.println("Vale configured patch=" + name + " plants=" + plants);
            }
        } finally { saved.forEach(level::setBlockAndUpdate); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "isolated_vale_decor")
    public static void valeDecorBlueprintsRotateAndRejectUnsafeSites(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var origin = new BlockPos(8, 160, 8);
        var saved = new HashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(origin.offset(-7, -1, -7), origin.offset(7, 9, 7)))
            saved.put(p.immutable(), level.getBlockState(p));
        // Suppress shape updates while dismantling fixtures: doors must not drop item entities.
        Runnable clear = () -> saved.keySet().forEach(p -> level.setBlock(p,
                p.getY() == origin.getY() - 1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18));
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
            for (String name : List.of("lantern_post", "direction_marker", "bench", "mushroom_picnic",
                    "flower_bed", "cozy_cluster", "pufik_cottage", "wise_fly_agaric_cottage")) {
                var feature = registry.get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/" + name));
                h.assertTrue(feature != null && feature.config() instanceof MushroomValeDecorFeature.Blueprint,
                        "Registered JSON blueprint must decode: " + name);
                var config = (MushroomValeDecorFeature.Blueprint) feature.config();
                var rotations = new HashSet<net.minecraft.world.level.block.Rotation>();
                // Find deterministic seeds covering every cardinal orientation.
                for (int seed = 0; rotations.size() < 4 && seed < 100000; seed++) {
                    var random = net.minecraft.util.RandomSource.create(seed);
                    var rotation = net.minecraft.world.level.block.Rotation.values()[random.nextInt(4)];
                    if (!rotations.add(rotation)) continue;
                    clear.run();
                    h.assertTrue(feature.place(level, level.getChunkSource().getGenerator(),
                            net.minecraft.util.RandomSource.create(seed), origin), "Dry level site must place: " + name);
                    for (var piece : config.blocks()) {
                        var p = origin.offset(piece.offset().rotate(rotation));
                        var expected = piece.state().rotate(rotation);
                        var actual = level.getBlockState(p);
                        h.assertTrue(actual.is(expected.getBlock()), "Blueprint must preserve rotated block positions: " + name + " " + p);
                        // Vanilla fences and mushroom faces recompute directional connections as neighbors are placed.
                        for (var property : expected.getProperties()) {
                            if (Set.of("north", "south", "east", "west", "up", "down").contains(property.getName())) continue;
                            h.assertTrue(actual.getValue(property).equals(expected.getValue(property)),
                                    "Preserve rotated non-connection state: " + name + " " + rotation + " " + p + " " + property.getName());
                        }
                        h.assertTrue(level.getBlockState(p).canSurvive(level, p), "Decor needs valid support: " + name + " " + p);
                    }
                    // The halo must stay clear at walking height, including around cottage roofs.
                    int edge = config.radius() + 1;
                    for (int x = -edge; x <= edge; x++) for (int z = -edge; z <= edge; z++) {
                        if (Math.abs(x) == edge || Math.abs(z) == edge)
                            h.assertTrue(level.isEmptyBlock(origin.offset(x, 0, z))
                                    && level.isEmptyBlock(origin.offset(x, 1, z)), "Preserve walk-around halo");
                    }
                    if (name.endsWith("cottage")) {
                        assertCottage(h, level, origin, config, rotation, name.equals("wise_fly_agaric_cottage"));
                    }
                }
                h.assertTrue(rotations.size() == 4, "Exercise all four orientations");
                if (name.equals("bench")) {
                    clear.run();
                    level.setBlock(origin.offset(2, -1, 2), Blocks.AIR.defaultBlockState(), 18);
                    h.assertTrue(feature.place(level, level.getChunkSource().getGenerator(),
                            net.minecraft.util.RandomSource.create(42), origin),
                            "Standalone bench needs seat support, not an artificially flat outer halo");
                }
                for (int scenario = 0; scenario < 4; scenario++) {
                    clear.run();
                    var obstacle = scenario == 0 ? origin : origin.below();
                    var state = switch (scenario) {
                        case 0 -> Blocks.STONE.defaultBlockState();
                        case 1 -> Blocks.WATER.defaultBlockState();
                        case 2 -> Blocks.AIR.defaultBlockState();
                        default -> Blocks.OAK_LEAVES.defaultBlockState();
                    };
                    level.setBlock(obstacle, state, 2);
                    var before = new HashMap<BlockPos, BlockState>();
                    saved.keySet().forEach(p -> before.put(p, level.getBlockState(p)));
                    h.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(),
                            net.minecraft.util.RandomSource.create(42), origin), "Reject occupied/wet/uneven/leaf site: " + name);
                    h.assertTrue(before.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),
                            "Rejected placement must not change any block");
                }
                h.assertTrue(!feature.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(),
                        net.minecraft.util.RandomSource.create(42), h.absolutePos(new BlockPos(8, 3, 8))),
                        "Decor feature must refuse other dimensions");
                System.out.println("Vale decor=" + name + " blocks=" + config.blocks().size()
                        + " rotations=4 unsafeSites=4 passed");
            }
        } finally { saved.forEach((p, state) -> level.setBlock(p, state, 18)); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "isolated_vale_preview")
    public static void valeCottagePreviewRequiresOptInAndPlacesSelectedOrientation(GameTestHelper h) {
        var vale = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        boolean alreadyForced = vale.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(0, 0));
        // Block reads load terrain synchronously, but entity visibility arrives on a later
        // chunk-status tick. The live-occupant fixture needs an accessible entity section.
        vale.setChunkForced(0, 0, true);
        // Entity-ticking readiness requires the two-chunk neighborhood at FULL.
        // Load it synchronously rather than racing background generation on a fresh suite.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) vale.getChunk(x, z);
        h.runAtTickTime(399, () -> { if (!alreadyForced) vale.setChunkForced(0, 0, false); });
        waitForValeEntities(h, vale, () -> {
            try { checkCottagePreview(h); }
            finally { if (!alreadyForced) vale.setChunkForced(0, 0, false); }
        });
    }

    private static void waitForValeEntities(GameTestHelper h, net.minecraft.server.level.ServerLevel level, Runnable check) {
        if (level.isPositionEntityTicking(new BlockPos(8, 160, 8))) check.run();
        else h.runAfterDelay(1, () -> waitForValeEntities(h, level, check));
    }

    private static void checkCottagePreview(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var origin = new BlockPos(8, 160, 8);
        var saved = new HashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(origin.offset(-7, -1, -7), origin.offset(7, 9, 7)))
            saved.put(p.immutable(), level.getBlockState(p));
        // Suppress shape updates while dismantling fixtures: doors must not drop item entities.
        Runnable clear = () -> saved.keySet().forEach(p -> level.setBlock(p,
                p.getY() == origin.getY() - 1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18));
        var player = player(h);
        String property = System.getProperty(MushroomVale.DEV_PROPERTY);
        try {
            var dispatcher = serverCommands(h);
            var source = player.createCommandSourceStack().withPermission(2).withLevel(level);
            String command = "dudunka_dev mushroom_vale cottage pufik 8 160 8 east";
            clear.run();
            rejected(h, dispatcher, source.withPermission(0), command);
            rejected(h, dispatcher, player.server.createCommandSourceStack().withLevel(level), command);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), command);
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            rejected(h, dispatcher, source, command);
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var creature = new net.minecraft.world.entity.decoration.ArmorStand(level, 8.5, 160, 8.5);
            h.assertTrue(level.addFreshEntity(creature), "Live occupant fixture must spawn");
            h.assertTrue(level.getEntities((Entity) null, creature.getBoundingBox()).contains(creature),
                    "Live occupant fixture must be visible to server entity queries");
            try { rejected(h, dispatcher, source, command); }
            finally { creature.discard(); }
            h.assertTrue(level.isEmptyBlock(origin), "Rejected preview must leave the site untouched");
            for (String name : List.of("bench", "lantern_post")) {
                clear.run();
                String decorCommand = "dudunka_dev mushroom_vale decor " + name + " 8 160 8 east";
                rejected(h, dispatcher, source.withPermission(0), decorCommand);
                h.assertTrue(dispatcher.execute(decorCommand, source) == 1, "Opt-in decor preview must place " + name);
                var check = name.equals("bench") ? origin : origin.above(3);
                h.assertTrue(level.getBlockState(check).is(MushroomValeDecor.BLOCKS.get(
                        name.equals("bench") ? "vale_bench" : "vale_lantern").get()), "Preview contains the real prop");
            }
            for (String name : List.of("pufik", "wise")) {
                clear.run();
                int result = dispatcher.execute(command.replace("pufik", name), source);
                h.assertTrue(result == 1, "Explicit developer cottage must place: " + name);
                int r = name.equals("pufik") ? 2 : 3;
                var window = origin.offset(new BlockPos(0, 2, r + 1).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90));
                var state = level.getBlockState(window);
                h.assertTrue(state.is(MushroomValeDecor.BLOCKS.get("vale_cottage_window").get())
                        && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                                == net.minecraft.core.Direction.WEST
                        && state.getLightEmission(level, window) == 9, "Preview must rotate its amber window and emit real light");
                var before = new HashMap<BlockPos, BlockState>();
                saved.keySet().forEach(p -> before.put(p, level.getBlockState(p)));
                rejected(h, dispatcher, source, command.replace("pufik", name));
                h.assertTrue(before.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),
                        "An occupied preview must not replace the existing cottage");
            }
        } catch (CommandSyntaxException e) { throw new AssertionError(e); }
        finally {
            if (property == null) System.clearProperty(MushroomVale.DEV_PROPERTY);
            else System.setProperty(MushroomVale.DEV_PROPERTY, property);
            saved.forEach((p, state) -> level.setBlock(p, state, 18));
            cleanup(player);
        }
        h.succeed();
    }

    private static void assertCottage(GameTestHelper h, net.minecraft.server.level.ServerLevel level,
            BlockPos origin, MushroomValeDecorFeature.Blueprint config,
            net.minecraft.world.level.block.Rotation rotation, boolean wise) {
        int r = wise ? 3 : 2;
        int height = wise ? 4 : 3;
        var floor = new HashSet<BlockPos>();
        for (var piece : config.blocks()) if (piece.offset().getY() == 0 && piece.state().is(Blocks.BIRCH_PLANKS))
            floor.add(piece.offset());
        h.assertTrue(floor.size() == (wise ? 37 : 21), "Distinct library bay versus rounded Pufik footprint");
        long doors = config.blocks().stream().filter(p -> p.state().is(Blocks.SPRUCE_DOOR)).count();
        h.assertTrue(doors == 2, "Exactly one real two-half entrance door");
        for (var p : floor) {
            boolean boundary = Arrays.stream(net.minecraft.core.Direction.values()).filter(d -> d.getAxis().isHorizontal())
                    .anyMatch(d -> !floor.contains(p.relative(d)));
            if (!boundary) continue;
            for (int y = 1; y <= height; y++) {
                var world = origin.offset(p.above(y).rotate(rotation));
                var state = level.getBlockState(world);
                boolean aperture = p.getX() == 0 && p.getZ() == -r && y <= 2;
                h.assertTrue(aperture ? state.is(Blocks.SPRUCE_DOOR) : state.isCollisionShapeFullBlock(level, world),
                        "Continuous exterior wall, including door jambs/lintel and recessed corners: " + world);
            }
        }
        // Opening the actual door must update both halves; no side aperture can substitute for it.
        var door = origin.offset(new BlockPos(0, 1, -r).rotate(rotation));
        var doorState = level.getBlockState(door);
        ((net.minecraft.world.level.block.DoorBlock) doorState.getBlock()).setOpen(null, level, doorState, door, true);
        for (int y = 0; y < 2; y++) h.assertTrue(level.getBlockState(door.above(y))
                .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN), "Both door halves open");
        // Standing player AABB through the doorway and down the entire central aisle.
        for (int z = -r-1; z < r; z++) {
            var p = origin.offset(new BlockPos(0, 1, z).rotate(rotation));
            var box = new net.minecraft.world.phys.AABB(p.getX()+.2, p.getY(), p.getZ()+.2,
                    p.getX()+.8, p.getY()+1.8, p.getZ()+.8);
            h.assertTrue(level.noCollision(box), "Usable doorway and two-block headroom along interior aisle: " + p);
            h.assertTrue(!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty(), "Safe continuous floor");
        }
        var walkable = new HashSet<BlockPos>();
        for (var p : floor) {
            var w = origin.offset(p.above().rotate(rotation));
            if (level.noCollision(new net.minecraft.world.phys.AABB(w.getX()+.2, w.getY(), w.getZ()+.2,
                    w.getX()+.8, w.getY()+1.8, w.getZ()+.8))) walkable.add(p);
        }
        var reached = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(new BlockPos(0, 0, -r));
        while (!queue.isEmpty()) {
            var p = queue.remove();
            if (!walkable.contains(p) || !reached.add(p)) continue;
            for (var d : List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST)) queue.add(p.relative(d));
        }
        h.assertTrue(reached.equals(walkable), "All standing interior spaces connect to the usable entrance");
        long shelves = config.blocks().stream().filter(p -> p.state().is(Blocks.BOOKSHELF)).count();
        h.assertTrue(wise ? shelves >= 6 : shelves == 0, "Wise has an interior library; Pufik has compact home furniture");
    }

    private static void clickBench(ServerPlayer player, BlockPos pos, BlockState state) {
        state.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_bench")
    public static void valeBenchesSeatOnePlayerAndCleanUpEveryLifecycle(GameTestHelper h) {
        var first = player(h);
        var second = player(h);
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        try {
            for (var direction : List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST)) {
                var state = MushroomValeDecor.BLOCKS.get("vale_bench").get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, direction);
                level.setBlockAndUpdate(pos, state);
                first.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()-1.5);
                second.moveTo(pos.getX()+1.5, pos.getY(), pos.getZ()-1.5);
                clickBench(first, pos, state);
                h.assertTrue(first.getVehicle() instanceof ValeBenchSeat, "Right click mounts a server seat");
                var seat = (ValeBenchSeat) first.getVehicle();
                seat.positionRider(first);
                h.assertTrue(Math.abs(first.getY() - (pos.getY()-.2)) < .001
                        && first.getYRot() == direction.toYRot(), "Seat height and facing follow unchanged bench geometry");
                clickBench(second, pos, state);
                clickBench(first, pos, state);
                h.assertTrue(!second.isPassenger() && seat.getPassengers().size() == 1
                        && level.getEntitiesOfClass(ValeBenchSeat.class, new net.minecraft.world.phys.AABB(pos)).size() == 1,
                        "Repeated clicks and a second player cannot duplicate or share a seat");
                h.assertTrue(!seat.shouldBeSaved() && !seat.save(new CompoundTag()), "Mount is never saved");
                first.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(0, 0, false, true));
                first.rideTick();
                h.assertTrue(!first.isPassenger(), "Vanilla Shift input dismounts");
                seat.tick();
                h.assertTrue(seat.isRemoved() && level.noCollision(first, first.getBoundingBox()), "Dismount clears seat and lands outside bench");
                first.setShiftKeyDown(false);
                clickBench(second, pos, state);
                var next = (ValeBenchSeat) second.getVehicle();
                h.assertTrue(next != null, "Vacated seat can be occupied again");
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                h.assertTrue(!second.isPassenger() && next.isRemoved(), "Breaking bench immediately ejects and removes mount");
            }
            var state = MushroomValeDecor.BLOCKS.get("vale_bench").get().defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            clickBench(first, pos, state);
            var dimensionSeat = (ValeBenchSeat) first.getVehicle();
            first.teleportTo(first.server.getLevel(MushroomVale.LEVEL), 8.5, 160, 8.5, 0, 0);
            dimensionSeat.tick();
            h.assertTrue(dimensionSeat.isRemoved() && !first.isPassenger(), "Dimension transfer leaves no mount");
            clickBench(second, pos, state);
            var logoutSeat = (ValeBenchSeat) second.getVehicle();
            second.server.getPlayerList().remove(second);
            logoutSeat.tick();
            h.assertTrue(logoutSeat.isRemoved(), "Actual player logout removes mount");
        } finally {
            first.stopRiding(); second.stopRiding();
            ValeBenchSeat.removeAt(level, pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            cleanup(first); cleanup(second);
        }
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "ValeTest"));
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.connection = new ServerGamePacketListenerImpl(player.server, connection, player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        room(h, pos);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 37, 12);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void room(GameTestHelper h, BlockPos pos) {
        for (var p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 3, 2)))
            h.getLevel().setBlockAndUpdate(p, p.getY() == pos.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private static CommandDispatcher<CommandSourceStack> commands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        MushroomVale.registerCommands(dispatcher);
        return dispatcher;
    }

    private static CommandDispatcher<CommandSourceStack> serverCommands(GameTestHelper h) {
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        h.assertTrue(dispatcher.getRoot().getChild("dudunka_dev") != null,
                "Forge RegisterCommandsEvent must register the opt-in route on the actual server");
        return dispatcher;
    }

    private static int execute(CommandDispatcher<CommandSourceStack> dispatcher, ServerPlayer player, String command) {
        try { return dispatcher.execute(command, player.createCommandSourceStack().withPermission(2)); }
        catch (CommandSyntaxException e) { throw new AssertionError(e.getMessage(), e); }
    }

    private static void rejected(GameTestHelper h, CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandSourceStack source, String command) {
        try {
            dispatcher.execute(command, source);
            h.fail("Command should have been rejected: " + command + " at "
                    + StackWalker.getInstance().walk(frames -> frames.skip(1).findFirst().orElseThrow()).toString());
        } catch (CommandSyntaxException expected) {}
    }

    /** Command tests own their arrival surface; random worldgen is tested separately. */
    private static final class ArrivalFixture implements AutoCloseable {
        private final net.minecraft.server.level.ServerLevel level;
        private final Map<BlockPos, BlockState> saved = new HashMap<>();
        ArrivalFixture(GameTestHelper h) {
            level = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
            level.getChunk(0, 0);
            for (var p : BlockPos.betweenClosed(new BlockPos(4, 62, 4), new BlockPos(12, 68, 12))) {
                saved.put(p.immutable(), level.getBlockState(p));
                level.setBlock(p, p.getY() <= 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
        }
        @Override public void close() { saved.forEach((p, state) -> level.setBlock(p, state, 18)); }
    }

    private static void cleanup(ServerPlayer player) {
        MushroomValeReturns.get(player.server).forget(player.getUUID());
        player.remove(Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_registration")
    public static void valeLoadsDataDrivenTerrainAndSeparateDimensionType(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var vale = server.getLevel(MushroomVale.LEVEL);
        h.assertTrue(vale != null && server.getLevel(Level.NETHER) != null && server.getLevel(Level.END) != null,
                "All vanilla levels and the additive dimension must load");
        var types = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        h.assertTrue(types.getKey(vale.dimensionType()).equals(MushroomVale.ID)
                        && !types.getKey(server.overworld().dimensionType()).equals(MushroomVale.ID),
                "Vale must use its own data-driven type without replacing the Overworld");
        h.assertTrue(vale.dimensionType().minY() == -64 && vale.dimensionType().height() == 384
                        && vale.dimensionType().coordinateScale() == 1 && vale.dimensionType().hasSkyLight()
                        && !vale.dimensionType().hasFixedTime() && vale.dimensionType().bedWorks(),
                "Vale type settings must match the documented day/night contract");
        h.assertTrue(vale.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                "Vale must use vanilla noise terrain (use a fresh test world)");
        var generator = (NoiseBasedChunkGenerator) vale.getChunkSource().getGenerator();
        h.assertTrue(generator.generatorSettings().is(MushroomVale.ID)
                        && generator.getBiomeSource() instanceof MultiNoiseBiomeSource
                        && generator.getBiomeSource().possibleBiomes().size() == 3,
                "Separate noise settings and explicit extensible biome source must select the three independent Vale biomes");
        var settings = generator.generatorSettings().value();
        h.assertTrue(settings.seaLevel() == 63 && !settings.isAquifersEnabled() && !settings.oreVeinsEnabled(),
                "Low basins use a single water level without noise caves/aquifers/ore veins");
        vale.getChunk(0, 0);
        h.assertTrue(vale.getBlockState(new BlockPos(8, -64, 8)).is(Blocks.BEDROCK),
                "JSON must retain bedrock");
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var id : List.of("glowing_mushroom_forest", "pink_meadows", "memory_forest")) {
            var key = ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, id);
            h.assertTrue(biomes.containsKey(key)
                            && generator.getBiomeSource().possibleBiomes().stream().anyMatch(biome -> biome.is(key)),
                    "Each approved biome must load and be selectable: " + id);
            h.assertTrue(biomes.get(key).getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).isEmpty(),
                    "Vale biomes must not introduce hostile spawn lists");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_terrain")
    public static void valeBaseTerrainHasBoundedGentleReliefAndWaterBasins(GameTestHelper h) {
        var vale = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var generator = (NoiseBasedChunkGenerator) vale.getChunkSource().getGenerator();
        for (long seed : new long[]{0, 42, -71021}) {
            var random = RandomState.create(generator.generatorSettings().value(),
                    vale.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, maxStep = 0, wet = 0, dry = 0;
            // Base columns need no chunk generation; feature/carver appearance is a separate acceptance check.
            for (int x = -2048; x <= 2048; x += 128) for (int z = -2048; z <= 2048; z += 128) {
                int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, vale, random);
                int east = generator.getBaseHeight(x + 1, z, Heightmap.Types.OCEAN_FLOOR_WG, vale, random);
                int south = generator.getBaseHeight(x, z + 1, Heightmap.Types.OCEAN_FLOOR_WG, vale, random);
                min = Math.min(min, y); max = Math.max(max, y);
                maxStep = Math.max(maxStep, Math.max(Math.abs(y - east), Math.abs(y - south)));
                if (y < 63) wet++; else dry++;
                if (y < 63) h.assertTrue(generator.getBaseColumn(x, z, vale, random).getBlock(62).is(Blocks.WATER),
                        "Low basins must contain actual water at sea level");
            }
            h.assertTrue(min >= 52 && max <= 101 && max - min >= 12 && maxStep <= 2 && wet > 0 && dry > 0,
                    "Base terrain must have bounded hills, walkable sampled slopes, and water basins; seed=" + seed
                            + " range=" + min + ".." + max + " step=" + maxStep + " wet=" + wet + " dry=" + dry);
            System.out.println("Vale base terrain seed=" + seed + " range=" + min + ".." + max
                    + " max adjacent step=" + maxStep + " wet=" + wet + " dry=" + dry);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "isolated_vale_biomes")
    public static void valeBiomesOccurAcrossSeedsAndHaveDistinctFinishedSurfaces(GameTestHelper h) {
        var vale = h.getLevel().getServer().getLevel(MushroomVale.LEVEL);
        var generator = (NoiseBasedChunkGenerator) vale.getChunkSource().getGenerator();
        var ids = List.of("glowing_mushroom_forest", "pink_meadows", "memory_forest");
        for (long seed : new long[]{0, 42, -71021, 1, -1, 12345, 8675309}) {
            var random = RandomState.create(generator.generatorSettings().value(),
                    vale.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var counts = new TreeMap<String, Integer>();
            var nearest = new TreeMap<String, Integer>();
            for (int x = -2048; x <= 2048; x += 64) for (int z = -2048; z <= 2048; z += 64) {
                var biome = generator.getBiomeSource().getNoiseBiome(x >> 2, 20, z >> 2, random.sampler());
                String id = biome.unwrapKey().orElseThrow().location().getPath();
                h.assertTrue(ids.contains(id), "Only approved biomes may be selected");
                counts.merge(id, 1, Integer::sum);
                nearest.merge(id, Math.max(Math.abs(x), Math.abs(z)), Math::min);
            }
            for (var id : ids) h.assertTrue(counts.getOrDefault(id, 0) >= 100 && nearest.getOrDefault(id, 9999) <= 1536,
                    "Each biome needs meaningful coverage and reasonable exploration distance; seed=" + seed
                            + " counts=" + counts + " nearest=" + nearest);
            System.out.println("Vale biome distribution seed=" + seed + " samples=4225 counts=" + counts
                    + " nearest Chebyshev blocks=" + nearest);
        }
        // Generate finished chunks for the actual fresh server seed, rather than only sampling noise.
        var random = vale.getChunkSource().randomState();
        var targets = new HashMap<String, BlockPos>();
        for (int radius = 0; radius <= 2048 && targets.size() < 3; radius += 64) {
            for (int x = -radius; x <= radius; x += 64) for (int z = -radius; z <= radius; z += 64) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                String id = generator.getBiomeSource().getNoiseBiome(x >> 2, 20, z >> 2, random.sampler())
                        .unwrapKey().orElseThrow().location().getPath();
                int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, vale, random);
                if (y > 66) targets.putIfAbsent(id, new BlockPos(x, y, z));
            }
        }
        h.assertTrue(targets.size() == 3, "Fresh server seed must offer dry terrain in every biome within 2048 blocks");
        for (var id : ids) {
            var pos = targets.get(id);
            var chunk = vale.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            var expected = switch (id) {
                case "pink_meadows" -> Blocks.GRASS_BLOCK;
                case "memory_forest" -> Blocks.PODZOL;
                default -> Blocks.MYCELIUM;
            };
            int ground = 0, clear = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int bx = chunk.getPos().getMinBlockX() + x, bz = chunk.getPos().getMinBlockZ() + z;
                for (int y = 63; y <= 101; y++) {
                    var floor = new BlockPos(bx, y, bz);
                    if (vale.getBiome(floor).is(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, id))
                            && vale.getBlockState(floor).is(expected)) {
                        ground++;
                        if (vale.getBlockState(floor.above()).getCollisionShape(vale, floor.above()).isEmpty()
                                && vale.getBlockState(floor.above(2)).getCollisionShape(vale, floor.above(2)).isEmpty()) clear++;
                    }
                }
            }
            h.assertTrue(ground > 0 && clear > 0, "Finished biome needs its own ground and standing clearance: " + id);
            if (id.equals("pink_meadows")) h.assertTrue(clear >= 64, "Meadows must retain substantial open ground");
            System.out.println("Vale finished biome=" + id + " sample=" + pos + " ground=" + ground + " clear=" + clear);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_vale_permissions")
    public static void valeCommandsRequireJvmOptInOperatorAndPlayer(GameTestHelper h) {
        var player = player(h);
        String original = System.getProperty(MushroomVale.DEV_PROPERTY);
        try {
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            h.assertTrue(commands().getRoot().getChild("dudunka_dev") == null, "Normal servers must not register developer commands");
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var dispatcher = commands();
            h.assertTrue(dispatcher.getRoot().getChild("dudunka_dev") != null, "Opt-in must register the command");
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(0), ENTER);
            rejected(h, dispatcher, player.server.createCommandSourceStack(), ENTER);
            System.clearProperty(MushroomVale.DEV_PROPERTY);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
        } finally {
            if (original == null) System.clearProperty(MushroomVale.DEV_PROPERTY);
            else System.setProperty(MushroomVale.DEV_PROPERTY, original);
            cleanup(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_roundtrip")
    public static void valeRoundTripKeepsPlayerIdentityAndAllCompanionsAtDeparture(GameTestHelper h) {
        var player = player(h);
        var source = player.serverLevel();
        var origin = player.blockPosition().immutable();
        UUID playerId = player.getUUID();
        var companions = new ArrayList<Companion>();
        var snapshots = new HashMap<UUID, CompanionTravelSnapshot>();
        var arrival = new ArrivalFixture(h);
        try {
            for (var kind : Kind.values()) {
                var companion = DudunkaMod.TYPES.get(kind).get().create(source);
                companion.moveTo(player.getX() + 3 + kind.ordinal(), player.getY(), player.getZ(), 0, 0);
                companion.initialize(playerId, origin);
                companion.setNoAi(true);
                source.addFreshEntity(companion);
                companions.add(companion);
                snapshots.put(companion.getUUID(), companionTravelSnapshot(companion));
            }
            var dispatcher = serverCommands(h);
            h.assertTrue(execute(dispatcher, player, ENTER) == 1 && player.level().dimension().equals(MushroomVale.LEVEL),
                    "Actual command must transfer the player to Vale");
            var returns = MushroomValeReturns.get(player.server);
            var point = returns.point(playerId);
            h.assertTrue(point != null && point.pos().equals(origin) && point.dimension().equals(source.dimension().location()),
                    "Departure must be saved before travel");
            h.assertTrue(player.saveWithoutId(new CompoundTag()).getString("Dimension").equals(MushroomVale.ID.toString()),
                    "Vanilla player save must retain the new dimension location");
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            h.assertTrue(point.equals(returns.point(playerId)), "Repeated entry must not overwrite the return bookmark");
            h.assertTrue(MushroomVale.safeLanding(player.serverLevel(), player.blockPosition(), player), "Arrival must be safe");
            execute(dispatcher, player, RETURN);
            h.assertTrue(player.serverLevel() == source && player.blockPosition().distSqr(origin) <= 25
                            && player.getUUID().equals(playerId) && player.getYRot() == 37 && player.getXRot() == 12
                            && player.fallDistance == 0 && player.getDeltaMovement().lengthSqr() == 0
                            && returns.point(playerId) == null,
                    "Return must preserve player identity and orientation, clear fall motion, and consume the bookmark");
            for (var companion : companions) {
                var before=snapshots.get(companion.getUUID());
                h.assertTrue(source.getEntity(companion.getUUID()) == companion
                                && player.server.getLevel(MushroomVale.LEVEL).getEntity(companion.getUUID()) == null
                                && before!=null && before.owner().equals(companion.ownerId())
                                && before.stage()==companion.stage() && before.trust()==companion.trust()
                                && before.position().equals(companion.position())
                                && before.persistentData().equals(companionTravelSnapshot(companion).persistentData()),
                        "Travel must leave each companion instance, UUID, owner, position, care, home and stable saved state at departure");
            }
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), RETURN);
        } finally {
            companions.forEach(Entity::discard);
            cleanup(player);
            arrival.close();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_vale_bookmarks")
    public static void valeReturnBookmarksSerializePrivatelyAndRejectMalformedRows(GameTestHelper h) {
        var returns = new MushroomValeReturns();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var point = new MushroomValeReturns.ReturnPoint(Level.NETHER.location(), new BlockPos(-20, 70, 9), 25, -15);
        returns.remember(first, point);
        var tag = returns.save(new CompoundTag());
        var rows = tag.getList("Returns", Tag.TAG_COMPOUND);
        var malformed = rows.getCompound(0).copy();
        malformed.putUUID("Player", second);
        malformed.putFloat("Yaw", Float.NaN);
        rows.add(malformed);
        var recursive = rows.getCompound(0).copy();
        recursive.putUUID("Player", UUID.randomUUID());
        recursive.putString("Dimension", MushroomVale.ID.toString());
        rows.add(recursive);
        var missing = rows.getCompound(0).copy();
        missing.putUUID("Player", UUID.randomUUID());
        missing.remove("Pos");
        rows.add(missing);
        var restored = MushroomValeReturns.load(tag);
        h.assertTrue(point.equals(restored.point(first)) && restored.point(second) == null
                        && restored.save(new CompoundTag()).getList("Returns", Tag.TAG_COMPOUND).size() == 1,
                "Reload must preserve another-dimension bookmark, keep owners separate, and ignore corrupt/recursive rows");
        restored.forget(first);
        h.assertTrue(restored.save(new CompoundTag()).getList("Returns", Tag.TAG_COMPOUND).isEmpty(),
                "Successful return must be persisted as a removed bookmark");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_vale_safety")
    public static void valeRejectsUnsafeDepartureHazardsAndPassengers(GameTestHelper h) {
        var player = player(h);
        var pos = player.blockPosition();
        try {
            var dispatcher = serverCommands(h);
            for (var block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.FIRE, Blocks.MAGMA_BLOCK,
                    Blocks.CACTUS, Blocks.POWDER_SNOW, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.SWEET_BERRY_BUSH)) {
                h.getLevel().setBlockAndUpdate(pos.east(), block.defaultBlockState());
                h.assertTrue(!MushroomVale.safeLanding(h.getLevel(), pos, player), "Nearby hazard must reject landing: " + block);
                rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
                h.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            }
            h.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            var passenger = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
            h.getLevel().addFreshEntity(passenger);
            passenger.startRiding(player, true);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            passenger.stopRiding();
            passenger.discard();
            h.assertTrue(player.serverLevel() == h.getLevel() && MushroomValeReturns.get(player.server).point(player.getUUID()) == null,
                    "Rejected entry must not move player or create a bookmark");
        } finally { cleanup(player); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_fallback")
    public static void valeReturnFallsBackForMissingRemovedAndUnsafeBookmarks(GameTestHelper h) {
        var player = player(h);
        var overworld = player.server.overworld();
        var oldSpawn = overworld.getSharedSpawnPos();
        float oldAngle = overworld.getSharedSpawnAngle();
        var spawn = player.blockPosition().immutable();
        var arrival = new ArrivalFixture(h);
        try {
            overworld.setDefaultSpawnPos(spawn, 0);
            var dispatcher = serverCommands(h);
            var returns = MushroomValeReturns.get(player.server);
            for (int scenario = 0; scenario < 3; scenario++) {
                execute(dispatcher, player, ENTER);
                if (scenario == 0) returns.forget(player.getUUID());
                if (scenario == 1) returns.remember(player.getUUID(), new MushroomValeReturns.ReturnPoint(
                        ResourceLocation.fromNamespaceAndPath("dudunka", "removed_test_dimension"), spawn, 0, 0));
                if (scenario == 2) returns.remember(player.getUUID(), new MushroomValeReturns.ReturnPoint(
                        Level.OVERWORLD.location(), new BlockPos(spawn.getX(), 300, spawn.getZ()), 0, 0));
                execute(dispatcher, player, RETURN);
                h.assertTrue(player.serverLevel() == overworld && player.blockPosition().distSqr(spawn) <= 25
                                && MushroomVale.safeLanding(overworld, player.blockPosition(), player),
                        "Missing, removed, or unsafe departure must fall back to safe Overworld spawn");
            }
        } finally {
            overworld.setDefaultSpawnPos(oldSpawn, oldAngle);
            cleanup(player);
            arrival.close();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_unsafe_return")
    public static void valeUnsafeReturnRetainsBookmarkUntilLandingIsRepaired(GameTestHelper h) {
        var player = player(h);
        var overworld = player.server.overworld();
        var oldSpawn = overworld.getSharedSpawnPos();
        float oldAngle = overworld.getSharedSpawnAngle();
        var blocks = new HashMap<BlockPos, BlockState>();
        var arrival = new ArrivalFixture(h);
        try {
            var dispatcher = serverCommands(h);
            execute(dispatcher, player, ENTER);
            var returns = MushroomValeReturns.get(player.server);
            var departure = returns.point(player.getUUID());
            // One remote chunk, with a hazardous top surface and no safe surface below it.
            overworld.getChunk(256, 256);
            for (var p : BlockPos.betweenClosed(new BlockPos(4096, 300, 4096), new BlockPos(4111, 300, 4111))) {
                blocks.put(p.immutable(), overworld.getBlockState(p));
                overworld.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
            overworld.setDefaultSpawnPos(new BlockPos(4104, 301, 4104), 0);
            var missing = new MushroomValeReturns.ReturnPoint(
                    ResourceLocation.fromNamespaceAndPath("dudunka", "removed_test_dimension"), departure.pos(), 0, 0);
            returns.remember(player.getUUID(), missing);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), RETURN);
            h.assertTrue(player.level().dimension().equals(MushroomVale.LEVEL) && missing.equals(returns.point(player.getUUID())),
                    "Unsafe fallback must refuse travel without discarding the bookmark");
            // Restoring a safe recorded departure makes retry succeed without reconstructing anything.
            returns.remember(player.getUUID(), departure);
            execute(dispatcher, player, RETURN);
            h.assertTrue(player.serverLevel() == overworld && returns.point(player.getUUID()) == null,
                    "Retry after repairing a return point must succeed and consume the bookmark");
        } finally {
            blocks.forEach(overworld::setBlockAndUpdate);
            overworld.setDefaultSpawnPos(oldSpawn, oldAngle);
            cleanup(player);
            arrival.close();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "isolated_vale_veto")
    public static void valeTravelVetoKeepsBookmarksAndBlockedArrivalDoesNotMovePlayer(GameTestHelper h) {
        var player = player(h);
        var dispatcher = serverCommands(h);
        var source = player.serverLevel();
        var vale = player.server.getLevel(MushroomVale.LEVEL);
        var returns = MushroomValeReturns.get(player.server);
        var previous = new MushroomValeReturns.ReturnPoint(Level.NETHER.location(), new BlockPos(5, 70, 5), 0, 0);
        Consumer<EntityTravelToDimensionEvent> veto = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        var blocks = new HashMap<BlockPos, BlockState>();
        var arrival = new ArrivalFixture(h);
        try {
            returns.remember(player.getUUID(), previous);
            MinecraftForge.EVENT_BUS.addListener(veto);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            h.assertTrue(player.serverLevel() == source && previous.equals(returns.point(player.getUUID())),
                    "Vetoed entry must restore the previous bookmark");
            MinecraftForge.EVENT_BUS.unregister(veto);
            execute(dispatcher, player, ENTER);
            var departure = returns.point(player.getUUID());
            MinecraftForge.EVENT_BUS.addListener(veto);
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), RETURN);
            h.assertTrue(player.serverLevel() == vale && departure.equals(returns.point(player.getUUID())),
                    "Vetoed return must retain the departure bookmark");
            MinecraftForge.EVENT_BUS.unregister(veto);
            execute(dispatcher, player, RETURN);
            // Obstruct every candidate column at its actual height, including flat legacy worlds.
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
                var p = vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
                blocks.put(p.immutable(), vale.getBlockState(p));
                vale.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
            // Also obstruct near-anchor candidates below any taller generated canopy.
            for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) {
                var p = new BlockPos(x, 63, z);
                blocks.putIfAbsent(p, vale.getBlockState(p));
                vale.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
            }
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            h.assertTrue(player.serverLevel() == source && returns.point(player.getUUID()) == null,
                    "Blocked arrival must leave player and existing terrain untouched by the command");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
            blocks.forEach(vale::setBlockAndUpdate);
            cleanup(player);
            arrival.close();
        }
        h.succeed();
    }
}
