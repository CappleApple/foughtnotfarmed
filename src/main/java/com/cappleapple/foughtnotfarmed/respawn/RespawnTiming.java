package com.cappleapple.foughtnotfarmed.respawn;

public final class RespawnTiming {
    public static final double BASELINE_HEALTH = 50.0;
    private static final double TICKS_PER_MINUTE = 1_200.0;
    private static final double MILLIS_PER_MINUTE = 60_000.0;

    private RespawnTiming() {
    }

    public static long delayTicks(double minutes, boolean adjustForHealth, double maximumHealth) {
        return roundedDelay(minutes, TICKS_PER_MINUTE, adjustForHealth, maximumHealth);
    }

    public static long delayMillis(double minutes, boolean adjustForHealth, double maximumHealth) {
        return roundedDelay(minutes, MILLIS_PER_MINUTE, adjustForHealth, maximumHealth);
    }

    private static long roundedDelay(double minutes, double unitsPerMinute, boolean adjustForHealth, double maximumHealth) {
        double healthFactor = adjustForHealth ? Math.max(1.0, maximumHealth) / BASELINE_HEALTH : 1.0;
        double delay = Math.max(0.0, minutes) * unitsPerMinute * healthFactor;
        return delay >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(delay);
    }
}
