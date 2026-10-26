import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PoolManager {
    private final ConnectionPool pool;

    // Métricas concurrentes
    private final AtomicLong operaciones   = new AtomicLong(0);
    private final AtomicLong fallos        = new AtomicLong(0);
    private final AtomicLong tiempoTotalNs = new AtomicLong(0);
    private final AtomicInteger enUso      = new AtomicInteger(0);
    private final AtomicInteger enEspera   = new AtomicInteger(0);

    private volatile boolean activo = false;
    private final Thread[] hilos;

    public PoolManager(ConnectionPool pool, int numHilos) {
        this.pool = pool;
        this.hilos = new Thread[numHilos];
    }

    public void iniciar() {
        if (activo) return;
        activo = true;
        for (int i = 0; i < hilos.length; i++) {
            hilos[i] = new Thread(this::bucle, "trabajador-" + i);
            hilos[i].setDaemon(true);
            hilos[i].start();
        }
    }

    private void bucle() {
        while (activo) {
            ejecutarOperacion();
        }
    }

    private void ejecutarOperacion() {
        long inicio = System.nanoTime();
        Object conn = null;
        enEspera.incrementAndGet();
        
        try {
            // Pide la conexión al Pool
            conn = pool.obtener(1500);
            enEspera.decrementAndGet();
            enUso.incrementAndGet();
            
            // Simula el tiempo de procesamiento (Query)
            Thread.sleep(300 + (long) (Math.random() * 500)); // Ahora tarda entre 300ms y 800ms            
            // Simula un 10% de fallos aleatorios
            if (Math.random() < 0.10) throw new RuntimeException("fallo simulado");
            
        } catch (Exception e) {
            fallos.incrementAndGet();
            enEspera.decrementAndGet();
        } finally {
            // Devuelve la conexión y actualiza métricas
            if (conn != null) pool.liberar(conn);
            if (enUso.get() > 0) enUso.decrementAndGet();
            tiempoTotalNs.addAndGet(System.nanoTime() - inicio);
            operaciones.incrementAndGet();
        }
    }

    public void detener() {
        activo = false;
        for (Thread t : hilos) {
            if (t != null) t.interrupt();
        }
    }

    // Getters para la interfaz gráfica
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