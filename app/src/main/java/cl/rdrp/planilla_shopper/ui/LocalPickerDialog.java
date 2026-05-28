package cl.rdrp.planilla_shopper.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Local;
import cl.rdrp.planilla_shopper.util.Prefs;

/**
 * Popup de seleccion de sala / local (N5).
 *
 * Igual paradigma que el popup de modalidad: muestra una card-button por
 * cada local activo del catalogo. Al tocar una, se guarda como local
 * activo en Prefs y se ejecuta el callback. Si el catalogo esta vacio,
 * muestra un estado especial con un boton para ir a crear el primero.
 *
 * Uso esperado:
 *   - Primer arranque (no hay local activo): se invoca antes del popup de
 *     modalidad, y al elegir uno se encadena con el de modalidad.
 *   - Desde el drawer: el usuario quiere cambiar de sala.
 */
public class LocalPickerDialog {

    public interface OnSeleccion {
        void onElegido(Local l);
    }

    /**
     * Muestra el popup. La carga de locales se hace async via ioExecutor;
     * mientras tanto la UI no se bloquea.
     *
     * @param activity  Activity para construir el dialog y para startActivity.
     * @param titulo    Titulo del dialog (ej. "¿En qué sala estás hoy?").
     * @param cancelable Si true, el usuario puede cerrarlo sin elegir.
     *                   Para el primer arranque usar false.
     * @param callback  Se ejecuta cuando el usuario elige un local.
     */
    public static void mostrar(android.app.Activity activity,
                               String titulo,
                               boolean cancelable,
                               OnSeleccion callback) {
        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_local, null);
        LinearLayout contenedor = v.findViewById(R.id.contenedorLocales);
        LinearLayout contenedorVacio = v.findViewById(R.id.contenedorVacio);
        MaterialButton btnIrACrear = v.findViewById(R.id.btnIrACrearLocal);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(titulo)
                .setView(v)
                .setCancelable(cancelable)
                .create();

        // Carga async para no bloquear UI thread
        AppDatabase.ioExecutor.execute(() -> {
            List<Local> locales = AppDatabase.get(activity).localDao().listActivos();
            activity.runOnUiThread(() -> {
                if (locales == null || locales.isEmpty()) {
                    contenedor.setVisibility(View.GONE);
                    contenedorVacio.setVisibility(View.VISIBLE);
                    btnIrACrear.setOnClickListener(b -> {
                        dialog.dismiss();
                        activity.startActivity(new Intent(activity, LocalesActivity.class));
                    });
                } else {
                    contenedor.setVisibility(View.VISIBLE);
                    contenedorVacio.setVisibility(View.GONE);
                    construirCards(activity, contenedor, locales, dialog, callback);
                }
            });
        });

        dialog.show();
    }

    /**
     * Construye una card-button por cada local. Reutiliza el estilo visual
     * de las cards del popup de modalidad para mantener consistencia.
     */
    private static void construirCards(android.app.Activity activity,
                                       LinearLayout contenedor,
                                       List<Local> locales,
                                       AlertDialog dialog,
                                       OnSeleccion callback) {
        Context ctx = activity;
        LayoutInflater inflater = LayoutInflater.from(ctx);
        contenedor.removeAllViews();

        int marginPx = (int) (8 * ctx.getResources().getDisplayMetrics().density);

        for (Local l : locales) {
            // Inflamos un MaterialCardView programaticamente para no crear
            // un layout extra. Hacemos lo justo para que se vea como las
            // cards de dialog_modalidad.xml.
            MaterialCardView card = new MaterialCardView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = marginPx;
            card.setLayoutParams(lp);
            card.setRadius(12 * ctx.getResources().getDisplayMetrics().density);
            card.setCardElevation(0f);
            card.setStrokeWidth((int) (1 * ctx.getResources().getDisplayMetrics().density));
            card.setStrokeColor(ctx.getColor(R.color.blue_primary));
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(ctx.getDrawable(
                    android.R.drawable.list_selector_background
            ));

            LinearLayout inner = new LinearLayout(ctx);
            inner.setOrientation(LinearLayout.VERTICAL);
            int padPx = (int) (14 * ctx.getResources().getDisplayMetrics().density);
            inner.setPadding(padPx, padPx, padPx, padPx);

            TextView tvCodigo = new TextView(ctx);
            tvCodigo.setText(l.codigo);
            tvCodigo.setTextSize(16f);
            tvCodigo.setTypeface(tvCodigo.getTypeface(), android.graphics.Typeface.BOLD);
            tvCodigo.setTextColor(ctx.getColor(R.color.blue_primary));

            inner.addView(tvCodigo);

            if (l.alias != null && !l.alias.trim().isEmpty()) {
                TextView tvAlias = new TextView(ctx);
                tvAlias.setText(l.alias);
                tvAlias.setTextSize(13f);
                tvAlias.setTextColor(ctx.getColor(R.color.blue_onSurface));
                tvAlias.setAlpha(0.8f);
                LinearLayout.LayoutParams lpa = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lpa.topMargin = (int) (2 * ctx.getResources().getDisplayMetrics().density);
                tvAlias.setLayoutParams(lpa);
                inner.addView(tvAlias);
            }

            card.addView(inner);

            card.setOnClickListener(v -> {
                Prefs.setLocalActivo(activity, l.codigo);
                dialog.dismiss();
                if (callback != null) callback.onElegido(l);
            });

            contenedor.addView(card);
        }
    }
}
