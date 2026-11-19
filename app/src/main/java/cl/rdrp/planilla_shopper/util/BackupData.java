package cl.rdrp.planilla_shopper.util;

import java.util.List;
import cl.rdrp.planilla_shopper.data.*;

/** Contenedor JSON compartido por exportación, importación y pruebas JVM. */
public class BackupData {
    public int version;
    public List<Registro> registros;
    public List<Combustible> combustibles;
    public List<BonoExtra> bonos;
}
