package cl.rdrp.planilla_shopper.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import cl.rdrp.planilla_shopper.R;
import cl.rdrp.planilla_shopper.util.PesableParser;
import cl.rdrp.planilla_shopper.util.Texts;

/**
 * QR Pesable: pantalla aislada (sin tocar registros) para regenerar codigos
 * QR del formato PESABLE del sistema. Dos modos de uso:
 *
 *   1. Manual: pegar texto en el campo y tocar "Generar QR".
 *   2. Escaner: tocar "Escanear QR", abrir camara, leer QR original. El
 *      texto leido se inyecta en el campo y, si tiene el header PESABLE,
 *      se genera automaticamente; si no, queda en el input para que el
 *      usuario lo revise/ajuste.
 *
 * No persiste nada: no hay historial, no toca la BD. Si se cierra la
 * pantalla, el QR generado se pierde.
 */
public class QrPesableActivity extends AppCompatActivity {

    // Tamaño en pixeles del bitmap del QR. 800x800 da buena calidad para
    // imprimir o mostrar en pantallas grandes; el ImageView lo escala.
    private static final int QR_PX = 800;

    // Tamaño del bitmap del codigo de barras (1D, formato CODE-128). Mas
    // ancho que alto porque es un codigo lineal. La altura del ImageView
    // es chica (140dp) asi que basta con un bitmap de altura moderada.
    private static final int BARCODE_W_PX = 1000;
    private static final int BARCODE_H_PX = 300;

    private TextInputLayout tilTexto;
    private TextInputEditText etTexto;
    private MaterialCardView cardQr;
    private ImageView ivQr;
    private TextView tvInfoParsed;

    // Codigo de barras (independiente del QR)
    private TextInputLayout tilCodigoBarras;
    private TextInputEditText etCodigoBarras;
    private FrameLayout containerBarras;
    private ImageView ivBarras;

    /** Launcher del scanner de ZXing. Se inicializa en onCreate. */
    private ActivityResultLauncher<ScanOptions> scannerLauncher;

