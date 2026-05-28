package cl.rdrp.planilla_shopper.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Local (sala) donde se hace el pedido. N5.
 *
 * Antes de N5, el campo "local" en Registro era texto libre (codigo numerico)
 * y el primer codigo ingresado se guardaba en Prefs como "local default".
 *
 * N5 convierte esto en un catalogo: cada local es una fila en la tabla
 * locales, con su codigo numerico (unico) y un alias opcional para mostrar
 * algo mas humano en las listas ("16234 — Las Condes").
 *
 * Los locales se pueden archivar (activo = false) en lugar de borrarse,
 * para preservar el historial de pedidos: un pedido viejo sigue mostrando
 * su codigo aunque el local ya no este activo.
 *
 * Migration 11->12 auto-importa los codigos distintos que ya hay en la
 * tabla registro, de modo que el usuario no tiene que recrear su catalogo
 * a mano la primera vez que actualiza.
 */
@Entity(
        tableName = "locales",
        indices = {@Index(value = {"codigo"}, unique = true)}
)
public class Local {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Codigo numerico del local. Sigue siendo lo que se guarda en
     * Registro.local para mantener compat con todos los pedidos historicos.
     * UNIQUE: no puede haber dos locales con el mismo codigo.
     */
    @NonNull
    public String codigo = "";

    /**
     * Alias amigable opcional. Si esta vacio, en la UI se muestra solo el
     * codigo. Si tiene valor, se muestra "codigo — alias".
     */
    public String alias;

    /**
     * Local activo significa que aparece en el dropdown / popup de seleccion
     * al momento de crear pedidos. Si se archiva (activo = false), el local
     * sigue en la base para que los pedidos historicos puedan mostrar su
     * codigo, pero no aparece como opcion seleccionable.
     */
    public boolean activo = true;

    public Local() {
        // constructor vacio requerido por Room
    }

    public Local(@NonNull String codigo, String alias) {
        this.codigo = codigo;
        this.alias = alias;
        this.activo = true;
    }

    /**
     * Como se muestra en listas: "16234" si no hay alias, "16234 — Las Condes"
     * si hay. Centralizado aca para que UI no tenga que armar el string.
     */
    public String display() {
        if (alias == null || alias.trim().isEmpty()) {
            return codigo;
        }
        return codigo + " — " + alias.trim();
    }
}
