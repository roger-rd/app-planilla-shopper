package cl.rdrp.planilla_shopper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
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
import cl.rdrp.planilla_shopper.util.CalculoNuevoRegistro;
import cl.rdrp.planilla_shopper.util.Config;
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
            } else if (id == R.id.nav_bonos_km) {
                startActivity(new Intent(this, BonosActivity.class));
            } else if (id == R.id.nav_tramos) {
                startActivity(new Intent(this, TramosActivity.class));
            } else if (id == R.id.nav_modalidad) {
                mostrarDialogoCambiarModalidad();
            } else if (id == R.id.nav_sala) {
                // N5: cambiar la sala/local activo. Usa el mismo popup que la
                // bienvenida pero cancelable, y no encadena al de modalidad.
                LocalPickerDialog.mostrar(this, "Sala de trabajo", true, l -> {
                    actualizarBadgeSalaActiva();
                    Toast.makeText(this,
                            "Sala activa: " + l.display(),
                            Toast.LENGTH_SHORT).show();
                });
            } else if (id == R.id.nav_locales) {
                // N5: pantalla CRUD del catalogo de salas.
                startActivity(new Intent(this, LocalesActivity.class));
            } else if (id == R.id.nav_qr_pesable) {
                startActivity(new Intent(this, QrPesableActivity.class));
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
        vb.etKmRetorno.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        vb.etSg.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        vb.etVentana.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));

        // N5: el campo Local ya no esta en el form. r.local sale de
        // Prefs.getLocalActivo (seteado via popup de bienvenida o drawer).

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

        // Popups de bienvenida encadenados (N5 + N1):
        //   1ro: local activo (si no tiene)
        //   2do: modalidad (si no tiene)
        // Se encadenan para que no aparezcan al mismo tiempo. Si el usuario
        // ya tiene ambos seteados, no se muestra nada.
        mostrarSetupInicial();

        // Aplicar visibilidad de campos segun modalidad (N2)
        aplicarVisibilidadPorModalidad();

        // N5: refrescar el badge de sala activa
        actualizarBadgeSalaActiva();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // N5: si el usuario cambio de sala desde el drawer o desde Locales,
        // refrescar el badge al volver a Main.
        actualizarBadgeSalaActiva();
        // Onboarding: si el usuario volvio de LocalesActivity habiendo creado
        // la primera sala (caso vacio del popup inicial), o si restauro un
        // backup parcial, reanudar el flow. El metodo tiene guard temprano,
        // asi que es no-op si ya hay sala + modalidad.
        mostrarSetupInicial();
    }

    /**
     * Pinta el badge "Sala: {codigo} — {alias}" segun el local activo. Si
     * existe alias en el catalogo, se muestra. Si no, solo el codigo.
     * Si no hay local activo (caso borde, no deberia pasar despues del
     * popup de bienvenida), muestra "Sala: no seleccionada".
     */
    private void actualizarBadgeSalaActiva() {
        TextView tvSala = findViewById(R.id.tvSalaActiva);
        if (tvSala == null) return;

        String codigo = Prefs.getLocalActivo(this);
        if (codigo == null || codigo.isEmpty()) {
            tvSala.setText("Sala: no seleccionada");
            return;
        }

        // Buscar el alias en el catalogo, async para no bloquear el UI thread.
        AppDatabase.ioExecutor.execute(() -> {
            cl.rdrp.planilla_shopper.data.Local l =
                    AppDatabase.get(this).localDao().findByCodigo(codigo);
            String display = (l != null) ? l.display() : codigo;
            runOnUiThread(() -> tvSala.setText("Sala: " + display));
        });
    }

    /**
     * Flag que indica que el dialog de setup inicial esta actualmente
     * desplegado. Sirve para:
     *   - Evitar que onResume re-dispare el dialog en bucle mientras ya
     *     esta visible.
     *   - Distinguir "elegir sala+modalidad porque arranco la app sin
     *     configurar" vs "cambio normal desde el drawer" — solo el primer
     *     caso muestra el snackbar final.
     */
    private boolean flujoInicialEnCurso = false;

    /**
     * Setup inicial unificado: UN solo dialog que configura sala + modalidad
     * en una sola pantalla. Reemplaza el flujo encadenado anterior que
     * mandaba al usuario a LocalesActivity en el medio y obligaba a cerrar
     * y abrir la app para terminar.
     *
     * Reglas:
     *   - Idempotente: si ya hay sala + modalidad, no hace nada (seguro
     *     llamarlo desde onResume libremente).
     *   - No-cancelable: la unica salida es el boton "Comenzar" tras
     *     validar las dos secciones.
     *   - No persiste nada hasta que el usuario toca "Comenzar": si elige
     *     "+ Crear nueva sala", la sala se INSERTA en DB solo al confirmar.
     *
     * Layout: res/layout/dialog_setup_inicial.xml.
     */
    private void mostrarSetupInicial() {
        // Idempotente: si ya esta todo configurado, salir.
        if (Prefs.hasLocalActivo(this) && Prefs.hasModalidad(this)) {
            flujoInicialEnCurso = false;
            return;
        }
        // Evitar reentrada si el dialog ya esta desplegado.
        if (flujoInicialEnCurso) return;
        flujoInicialEnCurso = true;

        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_setup_inicial, null);

        // Referencias UI
        final com.google.android.material.textfield.TextInputLayout tilSelectorSala =
                view.findViewById(R.id.tilSelectorSala);
        final android.widget.AutoCompleteTextView actvSala =
                view.findViewById(R.id.actvSala);
        final android.widget.LinearLayout contNuevaSala =
                view.findViewById(R.id.contNuevaSala);
        final com.google.android.material.textfield.TextInputLayout tilCodigoNueva =
                view.findViewById(R.id.tilCodigoNueva);
        final com.google.android.material.textfield.TextInputEditText etCodigoNueva =
                view.findViewById(R.id.etCodigoNueva);
        final com.google.android.material.textfield.TextInputEditText etAliasNueva =
                view.findViewById(R.id.etAliasNueva);

        // Estado mutable de la seleccion (final arrays porque se usan en
        // lambdas y necesitan ser efectivamente "mutables").
        final String[] codigoSalaElegida = { null };  // codigo elegido del dropdown
        final boolean[] crearNuevaSala = { false };   // true si el usuario eligio "+ Crear nueva"
        final Modalidad[] modalidadElegida = { null };

        // Cargar salas async y poblar el dropdown. Si el catalogo esta
        // vacio, forzar el modo "crear nueva" sin mostrar el selector.
        AppDatabase.ioExecutor.execute(() -> {
            java.util.List<cl.rdrp.planilla_shopper.data.Local> salas =
                    AppDatabase.get(this).localDao().listActivos();
            runOnUiThread(() -> {
                if (salas == null || salas.isEmpty()) {
                    // Sin salas: forzar modo creacion sin opcion de elegir.
                    tilSelectorSala.setVisibility(android.view.View.GONE);
                    contNuevaSala.setVisibility(android.view.View.VISIBLE);
                    crearNuevaSala[0] = true;
                } else {
                    // Hay salas: armar lista display + opcion "+ Crear nueva"
                    // al final. Cuando el usuario elige esta ultima, se
                    // muestran los inputs.
                    final String OPCION_NUEVA = "+ Crear nueva sala";
                    java.util.List<String> items = new java.util.ArrayList<>();
                    for (cl.rdrp.planilla_shopper.data.Local l : salas) {
                        items.add(l.display());
                    }
                    items.add(OPCION_NUEVA);
                    android.widget.ArrayAdapter<String> adapter =
                            new android.widget.ArrayAdapter<>(this,
                                    android.R.layout.simple_list_item_1, items);
                    actvSala.setAdapter(adapter);
                    actvSala.setOnItemClickListener((parent, v, pos, id) -> {
                        if (OPCION_NUEVA.equals(items.get(pos))) {
                            crearNuevaSala[0] = true;
                            codigoSalaElegida[0] = null;
                            contNuevaSala.setVisibility(android.view.View.VISIBLE);
                            etCodigoNueva.requestFocus();
                        } else {
                            crearNuevaSala[0] = false;
                            codigoSalaElegida[0] = salas.get(pos).codigo;
                            contNuevaSala.setVisibility(android.view.View.GONE);
                        }
                        tilSelectorSala.setError(null);
                    });
                }
            });
        });

        // Cards de modalidad — seleccion mutuamente exclusiva con tinte
        // primaryContainer en la elegida (mismo patron que dialog_modalidad).
        final java.util.Map<Integer, Modalidad> cardsModalidad = new java.util.LinkedHashMap<>();
        cardsModalidad.put(R.id.cardSetupShopper, Modalidad.SHOPPER);
        cardsModalidad.put(R.id.cardSetupPicker,  Modalidad.PICKER);
        cardsModalidad.put(R.id.cardSetupDriver,  Modalidad.DRIVER);

        final int colorTinte = androidx.core.content.ContextCompat.getColor(
                this, R.color.blue_primaryContainer);
        final int colorDefault = androidx.core.content.ContextCompat.getColor(
                this, R.color.blue_surface);

        for (java.util.Map.Entry<Integer, Modalidad> e : cardsModalidad.entrySet()) {
            com.google.android.material.card.MaterialCardView card =
                    view.findViewById(e.getKey());
            Modalidad m = e.getValue();
            card.setOnClickListener(v -> {
                modalidadElegida[0] = m;
                // Reset visual: todas vuelven al color default, solo la
                // elegida queda tintada.
                for (Integer id : cardsModalidad.keySet()) {
                    com.google.android.material.card.MaterialCardView c = view.findViewById(id);
                    c.setCardBackgroundColor(id == e.getKey() ? colorTinte : colorDefault);
                }
            });
        }

        // Construir el AlertDialog con boton positivo MANUAL (positiveButton
        // = null) para poder validar SIN cerrar el dialog si la validacion
        // falla. El setOnShowListener engancha el listener real al boton.
        final androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("¡Bienvenido a Planilla Shopper!")
                        .setView(view)
                        .setCancelable(false)
                        .setPositiveButton("Comenzar", null)
                        .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnComenzar = dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btnComenzar.setOnClickListener(v -> procesarSetupInicial(
                    dialog,
                    tilSelectorSala, tilCodigoNueva,
                    etCodigoNueva, etAliasNueva,
                    codigoSalaElegida, crearNuevaSala, modalidadElegida));
        });

        dialog.show();
    }

    /**
     * Valida + persiste el setup. Se invoca al tocar "Comenzar". Si algo
     * falta, deja el dialog abierto con error visible. Si todo OK:
     *   1) Inserta la sala nueva (si aplica) en DB.
     *   2) Setea Prefs (sala activa + modalidad).
     *   3) Cierra el dialog.
     *   4) Refresca UI (badge sala + visibilidad campos por modalidad).
     *   5) Snackbar de confirmacion.
     */
    private void procesarSetupInicial(
            androidx.appcompat.app.AlertDialog dialog,
            com.google.android.material.textfield.TextInputLayout tilSelectorSala,
            com.google.android.material.textfield.TextInputLayout tilCodigoNueva,
            com.google.android.material.textfield.TextInputEditText etCodigoNueva,
            com.google.android.material.textfield.TextInputEditText etAliasNueva,
            String[] codigoSalaElegida, boolean[] crearNuevaSala,
            Modalidad[] modalidadElegida) {

        // 1) Validar modalidad
        if (modalidadElegida[0] == null) {
            Toast.makeText(this,
                    "Elegí tu modalidad de trabajo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 2) Validar sala
        final String codigoFinal;
        final String aliasFinal;
        final boolean esNueva = crearNuevaSala[0];

        if (esNueva) {
            String cod = Texts.s(etCodigoNueva.getText());
            if (cod.isEmpty()) {
                tilCodigoNueva.setError("Ingresá el código de la sala");
                return;
            }
            tilCodigoNueva.setError(null);
            codigoFinal = cod;
            String alias = Texts.s(etAliasNueva.getText());
            aliasFinal = alias.isEmpty() ? null : alias;
        } else {
            if (codigoSalaElegida[0] == null) {
                tilSelectorSala.setError("Elegí una sala");
                return;
            }
            tilSelectorSala.setError(null);
            codigoFinal = codigoSalaElegida[0];
            aliasFinal = null;
        }

        // 3) Persistir async. Si era sala nueva, INSERT en DB primero (con
        // chequeo de no duplicar). Despues setear Prefs y refrescar UI.
        final Modalidad mod = modalidadElegida[0];

        AppDatabase.ioExecutor.execute(() -> {
            if (esNueva) {
                cl.rdrp.planilla_shopper.data.Local previo =
                        AppDatabase.get(this).localDao().findByCodigo(codigoFinal);
                if (previo == null) {
                    cl.rdrp.planilla_shopper.data.Local nuevo =
                            new cl.rdrp.planilla_shopper.data.Local(codigoFinal, aliasFinal);
                    AppDatabase.get(this).localDao().insert(nuevo);
                }
                // Si ya existia (rara vez: el dropdown deberia haberla
                // mostrado), tomamos su codigo igual.
            }
            runOnUiThread(() -> {
                Prefs.setLocalActivo(this, codigoFinal);
                Prefs.setModalidad(this, mod);
                dialog.dismiss();
                aplicarVisibilidadPorModalidad();
                actualizarBadgeSalaActiva();
                finalizarFlujoInicial();
            });
        });
    }

    /**
     * Cierre del flujo inicial: limpia el flag y muestra el snackbar de
     * confirmacion. Solo se ejecuta efectivamente si veniamos de un setup
     * inicial real (flag activo); si se llama desde cambio normal del
     * drawer es no-op.
     */
    private void finalizarFlujoInicial() {
        if (!flujoInicialEnCurso) return;
        flujoInicialEnCurso = false;
        try {
            com.google.android.material.snackbar.Snackbar.make(
                    vb.getRoot(),
                    "Configuración inicial completada",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show();
        } catch (Exception ignored) {
            // Defensivo: si por algun motivo no podemos mostrar el snackbar
            // (ej. la vista raiz no esta disponible), no rompemos el flujo.
        }
    }

    /**
     * Oculta/muestra los campos SKU y KM segun la modalidad actual (N2).
     * Solo afecta al formulario de NUEVO registro; el dialogo de edicion
     * sigue mostrando todos los campos para no perder funcionalidad al
     * editar registros viejos hechos en otra modalidad.
     *
     * Ventana siempre visible; Picker oculta la fila KM; Driver oculta SKU.
     */
    private void aplicarVisibilidadPorModalidad() {
        Modalidad m = Prefs.getModalidad(this);
        setRowVisibility(vb.tilSku, m != Modalidad.DRIVER, 1f);
        vb.rowKm.setVisibility(m == Modalidad.PICKER
                ? android.view.View.GONE : android.view.View.VISIBLE);
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

        if (CalculoNuevoRegistro.esNuevo(r)) {
            boolean usaKm = Modalidad.fromName(r.modalidad) != Modalidad.PICKER;
            boolean usaSku = Modalidad.fromName(r.modalidad) != Modalidad.DRIVER;
            com.google.android.material.textfield.TextInputEditText etEntrega =
                    view.findViewById(R.id.etKmEntregaEdit);
            com.google.android.material.textfield.TextInputEditText etRetorno =
                    view.findViewById(R.id.etKmRetornoEdit);
            view.findViewById(R.id.tilKmEdit).setVisibility(android.view.View.GONE);
            view.findViewById(R.id.tilCantEdit).setVisibility(android.view.View.GONE);
            view.findViewById(R.id.tilKmEntregaEdit).setVisibility(usaKm ? android.view.View.VISIBLE : android.view.View.GONE);
            view.findViewById(R.id.tilKmRetornoEdit).setVisibility(usaKm ? android.view.View.VISIBLE : android.view.View.GONE);
            etEntrega.setText(String.valueOf(r.km_entrega == null ? 0.0 : r.km_entrega));
            etRetorno.setText(String.valueOf(r.km_retorno == null ? 0.0 : r.km_retorno));
            // La fecha conserva las tarifas y reglas históricas del pedido.
            etFecha.setEnabled(false);
            etSku.setEnabled(usaSku);
            androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Editar registro")
                            .setView(view)
                            .setPositiveButton("Guardar", null)
                            .setNegativeButton("Cancelar", null)
                            .create();
            dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String retornoTxt = Texts.s(etRetorno.getText());
                        Double entrega = usaKm ? Parsers.parseDoubleOrNull(Texts.s(etEntrega.getText())) : 0.0;
                        Double retorno = usaKm ? Parsers.parseDoubleOrNull(retornoTxt.isEmpty() ? "0" : retornoTxt) : 0.0;
                        Integer sku = usaSku ? Parsers.parseIntOrNull(Texts.s(etSku.getText())) : 0;
                        Long sg = Parsers.parseLongOrNull(Texts.s(etSg.getText()));
                        Integer ventana = Parsers.parseIntOrNull(Texts.s(etVentana.getText()));
                        if (sku == null || entrega == null || retorno == null || sg == null || ventana == null) {
                            Toast.makeText(this, "SKU, KM, SG y Ventana deben ser numéricos", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!Double.isFinite(entrega) || !Double.isFinite(retorno)
                                || !Double.isFinite(entrega + retorno) || entrega < 0 || retorno < 0) {
                            Toast.makeText(this, "KM debe ser finito y no negativo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (sg < 0) { Toast.makeText(this, "SG no puede ser negativo", Toast.LENGTH_SHORT).show(); return; }
                        if (ventana < 0) { Toast.makeText(this, "Ventana no puede ser negativa", Toast.LENGTH_SHORT).show(); return; }
                        AppDatabase.ioExecutor.execute(() -> {
                            CalculoNuevoRegistro.editarSkuYKm(r, sku, entrega, retorno,
                                    cl.rdrp.planilla_shopper.util.TramosResolver::getValor);
                            AppDatabase.get(this).registroDao().updateCalculoNuevo(r.id,
                                    r.sku, r.tarifa_base, r.monto_base, r.monto_sku,
                                    r.km_entrega, r.km_retorno, r.km, r.monto_km,
                                    r.monto_bono_km, r.monto_bruto, r.bono_por_km, sg, ventana);
                            runOnUiThread(this::cargarListaDelDia);
                        });
                        dialog.dismiss();
                    }));
            dialog.show();
            return;
        }

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
        // N5: el local sale del local activo (Prefs), no del form. Si no
        // hay local activo, abrir el popup de seleccion en vez de guardar.
        String local    = Prefs.getLocalActivo(this);
        String sku      = Texts.s(vb.etSku.getText());
        String kmS      = Texts.s(vb.etKm.getText());
        String sgS      = Texts.s(vb.etSg.getText());
        String ventanaS = Texts.s(vb.etVentana.getText());
        String retornoS = Texts.s(vb.etKmRetorno.getText());
        if (retornoS.isEmpty()) retornoS = "0";

        if (local.isEmpty()) {
            Toast.makeText(this,
                    "Primero indicá tu sala desde el menú lateral",
                    Toast.LENGTH_LONG).show();
            LocalPickerDialog.mostrar(this, "Sala de trabajo", true, l -> actualizarBadgeSalaActiva());
            return;
        }

        // N2: si la modalidad oculta un campo, asignar default para que el
        // resto del flujo (validacion y guardado) trate al campo como si
        // tuviera valor cero. Esto permite a PICKER/DRIVER guardar sin
        // tener que llenar lo que no aplica.
        Modalidad modalidad = Prefs.getModalidad(this);
        if (modalidad == Modalidad.PICKER) {
            kmS = "0";  // Picker no usa KM
            retornoS = "0";
        } else if (modalidad == Modalidad.DRIVER) {
            sku = "0";  // Driver no usa SKU
        }

        // Campos obligatorios (el local ya esta validado arriba)
        if (fecha.isEmpty() || sku.isEmpty() || kmS.isEmpty() || sgS.isEmpty() || ventanaS.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parseo robusto
        Integer skuQtyI = Parsers.parseIntOrNull(sku);
        Double  kmD     = Parsers.parseDoubleOrNull(kmS);
        Long    sgL     = Parsers.parseLongOrNull(sgS);
        Integer venI    = Parsers.parseIntOrNull(ventanaS);
        Double retornoD = Parsers.parseDoubleOrNull(retornoS);

        if (skuQtyI == null || kmD == null || sgL == null || venI == null || retornoD == null) {
            Toast.makeText(this, "SKU, KM, SG y Ventana deben ser numéricos", Toast.LENGTH_SHORT).show();
            return;
        }

        int    skuQty = skuQtyI;
        double km     = kmD;
        long   sg     = sgL;
        int    ventana= venI;
        int    cant   = 0; // Valor neutro solo para altas nuevas.

        if (!Double.isFinite(km) || !Double.isFinite(retornoD)
                || !Double.isFinite(km + retornoD) || km < 0 || retornoD < 0) { Toast.makeText(this, "KM debe ser finito y no negativo", Toast.LENGTH_SHORT).show(); return; }
        if (sg < 0)      { Toast.makeText(this, "SG no puede ser negativo", Toast.LENGTH_SHORT).show(); return; }
        if (ventana < 0) { Toast.makeText(this, "Ventana no puede ser negativa", Toast.LENGTH_SHORT).show(); return; }

        Registro r = new Registro(fecha, local, sku, km, sg, ventana, cant);
        // N4: estampar la modalidad activa al momento de crear. Asi el
        // registro recuerda con que parametros fue creado, aunque despues
        // el usuario cambie su modalidad activa.
        r.modalidad = modalidad.name();

        AppDatabase.ioExecutor.execute(() -> {
            CalculoNuevoRegistro.completarNuevo(r, km, retornoD,
                    Config.basePorSku(skuQty, modalidad, fecha),
                    Config.getValorUnitSku(modalidad), Config.getValorKmNuevo(modalidad),
                    Config.bonoConfig(modalidad), local,
                    Config.getComisionPorc(modalidad),
                    Config.getRendimientoKmPorLitro(modalidad), Config.getPrecioLitro(modalidad));
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
        vb.etKmRetorno.setText("");
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





