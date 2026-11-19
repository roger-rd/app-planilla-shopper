package cl.rdrp.planilla_shopper.util;

import cl.rdrp.planilla_shopper.data.Modalidad;
import cl.rdrp.planilla_shopper.data.Registro;
import org.junit.Test;
import static org.junit.Assert.*;

public class MontosRegistroTest {
    private Registro guardado() {
        Registro r = new Registro("2026-03-30", "139", "11", 2.10, 1, 1, 1);
        r.calc_origen = 2;
        r.monto_base = 3026; r.monto_sku = 671; r.monto_km = 580;
        r.monto_bruto = 4277; r.comision_porc = .1525;
        r.rendimiento_km_litro = 12.3; r.precio_litro = 1234;
        return r;
    }

    @Test public void componentesGuardadosIgnoranCambiosDelProveedorLegacy() {
        Registro r = guardado(); r.monto_bono_km = 211;
        for (int legacy : new int[]{0, 1, 99999}) {
            assertEquals(3026, MontosRegistro.base(r, legacy));
            assertEquals(671, MontosRegistro.sku(r, legacy));
            assertEquals(580, MontosRegistro.km(r, legacy));
            assertEquals(211, MontosRegistro.bonoKm(r, legacy), 0);
            assertEquals(.1525, MontosRegistro.comision(r, legacy), 0);
            assertEquals(12.3, MontosRegistro.rendimiento(r, legacy), 0);
            assertEquals(1234, MontosRegistro.precioLitro(r, legacy));
        }
    }

    @Test public void origenDistintoDeDosYTresDevuelveLegacyExactoSinRedondeos() {
        Registro r = guardado();
        for (int origen : new int[]{0, 1, -1, 4}) {
            r.calc_origen = origen;
            assertFalse(MontosRegistro.usaGuardados(r));
            long base = Config.basePorSku(11, Modalidad.SHOPPER, r.fecha);
            long km = Config.calcularTotalKm(r.km, r.fecha);
            double bono = Config.calcularBonoKm(r.km, r.fecha, Modalidad.SHOPPER, "140");
            assertEquals(base, MontosRegistro.base(r, base));
            assertEquals(11 * 73, MontosRegistro.sku(r, 11 * 73));
            assertEquals(km, MontosRegistro.km(r, km));
            assertEquals(bono, MontosRegistro.bonoKm(r, bono), 0);
            assertEquals(1.23456789, MontosRegistro.bonoKm(r, 1.23456789), 0);
            assertEquals(.17777, MontosRegistro.comision(r, .17777), 0);
            assertEquals(9.876, MontosRegistro.rendimiento(r, 9.876), 0);
            assertEquals(1456, MontosRegistro.precioLitro(r, 1456));
        }
    }

    private long subtotal(Registro r) {
        return MontosRegistro.base(r, 10) + MontosRegistro.sku(r, 20) + MontosRegistro.km(r, 30);
    }

    @Test public void controlShopperDriverPickerYMezcla() {
        Registro r = guardado();
        assertEquals(4277, subtotal(r));
        Registro viejo = guardado(); viejo.calc_origen = 0;
        assertEquals(4337, subtotal(r) + subtotal(viejo));
        r.modalidad = "DRIVER"; r.sku = "0"; r.monto_base = 0; r.monto_sku = 0;
        assertEquals(580, subtotal(r));
        r.modalidad = "PICKER"; r.km = 0; r.monto_base = 3026; r.monto_sku = 671; r.monto_km = 0;
        assertEquals(3697, subtotal(r));
    }

    @Test public void bonoPositivoSeComponeSinUsarBrutoNiTarifas() {
        Registro r = guardado(); r.monto_bono_km = 211;
        r.monto_bruto = -999; r.tarifa_base = -1; r.valor_unit_sku = -1;
        r.valor_km = -1; r.bono_por_km = -1;
        long detalle = subtotal(r);
        double tarjeta = detalle + MontosRegistro.bonoKm(r, 0);
        assertEquals(4277, detalle);
        assertEquals(4488, tarjeta, 0);
        assertEquals(4488, detalle + (long) MontosRegistro.bonoKm(r, 0));
        assertEquals(684.42, tarjeta * MontosRegistro.comision(r, 0), 1e-10);
    }

    @Test public void dashboardRedondeaAlFinalYMonthlyConservaDecimales() {
        Registro r = guardado(); r.comision_porc = .0001;
        double comision = 0, combustible = 0;
        for (int i = 0; i < 2; i++) {
            comision += subtotal(r) * MontosRegistro.comision(r, 0);
            combustible += (r.km / MontosRegistro.rendimiento(r, 0)) * MontosRegistro.precioLitro(r, 0);
        }
        assertEquals(.8554, comision, 1e-12);
        assertEquals(1, Math.round(comision));
        assertEquals(0, 2 * Math.round(subtotal(r) * r.comision_porc));
        assertEquals(Math.round(2 * ((2.10 / 12.3) * 1234)), Math.round(combustible));
        assertNotEquals(2 * Math.round((2.10 / 12.3) * 1234), Math.round(combustible));
    }

    @Test public void rendimientoInvalidoRecuperaAmbasFuentesLegacy() {
        Registro r = guardado();
        for (double rendimiento : new double[]{0, -1}) {
            r.rendimiento_km_litro = rendimiento;
            assertFalse(MontosRegistro.usaCombustibleGuardado(r));
            assertEquals(10, MontosRegistro.rendimiento(r, 10), 0);
            assertEquals(999, MontosRegistro.precioLitro(r, 999));
        }
    }

    @Test public void montosLongNoSeTruncanAInt() {
        Registro r = guardado(); r.monto_base = 3_000_000_000L;
        assertEquals(3_000_000_000L, MontosRegistro.base(r, 0));
        assertEquals(3_000_001_251L, subtotal(r));
    }
}
