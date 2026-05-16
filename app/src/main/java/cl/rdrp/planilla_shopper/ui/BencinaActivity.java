package cl.rdrp.planilla_shopper.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Combustible;

public class BencinaActivity extends AppCompatActivity {

    private Spinner spBencinera, spTipoBencina;
    private EditText etKmAnterior, etKmActual, etValor, etLitros, etKmPorL;
    private TextView tvResumen;
    private Button btnGuardar;

    private final DecimalFormat dfNum = new DecimalFormat("#,##0.##");
    private final DecimalFormat dfMoney = new DecimalFormat("#,##0");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bencina);

        spBencinera   = findViewById(R.id.spBencinera);
        spTipoBencina = findViewById(R.id.spTipoBencina);
        etKmAnterior  = findViewById(R.id.etKmAnterior);
        etKmActual    = findViewById(R.id.etKmActual);
        etValor       = findViewById(R.id.etValor);
        etLitros      = findViewById(R.id.etLitros);
        tvResumen     = findViewById(R.id.tvResumen);
        btnGuardar    = findViewById(R.id.btnGuardarBencina);

        cargarSpinners();
        cargarKmAnteriorDesdeUltimaRecarga();

        btnGuardar.setOnClickListener(v -> guardarBencina());
    }

    private void cargarSpinners() {
        String[] bencineras = {"Shell", "Copec", "Aramco", "Otra"};
        ArrayAdapter<String> adapterBen =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bencineras);
        adapterBen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBencinera.setAdapter(adapterBen);

        String[] tipos = {"93", "95", "97", "Diésel"};
        ArrayAdapter<String> adapterTipo =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        adapterTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTipoBencina.setAdapter(adapterTipo);
    }

    // Rellenar kmAnterior con la última carga guardada
    private void cargarKmAnteriorDesdeUltimaRecarga() {
        AppDatabase.ioExecutor.execute(() -> {
            Combustible last = AppDatabase.get(BencinaActivity.this)
                    .combustibleDao()
                    .getLast();
            if (last != null) {
                runOnUiThread(() ->
                        etKmAnterior.setText(String.valueOf(last.kmActual))
                );
            }
        });
    }

    private void guardarBencina() {
        String fecha     = hoyEnISO();
        String bencinera = spBencinera.getSelectedItem().toString();
        String tipo      = spTipoBencina.getSelectedItem().toString();
        String kmAntS    = s(etKmAnterior.getText());
        String kmActS    = s(etKmActual.getText());
        String valorS    = s(etValor.getText());
        String litrosS   = s(etLitros.getText());

        if (fecha.isEmpty() || bencinera.isEmpty() || tipo.isEmpty() ||
                kmAntS.isEmpty() || kmActS.isEmpty() ||
                valorS.isEmpty() || litrosS.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        double kmAnt, kmAct, valorLitro, litros;
        try {
            kmAnt      = Double.parseDouble(kmAntS);
            kmAct      = Double.parseDouble(kmActS);
            valorLitro = Double.parseDouble(valorS);
            litros     = Double.parseDouble(litrosS);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valores numéricos inválidos", Toast.LENGTH_SHORT).show();
            return;
        }

        double kmRecorridos = kmAct - kmAnt;
        if (kmRecorridos <= 0) {
            Toast.makeText(this, "KM actual debe ser mayor que KM anterior", Toast.LENGTH_SHORT).show();
            return;
        }
        if (litros <= 0) {
            Toast.makeText(this, "Los litros deben ser mayores a 0", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalPagado    = valorLitro * litros;
        double consumoKmLitro = kmRecorridos / litros;

        Combustible c = new Combustible(
                fecha,
                bencinera,
                tipo,
                kmAnt,
                kmAct,
                kmRecorridos,
                valorLitro,
                litros,
                totalPagado
        );

        AppDatabase.ioExecutor.execute(() -> {
            AppDatabase.get(BencinaActivity.this).combustibleDao().insert(c);

            runOnUiThread(() -> {
                try {
                    // --- Actualizar resumen si el TextView existe ---
                    if (tvResumen != null) {
                        String resumen = "Recorriste " + dfNum.format(kmRecorridos) + " km\n" +
                                "Consumo: " + String.format(Locale.getDefault(), "%.2f km/L", consumoKmLitro) + "\n" +
                                "Total: $" + dfMoney.format(totalPagado);
                        tvResumen.setText(resumen);
                    }

                    // --- Mostrar km/L en el campo, solo si realmente existe en el layout ---
                    if (etKmPorL != null) {
                        etKmPorL.setText(
                                String.format(Locale.getDefault(), "%.2f km/L", consumoKmLitro)
                        );
                    }

                    Toast.makeText(BencinaActivity.this,
                            "Guardado. Rendimiento: " +
                                    String.format(Locale.getDefault(), "%.2f km/L", consumoKmLitro),
                            Toast.LENGTH_LONG).show();

                    limpiarCampos();

                } catch (Exception e) {
                    // Si algo falla aquí, no se cae la app, pero vemos el error en Logcat
                    e.printStackTrace();
                    Toast.makeText(BencinaActivity.this,
                            "Error al actualizar la vista: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }


    private void limpiarCampos() {
        // Dejamos el kmAnterior con el último kmActual
        String kmActS = s(etKmActual.getText());
        if (!kmActS.isEmpty()) {
            etKmAnterior.setText(kmActS);
        }
        etKmActual.setText("");
        etValor.setText("");
        etLitros.setText("");
        // tvResumen lo dejamos con el último resultado para que lo vea
    }

    // ==== Helpers ====

    private static String hoyEnISO() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private static String s(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
