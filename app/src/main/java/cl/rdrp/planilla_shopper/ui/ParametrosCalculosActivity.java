package cl.rdrp.planilla_shopper.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Parsers;
import cl.rdrp.planilla_shopper.util.Prefs;
import cl.rdrp.planilla_shopper.util.Texts;

/**
 * Pantalla de parametros de calculo.
 *
 * Permite editar los parametros que viven en SharedPreferences via Prefs.* y
 * son leidos por Config.get*. Los tramos por SKU, valores de KM con logica
 * de fecha y reglas de dia de la semana NO son configurables (siguen duros
 * en Config).
 *
 * Comportamiento N3 (rediseno tras feedback de usuario):
 *   - La pantalla solo muestra y edita la MODALIDAD ACTIVA del usuario
 *     (Prefs.getModalidad). Si el usuario quiere editar otra modalidad,
 *     debe cambiarla primero desde el drawer.
 *   - Los campos visibles dependen de la modalidad activa:
 *       * SHOPPER -> los 5 campos
 *       * PICKER  -> solo Valor por SKU + Comision (Picker no usa KM)
 *       * DRIVER  -> los 5 campos
 *   - Guardar y restaurar afectan SOLO los campos visibles. Las keys
 *     ocultas no se tocan: conservan el valor previo (o el fallback legacy).
 *
 * La persistencia sigue siendo por modalidad (N3a/N3b). Si el usuario
 * cambia de Shopper a Driver y vuelve, los valores de Shopper siguen
 * intactos.
 *
 * Convenciones:
 *   - La comision se MUESTRA como porcentaje (15.25), pero se PERSISTE
 *     como decimal (0.1525). La conversion ocurre solo en load/save.
 *   - Los doubles se formatean con Locale.US para usar punto decimal,
 *     evitando que el sistema con coma decimal muestre "10,0" en un
 *     campo numberDecimal que solo acepta punto.
 *   - Antes de guardar / restaurar siempre hay un dialogo de confirmacion
 *     que aclara cual modalidad se esta afectando.
 */
public class ParametrosCalculosActivity extends AppCompatActivity {

    // EditTexts
    private TextInputEditText etValorSku, etComision, etRendimiento, etPrecioLitro, etBonoKm;
    // TextInputLayouts (los wrappers — los uso para ocultar/mostrar)
    private TextInputLayout tilValorSku, tilComision, tilRendimiento, tilPrecioLitro, tilBonoKm;
    // Subtitulo dinamico y card informativa
    private TextView tvModalidadHint, tvValorSku;

