package cl.rdrp.planilla_shopper.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Local;
import cl.rdrp.planilla_shopper.data.LocalDao;
import cl.rdrp.planilla_shopper.util.Texts;

/**
 * Pantalla CRUD de locales / salas (N5).
 *
 * Funcionalidad:
 *   - Lista todos los locales activos (toggle para ver tambien los archivados).
 *   - FAB para agregar uno nuevo (popup con codigo + alias).
 *   - Tap sobre un item -> editar codigo/alias.
 *   - Long press -> menu de acciones (archivar / desarchivar).
 *
 * No expone borrado duro: archivar es la operacion "destructiva" porque
 * preserva los pedidos historicos que ya referenciaban ese codigo.
 */
public class LocalesActivity extends AppCompatActivity {

    private LocalDao dao;
    private LocalesAdapter adapter;
    private View tvEmpty;
    private MaterialSwitch swMostrarArchivados;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locales);

        setTitle("Salas / locales");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dao = AppDatabase.get(this).localDao();

        tvEmpty = findViewById(R.id.tvEmpty);
        swMostrarArchivados = findViewById(R.id.swMostrarArchivados);

        RecyclerView rv = findViewById(R.id.rvLocales);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LocalesAdapter(new LocalesAdapter.OnLocalAction() {
            @Override public void onTap(Local l) { mostrarFormulario(l); }
            @Override public void onLongPress(Local l) { mostrarMenuAcciones(l); }
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAgregar);
        fab.setOnClickListener(v -> mostrarFormulario(null));

        swMostrarArchivados.setOnCheckedChangeListener((b, isChecked) -> cargarLista());

        cargarLista();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarLista();
    }

    private void cargarLista() {
        final boolean incluirArchivados = swMostrarArchivados.isChecked();
        AppDatabase.ioExecutor.execute(() -> {
            List<Local> items = incluirArchivados ? dao.listAll() : dao.listActivos();
            runOnUiThread(() -> {
                adapter.submit(items);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    /**
     * Muestra el dialogo de formulario. Si local == null es alta nueva; si
     * viene con valores, edicion. La diferencia esta en que en edicion se
     * pre-llenan los campos y al guardar se llama a update() en lugar de
     * insert().
     */
    private void mostrarFormulario(Local existente) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_local_form, null);
        TextInputEditText etCodigo = v.findViewById(R.id.etCodigo);
        TextInputEditText etAlias  = v.findViewById(R.id.etAlias);

        if (existente != null) {
            etCodigo.setText(existente.codigo);
            etAlias.setText(existente.alias);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null ? "Agregar local" : "Editar local")
                .setView(v)
                .setPositiveButton(existente == null ? "Agregar" : "Guardar", (d, w) -> {
                    String codigo = Texts.s(etCodigo.getText());
                    String alias  = Texts.s(etAlias.getText());

                    if (codigo.isEmpty()) {
                        Toast.makeText(this, "Falta el código del local", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AppDatabase.ioExecutor.execute(() -> {
                        if (existente == null) {
                            // Alta: verificar que no exista ya el codigo
                            Local previo = dao.findByCodigo(codigo);
                            if (previo != null) {
                                runOnUiThread(() -> Toast.makeText(this,
                                        "Ya existe un local con código " + codigo,
                                        Toast.LENGTH_SHORT).show());
                                return;
                            }
                            Local nuevo = new Local(codigo, alias.isEmpty() ? null : alias);
                            dao.insert(nuevo);
                        } else {
                            // Edicion: si cambiaron codigo, verificar que no choque
                            if (!existente.codigo.equals(codigo)) {
                                Local previo = dao.findByCodigo(codigo);
                                if (previo != null && previo.id != existente.id) {
                                    runOnUiThread(() -> Toast.makeText(this,
                                            "Ya existe un local con código " + codigo,
                                            Toast.LENGTH_SHORT).show());
                                    return;
                                }
                            }
                            existente.codigo = codigo;
                            existente.alias  = alias.isEmpty() ? null : alias;
                            dao.update(existente);
                        }
                        runOnUiThread(this::cargarLista);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarMenuAcciones(Local l) {
        String accionToggle = l.activo ? "Archivar" : "Reactivar";
        new MaterialAlertDialogBuilder(this)
                .setTitle(l.display())
                .setItems(new CharSequence[]{ "Editar", accionToggle }, (d, which) -> {
                    if (which == 0) {
                        mostrarFormulario(l);
                    } else {
                        AppDatabase.ioExecutor.execute(() -> {
                            if (l.activo) {
                                dao.archivar(l.id);
                            } else {
                                dao.desarchivar(l.id);
                            }
                            runOnUiThread(this::cargarLista);
                        });
                    }
                })
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
