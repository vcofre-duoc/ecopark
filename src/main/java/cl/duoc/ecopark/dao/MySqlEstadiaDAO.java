package cl.duoc.ecopark.dao;

import cl.duoc.ecopark.model.Estadia;
import cl.duoc.ecopark.model.EstadiaAuto;
import cl.duoc.ecopark.model.EstadiaMoto;
import cl.duoc.ecopark.model.ResumenTipo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * DAO MYSQL 3.0: contiene el SQL y el acceso físico a los registros.
 * Agrega ID AUTO_INCREMENT, UPDATE, búsqueda por patente y estadísticas.
 * Modelos, cálculos de tarifa y lógica visual siguen separados del SQL.
 */
public class MySqlEstadiaDAO implements EstadiaDAO {

    private static final Path CONFIG = Path.of("config", "db.properties");
    private static final int TIEMPO_CONSULTA_SEGUNDOS = 10;

    /**
     * CONEXIÓN JDBC: carga db.properties y abre una conexión local.
     * Se configura un timeout para no esperar indefinidamente si MySQL falla.
     */
    private Connection conectar() throws IOException, SQLException {
        Properties config = new Properties();

        try (InputStream entrada = Files.newInputStream(CONFIG)) {
            config.load(entrada);
        } catch (IOException e) {
            throw new IOException("No se pudo leer " + CONFIG + ". Revisa Working directory.", e);
        }

        String url = config.getProperty("db.url", "").trim();
        String usuario = config.getProperty("db.user", "").trim();
        String clave = config.getProperty("db.password", "");

        if (!url.startsWith("jdbc:mysql://") || url.contains("NOMBRE_DE_TU_BD")) {
            throw new IOException("Configura db.url con el nombre real de la base en " + CONFIG);
        }
        if (usuario.isEmpty()) {
            throw new IOException("Configura db.user en " + CONFIG);
        }

        // Los parámetros de conexión se envían por Properties a DriverManager.
        Properties conexionConfig = new Properties();
        conexionConfig.setProperty("user", usuario);
        conexionConfig.setProperty("password", clave);
        conexionConfig.setProperty("connectTimeout", "5000"); // milisegundos
        conexionConfig.setProperty("socketTimeout", "10000");  // milisegundos

        Connection conexion = DriverManager.getConnection(url, conexionConfig);
        if (!conexion.isValid(3)) {
            conexion.close();
            throw new SQLException("La conexión MySQL no respondió a la validación.");
        }
        return conexion;
    }

    /**
     * CONVERSIÓN: una fila SQL se transforma en EstadiaAuto o EstadiaMoto.
     * El ID se obtiene desde la columna id y se asigna al objeto.
     */
    private Estadia convertirFila(ResultSet rs) throws SQLException, IOException {
        long id = rs.getLong("id");
        String patente = rs.getString("patente");
        String tipo = rs.getString("tipo");
        int horas = rs.getInt("horas");
        boolean convenio = rs.getBoolean("convenio");

        try {
            Estadia estadia;
            if ("Auto".equals(tipo)) {
                estadia = new EstadiaAuto(patente, horas);
            } else if ("Moto".equals(tipo)) {
                estadia = new EstadiaMoto(patente, horas, convenio);
            } else {
                throw new IOException("Tipo de estadía desconocido: " + tipo);
            }
            estadia.setId(id); // ID generado previamente por MySQL.
            return estadia;
        } catch (IllegalArgumentException e) {
            throw new IOException("Datos incorrectos en el registro ID=" + id, e);
        }
    }

