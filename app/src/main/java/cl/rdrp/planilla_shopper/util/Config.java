package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.App;
import cl.rdrp.planilla_shopper.data.Modalidad;

/**
 * Parametros de calculo y reglas de negocio.
 *
 * Desde Fase 4b, los 5 parametros configurables (valor por SKU, comision,
 * rendimiento km/litro, precio litro, bono por km dom/lun/mar) viven en
 * SharedPreferences a traves de Prefs.* y se exponen aqui via metodos
 * getter estaticos. Los valores DEFAULT_* son los que se usan cuando el
 * usuario nunca toco la pantalla de parametros (comportamiento original).
 *
 * Desde N3, los 5 parametros viven POR MODALIDAD (Shopper / Picker / Driver):
 *   - Los getters sin argumento usan la modalidad activa (Prefs.getModalidad).
 *   - Los getters con (Modalidad m) leen una modalidad puntual (lo usa la
 *     pantalla de parametros para mostrar/editar cada modalidad por separado).
 *
 * Lo que NO es configurable y permanece duro:
 *   - VALOR_UNIT_KM (constante referencial historica, no se usa en calculos)
 *   - basePorSku() (tramos hardcoded por SKU)
 *   - calcularBonoKm() (regla de dia de la semana y fecha de corte 2026-03-01)
 *   - getValorKm() (logica historica $234/$276 con corte 2026-03-26)
 *
 * Lo anterior se decidio para no romper calculos historicos. Si en el
 * futuro se quieren editar, requeriria estrategia de versionado por fecha.
 */
public class Config {

    // Constante referencial historica (no se usa en calculos)
    public static final double VALOR_UNIT_KM = 234.0;

    // ===== Defaults para SharedPreferences (Fase 4a) =====
    // Si Prefs no tiene valor, los getters de abajo retornan estos defaults.
    // El comportamiento de la app es identico al original mientras el
    // usuario no edite parametros desde ParametrosCalculosActivity.
    public static final int    DEFAULT_VALOR_UNIT_SKU            = 61;
    public static final double DEFAULT_COMISION_PORC             = 0.1525;
    public static final double DEFAULT_RENDIMIENTO_KM_POR_LITRO  = 10.0;
    public static final int    DEFAULT_PRECIO_LITRO              = 1200;
    public static final double DEFAULT_BONO_POR_KM_DOM_LUN_MAR   = 100.0;

    // ===== Getters de parametros configurables (Fase 4b + N3) =====
    //
    // Sin args -> modalidad activa (uso normal desde los calculos).
    // Con Modalidad m -> modalidad puntual (uso desde la pantalla de parametros
    // que permite ver/editar las 3 modalidades aunque solo una este activa).

    public static int getValorUnitSku() {
        return Prefs.getValorUnitSku(App.get());
    }
    public static int getValorUnitSku(Modalidad m) {
        return Prefs.getValorUnitSku(App.get(), m);
    }

    public static double getComisionPorc() {
        return Prefs.getComisionPorc(App.get());
    }
    public static double getComisionPorc(Modalidad m) {
        return Prefs.getComisionPorc(App.get(), m);
    }

    public static double getRendimientoKmPorLitro() {
        return Prefs.getRendimientoKmPorLitro(App.get());
    }
    public static double getRendimientoKmPorLitro(Modalidad m) {
        return Prefs.getRendimientoKmPorLitro(App.get(), m);
    }

    public static int getPrecioLitro() {
        return Prefs.getPrecioLitro(App.get());
    }
    public static int getPrecioLitro(Modalidad m) {
        return Prefs.getPrecioLitro(App.get(), m);
    }

    public static double getBonoPorKmDomLunMar() {
        return Prefs.getBonoPorKmDomLunMar(App.get());
    }
    public static double getBonoPorKmDomLunMar(Modalidad m) {
        return Prefs.getBonoPorKmDomLunMar(App.get(), m);
    }

    // ===== Reglas duras (no configurables) =====

    public static int basePorSku(int skuQty) {
        if (skuQty <= 0) return 0;
        if (skuQty <= 10) return 2623;
        if (skuQty <= 30) return 3026;
        if (skuQty <= 50) return 4035;
        if (skuQty <= 70) return 5044;
        if (skuQty <= 90) return 7061;
        if (skuQty <= 125) return 8070;
        if (skuQty <= 150) return 9079;
        return 10088;
    }


    // Calcula el bono en pesos usando el bono/km de la modalidad ACTIVA.
    // Atajo para codigo legacy. Para usos per-registro, preferir el overload
    // con (Modalidad m) y pasar la modalidad del registro.
    public static Double calcularBonoKm(double km, String fechaIso) {
        return calcularBonoKm(km, fechaIso, Prefs.getModalidad(App.get()));
    }

    /**
     * Calcula el bono por km usando el valor configurado para la modalidad
     * indicada. Las reglas de fecha (dom/lun/mar pre-2026-03-01, lun/mar
     * post) NO dependen de la modalidad: son reglas de negocio fijas.
     *
     * Lo unico que cambia entre modalidades es el monto por km (Prefs
     * "param_{MODALIDAD}_bono_km_dom_lun_mar").
     */
    public static Double calcularBonoKm(double km, String fechaIso, Modalidad modalidad) {
        if (km <= 0) return 0.0;
        if (fechaIso == null || fechaIso.trim().isEmpty()) return 0.0;

        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.util.Date fecha = sdf.parse(fechaIso);
            java.util.Date fechaCambio = sdf.parse("2026-03-01");

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(fecha);

            int day = cal.get(java.util.Calendar.DAY_OF_WEEK);


            boolean aplicaDomingo = fecha.before(fechaCambio);

            boolean aplica =
                    day == java.util.Calendar.MONDAY ||
                            day == java.util.Calendar.TUESDAY ||
                            (aplicaDomingo && day == java.util.Calendar.SUNDAY);
            if(!aplica) return 0.0;

            // monto por km recorrido segun la modalidad del registro
            return (double) Math.round(km * getBonoPorKmDomLunMar(modalidad));

        } catch (Exception e) {
            return 0.0; // si falla el parseo, mejor no dar bono
        }
    }

    //Calculo de bonos por contingencia en KM

    public static double getValorKm(String fechaIso){
        if (fechaIso == null || fechaIso.trim().isEmpty())return 234.0;

        try{
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());

            java.util.Date fecha = sdf.parse(fechaIso);

            // fecha cambio: 26-03-2026

            java.util.Date fechaCambio = sdf.parse("2026-03-26");

            if (fecha.before(fechaCambio)){
                return 234.0;
            } else {
                return 276.0;
            }
        }catch (Exception e){
            return 234.0;
        }
    }

    public static long calcularTotalKm(double km, String fechaIso){
        return Math.round(km * getValorKm(fechaIso));
    }

}
