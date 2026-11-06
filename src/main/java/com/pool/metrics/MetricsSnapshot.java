package com.pool.metrics;

import java.util.Collections;
import java.util.Map;

/** Foto inmutable de las métricas al terminar una fase. */
public class MetricsSnapshot {

    private final long elapsedMs;
    private final long successes;
    private final long failures;
    private final long retries;
    private final long totalNanos;
    private final long minNanos;
    private final long maxNanos;
    private final Map<String, Long> queryCounts;
    private final boolean aborted;
    private final int requestedTotal;

    // Constructor antiguo (para compatibilidad)
    public MetricsSnapshot(long elapsedMs, long successes, long failures, long retries,
                           long totalNanos, long minNanos, long maxNanos, Map<String, Long> queryCounts) {
        this(elapsedMs, successes, failures, retries, totalNanos, minNanos, maxNanos, queryCounts, false, 0);
    }

    // 🔥 NUEVO CONSTRUCTOR
    public MetricsSnapshot(long elapsedMs, long successes, long failures, long retries,
                           long totalNanos, long minNanos, long maxNanos, Map<String, Long> queryCounts,
                           boolean aborted, int requestedTotal) {
        this.elapsedMs = elapsedMs;
        this.successes = successes;
        this.failures = failures;
        this.retries = retries;
        this.totalNanos = totalNanos;
        this.minNanos = minNanos;
        this.maxNanos = maxNanos;
        this.queryCounts = Collections.unmodifiableMap(queryCounts);
        this.aborted = aborted;
        this.requestedTotal = requestedTotal;
    }

    public long getElapsedMs() { return elapsedMs; }
    public long getRetries()   { return retries; }
    public Map<String, Long> getQueryCounts() { return queryCounts; }

    // 🔥 SI SE ABORTÓ, LAS QUE FALTARON SE SUMAN A LAS FALLIDAS
    public long getFailures() {
        long realFailures = failures;
        if (aborted) {
            long completadas = successes + failures;
            long faltantes = requestedTotal - completadas;
            if (faltantes > 0) {
                realFailures += faltantes;
            }
        }
        return realFailures;
    }

    public long getSuccesses() { return successes; }
    
    // Total siempre será el solicitado
    public long getRequests()  { return requestedTotal > 0 ? requestedTotal : (successes + failures); }

    public double getSuccessPercent() {
        return getRequests() == 0 ? 0 : successes * 100.0 / getRequests();
    }

    public double getFailurePercent() {
        return getRequests() == 0 ? 0 : getFailures() * 100.0 / getRequests();
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

    public boolean isAborted() { return aborted; }
}