package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BonosFormularioTest {
    @Test public void valorKmExigePositivoFinito() {
        for (Double valor : new Double[]{null, 0.0, -1.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertFalse(BonosFormulario.valorKmValido(valor));
        assertTrue(BonosFormulario.valorKmValido(0.25));
        assertTrue(BonosFormulario.valorKmValido(276.0));
    }
    @Test public void montoExigeNumeroFinitoNoNegativoInclusoInactivo() {
        for (boolean activo : new boolean[]{true, false}) {
            for (Double valor : new Double[]{null, -0.01, Double.NaN,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
                assertNotNull(BonosFormulario.error(valor, activo, 2, 1));
            assertNull(BonosFormulario.error(0.0, activo, 2, 1));
            assertNull(BonosFormulario.error(100.25, activo, 2, 1));
        }
    }
    @Test public void bonoActivoExigeDiasYSalas() {
        assertNotNull(BonosFormulario.error(100.0, true, 0, 1));
        assertNotNull(BonosFormulario.error(100.0, true, 2, 0));
        assertNotNull(BonosFormulario.error(100.0, true, 0, 0));
        assertNull(BonosFormulario.error(100.0, true, 7, 3));
    }
    @Test public void bonoInactivoPermiteSeleccionVacia() {
        assertNull(BonosFormulario.error(100.0, false, 0, 0));
        assertNull(BonosFormulario.error(100.0, false, 0, 1));
        assertNull(BonosFormulario.error(100.0, false, 2, 0));
    }
}
