package cl.rdrp.planilla_shopper.ui;

import java.util.List;
import cl.rdrp.planilla_shopper.data.Registro;

public class DiaItem {
    public String fecha;              // yyyy-MM-dd
    public int pedidos;               // cantidad de registros del día
    public long totalBruto;           // pesos
    public List<Registro> registros;  // detalle del día
    public boolean expandido = false;

    public DiaItem(String fecha, List<Registro> registros, int pedidos, long totalBruto) {
        this.fecha = fecha;
        this.registros = registros;
        this.pedidos = pedidos;
        this.totalBruto = totalBruto;
    }
}
