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
                BonoExtra.class
        },
        version = 11,               // N4: agrega columna modalidad a registro
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
    public abstract BonoDao bonoDao();          // 👈 NUEVO

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
                                    MIGRATION_10_11   // N4
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
