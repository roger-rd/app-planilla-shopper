package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.TramosVersion;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Reutiliza el aislamiento/restauración del cache y la caracterización vigente. */
public class CalculoNuevoRegistroTest extends CalculosV13CharacterizationTest {
    private Registro nuevo(Modalidad m, double entrega, double retorno, long bono) {
        Registro r = new Registro("2026-03-26", "139",
                m == Modalidad.DRIVER ? "0" : "11", 0, 123, 1, 0);
        r.modalidad = m.name();
        CalculoNuevoRegistro.completar(r, entrega, retorno,
                Config.basePorSku(Integer.parseInt(r.sku), m, r.fecha),
                61, Config.getValorKm(r.fecha), 100, bono, .1525, 10, 1200);
        return r;
    }

    @Test public void sumaDecimalConComaOPuntoSinColaBinaria() {
        double entrega = Parsers.parseDoubleOrNull("1,55");
        double retorno = Parsers.parseDoubleOrNull("1.53");
        Registro r = nuevo(Modalidad.SHOPPER, entrega, retorno, 0);
        assertEquals(3.08, r.km, 0.0);
        assertEquals("3.08", Double.toString(r.km));
        assertEquals(1.55, r.km_entrega, 0.0);
        assertEquals(1.53, r.km_retorno, 0.0);
    }

    @Test public void retornoCeroSeGuardaNoNulo() {
        Registro r = nuevo(Modalidad.DRIVER, 1.55, 0, 0);
        assertEquals(Double.valueOf(0.0), r.km_retorno);
        assertEquals(1.55, r.km, 0.0);
    }

    @Test public void modalidadesYTarifasCompletas() {
        for (Modalidad m : Modalidad.values()) {
            Registro r = nuevo(m, 1.55, 1.53, 308);
            assertEquals(2, r.calc_origen);
            assertEquals(0, r.cant);
            assertEquals(61, r.valor_unit_sku);
            assertEquals(276, r.valor_km, 0);
            assertEquals(100, r.bono_por_km, 0);
            assertEquals(.1525, r.comision_porc, 0);
            assertEquals(10, r.rendimiento_km_litro, 0);
            assertEquals(1200, r.precio_litro);
            assertEquals(r.tarifa_base, r.monto_base);
            assertEquals(r.monto_base + r.monto_sku + r.monto_km + r.monto_bono_km,
                    r.monto_bruto);
            assertEquals(m == Modalidad.DRIVER ? 0 : 3026, r.monto_base);
            assertEquals(m == Modalidad.DRIVER ? 0 : 671, r.monto_sku);
            if (m == Modalidad.PICKER) {
                assertNull(r.km_entrega);
                assertNull(r.km_retorno);
                assertEquals(0, r.km, 0);
                assertEquals(0, r.monto_km);
                assertEquals(0, r.monto_bono_km);
            } else {
                assertEquals(850, r.monto_km);
                assertEquals(308, r.monto_bono_km);
            }
        }
    }

    @Test public void coincideConFormulaActualEnTresModalidadesYAmbasTarifasKm() {
        for (Modalidad m : Modalidad.values()) {
            for (String fecha : new String[]{"2026-03-25", "2026-03-26"}) {
                Registro r = nuevo(m, 1.55, 1.53, 0);
                r.fecha = fecha;
                int sku = Integer.parseInt(r.sku);
                int base = Config.basePorSku(sku, m, fecha);
                long bono = Math.round(Config.calcularBonoKm(r.km, fecha, m, r.local));
                CalculoNuevoRegistro.completar(r, 1.55, 1.53, base, 61,
                        Config.getValorKm(fecha), 100, bono, .1525, 10, 1200);
                double actual = (long) base + sku * 61
                        + Config.calcularTotalKm(r.km, fecha)
                        + Config.calcularBonoKm(r.km, fecha, m, r.local);
                assertEquals(actual, r.monto_bruto, 0);
            }
        }
    }

    @Test @SuppressWarnings("unchecked")
    public void baseVersionadaSeIncluyeExactamenteUnaVez() throws Exception {
        Field field = TramosResolver.class.getDeclaredField("cache");
        field.setAccessible(true);
        Map<String, List<TramosVersion>> cache =
                (Map<String, List<TramosVersion>>) field.get(null);
        cache.put("SHOPPER", Collections.singletonList(new TramosVersion(
                "SHOPPER", "2026-03-26", 100, 9876, 300, 400, 500, 600, 700, 800)));
        Registro r = nuevo(Modalidad.SHOPPER, 1.55, 1.53, 308);
        assertEquals(9876, r.tarifa_base);
        assertEquals(9876 + 671 + 850 + 308, r.monto_bruto);
    }
}
