package cl.rdrp.planilla_shopper.ui;

import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_KM;
import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_SKU;
import static cl.rdrp.planilla_shopper.util.Config.basePorSku;

import android.app.DatePickerDialog;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.RegistroDao;
import cl.rdrp.planilla_shopper.databinding.ActivityMonthlySummaryBinding;
import cl.rdrp.planilla_shopper.data.BonoDao;

public class MonthlySummaryActivity extends AppCompatActivity {

    private ActivityMonthlySummaryBinding vb;
    private final Executor exec = Executors.newSingleThreadExecutor();
    private RegistroDao dao;
    private BonoDao bonoDao;

    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));


    // Descuentos
    private static final double DESCUENTO_GASOLINA = 0.10; // 10%
    private static final double DESCUENTO_IMPUESTO = 0.15; // 10%

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMonthlySummaryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());


        setTitle("Resumen mensual");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        dao = AppDatabase.get(this).registroDao();
        bonoDao = AppDatabase.get(this).bonoDao();

        Calendar c = Calendar.getInstance();
        vb.tvMes.setText(formatoMes(c));

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
        String desde = String.format(Locale.US, "%04d-%02d-01",
                c.get(Calendar.YEAR), (c.get(Calendar.MONTH) + 1));
        Calendar end = (Calendar) c.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        String hasta = String.format(Locale.US, "%04d-%02d-%02d",
                end.get(Calendar.YEAR), (end.get(Calendar.MONTH) + 1), end.get(Calendar.DAY_OF_MONTH));

        exec.execute(() -> {
            List<Registro> items = dao.listByRangoFechas(desde, hasta);

            // ✅ BONOS DEL MES (desde tabla bonos)
            long bonos = bonoDao.sumBonosRango(desde, hasta);

            int pedidosMes = items.size();
            Set<String> dias = new HashSet<>();
            int totalSku = 0;
            double totalKm = 0.0;
            long totalMes$ = 0; // total de registros (sin bonos)

            for (Registro r : items) {
                dias.add(r.fecha);

                int skuQty = parseIntOnlyDigits(r.sku);
                totalSku += skuQty;
                totalKm += r.km;

                int base = basePorSku(skuQty);
                int sSku = skuQty * VALOR_UNIT_SKU;
                long sKm = Math.round(r.km * VALOR_UNIT_KM);

                totalMes$ += base + sSku + sKm;
            }

            // ✅ total final incluyendo bonos (una sola vez, fuera del for)
            long totalConBonos$ = totalMes$ + bonos;

            int diasTrabajados = Math.max(1, dias.size());
            int promPedidosDia = Math.round(pedidosMes / (float) diasTrabajados);
            int promSkuPorPed = pedidosMes == 0 ? 0 : Math.round(totalSku / (float) pedidosMes);
            long promedioDiario$ = Math.round(totalConBonos$ / (double) diasTrabajados);

            // ✅ Descuentos y líquido en base al total con bonos
            double descGasolina = totalConBonos$ * DESCUENTO_GASOLINA;
            double descImpuesto = totalConBonos$ * DESCUENTO_IMPUESTO;
            double liquido = totalConBonos$ - descGasolina - descImpuesto;

            // Congelar valores para el UI thread
            final int pedidosMesF = pedidosMes;
            final int diasTrabF = diasTrabajados;
            final int promPedF = promPedidosDia;
            final int totalSkuF = totalSku;
            final int promSkuF = promSkuPorPed;
            final double totalKmF = totalKm;
            final long bonosF = bonos;

            final String sPromDia = clp.format(promedioDiario$);
            final String sTotalMes = clp.format(totalConBonos$);
            final String sDescGas = "-" + clp.format(descGasolina);
            final String sDescImp = "-" + clp.format(descImpuesto);
            final String sLiquido = clp.format(liquido);

            runOnUiThread(() -> {
                vb.tvPedidosMes.setText(String.valueOf(pedidosMesF));
                vb.tvDiasTrab.setText(String.valueOf(diasTrabF));
                vb.tvPromPedidos.setText(String.valueOf(promPedF));
                vb.tvSkuMes.setText(String.valueOf(totalSkuF));
                vb.tvPromSku.setText(String.valueOf(promSkuF));
                vb.tvKmMes.setText(String.format(Locale.US, "%.2f", totalKmF));

                // ✅ Aquí muestra bonos (te recomiendo con formato CLP)
                vb.tvBonos.setText(clp.format(bonosF));

                vb.tvPromedioDiario.setText(sPromDia);
                vb.tvTotalMesPesos.setText(sTotalMes);
                vb.tvGasolina.setText(sDescGas);
                vb.tvImpuesto.setText(sDescImp);
                vb.tvLiquido.setText(sLiquido);
            });
        });
    }



    private static String formatoMes(Calendar c) {
        return String.format(Locale.US, "%04d-%02d",
                c.get(Calendar.YEAR), (c.get(Calendar.MONTH) + 1));
    }


    private static int parseIntOnlyDigits(String s) {
        if (s == null) return 0;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }


    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

}
