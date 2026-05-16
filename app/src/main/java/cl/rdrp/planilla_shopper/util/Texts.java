package cl.rdrp.planilla_shopper.util;

/**
 * Helpers de strings de uso general.
 *
 * El nombre es "Texts" (no "TextUtils") para evitar colision con
 * android.text.TextUtils, que es de uso comun en codigo Android.
 */
public final class Texts {

    private Texts() {}

    /**
     * Trim seguro contra null. Devuelve "" si la entrada es null.
     *
     * Equivalente al helper privado s(CharSequence) que esta duplicado en
     * MainActivity:392 y BencinaActivity:191.
     */
    public static String s(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
