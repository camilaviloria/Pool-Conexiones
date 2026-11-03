package com.pool.pool;

public class PoolConfig {
    private final int inicial;
    private final int maximo;
    private final long timeoutMs;

    public PoolConfig(int inicial, int maximo, long timeoutMs) {
        this.maximo = Math.max(1, maximo);
        this.inicial = Math.max(0, Math.min(inicial, this.maximo));
        this.timeoutMs = Math.max(1, timeoutMs);
    }

    public int getInicial()    { return inicial; }
    public int getMaximo()     { return maximo; }
    public long getTimeoutMs() { return timeoutMs; }
}