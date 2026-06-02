package cl.rdrp.planilla_shopper.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.TramosVersion;
import cl.rdrp.planilla_shopper.data.TramosVersionDao;
import cl.rdrp.planilla_shopper.util.Config;
import cl.rdrp.planilla_shopper.util.Fechas;
import cl.rdrp.planilla_shopper.util.Parsers;
import cl.rdrp.planilla_shopper.util.Texts;
import cl.rdrp.planilla_shopper.util.TramosResolver;

/**
 * Pantalla CRUD de versiones de tramos por modalidad.
 *
 * Selector de modalidad arriba (TabLayout). Por debajo, lista de versiones
 * de esa modalidad ordenadas desc por fecha_desde. FAB para crear nueva
 * version. Tap en una version -> editar. Long press -> menu eliminar.
 *
 * Despues de cualquier escritura llama TramosResolver.invalidate() para
 * que los calculos en el resto de la app refresquen el cache.
 *
 * Reglas de UX:
 *   - No se permite eliminar la ULTIMA version de una modalidad: si no
 *     hay ninguna, los calculos caen al fallback hardcoded sin que el
 *     usuario sepa por que. Mejor forzar a tener siempre al menos 1.
 *   - Si crea una version con fecha_desde duplicada para esa modalidad,
 *     se prevee con check via DAO (la nueva pisa la vieja semanticamente,
 *     pero como ambas existirian, las dos saldrian en la lista). Lo
 *     bloqueamos para evitar confusion.
 */
public class TramosActivity extends AppCompatActivity {

    private TramosVersionDao dao;
    private TramosAdapter adapter;
    private View tvEmpty;
    private TabLayout tabs;

