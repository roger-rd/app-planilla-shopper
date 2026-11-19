package cl.rdrp.planilla_shopper.data;

import android.database.Cursor;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Migration13To14Test {
    private static final String DB_NAME = "migration-13-14";
    private static final String ORIGINAL_COLUMNS = "id, fecha, local, sku, km, sg, ventana, cant, modalidad";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class,
            Collections.emptyList());

    @Test
    public void preservesOriginalRowsAndValidatesNewDefaults() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME, 13);
        db.execSQL("INSERT INTO registro (" + ORIGINAL_COLUMNS + ") VALUES "
                + "(1, '2026-11-01', 'Local A', '12', 15.75, 12345678901, 2, 3, 'SHOPPER'),"
                + "(7, NULL, NULL, NULL, 0, 0, 0, 0, 'PICKER'),"
                + "(42, '2024-12-31', 'Ñuñoa', '', 0.125, 987, 5, 1, 'DRIVER')");
        List<List<Object>> before = originalRows(db);
        db.close();

        // Ejecuta la migración real y valida todo el esquema contra 14.json.
        db = helper.runMigrationsAndValidate(DB_NAME, 14, true, AppDatabase.MIGRATION_13_14);
        assertEquals(before, originalRows(db));
        try (Cursor cursor = db.query("SELECT * FROM registro ORDER BY id")) {
            assertEquals(3, cursor.getCount());
            while (cursor.moveToNext()) {
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("km_entrega")));
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("km_retorno")));
                for (String column : new String[]{"calc_origen", "tarifa_base", "valor_unit_sku",
                        "precio_litro", "monto_base", "monto_sku", "monto_km", "monto_bono_km", "monto_bruto",
                        "valor_km", "bono_por_km", "comision_porc", "rendimiento_km_litro"}) {
                    int index = cursor.getColumnIndexOrThrow(column);
                    assertFalse(column, cursor.isNull(index));
                    assertEquals(column, 0.0, cursor.getDouble(index), 0.0);
                }
            }
        }
        db.close();
    }

    private static List<List<Object>> originalRows(SupportSQLiteDatabase db) {
        List<List<Object>> rows = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT " + ORIGINAL_COLUMNS + " FROM registro ORDER BY id")) {
            while (cursor.moveToNext()) {
                List<Object> row = new ArrayList<>();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: row.add(null); break;
                        case Cursor.FIELD_TYPE_INTEGER: row.add(cursor.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: row.add(cursor.getDouble(i)); break;
                        case Cursor.FIELD_TYPE_STRING: row.add(cursor.getString(i)); break;
                        default: throw new AssertionError("Tipo inesperado");
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
