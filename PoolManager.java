import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

public class PoolManager {

    private static PoolManager instancia;

    private final Queue<Connection> conexionesDisponibles;

    private final int MIN_CONEXIONES = 5;
    private final int MAX_CONEXIONES = 20; 
    private int conexionesTotales = 0;

    private final String URL = "jdbc:postgresql://localhost:5432/nombre_de_tu_base_de_datos";
    private final String USUARIO = "tu_usuario";
    private final String PASSWORD = "tu_password";

    private PoolManager() {
        conexionesDisponibles = new LinkedList<>();
        inicializarPool();
    }

    public static synchronized PoolManager getInstance() {
        if (instancia == null) {
            instancia = new PoolManager();
        }
        return instancia;
    }

    private void inicializarPool() {
        for (int i = 0; i < MIN_CONEXIONES; i++) {
            try {
                grow();
            } catch (SQLException e) {
                System.err.println("Error al inicializar conexión en el pool: " + e.getMessage());
            }
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        while (conexionesDisponibles.isEmpty()) {
            if (conexionesTotales < MAX_CONEXIONES) {
                grow();
            } else {
                System.out.println("Pool lleno. Cliente en espera de una conexión disponible...");
                try {
                    wait(5000); 
                    if (conexionesDisponibles.isEmpty()) {
                        throw new SQLException("Timeout: No se pudo obtener una conexión del pool a tiempo.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("El hilo fue interrumpido mientras esperaba una conexión.", e);
                }
            }
        }

        while (true) {
            Connection conn = conexionesDisponibles.poll();
            
            if (conn != null && conn.isValid(2)) {
                return conn;
            } else {
                System.out.println("Conexión inválida detectada. Descartando y reintentando...");
                conexionesTotales--; 
                
                if (conexionesDisponibles.isEmpty()) {
                    if (conexionesTotales < MAX_CONEXIONES) {
                        grow();
                    } else {
                         throw new SQLException("No hay conexiones válidas disponibles en el pool.");
                    }
                }
            }
        }
    }

    public synchronized void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conexionesDisponibles.offer(conn);
                    notifyAll();
                } else {
                    conexionesTotales--; 
                }
            } catch (SQLException e) {
                conexionesTotales--;
            }
        }
    }

    private void grow() throws SQLException {
        System.out.println("Creando nueva conexión física a la Base de Datos...");
        Connection nuevaConexion = DriverManager.getConnection(URL, USUARIO, PASSWORD);
        conexionesDisponibles.offer(nuevaConexion);
        conexionesTotales++;
    }

    public synchronized void shrink() {
        while (conexionesTotales > MIN_CONEXIONES && conexionesDisponibles.size() > MIN_CONEXIONES) {
            System.out.println("Cerrando conexiones ociosas (Shrink)...");
            Connection conn = conexionesDisponibles.poll();
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            conexionesTotales--;
        }
    }

    public synchronized void disconnect() {
        System.out.println("Cerrando todas las conexiones y destruyendo el Pool...");
        for (Connection conn : conexionesDisponibles) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        conexionesDisponibles.clear();
        conexionesTotales = 0;
        instancia = null; 
    }
}