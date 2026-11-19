package cl.rdrp.planilla_shopper.util;

import java.math.BigDecimal;

import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;

/** Cálculo puro del snapshot de alta. El llamador resuelve tarifas y bono con
 * Config (Prefs, TramosResolver, fecha y BonoKmPolicy), sin duplicar esas reglas.
 * No descuenta comisión ni combustible del bruto, igual que RegistroAdapter. */
public final class CalculoNuevoRegistro {
    private CalculoNuevoRegistro() {}

    public static double sumarKm(double entrega, double retorno) {
        // Suma decimal antes de convertir a REAL: 1.55 + 1.53 queda como 3.08.
        return BigDecimal.valueOf(entrega).add(BigDecimal.valueOf(retorno)).doubleValue();
    }

    /** Solo para registros nuevos: no modifica cant ni datos de pedidos históricos.
     * montoBonoKm es el resultado ya redondeado de Config.calcularBonoKm.
     * tarifaBase es el tramo vigente, no un cargo adicional por pedido. */
    public static void completar(Registro r, double entrega, double retorno,
                                 int tarifaBase, int valorUnitSku, double valorKm,
                                 double bonoPorKm, long montoBonoKm, double comision,
                                 double rendimiento, int precioLitro) {
        Modalidad modalidad = Modalidad.fromName(r.modalidad);
        boolean usaKm = modalidad != Modalidad.PICKER;
        int sku = modalidad == Modalidad.DRIVER ? 0 : Parsers.parseIntOrZero(r.sku);
        r.km_entrega = usaKm ? entrega : null;
        r.km_retorno = usaKm ? retorno : null;
        r.km = usaKm ? sumarKm(entrega, retorno) : 0.0;
        r.tarifa_base = modalidad == Modalidad.DRIVER ? 0 : tarifaBase;
        r.valor_unit_sku = valorUnitSku;
        r.valor_km = valorKm;
        r.bono_por_km = bonoPorKm;
        r.comision_porc = comision;
        r.rendimiento_km_litro = rendimiento;
        r.precio_litro = precioLitro;
        r.monto_base = r.tarifa_base;
        // Mantiene la multiplicación int de los lectores actuales.
        r.monto_sku = sku * valorUnitSku;
        r.monto_km = Math.round(r.km * valorKm);
        r.monto_bono_km = usaKm ? montoBonoKm : 0;
        r.monto_bruto = r.monto_base + r.monto_sku + r.monto_km + r.monto_bono_km;
        r.calc_origen = 2;
    }
}
