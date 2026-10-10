import io.github.golovinss.dudunka.client.MushroomValeMusic.Action;
import io.github.golovinss.dudunka.client.MushroomValeMusic.Schedule;

/** Deterministic client scheduling regression; audible playback is a separate client check. */
public final class CheckValeMusic {
    private static void expect(Action expected, Action actual) {
        if(expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
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
