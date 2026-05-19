package cl.rdrp.planilla_shopper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.BonoExtra;
import cl.rdrp.planilla_shopper.data.Registro;

public class BackupActivity extends AppCompatActivity {

    private static final int REQ_EXPORT_BACKUP = 1001;
    private static final int REQ_IMPORT_BACKUP = 1002;

    /** Version del formato JSON que escribimos al exportar (Fase 5). */
    private static final int BACKUP_VERSION_CURRENT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        // Botones
        com.google.android.material.button.MaterialButton btnExportar = findViewById(R.id.btnExportar);
        com.google.android.material.button.MaterialButton btnImportar = findViewById(R.id.btnImportar);

        btnExportar.setOnClickListener(v -> crearBackup());
        btnImportar.setOnClickListener(v -> importarBackup());
    }

    // === MODELO CONTENEDOR DE BACKUP ===
    //
    // Historial de versiones:
    //   V1 (pre-Fase 5): {registros, combustibles}, sin campo "version".
    //   V2 (Fase 5):     {version, registros, combustibles, bonos}.
    //
    // Al importar:
    //   - Si el JSON no trae "version" -> Gson deja en 0 -> tratamos como V1.
    //   - Si version >= 2 -> tratamos como V2 y restauramos bonos.
    //   - Bonos NO se borran en restauracion de V1 (preservacion: el backup
    //     viejo no los conocia, no los podemos restaurar).
    public static class BackupData {
        public int version;
        public java.util.List<cl.rdrp.planilla_shopper.data.Registro> registros;
        public java.util.List<cl.rdrp.planilla_shopper.data.Combustible> combustibles;
        public java.util.List<BonoExtra> bonos;
    }

    // === CREAR ARCHIVO PARA EXPORTAR ===
    private void crearBackup() {
        String fileName = "planilla-backup_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmm",
                        java.util.Locale.getDefault()
                ).format(new java.util.Date())
                + ".json";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, REQ_EXPORT_BACKUP);
    }

    // === ABRIR ARCHIVO PARA IMPORTAR ===
    private void importarBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        android.net.Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_EXPORT_BACKUP) {
            exportarDatosAUri(uri);
        } else if (requestCode == REQ_IMPORT_BACKUP) {
            importarDatosDesdeUri(uri);
        }
    }

    // === EXPORTAR DATOS A JSON ===
    private void exportarDatosAUri(android.net.Uri uri) {
        AppDatabase.ioExecutor.execute(() -> {
            try {
                cl.rdrp.planilla_shopper.data.AppDatabase db = cl.rdrp.planilla_shopper.data.AppDatabase.get(this);

                BackupData backup = new BackupData();
                backup.version       = BACKUP_VERSION_CURRENT;       // V2
                backup.registros     = db.registroDao().getAllSync();
                backup.combustibles  = db.combustibleDao().getAllSync();
                backup.bonos         = db.bonoDao().getAllSync();    // NUEVO en V2

                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(backup);

                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                     java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                             os, java.nio.charset.StandardCharsets.UTF_8)) {
                    writer.write(json);
                }

                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Backup exportado correctamente", android.widget.Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Error al exportar backup", android.widget.Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    // === IMPORTAR DATOS DESDE JSON ===
    private void importarDatosDesdeUri(android.net.Uri uri) {
        AppDatabase.ioExecutor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();

                try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                     java.io.BufferedReader reader = new java.io.BufferedReader(
                             new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)
                     )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                String json = sb.toString();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                BackupData backup = gson.fromJson(json, BackupData.class);

                cl.rdrp.planilla_shopper.data.AppDatabase db = cl.rdrp.planilla_shopper.data.AppDatabase.get(this);

                // Detectar version: si no viene el campo, Gson deja 0 -> V1.
                final int version = (backup != null) ? backup.version : 0;
                final boolean isV2OrMas = version >= 2;

                db.runInTransaction(() -> {
                    // Registros y combustibles: SIEMPRE delete + insert
                    db.registroDao().deleteAllSync();
                    db.combustibleDao().deleteAllSync();

                    if (backup != null) {
                        if (backup.registros != null && !backup.registros.isEmpty()) {
                            db.registroDao().insertAllSync(backup.registros);
                        }
                        if (backup.combustibles != null && !backup.combustibles.isEmpty()) {
                            db.combustibleDao().insertAllSync(backup.combustibles);
                        }

                        // Bonos: SOLO si V2+ (V1 no los conocia, los preservamos)
                        if (isV2OrMas) {
                            db.bonoDao().deleteAllSync();
                            if (backup.bonos != null && !backup.bonos.isEmpty()) {
                                db.bonoDao().insertAllSync(backup.bonos);
                            }
                        }
                    }
                });

                runOnUiThread(() -> {
                    String msg = isV2OrMas
                            ? "Backup V2 importado completo (registros, combustibles y bonos)"
                            : "Backup V1 importado — los bonos actuales se mantuvieron (no estaban en el backup)";
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Error al importar backup", android.widget.Toast.LENGTH_LONG).show()
                );
            }
        });
    }

}
