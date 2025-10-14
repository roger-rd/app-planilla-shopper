package cl.rdrp.planilla_shopper.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;


@Dao
public interface RegistroDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(Registro r);

    @Query("UPDATE registro SET fecha = :fecha, sku = :sku, km = :km, sg = :sg, ventana = :ventana, cant = :cant  WHERE id = :id")
    int update(long id, String fecha, String sku, double km, long sg, int ventana, int cant);

    @Query("DELETE FROM registro WHERE id = :id")
    int delete(long id);

    @Query("SELECT IFNULL(SUM(sg),0) FROM registro WHERE fecha = :fecha")
    long SumSG(String fecha);

    @Query("SELECT IFNULL(SUM(km),0.0) FROM registro WHERE fecha = :fecha")
    double sumKm(String fecha);


    @Query("SELECT * FROM registro WHERE fecha = :fecha ORDER BY id DESC ")
    java.util.List<Registro> listByFecha(String fecha);

    @Query("SELECT * FROM registro WHERE fecha = :fechaISO OR fecha = :fechaLegacy ORDER BY id DESC")
    java.util.List<Registro> listByFechaCompat(String fechaISO, String fechaLegacy);

    @Query("SELECT * FROM registro WHERE fecha BETWEEN :desde AND :hasta ORDER BY fecha DESC")
    java.util.List<Registro> listByRangoFechas(String desde, String hasta);



}
