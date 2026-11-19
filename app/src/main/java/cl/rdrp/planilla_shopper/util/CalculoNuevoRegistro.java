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

    /** Edita únicamente los KM del snapshot; sin cambios conserva incluso los montos.
     * No consulta Prefs ni tramos: fecha, sala y tarifas pertenecen al pedido. */
    public static void editarKm(Registro r, double entrega, double retorno) {
        if (r.calc_origen != 2) throw new IllegalArgumentException("El pedido no usa cálculo nuevo");
        Modalidad modalidad = Modalidad.fromName(r.modalidad);
        if (modalidad == Modalidad.PICKER) return;
        if (!Double.isFinite(entrega) || !Double.isFinite(retorno)
                || !Double.isFinite(entrega + retorno) || entrega < 0 || retorno < 0) {
            throw new IllegalArgumentException("KM debe ser finito y no negativo");
        }
        if (r.km_entrega != null && r.km_retorno != null
                && r.km_entrega == entrega && r.km_retorno == retorno) return;
        r.km_entrega = entrega;
        r.km_retorno = retorno;
        r.km = sumarKm(entrega, retorno);
        r.monto_km = Math.round(r.km * r.valor_km);
        r.monto_bono_km = Config.bonoKmAplica(r.fecha, modalidad, r.local)
                ? Math.round(r.km * r.bono_por_km) : 0;
        r.monto_bruto = r.monto_base + r.monto_sku + r.monto_km + r.monto_bono_km;
    }

    @FunctionalInterface
    public interface ResolverBase {
        int getValor(int sku, Modalidad modalidad, String fecha);
    }

    /** Edita SKU y KM con las tarifas del snapshot y la base de la fecha del pedido.
     * Sin cambios numéricos conserva todos los campos, incluido el texto del SKU. */
    public static void editarSkuYKm(Registro r, int nuevoSku, double entrega,
                                    double retorno, ResolverBase resolver) {
        if (r.calc_origen != 2) throw new IllegalArgumentException("El pedido no usa cálculo nuevo");
        Modalidad modalidad = Modalidad.fromName(r.modalidad);
        int anterior = Parsers.parseIntOrZero(r.sku);
        boolean cambiaSku = modalidad != Modalidad.DRIVER && anterior != nuevoSku;
        // Resolver antes de modificar el snapshot; editarKm valida antes de escribir.
        long base = r.tarifa_base;
        boolean cambiaTramo = cambiaSku && tramo(anterior) != tramo(nuevoSku);
        if (cambiaTramo) base = nuevoSku <= 0 ? 0 : resolver.getValor(nuevoSku, modalidad, r.fecha);
        editarKm(r, entrega, retorno);
        if (!cambiaSku) return;
        r.sku = String.valueOf(nuevoSku);
        if (cambiaTramo) {
            r.tarifa_base = base;
            r.monto_base = base;
        }
        // El alta multiplica dos int antes de asignar a long.
        r.monto_sku = nuevoSku * (int) r.valor_unit_sku;
        r.monto_bruto = r.monto_base + r.monto_sku + r.monto_km + r.monto_bono_km;
    }

    /** Sin tramo para SKU <= 0; los positivos comparten los límites inclusivos del resolver. */
    private static int tramo(int sku) {
        return sku <= 0 ? 0 : TramosResolver.indiceTramo(sku);
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
