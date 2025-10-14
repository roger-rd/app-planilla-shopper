// src/main/java/.../ui/MonthlySummaryActivity.java
package cl.rdrp.planilla_shopper.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.RegistroDao;
import cl.rdrp.planilla_shopper.databinding.ActivityMonthlySummaryBinding;
import cl.rdrp.planilla_shopper.util.Config;

public class MonthlySummaryActivity extends AppCompatActivity {

    private ActivityMonthlySummaryBinding vb;
    private final Executor exec = Executors.newSingleThreadExecutor();
    private RegistroDao dao;

    private static final int    PEDIDO_FIJO     = 1600;
    private static final int    VALOR_UNIT_SKU  = 60;
    private static final double VALOR_UNIT_KM   = 232.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMonthlySummaryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        setTitle("Resumen mensual");
        dao = AppDatabase.get(this).registroDao();

        // por defecto: mes actual
        Calendar c = Calendar.getInstance();
        vb.tvMes.setText(formatoMes(c)); // "2025-10"

        vb.btnElegirMes.setOnClickListener(v -> elegirMes());
        cargarMes(c);
    }

    private void elegirMes() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (picker, y, m, d) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(Calendar.YEAR, y);
            sel.set(Calendar.MONTH, m);
            sel.set(Calendar.DAY_OF_MONTH, 1);
            vb.tvMes.setText(formatoMes(sel));
            cargarMes(sel);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void cargarMes(Calendar c) {
        // rango [primer día, último día] en ISO
        String desde = String.format(Locale.US, "%04d-%02d-01",
                c.get(Calendar.YEAR), (c.get(Calendar.MONTH)+1));
        Calendar end = (Calendar) c.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        String hasta = String.format(Locale.US, "%04d-%02d-%02d",
                end.get(Calendar.YEAR), (end.get(Calendar.MONTH)+1), end.get(Calendar.DAY_OF_MONTH));

        exec.execute(() -> {
            List<Registro> items = dao.listByRangoFechas(desde, hasta);

            int pedidosMes = items.size();
            Set<String> dias = new HashSet<>();
            int totalSku = 0;
            double totalKm = 0.0;
            long totalMes$ = 0;

            for (Registro r : items) {
                dias.add(r.fecha);

                int skuQty = parseIntOnlyDigits(r.sku);
                totalSku += skuQty;
                totalKm  += r.km;

                int base   = basePorSku(skuQty);
                int pedido = PEDIDO_FIJO;
                int sSku   = skuQty * VALOR_UNIT_SKU;
                long sKm   = Math.round(r.km * VALOR_UNIT_KM);

                totalMes$ += base + pedido + sSku + sKm;
            }

            int diasTrabajados = Math.max(1, dias.size()); // evitar div/0
            int promPedidosDia = Math.round(pedidosMes / (float) diasTrabajados);
            int promSkuPorPed  = pedidosMes == 0 ? 0 : Math.round(totalSku / (float) pedidosMes);

            long bonos = 0; // si luego agregas reglas, las usamos aquí
            long promedioDiario$ = Math.round(totalMes$ / (double) diasTrabajados);

            final int pedidosMesF = pedidosMes;
            final int diasTrabF   = diasTrabajados;
            final int promPedF    = promPedidosDia;
            final int totalSkuF   = totalSku;
            final int promSkuF    = promSkuPorPed;
            final double totalKmF = totalKm;
            final long bonosF     = bonos;
            final long promDiaF   = promedioDiario$;

            runOnUiThread(() -> {
                vb.tvPedidosMes.setText(String.valueOf(pedidosMesF));
                vb.tvDiasTrab.setText(String.valueOf(diasTrabF));
                vb.tvPromPedidos.setText(String.valueOf(promPedF));
                vb.tvSkuMes.setText(String.valueOf(totalSkuF));
                vb.tvPromSku.setText(String.valueOf(promSkuF));
                vb.tvKmMes.setText(String.format(Locale.US, "%.2f", totalKmF));
                vb.tvBonos.setText(String.valueOf(bonosF));
                vb.tvPromedioDiario.setText(String.valueOf(promDiaF));
            });
        });
    }

    private static String formatoMes(Calendar c) {
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), (c.get(Calendar.MONTH)+1));
    }

    private static int basePorSku(int skuQty) {
        if (skuQty <= 0)   return 0;
        if (skuQty <= 10)  return 1000;
        if (skuQty <= 30)  return 1400;
        if (skuQty <= 50)  return 2400;
        if (skuQty <= 70)  return 3400;
        if (skuQty <= 90)  return 5400;
        if (skuQty <= 125) return 6400;
        if (skuQty <= 150) return 7400;
        return 8400;
    }

    private static int parseIntOnlyDigits(String s) {
        if (s == null) return 0;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
