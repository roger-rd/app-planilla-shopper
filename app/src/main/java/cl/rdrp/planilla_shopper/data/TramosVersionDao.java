package cl.rdrp.planilla_shopper.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO de versiones de tramos por modalidad.
 *
 * Convenciones:
 *   - listByModalidad -> todas las versiones de una modalidad, ordenadas
 *     desc por fecha_desde para mostrar las mas recientes arriba.
 *   - getVigenteEn -> la version vigente en una fecha dada. Es la de
 *     mayor fecha_desde menor o igual a la fecha consultada. Si no hay
 *     ninguna (caso borde, no deberia pasar despues del seed), retorna
 *     null y el codigo cliente cae a un default.
 *   - countByModalidad -> usado para evitar dejar una modalidad sin
 *     ninguna version (no se permite borrar la ultima).
 */
@Dao
public interface TramosVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TramosVersion v);

    @Update
    int update(TramosVersion v);

    @Delete
    int delete(TramosVersion v);

    @Query("SELECT * FROM tramos_versions WHERE modalidad = :modalidad " +
            "ORDER BY fecha_desde DESC")
    List<TramosVersion> listByModalidad(String modalidad);

    @Query("SELECT * FROM tramos_versions " +
            "WHERE modalidad = :modalidad AND fecha_desde <= :fechaIso " +
            "ORDER BY fecha_desde DESC LIMIT 1")
    TramosVersion getVigenteEn(String modalidad, String fechaIso);

    @Query("SELECT COUNT(*) FROM tramos_versions WHERE modalidad = :modalidad")
    int countByModalidad(String modalidad);

    @Query("SELECT * FROM tramos_versions WHERE id = :id LIMIT 1")
    TramosVersion findById(long id);
}
