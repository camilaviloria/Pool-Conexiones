public class SimuladorConexion implements CreadorConexiones {
    private int contador = 0;

    @Override
    public synchronized Object crear() {
        return "conn-" + (++contador);
    }
}