    /** Launcher para pedir permiso de camara en runtime. */
    private ActivityResultLauncher<String> permisoCamaraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_pesable);

        setTitle("QR Pesable");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tilTexto         = findViewById(R.id.tilTexto);
        etTexto          = findViewById(R.id.etTexto);
        cardQr           = findViewById(R.id.cardQr);
        ivQr             = findViewById(R.id.ivQr);
        tvInfoParsed     = findViewById(R.id.tvInfoParsed);

        // Codigo de barras: input + container + imageView
        tilCodigoBarras  = findViewById(R.id.tilCodigoBarras);
        etCodigoBarras   = findViewById(R.id.etCodigoBarras);
        containerBarras  = findViewById(R.id.containerBarras);
        ivBarras         = findViewById(R.id.ivBarras);

        // Launcher del scanner: recibe el resultado del escaneo y lo procesa.
        scannerLauncher = registerForActivityResult(
                new ScanContract(),
                result -> {
                    if (result == null || result.getContents() == null) {
                        // El usuario cancelo (back) - no es error.
                        return;
                    }
                    String texto = result.getContents();
                    etTexto.setText(texto);
                    // Auto-generar solo si parece PESABLE. Si no, dejamos
                    // que el usuario revise antes de generar.
                    if (texto.startsWith(PesableParser.HEADER + ";")) {
                        generarQr(texto);
                    } else {
                        Toast.makeText(this,
                                "Texto escaneado cargado. Revisalo y tocá Generar QR.",
                                Toast.LENGTH_LONG).show();
                    }
                });

        // Launcher de permiso de camara: si el usuario aprueba, lanzamos scan.
        permisoCamaraLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        lanzarScanner();
                    } else {
                        explicarPermisoDenegado();
                    }
                });

        MaterialButton btnGenerar       = findViewById(R.id.btnGenerar);
        MaterialButton btnEscanear      = findViewById(R.id.btnEscanear);
        MaterialButton btnLimpiar       = findViewById(R.id.btnLimpiar);
        MaterialButton btnGenerarBarras = findViewById(R.id.btnGenerarBarras);

        btnGenerar.setOnClickListener(v -> {
            String texto = Texts.s(etTexto.getText());
            if (!validar(texto)) return;
            generarQr(texto);
        });

        btnEscanear.setOnClickListener(v -> pedirPermisoYEscanear());

        btnLimpiar.setOnClickListener(v -> limpiar());

        btnGenerarBarras.setOnClickListener(v -> {
            String codigo = Texts.s(etCodigoBarras.getText());
            if (codigo.isEmpty()) {
                tilCodigoBarras.setError("Ingresá el código a generar.");
                return;
            }
            tilCodigoBarras.setError(null);
            generarBarras(codigo);
        });
    }

    /**
     * Valida que el texto tenga el header PESABLE. Si falla, pinta el
     * error en el TextInputLayout y devuelve false. Si pasa, limpia el
     * error y devuelve true.
     */
    private boolean validar(String texto) {
        if (texto.isEmpty()) {
            tilTexto.setError("Pegá el texto o escaneá un QR.");
            return false;
        }
        if (!texto.startsWith(PesableParser.HEADER + ";")) {
            tilTexto.setError("Debe empezar con \"" + PesableParser.HEADER + ";\".");
            return false;
        }
        tilTexto.setError(null);
        return true;
    }

    /**
     * Genera el bitmap del QR a partir del texto y lo muestra. Tambien
     * parsea el contenido y rellena la card de info abajo.
     *
     * Atajo: si el PESABLE es valido y el campo de codigo de barras esta
     * vacio, pre-rellena con el codBarras del PESABLE como conveniencia.
     * Si el usuario ya tipeo algo en ese campo, NO lo pisa.
     */
    private void generarQr(String texto) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bmp = encoder.encodeBitmap(texto, BarcodeFormat.QR_CODE, QR_PX, QR_PX);
            ivQr.setImageBitmap(bmp);

            PesableParser parsed = PesableParser.parse(texto);
            tvInfoParsed.setText(parsed.resumen());

            cardQr.setVisibility(View.VISIBLE);

            // Pre-rellenado del codigo de barras si el PESABLE es valido y
            // el campo todavia esta vacio (no pisamos lo que el usuario tipeo).
            if (parsed.isValid()
                    && parsed.codBarras != null
                    && !parsed.codBarras.isEmpty()
                    && Texts.s(etCodigoBarras.getText()).isEmpty()) {
                etCodigoBarras.setText(parsed.codBarras);
            }
        } catch (WriterException e) {
            Toast.makeText(this,
                    "No se pudo generar el QR: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Genera el bitmap de un codigo de barras CODE-128 a partir del input.
     * CODE-128 acepta cualquier ASCII printable y longitud variable, por
     * lo que es flexible para codigos numericos o alfanumericos.
     */
    private void generarBarras(String codigo) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bmp = encoder.encodeBitmap(
                    codigo, BarcodeFormat.CODE_128, BARCODE_W_PX, BARCODE_H_PX);
            ivBarras.setImageBitmap(bmp);
            containerBarras.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            Toast.makeText(this,
                    "No se pudo generar el código de barras: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Limpia ambos formularios y oculta sus resultados. El boton vive en
     * la card del QR pero la accion afecta tambien al codigo de barras
     * para no dejar residuos en la pantalla.
     */
    private void limpiar() {
        etTexto.setText("");
        tilTexto.setError(null);
        ivQr.setImageDrawable(null);
        tvInfoParsed.setText("");
        cardQr.setVisibility(View.GONE);

        etCodigoBarras.setText("");
        tilCodigoBarras.setError(null);
        ivBarras.setImageDrawable(null);
        containerBarras.setVisibility(View.GONE);
    }

    // ===== Permiso de camara + scanner =====

    /**
     * Punto de entrada del flujo de escaneo. Decide si pedir permiso o
     * lanzar directo segun el estado actual del permiso CAMERA.
     */
    private void pedirPermisoYEscanear() {
        int estado = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (estado == PackageManager.PERMISSION_GRANTED) {
            lanzarScanner();
        } else {
            permisoCamaraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Lanza el scanner de ZXing con opciones razonables: solo QR (no otros
     * formatos), camara trasera, sin guardar imagen, mensaje de prompt
     * personalizado.
     */
    private void lanzarScanner() {
        ScanOptions opciones = new ScanOptions();
        opciones.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        opciones.setPrompt("Enfocá el QR PESABLE");
        opciones.setBeepEnabled(true);
        opciones.setOrientationLocked(false);
        opciones.setBarcodeImageEnabled(false);
        scannerLauncher.launch(opciones);
    }

    /**
     * Dialogo amigable cuando el usuario denego el permiso. Le explica que
     * sin camara solo puede ingresar el texto manualmente.
     */
    private void explicarPermisoDenegado() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permiso de cámara necesario")
                .setMessage("Para escanear QRs hace falta acceso a la cámara. " +
                        "Igualmente podés pegar el texto manualmente y tocar Generar QR.\n\n" +
                        "Si querés habilitarlo, andá a Ajustes del sistema > Apps > " +
                        "Planilla Shopper > Permisos.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
