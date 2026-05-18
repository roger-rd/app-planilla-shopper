package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests de invariantes de Config.
 *
 * Estos tests son criticos para Fase 4: garantizan que los DEFAULT_*
 * (que en Fase 4b van a alimentar SharedPreferences) son EXACTAMENTE
 * iguales a las constantes legacy. Si alguien cambia un default y olvida
 * actualizar el legacy (o viceversa), este test falla y nos avisa antes
 * de que un calculo divergente llegue al usuario.
 */
public class ConfigTest {

    @Test
    public void defaultValorUnitSku_equalsLegacy() {
        assertEquals(Config.VALOR_UNIT_SKU, Config.DEFAULT_VALOR_UNIT_SKU);
    }

    @Test
    public void defaultComisionPorc_equalsLegacy() {
        assertEquals(Config.COMISION_PORC, Config.DEFAULT_COMISION_PORC, 0.0);
    }

    @Test
    public void defaultRendimientoKmPorLitro_equalsLegacy() {
        assertEquals(Config.RENDIMIENTO_KM_POR_LITRO,
                     Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO, 0.0);
    }

    @Test
    public void defaultPrecioLitro_equalsLegacy() {
        assertEquals(Config.PRECIO_LITRO, Config.DEFAULT_PRECIO_LITRO);
    }

    @Test
    public void defaultBonoPorKmDomLunMar_equalsLegacy() {
        // Legacy es Double (clase), default es double (primitivo) -> .doubleValue()
        assertEquals(Config.BONO_POR_KM_DOM_LUN_MAR.doubleValue(),
                     Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR, 0.0);
    }
}
