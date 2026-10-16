import io.github.golovinss.dudunka.ValeMusicDucking;
import io.github.golovinss.dudunka.ValeMusicHints;
import java.util.List;
import io.github.golovinss.dudunka.client.MushroomValeMusic.Action;
import io.github.golovinss.dudunka.client.MushroomValeMusic.Schedule;

/** Deterministic client scheduling regression; audible playback is a separate client check. */
public final class CheckValeMusic {
    private static void expect(Action expected, Action actual) {
        if(expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void close(float expected, float actual) {
        if (Math.abs(expected - actual) > .00001F)
            throw new AssertionError("Expected gain " + expected + ", got " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void hints() {
        String vale = "dudunka:mushroom_vale", overworld = "minecraft:overworld";
        var root = new ValeMusicHints.Anchor(-8, 100, 24);
        var near = new ValeMusicHints.Snapshot(vale, List.of(root));
        var empty = new ValeMusicHints.Snapshot(vale, List.of());
        var publisher = new ValeMusicHints.Publisher();
        for (int t = 0; t < 100; t++) require(!publisher.shouldSend(empty, t), "No empty-area heartbeat");
        require(publisher.shouldSend(near, 100), "Publish entry");
        for (int t = 101; t < 140; t++) require(!publisher.shouldSend(near, t), "No redundant per-tick packets");
        require(publisher.shouldSend(near, 140), "Refresh a stable nonempty hint every 40 ticks");
        require(publisher.shouldSend(empty, 141), "Publish removed/damaged/unloaded landmark once");
        for (int t = 142; t < 200; t++) require(!publisher.shouldSend(empty, t), "Stay silent after clear");
        require(new ValeMusicHints.Publisher().shouldSend(near, 142), "Another player's publications are independent");
        var restartedClock = new ValeMusicHints.Publisher();
        require(restartedClock.shouldSend(near, 100) && restartedClock.shouldSend(near, 0),
                "A restarted/overflowed publication clock cannot suppress future heartbeats");

        var hints = new ValeMusicHints();
        Object connection = new Object(), world = new Object();
        require(hints.accept(connection, connection, world, vale, near, 0), "Accept current S2C snapshot");
        require(hints.nearestDistanceSquared(connection, world, vale, 0, -7.5, 100.5, 24.5, a -> true) == 0,
                "Native anchor center, including negative coordinates");
        require(hints.nearestDistanceSquared(connection, world, vale, 1, 8.5, 100.5, 24.5, a -> true) == 256,
                "Exact 16-block horizontal entry");
        require(hints.nearestDistanceSquared(connection, world, vale, 2, -7.5, 120.5, 24.5, a -> true) == 400,
                "Vertical distance respects the 20-block boundary");
        require(hints.nearestDistanceSquared(connection, world, vale, 3, -7.5, 100.5, 24.5, a -> false)
                == Double.POSITIVE_INFINITY, "Ignore a client-unloaded or missing root");
        require(!hints.accept(connection, connection, world, vale,
                new ValeMusicHints.Snapshot(overworld, List.of(root)), 4), "Reject wrong-dimension packet");
        require(!hints.accept(new Object(), connection, world, vale, empty, 4), "Reject queued packet from old connection");
        require(hints.nearestDistanceSquared(connection, world, vale, 59, -7.5, 100.5, 24.5, a -> true) == 0,
                "Rejected packets cannot destroy a valid hint or renew its lease");
        require(hints.nearestDistanceSquared(connection, world, vale, 60, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Expire a stale hint at 60 active client ticks");
        hints.accept(connection, connection, world, vale, near, 60);
        hints.accept(connection, connection, world, vale, near, 100);
        require(hints.nearestDistanceSquared(connection, world, vale, 159, -7.5, 100.5, 24.5, a -> true) == 0,
                "A fresh heartbeat renews the lease");
        require(hints.nearestDistanceSquared(connection, new Object(), vale, 160, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Same-dimension ClientLevel replacement clears hints");
        hints.accept(connection, connection, world, vale, near, 200);
        require(hints.nearestDistanceSquared(connection, world, overworld, 201, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Dimension exit clears hints");
        require(hints.nearestDistanceSquared(connection, world, vale, 202, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Re-entry needs a new snapshot");
        hints.accept(connection, connection, world, vale, near, 210);
        require(hints.nearestDistanceSquared(new Object(), world, vale, 211, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Connection replacement clears hints");
        require(!hints.accept(connection, null, null, vale, near, 212), "Disconnected client rejects packets");
        hints.accept(connection, connection, world, vale, near, 220);
        hints.reset();
        require(hints.nearestDistanceSquared(connection, world, vale, 221, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Logout reset removes all anchors");
        hints.accept(connection, connection, world, vale, near, 230);
        hints.accept(connection, connection, world, vale, empty, 231);
        require(hints.nearestDistanceSquared(connection, world, vale, 231, -7.5, 100.5, 24.5, a -> true)
                == Double.POSITIVE_INFINITY, "Server clear removes hints immediately");
        System.out.println("PASS: hint publication, geometry, connection/dimension binding, stale leases, unloaded roots and resets");
    }
    private static void ducking() {
        var duck = new ValeMusicDucking();
        double absent = Double.POSITIVE_INFINITY;
        close(1, duck.tick(false, false, absent)); // Disconnected.
        close(1, duck.tick(true, false, 16.01 * 16.01));
        for (int t = 1; t <= 40; t++)
            close(1 - .75F * t / 40, duck.tick(true, false, 16 * 16));
        // Boundary motion cannot undo ducking until the 20-block exit radius.
        for (double d : new double[]{16.1, 15.9, 19.9, 20, 16.1})
            close(.25F, duck.tick(true, false, d * d));
        close(.25F, duck.tick(true, true, absent)); // Pause freezes the transition.
        for (int t = 1; t <= 40; t++)
            close(.25F + .75F * t / 40, duck.tick(true, false, 20.01 * 20.01));
        close(1, duck.tick(true, false, 19 * 19)); // No re-entry through the hysteresis band.
        for (int t = 0; t < 40; t++) duck.tick(true, false, 0); // Teleport to a landmark.
        for (int t = 1; t <= 40; t++)
            close(.25F + .75F * t / 40, duck.tick(true, false, absent)); // Teleport away / unload / stale hints.
        for (int t = 0; t < 20; t++) duck.tick(true, false, 0);
        close(1, duck.tick(false, true, 0)); // Exit dimension even while suspended.
        close(1, duck.tick(true, false, 19 * 19)); // No latch from previous level.
        for (int t = 0; t < 40; t++) duck.tick(true, false, 0);
        close(1, duck.tick(false, false, absent)); // Disconnect clears a full duck.
        close(1, duck.tick(true, false, absent)); // Reconnect away from a landmark.
        // Reverse halfway without jumping to either endpoint.
        for (int t = 0; t < 20; t++) duck.tick(true, false, 0);
        close(.625F + .75F / 40, duck.tick(true, false, absent));
        close(0, ValeMusicDucking.channelVolume(1, 0, .25F));
        close(.125F, ValeMusicDucking.channelVolume(1, .5F, .25F));
        close(.05F, ValeMusicDucking.channelVolume(.4F, .5F, .25F));
        close(.25F, ValeMusicDucking.channelVolume(4, .5F, .25F)); // Clamp before ducking.
        System.out.println("PASS: ducking radius, hysteresis, 40-tick fades, reversals, pause, teleport, unload, dimension, disconnect and user gain");
    }
    public static void main(String[] args) {
        hints();
        ducking();
        var schedule = new Schedule();
        for(int tick=0;tick<5000;tick++) expect(Action.NONE,schedule.tick(false,false,false,()->1200));
        expect(Action.START,schedule.tick(true,false,false,()->1200));
        // Same managed event throughout Creative/Survival and all three biome borders.
        for(int tick=0;tick<10000;tick++) expect(Action.NONE,schedule.tick(true,false,true,()->1200));
        for(int tick=0;tick<1200;tick++) expect(Action.NONE,schedule.tick(true,false,false,()->1200));
        for(int tick=0;tick<5000;tick++) expect(Action.NONE,schedule.tick(true,true,false,()->1200));
        expect(Action.START,schedule.tick(true,false,false,()->1200));
        expect(Action.NONE,schedule.tick(true,false,true,()->3600));
        for(int tick=0;tick<3600;tick++) expect(Action.NONE,schedule.tick(true,false,false,()->3600));
        expect(Action.START,schedule.tick(true,false,false,()->3600));
        // Native Survival scheduling may start the same event during a quiet gap.
        expect(Action.NONE,schedule.tick(true,false,true,()->1200));
        for(int tick=0;tick<500;tick++) expect(Action.NONE,schedule.tick(true,false,false,()->1200));
        expect(Action.NONE,schedule.tick(true,false,true,()->1200));
        expect(Action.STOP,schedule.tick(false,true,true,()->1200));
        expect(Action.NONE,schedule.tick(false,false,false,()->1200));
        expect(Action.NONE,schedule.tick(true,true,false,()->1200));
        expect(Action.START,schedule.tick(true,false,false,()->1200));
        expect(Action.STOP,schedule.tick(false,false,true,()->1200));
        System.out.println("PASS: Vale-only entry/exit, uninterrupted tracks, 1200/3600-tick gaps, native co-scheduling and pause/resume");
    }
}
