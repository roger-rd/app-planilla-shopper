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

    // ===== Tramos por SKU =====
    //
    // Los LIMITES son fijos (decision: la cantidad de tramos no cambia).
    // Solo los VALORES asociados a cada tramo son editables y versionados
    // por modalidad+fecha en la tabla tramos_versions. Estas constantes:
    //
    //   - TRAMOS_LIMITES: limite superior INCLUSIVO de cada tramo (1..7).
    //     skuQty > el ultimo limite cae en el tramo 8 ("mas de 150").
    //   - TRAMOS_VALORES_HISTORICOS: valores hardcoded pre-feature. Se
    //     usan SOLO como fallback defensivo si no hay version vigente en
    //     DB para la modalidad consultada (no deberia pasar despues del
    //     seed de la migration 12->13).
    //
    // Si en el futuro se quiere cambiar la CANTIDAD de tramos, este array
    // y los campos t1..t8 de TramosVersion tendrian que evolucionar juntos
    // (probablemente con una migration adicional).
    public static final int[] TRAMOS_LIMITES = {10, 30, 50, 70, 90, 125, 150};
    public static final int[] TRAMOS_VALORES_HISTORICOS =
            {2623, 3026, 4035, 5044, 7061, 8070, 9079, 10088};

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

    // ===== basePorSku — overloads =====
    //
    // La firma canonica es basePorSku(skuQty, modalidad, fechaIso): consulta
    // la version vigente de tramos para esa modalidad y fecha via
    // TramosResolver. Esto reemplaza el viejo hardcode con valores
    // editables y versionados.
    //
    // El overload sin args queda @Deprecated y mantiene los valores
    // HARDCODED historicos para compat defensiva: si algun consumidor
    // quedo sin migrar, no rompe — sigue devolviendo los mismos numeros
    // que daba antes. Pero pierde el versionado y la edicion.

    /**
     * @deprecated usar basePorSku(skuQty, modalidad, fechaIso) para que
     * el calculo respete los tramos editables y el versionado por fecha.
     * Este overload retorna los valores hardcoded historicos.
     */
    @Deprecated
    public static int basePorSku(int skuQty) {
        if (skuQty <= 0) return 0;
        int[] valores = TRAMOS_VALORES_HISTORICOS;
        int[] limites = TRAMOS_LIMITES;
        for (int i = 0; i < limites.length; i++) {
            if (skuQty <= limites[i]) return valores[i];
        }
        return valores[valores.length - 1];
    }

    /**
     * Valor base por SKU usando la version vigente para esa modalidad y
     * fecha. Delega en TramosResolver que maneja el cache en memoria.
     */
    public static int basePorSku(int skuQty, Modalidad modalidad, String fechaIso) {
        return TramosResolver.getValor(skuQty, modalidad, fechaIso);
    }


    // ===== calcularBonoKm — overloads =====
    //
    // El calculo del bono por km depende de:
    //   - KM del registro (debe ser > 0)
    //   - Fecha (regla actual: lun/mar; dom adicionalmente si fecha < 2026-03-01)
    //   - Modalidad (monto por km configurado per-modalidad en Prefs)
    //   - Sala (N5.5: gating por BonoKmPolicy; hoy solo sala 139 habilita)
    //
    // La firma "canonica" es la de 4 argumentos: calcularBonoKm(km, fecha,
    // modalidad, sala). Los overloads viejos quedan @Deprecated y delegan
    // a la canonica pasando sala=null, lo que hace que la policy retorne
    // false y por tanto el bono sea 0. Esto es defensivo: si algun codigo
    // viejo llama al overload sin sala, obtiene 0 en lugar de un calculo
    // que ignoraba la regla de sala.

    /**
     * @deprecated desde N5.5 usar calcularBonoKm(km, fecha, modalidad, sala).
     * Mantenido por compat; ahora siempre retorna 0 porque sin sala no se
     * puede aplicar la policy.
     */
    @Deprecated
    public static Double calcularBonoKm(double km, String fechaIso) {
        return calcularBonoKm(km, fechaIso, Prefs.getModalidad(App.get()), null);
    }

    /**
     * @deprecated desde N5.5 usar calcularBonoKm(km, fecha, modalidad, sala).
     * Mantenido por compat; ahora siempre retorna 0 porque sin sala no se
     * puede aplicar la policy. Los consumidores deben pasar r.local.
     */
    @Deprecated
    public static Double calcularBonoKm(double km, String fechaIso, Modalidad modalidad) {
        return calcularBonoKm(km, fechaIso, modalidad, null);
    }

    /**
     * Calcula el bono por km del registro.
     *
     * Reglas (de mas restrictivas a menos):
     *   1. KM > 0 y fecha no vacia (sino 0).
     *   2. La combinacion (sala, modalidad) habilita bono via BonoKmPolicy.
     *      Hoy: solo sala "139" habilita; en N6 sera configurable.
     *   3. El dia de la semana es lun/mar; o domingo si la fecha es anterior
     *      al 2026-03-01 (fecha de corte historica fija).
     *   4. Monto = km * getBonoPorKmDomLunMar(modalidad). Cada modalidad
     *      tiene su monto configurable; Picker default 0 -> no recibe.
     */
    public static Double calcularBonoKm(double km, String fechaIso,
                                        Modalidad modalidad, String sala) {
        if (km <= 0) return 0.0;
        if (fechaIso == null || fechaIso.trim().isEmpty()) return 0.0;

        // N5.5: gating por sala+modalidad. Si la policy dice que no aplica,
        // no se evalua el resto. Esto deja arquitectura lista para N6 sin
        // tocar a los consumidores.
        if (!BonoKmPolicy.aplica(sala, modalidad)) return 0.0;

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
