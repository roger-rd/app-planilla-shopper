package cl.rdrp.planilla_shopper.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Registro.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    public abstract RegistroDao registroDao();

    //MIGRACION 4 > 5 agg col cant
    //public static final Migration MIGRATION_5_6 = new Migration(5,6) {
    //    @Override
        //public void migrate(@NonNull SupportSQLiteDatabase db ) {
     //       db.execSQL("ALTER TABLE registro ADD COLUMN cant INTERGER NOT NULL DEFAULT 0");
      //  }
    //};

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            AppDatabase.class,
                            "foxer.db"
                    )
                            //.fallbackToDestructiveMigration()
                            //.addMigrations(MIGRATION_4_5)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
