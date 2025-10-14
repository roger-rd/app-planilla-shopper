package cl.rdrp.planilla_shopper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.databinding.ActivityMainBinding;
import cl.rdrp.planilla_shopper.util.Prefs;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding vb;
    private cl.rdrp.planilla_shopper.ui.RegistroAdapter adapter;
    private DrawerLayout drawer;
    private ActionBarDrawerToggle toggle;


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
            }
            drawer.closeDrawers();
            return true;
        });


        vb.etKm.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,"));
        vb.etSg.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,"));
        vb.etVentana.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,"));

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
            vb.etFecha.setText(hoyISO());
        }

        // Lista de registros del día
        adapter = new cl.rdrp.planilla_shopper.ui.RegistroAdapter(new cl.rdrp.planilla_shopper.ui.RegistroAdapter.OnEdit() {
            @Override public void onUpdate(cl.rdrp.planilla_shopper.data.Registro r) { mostrarDialogoEditar(r); }
            @Override public void onDelete(cl.rdrp.planilla_shopper.data.Registro r) { eliminarRegistro(r); }
        });
        vb.rvRegistros.setAdapter(adapter);
        vb.rvRegistros.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        cargarListaDelDia();
    }

    // === DatePicker estilo Dashboard ===
    private void mostrarDatePicker() {
        Calendar cal = Calendar.getInstance();
        String actual = s(vb.etFecha.getText());
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            if (!actual.isEmpty()) cal.setTime(f.parse(actual));
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
        String iso = s(vb.etFecha.getText());      // ahora etFecha está en yyyy-MM-dd
        String legacy = toLegacy(iso);             // compat dd/MM/yyyy
        Executors.newSingleThreadExecutor().execute(() -> {
            java.util.List<cl.rdrp.planilla_shopper.data.Registro> items =
                    cl.rdrp.planilla_shopper.data.AppDatabase.get(this).registroDao().listByFechaCompat(iso, legacy);
            runOnUiThread(() -> adapter.submit(items));
        });
    }
    private static String toLegacy(String iso) {
        if (iso == null) return "";
        iso = iso.trim();
        if (iso.length() < 10) return iso;
        try {
            String y = iso.substring(0,4);
            String m = iso.substring(5,7);
            String d = iso.substring(8,10);
            return d + "/" + m + "/" + y;
        } catch (Exception e) {
            return iso;
        }
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
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                String actual = s(etFecha.getText());
                if (!actual.isEmpty()) cal.setTime(f.parse(actual));
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
                    String fechaTxt = s(etFecha.getText());
                    String skuTxt = s(etSku.getText());
                    String kmTxt  = s(etKm.getText());
                    String venTxt = s(etVentana.getText());
                    String sgTxt  = s(etSg.getText());

                    if ( fechaTxt.isEmpty() ||skuTxt.isEmpty() || kmTxt.isEmpty() || venTxt.isEmpty() || sgTxt.isEmpty()) {
                        Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Double  kmD  = parseDoubleStrict(s(etKm.getText()));
                    Integer venI = parseIntOnlyDigits(s(etVentana.getText()));
                    Long    sgL  = parseLongStrict(s(etSg.getText()));
                    String  skuN = s(etSku.getText());
                    Integer cantI = parseIntOnlyDigits(s(etCant.getText()));

                    if (kmD == null || venI == null || sgL == null || cantI == null || skuN.isEmpty()) {
                        Toast.makeText(this, "Completa y usa valores válidos", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double km = kmD;
                    int ventana = venI;
                    long sg = sgL;
                    int cant = cantI;


                    Executors.newSingleThreadExecutor().execute(() -> {
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
                .setPositiveButton("Sí", (d, w) -> java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                    cl.rdrp.planilla_shopper.data.AppDatabase.get(this).registroDao().delete(r.id);
                    runOnUiThread(this::cargarListaDelDia);
                }))
                .setNegativeButton("No", null)
                .show();
    }

    private void guardar() {
        String fecha    = s(vb.etFecha.getText()); // ahora guarda yyyy-MM-dd
        String local    = s(vb.etLocal.getText());
        String sku      = s(vb.etSku.getText());
        String kmS      = s(vb.etKm.getText());
        String sgS      = s(vb.etSg.getText());
        String ventanaS = s(vb.etVentana.getText());
        String cantS    = s(vb.etCant.getText());

        // Guarda LOCAL predeterminado si no existe
        if (!Prefs.hasLocal(this)) {
            Prefs.setLocal(this, local);
        }

        // Campos obligatorios
        if (fecha.isEmpty() || local.isEmpty() || sku.isEmpty() || kmS.isEmpty() || sgS.isEmpty() || ventanaS.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parseo robusto
        Integer skuQtyI = parseIntOnlyDigits(sku);
        Double  kmD     = parseDoubleStrict(kmS);
        Long    sgL     = parseLongStrict(sgS);
        Integer venI    = parseIntOnlyDigits(ventanaS);
        Integer cantI   = parseIntOnlyDigits(cantS);

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

        Executors.newSingleThreadExecutor().execute(() -> {
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

    private String s(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    private Long parseLongStrict(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseIntOnlyDigits(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private Double parseDoubleStrict(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        // admite , o . como separador
        s = s.replace(",", ".").replaceAll("[^0-9.]", "");
        // evita 1..2 casos raros
        int first = s.indexOf('.');
        if (first >= 0) {
            int next = s.indexOf('.', first + 1);
            if (next >= 0) s = s.substring(0, next).replaceAll("\\.+$", "");
        }
        if (s.isEmpty() || s.equals(".")) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    // === helper fecha hoy en ISO ===
    private static String hoyISO() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }
}
