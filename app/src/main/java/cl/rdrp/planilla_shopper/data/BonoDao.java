package cl.rdrp.planilla_shopper.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BonoDao {

    @Insert
    void insert(BonoExtra bono);

    @Query("SELECT * FROM bonos WHERE fecha = :fecha ORDER BY id DESC")
    List<BonoExtra> listByFecha(String fecha);

    // ✅ SUMA BONOS ENTRE FECHAS (MES COMPLETO)
    @Query("SELECT IFNULL(SUM(monto),0) FROM bonos WHERE fecha BETWEEN :desde AND :hasta")
    long sumBonosRango(String desde, String hasta);

    @Delete
    void delete(BonoExtra bono);
}
