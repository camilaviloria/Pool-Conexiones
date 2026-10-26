import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool {
    private final BlockingQueue<Object> libres;
    private final CreadorConexiones factory;
    private final int maximo;
    private final AtomicInteger creadas = new AtomicInteger(0);

    public ConnectionPool(int tamanoInicial, int maximo, CreadorConexiones factory) throws Exception {
        this.factory = factory;
        this.maximo = maximo;
        this.libres = new LinkedBlockingQueue<>(maximo);
        for (int i = 0; i < tamanoInicial; i++) {
            libres.offer(factory.crear());
            creadas.incrementAndGet();
        }
    }

    // Pide una conexión. Si no hay, se expande (GROW)
    public Object obtener(long timeoutMs) throws Exception {
        Object c = libres.poll();
        if (c != null) return c;

        while (true) {
            int actuales = creadas.get();
            if (actuales >= maximo) break; // Llegamos al techo
            
            // Intenta crear una nueva conexión de forma atómica
            if (creadas.compareAndSet(actuales, actuales + 1)) {
                return factory.crear();
            }
        }

        // Si estamos llenos, esperamos a que alguien libere una
        c = libres.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (c == null) throw new RuntimeException("Timeout esperando conexión");
        return c;
    }

    // Devuelve la conexión al pool
    public void liberar(Object conexion) {
        if (conexion != null) libres.offer(conexion);
    }

    public int getLibres()    { return libres.size(); }
    public int getCapacidad() { return maximo; }
    public int getCreadas()   { return creadas.get(); }
}