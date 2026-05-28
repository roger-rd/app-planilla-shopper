package cl.rdrp.planilla_shopper.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO para el catalogo de locales (N5).
 *
 * Convenciones:
 *   - listActivos -> solo los visibles para seleccionar (popup / dropdown).
 *   - listAll     -> incluye archivados (para la pantalla de gestion).
 *   - findByCodigo -> usado para detectar si un codigo ya esta en catalogo
 *                     (insert con OnConflict.IGNORE deja el ya existente).
 *   - archivar/desarchivar -> cambian el flag activo sin borrar la fila.
 *
 * No se expone un delete duro a proposito. Si el usuario quiere "borrar"
 * un local, la operacion correcta es archivar para no romper pedidos
 * historicos que referencian ese codigo.
 */
@Dao
public interface LocalDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Local local);

    @Update
    int update(Local local);

    @Query("SELECT * FROM locales WHERE activo = 1 ORDER BY codigo ASC")
    List<Local> listActivos();

    @Query("SELECT * FROM locales ORDER BY activo DESC, codigo ASC")
    List<Local> listAll();

    @Query("SELECT * FROM locales WHERE codigo = :codigo LIMIT 1")
    Local findByCodigo(String codigo);

    @Query("SELECT COUNT(*) FROM locales WHERE activo = 1")
    int countActivos();

    @Query("UPDATE locales SET activo = 0 WHERE id = :id")
    int archivar(long id);

    @Query("UPDATE locales SET activo = 1 WHERE id = :id")
    int desarchivar(long id);
}
