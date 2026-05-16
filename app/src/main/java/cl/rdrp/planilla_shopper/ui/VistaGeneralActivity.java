package cl.rdrp.planilla_shopper.ui;

import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_KM;
import static cl.rdrp.planilla_shopper.util.Config.VALOR_UNIT_SKU;
import static cl.rdrp.planilla_shopper.util.Config.basePorSku;
import static cl.rdrp.planilla_shopper.util.Config.calcularBonoKm;


import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.util.Config;

public class VistaGeneralActivity extends AppCompatActivity {

    private Button  btnDesde, btnHasta, btnFiltrar;
    private EditText etBuscar;
    private TextView tvResumenRango;
    private RecyclerView rvDias;

    private Map<String, Long> bonosPorFecha = new HashMap<>();

    private String desdeSel, hastaSel;
    private VistaGeneralAdapter adapter;

    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es","CL"));
    private List<Registro> baseRango = new ArrayList<>(); // cache del rango

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vista_general);
        setTitle("Vista general");

        btnDesde = findViewById(R.id.btnDesde);
        btnHasta = findViewById(R.id.btnHasta);
        btnFiltrar = findViewById(R.id.btnFiltrar);
        etBuscar = findViewById(R.id.etBuscar);
        tvResumenRango = findViewById(R.id.tvResumenRango);
        rvDias = findViewById(R.id.rvDias);

        rvDias.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VistaGeneralAdapter();
        rvDias.setAdapter(adapter);

        setRangoMesActual();
        cargarRango(desdeSel, hastaSel);


        btnDesde.setOnClickListener(v -> pickFecha(true));
        btnHasta.setOnClickListener(v -> pickFecha(false));

        btnFiltrar.setOnClickListener(v -> {
            if (desdeSel.compareTo(hastaSel) > 0) {
                Toast.makeText(this, "Desde no puede ser mayor que Hasta", Toast.LENGTH_SHORT).show();
                return;
            }
            cargarRango(desdeSel, hastaSel);
        });



        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                aplicarBusqueda(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
    private void cargarRango(String desde, String hasta) {
        Executors.newSingleThreadExecutor().execute(() -> {

            baseRango = AppDatabase.get(this)
                    .registroDao()
                    .listByRangoFechas(desde, hasta);

            bonosPorFecha.clear();
            List<String> fechas = AppDatabase.get(this)
                    .bonoDao()
                    .listFechasConBonos(desde, hasta);

            for (String f : fechas) {
                long total = AppDatabase.get(this)
                        .bonoDao()
                        .sumBonosFecha(f);
                bonosPorFecha.put(f, total);
            }

            runOnUiThread(() ->
                    aplicarBusqueda(etBuscar.getText().toString().trim())
            );
        });
    }


    private void aplicarBusqueda(String q) {
        // 1) filtra por SG
        List<Registro> filtrado = new ArrayList<>();
        if (q == null || q.isEmpty()) {
            filtrado.addAll(baseRango);
        } else {
            String qLower = q.toLowerCase(Locale.ROOT);
            for (Registro r : baseRango) {
                // SG es numérico, buscarlo como string
                if (String.valueOf(r.sg).contains(qLower)) {
                    filtrado.add(r);
                }
            }
        }

        // 2) agrupar por fecha
        Map<String, List<Registro>> map = new LinkedHashMap<>();
        for (Registro r : filtrado) {
            if (!map.containsKey(r.fecha)) map.put(r.fecha, new ArrayList<>());
            map.get(r.fecha).add(r);
        }

        // 3) construir items por día (bruto = pedidos + bonoKm + bonosDB)
        List<DiaItem> dias = new ArrayList<>();
        long totalRango = 0;
        int pedidosRango = 0;

        for (String fecha : map.keySet()) {
            List<Registro> regs = map.get(fecha);
            if (regs == null) regs = new ArrayList<>();

            long totalDiaPedidos = 0;
            long totalDiaBonoKm = 0;

            for (Registro r : regs) {
                int skuQty = parseIntOnlyDigits(r.sku);
                int base   = basePorSku(skuQty);
                int sSku   = skuQty * VALOR_UNIT_SKU;
                long sKm   = Config.calcularTotalKm(r.km, r.fecha);

                totalDiaPedidos += base + sSku + sKm;

                // ✅ bono km domingo/lunes/martes
                totalDiaBonoKm += calcularBonoKm(r.km, r.fecha);
            }

            // ✅ bonos manuales (tabla bonos) del día
            long bonosDbDia = bonosPorFecha.containsKey(fecha)
                    ? bonosPorFecha.get(fecha)
                    : 0;


            // ✅ total bruto completo del día
            long totalDia = totalDiaPedidos + totalDiaBonoKm + bonosDbDia;

            pedidosRango += regs.size();
            totalRango += totalDia;

            dias.add(new DiaItem(fecha, regs, regs.size(), totalDia));
        }

        tvResumenRango.setText("Pedidos: " + pedidosRango + "  |  Bruto: " + clp.format(totalRango));
        adapter.setData(dias);
    }


    private void pickFecha(boolean esDesde) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (picker, y, m, d) -> {
            String f = String.format(Locale.US, "%04d-%02d-%02d", y, (m + 1), d);
            if (esDesde) {
                desdeSel = f;
                btnDesde.setText(f);
            } else {
                hastaSel = f;
                btnHasta.setText(f);
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setRangoMesActual() {
        Calendar c = Calendar.getInstance();
        desdeSel = String.format(Locale.US, "%04d-%02d-01", c.get(Calendar.YEAR), (c.get(Calendar.MONTH) + 1));
        Calendar end = (Calendar) c.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        hastaSel = String.format(Locale.US, "%04d-%02d-%02d", end.get(Calendar.YEAR), (end.get(Calendar.MONTH) + 1), end.get(Calendar.DAY_OF_MONTH));

        btnDesde.setText(desdeSel);
        btnHasta.setText(hastaSel);
    }

    private void setRangoSemanaActual() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.setFirstDayOfWeek(Calendar.MONDAY);

        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int deltaToMonday = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        cal.add(Calendar.DAY_OF_MONTH, deltaToMonday);

        Calendar monday = (Calendar) cal.clone();
        Calendar sunday = (Calendar) cal.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);

        desdeSel = String.format(Locale.US, "%04d-%02d-%02d",
                monday.get(Calendar.YEAR), monday.get(Calendar.MONTH) + 1, monday.get(Calendar.DAY_OF_MONTH));

        hastaSel = String.format(Locale.US, "%04d-%02d-%02d",
                sunday.get(Calendar.YEAR), sunday.get(Calendar.MONTH) + 1, sunday.get(Calendar.DAY_OF_MONTH));

        btnDesde.setText(desdeSel);
        btnHasta.setText(hastaSel);
    }

    private static int parseIntOnlyDigits(String s) {
        if (s == null) return 0;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
