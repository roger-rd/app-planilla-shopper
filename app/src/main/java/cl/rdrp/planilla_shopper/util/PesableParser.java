package cl.rdrp.planilla_shopper.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Parser del formato de codigos PESABLE del sistema de Cornershop / Shopper.
 *
 * Formato observado (8 campos separados por ';'):
 *
 *   PESABLE;260530110732;210154001550;10154;1550;1;260530;17990
 *      |          |             |       |    |   |    |     |
 *      |          |             |       |    |   |    |     +-- precio (CLP)
 *      |          |             |       |    |   |    +-------- fecha YYMMDD
 *      |          |             |       |    |   +------------- cantidad
 *      |          |             |       |    +----------------- peso en gramos
 *      |          |             |       +---------------------- codigo de producto
 *      |          |             +------------------------------ codigo de barras (EAN pesable)
 *      |          +-------------------------------------------- timestamp YYMMDDHHmmss
 *      +------------------------------------------------------- header literal "PESABLE"
 *
 * Notas:
 *   - El parser es tolerante: parsea lo que puede y guarda los campos crudos.
 *     isValid() distingue un PESABLE bien formado del resto.
 *   - Los campos numericos se exponen tanto como string crudo como
 *     parseado (Integer) para que la UI elija que mostrar.
 *   - El "peso" en el formato esta en gramos. pesoKg() devuelve gramos/1000.
 */
public class PesableParser {

    /** Header literal que debe abrir el string para considerarse valido. */
    public static final String HEADER = "PESABLE";

    /** Cantidad de campos esperados separados por ';'. */
    public static final int CAMPOS_ESPERADOS = 8;

    // Campos crudos (siempre, aunque el parser no este valido)
    public final String raw;

    // Si bien formado, estos quedan rellenados.
    public final String header;       // "PESABLE"
    public final String timestamp;    // YYMMDDHHmmss
    public final String codBarras;    // EAN
    public final String codProducto;  // ej. 10154
    public final String pesoGramos;   // ej. 1550
    public final String cantidad;     // ej. 1
    public final String fecha;        // YYMMDD
    public final String precio;       // ej. 17990

    private PesableParser(String raw,
                          String header, String timestamp, String codBarras,
                          String codProducto, String pesoGramos, String cantidad,
                          String fecha, String precio) {
        this.raw = raw;
        this.header = header;
        this.timestamp = timestamp;
        this.codBarras = codBarras;
        this.codProducto = codProducto;
        this.pesoGramos = pesoGramos;
        this.cantidad = cantidad;
        this.fecha = fecha;
        this.precio = precio;
    }

    /**
     * Parsea un string. Nunca tira; si el formato es invalido devuelve un
     * objeto con isValid() = false donde solo `raw` esta poblado.
     */
    public static PesableParser parse(String input) {
        if (input == null) {
            return new PesableParser("", null, null, null, null, null, null, null, null);
        }
        String trimmed = input.trim();
        String[] partes = trimmed.split(";", -1);
        if (partes.length < CAMPOS_ESPERADOS || !HEADER.equals(partes[0])) {
            return new PesableParser(trimmed, null, null, null, null, null, null, null, null);
        }
        return new PesableParser(
                trimmed,
                partes[0], partes[1], partes[2], partes[3],
                partes[4], partes[5], partes[6], partes[7]
        );
    }

    /**
     * True si el string parseado tiene la estructura esperada de PESABLE
     * (header correcto + 8 campos). NO valida que los numericos sean
     * realmente numericos.
     */
    public boolean isValid() {
        return HEADER.equals(header);
    }

    /** Peso en kilos como double. 0.0 si no parseable. */
    public double pesoKg() {
        if (pesoGramos == null) return 0.0;
        try { return Integer.parseInt(pesoGramos) / 1000.0; }
        catch (NumberFormatException e) { return 0.0; }
    }

    /** Precio en CLP. 0 si no parseable. */
    public int precioCLP() {
        if (precio == null) return 0;
        try { return Integer.parseInt(precio); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Resumen amigable para mostrar bajo el QR: 3-4 lineas con los datos
     * mas relevantes (producto, peso, precio). Si no es valido, devuelve
     * un texto explicativo.
     */
    public String resumen() {
        if (!isValid()) {
            return "Formato no reconocido (se esperaba PESABLE;...;...).";
        }
        NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
        clp.setMaximumFractionDigits(0);

        StringBuilder sb = new StringBuilder();
        sb.append("Producto: ").append(safe(codProducto)).append("\n");
        sb.append("Peso: ").append(String.format(Locale.US, "%.3f kg", pesoKg())).append("\n");
        sb.append("Precio: ").append(clp.format(precioCLP())).append("\n");
        sb.append("Código de barras: ").append(safe(codBarras));
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "-" : s; }
}
