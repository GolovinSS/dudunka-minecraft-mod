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
            h.fail("Command should have been rejected: " + command);
        } catch (CommandSyntaxException expected) {}
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
                        && vale.dimensionType().hasFixedTime() && vale.dimensionType().bedWorks(),
                "Prototype type settings must match the documented contract");
        h.assertTrue(vale.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                "Vale must use vanilla noise terrain (use a fresh test world)");
        var generator = (NoiseBasedChunkGenerator) vale.getChunkSource().getGenerator();
        h.assertTrue(generator.generatorSettings().is(MushroomVale.ID)
                        && generator.getBiomeSource() instanceof MultiNoiseBiomeSource
                        && generator.getBiomeSource().possibleBiomes().size() == 1,
                "Separate noise settings and explicit extensible biome source must select one temporary biome");
        var settings = generator.generatorSettings().value();
        h.assertTrue(settings.seaLevel() == 63 && !settings.isAquifersEnabled() && !settings.oreVeinsEnabled(),
                "Low basins use a single water level without noise caves/aquifers/ore veins");
        vale.getChunk(0, 0);
        h.assertTrue(vale.getBlockState(new BlockPos(8, -64, 8)).is(Blocks.BEDROCK)
                        && vale.getBiome(new BlockPos(8, 64, 8)).is(ResourceLocation.withDefaultNamespace("mushroom_fields")),
                "JSON must retain bedrock and the temporary vanilla mushroom biome");
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var id : List.of("glowing_mushroom_forest", "pink_meadows", "memory_forest"))
            h.assertTrue(!biomes.containsKey(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, id)),
                    "Final biomes must not be registered as placeholders");
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
        var snapshots = new HashMap<UUID, CompoundTag>();
        try {
            for (var kind : Kind.values()) {
                var companion = DudunkaMod.TYPES.get(kind).get().create(source);
                companion.moveTo(player.getX() + 3 + kind.ordinal(), player.getY(), player.getZ(), 0, 0);
                companion.initialize(playerId, origin);
                companion.setNoAi(true);
                source.addFreshEntity(companion);
                companions.add(companion);
                snapshots.put(companion.getUUID(), companion.saveWithoutId(new CompoundTag()));
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
                h.assertTrue(source.getEntity(companion.getUUID()) == companion
                                && player.server.getLevel(MushroomVale.LEVEL).getEntity(companion.getUUID()) == null
                                && snapshots.get(companion.getUUID()).equals(companion.saveWithoutId(new CompoundTag())),
                        "Travel must leave each companion instance, UUID, and complete NBT at departure");
            }
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), RETURN);
        } finally {
            companions.forEach(Entity::discard);
            cleanup(player);
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
            rejected(h, dispatcher, player.createCommandSourceStack().withPermission(2), ENTER);
            h.assertTrue(player.serverLevel() == source && returns.point(player.getUUID()) == null,
                    "Blocked arrival must leave player and existing terrain untouched by the command");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
            blocks.forEach(vale::setBlockAndUpdate);
            cleanup(player);
        }
        h.succeed();
    }
}
