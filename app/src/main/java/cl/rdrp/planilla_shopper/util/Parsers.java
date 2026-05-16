package cl.rdrp.planilla_shopper.util;

/**
 * Helpers de parseo numerico tolerantes a basura en la entrada.
 *
 * Todos los metodos limpian caracteres no validos antes de intentar el parse,
 * para soportar entradas tipo "$1.234", "123 unidades", "1,5 km", etc.
 *
 * Existen DOS variantes de parseInt con semantica de fallo distinta, porque el
 * codigo actual tiene ambas convenciones en diferentes pantallas:
 *   - parseIntOrNull  -> devuelve null si la entrada no es parseable
 *                        (lo usan MainActivity, RegistroAdapter, DashboardActivity).
 *   - parseIntOrZero  -> devuelve 0 si la entrada no es parseable
 *                        (lo usan VistaGeneralActivity, MonthlySummaryActivity,
 *                         PedidoDetalleAdapter).
 *
 * Unificarlas cambiaria comportamiento, por eso se exponen ambas.
 */
public final class Parsers {

    private Parsers() {}

    /**
     * Variante nullable: equivalente al parseIntOnlyDigits de MainActivity:403,
     * RegistroAdapter:121 y DashboardActivity:295.
     *
     * Devuelve null si la entrada es null, vacia, o no contiene digitos.
     */
    public static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Variante con default 0: equivalente al parseIntOnlyDigits de
     * VistaGeneralActivity:233, MonthlySummaryActivity:170 y PedidoDetalleAdapter:77.
     *
     * Devuelve 0 si la entrada es null, vacia, o no contiene digitos.
     * Nota: el catch original en algunos sitios es Exception y no NumberFormatException;
     * aqui mantenemos Exception para preservar comportamiento exacto.
     */
    public static int parseIntOrZero(String s) {
        if (s == null) return 0;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    /**
     * Equivalente a MainActivity.parseLongStrict(String).
     * Devuelve null si la entrada es null, vacia, o no contiene digitos.
     */
    public static Long parseLongOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Equivalente a MainActivity.parseDoubleStrict(String).
     * Admite coma o punto como separador decimal. Si hay multiples puntos,
     * mantiene solo el primero. Devuelve null si la entrada queda vacia o es
     * solo ".".
     */
    public static Double parseDoubleOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        // admite , o . como separador
        s = s.replace(",", ".").replaceAll("[^0-9.]", "");
        // evita 1..2 casos raros
        int first = s.indexOf('.');
        if (first >= 0) {
            int next = s.indexOf('.', first + 1);
            if (next >= 0) s = s.substring(0, next).replaceAll("\\.+$", "");
        }
        if (s.isEmpty() || s.equals(".")) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}
