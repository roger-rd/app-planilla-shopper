package cl.rdrp.planilla_shopper.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.TramosVersion;

import static org.junit.Assert.*;

/**
 * Referencia v13: llama a logica de produccion pura, sin duplicar formulas de UI.
 * RegistroAdapter (Main) incluye bono KM; PedidoDetalleAdapter lo omite.
 * VistaGeneral, Dashboard y MonthlySummary agregan bono KM y bonos manuales.
 * Dashboard descuenta combustible estimado y redondea comision/combustible;
 * MonthlySummary descuenta cargas reales y mantiene doubles hasta formatear.
 * Esas diferencias de Activities NO quedan ejecutadas por estos tests JVM.
 * Los getters de Prefs y bonos positivos requieren Context y quedan sin cubrir.
 */
public class CalculosV13CharacterizationTest {
    private Map<String, List<TramosVersion>> cache;
    private Map<String, List<TramosVersion>> original;

    @Before
    @SuppressWarnings("unchecked")
    public void aislarCacheSinRoomNiCambiosDeProduccion() throws Exception {
        // Fixture de cache solamente: no prueba DAO, warmup ni persistencia.
        Field field = TramosResolver.class.getDeclaredField("cache");
        field.setAccessible(true);
        cache = (Map<String, List<TramosVersion>>) field.get(null);
        original = new HashMap<>(cache);
        cache.clear();
    }

    @After
    public void restaurarCache() {
        cache.clear();
        cache.putAll(original);
    }

    @Test
    public void todosLosLimitesInclusivosConservanMontosHistoricosEnLasTresModalidades() {
        int[] cantidades = {-1, 0, 1, 10, 11, 30, 31, 50, 51, 70, 71, 90,
                91, 125, 126, 150, 151, Integer.MAX_VALUE};
        int[] pesos = {0, 0, 2623, 2623, 3026, 3026, 4035, 4035, 5044, 5044,
                7061, 7061, 8070, 8070, 9079, 9079, 10088, 10088};
        for (Modalidad m : Modalidad.values()) {
            for (int i = 0; i < cantidades.length; i++) {
                assertEquals(m + " SKU=" + cantidades[i], pesos[i],
                        Config.basePorSku(cantidades[i], m, "2026-03-26"));
                assertEquals(pesos[i], Config.basePorSku(cantidades[i]));
            }
        }
    }

    @Test
    public void versionVigenteEsInclusivaPorFechaYModalidadPeroOverloadLegacyLaIgnora() {
        TramosVersion vieja = new TramosVersion("SHOPPER", "2025-01-01",
                100, 200, 300, 400, 500, 600, 700, 800);
        TramosVersion nueva = new TramosVersion("SHOPPER", "2026-03-26",
                110, 210, 310, 410, 510, 610, 710, 810);
        cache.put("SHOPPER", Arrays.asList(nueva, vieja)); // DAO entrega DESC.
        assertEquals(2623, Config.basePorSku(1, Modalidad.SHOPPER, "2024-12-31"));
        assertEquals(100, Config.basePorSku(1, Modalidad.SHOPPER, "2025-01-01"));
        assertEquals(100, Config.basePorSku(1, Modalidad.SHOPPER, "2026-03-25"));
        int[] cantidades = {1, 11, 31, 51, 71, 91, 126, 151};
        int[] pesos = {110, 210, 310, 410, 510, 610, 710, 810};
        for (int i = 0; i < cantidades.length; i++) {
            assertEquals(pesos[i], Config.basePorSku(cantidades[i], Modalidad.SHOPPER, "2026-03-26"));
        }
        assertEquals(110, Config.basePorSku(1, Modalidad.SHOPPER, "2027-01-01"));
        assertEquals(2623, Config.basePorSku(1, Modalidad.DRIVER, "2026-03-26"));
        assertEquals(2623, Config.basePorSku(1));
        assertEquals(0, Config.basePorSku(0, Modalidad.SHOPPER, "2026-03-26"));
    }

    @Test
    public void kmCambianDe234A276EnElCorteYRedondeanCadaRegistro() {
        assertEquals(293L, Config.calcularTotalKm(1.25, "2026-03-25"));
        assertEquals(345L, Config.calcularTotalKm(1.25, "2026-03-26"));
        assertEquals(586L, Config.calcularTotalKm(1.25, "2026-03-25")
                + Config.calcularTotalKm(1.25, "2026-03-25"));
        assertEquals(585L, Config.calcularTotalKm(2.5, "2026-03-25"));
        assertEquals(0L, Config.calcularTotalKm(0, "2026-03-26"));
        // El helper no valida negativos; el formulario de alta si los rechaza.
        assertEquals(-292L, Config.calcularTotalKm(-1.25, "2026-03-25"));
    }

    @Test
    public void fechasAusentesOInvalidasUsan234PeroElParserEsLenient() {
        for (String fecha : new String[]{null, "", " ", "invalida"}) {
            assertEquals(234.0, Config.getValorKm(fecha), 0.0);
        }
        // SimpleDateFormat normaliza marzo 32 a abril 1; no corregir en bloque A.
        assertEquals(276.0, Config.getValorKm("2026-03-32"), 0.0);
    }

    @Test
    public void policyHabilita139InclusoPickerYModalidadNulaSinDecidirElMonto() {
        for (Modalidad m : Modalidad.values()) {
            assertTrue(BonoKmPolicy.aplica("139", m));
            assertTrue(BonoKmPolicy.aplica(" 139 ", m));
            for (String sala : new String[]{null, "", " ", "0139", "140", "139.0"}) {
                assertFalse(BonoKmPolicy.aplica(sala, m));
            }
        }
        assertTrue(BonoKmPolicy.aplica("139", null));
    }

    @Test
    public void bonoSinSalaEsCeroInclusoLunesConKmPositivos() {
        for (Modalidad m : Modalidad.values()) {
            assertEquals(0.0, Config.calcularBonoKm(10, "2026-03-02", m), 0.0);
            assertEquals(0.0, Config.calcularBonoKm(10, "2026-03-02", m, "140"), 0.0);
        }
    }

    @Test
    public void bonoDescartaKmNoPositivosYFechaAusenteAntesDeLeerPrefs() {
        assertEquals(0.0, Config.calcularBonoKm(0, "2026-03-02", Modalidad.SHOPPER, "139"), 0.0);
        assertEquals(0.0, Config.calcularBonoKm(-1, "2026-03-02", Modalidad.SHOPPER, "139"), 0.0);
        for (String fecha : new String[]{null, "", " "}) {
            assertEquals(0.0, Config.calcularBonoKm(10, fecha, Modalidad.SHOPPER, "139"), 0.0);
        }
    }

    @Test
    public void domingoDesdeElCorteYDeMiercolesASabadoNoHayBono() {
        // Solo dias excluidos: no confundir un fallo de Context capturado con bono=0.
        for (String fecha : new String[]{"2026-03-01", "2026-03-08", "2026-02-25",
                "2026-02-26", "2026-02-27", "2026-02-28"}) {
            assertEquals(fecha, 0.0,
                    Config.calcularBonoKm(10, fecha, Modalidad.SHOPPER, "139"), 0.0);
        }
    }
}
