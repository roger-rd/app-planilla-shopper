package cl.rdrp.planilla_shopper.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BackupVersionTest {
    @Test
    public void exportsV5() {
        assertEquals(5, BackupVersion.BACKUP_VERSION_CURRENT);
    }

    @Test
    public void v1PreservesBonosAndFillsShopper() {
        assertDecision(1, false, true,
                "Backup V1 importado — registros marcados como Shopper. Los bonos actuales se mantuvieron");
    }

    @Test
    public void missingVersionUsesV1() {
        assertDecision(0, false, true,
                "Backup V1 importado — registros marcados como Shopper. Los bonos actuales se mantuvieron");
    }

    @Test
    public void v2RestoresBonosAndFillsShopper() {
        assertDecision(2, true, true,
                "Backup V2 importado — registros viejos marcados como Shopper");
    }

    @Test
    public void v3RestoresBonosAndKeepsModalidad() {
        assertDecision(3, true, false,
                "Backup V3 importado completo (registros con modalidad, combustibles y bonos)");
    }

    @Test
    public void v4RestoresBonosAndKeepsModalidad() {
        assertDecision(4, true, false,
                "Backup V4 importado completo (registros con modalidad, combustibles y bonos)");
    }

    private static void assertDecision(int version, boolean restoreBonos,
                                       boolean fillShopperModalidad, String message) {
        BackupVersion.ImportDecision decision = BackupVersion.forImport(version);
        assertEquals(restoreBonos, decision.restoreBonos);
        assertEquals(fillShopperModalidad, decision.fillShopperModalidad);
        assertEquals(message, decision.message);
    }
}
