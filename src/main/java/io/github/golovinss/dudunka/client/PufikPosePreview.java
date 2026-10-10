package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.MushroomVale;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only, opt-in pose inspection; no entity data, AI or packets are changed. */
@Mod.EventBusSubscriber(modid = DudunkaMod.ID, value = Dist.CLIENT)
public final class PufikPosePreview {
    public enum Mode { OFF, REST, LOOK_UP }
    private static Mode mode = Mode.OFF;

    public static Mode mode() {
        return Boolean.getBoolean(MushroomVale.DEV_PROPERTY) ? mode : Mode.OFF;
    }

    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        if (!Boolean.getBoolean(MushroomVale.DEV_PROPERTY)) return;
        var command = Commands.literal("dudunka_pufik_pose");
        for (Mode value : Mode.values()) {
            String name = value.name().toLowerCase(java.util.Locale.ROOT);
            command.then(Commands.literal(name).executes(context -> {
                mode = value;
                context.getSource().sendSuccess(() -> Component.literal("Pufik client pose: " + name
                        + (value == Mode.OFF ? " (live animations)" : " (all visible Pufiks; AI unchanged)")), false);
                return 1;
            }));
        }
        event.getDispatcher().register(command);
    }

    private PufikPosePreview() {}
}
