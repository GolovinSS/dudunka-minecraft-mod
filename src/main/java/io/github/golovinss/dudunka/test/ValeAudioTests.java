package io.github.golovinss.dudunka.test;

import io.github.golovinss.dudunka.*;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;

/** Actual private packets from all five production playback paths, including simultaneous voices. */
@GameTestHolder("dudunka_music") @PrefixGameTestTemplate(false)
public final class ValeAudioTests {
    private static List<ClientboundSoundPacket> sounds(ValeMusicTests.Site site, ServerPlayer player) {
        return site.packets.get(player.getUUID()).stream().filter(ClientboundSoundPacket.class::isInstance)
                .map(ClientboundSoundPacket.class::cast).toList();
    }
    private static void cue(GameTestHelper h, ClientboundSoundPacket packet, boolean memory, float volume) {
        h.assertTrue(packet.getSound().value().getLocation().equals((memory ? SoundEvents.NOTE_BLOCK_PLING.get()
                : SoundEvents.NOTE_BLOCK_HARP.get()).getLocation()), "Reliable vanilla timbre for this voice");
        h.assertTrue(packet.getSource() == SoundSource.BLOCKS && Math.abs(packet.getVolume() - volume) < .00001F,
                "Puzzle category and balanced cue volume");
    }
    private static void ready(ValeMusicTests.Site site, ServerPlayer p) {
        var data = ValeMusicProgress.get(p.server); var root = site.root; var owner = p.getUUID();
        for (int bell = 0; bell < 3; bell++) data.strike(owner, root, bell);
        for (int flower : new int[]{0,0,1,2,2,3}) data.dance(owner, root, flower);
        data.listenToMemory(owner, root); data.interpretMemory(owner, root, 2);
        data.tuneHarp(owner, root, 0); data.tuneHarp(owner, root, 0); data.tuneHarp(owner, root, 1);
        data.playHarp(owner, root);
    }
    @GameTest(template="empty", timeoutTicks=60, batch="isolated_music_interaction")
    public static void demonstrationsInteractionsAndChordsUseAudiblePrivateBalancedCues(GameTestHelper h) {
        for (var kind : ValeAdventureStructure.Landmark.values()) {
            try (var site = new ValeMusicTests.Site(h, kind)) {
                var p = site.player(); var observer = site.player(); ready(site, p);
                var listening = kind == ValeAdventureStructure.Landmark.MEMORY_STONE ? site.root.above() : site.root;
                site.click(p, listening, InteractionHand.MAIN_HAND);
                if (kind != ValeAdventureStructure.Landmark.SINGING_MUSHROOM) {
                    for (int tick = 0; tick < 100; tick++) {
                        var event = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p);
                        ValeMusicQuest.tick(event); ValeDanceQuest.tick(event); ValeMemoryQuest.tick(event);
                    }
                    var packets = sounds(site, p);
                    int count = switch (kind) {
                        case MUSIC_BELLS, SILVER_WEB_HARP -> 3;
                        case JUMPER_DANCE -> 6;
                        case MEMORY_STONE -> 4;
                        default -> throw new AssertionError(kind);
                    };
                    h.assertTrue(packets.size() == count, "Demonstration retains its note count");
                    for (var packet : packets) cue(h, packet, kind == ValeAdventureStructure.Landmark.MEMORY_STONE,
                            kind == ValeAdventureStructure.Landmark.SILVER_WEB_HARP ? .3F : .9F);
                    site.packets.get(p.getUUID()).clear();
                    var interaction = switch (kind) {
                        case MUSIC_BELLS -> site.bell(0);
                        case JUMPER_DANCE -> site.root.offset(0,0,-5);
                        case MEMORY_STONE -> site.root.offset(-4,1,-3);
                        case SILVER_WEB_HARP -> site.root.offset(-2,1,0);
                        default -> throw new AssertionError(kind);
                    };
                    site.click(p, interaction, InteractionHand.MAIN_HAND);
                    h.assertTrue(sounds(site,p).size() == 1, "Interaction emits exactly one solo cue");
                    cue(h, sounds(site,p).get(0), kind == ValeAdventureStructure.Landmark.MEMORY_STONE, .9F);
                } else {
                    for (int tick = 0; tick < 440; tick++)
                        ValeConcertQuest.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
                    var packets = sounds(site,p);
                    h.assertTrue(packets.size() == 25, "Complete concert keeps its score and simultaneous note count");
                    for (int i = 0; i < packets.size(); i++) {
                        boolean chord = i >= 12 && i <= 14 || i >= 22;
                        boolean duet = i >= 18 && i <= 21;
                        cue(h, packets.get(i), i >= 9 && i <= 11 || i == 19 || i == 21,
                                chord ? .3F : duet ? .45F : .9F);
                    }
                }
                h.assertTrue(sounds(site,observer).isEmpty(), "Puzzle audio stays private in every chapter");
            }
        }
        h.succeed();
    }
}
