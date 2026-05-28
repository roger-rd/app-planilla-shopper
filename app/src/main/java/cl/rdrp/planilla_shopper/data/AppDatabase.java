package cl.rdrp.planilla_shopper.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Registro.class,
                Combustible.class,
                BonoExtra.class,
                Local.class             // N5: catalogo de locales
        },
        version = 12,               // N5: agrega tabla locales con auto-import
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /**
     * Executor unico compartido para operaciones de IO/BD en toda la app.
     * Centralizar aqui evita crear hilos por operacion y serializa las
     * llamadas a Room en un solo worker thread.
     *
     * Uso: AppDatabase.ioExecutor.execute(() -> { ... });
     *
     * No se llama shutdown porque es de vida-de-aplicacion; el proceso JVM
     * lo libera al cerrarse.
     */
    public static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public abstract RegistroDao registroDao();
    public abstract CombustibleDao combustibleDao();
    public abstract BonoDao bonoDao();
    public abstract LocalDao localDao();        // N5

    // 6 → 7
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP INDEX IF EXISTS `index_registro_fecha_local`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_registro_fecha_local` " +
                    "ON registro (fecha, local)");
        }
    };

    // 7 → 8 combustible
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS combustible (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "fecha TEXT, " +
                            "bencinera TEXT, " +
                            "tipo TEXT, " +
                            "kmAnterior REAL NOT NULL, " +
                            "kmActual REAL NOT NULL, " +
                            "kmRecorridos REAL NOT NULL, " +
                            "valorLitro REAL NOT NULL, " +
                            "litros REAL NOT NULL, " +
                            "totalPagado REAL NOT NULL" +
                            ")"
            );
        }
    };

    // 8 → 9 (ya la tenías)
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            try {
                db.execSQL("ALTER TABLE combustible ADD COLUMN fecha TEXT");
            } catch (Exception ignored) {}
        }
    };

    // 9 → 10  👉 tabla de bonos
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bonos (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "fecha TEXT, " +
                            "sg TEXT, " +
                            "descripcion TEXT, " +
                            "monto INTEGER NOT NULL" +
                            ")"
            );
        }
    };

    // 10 → 11  👉 N4: agrega columna modalidad a registro.
    // Default 'SHOPPER' para todos los registros pre-N4 (decision N1:
    // comportamiento original = Shopper). NOT NULL para evitar nulls.
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "ALTER TABLE registro ADD COLUMN modalidad TEXT NOT NULL DEFAULT 'SHOPPER'"
            );
        }
    };

    // 11 → 12  👉 N5: tabla locales (catalogo) + auto-import de codigos
    // distintos que ya existian en registro. Asi el usuario no tiene que
    // recrear su catalogo a mano al actualizar.
    //
    // Notas:
    //  - "activo" se guarda como INTEGER NOT NULL DEFAULT 1 (true) porque
    //    SQLite no tiene tipo boolean. Room lo lee como boolean igualmente.
    //  - El UNIQUE INDEX en "codigo" garantiza que el auto-import no
    //    duplique codigos si DISTINCT ya los normalizo, pero ademas previene
    //    duplicados creados manualmente desde la UI.
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS locales (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "codigo TEXT NOT NULL, " +
                            "alias TEXT, " +
                            "activo INTEGER NOT NULL DEFAULT 1" +
                            ")"
            );
            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_locales_codigo " +
                            "ON locales (codigo)"
            );
            // Auto-import: poblar locales con los codigos distintos que ya
            // hay en registro. INSERT OR IGNORE para que no falle si por
            // alguna razon la tabla locales ya tuviera filas (no deberia
            // pasar en una migration 11->12, pero defensivo).
            db.execSQL(
                    "INSERT OR IGNORE INTO locales (codigo, alias, activo) " +
                            "SELECT DISTINCT local, NULL, 1 FROM registro " +
                            "WHERE local IS NOT NULL AND TRIM(local) != ''"
            );
        }
    };

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "foxer.db"
                            )
                            .addMigrations(
                                    MIGRATION_6_7,
                                    MIGRATION_7_8,
                                    MIGRATION_8_9,
                                    MIGRATION_9_10,
                                    MIGRATION_10_11,  // N4
                                    MIGRATION_11_12   // N5
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
