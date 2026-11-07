package com.feed_the_beast.ftbquests.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.function.LongSupplier;
import static org.junit.jupiter.api.Assertions.*;

class EasingTest
{
    private boolean previous;
    private static final class Clock implements LongSupplier
    {
        long now;
        public long getAsLong() { return now; }
        void ms(double milliseconds) { now = Math.round(milliseconds * 1_000_000); }
    }
    @BeforeEach void smooth() { previous = VisualConfig.smoothAnimations; VisualConfig.smoothAnimations = true; }
    @AfterEach void restore() { VisualConfig.smoothAnimations = previous; }

    @Test void finiteCurveHasSoftBoundariesAndNoOvershoot()
    {
        assertEquals(0, Easing.curve(-1)); assertEquals(1, Easing.curve(2));
        assertEquals(.5, Easing.curve(.5), 1e-14);
        assertTrue(Easing.curve(.001) < .000001);
        assertTrue(1 - Easing.curve(.999) < .000001);
        double previous = 0;
        for (int i = 0; i <= 1000; i++) {
            double value = Easing.curve(i / 1000D);
            assertTrue(value >= previous && value <= 1); previous = value;
        }
    }

    @Test void samplingAtDifferentFrameRatesHasTheSamePosition()
    {
        double reference = sample(1, 183);
        assertEquals(reference, sample(1000D / 30, 183), 1e-12);
        assertEquals(reference, sample(1000D / 60, 183), 1e-12);
        assertEquals(reference, sample(1000D / 144, 183), 1e-12);
    }
    private double sample(double step, double end)
    {
        Clock clock = new Clock(); Easing easing = new Easing(0, clock); easing.next(1, 320);
        for (double time = step; time < end; time += step) { clock.ms(time); easing.next(1, 320); }
        clock.ms(end); return easing.next(1, 320);
    }

    @Test void reversalSamplesTheOldMotionBeforeChangingTargetAndKeepsVelocity()
    {
        Clock clock = new Clock(); Easing easing = new Easing(0, clock); easing.next(1, 350);
        clock.ms(80); double before = easing.next(1, 350);
        clock.ms(80.01); double reversed = easing.next(0, 350);
        assertTrue(reversed > before, "Retarget must advance the unrendered part of the old trajectory");
        clock.ms(80.02); double after = easing.next(0, 350);
        assertTrue(after > reversed, "Reversal should decelerate existing velocity, without resetting it");
        assertEquals((reversed-before) / .01, (after-reversed) / .01, .00002);
        clock.ms(1500); assertEquals(0, easing.next(0, 350), 1e-7);
    }

    @Test void repeatedReversalsStayBoundedAndSettleWithoutFrameSteps()
    {
        Clock clock = new Clock(); Easing easing = new Easing(0, clock);
        for (int i = 0; i < 500; i++) {
            clock.ms(i * 7); double value = easing.next(i / 9 % 2, 220);
            assertTrue(value >= 0 && value <= 1, "Unbounded hover: " + value);
        }
        easing.next(1, 220); clock.ms(7000); assertEquals(1, easing.next(1, 220));
    }

    @Test void configuredDurationReaches99PercentAndSnapClearsMomentum()
    {
        Clock clock = new Clock(); Easing easing = new Easing(0, clock); easing.next(1, 320);
        clock.ms(320); assertEquals(.99, easing.next(1, 320), .0001);
        easing.snap(16); clock.ms(360); assertEquals(16, easing.next(16, 320));
        clock.ms(10_000); assertEquals(16, easing.next(16, 320));
    }

    @Test void linearPreferenceAndMinimumDurationStillCompleteExactly()
    {
        VisualConfig.smoothAnimations = false;
        assertEquals(.25, Easing.curve(.25));
        Clock clock = new Clock(); Easing easing = new Easing(0, clock); easing.next(1, 100);
        clock.ms(50); assertEquals(.5, easing.next(1, 100));
        assertEquals(.5, easing.next(0, 0));
        clock.ms(65); assertEquals(.25, easing.next(0, 0));
        clock.ms(80); assertEquals(0, easing.next(0, 0));
    }
}
