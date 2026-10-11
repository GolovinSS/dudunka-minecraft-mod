package io.github.golovinss.dudunka;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared player-only travel safety and bookmarks for the gateway and opt-in developer route. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID)
public final class MushroomVale {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    public static final String DEV_PROPERTY = "dudunka.devMushroomVale";
    static final BlockPos ENTRY = new BlockPos(8, 64, 8);
    private MushroomVale() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dudunka")
                .then(Commands.literal("return").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var result = returnPlayer(player);
                    tell(player, result);
                    return result.success() ? 1 : 0;
                })));
        if (!Boolean.getBoolean(DEV_PROPERTY)) return;
        dispatcher.register(Commands.literal("dudunka_dev").requires(source -> source.hasPermission(2))
                .then(Commands.literal("mushroom_vale")
                        .then(Commands.literal("enter").executes(context -> enter(context.getSource())))
                        .then(Commands.literal("return").executes(context -> leave(context.getSource())))
                        .then(FamilyGatewayDevCommand.command())
                        .then(MushroomValeCottagePreview.command())
                        .then(MushroomValeCottagePreview.residentCommand())
                        .then(MushroomValeCottagePreview.decorCommand())));
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    private static ServerPlayer traveler(CommandSourceStack source) throws CommandSyntaxException {
        if (!Boolean.getBoolean(DEV_PROPERTY) || !source.hasPermission(2)) throw error("Developer travel is disabled.");
        var player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.isVehicle())
            throw error("Travel requires a living, awake player without a mount or passengers.");
        return player;
    }

    public enum TravelResult {
        ENTERED, RETURNED, FALLBACK, PLAYER_UNSAFE, ALREADY_THERE, DEPARTURE_UNSAFE,
        UNAVAILABLE, ARRIVAL_UNSAFE, DENIED, WRONG_DIMENSION, RETURN_UNSAFE;
        public boolean success() { return this == ENTERED || this == RETURNED || this == FALLBACK; }
        public Component message() { return Component.translatable("message.dudunka.gateway.travel." + name().toLowerCase(java.util.Locale.ROOT)); }
    }

    public static void returnHint(ServerPlayer player) {
        var command = Component.literal("/dudunka return").withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.AQUA)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/dudunka return")));
        player.displayClientMessage(Component.translatable("message.dudunka.gateway.return_hint", command), false);
    }

    public static void tell(ServerPlayer player, TravelResult result) {
        player.displayClientMessage(result.message(), false);
        if (result == TravelResult.ENTERED || player.level().dimension().equals(LEVEL)) returnHint(player);
    }

    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(LEVEL)) returnHint(player);
    }

    private static boolean canTravel(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger() && !player.isVehicle();
    }

    private static int enter(CommandSourceStack source) throws CommandSyntaxException {
        var result = enterPlayer(traveler(source));
        if (!result.success()) throw new SimpleCommandExceptionType(result.message()).create();
        source.sendSuccess(result::message, false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        var result = returnPlayer(traveler(source));
        if (!result.success()) throw new SimpleCommandExceptionType(result.message()).create();
        source.sendSuccess(result::message, false);
        return 1;
    }

    /** Caller authorizes entry (gateway owner or developer command). Only explicit travel loads a target chunk. */
    public static TravelResult enterPlayer(ServerPlayer player) {
        if (!canTravel(player)) return TravelResult.PLAYER_UNSAFE;
        if (player.level().dimension().equals(LEVEL)) return TravelResult.ALREADY_THERE;
        if (!safeLanding(player.serverLevel(), player.blockPosition(), player)) return TravelResult.DEPARTURE_UNSAFE;
        var destination = player.server.getLevel(LEVEL);
        if (destination == null) return TravelResult.UNAVAILABLE;
        // Prefer the existing return gate's clear approaches; never land on its roof.
        destination.getChunk(ENTRY.getX() >> 4, ENTRY.getZ() >> 4);
        var gate = MushroomValeReturnGateway.find(destination);
        var pos = gate == null ? null : MushroomValeReturnGateway.landing(destination, gate, player);
        if (pos == null) pos = surfaceLanding(destination, ENTRY, player);
        if (pos == null) return TravelResult.ARRIVAL_UNSAFE;
        var returns = MushroomValeReturns.get(player.server);
        var previous = returns.point(player.getUUID());
        returns.remember(player.getUUID(), new MushroomValeReturns.ReturnPoint(
                player.level().dimension().location(), player.blockPosition().immutable(), player.getYRot(), player.getXRot()));
        if (!move(player, destination, pos, player.getYRot(), player.getXRot())) {
            if (previous == null) returns.forget(player.getUUID()); else returns.remember(player.getUUID(), previous);
            return TravelResult.DENIED;
        }
        MysteryAdventure.get(player.server).unlockThrough(player.getUUID(),5);
        return TravelResult.ENTERED;
    }

    public static TravelResult returnPlayer(ServerPlayer player) {
        if (!canTravel(player)) return TravelResult.PLAYER_UNSAFE;
        if (!player.level().dimension().equals(LEVEL)) return TravelResult.WRONG_DIMENSION;
        var returns = MushroomValeReturns.get(player.server);
        var point = returns.point(player.getUUID());
        var destination = point == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, point.dimension()));
        var pos = destination == null ? null : landingNear(destination, point.pos(), player);
        boolean fallback = pos == null;
        if (fallback) {
            destination = player.server.overworld();
            pos = spawnLanding(destination, player);
        }
        if (pos == null) return TravelResult.RETURN_UNSAFE;
        if (!move(player, destination, pos, point == null ? 0 : point.yaw(), point == null ? 0 : point.pitch())) return TravelResult.DENIED;
        returns.forget(player.getUUID());
        return fallback ? TravelResult.FALLBACK : TravelResult.RETURNED;
    }

    private static boolean move(ServerPlayer player, ServerLevel destination, BlockPos pos, float yaw, float pitch) {
        player.teleportTo(destination, pos.getX() + .5, pos.getY(), pos.getZ() + .5, yaw, pitch);
        if (player.serverLevel() != destination) return false; // Forge travel event may veto the transfer.
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        return true;
    }

    private static BlockPos landingNear(ServerLevel level, BlockPos anchor, ServerPlayer player) {
        // Explicit player travel loads only the target chunk normally. No force-load or persistent tickets.
        level.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        for (int radius = 0; radius <= 4; radius++)
            for (int dy : new int[]{0, 1, -1, 2, -2})
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var pos = anchor.offset(dx, dy, dz);
                    if ((pos.getX() >> 4) == (anchor.getX() >> 4) && (pos.getZ() >> 4) == (anchor.getZ() >> 4)
                            && insideChunk(pos) && safeLanding(level, pos, player)) return pos;
                }
        return null;
    }

    private static BlockPos spawnLanding(ServerLevel level, ServerPlayer player) {
        var spawn = level.getSharedSpawnPos();
        var nearby = landingNear(level, spawn, player);
        if (nearby != null) return nearby;
        return surfaceLanding(level, spawn, player);
    }

    private static BlockPos surfaceLanding(ServerLevel level, BlockPos anchor, ServerPlayer player) {
        // Terrain height varies. Check only interior columns of this one chunk; never terraform a landing.
        level.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        int x = (anchor.getX() >> 4) << 4, z = (anchor.getZ() >> 4) << 4;
        for (int dx = 1; dx < 15; dx++) for (int dz = 1; dz < 15; dz++) {
            var pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x + dx, 0, z + dz));
            if (safeLanding(level, pos, player)) return pos;
        }
        return null;
    }

    private static boolean insideChunk(BlockPos pos) {
        return (pos.getX() & 15) > 0 && (pos.getX() & 15) < 15 && (pos.getZ() & 15) > 0 && (pos.getZ() & 15) < 15;
    }

    /** Read-only, loaded-area landing check using the standing player size, even when crouching/flying. */
    public static boolean safeLanding(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 2 >= level.getMaxBuildHeight()) return false;
        var box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        for (var p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
            if (!level.hasChunkAt(p)) return false;
            var state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                    || state.is(Blocks.END_GATEWAY)) return false;
        }
        var floor = level.getBlockState(pos.below());
        return !floor.is(BlockTags.LEAVES) && !floor.is(DudunkaMod.FAMILY_GATEWAY.get())
                && floor.isFaceSturdy(level, pos.below(), Direction.UP)
                && level.noCollision(player, box)
                && level.getEntities(player, box, entity -> entity.isAlive()).isEmpty();
    }
}
