package cl.rdrp.planilla_shopper.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helpers de fechas en formato ISO yyyy-MM-dd.
 *
 * Equivalentes a los metodos privados hoyISO() / toLegacy(String) que vivian
 * duplicados en MainActivity y DashboardActivity. Los originales se mantienen
 * por ahora; la sustitucion ocurre en sub-fase 2.1.
 */
public final class Fechas {

    public static final String ISO_PATTERN = "yyyy-MM-dd";

    private Fechas() {}

    /**
     * Fecha actual en formato yyyy-MM-dd usando Locale del sistema.
     * Equivalente a MainActivity.hoyISO() y DashboardActivity.hoyISO().
     */
    public static String hoyISO() {
        return new SimpleDateFormat(ISO_PATTERN, Locale.getDefault()).format(new Date());
    }

    /**
     * Convierte una fecha ISO yyyy-MM-dd al formato legacy dd/MM/yyyy.
     * Si la entrada es null devuelve "". Si tiene menos de 10 caracteres devuelve
     * la entrada original sin transformar. Si ocurre cualquier excepcion al
     * recortar substrings, devuelve la entrada original.
     *
     * Equivalente exacto a MainActivity.toLegacy(String) y DashboardActivity.toLegacy(String).
     */
    public static String toLegacy(String iso) {
        if (iso == null) return "";
        iso = iso.trim();
        if (iso.length() < 10) return iso;
        try {
            String y = iso.substring(0, 4);
            String m = iso.substring(5, 7);
            String d = iso.substring(8, 10);
            return d + "/" + m + "/" + y;
        } catch (Exception e) {
            return iso;
        }
    }

    /**
     * Parsea una fecha ISO yyyy-MM-dd. Lanza ParseException si el formato es invalido.
     */
    public static Date parseISO(String iso) throws ParseException {
        return new SimpleDateFormat(ISO_PATTERN, Locale.getDefault()).parse(iso);
    }

    /**
     * Formatea una Date como yyyy-MM-dd.
     */
    public static String formatISO(Date date) {
        return new SimpleDateFormat(ISO_PATTERN, Locale.getDefault()).format(date);
    }
}
