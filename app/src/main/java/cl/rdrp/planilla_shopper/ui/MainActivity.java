package cl.rdrp.planilla_shopper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Calendar;
import java.util.Locale;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.databinding.ActivityMainBinding;
import cl.rdrp.planilla_shopper.util.Fechas;
import cl.rdrp.planilla_shopper.util.Parsers;
import cl.rdrp.planilla_shopper.util.Prefs;
import cl.rdrp.planilla_shopper.util.Texts;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding vb;
    private cl.rdrp.planilla_shopper.ui.RegistroAdapter adapter;

    private BonoAdapter bonoAdapter;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());


        // Color de status bar consistente con el toolbar
        getWindow().setStatusBarColor(getColor(R.color.blue_primary));

        // Asegurar iconos claros sobre el azul (en APIs nuevas)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            final android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }

        // Toolbar como ActionBar + título forzado en blanco
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setBackgroundTintList(
                androidx.core.content.ContextCompat.getColorStateList(this, R.color.blue_primary)
        );

        int white = androidx.core.content.ContextCompat.getColor(this, android.R.color.white);
        toolbar.setTitleTextColor(white);
        toolbar.setSubtitleTextColor(white);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Planilla Shopper");
        }

        androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawerLayout);
        com.google.android.material.navigation.NavigationView nav = findViewById(R.id.navView);

        androidx.appcompat.app.ActionBarDrawerToggle toggle =
                new androidx.appcompat.app.ActionBarDrawerToggle(
                        this, drawer, toolbar,
                        R.string.navigation_drawer_open,
                        R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // 2) Clicks del drawer

        nav.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_monthly) {
                startActivity(new Intent(this, MonthlySummaryActivity.class));
            } else if (id == R.id.nav_contact) {
                // abre email listo para enviar
                Intent email = new Intent(Intent.ACTION_SEND);
                email.setType("message/rfc822");
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{"rogerdavid.rd@gmail.com"});
                email.putExtra(Intent.EXTRA_SUBJECT, "Personalizar mi app Planilla Shopper");
                email.putExtra(Intent.EXTRA_TEXT,
                        "Hola Roger, quiero agregar funcionalidades a mi app. Detalle:\n\n• ...\n");
                try { startActivity(Intent.createChooser(email, "Contactar por correo")); }
                catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "No hay cliente de correo instalado", Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.nav_calculo) {
                startActivity(new Intent(this, ParametrosCalculosActivity.class));
            } else if (id == R.id.nav_modalidad) {
                mostrarDialogoCambiarModalidad();
            } else if (id == R.id.nav_backup) {
                startActivity(new Intent(this, BackupActivity.class));
            }else if (id == R.id.nav_bencina) {
                startActivity(new Intent(this, BencinaActivity.class));
            }else if (id == R.id.nav_historial_bencina) {
                startActivity(new Intent(this, HistorialBencinaActivity.class));
            } else if (id == R.id.nav_vista_general) {
            startActivity(new Intent(this, VistaGeneralActivity.class));
        }
            drawer.closeDrawers();
            return true;
        });




        vb.etKm.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        vb.etSg.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        vb.etVentana.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));

        // cargar local guardado si existe
        String saveLocal = cl.rdrp.planilla_shopper.util.Prefs.getLocal(this);

        vb.btnGuardar.setOnClickListener(v -> guardar());
        vb.btnLimpiar.setOnClickListener(v -> limpiar());
        vb.btnDashboard.setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)));

        // === Calendario como el del Dashboard ===
        // Campo no editable y con selector DatePickerDialog; formato yyyy-MM-dd
        vb.etFecha.setInputType(android.text.InputType.TYPE_NULL);
        vb.etFecha.setFocusable(false);
        vb.etFecha.setOnClickListener(v -> mostrarDatePicker());

        // autorellenar hoy si está vacío (yyyy-MM-dd)
        if (vb.etFecha.getText() == null || vb.etFecha.getText().toString().isEmpty() ){
            vb.etFecha.setText(Fechas.hoyISO());
        }

        // Lista de registros del día
        adapter = new cl.rdrp.planilla_shopper.ui.RegistroAdapter(new cl.rdrp.planilla_shopper.ui.RegistroAdapter.OnEdit() {
            @Override public void onUpdate(cl.rdrp.planilla_shopper.data.Registro r) { mostrarDialogoEditar(r); }
            @Override public void onDelete(cl.rdrp.planilla_shopper.data.Registro r) { eliminarRegistro(r); }
        });
        vb.rvRegistros.setAdapter(adapter);
        vb.rvRegistros.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        cargarListaDelDia();

        // Lista de bonos del día
        bonoAdapter = new BonoAdapter(bono -> {
            // diálogo de confirmación
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Eliminar bono")
                    .setMessage(
                            "¿Eliminar este bono de $" + bono.monto +
                                    (bono.descripcion != null && !bono.descripcion.trim().isEmpty()
                                            ? " (" + bono.descripcion + ")"
                                            : "") + "?"
                    )
                    .setPositiveButton("Eliminar", (d, w) -> {
                        AppDatabase.ioExecutor.execute(() -> {
                                    cl.rdrp.planilla_shopper.data.AppDatabase
                                            .get(this)
                                            .bonoDao()
                                            .delete(bono);

                                    runOnUiThread(this::cargarListaDelDia);
                                });
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        vb.rvBonos.setAdapter(bonoAdapter);
        vb.rvBonos.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this)
        );

        // Botón "Agregar bono"
                vb.btnAgregarBono.setOnClickListener(v -> mostrarDialogoBono());

        // Popup de bienvenida si nunca eligio modalidad (N1)
        if (!Prefs.hasModalidad(this)) {
            mostrarPopupBienvenidaModalidad();
        }

        // Aplicar visibilidad de campos segun modalidad (N2)
        aplicarVisibilidadPorModalidad();
    }

    /**
     * Oculta/muestra los campos SKU y KM segun la modalidad actual (N2).
     * Solo afecta al formulario de NUEVO registro; el dialogo de edicion
     * sigue mostrando todos los campos para no perder funcionalidad al
     * editar registros viejos hechos en otra modalidad.
     *
     *   SHOPPER -> ambos visibles, weight 1 cada uno (estado original).
     *   PICKER  -> oculta KM. SKU ocupa la fila completa (weight 2).
     *   DRIVER  -> oculta SKU. KM ocupa la fila completa (weight 2).
     */
    private void aplicarVisibilidadPorModalidad() {
        Modalidad m = Prefs.getModalidad(this);

        com.google.android.material.textfield.TextInputLayout tilSku =
                findViewById(R.id.tilSku);
        com.google.android.material.textfield.TextInputLayout tilKm =
                findViewById(R.id.tilKm);

        // Reset a default (ambos visibles, weight 1) y desde ahi ajustar.
        setRowVisibility(tilSku, true,  1f);
        setRowVisibility(tilKm,  true,  1f);

        switch (m) {
            case PICKER:
                // Sin KM: el campo SKU ocupa ancho completo.
                setRowVisibility(tilKm,  false, 1f);
                setRowVisibility(tilSku, true,  2f);
                break;
            case DRIVER:
                // Sin SKU: el campo KM ocupa ancho completo.
                setRowVisibility(tilSku, false, 1f);
                setRowVisibility(tilKm,  true,  2f);
                break;
            case SHOPPER:
            default:
                // Ambos visibles con weight 1 (ya quedo seteado arriba).
                break;
        }
    }

    /** Helper que ajusta visibility + layout_weight de un hijo de LinearLayout horizontal. */
    private static void setRowVisibility(android.view.View v, boolean visible, float weight) {
        if (v == null) return;
        v.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
            ((android.widget.LinearLayout.LayoutParams) lp).weight = weight;
            v.setLayoutParams(lp);
        }
    }

    /**
     * Popup no-cancelable que pide elegir modalidad la primera vez que
     * el usuario abre la app. Solo se muestra una vez (despues hasModalidad
     * retorna true).
     */
    private void mostrarPopupBienvenidaModalidad() {
        mostrarDialogoModalidad(
                "¡Bienvenido! Elige tu modalidad",
                "Para empezar, indica cuál es tu modalidad de trabajo. " +
                        "Puedes cambiarla después desde el menú lateral.",
                null,           // sin modalidad actual (es la primera vez)
                false           // no se puede cancelar
        );
    }

    /**
     * Dialogo para cambiar de modalidad desde el drawer. Muestra la actual
     * seleccionada y permite cancelar sin cambios.
     */
    private void mostrarDialogoCambiarModalidad() {
        Modalidad actual = Prefs.getModalidad(this);
        mostrarDialogoModalidad(
                "Modalidad de trabajo",
                "Modalidad actual: " + actual.getLabel() + ". Tocá una opción para cambiar.",
                actual,         // la actual se destaca visualmente
                true            // permite cancelar
        );
    }

    /**
     * Helper unico para los dos popups de modalidad (bienvenida y cambio).
     * Infla dialog_modalidad.xml con 3 MaterialCardView clickeables y
     * destaca la modalidad actual (si se pasa) cambiandole el tinte.
     */
    private void mostrarDialogoModalidad(String titulo,
                                         String intro,
                                         Modalidad actual,
                                         boolean cancelable) {
        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_modalidad, null);

        ((android.widget.TextView) view.findViewById(R.id.tvDialogModalidadIntro))
                .setText(intro);

        com.google.android.material.dialog.MaterialAlertDialogBuilder b =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(titulo)
                        .setView(view)
                        .setCancelable(cancelable);
        if (cancelable) b.setNegativeButton("Cancelar", null);
        androidx.appcompat.app.AlertDialog dialog = b.create();

        // Mapeo card -> modalidad para el handler
        java.util.Map<Integer, Modalidad> cards = new java.util.HashMap<>();
        cards.put(R.id.cardShopper, Modalidad.SHOPPER);
        cards.put(R.id.cardPicker,  Modalidad.PICKER);
        cards.put(R.id.cardDriver,  Modalidad.DRIVER);

        for (java.util.Map.Entry<Integer, Modalidad> e : cards.entrySet()) {
            com.google.android.material.card.MaterialCardView card = view.findViewById(e.getKey());
            Modalidad m = e.getValue();

            // Destacar la actual: card con tinte primaryContainer
            if (actual != null && m == actual) {
                card.setCardBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(this, R.color.blue_primaryContainer));
            }

            card.setOnClickListener(v -> {
                if (actual == null || m != actual) {
                    Prefs.setModalidad(this, m);
                    Toast.makeText(this,
                            "Modalidad: " + m.getLabel(),
                            Toast.LENGTH_SHORT).show();
                    // N2: aplicar la nueva visibilidad de campos inmediatamente
                    aplicarVisibilidadPorModalidad();
                }
                dialog.dismiss();
            });
        }

        dialog.show();
    }



    // === DatePicker estilo Dashboard ===
    private void mostrarDatePicker() {
        Calendar cal = Calendar.getInstance();
        String actual = Texts.s(vb.etFecha.getText());
        try {
            if (!actual.isEmpty()) cal.setTime(Fechas.parseISO(actual));
        } catch (Exception ignored) {}

        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        new android.app.DatePickerDialog(this, (picker, yy, mm, dd) -> {
            String sel = String.format(Locale.US, "%04d-%02d-%02d", yy, (mm + 1), dd);
            vb.etFecha.setText(sel);
            cargarListaDelDia(); // refresca lista con la nueva fecha
        }, y, m, d).show();
    }

    private void cargarListaDelDia() {
        String iso = Texts.s(vb.etFecha.getText());      // yyyy-MM-dd
        String legacy = Fechas.toLegacy(iso);            // dd/MM/yyyy compat

        AppDatabase.ioExecutor.execute(() -> {
            // registros normales
            java.util.List<cl.rdrp.planilla_shopper.data.Registro> items =
                    cl.rdrp.planilla_shopper.data.AppDatabase.get(this)
                            .registroDao()
                            .listByFechaCompat(iso, legacy);

            // bonos del día
            java.util.List<cl.rdrp.planilla_shopper.data.BonoExtra> bonos =
                    cl.rdrp.planilla_shopper.data.AppDatabase.get(this)
                            .bonoDao()
                            .listByFecha(iso);

            runOnUiThread(() -> {
                adapter.submit(items);
                bonoAdapter.submit(bonos);
            });
        });
    }

    private void mostrarDialogoEditar(cl.rdrp.planilla_shopper.data.Registro r) {
        android.view.LayoutInflater inf = android.view.LayoutInflater.from(this);
        android.view.View view = inf.inflate(R.layout.dialog_editar_registro, null);
        com.google.android.material.textfield.TextInputEditText etFecha    = view.findViewById(R.id.etFechaEdit);
        com.google.android.material.textfield.TextInputEditText etSku      = view.findViewById(R.id.etSkuEdit);
        com.google.android.material.textfield.TextInputEditText etKm       = view.findViewById(R.id.etKmEdit);
        com.google.android.material.textfield.TextInputEditText etVentana  = view.findViewById(R.id.etVentanaEdit);
        com.google.android.material.textfield.TextInputEditText etSg       = view.findViewById(R.id.etSgEdit);
        com.google.android.material.textfield.TextInputEditText etCant     = view.findViewById(R.id.etCantEdit);

        etFecha.setText(r.fecha);
        etSku.setText(r.sku);
        etKm.setText(String.valueOf(r.km));
        etVentana.setText(String.valueOf(r.ventana));
        etSg.setText(String.valueOf(r.sg));
        etCant.setText(String.valueOf(r.cant));

        // Hacer que el campo de fecha abra DatePicker y no teclado
        etFecha.setInputType(android.text.InputType.TYPE_NULL);
        etFecha.setFocusable(false);
        etFecha.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            try {
                String actual = Texts.s(etFecha.getText());
                if (!actual.isEmpty()) cal.setTime(Fechas.parseISO(actual));
            } catch (Exception ignored) {}

            int y = cal.get(java.util.Calendar.YEAR);
            int m = cal.get(java.util.Calendar.MONTH);
            int d = cal.get(java.util.Calendar.DAY_OF_MONTH);

            new android.app.DatePickerDialog(this, (picker, yy, mm, dd) -> {
                String sel = String.format(java.util.Locale.US, "%04d-%02d-%02d", yy, (mm + 1), dd);
                etFecha.setText(sel);
            }, y, m, d).show();
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Editar registro")
                .setView(view)
                .setPositiveButton("Guardar", (d, w) -> {
                    String fechaTxt = Texts.s(etFecha.getText());
                    String skuTxt = Texts.s(etSku.getText());
                    String kmTxt  = Texts.s(etKm.getText());
                    String venTxt = Texts.s(etVentana.getText());
                    String sgTxt  = Texts.s(etSg.getText());

                    if ( fechaTxt.isEmpty() ||skuTxt.isEmpty() || kmTxt.isEmpty() || venTxt.isEmpty() || sgTxt.isEmpty()) {
                        Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Double  kmD  = Parsers.parseDoubleOrNull(Texts.s(etKm.getText()));
                    Integer venI = Parsers.parseIntOrNull(Texts.s(etVentana.getText()));
                    Long    sgL  = Parsers.parseLongOrNull(Texts.s(etSg.getText()));
                    String  skuN = Texts.s(etSku.getText());
                    Integer cantI = Parsers.parseIntOrNull(Texts.s(etCant.getText()));

                    if (kmD == null || venI == null || sgL == null || cantI == null || skuN.isEmpty()) {
                        Toast.makeText(this, "Completa y usa valores válidos", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double km = kmD;
                    int ventana = venI;
                    long sg = sgL;
                    int cant = cantI;


                    AppDatabase.ioExecutor.execute(() -> {
                        AppDatabase.get(this).registroDao().update(r.id, fechaTxt, skuN, km, sg, ventana, cant);
                        runOnUiThread(this::cargarListaDelDia);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminarRegistro(cl.rdrp.planilla_shopper.data.Registro r) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar")
                .setMessage("¿Eliminar este registro?")
                .setPositiveButton("Sí", (d, w) -> AppDatabase.ioExecutor.execute(() -> {
                    cl.rdrp.planilla_shopper.data.AppDatabase.get(this).registroDao().delete(r.id);
                    runOnUiThread(this::cargarListaDelDia);
                }))
                .setNegativeButton("No", null)
                .show();
    }

    private void guardar() {
        String fecha    = Texts.s(vb.etFecha.getText()); // ahora guarda yyyy-MM-dd
        String local    = Texts.s(vb.etLocal.getText());
        String sku      = Texts.s(vb.etSku.getText());
        String kmS      = Texts.s(vb.etKm.getText());
        String sgS      = Texts.s(vb.etSg.getText());
        String ventanaS = Texts.s(vb.etVentana.getText());
        String cantS    = Texts.s(vb.etCant.getText());

        // N2: si la modalidad oculta un campo, asignar default para que el
        // resto del flujo (validacion y guardado) trate al campo como si
        // tuviera valor cero. Esto permite a PICKER/DRIVER guardar sin
        // tener que llenar lo que no aplica.
        Modalidad modalidad = Prefs.getModalidad(this);
        if (modalidad == Modalidad.PICKER) {
            kmS = "0";  // Picker no usa KM
        } else if (modalidad == Modalidad.DRIVER) {
            sku = "0";  // Driver no usa SKU
        }

        // Guarda LOCAL predeterminado si no existe
        if (!Prefs.hasLocal(this)) {
            Prefs.setLocal(this, local);
        }

        // Campos obligatorios
        if (fecha.isEmpty() || local.isEmpty() || sku.isEmpty() || kmS.isEmpty() || sgS.isEmpty() || ventanaS.isEmpty() || cantS.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parseo robusto
        Integer skuQtyI = Parsers.parseIntOrNull(sku);
        Double  kmD     = Parsers.parseDoubleOrNull(kmS);
        Long    sgL     = Parsers.parseLongOrNull(sgS);
        Integer venI    = Parsers.parseIntOrNull(ventanaS);
        Integer cantI   = Parsers.parseIntOrNull(cantS);

        if (skuQtyI == null || kmD == null || sgL == null || venI == null || cantI == null) {
            Toast.makeText(this, "SKU, KM, SG, Ventana y Cant deben ser numéricos", Toast.LENGTH_SHORT).show();
            return;
        }

        int    skuQty = skuQtyI;
        double km     = kmD;
        long   sg     = sgL;
        int    ventana= venI;
        int    cant   = cantI;

        if (km < 0)      { Toast.makeText(this, "KM no puede ser negativo", Toast.LENGTH_SHORT).show(); return; }
        if (sg < 0)      { Toast.makeText(this, "SG no puede ser negativo", Toast.LENGTH_SHORT).show(); return; }
        if (ventana < 0) { Toast.makeText(this, "Ventana no puede ser negativa", Toast.LENGTH_SHORT).show(); return; }

        Registro r = new Registro(fecha, local, sku, km, sg, ventana, cant);
        // N4: estampar la modalidad activa al momento de crear. Asi el
        // registro recuerda con que parametros fue creado, aunque despues
        // el usuario cambie su modalidad activa.
        r.modalidad = Prefs.getModalidad(this).name();

        AppDatabase.ioExecutor.execute(() -> {
            AppDatabase.get(this).registroDao().insertIgnore(r);
            runOnUiThread(() -> {
                Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
                cargarListaDelDia();
                limpiar();
            });
        });
    }

    private void limpiar() {
        vb.etSku.setText("");
        vb.etKm.setText("");
        vb.etSg.setText("");
        vb.etVentana.setText("");
        vb.etCant.setText("");
    }

    private void mostrarDialogoBono() {
        android.view.LayoutInflater inf = android.view.LayoutInflater.from(this);
        android.view.View view = inf.inflate(R.layout.dialog_bono_extra, null);

        android.widget.EditText etSg   = view.findViewById(R.id.etBonoSg);
        android.widget.EditText etDesc = view.findViewById(R.id.etBonoDescripcion);
        android.widget.EditText etMonto= view.findViewById(R.id.etBonoMonto);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Agregar bono")
                .setView(view)
                .setPositiveButton("Guardar", (d, w) -> {
                    String fecha = Texts.s(vb.etFecha.getText()); // día actual
                    String sg    = Texts.s(etSg.getText());
                    String desc  = Texts.s(etDesc.getText());
                    String montoS= Texts.s(etMonto.getText());

                    if (fecha.isEmpty() || montoS.isEmpty()) {
                        android.widget.Toast.makeText(this,
                                "Ingresa al menos el monto", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer montoI = Parsers.parseIntOrNull(montoS);
                    if (montoI == null) {
                        android.widget.Toast.makeText(this,
                                "Monto inválido", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int monto = montoI;

                    cl.rdrp.planilla_shopper.data.BonoExtra b =
                            new cl.rdrp.planilla_shopper.data.BonoExtra();
                    b.fecha = fecha;
                    b.sg = sg;
                    b.descripcion = desc;
                    b.monto = monto;

                    AppDatabase.ioExecutor.execute(() -> {
                                cl.rdrp.planilla_shopper.data.AppDatabase
                                        .get(this).bonoDao().insert(b);

                                runOnUiThread(() -> {
                                    android.widget.Toast.makeText(this,
                                            "Bono guardado", android.widget.Toast.LENGTH_SHORT).show();
                                    cargarListaDelDia();  // refrescar listas
                                });
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }


}





