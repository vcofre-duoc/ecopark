package cl.duoc.ecopark.repository;

import cl.duoc.ecopark.dao.EstadiaDAO;
import cl.duoc.ecopark.model.Estadia;
import cl.duoc.ecopark.model.ResumenTipo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITORY 3.0: coordina las operaciones sobre los objetos Estadia.
 * El DAO conoce MySQL. El Repository conoce las operaciones del negocio.
 */
public class EstadiaRepository {

    private final EstadiaDAO dao;
    private final List<Estadia> estadias = new ArrayList<>();

    public EstadiaRepository(EstadiaDAO dao) {
        this.dao = dao;
    }

    /** Carga la lista de la base. No borra datos locales si falla la consulta. */
    public void cargar() throws IOException {
        List<Estadia> recuperadas = dao.cargar();
        estadias.clear();
        estadias.addAll(recuperadas);
    }

    /** Retorna una copia, evitando que la interfaz modifique la lista interna. */
    public List<Estadia> listar() {
        return List.copyOf(estadias);
    }

    /**
     * INSERT: primero guardamos en MySQL y obtenemos el ID.
     * Después agregamos el objeto a la colección en memoria.
     */
    public void registrar(Estadia nueva) throws IOException {
        if (nueva == null) throw new IllegalArgumentException("La estadía es obligatoria.");
        for (Estadia actual : estadias) {
            if (actual.getPatente().equalsIgnoreCase(nueva.getPatente())) {
                throw new IllegalArgumentException("La patente " + nueva.getPatente() + " ya existe.");
            }
        }
        dao.registrar(nueva); // INSERT y asignación de ID en el DAO.
        estadias.add(nueva);
    }

    /** DELETE por PK. Funciona incluso si la fila se obtuvo mediante una búsqueda. */
    public void eliminar(Estadia seleccionada) throws IOException {
        if (seleccionada == null || seleccionada.getId() <= 0) {
            throw new IllegalArgumentException("Seleccione una estadía registrada para eliminar.");
        }
        dao.eliminar(seleccionada.getId());
        estadias.removeIf(e -> e.getId() == seleccionada.getId());
    }

    /** UPDATE por ID. Validamos antes de solicitar la operación SQL. */
    public void actualizarHoras(Estadia seleccionada, int nuevasHoras) throws IOException {
        if (seleccionada == null || seleccionada.getId() <= 0) {
            throw new IllegalArgumentException("Seleccione una estadía para actualizar.");
        }
        if (nuevasHoras < 1 || nuevasHoras > 24) {
            throw new IllegalArgumentException("Las horas deben estar entre 1 y 24.");
        }

        dao.actualizarHoras(seleccionada.getId(), nuevasHoras); // UPDATE SQL exitoso.
        // Actualizamos el objeto que está en nuestra colección.
        boolean encontrada = false;
        for (Estadia actual : estadias) {
            if (actual.getId() == seleccionada.getId()) {
                actual.setHoras(nuevasHoras);
                encontrada = true;
                break;
            }
        }
        // Si la lista no tenía el registro (por ejemplo, datos externos), la recargamos.
        if (!encontrada) cargar();
    }

    /** SELECT parametrizado por patente: devuelve 0 o 1 estadía (UNIQUE). */
    public Optional<Estadia> buscarPorPatente(String patente) throws IOException {
        return dao.buscarPorPatente(patente);
    }

    /** Consultas agregadas con GROUP BY/HAVING; no duplicamos SQL aquí. */
    public List<ResumenTipo> resumenPorTipo() throws IOException {
        return dao.resumenPorTipo(1);
    }
}
