package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Additive music-story ledger, deliberately independent of the album's Mystery story. */
public final class ValeMusicProgress extends SavedData {
    public static final String FIRST_NOTE = "dudunka:music_bells";
    public static final String DANCE = "dudunka:jumper_dance";
    public static final String MEMORY = "dudunka:memory_stone";
    public static final String HARP = "dudunka:silver_web_harp";
    public static final String CONCERT = "dudunka:singing_mushroom";
    public enum ConcertResult { READY, NEED_FIRST_NOTE, NEED_DANCE, NEED_MEMORY, NEED_HARP, COMPLETED, ALREADY_COMPLETE }
    public enum HarpResult { TUNED, RETRY, COMPLETED, ALREADY_COMPLETE, NEED_FIRST_NOTE, NEED_DANCE, NEED_MEMORY }
    private static final int[] HARP_HARMONY = {0, 2, 1};
    private record HarpAttempt(BlockPos anchor, int[] tones) {}
    private final Map<UUID, HarpAttempt> harpAttempts = new HashMap<>();
    public enum DanceResult { CORRECT, RETRY, COMPLETED, ALREADY_COMPLETE, NEED_FIRST_NOTE }
    public enum MemoryResult { RETRY, COMPLETED, ALREADY_COMPLETE, NEED_FIRST_NOTE, NEED_DANCE, LISTEN_FIRST }
    private static final int[] DANCE_PATTERN = {0, 0, 1, 2, 2, 3};
    public enum Result { CORRECT, RETRY, COMPLETED, ALREADY_COMPLETE }
    private record Attempt(BlockPos anchor, int next) {}
    private final Map<UUID, CompoundTag> owners = new HashMap<>();
    private final Map<UUID, Attempt> attempts = new HashMap<>(); // Never serialized; no shared block state.
    private final Map<UUID, Attempt> danceAttempts = new HashMap<>();
    private final Map<UUID, BlockPos> memoryListening = new HashMap<>();
    private CompoundTag envelope = new CompoundTag();

