package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import io.github.golovinss.dudunka.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.UUID;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class PufikTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void registrationAttributesAndIdentity(GameTestHelper h) {
        var type = DudunkaMod.PUFIK.get();
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals("dudunka:pufik"), "Stable independent Pufik ID");
        h.assertTrue(type.getCategory() == MobCategory.CREATURE && type.getWidth() == .68f && type.getHeight() == .62f,
                "Small friendly creature dimensions");
        h.assertTrue(SpawnPlacements.getPlacementType(type) == SpawnPlacements.Type.ON_GROUND, "Registered ground spawn placement");
        var pufik = type.create(h.getLevel());
        h.assertTrue(pufik != null && pufik.getAttributeValue(Attributes.MAX_HEALTH) == 8
                && pufik.getAttributeValue(Attributes.MOVEMENT_SPEED) == .16, "Registered Pufik attributes");
        h.assertTrue(!pufik.isPersistenceRequired(),
                "Natural inhabitants do not inherit companion ownership or forced persistence");
        h.assertTrue(Kind.values().length == 3 && java.util.Arrays.stream(Kind.values()).noneMatch(k -> k.id.equals("pufik"))
                && DudunkaMod.TYPES.size() == 3 && !DudunkaMod.TYPES.containsValue(DudunkaMod.PUFIK),
                "Family entity identities remain separate");
        pufik.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=180, batch="isolated_pufik_spawn")
    public static void spawnRulesRequireValeForestAndSafeGround(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var vale = server.getLevel(MushroomVale.LEVEL);
        var forest = server.registryAccess().registryOrThrow(Registries.BIOME).get(Pufik.HABITAT);
        var entries = forest.getMobSettings().getMobs(MobCategory.CREATURE).unwrap();
        h.assertTrue(entries.stream().anyMatch(e -> e.type == DudunkaMod.PUFIK.get()
                        && e.minCount == 1 && e.maxCount == 2 && e.getWeight().asInt() == 40),
                "Forest adds a small 1–2 Pufik group without replacing mooshrooms");
        h.assertTrue(entries.stream().anyMatch(e -> e.type == net.minecraft.world.entity.EntityType.MOOSHROOM),
                "Existing forest inhabitants remain");
        BlockPos site = null;
        // Resolve the generated biome directly; the selected column must match the actual spawn predicate.
        outer: for (int x = 32; x < 160; x += 16) for (int z = 32; z < 160; z += 16) {
            var candidate = new BlockPos(x + 8, 80, z + 8);
            if (vale.getBiome(candidate).is(Pufik.HABITAT)) { site = candidate; break outer; }
        }
        h.assertTrue(site != null, "A forest biome is reachable in sampled Vale terrain");
        vale.getChunk(site.getX() >> 4, site.getZ() >> 4);
        // Use a tiny reversible surface fixture at the biome's sampled quart position.
        var saved = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        try {
            for (var pos : BlockPos.betweenClosed(site.offset(-1,-2,-1), site.offset(1,1,1))) {
                saved.put(pos.immutable(), vale.getBlockState(pos));
                vale.setBlock(pos, pos.getY() == site.getY()-1 ? Blocks.MYCELIUM.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            h.assertTrue(vale.getBiome(site).is(Pufik.HABITAT), "Fixture lies in the forest biome");
            h.assertTrue(Pufik.inHabitat(vale, site), "Vale forest is Pufik's natural habitat");
            BlockPos otherBiome = null;
            outer: for (int x = -256; x <= 256; x += 64) for (int z = -256; z <= 256; z += 64) {
                var candidate = new BlockPos(x, 80, z);
                if (!vale.getBiome(candidate).is(Pufik.HABITAT)) { otherBiome = candidate; break outer; }
            }
            h.assertTrue(otherBiome != null && !Pufik.inHabitat(vale, otherBiome),
                    "Other Vale biomes cannot naturally spawn Pufik");
            h.assertTrue(SpawnPlacements.checkSpawnRules(DudunkaMod.PUFIK.get(), vale, MobSpawnType.NATURAL, site, vale.random),
                    "Safe Vale forest ground permits natural Pufik spawn");
            h.assertTrue(!SpawnPlacements.checkSpawnRules(DudunkaMod.PUFIK.get(), h.getLevel(), MobSpawnType.NATURAL,
                    h.absolutePos(new BlockPos(1,2,1)), h.getLevel().random), "Overworld spawn is refused");
            // Keep actual ground/clearance safety independent of spawn-list weight.
            for (var hazard : java.util.List.of(Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.POWDER_SNOW,
                    Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE)) {
                vale.setBlock(site, hazard.defaultBlockState(), 18);
                h.assertTrue(!Pufik.canSpawn(DudunkaMod.PUFIK.get(), vale, MobSpawnType.NATURAL, site, vale.random),
                        "Hazardous body position is refused: " + hazard);
            }
            vale.setBlock(site, Blocks.AIR.defaultBlockState(), 18);
            vale.setBlock(site.above(), Blocks.STONE.defaultBlockState(), 18);
            h.assertTrue(!Pufik.canSpawn(DudunkaMod.PUFIK.get(), vale, MobSpawnType.NATURAL, site, vale.random),
                    "Blocked head clearance is refused");
            vale.setBlock(site.above(), Blocks.AIR.defaultBlockState(), 18);
            vale.setBlock(site.below(), Blocks.WATER.defaultBlockState(), 18);
            h.assertTrue(!Pufik.canSpawn(DudunkaMod.PUFIK.get(), vale, MobSpawnType.NATURAL, site, vale.random),
                    "Water floor is refused");
            vale.setBlock(site.below(), Blocks.MYCELIUM.defaultBlockState(), 18);
            vale.setBlock(site.east().below(), Blocks.AIR.defaultBlockState(), 18);
            vale.setBlock(site.east().below(2), Blocks.AIR.defaultBlockState(), 18);
            h.assertTrue(!Pufik.canSpawn(DudunkaMod.PUFIK.get(), vale, MobSpawnType.NATURAL, site, vale.random),
                    "Cliff edge is refused");
        } finally {
            saved.forEach((pos,state) -> vale.setBlock(pos,state,18));
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void peacefulInteractionAndSaveLoad(GameTestHelper h) {
        var level = h.getLevel();
        var pufik = DudunkaMod.PUFIK.get().create(level);
        var pos = h.absolutePos(new BlockPos(2,2,2));
        pufik.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        pufik.setNoAi(true);
        level.addFreshEntity(pufik);
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "PufikTest"));
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.connection = new ServerGamePacketListenerImpl(player.server, connection, player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        h.assertTrue(pufik.getTarget() == null && pufik.getPassengers().isEmpty()
                        && pufik.getNavigation() != null && pufik.getAttribute(Attributes.ATTACK_DAMAGE) == null,
                "Pufik has no target or attack attribute");
        h.assertTrue(pufik.interact(player, InteractionHand.MAIN_HAND).consumesAction() && pufik.emote() == Pufik.HAPPY,
                "Right click greets and triggers happy animation");
        UUID id = pufik.getUUID();
        pufik.setCustomName(Component.literal("Little Pufik"));
        var saved = pufik.saveWithoutId(new CompoundTag());
        h.assertTrue(!saved.contains("FamilyOwner") && !saved.contains("Trust") && !saved.contains("GrowthTicks"),
                "Pufik save data has no companion progression or ownership");
        var restored = DudunkaMod.PUFIK.get().create(level);
        restored.load(saved);
        h.assertTrue(restored.getUUID().equals(id) && restored.hasCustomName()
                        && !restored.removeWhenFarAway(100000) && restored.getHealth() == pufik.getHealth(),
                "Named Pufik identity and vanilla health survive save/load");
        var ordinary = DudunkaMod.PUFIK.get().create(level);
        h.assertTrue(!ordinary.isPersistenceRequired() && ordinary.removeWhenFarAway(100000),
                "Ordinary naturally spawned Pufik may despawn");
        pufik.discard(); restored.discard(); ordinary.discard();
        h.succeed();
    }
}
