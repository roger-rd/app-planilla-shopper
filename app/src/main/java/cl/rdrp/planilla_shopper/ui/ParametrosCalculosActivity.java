package cl.rdrp.planilla_shopper.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Parsers;
import cl.rdrp.planilla_shopper.util.Prefs;
import cl.rdrp.planilla_shopper.util.Texts;

/**
 * Pantalla de parametros de calculo (Fase 4c).
 *
 * Permite editar 5 valores que viven en SharedPreferences via Prefs.* y son
 * leidos por Config.get*. Los tramos por SKU, valores de KM con logica de
 * fecha y reglas de dia de la semana NO son configurables (siguen duros
 * en Config).
 *
 * Convenciones:
 *   - La comision se MUESTRA como porcentaje (15.25), pero se PERSISTE
 *     como decimal (0.1525). La conversion ocurre solo en load/save.
 *   - Los doubles se formatean con Locale.US para usar punto decimal,
 *     evitando que el sistema con coma decimal muestre "10,0" en un
 *     campo numberDecimal que solo acepta punto.
 *   - Antes de guardar / restaurar siempre hay un dialogo de confirmacion
 *     porque el cambio afecta calculos de dias pasados.
 */
public class ParametrosCalculosActivity extends AppCompatActivity {

    private TextInputEditText etValorSku, etComision, etRendimiento, etPrecioLitro, etBonoKm;
    private TextView tvValorSku;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parametros_calculos);

        setTitle("Parámetros de cálculo");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etValorSku    = findViewById(R.id.etValorSku);
        etComision    = findViewById(R.id.etComision);
        etRendimiento = findViewById(R.id.etRendimiento);
        etPrecioLitro = findViewById(R.id.etPrecioLitro);
        etBonoKm      = findViewById(R.id.etBonoKm);
        tvValorSku    = findViewById(R.id.tvValorSku);

        cargarValoresActuales();

        Button btnGuardar   = findViewById(R.id.btnGuardarParams);
        Button btnRestaurar = findViewById(R.id.btnRestaurarParams);

        btnGuardar.setOnClickListener(v -> confirmarYGuardar());
        btnRestaurar.setOnClickListener(v -> confirmarYRestaurar());
    }

    /** Lee los valores actuales desde Config y los pinta en los EditTexts y en la card informativa. */
    private void cargarValoresActuales() {
        int    valorSku    = Config.getValorUnitSku();
        double comisionDec = Config.getComisionPorc();
        double rendimiento = Config.getRendimientoKmPorLitro();
        int    precioLitro = Config.getPrecioLitro();
        double bonoKm      = Config.getBonoPorKmDomLunMar();

        etValorSku.setText(String.valueOf(valorSku));
        etComision.setText(formatDecimal(comisionDec * 100.0));
        etRendimiento.setText(formatDecimal(rendimiento));
        etPrecioLitro.setText(String.valueOf(precioLitro));
        etBonoKm.setText(formatDecimal(bonoKm));

        // Card informativa refleja el valor actual
        if (tvValorSku != null) {
            tvValorSku.setText("Valor por SKU: $" + valorSku);
        }
    }

    /** Formatea un double con Locale.US (punto decimal), evitando "10,0" en sistemas con coma. */
    private static String formatDecimal(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            // entero como entero
            return String.format(Locale.US, "%d", (long) d);
        }
        return String.format(Locale.US, "%s", d);
    }

    private void confirmarYGuardar() {
        // Parseo robusto
        Integer valorSku   = Parsers.parseIntOrNull(Texts.s(etValorSku.getText()));
        Double comisionPct = Parsers.parseDoubleOrNull(Texts.s(etComision.getText()));
        Double rendimiento = Parsers.parseDoubleOrNull(Texts.s(etRendimiento.getText()));
        Integer precioLitro= Parsers.parseIntOrNull(Texts.s(etPrecioLitro.getText()));
        Double bonoKm      = Parsers.parseDoubleOrNull(Texts.s(etBonoKm.getText()));

        if (valorSku == null || comisionPct == null || rendimiento == null
                || precioLitro == null || bonoKm == null) {
            Toast.makeText(this,
                    "Completa todos los campos con valores numéricos válidos",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Validaciones de rango
        if (valorSku <= 0) {
            Toast.makeText(this, "Valor por SKU debe ser mayor a 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (comisionPct < 0 || comisionPct > 100) {
            Toast.makeText(this, "Comisión debe estar entre 0 y 100", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rendimiento <= 0) {
            Toast.makeText(this, "Rendimiento km/litro debe ser mayor a 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (precioLitro <= 0) {
            Toast.makeText(this, "Precio del litro debe ser mayor a 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bonoKm < 0) {
            Toast.makeText(this, "Bono por km no puede ser negativo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Valores finales (la comision pasa de % a decimal)
        final int    fValorSku    = valorSku;
        final double fComisionDec = comisionPct / 100.0;
        final double fRendimiento = rendimiento;
        final int    fPrecioLitro = precioLitro;
        final double fBonoKm      = bonoKm;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar cambio de parámetros")
                .setMessage(
                        "Estos valores afectan los cálculos de TODOS los días (pasados y futuros).\n\n" +
                        "No se afectan: los tramos base por SKU, los valores de KM con su lógica " +
                        "histórica de fecha, ni las reglas de día de la semana.\n\n" +
                        "¿Confirmás guardar?"
                )
                .setPositiveButton("Guardar", (d, w) -> {
                    Prefs.setValorUnitSku(this,           fValorSku);
                    Prefs.setComisionPorc(this,           fComisionDec);
                    Prefs.setRendimientoKmPorLitro(this,  fRendimiento);
                    Prefs.setPrecioLitro(this,            fPrecioLitro);
                    Prefs.setBonoPorKmDomLunMar(this,     fBonoKm);

                    Toast.makeText(this, "Parámetros guardados", Toast.LENGTH_SHORT).show();
                    cargarValoresActuales();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarYRestaurar() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restaurar valores por defecto")
                .setMessage(
                        "Esto vuelve los 5 parámetros editables a sus valores originales:\n\n" +
                        "  • Valor por SKU: $" + Config.DEFAULT_VALOR_UNIT_SKU + "\n" +
                        "  • Comisión: " + (Config.DEFAULT_COMISION_PORC * 100.0) + "%\n" +
                        "  • Rendimiento: " + Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO + " km/L\n" +
                        "  • Precio del litro: $" + Config.DEFAULT_PRECIO_LITRO + "\n" +
                        "  • Bono por km: $" + Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR + "\n\n" +
                        "¿Confirmás restaurar?"
                )
                .setPositiveButton("Restaurar", (d, w) -> {
                    Prefs.setValorUnitSku(this,           Config.DEFAULT_VALOR_UNIT_SKU);
                    Prefs.setComisionPorc(this,           Config.DEFAULT_COMISION_PORC);
                    Prefs.setRendimientoKmPorLitro(this,  Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO);
                    Prefs.setPrecioLitro(this,            Config.DEFAULT_PRECIO_LITRO);
                    Prefs.setBonoPorKmDomLunMar(this,     Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR);

                    Toast.makeText(this, "Parámetros restaurados", Toast.LENGTH_SHORT).show();
                    cargarValoresActuales();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
