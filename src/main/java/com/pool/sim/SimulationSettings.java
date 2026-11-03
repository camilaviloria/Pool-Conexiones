package com.pool.sim;

public class SimulationSettings {
    private final int requests;
    private final int threads;
    private final int reintentos;
    private final int poolInicial;
    private final int poolMaximo;

    public SimulationSettings(int requests, int threads, int reintentos, int poolInicial, int poolMaximo) {
        this.requests = requests;
        this.threads = threads;
        this.reintentos = reintentos;
        this.poolInicial = poolInicial;
        this.poolMaximo = poolMaximo;
    }

    public int getRequests()    { return requests; }
    public int getThreads()     { return threads; }
    public int getReintentos()  { return reintentos; }
    public int getPoolInicial() { return poolInicial; }
    public int getPoolMaximo()  { return poolMaximo; }
}