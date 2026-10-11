package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.DudunkaMod;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;

/** Forge constructs GameTestServer through Main, bypassing vanilla create()'s test rules. */
@Mod.EventBusSubscriber(modid=DudunkaMod.ID)
@GameTestHolder(DudunkaMod.ID) @PrefixGameTestTemplate(false)
public class DeterministicTestEnvironment {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var rules=event.getServer().overworld().getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,event.getServer());
    }
    @GameTest(template="empty",timeoutTicks=40,batch="isolated_test_environment")
    public static void forgeUsesVanillaGameTestEnvironmentRules(GameTestHelper h) {
        if(h.getLevel().getServer() instanceof GameTestServer) {
            var rules=h.getLevel().getGameRules();
            h.assertTrue(!rules.getBoolean(GameRules.RULE_DOMOBSPAWNING) && !rules.getBoolean(GameRules.RULE_WEATHER_CYCLE),
                    "Automatic GameTests start with vanilla's intended no-random-mobs/no-weather-cycle rules; population tests restore them");
        }
        h.succeed();
    }
}
