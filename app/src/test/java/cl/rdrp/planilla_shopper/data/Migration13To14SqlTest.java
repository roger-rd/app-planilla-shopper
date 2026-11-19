package cl.rdrp.planilla_shopper.data;

import androidx.sqlite.db.SupportSQLiteDatabase;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class Migration13To14SqlTest {
    @Test
    public void migrationOnlyAddsTheSpecifiedColumns() {
        List<String> statements = new ArrayList<>();
        SupportSQLiteDatabase db = (SupportSQLiteDatabase) Proxy.newProxyInstance(
                SupportSQLiteDatabase.class.getClassLoader(),
                new Class<?>[]{SupportSQLiteDatabase.class}, (proxy, method, args) -> {
                    assertEquals("execSQL", method.getName());
                    assertEquals(1, args.length);
                    statements.add((String) args[0]);
                    return null;
                });
        assertEquals(13, AppDatabase.MIGRATION_13_14.startVersion);
        assertEquals(14, AppDatabase.MIGRATION_13_14.endVersion);
        AppDatabase.MIGRATION_13_14.migrate(db);
        List<String> expected = new ArrayList<>();
        for (String column : Arrays.asList("km_entrega", "km_retorno")) {
            expected.add("ALTER TABLE registro ADD COLUMN " + column + " REAL");
        }
        for (String column : Arrays.asList("calc_origen", "tarifa_base", "valor_unit_sku",
                "precio_litro", "monto_base", "monto_sku", "monto_km", "monto_bono_km", "monto_bruto")) {
            expected.add("ALTER TABLE registro ADD COLUMN " + column + " INTEGER NOT NULL DEFAULT 0");
        }
        for (String column : Arrays.asList("valor_km", "bono_por_km", "comision_porc", "rendimiento_km_litro")) {
            expected.add("ALTER TABLE registro ADD COLUMN " + column + " REAL NOT NULL DEFAULT 0");
        }
        assertEquals(expected, statements);
    }
}
