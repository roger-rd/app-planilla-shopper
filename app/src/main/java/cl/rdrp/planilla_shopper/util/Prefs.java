package cl.rdrp.planilla_shopper.util;

import android.content.Context;
import android.content.SharedPreferences;

import cl.rdrp.planilla_shopper.data.Modalidad;

/**
 * Wrapper sobre SharedPreferences para guardar configuracion de la app.
 *
 * Convencion de keys:
 *   - "local_default"                       -> nombre del local default del usuario
 *   - "param_*"                             -> parametros LEGACY (pre-N3, sin modalidad)
 *   - "param_{MODALIDAD}_*"                 -> parametros por modalidad (N3)
 *   - "modalidad_actual"                    -> modalidad de trabajo del usuario (N1)
 *
 * Los defaults numericos se toman de Config.DEFAULT_* para mantenerlos en
 * un unico lugar. Si esos defaults cambian, automaticamente cambian aqui.
 *
 * ===== Estrategia de migracion N3 =====
 *
 * Antes de N3 habia 5 keys globales (param_valor_unit_sku, etc.) que servian
 * para los calculos del usuario sin importar modalidad. Ahora los 5 valores
 * pueden ser distintos para Shopper, Picker y Driver.
 *
 * Para no perder los valores que el usuario ya tenia guardados:
 *
 *   1. Si existe una key especifica para esa modalidad (param_SHOPPER_*,
 *      param_PICKER_*, param_DRIVER_*), se usa ese valor.
 *   2. Si NO existe, se hace fallback a la key legacy (param_*) que tiene
 *      el valor que el usuario tenia antes de N3.
 *   3. Si tampoco existe la legacy, se usa el DEFAULT_* de Config.
 *
 * Esto significa que:
 *   - Shoppers existentes conservan sus valores.
 *   - Picker y Driver heredan los mismos valores como placeholder hasta que
 *     el usuario los edite explicitamente (lo cual genera la key por modalidad
 *     y rompe el fallback solo para esa modalidad).
 *
 * Cuando se guarda un valor (set*) siempre se escribe la key POR MODALIDAD.
 * Las keys legacy ya no se escriben (quedan solo como fuente de migracion).
 */
public class Prefs {

    private static final String NAME = "rdrp_prefs";

    private static final String K_LOCAL = "local_default";

    // ===== Keys LEGACY (pre-N3, sin modalidad) =====
    // No se escriben mas. Solo se leen como fallback de migracion.
    private static final String K_VALOR_UNIT_SKU         = "param_valor_unit_sku";
    private static final String K_COMISION_PORC          = "param_comision_porc";
    private static final String K_RENDIMIENTO_KM_LITRO   = "param_rendimiento_km_litro";
    private static final String K_PRECIO_LITRO           = "param_precio_litro";
    private static final String K_BONO_KM_DOM_LUN_MAR    = "param_bono_km_dom_lun_mar";

    // Sufijos de keys por modalidad (N3). La key completa se arma con
    // keyFor(modalidad, sufijo) = "param_{MODALIDAD}_{sufijo}".
    private static final String S_VALOR_UNIT_SKU         = "valor_unit_sku";
    private static final String S_COMISION_PORC          = "comision_porc";
    private static final String S_RENDIMIENTO_KM_LITRO   = "rendimiento_km_litro";
    private static final String S_PRECIO_LITRO           = "precio_litro";
    private static final String S_BONO_KM_DOM_LUN_MAR    = "bono_km_dom_lun_mar";

    // Key de modalidad de trabajo (N1)
    private static final String K_MODALIDAD              = "modalidad_actual";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Arma la key completa por modalidad: "param_SHOPPER_valor_unit_sku". */
    private static String keyFor(Modalidad m, String sufijo) {
        return "param_" + m.name() + "_" + sufijo;
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

    // ===== Parametros de calculo por modalidad (N3) =====
    //
    // Cada parametro tiene:
    //   - getter(Context, Modalidad)    -> lee con fallback legacy -> default
    //   - setter(Context, Modalidad, v) -> escribe solo la key por modalidad
    //   - getter(Context)               -> atajo a la MODALIDAD ACTIVA (compat)
    //   - setter(Context, v)            -> atajo a la MODALIDAD ACTIVA (compat)
    //
    // Los atajos sin Modalidad usan getModalidad(c) para que el codigo que
    // ya existia siga funcionando: la modalidad activa decide que valor leer.
    //
    // Nota: SharedPreferences no tiene putDouble nativo, por lo que los
    // doubles se serializan como String para preservar precision exacta
    // (a diferencia de putFloat que pierde bits) y se recuperan con
    // Double.parseDouble usando el default si el valor no existe.

    // --- Valor por SKU ---

    public static int getValorUnitSku(Context c, Modalidad m) {
        SharedPreferences p = prefs(c);
        String key = keyFor(m, S_VALOR_UNIT_SKU);
        if (p.contains(key)) {
            return p.getInt(key, Config.DEFAULT_VALOR_UNIT_SKU);
        }
        // Fallback a key legacy (= valor pre-N3 del usuario, si lo tenia)
        return p.getInt(K_VALOR_UNIT_SKU, Config.DEFAULT_VALOR_UNIT_SKU);
    }
    public static void setValorUnitSku(Context c, Modalidad m, int v) {
        prefs(c).edit().putInt(keyFor(m, S_VALOR_UNIT_SKU), v).apply();
    }
    public static int getValorUnitSku(Context c) {
        return getValorUnitSku(c, getModalidad(c));
    }
    public static void setValorUnitSku(Context c, int v) {
        setValorUnitSku(c, getModalidad(c), v);
    }

    // --- Comision (decimal) ---

    public static double getComisionPorc(Context c, Modalidad m) {
        SharedPreferences p = prefs(c);
        String key = keyFor(m, S_COMISION_PORC);
        String s = p.getString(key, null);
        if (s == null) {
            // Fallback a legacy
            s = p.getString(K_COMISION_PORC, null);
        }
        if (s == null) return Config.DEFAULT_COMISION_PORC;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_COMISION_PORC; }
    }
    public static void setComisionPorc(Context c, Modalidad m, double v) {
        prefs(c).edit().putString(keyFor(m, S_COMISION_PORC), Double.toString(v)).apply();
    }
    public static double getComisionPorc(Context c) {
        return getComisionPorc(c, getModalidad(c));
    }
    public static void setComisionPorc(Context c, double v) {
        setComisionPorc(c, getModalidad(c), v);
    }