    /** SELECT: recupera todos los registros ordenados por ID. */
    @Override
    public List<Estadia> cargar() throws IOException {
        List<Estadia> resultado = new ArrayList<>();
        String sql = "SELECT id, patente, tipo, horas, convenio FROM estadias ORDER BY id";

        try (Connection conexion = conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { // Recorre todas las filas.
                    resultado.add(convertirFila(rs));
                }
            }
        } catch (SQLException e) {
            throw new IOException("Error al cargar las estadías desde MySQL: " + e.getMessage(), e);
        }
        return resultado;
    }

    /**
     * INSERT + AUTO_INCREMENT + TRANSACCIÓN.
     * MySQL asigna id. JDBC obtiene la clave con getGeneratedKeys().
     * commit confirma el INSERT; rollback revierte la operación ante un error.
     */
    @Override
    public void registrar(Estadia estadia) throws IOException {
        if (estadia == null) {
            throw new IllegalArgumentException("La estadía es obligatoria.");
        }
        String sql = "INSERT INTO estadias (patente, tipo, horas, convenio) VALUES (?, ?, ?, ?)";

        try (Connection conexion = conectar()) {
            conexion.setAutoCommit(false); // Comenzamos una transacción explícita.
            try (PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
                ps.setString(1, estadia.getPatente());
                ps.setString(2, estadia.getTipo());
                ps.setInt(3, estadia.getHoras());
                boolean convenio = estadia instanceof EstadiaMoto moto && moto.isConvenio();
                ps.setBoolean(4, convenio);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("MySQL no confirmó la creación de la estadía.");
                }

                long idNuevo;
                try (ResultSet claves = ps.getGeneratedKeys()) {
                    if (!claves.next()) {
                        throw new SQLException("MySQL no devolvió el ID generado.");
                    }
                    idNuevo = claves.getLong(1);
                    if (idNuevo <= 0) {
                        throw new SQLException("MySQL devolvió un ID inválido.");
                    }
                }

                conexion.commit();     // Confirmamos que el registro se guardó.
                estadia.setId(idNuevo); // Recién ahora actualizamos el objeto Java.
            } catch (SQLException e) {
                try {
                    conexion.rollback(); // Revertir si falla INSERT o lectura del ID.
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IOException("Patente duplicada o restricción MySQL incumplida: " + estadia.getPatente(), e);
        } catch (SQLException e) {
            throw new IOException("No se pudo registrar en MySQL: " + e.getMessage(), e);
        }
    }

    /** DELETE por ID: la patente ya no es la clave primaria. */
    @Override
    public void eliminar(long id) throws IOException {
        if (id <= 0) {
            throw new IllegalArgumentException("Debe indicar un ID válido.");
        }
        String sql = "DELETE FROM estadias WHERE id = ?";

        try (Connection conexion = conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            ps.setLong(1, id); // Identificamos exactamente la fila que se eliminará.
            if (ps.executeUpdate() == 0) {
                throw new IOException("No se encontró el ID " + id + " en MySQL.");
            }
        } catch (SQLException e) {
            throw new IOException("Error al eliminar el ID " + id + ": " + e.getMessage(), e);
        }
    }

    /** UPDATE con WHERE id: solo cambiamos las horas, no el tipo ni la patente. */
    @Override
    public void actualizarHoras(long id, int horas) throws IOException {
        if (id <= 0) throw new IllegalArgumentException("El ID es inválido.");
        if (horas < 1 || horas > 24) {
            throw new IllegalArgumentException("Las horas deben estar entre 1 y 24.");
        }
        String sql = "UPDATE estadias SET horas = ? WHERE id = ?";

        try (Connection conexion = conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            ps.setInt(1, horas);
            ps.setLong(2, id);
            if (ps.executeUpdate() == 0) {
                throw new IOException("No existe la estadía ID " + id + " para actualizar.");
            }
        } catch (SQLException e) {
            throw new IOException("Error al actualizar horas: " + e.getMessage(), e);
        }
    }

    /** SELECT ... WHERE con PreparedStatement: busca una patente exacta. */
    @Override
    public Optional<Estadia> buscarPorPatente(String patente) throws IOException {
        if (patente == null || patente.isBlank()) {
            throw new IllegalArgumentException("Ingrese una patente para buscar.");
        }
        String sql = "SELECT id, patente, tipo, horas, convenio FROM estadias WHERE patente = ?";
        String criterio = patente.trim().toUpperCase(Locale.ROOT);

        try (Connection conexion = conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            ps.setString(1, criterio);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(convertirFila(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IOException("Error al buscar patente: " + e.getMessage(), e);
        }
    }

    /**
     * ESTADÍSTICAS: agrupa por tipo y calcula cantidad y total de horas.
     * HAVING filtra grupos; por defecto el Controller solicita mínimo 1.
     */
    @Override
    public List<ResumenTipo> resumenPorTipo(int cantidadMinima) throws IOException {
        if (cantidadMinima < 1) {
            throw new IllegalArgumentException("La cantidad mínima debe ser 1 o mayor.");
        }
        String sql = """
                SELECT tipo, COUNT(*) AS cantidad, SUM(horas) AS horas_acumuladas
                FROM estadias
                GROUP BY tipo
                HAVING COUNT(*) >= ?
                ORDER BY tipo
                """;
        List<ResumenTipo> resultado = new ArrayList<>();

        try (Connection conexion = conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            ps.setInt(1, cantidadMinima);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ResumenTipo(
                            rs.getString("tipo"),
                            rs.getLong("cantidad"),
                            rs.getLong("horas_acumuladas")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IOException("Error al consultar estadísticas: " + e.getMessage(), e);
        }
        return resultado;
    }
}
