package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.data.Registro;

/** Selección pura de fuentes; no recalcula tarifas, compone totales ni redondea.
 * El argumento legacy lo calcula el consumidor con sus expresiones originales.
 * En una rama ya identificada como guardada se puede pasar cero: no se utiliza.
 */
public final class MontosRegistro {
    private MontosRegistro() {}

    public static boolean usaGuardados(Registro r) { return CalculoNuevoRegistro.esNuevo(r); }
    public static long base(Registro r, long legacy) { return usaGuardados(r) ? r.monto_base : legacy; }
    public static long sku(Registro r, long legacy) { return usaGuardados(r) ? r.monto_sku : legacy; }
    public static long km(Registro r, long legacy) { return usaGuardados(r) ? r.monto_km : legacy; }
    public static double bonoKm(Registro r, double legacy) { return usaGuardados(r) ? r.monto_bono_km : legacy; }
    public static double comision(Registro r, double legacy) { return usaGuardados(r) ? r.comision_porc : legacy; }

    // Si falta rendimiento válido, Dashboard conserva AMBAS fuentes legacy.
    public static boolean usaCombustibleGuardado(Registro r) {
        return usaGuardados(r) && r.rendimiento_km_litro > 0;
    }
    public static double rendimiento(Registro r, double legacy) {
        return usaCombustibleGuardado(r) ? r.rendimiento_km_litro : legacy;
    }
    public static long precioLitro(Registro r, long legacy) {
        return usaCombustibleGuardado(r) ? r.precio_litro : legacy;
    }
}
