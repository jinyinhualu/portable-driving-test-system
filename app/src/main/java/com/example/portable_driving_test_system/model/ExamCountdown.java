package com.example.portable_driving_test_system.model;

// 使用开机后经过的时间确定截止点，避免修改系统日期影响考试时长。
public final class ExamCountdown {
    public static final long DURATION_MILLIS = 45L * 60 * 1000;
    public static final long WARNING_SECONDS = 5L * 60;

    private ExamCountdown() {
    }

    public static long start(long elapsedRealtime) {
        return elapsedRealtime + DURATION_MILLIS;
    }

    public static long remainingSeconds(long deadline, long elapsedRealtime) {
        long remaining = Math.max(0L, deadline - elapsedRealtime);
        // 最后不足一秒仍显示00:01，真正到期才显示00:00并交卷。
        return (remaining + 999L) / 1000L;
    }

    public static boolean isExpired(long deadline, long elapsedRealtime) {
        return elapsedRealtime >= deadline;
    }
}
