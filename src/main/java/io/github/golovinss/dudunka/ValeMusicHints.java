package io.github.golovinss.dudunka;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure, disposable proximity snapshots; no Minecraft client, world mutation or saved data. */
public final class ValeMusicHints {
    public static final int MAX_ANCHORS = 125; // 25 loaded chunks, five native start types.
    public static final int REFRESH_TICKS = 40;
    public static final int STALE_TICKS = 60;

    public record Anchor(int x, int y, int z) {
        public double distanceSquared(double px, double py, double pz) {
            double dx = px - (x + .5), dy = py - (y + .5), dz = pz - (z + .5);
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record Snapshot(String dimension, List<Anchor> anchors) {
        public Snapshot {
            Objects.requireNonNull(dimension);
            if (anchors.size() > MAX_ANCHORS) throw new IllegalArgumentException("Too many music landmarks");
            anchors = List.copyOf(anchors);
        }
    }

    /** Change notifications plus a sparse heartbeat only while landmarks are present. */
    public static final class Publisher {
        private Snapshot last;
        private long sentAt;
        public boolean shouldSend(Snapshot next, long tick) {
            if (next.anchors().isEmpty() && (last == null || last.anchors().isEmpty())) {
                last = next;
                return false;
            }
            if (!next.equals(last) || tick < sentAt || tick - sentAt >= REFRESH_TICKS) {
                last = next;
                sentAt = tick;
                return true;
            }
            return false;
        }
    }

    private Object connection, level;
    private Snapshot snapshot;
    private long receivedAt;

    /** Reject queued packets from another connection or dimension without destroying current hints. */
    public boolean accept(Object packetConnection, Object currentConnection, Object currentLevel,
                          String currentDimension, Snapshot next, long tick) {
        if (currentConnection == null || currentLevel == null || packetConnection != currentConnection
                || !next.dimension().equals(currentDimension)) return false;
        connection = currentConnection;
        level = currentLevel;
        snapshot = next;
        receivedAt = tick;
        return true;
    }

    public double nearestDistanceSquared(Object currentConnection, Object currentLevel, String currentDimension,
                                         long tick, double x, double y, double z, Predicate<Anchor> loaded) {
        if (snapshot == null || connection != currentConnection || level != currentLevel
                || !snapshot.dimension().equals(currentDimension) || tick < receivedAt
                || tick - receivedAt >= STALE_TICKS) {
            reset();
            return Double.POSITIVE_INFINITY;
        }
        double nearest = Double.POSITIVE_INFINITY;
        for (var anchor : snapshot.anchors()) if (loaded.test(anchor))
            nearest = Math.min(nearest, anchor.distanceSquared(x, y, z));
        return nearest;
    }

    public void reset() {
        connection = null;
        level = null;
        snapshot = null;
        receivedAt = 0;
    }
}
