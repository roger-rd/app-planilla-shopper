package cl.rdrp.planilla_shopper.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wrapper sobre SharedPreferences para guardar configuracion de la app.
 *
 * Convencion de keys:
 *   - "local_default"           -> nombre del local default del usuario
 *   - "param_*"                 -> parametros de calculo (Fase 4a)
 *
 * Los defaults numericos se toman de Config.DEFAULT_* para mantenerlos en
 * un unico lugar. Si esos defaults cambian, automaticamente cambian aqui.
 *
 * Los metodos param_* fueron agregados en Fase 4a y NO se usan todavia
 * desde ningun lado del codigo. La conexion con Config viene en Fase 4b.
 */
public class Prefs {

    private static final String NAME = "rdrp_prefs";

    private static final String K_LOCAL = "local_default";

    // Keys de parametros de calculo (Fase 4a)
    private static final String K_VALOR_UNIT_SKU         = "param_valor_unit_sku";
    private static final String K_COMISION_PORC          = "param_comision_porc";
    private static final String K_RENDIMIENTO_KM_LITRO   = "param_rendimiento_km_litro";
    private static final String K_PRECIO_LITRO           = "param_precio_litro";
    private static final String K_BONO_KM_DOM_LUN_MAR    = "param_bono_km_dom_lun_mar";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // ===== Local default (legacy) =====

    public static void setLocal(Context c, String local) {
        prefs(c).edit().putString(K_LOCAL, local).apply();
    }
    public static String getLocal(Context c) {
        return prefs(c).getString(K_LOCAL, "");
    }
    public static boolean hasLocal(Context c) {
        return !getLocal(c).isEmpty();
    }

    // ===== Parametros de calculo (Fase 4a — sin uso todavia) =====
    //
    // Nota: SharedPreferences no tiene putDouble nativo, por lo que los
    // doubles se serializan como String para preservar precision exacta
    // (a diferencia de putFloat que pierde bits) y se recuperan con
    // Double.parseDouble usando el default si el valor no existe.

    public static int getValorUnitSku(Context c) {
        return prefs(c).getInt(K_VALOR_UNIT_SKU, Config.DEFAULT_VALOR_UNIT_SKU);
    }
    public static void setValorUnitSku(Context c, int v) {
        prefs(c).edit().putInt(K_VALOR_UNIT_SKU, v).apply();
    }

    public static double getComisionPorc(Context c) {
        String s = prefs(c).getString(K_COMISION_PORC, null);
        if (s == null) return Config.DEFAULT_COMISION_PORC;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_COMISION_PORC; }
    }
    public static void setComisionPorc(Context c, double v) {
        prefs(c).edit().putString(K_COMISION_PORC, Double.toString(v)).apply();
    }

    public static double getRendimientoKmPorLitro(Context c) {
        String s = prefs(c).getString(K_RENDIMIENTO_KM_LITRO, null);
        if (s == null) return Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO; }
    }
    public static void setRendimientoKmPorLitro(Context c, double v) {
        prefs(c).edit().putString(K_RENDIMIENTO_KM_LITRO, Double.toString(v)).apply();
    }

    public static int getPrecioLitro(Context c) {
        return prefs(c).getInt(K_PRECIO_LITRO, Config.DEFAULT_PRECIO_LITRO);
    }
    public static void setPrecioLitro(Context c, int v) {
        prefs(c).edit().putInt(K_PRECIO_LITRO, v).apply();
    }

    public static double getBonoPorKmDomLunMar(Context c) {
        String s = prefs(c).getString(K_BONO_KM_DOM_LUN_MAR, null);
        if (s == null) return Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR; }
    }
    public static void setBonoPorKmDomLunMar(Context c, double v) {
        prefs(c).edit().putString(K_BONO_KM_DOM_LUN_MAR, Double.toString(v)).apply();
    }
}
