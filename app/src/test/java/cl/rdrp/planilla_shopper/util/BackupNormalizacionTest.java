package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.data.Registro;
import cl.rdrp.planilla_shopper.data.Modalidad;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackupNormalizacionTest {
    private final Gson gson = new Gson();

    private Registro legacy(String fecha, String sala) {
        Registro r = new Registro(fecha, sala, "11", 0, 0, 1, 0);
        r.modalidad = Modalidad.SHOPPER.name();
        CalculoNuevoRegistro.completar(r, 2, 1, 3026, 61, 276, 100,
                Config.bonoKmAplica(fecha, Modalidad.SHOPPER, sala) ? 300 : 0,
                .15, 10, 1200);
        return r;
    }

    private BackupData restaurar(int version, Registro... registros) {
        BackupData b = new BackupData();
        b.version = version;
        b.registros = Arrays.asList(registros);
        b.combustibles = Collections.emptyList();
        b.bonos = Collections.emptyList();
        BackupData restored = gson.fromJson(gson.toJson(b), BackupData.class);
        BackupNormalizacion.normalizarImportados(restored.registros);
        return restored;
    }

    @Test public void respaldoMixtoV5ConservaDerechoNuevoYNormalizaSolo111() {
        Registro nuevo = legacy("2026-03-04", "140"); // miércoles
        BonoConfig bono = new BonoConfig(true, 150,
                Arrays.asList(Calendar.MONDAY, Calendar.WEDNESDAY), Collections.singleton("140"));
        CalculoNuevoRegistro.completarNuevo(nuevo, 2, 1, 3026, 61, 321, bono, nuevo.local, .15, 10, 1200);
        assertEquals(3, nuevo.calc_origen);
        String original = gson.toJson(nuevo);
        BackupData b = restaurar(BackupVersion.BACKUP_VERSION_CURRENT, nuevo,
                legacy("2026-03-02", "140"), legacy("2026-03-02", "139"));
        assertEquals(5, b.version);
        Registro n = b.registros.get(0), sin = b.registros.get(1), con = b.registros.get(2);
        assertEquals(original, gson.toJson(n));
        assertEquals(450, n.monto_bono_km);
        assertEquals(963, n.monto_km);
        assertEquals(0, sin.bono_por_km, 0);
        assertEquals(100, con.bono_por_km, 0);
        for (Registro r : b.registros) {
            assertEquals(3, r.calc_origen);
            CalculoNuevoRegistro.editarKm(r, 4, 1);
        }
        assertEquals(150, n.bono_por_km, 0);
        assertEquals(750, n.monto_bono_km);
        assertEquals(1605, n.monto_km);
        assertEquals(0, sin.monto_bono_km);
        assertEquals(500, con.monto_bono_km);
    }

    @Test public void v4ConservaHistoricosYMontos111YEsIdempotente() {
        Registro historico = legacy("2026-03-04", "140"); historico.calc_origen = 0;
        Registro viejo = legacy("2026-03-04", "139");
        viejo.monto_bono_km = 777; viejo.monto_bruto = 888;
        String h = gson.toJson(historico);
        BackupData b = restaurar(4, historico, viejo);
        assertTrue(BackupVersion.forImport(b.version).restoreBonos);
        assertEquals(h, gson.toJson(b.registros.get(0)));
        assertEquals(3, b.registros.get(1).calc_origen);
        assertEquals(0, b.registros.get(1).bono_por_km, 0);
        assertEquals(777, b.registros.get(1).monto_bono_km);
        assertEquals(888, b.registros.get(1).monto_bruto);
        String antes = gson.toJson(b);
        BackupNormalizacion.normalizarImportados(b.registros);
        assertEquals(antes, gson.toJson(b));
        BackupNormalizacion.normalizarImportados(null);
    }

    @Test public void editar111NormalizaAntesDeRecalcularEnUnaLlamada() {
        for (boolean soloKm : new boolean[]{true, false}) {
            Registro r = legacy("2026-03-04", "140");
            if (soloKm) CalculoNuevoRegistro.editarKm(r, 4, 1);
            else CalculoNuevoRegistro.editarSkuYKm(r, 31, 4, 1, (sku, m, f) -> 4000);
            assertEquals(3, r.calc_origen);
            assertEquals(0, r.bono_por_km, 0);
            assertEquals(0, r.monto_bono_km);
            assertEquals(1380, r.monto_km);
            assertEquals(r.monto_base + r.monto_sku + r.monto_km, r.monto_bruto);
        }
    }

    @Test public void editarSinCambiarKmTambienConvierteMarcadorConservandoMontos() {
        Registro r = legacy("2026-03-04", "140");
        long bruto = r.monto_bruto;
        CalculoNuevoRegistro.editarKm(r, 2, 1);
        assertEquals(3, r.calc_origen);
        assertEquals(0, r.bono_por_km, 0);
        assertEquals(bruto, r.monto_bruto);
    }

    @Test public void lectoresAceptanSoloDosYTres() {
        Registro r = legacy("2026-03-02", "139");
        for (int origen : new int[]{0, 1, 2, 3, 4}) {
            r.calc_origen = origen;
            assertEquals(origen == 2 || origen == 3, MontosRegistro.usaGuardados(r));
        }
    }

    @Test public void pickerNuevoNoTieneDerechoAunqueConfiguracionLoHabilite() {
        Registro r = legacy("2026-03-04", "140"); r.modalidad = Modalidad.PICKER.name();
        CalculoNuevoRegistro.completarNuevo(r, 2, 1, 3026, 61, 321,
                new BonoConfig(true, 150, Collections.singleton(Calendar.WEDNESDAY), Collections.singleton("140")),
                r.local, .15, 10, 1200);
        assertEquals(3, r.calc_origen);
        assertEquals(0, r.bono_por_km, 0);
        assertEquals(0, r.monto_bono_km);
    }
}
