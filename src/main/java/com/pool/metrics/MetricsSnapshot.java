package com.pool.metrics;

public class MetricsSnapshot {

    private final long elapsedMs;
    private final long exitos;
    private final long fallos;
    private final long reintentos;
    private final long totalNanos;
    private final long minNanos;
    private final long maxNanos;
    private final boolean aborted;

    public MetricsSnapshot(long elapsedMs, long exitos, long fallos, long reintentos,
                           long totalNanos, long minNanos, long maxNanos) {
        this(elapsedMs, exitos, fallos, reintentos, totalNanos, minNanos, maxNanos, false);
    }

    public MetricsSnapshot(long elapsedMs, long exitos, long fallos, long reintentos,
                           long totalNanos, long minNanos, long maxNanos, boolean aborted) {
        this.elapsedMs = elapsedMs;
        this.exitos = exitos;
        this.fallos = fallos;
        this.reintentos = reintentos;
        this.totalNanos = totalNanos;
        this.minNanos = minNanos;
        this.maxNanos = maxNanos;
        this.aborted = aborted;
    }

    public long getElapsedMs()  { return elapsedMs; }
    public long getSuccesses()  { return exitos; }
    public long getFailures()   { return fallos; }
    public long getRetries()    { return reintentos; }
    public long getRequests()   { return exitos + fallos; }

    public double getSuccessPercent() {
        return getRequests() == 0 ? 0 : exitos * 100.0 / getRequests();
    }
    public double getFailurePercent() {
        return getRequests() == 0 ? 0 : fallos * 100.0 / getRequests();
    }
    public double getAvgResponseMs() {
        return getRequests() == 0 ? 0 : totalNanos / (double) getRequests() / 1_000_000.0;
    }
    public double getMinResponseMs() {
        return getRequests() == 0 ? 0 : minNanos / 1_000_000.0;
    }
    public double getMaxResponseMs() {
        return maxNanos / 1_000_000.0;
    }
    public double getThroughput() {
        return elapsedMs == 0 ? 0 : getRequests() * 1000.0 / elapsedMs;
    }

    public boolean isAborted() {
        return aborted;
    }
}