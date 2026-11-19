package cl.rdrp.planilla_shopper.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import cl.rdrp.planilla_shopper.App;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import org.junit.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** JVM: preferencias en memoria mediante proxies; no ejecuta métodos Android. */
public class BonoConfigTest {
    private final Map<String, Object> valores = new HashMap<>();
    private Context contexto;
    private Object contextoAnterior;
    private Field appContext;

    public static class ContextoPrueba extends ContextWrapper {
        SharedPreferences preferencias;
        public ContextoPrueba() { super(null); }
        @Override public SharedPreferences getSharedPreferences(String nombre, int modo) {
            return preferencias;
        }
    }

    @Before public void prepararPrefs() throws Exception {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) { valores.put((String) args[0], args[1]); return proxy; }
                    if (method.getName().equals("apply")) return null;
                    throw new AssertionError(method);
                });
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "edit": return editor;
                        case "contains": return valores.containsKey(args[0]);
                        case "getString": case "getBoolean": case "getInt":
                            return valores.getOrDefault(args[0], args[1]);
                        default: throw new AssertionError(method);
                    }
                });
        // Omite el constructor stub de ContextWrapper del android.jar de tests JVM.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        ContextoPrueba c = (ContextoPrueba) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), ContextoPrueba.class);
        c.preferencias = prefs; contexto = c;
        appContext = App.class.getDeclaredField("appContext"); appContext.setAccessible(true);
        contextoAnterior = appContext.get(null); appContext.set(null, contexto);
    }
    @After public void restaurarContexto() throws Exception {
        if (appContext != null) appContext.set(null, contextoAnterior);
    }
    private BonoConfig config(boolean activo, double monto, String dias, String salas) {
        return new BonoConfig(activo, monto, BonoConfig.parseDias(dias), BonoConfig.parseSalas(salas));
    }
    private Registro alta(String fecha, String sala, Modalidad m, double km, BonoConfig bono, double valorKm) {
        Registro r = new Registro(fecha, sala, "11", 0, 123, 1, 0); r.modalidad = m.name();
        CalculoNuevoRegistro.completarNuevo(r, km, 0, 3026, 61, valorKm, bono, sala, .1525, 10, 1200);
        return r;
    }
    @Test public void defaultsFallbackYMontoNuevoAisladosPorModalidad() {
        valores.put("param_bono_km_dom_lun_mar", "87.5");
        for (Modalidad m : Modalidad.values()) {
            assertEquals(276, Config.getValorKmNuevo(m), 0);
            assertTrue(Config.bonoConfig(m).activo);
            assertEquals(BonoConfig.parseDias("2,3"), Config.bonoConfig(m).dias);
            assertEquals(BonoConfig.parseSalas("139"), Config.bonoConfig(m).salas);
            assertEquals(87.5, Config.bonoConfig(m).monto, 0);
            double anterior = m == Modalidad.PICKER ? 0 : 100;
            Prefs.setBonoPorKmDomLunMar(contexto, m, anterior);
            assertEquals(anterior, Config.bonoConfig(m).monto, 0);
            Prefs.setBonoKmMonto(contexto, m, 225);
            assertEquals(225, Config.bonoConfig(m).monto, 0);
            assertEquals(anterior, Config.getBonoPorKmDomLunMar(m), 0);
            assertEquals(Double.toString(anterior), valores.get("param_" + m + "_bono_km_dom_lun_mar"));
        }
        assertEquals("87.5", valores.get("param_bono_km_dom_lun_mar"));
        Prefs.setBonoKmMonto(contexto, Modalidad.DRIVER, 999);
        assertEquals(225, Config.bonoConfig(Modalidad.SHOPPER).monto, 0);
    }
    @Test public void serializacionOrdenadaYParseoTolerante() {
        Modalidad m = Modalidad.SHOPPER;
        Prefs.setBonoKmDias(contexto, m, Arrays.asList(7, 3, 2, 3, 0, 8, null));
        Prefs.setBonoKmSalas(contexto, m, Arrays.asList(" 140 ", "139", "", "139", null));
        assertEquals("2,3,7", valores.get("param_SHOPPER_bono_km_dias"));
        assertEquals("139,140", valores.get("param_SHOPPER_bono_km_salas"));
        valores.put("param_SHOPPER_bono_km_dias", "7, x,2,,0,8, 3,2");
        assertEquals(BonoConfig.parseDias("2,3,7"), Prefs.getBonoKmDias(contexto, m));
        valores.put("param_SHOPPER_bono_km_salas", " 140,,139,139, ");
        assertEquals(BonoConfig.parseSalas("139,140"), Prefs.getBonoKmSalas(contexto, m));
        Prefs.setBonoKmDias(contexto, m, Collections.emptySet());
        Prefs.setBonoKmSalas(contexto, m, Collections.emptySet());
        assertTrue(Config.bonoConfig(m).dias.isEmpty()); assertTrue(Config.bonoConfig(m).salas.isEmpty());
    }
    @Test public void derechoDiasSalasActivoYFechasInvalidas() {
        BonoConfig b = config(true, 100, "2,3", "139,140");
        assertTrue(BonoConfig.derecho(b, "2026-03-02", " 139 "));
        assertTrue(BonoConfig.derecho(b, "2026-03-03", "140"));
        for (String fecha : new String[]{null, "", " ", "no-fecha", "2026-02-30", "2026-03-02x", "2026-3-2", "2026-03-04", "2026-02-22", "2026-03-01"})
            assertFalse(String.valueOf(fecha), BonoConfig.derecho(b, fecha, "139"));
        assertFalse(BonoConfig.derecho(b, "2026-03-02", null));
        assertFalse(BonoConfig.derecho(b, "2026-03-02", "141"));
        assertFalse(BonoConfig.derecho(config(false, 100, "2", "139"), "2026-03-02", "139"));
        assertTrue(BonoConfig.derecho(config(true, 100, "1,4,7", "139"), "2026-03-01", "139"));
        assertTrue(BonoConfig.derecho(config(true, 100, "1,4,7", "139"), "2026-03-04", "139"));
        Locale anterior = Locale.getDefault();
        try { Locale.setDefault(new Locale("th", "TH")); assertTrue(BonoConfig.derecho(b, "2026-03-02", "139")); }
        finally { Locale.setDefault(anterior); }
    }
    @Test public void configuracionEsInmutable() {
        Set<Integer> dias = new HashSet<>(Arrays.asList(2,3));
        Set<String> salas = new HashSet<>(Arrays.asList("139"));
        BonoConfig b = new BonoConfig(true, 100, dias, salas);
        dias.clear(); salas.clear(); assertEquals(2, b.dias.size()); assertEquals(1, b.salas.size());
        try { b.dias.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { b.salas.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void habilitadoConCeroKmConservaDerechoAlEditarKmYSku() {
        for (Modalidad m : new Modalidad[]{Modalidad.SHOPPER, Modalidad.DRIVER}) {
            Registro r = alta("2026-03-02", "139", m, 0, Config.bonoConfig(m), 300);
            assertEquals(100, r.bono_por_km, 0); assertEquals(0, r.monto_bono_km);
            Prefs.setBonoKmDias(contexto, m, Arrays.asList(4));
            Prefs.setBonoKmMonto(contexto, m, 999); Prefs.setBonoKmActivo(contexto, m, false);
            assertFalse(BonoConfig.derecho(Config.bonoConfig(m), r.fecha, r.local));
            CalculoNuevoRegistro.editarKm(r, 2, .5);
            assertEquals(250, r.monto_bono_km); assertEquals(750, r.monto_km);
            CalculoNuevoRegistro.editarSkuYKm(r, 12, 3, .5, (sku, mod, fecha) -> 3026);
            assertEquals(350, r.monto_bono_km); assertEquals(100, r.bono_por_km, 0);
        }
    }
    @Test public void sinDerechoNuncaAdquiereBonoPorCambioDeConfig() {
        for (BonoConfig b : Arrays.asList(config(true,100,"4","139"), config(true,100,"2","140"), config(false,100,"2","139"), config(true,0,"2","139"))) {
            Registro r = alta("2026-03-02", "139", Modalidad.SHOPPER, 1, b, 276);
            assertEquals(0, r.bono_por_km, 0); assertEquals(0, r.monto_bono_km);
            Prefs.setBonoKmActivo(contexto, Modalidad.SHOPPER, true);
            Prefs.setBonoKmMonto(contexto, Modalidad.SHOPPER, 300);
            Prefs.setBonoKmDias(contexto, Modalidad.SHOPPER, Arrays.asList(2));
            Prefs.setBonoKmSalas(contexto, Modalidad.SHOPPER, Arrays.asList("139"));
            assertTrue(BonoConfig.derecho(Config.bonoConfig(Modalidad.SHOPPER), r.fecha, r.local));
            CalculoNuevoRegistro.editarSkuYKm(r, 12, 2, .5, (sku, m, fecha) -> 3026);
            assertEquals(0, r.monto_bono_km);
        }
    }
    @Test public void historicosConservanTarifaYBonoTrasConfigNueva() {
        Registro r = new Registro("2026-02-22", "139", "11", 2.5, 0, 0, 0);
        assertEquals(0, r.calc_origen);
        Prefs.setBonoPorKmDomLunMar(contexto, Modalidad.SHOPPER, 87.5);
        double antes = Config.calcularBonoKm(r.km, r.fecha, Modalidad.SHOPPER, r.local);
        assertEquals(219, antes, 0);
        Prefs.setBonoKmMonto(contexto, Modalidad.SHOPPER, 999);
        Prefs.setBonoKmDias(contexto, Modalidad.SHOPPER, Arrays.asList(7));
        Prefs.setBonoKmSalas(contexto, Modalidad.SHOPPER, Arrays.asList("140"));
        Prefs.setBonoKmActivo(contexto, Modalidad.SHOPPER, false);
        Prefs.setValorKmNuevo(contexto, Modalidad.SHOPPER, 300);
        assertEquals(antes, Config.calcularBonoKm(r.km,r.fecha,Modalidad.SHOPPER,r.local), 0);
        assertEquals(87.5, Config.getBonoPorKmDomLunMar(Modalidad.SHOPPER), 0);
        assertEquals(234, Config.getValorKm(r.fecha), 0); assertEquals(585, Config.calcularTotalKm(2.5, r.fecha));
        assertEquals(276, Config.getValorKm("2026-03-26"), 0);
    }
    @Test public void altaUsaParametroKmInclusoAntesDelCorteYControl4277() {
        Prefs.setValorKmNuevo(contexto, Modalidad.SHOPPER, 300);
        Registro r = alta("2026-03-04", "139", Modalidad.SHOPPER, 2.1, Config.bonoConfig(Modalidad.SHOPPER), Config.getValorKmNuevo(Modalidad.SHOPPER));
        assertEquals(300, r.valor_km, 0); assertEquals(630, r.monto_km);
        r = alta("2026-03-04", "139", Modalidad.SHOPPER, 2.1, Config.bonoConfig(Modalidad.SHOPPER), 276);
        assertEquals(3026, r.monto_base); assertEquals(671, r.monto_sku); assertEquals(580, r.monto_km);
        assertEquals(0, r.bono_por_km, 0); assertEquals(4277, r.monto_bruto);
    }
    @Test public void pickerSinKmNiBonoYTarifaNoPositivaNoCobra() {
        Registro r = alta("2026-03-02", "139", Modalidad.PICKER, 5, config(true,100,"2","139"),276);
        assertEquals(0, r.km, 0); assertEquals(0, r.bono_por_km, 0); assertEquals(0, r.monto_bono_km);
        r = alta("2026-03-02", "139", Modalidad.SHOPPER, 1, config(true,-100,"2","139"),276);
        assertEquals(0, r.bono_por_km, 0);
        r.bono_por_km = -100; CalculoNuevoRegistro.editarKm(r, 2, 0); assertEquals(0, r.monto_bono_km);
    }
    @Test public void normalizacion111LegacyIdempotenteSoloTarifa() throws Exception {
        for (String fecha : new String[]{"2026-03-02", "2026-03-03", "2026-02-22", "2026-03-01", "2026-03-04", "2026-02-30"}) {
            for (String sala : new String[]{"139", "140"}) for (int origen : new int[]{0,2}) {
                Registro r = alta(fecha,sala,Modalidad.SHOPPER,0,config(true,100,"2","139"),276);
                r.calc_origen = origen; r.bono_por_km = 100; r.monto_bono_km = 777;
                Map<String,Object> antes = new HashMap<>();
                for (Field f : Registro.class.getFields()) if (!f.getName().equals("bono_por_km")) antes.put(f.getName(), f.get(r));
                boolean cambia = origen == 2 && !Config.bonoKmAplica(fecha, Modalidad.SHOPPER, sala);
                assertEquals(cambia, CalculoNuevoRegistro.normalizarBonoSnapshot111(r, Config::bonoKmAplica));
                assertEquals(cambia ? 0 : 100, r.bono_por_km, 0);
                assertFalse(CalculoNuevoRegistro.normalizarBonoSnapshot111(r));
                for (Field f : Registro.class.getFields()) if (!f.getName().equals("bono_por_km")) assertEquals(f.getName(), antes.get(f.getName()), f.get(r));
            }
        }
    }
}
