package cl.rdrp.planilla_shopper.util;

import java.util.Calendar;

public class Config {
    public static final double RENDIMIENTO_KM_POR_LITRO = 10.0;
    public static final int PRECIO_LITRO = 1200;
    public static final double COMISION_PORC = 0.1525; // 15.25%

    public static final int VALOR_UNIT_SKU = 61;   // $ por SKU
    public static final double VALOR_UNIT_KM = 234.0; // $ por KM (KM es double)
    public static final Double BONO_POR_KM_DOM_LUN_MAR = 100.0; // BONO DOMINGO, LUNES, MARTES

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


    // Calcula el bono en pesos
    public static Double calcularBonoKm(double km, String fechaIso) {
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

            // $100 por km recorrido
            return (double) Math.round(km * BONO_POR_KM_DOM_LUN_MAR);

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


