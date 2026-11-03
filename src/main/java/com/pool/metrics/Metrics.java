package com.pool.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {

    private final LongAdder exitos    = new LongAdder();
    private final LongAdder fallos    = new LongAdder();
    private final LongAdder reintentos = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong(0);

    public void record(boolean ok, long nanos, int retries) {
        (ok ? exitos : fallos).increment();
        reintentos.add(retries);
        totalNanos.add(nanos);
        minNanos.accumulateAndGet(nanos, Math::min);
        maxNanos.accumulateAndGet(nanos, Math::max);
    }

    public int getCompletadas() { return (int) (exitos.sum() + fallos.sum()); }
    public long getFallos()     { return fallos.sum(); }

    public MetricsSnapshot snapshot(long elapsedMs) {
        return new MetricsSnapshot(elapsedMs, exitos.sum(), fallos.sum(), reintentos.sum(),
                totalNanos.sum(), minNanos.get(), maxNanos.get());
    }
}