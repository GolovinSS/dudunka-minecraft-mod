package io.github.golovinss.dudunka;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/** Bounded explicit Vale blueprint placement, registered only under the existing developer opt-in. */
final class MushroomValeCottagePreview {
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return command("cottage", new String[]{"pufik", "wise"});
    }
    static LiteralArgumentBuilder<CommandSourceStack> decorCommand() {
        return command("decor", new String[]{"bench", "lantern_post"});
    }
    private static LiteralArgumentBuilder<CommandSourceStack> command(String literal, String[] names) {
        var root = Commands.literal(literal);
        for (String name : names) {
            var position = Commands.argument("origin", BlockPosArgument.blockPos());
            String[] directions = {"north", "east", "south", "west"};
            for (int n = 0; n < directions.length; n++) {
                var rotation = Rotation.values()[n];
                position.then(Commands.literal(directions[n]).executes(context -> place(context.getSource(), name,
                        BlockPosArgument.getLoadedBlockPos(context, "origin"), rotation)));
            }
            root.then(Commands.literal(name).then(position));
        }
        return root;
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
    private static int place(CommandSourceStack source, String name, net.minecraft.core.BlockPos origin,
                             Rotation rotation) throws CommandSyntaxException {
        if (!Boolean.getBoolean(MushroomVale.DEV_PROPERTY) || !source.hasPermission(2))
            throw error("Developer Vale previews are disabled.");
        source.getPlayerOrException();
        var level = source.getLevel();
        if (!level.dimension().equals(MushroomVale.LEVEL)) throw error("Place Vale previews in Mushroom Vale only.");
        String suffix = name.equals("pufik") ? "pufik_cottage" : name.equals("wise") ? "wise_fly_agaric_cottage" : name;
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "mushroom_vale/" + suffix));
        if (feature == null || !(feature.config() instanceof MushroomValeDecorFeature.Blueprint config))
            throw error("Vale blueprint is unavailable.");
        int extent = config.radius() + 1;
        var volume = new AABB(origin.offset(-extent, 0, -extent), origin.offset(extent + 1, config.height(), extent + 1));
        var occupants = level.getEntities((net.minecraft.world.entity.Entity) null, volume, entity -> entity.isAlive());
        if (!occupants.isEmpty()) {
            var first = occupants.get(0);
            throw error("Move " + first.getName().getString() + " at " + first.blockPosition().toShortString()
                    + " outside the preview footprint first. No blocks changed.");
        }
        if (!MushroomValeDecorFeature.placeBlueprint(level, origin, config, rotation))
            throw error("Needs loaded chunks, dry level dirt/moss and clear air/flowers across the footprint and halo. No blocks changed.");
        source.sendSuccess(() -> Component.literal("Placed " + suffix + " at " + origin.toShortString()
                + " (" + rotation + "). Explicit preview only; natural frequency unchanged."), false);
        return 1;
    }
    private MushroomValeCottagePreview() {}
}
