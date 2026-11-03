import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PoolManager {
    private static PoolManager instancia;
    private final ConnectionPool pool;
    
    private final AtomicLong operaciones   = new AtomicLong(0);
    private final AtomicLong fallos        = new AtomicLong(0);
    private final AtomicLong tiempoTotalNs = new AtomicLong(0);
    private final AtomicInteger enUso      = new AtomicInteger(0);
    private final AtomicInteger enEspera   = new AtomicInteger(0);

    private volatile boolean activo = false;
    
    private ExecutorService executor;
    private final int numHilos;

    private PoolManager(ConnectionPool pool, int numHilos) {
        this.pool = pool;
        this.numHilos = numHilos;
    }

    public static synchronized PoolManager getInstance(ConnectionPool pool, int numHilos) {
        if (instancia == null) instancia = new PoolManager(pool, numHilos);
        return instancia;
    }

    public void iniciar() {
        if (activo) return;
        activo = true;
        
        executor = Executors.newFixedThreadPool(numHilos);
        
        for (int i = 0; i < numHilos; i++) {
            executor.execute(this::bucle);
        }
    }

    private void bucle() {
        while (activo) {
            ejecutarOperacion();
            try {
                Thread.sleep(50); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void ejecutarOperacion() {
        long inicio = System.nanoTime();
        Object conn = null;
        enEspera.incrementAndGet();
        
        try {
            conn = pool.obtener(6000); 
            
            enEspera.decrementAndGet();
            enUso.incrementAndGet();
            
            Thread.sleep(100); 
            
            if (Math.random() < 0.10) throw new RuntimeException("fallo simulado");
            
        } catch (Exception e) {
            fallos.incrementAndGet();
            enEspera.decrementAndGet();
        } finally {
            if (conn != null) pool.liberar(conn);
            if (enUso.get() > 0) enUso.decrementAndGet();
            tiempoTotalNs.addAndGet(System.nanoTime() - inicio);
            operaciones.incrementAndGet();
        }
    }

    public void detener() {
        activo = false;
        if (executor != null) {
            executor.shutdownNow(); 
        }
    }

    public long getOperaciones() { return operaciones.get(); }
    public long getFallos()      { return fallos.get(); }
    public int  getEnUso()       { return enUso.get(); }
    public int  getEnEspera()    { return Math.max(0, enEspera.get()); }
    public int  getLibres()      { return pool.getLibres(); }
    public int  getCapacidad()   { return pool.getCapacidad(); }
    public int  getCreadas()     { return pool.getCreadas(); }

    public double getVelocidadPromedioMs() {
        long n = operaciones.get();
        return n == 0 ? 0 : (tiempoTotalNs.get() / 1_000_000.0) / n;
    }

    public double getTasaFallosPorciento() {
        long n = operaciones.get();
        return n == 0 ? 0 : (100.0 * fallos.get() / n);
    }
}