    // --- Rendimiento km/litro ---

    public static double getRendimientoKmPorLitro(Context c, Modalidad m) {
        SharedPreferences p = prefs(c);
        String key = keyFor(m, S_RENDIMIENTO_KM_LITRO);
        String s = p.getString(key, null);
        if (s == null) {
            s = p.getString(K_RENDIMIENTO_KM_LITRO, null);
        }
        if (s == null) return Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO; }
    }
    public static void setRendimientoKmPorLitro(Context c, Modalidad m, double v) {
        prefs(c).edit().putString(keyFor(m, S_RENDIMIENTO_KM_LITRO), Double.toString(v)).apply();
    }
    public static double getRendimientoKmPorLitro(Context c) {
        return getRendimientoKmPorLitro(c, getModalidad(c));
    }
    public static void setRendimientoKmPorLitro(Context c, double v) {
        setRendimientoKmPorLitro(c, getModalidad(c), v);
    }

    // --- Precio litro ---

    public static int getPrecioLitro(Context c, Modalidad m) {
        SharedPreferences p = prefs(c);
        String key = keyFor(m, S_PRECIO_LITRO);
        if (p.contains(key)) {
            return p.getInt(key, Config.DEFAULT_PRECIO_LITRO);
        }
        return p.getInt(K_PRECIO_LITRO, Config.DEFAULT_PRECIO_LITRO);
    }
    public static void setPrecioLitro(Context c, Modalidad m, int v) {
        prefs(c).edit().putInt(keyFor(m, S_PRECIO_LITRO), v).apply();
    }
    public static int getPrecioLitro(Context c) {
        return getPrecioLitro(c, getModalidad(c));
    }
    public static void setPrecioLitro(Context c, int v) {
        setPrecioLitro(c, getModalidad(c), v);
    }

    // --- Bono por km dom/lun/mar ---

    public static double getBonoPorKmDomLunMar(Context c, Modalidad m) {
        SharedPreferences p = prefs(c);
        String key = keyFor(m, S_BONO_KM_DOM_LUN_MAR);
        String s = p.getString(key, null);
        if (s == null) {
            s = p.getString(K_BONO_KM_DOM_LUN_MAR, null);
        }
        if (s == null) return Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR; }
    }
    public static void setBonoPorKmDomLunMar(Context c, Modalidad m, double v) {
        prefs(c).edit().putString(keyFor(m, S_BONO_KM_DOM_LUN_MAR), Double.toString(v)).apply();
    }
    public static double getBonoPorKmDomLunMar(Context c) {
        return getBonoPorKmDomLunMar(c, getModalidad(c));
    }
    public static void setBonoPorKmDomLunMar(Context c, double v) {
        setBonoPorKmDomLunMar(c, getModalidad(c), v);
    }

    // ===== Modalidad de trabajo (N1) =====

    /**
     * Devuelve la modalidad activa. Si no hay valor guardado retorna SHOPPER
     * (fallback seguro: comportamiento original de la app).
     */
    public static Modalidad getModalidad(Context c) {
        return Modalidad.fromName(prefs(c).getString(K_MODALIDAD, null));
    }

    public static void setModalidad(Context c, Modalidad m) {
        prefs(c).edit().putString(K_MODALIDAD, m.name()).apply();
    }

    /**
     * True si el usuario ya eligio alguna modalidad. Sirve para detectar
     * el primer arranque y disparar el popup de bienvenida.
     */
    public static boolean hasModalidad(Context c) {
        return prefs(c).getString(K_MODALIDAD, null) != null;
    }
}
