package cl.rdrp.planilla_shopper.ui;

import static cl.rdrp.planilla_shopper.util.Config.basePorSku;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Parsers;

public class RegistroAdapter extends RecyclerView.Adapter<RegistroAdapter.VH> {

    public interface OnEdit {
        void onUpdate(Registro r);
        void onDelete(Registro r);
    }

    private final List<Registro> data = new ArrayList<>();
    private final OnEdit onEdit;

    // formateador CLP
    private final NumberFormat money =
            NumberFormat.getCurrencyInstance(new java.util.Locale("es","CL"));

    public RegistroAdapter(OnEdit onEdit) {
        this.onEdit = onEdit;
        money.setMaximumFractionDigits(0); // CLP sin decimales
    }

    public void submit(List<Registro> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_registro, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Registro r = data.get(i);

        // SKU en DB es String → obtener cantidad (solo dígitos)
        Integer skuQtyI = Parsers.parseIntOrNull(r.sku);
        int skuQty = (skuQtyI == null ? 0 : skuQtyI);

        // N4: parametros segun la modalidad del registro (no la activa)
        Modalidad m = Modalidad.fromName(r.modalidad);

        // Cálculos por fila
        int base   = basePorSku(skuQty);
        int sSku   = skuQty * Config.getValorUnitSku(m);
        long sKm   = Config.calcularTotalKm(r.km, r.fecha); // KM con decimales → pesos // modif cuando pase contingencia

        // 💰 bono por km según fecha, usando bono de la modalidad del registro
        double bonoKm = Config.calcularBonoKm(r.km, r.fecha, m);

        // total ya incluye el bono
        double total = (long) base + sSku + sKm + bonoKm;

        // ===== Bind a la UI =====
        h.titulo.setText("SG: " + r.sg);

        h.detalle.setText(
                "SKU: " + skuQty +
                        "  KM: " + String.format(java.util.Locale.US, "%.2f", r.km) +
                        "  Ventana: " + r.ventana +
                        "\nBase " + money.format(base) +
                        "  SKU " + money.format(sSku) +
                        "  KM " + money.format(sKm) +
                        "  CANT: " + r.cant
        );

        // Bono KM visible solo si aplica
        if (bonoKm > 0) {
            h.tvBonoKm.setVisibility(View.VISIBLE);
            h.tvBonoKm.setText("Bono KM: " + money.format(bonoKm));
        } else {
            h.tvBonoKm.setVisibility(View.GONE);
        }

        // N4: chip de modalidad solo visible si NO es Shopper
        if (h.tvChipModalidad != null) {
            if (m != Modalidad.SHOPPER) {
                h.tvChipModalidad.setText(m.getLabel().toUpperCase(java.util.Locale.ROOT));
                h.tvChipModalidad.setVisibility(View.VISIBLE);
            } else {
                h.tvChipModalidad.setVisibility(View.GONE);
            }
        }

        // Monto final
        h.monto.setText(money.format(total));

        // Long press → editar / eliminar
        h.itemView.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(v.getContext())
                    .setTitle("Acciones")
                    .setItems(new CharSequence[]{"Editar", "Eliminar"}, (d, which) -> {
                        if (which == 0) onEdit.onUpdate(r);
                        else onEdit.onDelete(r);
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView titulo, detalle, tvBonoKm, monto, tvChipModalidad;
        VH(@NonNull View v) {
            super(v);
            titulo   = v.findViewById(R.id.tvTitulo);
            detalle  = v.findViewById(R.id.tvDetalle);
            tvBonoKm = v.findViewById(R.id.tvBonoKm);
            monto    = v.findViewById(R.id.tvMonto);
            tvChipModalidad = v.findViewById(R.id.tvChipModalidad);
        }
    }
}
