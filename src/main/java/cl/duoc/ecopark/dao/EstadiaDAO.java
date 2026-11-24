package cl.duoc.ecopark.dao;

import cl.duoc.ecopark.model.Estadia;
import cl.duoc.ecopark.model.ResumenTipo;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * DAO (Data Access Object): contrato del acceso a MySQL.
 * El Controller y el Repository no contienen consultas SQL.
 * Los métodos nuevos de la versión 3.0 están marcados abajo.
 */
public interface EstadiaDAO {

    // SELECT: lista todas las estadías.
    List<Estadia> cargar() throws IOException;

    // INSERT: almacena y recupera el ID AUTO_INCREMENT generado por MySQL.
    void registrar(Estadia estadia) throws IOException;

    // V3.0: DELETE por ID (PK), ya no por patente.
    void eliminar(long id) throws IOException;

    // V3.0: UPDATE modifica únicamente las horas del registro identificado por su ID.
    void actualizarHoras(long id, int horas) throws IOException;

    // V3.0: SELECT con WHERE y parámetro de búsqueda.
    Optional<Estadia> buscarPorPatente(String patente) throws IOException;

    // V3.0: COUNT, SUM, GROUP BY y HAVING para estadísticas por tipo.
    List<ResumenTipo> resumenPorTipo(int cantidadMinima) throws IOException;
}
