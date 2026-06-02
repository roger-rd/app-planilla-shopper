package cl.rdrp.planilla_shopper.ui;

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
import cl.rdrp.planilla_shopper.data.TramosVersion;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Fechas;

/**
 * Adapter del CRUD de versiones de tramos.
 *
 * Cada item muestra:
 *   - "Desde: YYYY-MM-DD" como titulo
 *   - Chip "VIGENTE" si esta version es la vigente para hoy
 *   - Lista compacta de los 8 valores con sus rangos
 *
 * Tap -> editar version
 * Long press -> menu (Editar / Eliminar)
 *
 * No usa ListAdapter ni DiffUtil porque la lista es chica (en uso real
 * son pocas versiones por modalidad) y los rebuilds son raros.
 */
public class TramosAdapter extends RecyclerView.Adapter<TramosAdapter.VH> {

    public interface OnAction {
        void onTap(TramosVersion v);
        void onLongPress(TramosVersion v);
    }

    private final List<TramosVersion> data = new ArrayList<>();
    private final OnAction listener;
    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));

    public TramosAdapter(OnAction listener) {
        this.listener = listener;
        clp.setMaximumFractionDigits(0);
    }

    public void submit(List<TramosVersion> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tramos_version, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TramosVersion v = data.get(pos);

        h.tvFechaDesde.setText("Desde: " + v.fechaDesde);

        // Marcar vigente si su fecha_desde <= hoy y NINGUNA otra version
        // posterior la haya superseded. Como la lista viene DESC por
        // fecha_desde, la primera con fecha_desde <= hoy es la vigente.
        String hoy = Fechas.hoyISO();
        boolean esVigente = false;
        if (v.fechaDesde.compareTo(hoy) <= 0) {
            // Verificar que ninguna anterior en la lista (mas reciente) cumpla
            esVigente = true;
            for (int i = 0; i < pos; i++) {
                if (data.get(i).fechaDesde.compareTo(hoy) <= 0) {
                    esVigente = false;
                    break;
                }
            }
        }
        h.tvChipVigente.setVisibility(esVigente ? View.VISIBLE : View.GONE);

        // Lista de los 8 tramos con sus rangos
        int[] limites = Config.TRAMOS_LIMITES;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            String rango;
            if (i == 1) {
                rango = "1-" + limites[0];
            } else if (i <= limites.length) {
                rango = (limites[i - 2] + 1) + "-" + limites[i - 1];
            } else {
                rango = "más de " + limites[limites.length - 1];
            }
            if (i > 1) sb.append("\n");
            sb.append(rango).append(": ").append(clp.format(v.valorTramo(i)));
        }
        h.tvValores.setText(sb.toString());

        h.itemView.setOnClickListener(view -> {
            if (listener != null) listener.onTap(v);
        });
        h.itemView.setOnLongClickListener(view -> {
            if (listener != null) listener.onLongPress(v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvFechaDesde, tvChipVigente, tvValores;
        VH(@NonNull View v) {
            super(v);
            tvFechaDesde  = v.findViewById(R.id.tvFechaDesde);
            tvChipVigente = v.findViewById(R.id.tvChipVigente);
            tvValores     = v.findViewById(R.id.tvValores);
        }
    }
}