    public static ValeMusicProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ValeMusicProgress::load, ValeMusicProgress::new, "dudunka_vale_music");
    }
    public boolean completed(UUID owner) {
        return completed(owner, FIRST_NOTE);
    }
    public boolean concertCompleted(UUID owner) { return completed(owner, CONCERT); }
    public boolean danceCompleted(UUID owner) { return completed(owner, DANCE); }
    public boolean harpCompleted(UUID owner) { return completed(owner, HARP); }
    public boolean memoryCompleted(UUID owner) { return completed(owner, MEMORY); }
    private boolean completed(UUID owner, String id) {
        var row = owners.get(owner);
        if (row == null) return false;
        var chapter = row.getCompound("Chapters").getCompound(id);
        return chapter.contains("Completed", Tag.TAG_BYTE) && chapter.getByte("Completed") == 1;
    }
    public void resetAttempt(UUID owner) { attempts.remove(owner); }

    /** Called only after the server resolves a real landmark interaction. Bell order is low to high. */
    public Result strike(UUID owner, BlockPos anchor, int bell) {
        if (bell < 0 || bell > 2) throw new IllegalArgumentException("Unknown music bell");
        if (completed(owner)) return Result.ALREADY_COMPLETE;
        var previous = attempts.get(owner);
        int next = previous != null && previous.anchor.equals(anchor) ? previous.next : 0;
        if (bell != next) {
            resetAttempt(owner);
            return Result.RETRY;
        }
        if (next < 2) {
            attempts.put(owner, new Attempt(anchor.immutable(), next + 1));
            return Result.CORRECT;
        }
        resetAttempt(owner);
        var row = owners.computeIfAbsent(owner, id -> new CompoundTag());
        var chapters = row.getCompound("Chapters");
        var chapter = chapters.getCompound(FIRST_NOTE);
        chapter.putBoolean("Completed", true);
        chapters.put(FIRST_NOTE, chapter);
        row.put("Chapters", chapters);
        setDirty();
        return Result.COMPLETED;
    }
    public void resetDanceAttempt(UUID owner) { danceAttempts.remove(owner); }

    /** Listening is deliberately transient and belongs to one player at one native anchor. */
    public void listenToMemory(UUID owner, BlockPos anchor) {
        if (!memoryCompleted(owner)) memoryListening.put(owner, anchor.immutable());
    }
    public void resetMemoryListening(UUID owner) { memoryListening.remove(owner); }

    /** One of three story interpretations; choice 2 is the little corner lights together. */
    public MemoryResult interpretMemory(UUID owner, BlockPos anchor, int symbol) {
        if (symbol < 0 || symbol > 2) throw new IllegalArgumentException("Unknown memory symbol");
        if (memoryCompleted(owner)) return MemoryResult.ALREADY_COMPLETE;
        if (!completed(owner)) return MemoryResult.NEED_FIRST_NOTE;
        if (!danceCompleted(owner)) return MemoryResult.NEED_DANCE;
        var listenedAt = memoryListening.get(owner);
        if (listenedAt == null || !listenedAt.equals(anchor)) return MemoryResult.LISTEN_FIRST;
        if (symbol != 2) return MemoryResult.RETRY;
        memoryListening.remove(owner);
        var row = owners.computeIfAbsent(owner, id -> new CompoundTag());
        var chapters = row.getCompound("Chapters");
        var chapter = chapters.getCompound(MEMORY);
        chapter.putBoolean("Completed", true);
        chapters.put(MEMORY, chapter);
        row.put("Chapters", chapters);
        setDirty();
        return MemoryResult.COMPLETED;
    }

    /** Spatial tap groups, never timed; other ring flowers (4) are freely retryable. */
    public DanceResult dance(UUID owner, BlockPos anchor, int flower) {
        if (flower < 0 || flower > 4) throw new IllegalArgumentException("Unknown dance flower");
        if (!completed(owner)) { resetDanceAttempt(owner); return DanceResult.NEED_FIRST_NOTE; }
        if (danceCompleted(owner)) return DanceResult.ALREADY_COMPLETE;
        var previous = danceAttempts.get(owner);
        int next = previous != null && previous.anchor.equals(anchor) ? previous.next : 0;
        if (flower != DANCE_PATTERN[next]) { resetDanceAttempt(owner); return DanceResult.RETRY; }
        if (next + 1 < DANCE_PATTERN.length) {
            danceAttempts.put(owner, new Attempt(anchor.immutable(), next + 1));
            return DanceResult.CORRECT;
        }
        resetDanceAttempt(owner);
        var row = owners.computeIfAbsent(owner, id -> new CompoundTag());
        var chapters = row.getCompound("Chapters");
        var chapter = chapters.getCompound(DANCE);
        chapter.putBoolean("Completed", true);
        chapters.put(DANCE, chapter); row.put("Chapters", chapters); setDirty();
        return DanceResult.COMPLETED;
    }
    public HarpResult harpPrerequisite(UUID owner) {
        if (!completed(owner)) return HarpResult.NEED_FIRST_NOTE;
        if (!danceCompleted(owner)) return HarpResult.NEED_DANCE;
        if (!memoryCompleted(owner)) return HarpResult.NEED_MEMORY;
        return HarpResult.TUNED;
    }
    public void resetHarpAttempt(UUID owner) { harpAttempts.remove(owner); }
    /** Read without creating state. A different saved landmark starts with three middle tones. */
    public int harpTone(UUID owner, BlockPos anchor, int string) {
        if (string < 0 || string > 2) throw new IllegalArgumentException("Unknown harp string");
        var attempt = harpAttempts.get(owner);
        return attempt != null && attempt.anchor.equals(anchor) ? attempt.tones[string] : 1;
    }
    /** Each string is independent: there is no ordering or timing requirement. */
    public HarpResult tuneHarp(UUID owner, BlockPos anchor, int string) {
        int tone = harpTone(owner, anchor, string);
        var gate = harpPrerequisite(owner);
        if (gate != HarpResult.TUNED) return gate;
        var attempt = harpAttempts.get(owner);
        if (attempt == null || !attempt.anchor.equals(anchor)) {
            attempt = new HarpAttempt(anchor.immutable(), new int[]{1, 1, 1});
            harpAttempts.put(owner, attempt);
        }
        attempt.tones[string] = (tone + 1) % 3;
        return HarpResult.TUNED;
    }
    /** Explicit combined chord. A mismatch leaves every selected tone untouched, including replay. */
    public HarpResult playHarp(UUID owner, BlockPos anchor) {
        var gate = harpPrerequisite(owner);
        if (gate != HarpResult.TUNED) return gate;
        for (int string = 0; string < 3; string++)
            if (harpTone(owner, anchor, string) != HARP_HARMONY[string]) return HarpResult.RETRY;
        if (harpCompleted(owner)) return HarpResult.ALREADY_COMPLETE;
        var row = owners.computeIfAbsent(owner, id -> new CompoundTag());
        var chapters = row.getCompound("Chapters");
        var chapter = chapters.getCompound(HARP);
        chapter.putBoolean("Completed", true);
        chapters.put(HARP, chapter); row.put("Chapters", chapters); setDirty();
        return HarpResult.COMPLETED;
    }

    public ConcertResult concertPrerequisite(UUID owner) {
        if (!completed(owner)) return ConcertResult.NEED_FIRST_NOTE;
        if (!danceCompleted(owner)) return ConcertResult.NEED_DANCE;
        if (!memoryCompleted(owner)) return ConcertResult.NEED_MEMORY;
        if (!harpCompleted(owner)) return ConcertResult.NEED_HARP;
        return ConcertResult.READY;
    }
    /** Called by the server only after the entire validated native concert has played. */
    public ConcertResult finishConcert(UUID owner) {
        var gate = concertPrerequisite(owner);
        if (gate != ConcertResult.READY) return gate;
        if (concertCompleted(owner)) return ConcertResult.ALREADY_COMPLETE;
        var row = owners.computeIfAbsent(owner, id -> new CompoundTag());
        var chapters = row.getCompound("Chapters");
        var chapter = chapters.getCompound(CONCERT);
        chapter.putBoolean("Completed", true);
        chapters.put(CONCERT, chapter); row.put("Chapters", chapters); setDirty();
        return ConcertResult.COMPLETED;
    }

    public static ValeMusicProgress load(CompoundTag tag) {
        var data = new ValeMusicProgress();
        data.envelope = tag.copy(); // Preserve future schema/version and fields on rewrite.
        var rows = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            if (!row.hasUUID("Owner")) continue;
            var owner = row.getUUID("Owner");
            boolean complete = data.completed(owner);
            boolean danceComplete = data.danceCompleted(owner);
            boolean memoryComplete = data.memoryCompleted(owner);
            boolean harpComplete = data.harpCompleted(owner);
            boolean concertComplete = data.concertCompleted(owner);
            var merged = data.owners.computeIfAbsent(owner, id -> new CompoundTag());
            merged.merge(row.copy());
            for (String id : new String[]{FIRST_NOTE, DANCE, MEMORY, HARP, CONCERT}) {
                if (!(id.equals(FIRST_NOTE) ? complete : id.equals(DANCE) ? danceComplete : id.equals(MEMORY) ? memoryComplete : id.equals(HARP) ? harpComplete : concertComplete)) continue;
                var chapters = merged.getCompound("Chapters");
                var chapter = chapters.getCompound(id);
                chapter.putBoolean("Completed", true);
                chapters.put(id, chapter);
                merged.put("Chapters", chapters);
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.merge(envelope.copy());
        if (!tag.contains("Version")) tag.putInt("Version", 1);
        var rows = new ListTag();
        owners.forEach((owner, stored) -> {
            var row = stored.copy(); row.putUUID("Owner", owner); rows.add(row);
        });
        // Keep unrecognized rows too: a future version may use a different owner representation.
        var oldRows = envelope.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < oldRows.size(); i++)
            if (!oldRows.getCompound(i).hasUUID("Owner")) rows.add(oldRows.getCompound(i).copy());
        tag.put("Owners", rows);
        return tag;
    }
}