    /** Modalidad activa del usuario. Se lee en onCreate y no cambia. */
    private Modalidad modalidadActual = Modalidad.SHOPPER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parametros_calculos);

        setTitle("Parámetros de cálculo");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Modalidad activa (no se permite cambiar desde esta pantalla)
        modalidadActual = Prefs.getModalidad(this);

        // EditTexts
        etValorSku    = findViewById(R.id.etValorSku);
        etComision    = findViewById(R.id.etComision);
        etRendimiento = findViewById(R.id.etRendimiento);
        etPrecioLitro = findViewById(R.id.etPrecioLitro);
        etBonoKm      = findViewById(R.id.etBonoKm);

        // TextInputLayouts (para ocultar segun modalidad)
        tilValorSku    = findViewById(R.id.tilValorSku);
        tilComision    = findViewById(R.id.tilComision);
        tilRendimiento = findViewById(R.id.tilRendimiento);
        tilPrecioLitro = findViewById(R.id.tilPrecioLitro);
        tilBonoKm      = findViewById(R.id.tilBonoKm);

        // Texto informativo
        tvModalidadHint = findViewById(R.id.tvModalidadHint);
        tvValorSku      = findViewById(R.id.tvValorSku);

        aplicarVisibilidadPorModalidad();
        cargarValoresActuales();

        Button btnGuardar   = findViewById(R.id.btnGuardarParams);
        Button btnRestaurar = findViewById(R.id.btnRestaurarParams);

        btnGuardar.setOnClickListener(v -> confirmarYGuardar());
        btnRestaurar.setOnClickListener(v -> confirmarYRestaurar());
    }

    /**
     * Oculta los TextInputLayouts que no aplican a la modalidad activa.
     * - PICKER no usa KM -> oculta rendimiento, precio litro y bono km.
     * - SHOPPER y DRIVER muestran todos.
     *
     * Tambien actualiza el hint para dejar claro que modalidad se edita.
     */
    private void aplicarVisibilidadPorModalidad() {
        // Por defecto, todo visible
        tilValorSku.setVisibility(View.VISIBLE);
        tilComision.setVisibility(View.VISIBLE);
        tilRendimiento.setVisibility(View.VISIBLE);
        tilPrecioLitro.setVisibility(View.VISIBLE);
        tilBonoKm.setVisibility(View.VISIBLE);

        if (modalidadActual == Modalidad.PICKER) {
            tilRendimiento.setVisibility(View.GONE);
            tilPrecioLitro.setVisibility(View.GONE);
            tilBonoKm.setVisibility(View.GONE);
        }

        if (tvModalidadHint != null) {
            tvModalidadHint.setText(
                    "Parámetros de tu modalidad activa: " + modalidadActual.getLabel() +
                    ". Para editar otra modalidad, cambiala desde el menú lateral."
            );
        }
    }

    /**
     * Lee los valores actuales de la modalidad activa desde Config y los
     * pinta en los EditTexts. Para los campos ocultos no es necesario
     * pintarlos, pero igual los seteamos por si se vuelven visibles
     * (cambio de modalidad mientras la pantalla esta abierta no aplica
     * porque modalidadActual no se recarga, pero queda defensivo).
     */
    private void cargarValoresActuales() {
        int    valorSku    = Config.getValorUnitSku(modalidadActual);
        double comisionDec = Config.getComisionPorc(modalidadActual);
        double rendimiento = Config.getRendimientoKmPorLitro(modalidadActual);
        int    precioLitro = Config.getPrecioLitro(modalidadActual);
        double bonoKm      = Config.getBonoPorKmDomLunMar(modalidadActual);

        etValorSku.setText(String.valueOf(valorSku));
        etComision.setText(formatDecimal(comisionDec * 100.0));
        etRendimiento.setText(formatDecimal(rendimiento));
        etPrecioLitro.setText(String.valueOf(precioLitro));
        etBonoKm.setText(formatDecimal(bonoKm));

        // Card informativa refleja el valor actual de la modalidad activa
        if (tvValorSku != null) {
            tvValorSku.setText("Valor por SKU (" + modalidadActual.getLabel() + "): $" + valorSku);
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
        final boolean usaKm = modalidadActual != Modalidad.PICKER;

        // Parseo robusto solo de los campos que aplican a la modalidad
        Integer valorSku   = Parsers.parseIntOrNull(Texts.s(etValorSku.getText()));
        Double comisionPct = Parsers.parseDoubleOrNull(Texts.s(etComision.getText()));
        Double rendimiento = usaKm ? Parsers.parseDoubleOrNull(Texts.s(etRendimiento.getText())) : null;
        Integer precioLitro= usaKm ? Parsers.parseIntOrNull(Texts.s(etPrecioLitro.getText())) : null;
        Double bonoKm      = usaKm ? Parsers.parseDoubleOrNull(Texts.s(etBonoKm.getText())) : null;

        // Validacion: campos obligatorios segun modalidad
        boolean faltaAlgo = valorSku == null || comisionPct == null
                || (usaKm && (rendimiento == null || precioLitro == null || bonoKm == null));
        if (faltaAlgo) {
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
        if (usaKm) {
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
        }

        // Valores finales (la comision pasa de % a decimal)
        final Modalidad fModalidad   = modalidadActual;
        final int    fValorSku       = valorSku;
        final double fComisionDec    = comisionPct / 100.0;
        // Estos solo se usan si usaKm == true
        final Double fRendimiento    = rendimiento;
        final Integer fPrecioLitro   = precioLitro;
        final Double fBonoKm         = bonoKm;

        // Mensaje del dialogo: lista solo los campos que se van a guardar
        StringBuilder lista = new StringBuilder();
        lista.append("  • Valor por SKU: $").append(fValorSku).append("\n");
        lista.append("  • Comisión: ").append(comisionPct).append("%\n");
        if (usaKm) {
            lista.append("  • Rendimiento: ").append(formatDecimal(fRendimiento)).append(" km/L\n");
            lista.append("  • Precio del litro: $").append(fPrecioLitro).append("\n");
            lista.append("  • Bono por km: $").append(formatDecimal(fBonoKm)).append("\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar cambio de parámetros")
                .setMessage(
                        "Vas a guardar los parámetros de la modalidad: " + fModalidad.getLabel() + ".\n\n" +
                        lista +
                        "\nEstos valores afectan los cálculos de TODOS los días (pasados y futuros) " +
                        "en los que esta modalidad esté activa.\n\n" +
                        "¿Confirmás guardar?"
                )
                .setPositiveButton("Guardar", (d, w) -> {
                    Prefs.setValorUnitSku(this, fModalidad, fValorSku);
                    Prefs.setComisionPorc(this, fModalidad, fComisionDec);
                    if (usaKm) {
                        Prefs.setRendimientoKmPorLitro(this, fModalidad, fRendimiento);
                        Prefs.setPrecioLitro(this,           fModalidad, fPrecioLitro);
                        Prefs.setBonoPorKmDomLunMar(this,    fModalidad, fBonoKm);
                    }

                    Toast.makeText(this,
                            "Parámetros de " + fModalidad.getLabel() + " guardados",
                            Toast.LENGTH_SHORT).show();
                    cargarValoresActuales();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarYRestaurar() {
        final Modalidad fModalidad = modalidadActual;
        final boolean usaKm = fModalidad != Modalidad.PICKER;

        // Mensaje del dialogo: lista solo los campos que se van a restaurar
        StringBuilder lista = new StringBuilder();
        lista.append("  • Valor por SKU: $").append(Config.DEFAULT_VALOR_UNIT_SKU).append("\n");
        lista.append("  • Comisión: ").append(Config.DEFAULT_COMISION_PORC * 100.0).append("%\n");
        if (usaKm) {
            lista.append("  • Rendimiento: ").append(Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO).append(" km/L\n");
            lista.append("  • Precio del litro: $").append(Config.DEFAULT_PRECIO_LITRO).append("\n");
            lista.append("  • Bono por km: $").append(Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR).append("\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Restaurar valores por defecto")
                .setMessage(
                        "Esto vuelve los parámetros editables de la modalidad " +
                        fModalidad.getLabel() + " a sus valores originales:\n\n" +
                        lista +
                        "\n¿Confirmás restaurar?"
                )
                .setPositiveButton("Restaurar", (d, w) -> {
                    Prefs.setValorUnitSku(this, fModalidad, Config.DEFAULT_VALOR_UNIT_SKU);
                    Prefs.setComisionPorc(this, fModalidad, Config.DEFAULT_COMISION_PORC);
                    if (usaKm) {
                        Prefs.setRendimientoKmPorLitro(this, fModalidad, Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO);
                        Prefs.setPrecioLitro(this,           fModalidad, Config.DEFAULT_PRECIO_LITRO);
                        Prefs.setBonoPorKmDomLunMar(this,    fModalidad, Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR);
                    }

                    Toast.makeText(this,
                            "Parámetros de " + fModalidad.getLabel() + " restaurados",
                            Toast.LENGTH_SHORT).show();
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
