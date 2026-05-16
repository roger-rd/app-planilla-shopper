package cl.rdrp.planilla_shopper.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;

public class VistaGeneralAdapter extends RecyclerView.Adapter<VistaGeneralAdapter.VH> {

    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es","CL"));
    private final List<DiaItem> data = new ArrayList<>();

    public void setData(List<DiaItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dia_resumen, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DiaItem d = data.get(pos);

        h.tvFecha.setText(d.fecha);
        h.tvPedidos.setText("Pedidos: " + d.pedidos);
        h.tvTotal.setText(clp.format(d.totalBruto));

        // detalle
        h.rvDetalle.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        PedidoDetalleAdapter detAdapter = new PedidoDetalleAdapter();
        h.rvDetalle.setAdapter(detAdapter);
        detAdapter.setData(d.registros);

        h.rvDetalle.setVisibility(d.expandido ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            d.expandido = !d.expandido;
            notifyItemChanged(pos);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvFecha, tvPedidos, tvTotal;
        RecyclerView rvDetalle;
        VH(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tvFecha);
            tvPedidos = itemView.findViewById(R.id.tvPedidos);
            tvTotal = itemView.findViewById(R.id.tvTotal);
            rvDetalle = itemView.findViewById(R.id.rvDetalle);
        }
    }
}
