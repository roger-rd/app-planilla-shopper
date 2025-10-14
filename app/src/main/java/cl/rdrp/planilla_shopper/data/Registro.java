package cl.rdrp.planilla_shopper.data;


import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "registro",
        indices = {@Index(value = {"fecha","local"}, unique = true)}
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

    public Registro( String fecha, String local, String sku, double km, long sg, int ventana, int cant) {
        this.fecha = fecha;
        this.local = local;
        this.sku = sku;
        this.km = km;
        this.sg = sg;
        this.ventana = ventana;
        this.cant = cant;
    }
}
