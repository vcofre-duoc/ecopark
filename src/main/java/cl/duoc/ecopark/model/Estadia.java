package cl.duoc.ecopark.model;

/**
 * MODELO / CLASE ABSTRACTA:
 * Agrupa los datos comunes de autos y motos.
 * Los subtipos mantienen sus reglas originales de cálculo.
 */
public abstract class Estadia {

    // V3.0: ID interno generado automáticamente por MySQL.
    // El valor 0 indica que la estadía todavía no fue guardada.
    private long id;

    // La patente sigue siendo única, pero ya NO es la clave primaria.
    private String patente;
    private int horas;

    protected Estadia(String patente, int horas) {
        setPatente(patente);
        setHoras(horas);
    }

    public long getId() { return id; }

    /** Solo el DAO asigna el ID luego del INSERT o de un SELECT. */
    public void setId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("El ID no puede ser negativo.");
        }
        this.id = id;
    }

    public String getPatente() { return patente; }

    public void setPatente(String patente) {
        if (patente == null || patente.trim().isEmpty()) {
            throw new IllegalArgumentException("La patente no puede estar vacía.");
        }
        this.patente = patente.trim().toUpperCase();
    }

    public int getHoras() { return horas; }

    /** Esta validación también se utiliza en la actualización de horas. */
    public void setHoras(int horas) {
        if (horas < 1 || horas > 24) {
            throw new IllegalArgumentException("Las horas deben estar entre 1 y 24.");
        }
        this.horas = horas;
    }

    // POLIMORFISMO: el costo depende del subtipo concreto.
    public abstract int calcularCosto();
    public abstract String getTipo();

    @Override
    public String toString() {
        return "#" + id + " | " + getTipo() + " | " + patente + " | " + horas + " hora(s)";
    }
}
