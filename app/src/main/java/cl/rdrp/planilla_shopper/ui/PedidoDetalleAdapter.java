package cl.rdrp.planilla_shopper.ui;

import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_KM;
import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_SKU;
import static cl.rdrp.planilla_shopper.util.Config.basePorSku;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.util.Config;

public class PedidoDetalleAdapter extends RecyclerView.Adapter<PedidoDetalleAdapter.VH> {

    private final List<Registro> data = new ArrayList<>();
    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es","CL"));

    public void setData(List<Registro> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido_detalle, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Registro r = data.get(pos);

        int skuQty = parseIntOnlyDigits(r.sku);
        int base   = basePorSku(skuQty);
        int sSku   = skuQty * VALOR_UNIT_SKU;
        long sKm   = Config.calcularTotalKm(r.km, r.fecha); // modificar cuando pase contingencia
        long total = base + sSku + sKm;

        h.tvTitulo.setText("SG: " + r.sg);
        h.tvDetalle.setText(
                "SKU: " + skuQty +
                        "   KM: " + String.format(Locale.US, "%.2f", r.km) +
                        "   Ventana: " + r.ventana +
                        "   CANT: " + r.cant + "\n" +
                        "Base " + clp.format(base) +
                        "  SKU " + clp.format(sSku) +
                        "  KM " + clp.format(sKm)
        );
        h.tvTotal.setText(clp.format(total));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvDetalle, tvTotal;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTitulo);
            tvDetalle = itemView.findViewById(R.id.tvDetalle);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }

    private static int parseIntOnlyDigits(String s) {
        if (s == null) return 0;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
