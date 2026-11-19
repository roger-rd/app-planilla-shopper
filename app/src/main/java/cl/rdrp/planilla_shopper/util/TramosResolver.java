package cl.rdrp.planilla_shopper.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cl.rdrp.planilla_shopper.App;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.TramosVersion;

/**
 * Resolver de tramos editables con cache en memoria.
 *
 * El cache vive en memoria y se calienta async desde App.onCreate() (y
 * cada vez que se llama invalidate() despues de un edit). Los adapters
 * y activities llaman getValor() en UI thread o bg thread indistintamente
 * y NUNCA tocan DB en el hot path: si el cache no esta listo, se cae al
 * fallback hardcoded (Config.TRAMOS_VALORES_HISTORICOS), que coincide
 * exactamente con el seed inicial — asi no hay riesgo de mostrar
 * numeros distintos por race condition de boot.
 *
 * Por que asi:
 *   - Llamar dao.listByModalidad() sync desde UI thread crashea Room.
 *   - Hacer todo el calculo async cambiaria la arquitectura de todos
 *     los adapters; demasiado refactor para una optimizacion menor.
 *   - El warmup es muy rapido (3 modalidades, pocas versiones c/u).
 *
 * Concurrencia: cache es ConcurrentHashMap. warmup() escribe atomicamente
 * cada modalidad. Si dos hilos llaman warmup simultaneamente el ultimo
 * gana (resultado deterministico porque ambos leen lo mismo de DB).
 */
public class TramosResolver {

    /** Cache: modalidad -> lista DESC por fecha_desde. */
    private static final Map<String, List<TramosVersion>> cache = new ConcurrentHashMap<>();

    private TramosResolver() {} // no instanciable

    /**
     * Devuelve el valor del tramo correspondiente a skuQty para la modalidad
     * y fecha indicadas. Aplica los limites fijos definidos en
     * Config.TRAMOS_LIMITES.
     *
     * Hot path: solo lee del cache. Si el cache esta vacio para esta
     * modalidad (warmup todavia no corrio o todavia no termino), cae al
     * valor HARDCODED. Eso es seguro: hardcoded == seed inicial.
     */
    public static int getValor(int skuQty, Modalidad modalidad, String fechaIso) {
        if (skuQty <= 0) return 0;
        int idxTramo = indiceTramo(skuQty);

        TramosVersion v = vigente(modalidad, fechaIso);
        if (v == null) {
            return Config.TRAMOS_VALORES_HISTORICOS[idxTramo - 1];
        }
        return v.valorTramo(idxTramo);
    }

    /**
     * Version vigente para esa modalidad y fecha, leida del cache.
     * Retorna null si el cache no esta listo o no hay version aplicable.
     */
    public static TramosVersion vigente(Modalidad modalidad, String fechaIso) {
        if (fechaIso == null || fechaIso.trim().isEmpty()) {
            fechaIso = Fechas.hoyISO();
        }
        List<TramosVersion> versiones = cache.get(modalidad.name());
        if (versiones == null) return null;
        // versiones viene ordenada DESC por fecha_desde; la primera con
        // fecha_desde <= fechaIso es la vigente.
        for (TramosVersion v : versiones) {
            if (v.fechaDesde.compareTo(fechaIso) <= 0) {
                return v;
            }
        }
        return null;
    }

    /**
     * Carga (o recarga) el cache desde DB para las 3 modalidades, en bg.
     * Llamar desde App.onCreate() para warmup inicial, y desde la
     * pantalla de edicion despues de cualquier insert/update/delete.
     *
     * No bloquea: corre en AppDatabase.ioExecutor. Mientras se este
     * ejecutando, getValor sigue retornando lo viejo (o fallback si es
     * el primer arranque), nunca crashea.
     */
    public static void warmupAsync() {
        AppDatabase.ioExecutor.execute(TramosResolver::warmupSync);
    }

    /** Igual que warmupAsync pero bloqueante. Para tests o flujos donde
     * ya se este en un bg thread y se quiera asegurar que el cache esta
     * listo antes de continuar. */
    public static void warmupSync() {
        for (Modalidad m : Modalidad.values()) {
            List<TramosVersion> fromDb = AppDatabase.get(App.get())
                    .tramosDao()
                    .listByModalidad(m.name());
            if (fromDb == null) fromDb = Collections.emptyList();
            cache.put(m.name(), fromDb);
        }
    }

    /**
     * Limpia el cache y dispara warmup async. Llamar despues de cualquier
     * insert/update/delete en tramos_versions desde TramosActivity.
     */
    public static void invalidate() {
        cache.clear();
        warmupAsync();
    }

    /**
     * Indice de tramo (1..8) para una cantidad de SKU, usando los limites
     * fijos de Config.TRAMOS_LIMITES.
     */
    public static int indiceTramo(int skuQty) {
        int[] limites = Config.TRAMOS_LIMITES;
        for (int i = 0; i < limites.length; i++) {
            if (skuQty <= limites[i]) return i + 1;
        }
        return limites.length + 1;
    }
}
