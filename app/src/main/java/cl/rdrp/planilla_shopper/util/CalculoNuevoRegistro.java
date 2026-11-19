package cl.rdrp.planilla_shopper.util;

import java.math.BigDecimal;

import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;

/** Cálculo puro del snapshot de alta. El llamador resuelve tarifas y bono con
 * Config (Prefs, TramosResolver, fecha y BonoKmPolicy), sin duplicar esas reglas.
 * No descuenta comisión ni combustible del bruto, igual que RegistroAdapter. */
public final class CalculoNuevoRegistro {
    public static final int ORIGEN_1_1_1 = 2;
    public static final int ORIGEN_BONO_CONFIG = 3;

    private CalculoNuevoRegistro() {}

    public static boolean esNuevo(Registro r) {
        return r.calc_origen == ORIGEN_1_1_1 || r.calc_origen == ORIGEN_BONO_CONFIG;
    }

    /** Convierte el marcador junto con la tarifa; conserva los montos originales. */
    public static void normalizarOrigen111(Registro r) {
        if (r.calc_origen != ORIGEN_1_1_1) return;
        normalizarBonoSnapshot111(r);
        r.calc_origen = ORIGEN_BONO_CONFIG;
    }

    public static double sumarKm(double entrega, double retorno) {
        // Suma decimal antes de convertir a REAL: 1.55 + 1.53 queda como 3.08.
        return BigDecimal.valueOf(entrega).add(BigDecimal.valueOf(retorno)).doubleValue();
    }

    /** Edita únicamente los KM del snapshot; sin cambios conserva incluso los montos.
     * No consulta Prefs ni tramos: fecha, sala y tarifas pertenecen al pedido. */
    public static void editarKm(Registro r, double entrega, double retorno) {
        if (!esNuevo(r)) throw new IllegalArgumentException("El pedido no usa cálculo nuevo");
        Modalidad modalidad = Modalidad.fromName(r.modalidad);
        if (modalidad == Modalidad.PICKER) {
            normalizarOrigen111(r);
            return;
        }
        if (!Double.isFinite(entrega) || !Double.isFinite(retorno)
                || !Double.isFinite(entrega + retorno) || entrega < 0 || retorno < 0) {
            throw new IllegalArgumentException("KM debe ser finito y no negativo");
        }
        normalizarOrigen111(r);
        if (r.km_entrega != null && r.km_retorno != null
                && r.km_entrega == entrega && r.km_retorno == retorno) return;
        r.km_entrega = entrega;
        r.km_retorno = retorno;
        r.km = sumarKm(entrega, retorno);
        r.monto_km = Math.round(r.km * r.valor_km);
        r.monto_bono_km = r.bono_por_km > 0
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
        if (!esNuevo(r)) throw new IllegalArgumentException("El pedido no usa cálculo nuevo");
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

    /** Alta con parámetros nuevos. La tarifa positiva guarda el derecho aun con cero KM. */
    public static void completarNuevo(Registro r, double entrega, double retorno,
                                      int tarifaBase, int valorUnitSku, double valorKm,
                                      BonoConfig bono, String sala, double comision,
                                      double rendimiento, int precioLitro) {
        boolean derecho = Modalidad.fromName(r.modalidad) != Modalidad.PICKER
                && BonoConfig.derecho(bono, r.fecha, sala) && bono.monto > 0;
        double tarifa = derecho ? bono.monto : 0.0;
        long monto = derecho ? Math.round(sumarKm(entrega, retorno) * tarifa) : 0;
        completar(r, entrega, retorno, tarifaBase, valorUnitSku, valorKm, tarifa,
                monto, comision, rendimiento, precioLitro);
        r.calc_origen = ORIGEN_BONO_CONFIG;
    }

    @FunctionalInterface
    public interface DerechoLegacy {
        boolean aplica(String fecha, Modalidad modalidad, String sala);
    }

    /** Normaliza SOLO snapshots 1.1.1, antes de editar: no cambia ningún monto.
     * El llamador convierte el marcador al completar la normalización.
     * No ejecutar sobre altas nuevas: su derecho puede diferir de la regla legacy. */
    public static boolean normalizarBonoSnapshot111(Registro r, DerechoLegacy legacy) {
        if (r.calc_origen != ORIGEN_1_1_1 || r.bono_por_km == 0.0
                || legacy.aplica(r.fecha, Modalidad.fromName(r.modalidad), r.local)) return false;
        r.bono_por_km = 0.0;
        return true;
    }

    public static boolean normalizarBonoSnapshot111(Registro r) {
        return normalizarBonoSnapshot111(r, Config::bonoKmAplica);
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
        r.calc_origen = ORIGEN_1_1_1;
    }
}
