package cl.rdrp.planilla_shopper.data;


import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "registro",
        indices = {@Index(value = {"fecha","local"}, unique = false)}
)
public class Registro {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String fecha;
    public String local;
    public String sku;
    public double km;
    public long sg;
    public int ventana;
    public int cant;

    // km conserva el total. calc_origen: 0 = histórico; 2 = snapshot 1.1.1; 3 = derecho al bono guardado.
    @ColumnInfo(name = "km_entrega")
    public Double km_entrega;

    @ColumnInfo(name = "km_retorno")
    public Double km_retorno;

    @ColumnInfo(name = "calc_origen", defaultValue = "0")
    public int calc_origen;

    @ColumnInfo(name = "tarifa_base", defaultValue = "0")
    public long tarifa_base;

    @ColumnInfo(name = "valor_unit_sku", defaultValue = "0")
    public long valor_unit_sku;

    @ColumnInfo(name = "precio_litro", defaultValue = "0")
    public long precio_litro;

    @ColumnInfo(name = "monto_base", defaultValue = "0")
    public long monto_base;

    @ColumnInfo(name = "monto_sku", defaultValue = "0")
    public long monto_sku;

    @ColumnInfo(name = "monto_km", defaultValue = "0")
    public long monto_km;

    @ColumnInfo(name = "monto_bono_km", defaultValue = "0")
    public long monto_bono_km;

    @ColumnInfo(name = "monto_bruto", defaultValue = "0")
    public long monto_bruto;

    @ColumnInfo(name = "valor_km", defaultValue = "0")
    public double valor_km;

    @ColumnInfo(name = "bono_por_km", defaultValue = "0")
    public double bono_por_km;

    @ColumnInfo(name = "comision_porc", defaultValue = "0")
    public double comision_porc;

    @ColumnInfo(name = "rendimiento_km_litro", defaultValue = "0")
    public double rendimiento_km_litro;


    /**
     * Modalidad de trabajo con la que se creó el registro (N4).
     *
     * Se guarda el name() del enum Modalidad (SHOPPER / PICKER / DRIVER).
     * Los calculos del pedido (valor SKU, comision, fuel, bono km) usan los
     * parametros configurados para esta modalidad, no la modalidad activa
     * actual. Eso permite que cuando el usuario cambia su modalidad activa,
     * los registros viejos sigan calculandose como cuando fueron creados.
     *
     * Migration 10->11: a todos los registros pre-N4 se les asigna 'SHOPPER'
     * (decision tomada en N1: comportamiento original = Shopper).
     *
     * NOT NULL por seguridad: nunca debe quedar null. Si llega null por
     * backup viejo, el codigo de lectura defiende usando
     * Modalidad.fromName(null) -> SHOPPER.
     */
    @NonNull
    public String modalidad = Modalidad.SHOPPER.name();

    public Registro( String fecha, String local, String sku, double km, long sg, int ventana, int cant) {
        this.fecha = fecha;
        this.local = local;
        this.sku = sku;
        this.km = km;
        this.sg = sg;
        this.ventana = ventana;
        this.cant = cant;
        // Por defecto SHOPPER. Quien cree el registro deberia setearlo
        // explicitamente con la modalidad activa al momento de crear.
        this.modalidad = Modalidad.SHOPPER.name();
    }
}
