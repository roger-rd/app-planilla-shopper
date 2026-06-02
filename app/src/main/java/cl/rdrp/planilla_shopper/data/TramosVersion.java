package cl.rdrp.planilla_shopper.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Snapshot completo de los 8 tramos base por SKU para una modalidad, vigente
 * desde una fecha determinada.
 *
 * Hasta antes de esta feature los 8 valores estaban hardcoded en
 * Config.basePorSku(). Ahora son editables y se versionan por fecha:
 *
 *   - Cada modalidad puede tener N versiones, cada una con una fecha_desde.
 *   - La version vigente para una fecha dada es la de mayor fecha_desde
 *     menor o igual a la fecha consultada.
 *   - Cuando el usuario cambia tramos, crea una nueva version con fecha
 *     de hoy; los registros viejos siguen calculandose con la version que
 *     era vigente en sus fechas, preservando la integridad historica.
 *
 * Los limites de los tramos (≤10, ≤30, ≤50, ...) NO se versionan: son
 * constantes en Config.TRAMOS_LIMITES. Solo los 8 valores en pesos varian.
 *
 * Seed inicial (migration 12->13): se insertan 3 filas, una por modalidad,
 * con fecha_desde = "2025-01-01" (epoca aproximada de inicio real de uso
 * de la app, mas representativa que 1970) y los valores actuales
 * hardcoded. Cualquier registro post-2025-01-01 cae dentro de esta
 * version inicial y obtiene exactamente los mismos numeros que daba el
 * hardcode anterior. Registros pre-2025-01-01 (si los hay) caen al
 * fallback hardcoded del resolver, que tiene exactamente los mismos
 * valores -> tampoco rompen nada.
 */
@Entity(
        tableName = "tramos_versions",
        indices = {@Index(value = {"modalidad", "fecha_desde"})}
)
public class TramosVersion {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** name() del enum Modalidad: "SHOPPER", "PICKER" o "DRIVER". */
    @NonNull
    public String modalidad = "SHOPPER";

    /** Fecha ISO yyyy-MM-dd desde la que esta version es vigente (inclusive). */
    @NonNull
    @androidx.room.ColumnInfo(name = "fecha_desde")
    public String fechaDesde = "2025-01-01";

    /** Valor para SKU 1-10 */ public int t1;
    /** Valor para SKU 11-30 */ public int t2;
    /** Valor para SKU 31-50 */ public int t3;
    /** Valor para SKU 51-70 */ public int t4;
    /** Valor para SKU 71-90 */ public int t5;
    /** Valor para SKU 91-125 */ public int t6;
    /** Valor para SKU 126-150 */ public int t7;
    /** Valor para SKU >150 */ public int t8;

    public TramosVersion() {
        // requerido por Room
    }

    public TramosVersion(@NonNull String modalidad, @NonNull String fechaDesde,
                         int t1, int t2, int t3, int t4,
                         int t5, int t6, int t7, int t8) {
        this.modalidad = modalidad;
        this.fechaDesde = fechaDesde;
        this.t1 = t1; this.t2 = t2; this.t3 = t3; this.t4 = t4;
        this.t5 = t5; this.t6 = t6; this.t7 = t7; this.t8 = t8;
    }

    /**
     * Devuelve el valor del tramo i (1..8). Centralizado aca para que el
     * codigo cliente no tenga que hacer switch sobre i.
     */
    public int valorTramo(int i) {
        switch (i) {
            case 1: return t1;
            case 2: return t2;
            case 3: return t3;
            case 4: return t4;
            case 5: return t5;
            case 6: return t6;
            case 7: return t7;
            case 8: return t8;
            default: throw new IllegalArgumentException("Tramo invalido: " + i);
        }
    }
}
