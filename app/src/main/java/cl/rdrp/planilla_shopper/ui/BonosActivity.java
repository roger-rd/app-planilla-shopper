package cl.rdrp.planilla_shopper.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Local;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.util.BonoConfig;
import cl.rdrp.planilla_shopper.util.BonosFormulario;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Parsers;
import cl.rdrp.planilla_shopper.util.Prefs;
import cl.rdrp.planilla_shopper.util.Texts;

/** Editor por modalidad. Solo persiste las cuatro preferencias nuevas del bono. */
public class BonosActivity extends AppCompatActivity {
    private static final int[] DIAS = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
    private static final String[] NOMBRES = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private final Map<Integer, MaterialCheckBox> dias = new LinkedHashMap<>();
    private final Map<String, MaterialCheckBox> salas = new LinkedHashMap<>();
    private Modalidad modalidad;
    private MaterialSwitch activo;
    private TextInputEditText monto;
    private boolean cargado;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_bonos);
        setTitle("Bonos por km");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        modalidad = state == null ? Prefs.getModalidad(this)
                : Modalidad.valueOf(state.getString("modalidad"));
        ((TextView) findViewById(R.id.tvModalidadBonos)).setText("Modalidad activa: "
                + modalidad.getLabel() + ". Para editar otra modalidad, cámbiala desde el menú lateral.");
        TextView estado = findViewById(R.id.tvEstadoBonos);
        if (modalidad == Modalidad.PICKER) {
            estado.setText("Picker no usa KM / bonos por km");
            return;
        }
        activo = findViewById(R.id.swBonoKmActivo);
        monto = findViewById(R.id.etMontoBonoKm);
        LinearLayout diasLayout = findViewById(R.id.diasBonoKm);
        for (int i = 0; i < DIAS.length; i++) {
            MaterialCheckBox check = new MaterialCheckBox(this);
            check.setText(NOMBRES[i]);
            dias.put(DIAS[i], check);
            diasLayout.addView(check);
        }
        BonoConfig inicial = Config.bonoConfig(modalidad);
        boolean restaurarBorrador = state != null && state.getBoolean("cargado");
        if (restaurarBorrador) {
            inicial = new BonoConfig(state.getBoolean("activo"), 0,
                    state.getIntegerArrayList("dias"), state.getStringArrayList("salas"));
        }
        final BonoConfig seleccion = inicial;
        AppDatabase.ioExecutor.execute(() -> {
            try {
                List<Local> catalogo = AppDatabase.get(getApplicationContext()).localDao().listAll();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    for (Local local : catalogo) agregarSala(local.codigo,
                            local.display() + (local.activo ? "" : " (archivada)"));
                    aplicar(seleccion);
                    if (restaurarBorrador) monto.setText(state.getString("monto"));
                    cargado = true;
                    estado.setVisibility(View.GONE);
                    findViewById(R.id.controlesBonos).setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        estado.setText("No se pudieron cargar las salas. Vuelve a abrir esta pantalla para reintentar.");
                });
            }
        });
        findViewById(R.id.btnGuardarBonos).setOnClickListener(v -> guardar());
        findViewById(R.id.btnRestaurarBonos).setOnClickListener(v -> confirmar(new BonoConfig(true,
                Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR,
                Arrays.asList(Calendar.MONDAY, Calendar.TUESDAY), Collections.singleton("139")), true));
    }

    private void agregarSala(String codigo, String etiqueta) {
        if (salas.containsKey(codigo)) return;
        MaterialCheckBox check = new MaterialCheckBox(this);
        check.setText(etiqueta);
        salas.put(codigo, check);
        ((LinearLayout) findViewById(R.id.salasBonoKm)).addView(check);
    }

    private void aplicar(BonoConfig config) {
        activo.setChecked(config.activo);
        monto.setText(String.format(Locale.US, "%s", config.monto));
        for (String sala : config.salas) agregarSala(sala, sala);
        for (Map.Entry<Integer, MaterialCheckBox> e : dias.entrySet())
            e.getValue().setChecked(config.dias.contains(e.getKey()));
        for (Map.Entry<String, MaterialCheckBox> e : salas.entrySet())
            e.getValue().setChecked(config.salas.contains(e.getKey()));
    }

    private <T extends Comparable<? super T>> Set<T> seleccionados(Map<T, MaterialCheckBox> opciones) {
        Set<T> seleccion = new TreeSet<>();
        for (Map.Entry<T, MaterialCheckBox> e : opciones.entrySet())
            if (e.getValue().isChecked()) seleccion.add(e.getKey());
        return seleccion;
    }

    private void guardar() {
        if (!cargado) return;
        Double valor = Parsers.parseDoubleOrNull(Texts.s(monto.getText()));
        Set<Integer> diasElegidos = seleccionados(dias);
        Set<String> salasElegidas = seleccionados(salas);
        String error = BonosFormulario.error(valor, activo.isChecked(), diasElegidos.size(), salasElegidas.size());
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        confirmar(new BonoConfig(activo.isChecked(), valor, diasElegidos, salasElegidas), false);
    }

    private void confirmar(BonoConfig config, boolean defaults) {
        if (!cargado || modalidad == Modalidad.PICKER) return;
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < DIAS.length; i++) if (config.dias.contains(DIAS[i])) nombres.add(NOMBRES[i]);
        String resumen = "Modalidad: " + modalidad.getLabel()
                + "\nBono por km: " + (config.activo ? "activo" : "inactivo")
                + "\nMonto: $" + String.format(Locale.US, "%s", config.monto)
                + "\nDías: " + (nombres.isEmpty() ? "ninguno" : String.join(", ", nombres))
                + "\nSalas: " + (config.salas.isEmpty() ? "ninguna" : String.join(", ", config.salas))
                + "\n\nEl cambio afecta solo pedidos nuevos, no los ya guardados.";
        new MaterialAlertDialogBuilder(this)
                .setTitle(defaults ? "Restaurar valores por defecto" : "Confirmar bonos por km")
                .setMessage(resumen)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(defaults ? "Restaurar" : "Guardar", (d, w) -> {
                    Prefs.setBonoKmActivo(this, modalidad, config.activo);
                    Prefs.setBonoKmMonto(this, modalidad, config.monto);
                    Prefs.setBonoKmDias(this, modalidad, config.dias);
                    Prefs.setBonoKmSalas(this, modalidad, config.salas);
                    aplicar(config);
                    Toast.makeText(this, "Bonos de " + modalidad.getLabel() + " guardados", Toast.LENGTH_SHORT).show();
                }).show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("modalidad", modalidad.name());
        out.putBoolean("cargado", cargado);
        if (cargado) {
            out.putBoolean("activo", activo.isChecked());
            out.putString("monto", Texts.s(monto.getText()));
            out.putIntegerArrayList("dias", new ArrayList<>(seleccionados(dias)));
            out.putStringArrayList("salas", new ArrayList<>(seleccionados(salas)));
        }
        super.onSaveInstanceState(out);
    }

    @Override public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
