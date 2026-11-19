package cl.rdrp.planilla_shopper.util;

import java.util.List;
import cl.rdrp.planilla_shopper.data.Registro;

/** Normalización en memoria, sin IO ni configuración actual. */
public final class BackupNormalizacion {
    private BackupNormalizacion() {}

    public static void normalizarImportados(List<Registro> registros) {
        if (registros == null) return;
        for (Registro r : registros) CalculoNuevoRegistro.normalizarOrigen111(r);
    }
}
