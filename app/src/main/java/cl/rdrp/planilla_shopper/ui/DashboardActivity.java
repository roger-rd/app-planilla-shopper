package cl.rdrp.planilla_shopper.ui;

import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_KM;
import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_SKU;
import static cl.rdrp.planilla_shopper.util.Config.basePorSku;   // 👈 usamos SIEMPRE el del Config

import android.app.DatePickerDialog;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.BonoDao;
import cl.rdrp.planilla_shopper.data.BonoExtra;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.RegistroDao;
import cl.rdrp.planilla_shopper.databinding.ActivityDashboardBinding;
import cl.rdrp.planilla_shopper.util.Config;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding vb;
    private RegistroDao dao;
    private BonoDao bonoDao;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("es","CL"));
    private final Executor exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dao = AppDatabase.get(this).registroDao();
        bonoDao = AppDatabase.get(this).bonoDao();

        // Fecha inicial
        TextInputEditText et = vb.etFechaDash;
        et.setFocusable(false);
        et.setOnClickListener(v -> mostrarDatePicker());
        if (isEmpty(et.getText())) {
            et.setText(hoyISO()); // "yyyy-MM-dd"
        }

        // Cargar resumen por la fecha mostrada
        cargarResumen(et.getText().toString().trim());
    }

    // ===================== UI & Eventos =====================

    private void mostrarDatePicker() {
        Calendar cal = Calendar.getInstance();
        String actual = safeText(vb.etFechaDash.getText());
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            if (!actual.isEmpty()) cal.setTime(f.parse(actual));
        } catch (Exception ignored) {}

        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (picker, yy, mm, dd) -> {
            String sel = String.format(Locale.US, "%04d-%02d-%02d", yy, (mm + 1), dd);
            vb.etFechaDash.setText(sel);
            cargarResumen(sel);
        }, y, m, d).show();
    }

    // ===================== Carga y cálculo =====================

    private static String toLegacy(String iso) {
        if (iso == null) return "";
        iso = iso.trim();
        if (iso.length() < 10) return iso;
        try {
            String y = iso.substring(0,4);
            String m = iso.substring(5,7);
            String d = iso.substring(8,10);
            return d + "/" + m + "/" + y;
        } catch (Exception e) {
            return iso;
        }
    }

    private void cargarResumen(String fechaISO) {
        final String iso = (fechaISO == null) ? "" : fechaISO.trim();
        final String legacy = toLegacy(iso);

        exec.execute(() -> {
            // Registros del día
            List<Registro> items = dao.listByFechaCompat(iso, legacy);

            // Acumuladores de dinero
            long totalDia      = 0L;
            long totalBonosKm  = 0L;  // bonos automáticos por km
            long totalBonosExt = 0L;  // bonos extras (popup)

            // Métricas no monetarias
            int    pedidos = items.size();
            double totalKm = 0.0;

            for (Registro r : items) {
                Integer skuQtyI = parseIntOnlyDigits(r.sku);
                int skuQty = (skuQtyI == null ? 0 : skuQtyI);

                int  base   = basePorSku(skuQty);               // 👈 ahora viene de Config
                int  sSku   = skuQty * VALOR_UNIT_SKU;
                long sKm    = Config.calcularTotalKm(r.km, r.fecha); //modif cuando pase contingencia

                // bono km según fecha (solo domingo, lunes, martes)
                double bonoKm = Config.calcularBonoKm(r.km, r.fecha);
                double total = (long) base + sSku + sKm + bonoKm; // mismo total que en las cards

                totalDia     += total;
                totalKm      += r.km;
                totalBonosKm += bonoKm;
            }

            // Bonos extras guardados con el popup "+ bono"
            List<BonoExtra> bonos = bonoDao.listByFecha(iso);
            for (BonoExtra b : bonos) {
                totalBonosExt += b.monto;
            }

            // sumar bonos extra al total del día
            totalDia += totalBonosExt;

            // Costos y comisiones (según tu Config)
            long costoCombustible = Math.round(
                    (totalKm / Config.RENDIMIENTO_KM_POR_LITRO) * Config.PRECIO_LITRO
            );
            long comision = Math.round(totalDia * Config.COMISION_PORC);
            long liquido  = totalDia - comision - costoCombustible;

            // Copias finales para la UI
            final int    pedidosF          = pedidos;
            final double totalKmF          = totalKm;
            final long   totalDiaF         = totalDia;
            final long   costoCombustibleF = costoCombustible;
            final long   comisionF         = comision;
            final long   liquidoF          = liquido;
            final long   totalBonosKmF     = totalBonosKm;
            final long   totalBonosExtF    = totalBonosExt;

            runOnUiThread(() -> {
                money.setMaximumFractionDigits(0);

                vb.tvPedidosDia.setText("Pedidos del día: " + pedidosF);

                // Texto del total, mostrando también los bonos
                String txtTotal = "Total del día: " + money.format(totalDiaF);
                long totalBonos = totalBonosKmF + totalBonosExtF;
                if (totalBonos > 0) {
                    txtTotal += " (bonos: " + money.format(totalBonos) + ")";
                }
                vb.tvTotalDia.setText(txtTotal);

                String kmTxt = String.format(Locale.US, "%.2f", totalKmF);
                vb.tvCombustible.setText(
                        "Combustible: " + kmTxt + " km (" + money.format(costoCombustibleF) + ")"
                );

                double porcentaje = Config.COMISION_PORC * 100.0;

                String texto = String.format(
                        Locale.US,
                        "Impuesto %.2f%%: %s",
                        porcentaje,
                        money.format(comisionF)
                );

                vb.tvComision.setText(texto);

                vb.tvLiquido.setText("Líquido: " + money.format(liquidoF));

                drawPie(liquidoF, comisionF, costoCombustibleF);
            });
        });
    }

    // ===================== Gráfico =====================

    private void drawPie(long liquido, long comision, long combustible) {
        PieChart chart = vb.pieChart;

        if (liquido <= 0 && comision <= 0 && combustible <= 0) {
            chart.clear();
            chart.getDescription().setEnabled(false);
            chart.setCenterText("Sin datos");
            chart.setCenterTextSize(14f);
            chart.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        if (liquido > 0)     entries.add(new PieEntry((float) liquido,     "Líquido"));
        if (comision > 0)    entries.add(new PieEntry((float) comision,    "Comisión"));
        if (combustible > 0) entries.add(new PieEntry((float) combustible, "Combustible"));

        PieDataSet ds = new PieDataSet(entries, "");
        ds.setSliceSpace(2f);
        ds.setValueTextSize(12f);
        ds.setValueTextColor(android.graphics.Color.WHITE);

        ds.setColors(
                androidx.core.content.ContextCompat.getColor(this, R.color.pie_liquido),
                androidx.core.content.ContextCompat.getColor(this, R.color.pie_comision),
                androidx.core.content.ContextCompat.getColor(this, R.color.pie_combustible)
        );

        // calcular total para porcentajes
        double totalAll = 0;
        for (PieEntry e : entries) totalAll += e.getValue();
        final double totalAllF = totalAll;

        final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es","CL"));
        clp.setMaximumFractionDigits(0);

        ds.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float v) {
                double val = (double) v;
                double pct = (totalAllF <= 0) ? 0 : (val / totalAllF * 100.0);
                return clp.format(val) + String.format(Locale.US, " (%.0f%%)", pct);
            }
        });

        PieData data = new PieData(ds);
        chart.setData(data);

        chart.setUsePercentValues(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawEntryLabels(true);
        chart.setEntryLabelTextSize(11f);
        chart.setEntryLabelColor(android.graphics.Color.BLACK);

        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.setHoleColor(android.graphics.Color.TRANSPARENT);
        chart.setCenterText("Líquido\n" + clp.format((double) liquido));
        chart.setCenterTextSize(14f);

        com.github.mikephil.charting.components.Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(12f);

        chart.setRotationEnabled(false);
        chart.animateY(700);
        chart.highlightValues(null);
        chart.invalidate();
    }

    // ===================== Helpers =====================

    private static String hoyISO() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    private static boolean isEmpty(CharSequence cs){
        return cs == null || cs.toString().trim().isEmpty();
    }

    private static String safeText(CharSequence cs){
        return cs == null ? "" : cs.toString();
    }

    /** Extrae solo dígitos de un String y los parsea a int */
    private static Integer parseIntOnlyDigits(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
