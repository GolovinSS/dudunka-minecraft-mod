package io.github.golovinss.dudunka;

/** Pure client gain policy, independent of Minecraft, OpenAL and player settings. */
public final class ValeMusicDucking {
    public static final double ENTER_RADIUS = 16;
    public static final double EXIT_RADIUS = 20;
    public static final float DUCK_GAIN = .25F;
    public static final int FADE_TICKS = 40;
    private static final float STEP = (1 - DUCK_GAIN) / FADE_TICKS;
    private boolean nearby;
    private float gain = 1;

    /** Missing/unloaded landmarks use positive infinity; dimension exit/disconnect resets immediately. */
    public float tick(boolean inVale, boolean paused, double nearestDistanceSquared) {
        if (!inVale) {
            nearby = false;
            return gain = 1;
        }
        if (paused) return gain;
        double radius = nearby ? EXIT_RADIUS : ENTER_RADIUS;
        nearby = Double.isFinite(nearestDistanceSquared) && nearestDistanceSquared >= 0
                && nearestDistanceSquared <= radius * radius;
        float target = nearby ? DUCK_GAIN : 1;
        gain = target < gain ? Math.max(target, gain - STEP) : Math.min(target, gain + STEP);
        return gain;
    }

    /** Match vanilla's effective channel volume, then apply gain. Master remains on the listener. */
    public static float channelVolume(float soundVolume, float musicVolume, float gain) {
        return Math.max(0, Math.min(1, soundVolume * musicVolume)) * gain;
    }
}
