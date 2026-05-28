package cl.rdrp.planilla_shopper.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.Local;

/**
 * Adapter de la pantalla CRUD de locales (N5).
 *
 * Cada item muestra codigo, alias (si tiene) y un chip "ARCHIVADO" para los
 * locales con activo=false. Tap normal y long press se delegan al listener
 * para que la Activity decida la accion (editar / archivar / desarchivar).
 */
public class LocalesAdapter extends RecyclerView.Adapter<LocalesAdapter.VH> {

    public interface OnLocalAction {
        void onTap(Local l);
        void onLongPress(Local l);
    }

    private final List<Local> data = new ArrayList<>();
    private final OnLocalAction listener;

    public LocalesAdapter(OnLocalAction listener) {
        this.listener = listener;
    }

    public void submit(List<Local> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_local, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Local l = data.get(pos);

        h.tvCodigo.setText(l.codigo);

        if (l.alias != null && !l.alias.trim().isEmpty()) {
            h.tvAlias.setVisibility(View.VISIBLE);
            h.tvAlias.setText(l.alias);
        } else {
            h.tvAlias.setVisibility(View.GONE);
        }

        if (!l.activo) {
            h.tvEstadoChip.setText("ARCHIVADO");
            h.tvEstadoChip.setVisibility(View.VISIBLE);
            h.itemView.setAlpha(0.55f);
        } else {
            h.tvEstadoChip.setVisibility(View.GONE);
            h.itemView.setAlpha(1.0f);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTap(l);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(l);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCodigo, tvAlias, tvEstadoChip;
        VH(@NonNull View v) {
            super(v);
            tvCodigo     = v.findViewById(R.id.tvCodigo);
            tvAlias      = v.findViewById(R.id.tvAlias);
            tvEstadoChip = v.findViewById(R.id.tvEstadoChip);
        }
    }
}
