package cl.rdrp.planilla_shopper.ui;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.NumberFormat;
import java.util.*;
import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.Registro;

public class RegistroAdapter extends RecyclerView.Adapter<RegistroAdapter.VH> {
    public interface OnEdit {
        void onUpdate(Registro r);
        void onDelete(Registro r);
    }

    // --- Constantes de negocio ---
    private static final int PEDIDO_FIJO   = 1600;  // $
    private static final int VALOR_UNIT_SKU = 60;   // $ por SKU
    private static final double VALOR_UNIT_KM = 232.0; // $ por KM (KM es double)

    private final List<Registro> data = new ArrayList<>();
    private final OnEdit onEdit;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new java.util.Locale("es","CL"));

    public RegistroAdapter(OnEdit onEdit) {
        this.onEdit = onEdit;
        money.setMaximumFractionDigits(0); // CLP sin decimales
    }

    public void submit(List<Registro> items) {
        data.clear(); data.addAll(items); notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_registro, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        Registro r = data.get(i);

        // SKU en DB es String → obtener cantidad (solo dígitos)
        Integer skuQtyI = parseIntOnlyDigits(r.sku);
        int skuQty = (skuQtyI == null ? 0 : skuQtyI);

        // Cálculos por fila
        int base   = basePorSku(skuQty);
        int pedido = PEDIDO_FIJO;
        int sSku   = skuQty * VALOR_UNIT_SKU;
        long sKm   = Math.round(r.km * VALOR_UNIT_KM); // KM con decimales → pesos

        long total = (long) base + pedido + sSku + sKm;

        // Bind
        h.titulo.setText("SG: " + r.sg);
        h.detalle.setText(
                "SKU: " + skuQty +
                        "  KM: " + String.format(java.util.Locale.US, "%.2f", r.km) +
                        "  Ventana: " + r.ventana +
                        "\nBase " + money.format(base) +
                        "  Pedido " + money.format(pedido) +
                        "  SKU " + money.format(sSku) +
                        "  KM " + money.format(sKm) +
                        "  CANT: " + r.cant
        );
        h.monto.setText(money.format(total));

        // Long press → editar/eliminar
        h.itemView.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(v.getContext())
                    .setTitle("Acciones")
                    .setItems(new CharSequence[]{"Editar", "Eliminar"}, (d, which) -> {
                        if (which == 0) onEdit.onUpdate(r); else onEdit.onDelete(r);
                    }).show();
            return true;
        });
    }

    @Override public int getItemCount() { return data.size(); }

    // === Helpers ===
    private static int basePorSku(int skuQty) {
        if (skuQty <= 0)   return 0;
        if (skuQty <= 10)  return 1000;
        if (skuQty <= 30)  return 1400;
        if (skuQty <= 50)  return 2400;
        if (skuQty <= 70)  return 3400;
        if (skuQty <= 90)  return 5400;
        if (skuQty <= 125) return 6400;
        if (skuQty <= 150) return 7400;
        return 8400; // >= 151
    }

    /** extrae solo dígitos de un String y los parsea a int */
    private static Integer parseIntOnlyDigits(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView titulo, detalle, monto;
        VH(@NonNull View v) {
            super(v);
            titulo = v.findViewById(R.id.tvTitulo);
            detalle = v.findViewById(R.id.tvDetalle);
            monto   = v.findViewById(R.id.tvMonto);
        }
    }
}
