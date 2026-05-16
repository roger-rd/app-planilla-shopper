package cl.rdrp.planilla_shopper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.data.AppDatabase;
import cl.rdrp.planilla_shopper.data.Registro;

public class BackupActivity extends AppCompatActivity {

    private static final int REQ_EXPORT_BACKUP = 1001;
    private static final int REQ_IMPORT_BACKUP = 1002;

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
    public static class BackupData {
        public java.util.List<cl.rdrp.planilla_shopper.data.Registro> registros;
        public java.util.List<cl.rdrp.planilla_shopper.data.Combustible> combustibles;
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
                backup.registros     = db.registroDao().getAllSync();
                backup.combustibles  = db.combustibleDao().getAllSync();

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

                db.runInTransaction(() -> {
                    // borra TODO lo actual
                    db.registroDao().deleteAllSync();
                    db.combustibleDao().deleteAllSync();

                    // inserta lo del backup si viene algo
                    if (backup != null) {
                        if (backup.registros != null && !backup.registros.isEmpty()) {
                            db.registroDao().insertAllSync(backup.registros);
                        }
                        if (backup.combustibles != null && !backup.combustibles.isEmpty()) {
                            db.combustibleDao().insertAllSync(backup.combustibles);
                        }
                    }
                });

                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Datos importados correctamente", android.widget.Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Error al importar backup", android.widget.Toast.LENGTH_LONG).show()
                );
            }
        });
    }

}
