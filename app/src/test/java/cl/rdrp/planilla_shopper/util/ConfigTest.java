package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests de invariantes de Config.
 *
 * En Fase 4a los tests comparaban DEFAULT_* contra las constantes legacy.
 * En Fase 4b las constantes legacy desaparecieron (se reemplazaron por
 * getters que leen de Prefs), asi que los tests verifican directamente
 * que los DEFAULT_* tienen los valores esperados.
 *
 * Si alguien cambia un DEFAULT_* por error, este test falla y nos avisa
 * antes de que un calculo divergente llegue al usuario.
 *
 * Nota: los getters publicos de Config (getValorUnitSku, etc.) NO se
 * testean aqui porque dependen de App.get(), que es Application context
 * y no esta disponible en tests JVM puros. Para probarlos haria falta
 * Robolectric o androidTest, que ahora mismo no son parte del proyecto.
 * La validacion de que los getters retornan el default cuando Prefs esta
 * vacio se hace manualmente en celular (primer arranque post-update).
 */
public class ConfigTest {

    @Test
    public void defaultValorUnitSku_isSixtyOne() {
        assertEquals(61, Config.DEFAULT_VALOR_UNIT_SKU);
    }

    @Test
    public void defaultComisionPorc_isFifteen25() {
        assertEquals(0.1525, Config.DEFAULT_COMISION_PORC, 0.0);
    }

    @Test
    public void defaultRendimientoKmPorLitro_isTen() {
        assertEquals(10.0, Config.DEFAULT_RENDIMIENTO_KM_POR_LITRO, 0.0);
    }

    @Test
    public void defaultPrecioLitro_is1200() {
        assertEquals(1200, Config.DEFAULT_PRECIO_LITRO);
    }

    @Test
    public void defaultBonoPorKmDomLunMar_isOneHundred() {
        assertEquals(100.0, Config.DEFAULT_BONO_POR_KM_DOM_LUN_MAR, 0.0);
    }

    @Test
    public void valorUnitKmReferencial_unchanged() {
        // VALOR_UNIT_KM se mantiene como constante referencial historica.
        // No se usa en calculos (la logica esta en getValorKm con fecha).
        assertEquals(234.0, Config.VALOR_UNIT_KM, 0.0);
    }

    // ===== Reglas duras (no configurables): smoke tests =====

    @Test
    public void basePorSku_tramos_unchanged() {
        // Garantiza que los tramos siguen como estaban. Si alguien los
        // edita por error, este test falla.
        assertEquals(0,     Config.basePorSku(0));
        assertEquals(2623,  Config.basePorSku(1));
        assertEquals(2623,  Config.basePorSku(10));
        assertEquals(3026,  Config.basePorSku(11));
        assertEquals(3026,  Config.basePorSku(30));
        assertEquals(4035,  Config.basePorSku(50));
        assertEquals(5044,  Config.basePorSku(70));
        assertEquals(7061,  Config.basePorSku(90));
        assertEquals(8070,  Config.basePorSku(125));
        assertEquals(9079,  Config.basePorSku(150));
        assertEquals(10088, Config.basePorSku(151));
        assertEquals(10088, Config.basePorSku(9999));
    }

    @Test
    public void getValorKm_preCambio_returns234() {
        // Cualquier fecha anterior al 2026-03-26 retorna 234
        assertEquals(234.0, Config.getValorKm("2024-01-15"), 0.0);
        assertEquals(234.0, Config.getValorKm("2026-03-25"), 0.0);
    }

    @Test
    public void getValorKm_postCambio_returns276() {
        // Desde 2026-03-26 inclusive en adelante retorna 276
        assertEquals(276.0, Config.getValorKm("2026-03-26"), 0.0);
        assertEquals(276.0, Config.getValorKm("2026-12-31"), 0.0);
    }

    @Test
    public void calcularTotalKm_combinaKmYValorPorFecha() {
        // 10 km * 234 = 2340 (pre-cambio)
        assertEquals(2340L, Config.calcularTotalKm(10.0, "2024-05-01"));
        // 10 km * 276 = 2760 (post-cambio)
        assertEquals(2760L, Config.calcularTotalKm(10.0, "2026-06-01"));
    }
}
