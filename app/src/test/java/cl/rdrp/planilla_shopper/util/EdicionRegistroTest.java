package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class EdicionRegistroTest {
    private Registro pedido(Modalidad m) {
        Registro r = new Registro("2026-03-02", "139", "11", 0, 123, 1, 0);
        r.modalidad = m.name();
        CalculoNuevoRegistro.completar(r, 1.55, 1.53, 3026, 61, 17.5, 23.5,
                m == Modalidad.PICKER ? 0 : 72, .1525, 10, 1200);
        return r;
    }

    private Map<String, Object> campos(Registro r) throws Exception {
        Map<String, Object> result = new HashMap<>();
        for (Field f : Registro.class.getFields()) result.put(f.getName(), f.get(r));
        return result;
    }

    @Test public void shopperYDriverRecalculanSoloKmConTarifasGuardadas() throws Exception {
        for (Modalidad m : new Modalidad[]{Modalidad.SHOPPER, Modalidad.DRIVER}) {
            Registro r = pedido(m);
            assertEquals(3.08, r.km, 0);
            Map<String, Object> antes = campos(r);
            CalculoNuevoRegistro.editarKm(r, Parsers.parseDoubleOrNull("2,55"), Parsers.parseDoubleOrNull("1.54"));
            assertEquals(4.09, r.km, 0);
            assertEquals(72, r.monto_km);
            assertEquals(96, r.monto_bono_km);
            assertEquals(r.monto_base + r.monto_sku + 168, r.monto_bruto);
            Map<String, Object> despues = campos(r);
            for (String nombre : new String[]{"km", "km_entrega", "km_retorno", "monto_km", "monto_bono_km", "monto_bruto"}) {
                antes.remove(nombre); despues.remove(nombre);
            }
            assertEquals(antes, despues);
            // La tarifa histórica de Config es distinta; editar no la consulta.
            assertNotEquals(Config.getValorKm(r.fecha), r.valor_km, 0);
        }
    }

    @Test public void retornoCeroYSalaYFechaDelPedido() {
        Registro r = pedido(Modalidad.DRIVER);
        CalculoNuevoRegistro.editarKm(r, 2.55, 0);
        assertEquals(Double.valueOf(0), r.km_retorno);
        assertEquals(2.55, r.km, 0);
        assertEquals(60, r.monto_bono_km);
        r.local = "140";
        CalculoNuevoRegistro.editarKm(r, 3, 0);
        assertEquals(0, r.monto_bono_km);
        r.local = "139"; r.fecha = "2026-03-01";
        CalculoNuevoRegistro.editarKm(r, 4, 0);
        assertEquals(0, r.monto_bono_km);
    }

    @Test public void pickerNoCambiaNada() throws Exception {
        Registro r = pedido(Modalidad.PICKER);
        Map<String, Object> antes = campos(r);
        CalculoNuevoRegistro.editarKm(r, 0, 0);
        assertEquals(antes, campos(r));
        assertNull(r.km_entrega); assertNull(r.km_retorno);
        assertEquals(0, r.km, 0); assertEquals(0, r.monto_km);
        assertEquals(0, r.monto_bono_km);
    }

    @Test public void soloSgVentanaConservaInclusoMontosNoReproducibles() throws Exception {
        Registro r = pedido(Modalidad.SHOPPER);
        r.monto_km = 777; r.monto_bono_km = 888; r.monto_bruto = 999;
        r.sg = 456; r.ventana = 2;
        Map<String, Object> antes = campos(r);
        CalculoNuevoRegistro.editarKm(r, 1.55, 1.53);
        assertEquals(antes, campos(r));
    }

    @Test public void rechazaKmInvalidosSinModificarRegistro() throws Exception {
        for (double[] km : new double[][]{{-1, 0}, {0, -1}, {Double.NaN, 0},
                {0, Double.POSITIVE_INFINITY}, {Double.MAX_VALUE, Double.MAX_VALUE}}) {
            Registro r = pedido(Modalidad.SHOPPER);
            Map<String, Object> antes = campos(r);
            try { CalculoNuevoRegistro.editarKm(r, km[0], km[1]); fail(); }
            catch (IllegalArgumentException expected) { assertEquals(antes, campos(r)); }
        }
    }

    @Test public void elegibilidadConservaCorteDiasParseoLenientYSala() {
        for (Modalidad m : Modalidad.values()) {
            for (String fecha : new String[]{"2026-02-22", "2026-03-02", "2026-03-03", "2026-02-30"}) {
                assertTrue(fecha, Config.bonoKmAplica(fecha, m, " 139 "));
                assertFalse(Config.bonoKmAplica(fecha, m, "140"));
            }
            for (String fecha : new String[]{null, "", " ", "invalida", "2026-03-01",
                    "2026-03-04", "2026-03-05", "2026-03-06", "2026-03-07"}) {
                assertFalse(Config.bonoKmAplica(fecha, m, "139"));
                assertEquals(0, Config.calcularBonoKm(3.08, fecha, m, "139"), 0);
            }
        }
    }

    @Test public void mismoTramoConservaAmbasBasesYTarifaSkuGuardadas() {
        Registro r = pedido(Modalidad.SHOPPER);
        r.sku = "5"; r.tarifa_base = 2623; r.monto_base = 2600;
        r.valor_unit_sku = 79;
        CalculoNuevoRegistro.editarSkuYKm(r, 7, 1.55, 1.53, (sku, m, fecha) -> {
            fail("No debe consultar la tabla aunque su base haya cambiado"); return 9999;
        });
        assertEquals("7", r.sku);
        assertEquals(2623, r.tarifa_base); assertEquals(2600, r.monto_base);
        assertEquals(7 * 79, r.monto_sku);
        assertEquals(2600 + 553 + r.monto_km + r.monto_bono_km, r.monto_bruto);
    }

    @Test public void crucesInclusivosAscendentesYDescendentesUsanFechaYModalidadDelPedido() {
        for (Modalidad modalidad : new Modalidad[]{Modalidad.SHOPPER, Modalidad.PICKER}) {
            for (int[] cambio : new int[][]{{10,11}, {30,31}, {150,151}, {31,30}, {11,10}, {0,5}}) {
                Registro r = pedido(modalidad); r.sku = String.valueOf(cambio[0]);
                r.fecha = "2025-01-09";
                int[] llamadas = {0};
                CalculoNuevoRegistro.editarSkuYKm(r, cambio[1], 1.55, 1.53, (sku, m, fecha) -> {
                    llamadas[0]++;
                    assertEquals(cambio[1], sku); assertEquals(modalidad, m);
                    assertEquals("2025-01-09", fecha);
                    return 4000 + TramosResolver.indiceTramo(sku);
                });
                assertEquals(1, llamadas[0]);
                assertEquals(4000 + TramosResolver.indiceTramo(cambio[1]), r.tarifa_base);
                assertEquals(r.tarifa_base, r.monto_base);
                assertEquals(cambio[1] * 61, r.monto_sku);
                assertEquals(r.monto_base + r.monto_sku + r.monto_km + r.monto_bono_km, r.monto_bruto);
                if (modalidad == Modalidad.PICKER) {
                    assertNull(r.km_entrega); assertNull(r.km_retorno);
                    assertEquals(0, r.monto_km); assertEquals(0, r.monto_bono_km);
                }
            }
        }
    }

    @Test public void ceroPermitidoComoEnAltaSinBaseNiMontoSku() {
        for (Modalidad m : new Modalidad[]{Modalidad.SHOPPER, Modalidad.PICKER}) {
            Registro r = pedido(m);
            CalculoNuevoRegistro.editarSkuYKm(r, 0, 1.55, 1.53, (sku, mod, fecha) -> {
                fail("Cero no necesita tabla"); return 9999;
            });
            assertEquals("0", r.sku); assertEquals(0, r.tarifa_base);
            assertEquals(0, r.monto_base); assertEquals(0, r.monto_sku);
            assertEquals(r.monto_km + r.monto_bono_km, r.monto_bruto);
        }
    }

    @Test public void cambioSimultaneoSkuYKm() {
        Registro r = pedido(Modalidad.SHOPPER);
        CalculoNuevoRegistro.editarSkuYKm(r, 31, 2.55, 1.54, (sku, m, fecha) -> 4567);
        assertEquals(4567, r.tarifa_base); assertEquals(4567, r.monto_base);
        assertEquals(1891, r.monto_sku); assertEquals(4.09, r.km, 0);
        assertEquals(72, r.monto_km); assertEquals(96, r.monto_bono_km);
        assertEquals(6626, r.monto_bruto);
    }

    @Test public void driverIgnoraSkuYConservaCalculoKm() throws Exception {
        Registro r = pedido(Modalidad.DRIVER);
        Map<String, Object> antes = campos(r);
        CalculoNuevoRegistro.editarSkuYKm(r, 99, 1.55, 1.53, (sku, m, fecha) -> {
            fail("Driver no resuelve SKU"); return 9999;
        });
        assertEquals(antes, campos(r));
        CalculoNuevoRegistro.editarSkuYKm(r, 99, 2.55, 1.54, (sku, m, fecha) -> 9999);
        assertEquals(antes.get("sku"), r.sku); assertEquals(0, r.monto_sku);
        assertEquals(168, r.monto_bruto);
    }

    @Test public void sinCambiosConservaSnapshotYTextoSku() throws Exception {
        Registro r = pedido(Modalidad.SHOPPER);
        r.sku = "011"; r.monto_bruto = 999; r.monto_base = 888; r.monto_sku = 777;
        Map<String, Object> antes = campos(r);
        CalculoNuevoRegistro.editarSkuYKm(r, 11, 1.55, 1.53, (sku, m, fecha) -> {
            fail(); return 0;
        });
        assertEquals(antes, campos(r));
    }

    @Test public void multiplicacionSkuMantieneOverflowIntDelAlta() {
        Registro r = pedido(Modalidad.SHOPPER);
        r.valor_unit_sku = Integer.MAX_VALUE;
        CalculoNuevoRegistro.editarSkuYKm(r, 12, 1.55, 1.53, (sku, m, fecha) -> 0);
        assertEquals(12 * Integer.MAX_VALUE, r.monto_sku);
    }

    @Test public void kmInvalidoConCambioSkuNoModificaSnapshot() throws Exception {
        Registro r = pedido(Modalidad.SHOPPER);
        Map<String, Object> antes = campos(r);
        try {
            CalculoNuevoRegistro.editarSkuYKm(r, 31, -1, 0, (sku, m, fecha) -> 4000);
            fail();
        } catch (IllegalArgumentException expected) { assertEquals(antes, campos(r)); }
    }

    @Test public void pedidosAnterioresRechazadosSinCambios() throws Exception {
        Registro r = pedido(Modalidad.SHOPPER); r.calc_origen = 0;
        Map<String, Object> antes = campos(r);
        try {
            CalculoNuevoRegistro.editarSkuYKm(r, 31, 2, 0, (sku, m, fecha) -> 4000);
            fail();
        } catch (IllegalArgumentException expected) { assertEquals(antes, campos(r)); }
    }
}
