package com.pool.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {

    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong(0);
    private final ConcurrentHashMap<String, LongAdder> perQuery = new ConcurrentHashMap<>();

    public void record(String queryName, boolean ok, long nanos, int retryCount) {
        (ok ? successes : failures).increment();
        retries.add(retryCount);
        totalNanos.add(nanos);
        minNanos.accumulateAndGet(nanos, Math::min);
        maxNanos.accumulateAndGet(nanos, Math::max);
        perQuery.computeIfAbsent(queryName, k -> new LongAdder()).increment();
    }

    public int getCompleted() {
        return (int) (successes.sum() + failures.sum());
    }

    public long getFailures() {
        return failures.sum();
    }

    public MetricsSnapshot snapshot(long elapsedMs, boolean aborted, int requestedTotal) {
        Map<String, Long> queries = new TreeMap<>();
        perQuery.forEach((name, count) -> queries.put(name, count.sum()));
        return new MetricsSnapshot(elapsedMs, successes.sum(), failures.sum(), retries.sum(),
                totalNanos.sum(), minNanos.get(), maxNanos.get(), queries, aborted, requestedTotal);
    }
    
    // Compatibilidad
    public MetricsSnapshot snapshot(long elapsedMs) {
        return snapshot(elapsedMs, false, 0);
    }
}