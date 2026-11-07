package com.feed_the_beast.ftbquests.client;

import java.util.function.LongSupplier;

/** Time based, critically damped motion. Retargeting preserves both position and velocity. */
public final class Easing
{
    private final LongSupplier clock;
    private long sampledAt, started;
    private double value, velocity, from, target;
    private int duration = 140;
    private boolean smooth;

    public Easing(double value) { this(value, System::nanoTime); }

    Easing(double value, LongSupplier clock)
    {
        this.clock = clock;
        snap(value);
    }

    public void snap(double value)
    {
        this.value = this.from = this.target = value;
        velocity = 0;
        sampledAt = started = clock.getAsLong();
        smooth = VisualConfig.smoothAnimations;
    }

    /** A finite transition with zero speed and acceleration at both ends. */
    public static double curve(double progress)
    {
        double t = Math.max(0, Math.min(1, progress));
        return VisualConfig.smoothAnimations ? t * t * t * (t * (t * 6 - 15) + 10) : t;
    }

    public static double opening(long started) { return opening(started, VisualConfig.smoothingMilliseconds); }
    public static double opening(long started, int milliseconds)
    { return curve((System.nanoTime() - started) / (Math.max(30, milliseconds) * 1_000_000D)); }

    public double next(double target) { return next(target, VisualConfig.smoothingMilliseconds); }

    public double next(double target, int milliseconds)
    {
        long now = clock.getAsLong();
        advance(now);
        boolean useSmooth = VisualConfig.smoothAnimations;
        if (this.target != target || smooth != useSmooth || duration != Math.max(30, milliseconds))
        {
            from = value;
            this.target = target;
            started = now;
            smooth = useSmooth;
            duration = Math.max(30, milliseconds);
            if (!smooth) velocity = 0;
        }
        return value;
    }

    private void advance(long now)
    {
        double seconds = Math.max(0, now - sampledAt) / 1_000_000_000D;
        sampledAt = now;
        if (seconds == 0 || value == target && velocity == 0) return;
        if (!smooth)
        {
            double progress = Math.min(1, Math.max(0, now - started) / (duration * 1_000_000D));
            value = from + (target - from) * progress;
            return;
        }
        // Analytic integration gives the same trajectory at 30, 60 and 144 FPS.
        // 6.64 reaches 99% of a step in the configured duration, without overshoot.
        double omega = 6640D / duration;
        double displacement = value - target;
        double coefficient = velocity + omega * displacement;
        double decay = Math.exp(-omega * seconds);
        value = target + (displacement + coefficient * seconds) * decay;
        velocity = (velocity - omega * coefficient * seconds) * decay;
        if (Math.abs(value - target) < .00001 && Math.abs(velocity) < .0001)
        {
            value = target;
            velocity = 0;
        }
    }
}
