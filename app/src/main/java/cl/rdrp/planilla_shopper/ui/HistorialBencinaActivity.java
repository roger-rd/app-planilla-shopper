package cl.rdrp.planilla_shopper.ui;

import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Combustible;

public class HistorialBencinaActivity extends AppCompatActivity {

    private RecyclerView rv;
    private BencinaAdapter adapter;

    private Button btnDesde, btnHasta, btnFiltrar;
    private TextView tvTotales;

    private String desdeSel = null;
    private String hastaSel = null;

    private final NumberFormat clp = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial_bencina);

        rv = findViewById(R.id.rvBencina);
        rv.setLayoutManager(new LinearLayoutManager(this));

        btnDesde = findViewById(R.id.btnDesde);
        btnHasta = findViewById(R.id.btnHasta);
        btnFiltrar = findViewById(R.id.btnFiltrar);
        tvTotales = findViewById(R.id.tvTotales);

        adapter = new BencinaAdapter(new BencinaAdapter.OnBencinaAction() {
            @Override
            public void onEdit(Combustible c) {
                mostrarDialogoEditar(c);
            }

            @Override
            public void onDelete(Combustible c) {
                confirmarEliminar(c);
            }
        });
        rv.setAdapter(adapter);

        AppDatabase.ioExecutor.execute(() -> {
            AppDatabase.get(this).combustibleDao().normalizarFechasGuardadas();
            runOnUiThread(() -> {
                setRangoMesActual();
                cargarDatosRango(desdeSel, hastaSel);
            });
        });


        // ✅ Por defecto: mes actual
        setRangoMesActual();
        cargarDatosRango(desdeSel, hastaSel);

        btnDesde.setOnClickListener(v -> pickFecha(true));
        btnHasta.setOnClickListener(v -> pickFecha(false));
        btnFiltrar.setOnClickListener(v -> {
            if (desdeSel == null || hastaSel == null) {
                Toast.makeText(this, "Selecciona Desde y Hasta", Toast.LENGTH_SHORT).show();
                return;
            }
            // Validación simple: desde <= hasta (lexicográfico funciona con yyyy-MM-dd)
            if (desdeSel.compareTo(hastaSel) > 0) {
                Toast.makeText(this, "La fecha Desde no puede ser mayor que Hasta", Toast.LENGTH_SHORT).show();
                return;
            }
            cargarDatosRango(desdeSel, hastaSel);
        });
    }

    private void setRangoMesActual() {
        Calendar c = Calendar.getInstance();
        String desde = String.format(Locale.US, "%04d-%02d-01",
                c.get(Calendar.YEAR), (c.get(Calendar.MONTH) + 1));
        Calendar end = (Calendar) c.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        String hasta = String.format(Locale.US, "%04d-%02d-%02d",
                end.get(Calendar.YEAR), (end.get(Calendar.MONTH) + 1), end.get(Calendar.DAY_OF_MONTH));

        desdeSel = desde;
        hastaSel = hasta;

        btnDesde.setText(desdeSel);
        btnHasta.setText(hastaSel);
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

    private void cargarDatosRango(String desde, String hasta) {
        AppDatabase.ioExecutor.execute(() -> {
            // ✅ rango
            List<Combustible> lista = AppDatabase.get(this).combustibleDao().listByRango(desde, hasta);
            double total = AppDatabase.get(this).combustibleDao().sumTotalPagadoRango(desde, hasta);
            int recargas = AppDatabase.get(this).combustibleDao().countRecargasRango(desde, hasta);

            runOnUiThread(() -> {
                adapter.setData(lista);
                tvTotales.setText("Recargas: " + recargas + "  |  Total: " + clp.format(total));
            });
        });
    }

    private void confirmarEliminar(Combustible c) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar recarga")
                .setMessage("¿Eliminar esta recarga de combustible?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    AppDatabase.ioExecutor.execute(() -> {
                        AppDatabase.get(this).combustibleDao().deleteById(c.id);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show();
                            cargarDatosRango(desdeSel, hastaSel);
                        });
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    private void abrirCalendario(EditText et) {
        Calendar c = Calendar.getInstance();

        // Si ya hay fecha, la usa como inicial
        String actual = et.getText().toString();
        if (actual != null && actual.contains("-")) {
            String[] p = actual.split("-");
            if (p.length == 3) {
                try {
                    c.set(Calendar.YEAR, Integer.parseInt(p[0]));
                    c.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
                    c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
            }
        }

        new DatePickerDialog(this, (picker, y, m, d) -> {
            String fechaIso = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            et.setText(fechaIso);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void mostrarDialogoEditar(Combustible c) {
        LayoutInflater inf = LayoutInflater.from(this);
        View view = inf.inflate(R.layout.dialog_editar_bencina, null);

        EditText etFecha    = view.findViewById(R.id.etFechaEdit);
        EditText etBenc     = view.findViewById(R.id.etBencineraEdit);
        EditText etTipo     = view.findViewById(R.id.etTipoEdit);
        EditText etKmAnt    = view.findViewById(R.id.etKmAntEdit);
        EditText etKmAct    = view.findViewById(R.id.etKmActEdit);
        EditText etValor    = view.findViewById(R.id.etValorEdit);
        EditText etLitros   = view.findViewById(R.id.etLitrosEdit);

        etFecha.setText(c.fecha);
        etFecha.setOnClickListener(v -> abrirCalendario(etFecha));

        etBenc.setText(c.bencinera);
        etTipo.setText(c.tipo);
        etKmAnt.setText(String.valueOf(c.kmAnterior));
        etKmAct.setText(String.valueOf(c.kmActual));
        etValor.setText(String.valueOf(c.valorLitro));
        etLitros.setText(String.valueOf(c.litros));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Editar recarga")
                .setView(view)
                .setPositiveButton("Guardar", (d, w) -> {
                    String fechaStrRaw = etFecha.getText().toString().trim();
                    String fechaStr = normalizarFechaIso(fechaStrRaw);
                    if (fechaStr == null) {
                        Toast.makeText(this, "Fecha inválida.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String bencStr  = etBenc.getText().toString().trim();
                    String tipoStr  = etTipo.getText().toString().trim();
                    String kmAntStr = etKmAnt.getText().toString().trim();
                    String kmActStr = etKmAct.getText().toString().trim();
                    String valorStr = etValor.getText().toString().trim();
                    String litrosStr= etLitros.getText().toString().trim();

                    if (fechaStr.isEmpty() || bencStr.isEmpty() || tipoStr.isEmpty() ||
                            kmAntStr.isEmpty() || kmActStr.isEmpty() ||
                            valorStr.isEmpty() || litrosStr.isEmpty()) {
                        Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double kmAnt, kmAct, valor, litros;
                    try {
                        kmAnt = Double.parseDouble(kmAntStr);
                        kmAct = Double.parseDouble(kmActStr);
                        valor = Double.parseDouble(valorStr);
                        litros = Double.parseDouble(litrosStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Valores numéricos inválidos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double kmRec = kmAct - kmAnt;
                    double total = valor * litros;

                    AppDatabase.ioExecutor.execute(() -> {
                        AppDatabase.get(this).combustibleDao().updateById(
                                c.id,
                                fechaStr,
                                bencStr,
                                tipoStr,
                                kmAnt,
                                kmAct,
                                kmRec,
                                valor,
                                litros,
                                total
                        );
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show();
                            cargarDatosRango(desdeSel, hastaSel);
                        });
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String normalizarFechaIso(String f) {
        if (f == null) return null;
        f = f.trim();
        if (f.isEmpty()) return null;

        // Acepta "yyyy/MM/dd" o "yyyy-MM-dd"
        f = f.replace('/', '-');

        // Validación básica rápida yyyy-MM-dd
        String[] p = f.split("-");
        if (p.length != 3) return null;

        try {
            int y = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int d = Integer.parseInt(p[2]);
            if (y < 2000 || m < 1 || m > 12 || d < 1 || d > 31) return null;

            return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

}
