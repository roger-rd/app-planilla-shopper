package cl.rdrp.planilla_shopper.data;

/**
 * Modalidad de trabajo del usuario.
 *
 * Se elige al primer arranque (popup de bienvenida) y se puede cambiar
 * despues desde el item del drawer. La persistencia vive en Prefs
 * (key "modalidad_actual").
 *
 * En N1 solo se persiste la preferencia: NO afecta campos visibles
 * ni calculos. Las fases siguientes van a usar esta enum para:
 *   - N2: ocultar/mostrar campos en Main segun modalidad
 *   - N3: parametros de calculo por modalidad
 *   - N4 (opcional): modalidad por registro
 */
public enum Modalidad {
    SHOPPER("Shopper", "Maneja todos los campos (SKU, KM, ventana, etc.)"),
    PICKER ("Picker",  "Solo hace el pedido. NO usa KM."),
    DRIVER ("Driver",  "Maneja KM y entrega. NO usa SKU.");

    private final String label;
    private final String descripcion;

    Modalidad(String label, String descripcion) {
        this.label = label;
        this.descripcion = descripcion;
    }

    public String getLabel()       { return label; }
    public String getDescripcion() { return descripcion; }

    /**
     * Convierte un name() guardado en Prefs a la enum, retornando SHOPPER
     * si el string es null o no coincide con ningun valor (defensivo
     * contra Prefs corruptas o cambios futuros de enum).
     */
    public static Modalidad fromName(String name) {
        if (name == null) return SHOPPER;
        try {
            return Modalidad.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SHOPPER;
        }
    }
}
