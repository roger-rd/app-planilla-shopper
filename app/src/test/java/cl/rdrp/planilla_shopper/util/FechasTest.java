package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.ParseException;
import java.util.Date;

public class FechasTest {

    // ===== toLegacy =====

    @Test
    public void toLegacy_null_returnsEmpty() {
        assertEquals("", Fechas.toLegacy(null));
    }

    @Test
    public void toLegacy_empty_returnsEmpty() {
        assertEquals("", Fechas.toLegacy(""));
    }

    @Test
    public void toLegacy_whitespace_returnsEmpty() {
        // trim deja "", length < 10 -> devuelve ""
        assertEquals("", Fechas.toLegacy("   "));
    }

    @Test
    public void toLegacy_shorterThan10_returnsTrimmedInput() {
        assertEquals("abc", Fechas.toLegacy("abc"));
        assertEquals("2026-05", Fechas.toLegacy("2026-05"));
    }

    @Test
    public void toLegacy_validISO_converts() {
        assertEquals("16/05/2026", Fechas.toLegacy("2026-05-16"));
        assertEquals("01/01/2024", Fechas.toLegacy("2024-01-01"));
    }

    @Test
    public void toLegacy_isoWithSurroundingSpaces_trimsAndConverts() {
        assertEquals("16/05/2026", Fechas.toLegacy("  2026-05-16  "));
    }

    @Test
    public void toLegacy_longerThan10_takesFirst10() {
        // Solo usa los primeros 10 caracteres bien posicionados
        assertEquals("16/05/2026", Fechas.toLegacy("2026-05-16T12:00:00"));
    }

    // ===== hoyISO =====

    @Test
    public void hoyISO_returnsTenCharsInISOShape() {
        String today = Fechas.hoyISO();
        assertNotNull(today);
        assertEquals(10, today.length());
        // forma yyyy-MM-dd
        assertTrue("formato inesperado: " + today, today.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    // ===== parseISO + formatISO roundtrip =====

    @Test
    public void parseISO_formatISO_roundtrip() throws ParseException {
        String iso = "2026-05-16";
        Date d = Fechas.parseISO(iso);
        assertNotNull(d);
        assertEquals(iso, Fechas.formatISO(d));
    }

    @Test(expected = ParseException.class)
    public void parseISO_invalidThrows() throws ParseException {
        Fechas.parseISO("no-es-fecha");
    }
}
