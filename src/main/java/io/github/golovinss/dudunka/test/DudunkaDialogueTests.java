package io.github.golovinss.dudunka;

import com.mojang.authlib.GameProfile;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public class DudunkaDialogueTests {
    private record Fixture(ServerPlayer owner, ServerPlayer other, List<Packet<?>> ownerPackets,
                           List<Packet<?>> otherPackets, Companion dudunka, Companion second) {}

    private static ServerPlayer player(GameTestHelper h, String name, List<Packet<?>> packets, BlockPos pos) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        player.connection = new ServerGamePacketListenerImpl(player.server, connection, player) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static Companion companion(GameTestHelper h, Kind kind, UUID owner, BlockPos pos) {
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++)
            h.getLevel().setBlock(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
        var mob = DudunkaMod.TYPES.get(kind).get().create(h.getLevel());
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mob.initialize(owner, pos);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static Fixture fixture(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(4, 3, 4));
        var ownerPackets = new ArrayList<Packet<?>>();
        var otherPackets = new ArrayList<Packet<?>>();
        var owner = player(h, "DialogueOwner", ownerPackets, origin);
        var other = player(h, "OtherPlayer", otherPackets, origin.offset(0, 0, 5));
        var first = companion(h, Kind.DUDUNKA, owner.getUUID(), origin);
        var second = companion(h, Kind.DUDUNKA, owner.getUUID(), origin.offset(0, 0, 2));
        return new Fixture(owner, other, ownerPackets, otherPackets, first, second);
    }

    private static void cleanup(Fixture f) {
        f.dudunka().setNoAi(true);
        f.second().setNoAi(true);
        f.dudunka().discard();
        f.second().discard();
        f.owner().remove(Entity.RemovalReason.DISCARDED);
        f.other().remove(Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_eligibility")
    public static void dialogueEligibilityRequiresDudunkaOwnedNearbyAndAvailableOwner(GameTestHelper h) {
        var f = fixture(h);
        var mob = f.dudunka();
        var owner = f.owner();
        try {
            h.assertTrue(DudunkaDialogueGoal.eligible(mob, owner), "Owned Dudunka may start near her awake owner");
            h.assertTrue(!DudunkaDialogueGoal.eligible(f.second(), f.other()), "A different owner's Dudunka cannot join this scene");
            h.assertTrue(!DudunkaDialogueGoal.eligible(companion(h, Kind.MARUSYA, owner.getUUID(), mob.blockPosition().offset(1, 0, 0)), owner),
                    "Marusya can never initiate Dudunka dialogue");

            mob.setPos(mob.getX() + 13, mob.getY(), mob.getZ());
            h.assertTrue(!DudunkaDialogueGoal.eligible(mob, owner), "A distant owner cannot start dialogue");
            mob.setPos(owner.getX(), owner.getY(), owner.getZ());
            owner.setGameMode(GameType.SPECTATOR);
            h.assertTrue(!DudunkaDialogueGoal.eligible(mob, owner), "Spectators cannot start dialogue");
            owner.setGameMode(GameType.SURVIVAL);
            owner.startSleeping(owner.blockPosition());
            h.assertTrue(!DudunkaDialogueGoal.eligible(mob, owner), "A sleeping owner cannot start dialogue");
            owner.stopSleeping();
            mob.commandStay(owner, true);
            h.assertTrue(!DudunkaDialogueGoal.eligible(mob, owner), "Stay mode remains authoritative");
            mob.commandStay(owner, false);
            var homeMode = mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            homeMode.putBoolean("HomeMode", true);
            mob.readAdditionalSaveData(homeMode);
            h.assertTrue(!DudunkaDialogueGoal.eligible(mob, owner), "Home mode excludes the conversation");
        } finally { cleanup(f); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_selection")
    public static void dialogueSelectionUsesOnlyLoadedLivingMarusyaFromSameFamily(GameTestHelper h) {
        var f = fixture(h);
        var cat = companion(h, Kind.MARUSYA, f.owner().getUUID(), f.owner().blockPosition().offset(0, 0, 5));
        try {
            h.assertTrue(DudunkaDialogueGoal.marusyaNearby(f.dudunka(), f.owner()), "A loaded same-owner Marusya qualifies nearby");
            h.assertTrue(DudunkaDialogueGoal.choose(true, true) == DudunkaDialogueGoal.Dialogue.MARUSYA,
                    "Eligible selection may choose the fictional Marusya line");
            h.assertTrue(DudunkaDialogueGoal.choose(false, true) == DudunkaDialogueGoal.Dialogue.BIG
                            && DudunkaDialogueGoal.choose(true, false) == DudunkaDialogueGoal.Dialogue.BIG,
                    "The Marusya line is never selected without its eligibility and random choice");
            cat.discard();
            h.assertTrue(!DudunkaDialogueGoal.marusyaNearby(f.dudunka(), f.owner()), "A removed cat no longer qualifies");
            cat = companion(h, Kind.MARUSYA, f.other().getUUID(), f.owner().blockPosition().offset(0, 0, 4));
            h.assertTrue(!DudunkaDialogueGoal.marusyaNearby(f.dudunka(), f.owner()), "Another owner's Marusya does not qualify");
            cat.discard();
            cat = companion(h, Kind.MARUSYA, f.owner().getUUID(), f.owner().blockPosition().offset(0, 0, 10));
            h.assertTrue(!DudunkaDialogueGoal.marusyaNearby(f.dudunka(), f.owner()), "A Marusya beyond eight blocks does not qualify");
        } finally { if (cat.isAlive()) cat.discard(); cleanup(f); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_cooldown")
    public static void dialogueCooldownIsFiveToTenMinutesAndSharedByOwnedDudunkas(GameTestHelper h) {
        var cooldowns = new DudunkaDialogueGoal.Cooldowns();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var random = net.minecraft.util.RandomSource.create(93241);
        long now = 2000;
        h.assertTrue(!cooldowns.due(owner, now, random), "The first scene waits for its initial interval");
        long due = cooldowns.deadline(owner);
        h.assertTrue(due - now >= DudunkaDialogueGoal.MIN_INTERVAL && due - now <= DudunkaDialogueGoal.MAX_INTERVAL,
                "Initial interval must be between five and ten loaded minutes");
        h.assertTrue(!cooldowns.due(owner, due - 1, random) && cooldowns.due(owner, due, random),
                "Scene becomes available at the stored deadline");
        h.assertTrue(cooldowns.claim(owner, due, random) && !cooldowns.claim(owner, due, random),
                "Only one Dudunka for an owner can claim the same due scene");
        long next = cooldowns.deadline(owner);
        h.assertTrue(next - due >= DudunkaDialogueGoal.MIN_INTERVAL && next - due <= DudunkaDialogueGoal.MAX_INTERVAL,
                "Every completed start establishes another five-to-ten-minute interval");
        h.assertTrue(!cooldowns.due(other, due, random) && cooldowns.deadline(other) != next,
                "Cooldowns are private per owner");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_delivery")
    public static void dialogueUsesWaveAndSendsOnePrivatePrefixedLocalizedLine(GameTestHelper h) {
        var f = fixture(h);
        var clock = new AtomicLong(4000);
        var cooldowns = new DudunkaDialogueGoal.Cooldowns();
        try {
            var first = new DudunkaDialogueGoal(f.dudunka(), cooldowns, clock::get);
            var second = new DudunkaDialogueGoal(f.second(), cooldowns, clock::get);
            h.assertTrue(!first.canUse() && !second.canUse(), "Neither friend skips the initial cooldown");
            long due = cooldowns.deadline(f.owner().getUUID());
            clock.set(due);
            h.assertTrue(first.canUse(), "First eligible Dudunka reserves the due scene");
            h.assertTrue(!second.canUse(), "A second Dudunka cannot reserve the same owner's scene");
            first.start();
            first.tick();
            h.assertTrue(f.dudunka().activity() == Activity.WAVE, "Arrival uses the existing synchronized WAVE activity");
            var messages = f.ownerPackets().stream().filter(ClientboundSystemChatPacket.class::isInstance)
                    .map(ClientboundSystemChatPacket.class::cast).map(ClientboundSystemChatPacket::content).toList();
            h.assertTrue(messages.size() == 1 && messages.get(0).toString().contains("dialogue.dudunka.named")
                            && messages.get(0).toString().contains("dialogue.dudunka.big"),
                    "Arrival sends one localized, name-prefixed big-girl line to the owner");
            h.assertTrue(f.otherPackets().stream().noneMatch(ClientboundSystemChatPacket.class::isInstance),
                    "Other players receive no dialogue packet");
            first.tick();
            h.assertTrue(f.ownerPackets().stream().filter(ClientboundSystemChatPacket.class::isInstance).count() == 1,
                    "The WAVE duration cannot repeat the message");
            clock.addAndGet(81);
            h.assertTrue(!first.canContinueToUse(), "The scene ends when the WAVE duration completes");
            first.stop();
        } finally { cleanup(f); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_cancel")
    public static void dialogueCancelsWhenOwnerLeavesWithoutMovingOrMessaging(GameTestHelper h) {
        var f = fixture(h);
        var clock = new AtomicLong(8000);
        var cooldowns = new DudunkaDialogueGoal.Cooldowns();
        try {
            var goal = new DudunkaDialogueGoal(f.dudunka(), cooldowns, clock::get);
            h.assertTrue(!goal.canUse(), "Initial wait must be scheduled");
            clock.set(cooldowns.deadline(f.owner().getUUID()));
            var original = f.dudunka().position();
            h.assertTrue(goal.canUse(), "A due scene may start when the owner is already within greeting range");
            goal.start();
            f.owner().moveTo(f.dudunka().getX() + 20, f.owner().getY(), f.owner().getZ(), 0, 0);
            h.assertTrue(!goal.canContinueToUse(), "Leaving the nearby area cancels the scene");
            goal.stop();
            h.assertTrue(f.dudunka().position().equals(original) && f.dudunka().activity() == Activity.IDLE
                            && f.ownerPackets().stream().noneMatch(ClientboundSystemChatPacket.class::isInstance),
                    "Cancellation stops cleanly, never teleports, and sends no premature line");
        } finally { cleanup(f); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "isolated_dudunkadialogue_regression")
    public static void dialogueAddsNoPersistentFieldsAndCannotRunDuringAnExistingActivity(GameTestHelper h) {
        var f = fixture(h);
        try {
            var before = f.dudunka().saveWithoutId(new net.minecraft.nbt.CompoundTag());
            int waveOrdinal = Activity.WAVE.ordinal();
            f.dudunka().setActivity(Activity.CURIOUS);
            h.assertTrue(!DudunkaDialogueGoal.eligible(f.dudunka(), f.owner()), "Existing family activity retains priority");
            f.dudunka().setActivity(Activity.IDLE);
            var recovering = before.copy();
            recovering.putInt("RecoveryTicks", 200);
            f.dudunka().readAdditionalSaveData(recovering);
            h.assertTrue(f.dudunka().recovering() && !DudunkaDialogueGoal.eligible(f.dudunka(), f.owner()),
                    "Recovery suppresses dialogue");
            var restored = before.copy();
            restored.putInt("RecoveryTicks", 0);
            f.dudunka().readAdditionalSaveData(restored);
            var after = f.dudunka().saveWithoutId(new net.minecraft.nbt.CompoundTag());
            h.assertTrue(before.equals(after) && waveOrdinal == Activity.WAVE.ordinal()
                            && !after.contains("DialogueCooldown") && !after.contains("DialogueState"),
                    "Dialogue introduces no companion NBT and does not reorder Activity IDs");
        } finally { cleanup(f); }
        h.succeed();
    }
}
