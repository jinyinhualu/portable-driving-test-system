package com.example.portable_driving_test_system;

import com.example.portable_driving_test_system.model.ExamCountdown;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamCountdownTest {
    @Test
    public void startsWith45Minutes() {
        long now = 10000L;
        long deadline = ExamCountdown.start(now);
        assertEquals(45L * 60, ExamCountdown.remainingSeconds(deadline, now));
        assertFalse(ExamCountdown.isExpired(deadline, now));
    }

    @Test
    public void elapsedTimeIncludesBackgroundInterval_withoutTimerTicks() {
        long started = 10000L;
        long deadline = ExamCountdown.start(started);
        long returnedFromBackground = started + 12L * 60 * 1000;
        assertEquals(33L * 60,
                ExamCountdown.remainingSeconds(deadline, returnedFromBackground));
    }

    @Test
    public void returningAfterLongBackgroundInterval_isAlreadyExpired() {
        long started = 10000L;
        long deadline = ExamCountdown.start(started);
        long returnedFromBackground = started + 46L * 60 * 1000;
        assertEquals(0L, ExamCountdown.remainingSeconds(deadline, returnedFromBackground));
        assertTrue(ExamCountdown.isExpired(deadline, returnedFromBackground));
    }

    @Test
    public void deadlineAndLater_areExpiredAndNeverShowNegativeTime() {
        long deadline = ExamCountdown.start(10000L);
        assertTrue(ExamCountdown.isExpired(deadline, deadline));
        assertEquals(0L, ExamCountdown.remainingSeconds(deadline, deadline));
        assertTrue(ExamCountdown.isExpired(deadline, deadline + 600000L));
        assertEquals(0L, ExamCountdown.remainingSeconds(deadline, deadline + 600000L));
    }

    @Test
    public void secondsRoundUpUntilActualExpiry() {
        long deadline = 5000L;
        assertEquals(2L, ExamCountdown.remainingSeconds(deadline, 3999L));
        assertEquals(1L, ExamCountdown.remainingSeconds(deadline, 4000L));
        assertEquals(1L, ExamCountdown.remainingSeconds(deadline, 4999L));
        assertEquals(0L, ExamCountdown.remainingSeconds(deadline, 5000L));
    }
}
