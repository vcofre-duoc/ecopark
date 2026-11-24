package cl.duoc.ecopark.model;

/**
 * V3.0 / RESUMEN DE SQL:
 * Un record pequeño para guardar el resultado de COUNT y SUM con GROUP BY.
 * No representa una estadía individual, sino un resumen por tipo.
 */
public record ResumenTipo(String tipo, long cantidad, long horasAcumuladas) {
}