    private Modalidad modalidadActiva = Modalidad.SHOPPER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tramos);

        setTitle("Tramos por SKU");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dao = AppDatabase.get(this).tramosDao();
        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.rvVersiones);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TramosAdapter(new TramosAdapter.OnAction() {
            @Override public void onTap(TramosVersion v) { iniciarEdicion(v); }
            @Override public void onLongPress(TramosVersion v) { mostrarMenuEliminar(v); }
        });
        rv.setAdapter(adapter);

        tabs = findViewById(R.id.tabsModalidad);
        for (Modalidad m : Modalidad.values()) {
            tabs.addTab(tabs.newTab().setText(m.getLabel()));
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int idx = tab.getPosition();
                Modalidad[] all = Modalidad.values();
                if (idx >= 0 && idx < all.length) {
                    modalidadActiva = all[idx];
                    cargarLista();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        FloatingActionButton fab = findViewById(R.id.fabAgregar);
        fab.setOnClickListener(v -> mostrarFormulario(null));

        cargarLista();
    }

    private void cargarLista() {
        final Modalidad m = modalidadActiva;
        AppDatabase.ioExecutor.execute(() -> {
            List<TramosVersion> items = dao.listByModalidad(m.name());
            runOnUiThread(() -> {
                adapter.submit(items);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    /**
     * Punto de entrada cuando el usuario tapea una version para editarla.
     *
     * Politica de proteccion del historico (decision Roger 2026-05-27):
     *   - Editar la version MAS RECIENTE de esa modalidad: OK, sin warning.
     *     Es el caso tipico de "ajustar antes de que aplique a otros pedidos"
     *     o corregir un typo en una version recien creada.
     *   - Editar cualquier version con al menos una posterior: warning
     *     explicito, porque cambiarla altera calculos de los pedidos que
     *     se hicieron con esa version como vigente.
     *
     * El criterio "mas reciente" se aplica sobre fecha_desde (no importa si
     * la mas reciente es futura — todavia no aplica a nadie).
     */
    private void iniciarEdicion(TramosVersion v) {
        AppDatabase.ioExecutor.execute(() -> {
            List<TramosVersion> todas = dao.listByModalidad(modalidadActiva.name());
            // todas viene DESC por fecha_desde -> la mas reciente es el indice 0.
            boolean esLaMasReciente = !todas.isEmpty() && todas.get(0).id == v.id;
            runOnUiThread(() -> {
                if (esLaMasReciente) {
                    mostrarFormulario(v);
                } else {
                    advertirYEditar(v);
                }
            });
        });
    }

    private void advertirYEditar(TramosVersion v) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Editar versión antigua")
                .setMessage(
                        "Esta versión NO es la más reciente. Editarla va a cambiar " +
                                "los cálculos de los pedidos que dependieron de ella " +
                                "en el pasado.\n\n" +
                                "Si lo que querés es definir nuevos valores sin tocar " +
                                "el histórico, cancelá y creá una nueva versión con el " +
                                "botón + (fecha de hoy en adelante).")
                .setPositiveButton("Editar igual", (d, w) -> mostrarFormulario(v))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Dialogo para crear o editar. Si existente == null es alta nueva; el
     * formulario se prellena con los valores HARDCODED iniciales para que
     * el usuario tenga un punto de partida. Si edicion, con los actuales.
     */
    private void mostrarFormulario(TramosVersion existente) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_tramos_form, null);

        TextInputEditText etFecha = v.findViewById(R.id.etFechaDesde);
        TextInputEditText[] ets = {
                v.findViewById(R.id.etT1),
                v.findViewById(R.id.etT2),
                v.findViewById(R.id.etT3),
                v.findViewById(R.id.etT4),
                v.findViewById(R.id.etT5),
                v.findViewById(R.id.etT6),
                v.findViewById(R.id.etT7),
                v.findViewById(R.id.etT8),
        };

        // Prellenado: edicion -> valores actuales. Alta -> hoy + valores hist.
        if (existente != null) {
            etFecha.setText(existente.fechaDesde);
            for (int i = 1; i <= 8; i++) {
                ets[i - 1].setText(String.valueOf(existente.valorTramo(i)));
            }
        } else {
            etFecha.setText(Fechas.hoyISO());
            int[] hist = Config.TRAMOS_VALORES_HISTORICOS;
            for (int i = 0; i < 8; i++) {
                ets[i].setText(String.valueOf(hist[i]));
            }
        }

        // Date picker para fecha_desde (sin tipeo libre)
        etFecha.setOnClickListener(b -> abrirDatePicker(etFecha));

        new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null
                        ? ("Nueva versión — " + modalidadActiva.getLabel())
                        : ("Editar versión — " + modalidadActiva.getLabel()))
                .setView(v)
                .setPositiveButton(existente == null ? "Crear" : "Guardar", (d, w) -> {
                    String fecha = Texts.s(etFecha.getText());
                    // El campo solo se llena via DatePicker (focusable=false) asi
                    // que el formato yyyy-MM-dd esta garantizado. Solo chequear vacio.
                    if (fecha.isEmpty()) {
                        Toast.makeText(this, "Elegí una fecha", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int[] vals = new int[8];
                    for (int i = 0; i < 8; i++) {
                        Integer x = Parsers.parseIntOrNull(Texts.s(ets[i].getText()));
                        if (x == null || x < 0) {
                            Toast.makeText(this,
                                    "Todos los tramos deben ser números >= 0",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        vals[i] = x;
                    }

                    AppDatabase.ioExecutor.execute(() -> guardar(existente, fecha, vals));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Persistencia: alta o edicion. En alta verifica fecha duplicada. */
    private void guardar(TramosVersion existente, String fecha, int[] vals) {
        if (existente == null) {
            // Alta: rechazar si ya existe una version con esa fecha_desde
            // para la modalidad (semanticamente ambigua).
            List<TramosVersion> previas = dao.listByModalidad(modalidadActiva.name());
            for (TramosVersion p : previas) {
                if (p.fechaDesde.equals(fecha)) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Ya existe una versión con fecha " + fecha + ". " +
                                    "Editala o elegí otra fecha.",
                            Toast.LENGTH_LONG).show());
                    return;
                }
            }
            TramosVersion nueva = new TramosVersion(
                    modalidadActiva.name(), fecha,
                    vals[0], vals[1], vals[2], vals[3],
                    vals[4], vals[5], vals[6], vals[7]);
            dao.insert(nueva);
        } else {
            existente.fechaDesde = fecha;
            existente.t1 = vals[0]; existente.t2 = vals[1];
            existente.t3 = vals[2]; existente.t4 = vals[3];
            existente.t5 = vals[4]; existente.t6 = vals[5];
            existente.t7 = vals[6]; existente.t8 = vals[7];
            dao.update(existente);
        }
        TramosResolver.invalidate();
        runOnUiThread(() -> {
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
            cargarLista();
        });
    }

    private void mostrarMenuEliminar(TramosVersion v) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar versión")
                .setMessage("¿Eliminar la versión desde " + v.fechaDesde + " de " +
                        modalidadActiva.getLabel() + "?\n\nLos registros que dependían " +
                        "de esta versión pasarán a usar la versión anterior vigente.")
                .setPositiveButton("Eliminar", (d, w) -> AppDatabase.ioExecutor.execute(() -> {
                    int count = dao.countByModalidad(modalidadActiva.name());
                    if (count <= 1) {
                        runOnUiThread(() -> Toast.makeText(this,
                                "No se puede eliminar la única versión. Editá los valores en su lugar.",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    dao.delete(v);
                    TramosResolver.invalidate();
                    runOnUiThread(this::cargarLista);
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrirDatePicker(TextInputEditText target) {
        Calendar c = Calendar.getInstance();
        String actual = Texts.s(target.getText());
        try {
            if (!actual.isEmpty()) c.setTime(Fechas.parseISO(actual));
        } catch (Exception ignored) {}

        new DatePickerDialog(this, (picker, y, m, d) -> {
            String sel = String.format(Locale.US, "%04d-%02d-%02d", y, (m + 1), d);
            target.setText(sel);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
