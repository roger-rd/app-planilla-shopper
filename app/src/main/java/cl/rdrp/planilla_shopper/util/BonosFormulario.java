package cl.rdrp.planilla_shopper.util;

/** Validación de entrada de las pantallas; no cambia reglas de cálculo. */
public final class BonosFormulario {
    private BonosFormulario() {}

    public static boolean valorKmValido(Double valor) {
        return valor != null && !Double.isNaN(valor) && !Double.isInfinite(valor) && valor > 0;
    }

    public static String error(Double monto, boolean activo, int dias, int salas) {
        if (monto == null || Double.isNaN(monto) || Double.isInfinite(monto) || monto < 0) {
            return "Ingresa un monto del bono mayor o igual a 0 y finito";
        }
        if (activo && dias < 1) return "Selecciona al menos un día";
        if (activo && salas < 1) return "Selecciona al menos una sala";
        return null;
    }
}
