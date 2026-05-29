package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.data.Modalidad;

/**
 * Politica de aplicabilidad del bono por KM (N5.5).
 *
 * Decide si la combinacion (sala, modalidad) HABILITA el bono por KM
 * de ese registro. El MONTO del bono (por km) lo decide la modalidad via
 * Prefs (param_{MODALIDAD}_bono_km_dom_lun_mar); esta clase solo decide
 * si el bono aplica o no en esa sala-modalidad.
 *
 * ===== Estado actual (hardcoded) =====
 *
 * Solo la sala "139" habilita el bono. Para cualquier otra sala el bono
 * es 0 sin importar la modalidad ni el dia de la semana. Esto es
 * intencional: las otras salas todavia no estan parametrizadas.
 *
 * Cuando aplica, el monto resultante puede igual ser 0 si la modalidad
 * tiene su bono configurado en 0 (caso por defecto de Picker, que no usa
 * KM y no tendria sentido recibir bono).
 *
 * ===== Por que esta clase existe (vs. hardcoding directo en Config) =====
 *
 * En N6 esta clase se reemplaza por una implementacion que consulta una
 * tabla configurable beneficios(sala, modalidad, *) en lugar de la
 * constante. El resto del codigo (Config.calcularBonoKm y sus consumidores)
 * ya esta listo: llaman a BonoKmPolicy.aplica(sala, modalidad) sin saber
 * de la implementacion subyacente. Asi N6 toca solo este archivo.
 *
 * Decision tomada con Roger (2026-05-27):
 *   - Sala 139 habilita bono para CUALQUIER modalidad cuyo bono > 0.
 *   - Picker tiene bono=0 default -> no recibe aunque la sala califique.
 *   - Driver con bono>0 si recibe.
 */
public class BonoKmPolicy {

    /**
     * Sala unica habilitada para bono por KM hasta N6. Hardcoded a proposito.
     * Si cambia la regla mientras N6 no esta listo, actualizar este string.
     */
    public static final String SALA_CON_BONO_KM = "139";

    private BonoKmPolicy() {
        // no instanciable
    }

    /**
     * @param sala       codigo numerico del local donde se hizo el pedido
     *                   (lo que se guarda en Registro.local).
     * @param modalidad  modalidad del registro. Ignorada hoy; reservada para N6.
     * @return true si la combinacion habilita el calculo del bono.
     */
    public static boolean aplica(String sala, Modalidad modalidad) {
        if (sala == null) return false;
        return SALA_CON_BONO_KM.equals(sala.trim());
    }
}
