package cl.rdrp.planilla_shopper.util;

/** Version del backup y decisiones de importacion independientes de Android. */
public final class BackupVersion {
    public static final int BACKUP_VERSION_CURRENT = 4;

    private BackupVersion() {}

    public static ImportDecision forImport(int version) {
        String message;
        if (version >= 4) {
            message = "Backup V4 importado completo (registros con modalidad, combustibles y bonos)";
        } else if (version >= 3) {
            message = "Backup V3 importado completo (registros con modalidad, combustibles y bonos)";
        } else if (version >= 2) {
            message = "Backup V2 importado — registros viejos marcados como Shopper";
        } else {
            message = "Backup V1 importado — registros marcados como Shopper. Los bonos actuales se mantuvieron";
        }
        return new ImportDecision(version >= 2, version < 3, message);
    }

    public static final class ImportDecision {
        public final boolean restoreBonos;
        public final boolean fillShopperModalidad;
        public final String message;

        private ImportDecision(boolean restoreBonos, boolean fillShopperModalidad, String message) {
            this.restoreBonos = restoreBonos;
            this.fillShopperModalidad = fillShopperModalidad;
            this.message = message;
        }
    }
}
