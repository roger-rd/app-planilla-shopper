package cl.rdrp.planilla_shopper.ui;

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

import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.RegistroDao;
import cl.rdrp.planilla_shopper.databinding.ActivityDashboardBinding;
import cl.rdrp.planilla_shopper.util.Config;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding vb;
    private RegistroDao dao;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("es","CL"));
    private final Executor exec = Executors.newSingleThreadExecutor();

    // --- Constantes de negocio (mismas que el Adapter) ---
    private static final int    PEDIDO_FIJO     = 1600;   // $
    private static final int    VALOR_UNIT_SKU  = 60;     // $ por SKU
    private static final double VALOR_UNIT_KM   = 232.0;  // $ por KM

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        dao = AppDatabase.get(this).registroDao();

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
        final String f = (fechaISO == null) ? "" : fechaISO.trim();
        final String iso = (fechaISO == null) ? "" : fechaISO.trim();
        final String legacy = toLegacy(iso);

        exec.execute(() -> {
            // Traer registros del día
            List<Registro> items = dao.listByFechaCompat(iso,legacy);

            // Acumuladores de dinero
            long totalDia     = 0L;
            int  totalBase$   = 0;
            int  totalPedido$ = 0;
            int  totalSku$    = 0;
            long totalKm$     = 0L;

            // Métricas no monetarias
            int    pedidos     = items.size(); // cantidad de registros
            int    totalSkuQty = 0;
            double totalKm     = 0.0;          // km reales (double)

            for (Registro r : items) {
                Integer skuQtyI = parseIntOnlyDigits(r.sku);
                int skuQty = (skuQtyI == null ? 0 : skuQtyI);

                int  base   = basePorSku(skuQty);
                int  pedido = PEDIDO_FIJO;
                int  sSku   = skuQty * VALOR_UNIT_SKU;
                long sKm    = Math.round(r.km * VALOR_UNIT_KM); // KM decimal → pesos

                long total = (long) base + pedido + sSku + sKm;

                // Acumular
                totalBase$   += base;
                totalPedido$ += pedido;
                totalSku$    += sSku;
                totalKm$     += sKm;
                totalDia     += total;

                totalSkuQty  += skuQty;
                totalKm      += r.km;
            }

            // Costos y comisiones (según tu Config)
            long costoCombustible = Math.round((totalKm / Config.RENDIMIENTO_KM_POR_LITRO) * Config.PRECIO_LITRO);
            long comision         = Math.round(totalDia * Config.COMISION_PORC);
            long liquido          = totalDia - comision - costoCombustible;

            // Copias finales para la UI
            final int    pedidosF          = pedidos;
            final long   totalDiaF         = totalDia;
            final double totalKmF          = totalKm;
            final long   costoCombustibleF = costoCombustible;
            final long   comisionF         = comision;
            final long   liquidoF          = liquido;

            runOnUiThread(() -> {
                money.setMaximumFractionDigits(0);

                vb.tvPedidosDia.setText("Pedidos del día: " + pedidosF);
                vb.tvTotalDia.setText("Total del día: " + money.format(totalDiaF));

                String kmTxt = String.format(Locale.US, "%.2f", totalKmF);
                vb.tvCombustible.setText("Combustible: " + kmTxt + " km (" + money.format(costoCombustibleF) + ")");

                vb.tvComision.setText(String.format(Locale.US, "%.1f%%: %s",
                        (Config.COMISION_PORC * 100.0), money.format(comisionF)));

                vb.tvLiquido.setText("Líquido: " + money.format(liquidoF));

                drawPie(liquidoF, comisionF, costoCombustibleF);
            });
        });
    }

    // ===================== Gráfico =====================

    private void drawPie(long liquido, long comision, long combustible){
        PieChart chart = vb.pieChart;
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry((float) liquido,      "Líquido"));
        entries.add(new PieEntry((float) comision,     "Comisión"));
        entries.add(new PieEntry((float) combustible,  "Combustible"));

        PieDataSet ds = new PieDataSet(entries, "");
        ds.setSliceSpace(2f);

        chart.setData(new PieData(ds));
        chart.getDescription().setEnabled(false);
        chart.setUsePercentValues(false);
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

    /** Extrae solo dígitos de un String y los parsea a int */
    private static Integer parseIntOnlyDigits(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
