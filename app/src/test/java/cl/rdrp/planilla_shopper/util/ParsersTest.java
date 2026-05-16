package cl.rdrp.planilla_shopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParsersTest {

    // ===== parseIntOrNull =====

    @Test
    public void parseIntOrNull_null_returnsNull() {
        assertNull(Parsers.parseIntOrNull(null));
    }

    @Test
    public void parseIntOrNull_empty_returnsNull() {
        assertNull(Parsers.parseIntOrNull(""));
        assertNull(Parsers.parseIntOrNull("   "));
    }

    @Test
    public void parseIntOrNull_onlyNonDigits_returnsNull() {
        assertNull(Parsers.parseIntOrNull("abc"));
        assertNull(Parsers.parseIntOrNull("$,. -"));
    }

    @Test
    public void parseIntOrNull_validDigits_parsed() {
        assertEquals(Integer.valueOf(123), Parsers.parseIntOrNull("123"));
        assertEquals(Integer.valueOf(123), Parsers.parseIntOrNull(" 123 "));
    }

    @Test
    public void parseIntOrNull_stripsNonDigits() {
        // "$1,234" -> conserva solo digitos
        assertEquals(Integer.valueOf(1234), Parsers.parseIntOrNull("$1,234"));
        assertEquals(Integer.valueOf(123456), Parsers.parseIntOrNull("123abc456"));
    }

    // ===== parseIntOrZero =====

    @Test
    public void parseIntOrZero_null_returnsZero() {
        assertEquals(0, Parsers.parseIntOrZero(null));
    }

    @Test
    public void parseIntOrZero_empty_returnsZero() {
        assertEquals(0, Parsers.parseIntOrZero(""));
        assertEquals(0, Parsers.parseIntOrZero("   "));
    }

    @Test
    public void parseIntOrZero_onlyNonDigits_returnsZero() {
        assertEquals(0, Parsers.parseIntOrZero("abc"));
    }

    @Test
    public void parseIntOrZero_validDigits_parsed() {
        assertEquals(123, Parsers.parseIntOrZero("123"));
        assertEquals(123, Parsers.parseIntOrZero(" 123 "));
        assertEquals(1234, Parsers.parseIntOrZero("$1,234"));
    }

    // ===== parseLongOrNull =====

    @Test
    public void parseLongOrNull_null_returnsNull() {
        assertNull(Parsers.parseLongOrNull(null));
    }

    @Test
    public void parseLongOrNull_empty_returnsNull() {
        assertNull(Parsers.parseLongOrNull(""));
        assertNull(Parsers.parseLongOrNull("   "));
    }

    @Test
    public void parseLongOrNull_onlyNonDigits_returnsNull() {
        assertNull(Parsers.parseLongOrNull("abc"));
    }

    @Test
    public void parseLongOrNull_validDigits_parsed() {
        assertEquals(Long.valueOf(123L), Parsers.parseLongOrNull("123"));
    }

    @Test
    public void parseLongOrNull_largerThanIntMax() {
        // 9_999_999_999 no cabe en int pero si en long
        assertEquals(Long.valueOf(9999999999L), Parsers.parseLongOrNull("9999999999"));
    }

    // ===== parseDoubleOrNull =====

    @Test
    public void parseDoubleOrNull_null_returnsNull() {
        assertNull(Parsers.parseDoubleOrNull(null));
    }

    @Test
    public void parseDoubleOrNull_empty_returnsNull() {
        assertNull(Parsers.parseDoubleOrNull(""));
        assertNull(Parsers.parseDoubleOrNull("   "));
    }

    @Test
    public void parseDoubleOrNull_onlyDot_returnsNull() {
        assertNull(Parsers.parseDoubleOrNull("."));
    }

    @Test
    public void parseDoubleOrNull_commaAsSeparator_parsed() {
        assertEquals(Double.valueOf(1.5), Parsers.parseDoubleOrNull("1,5"));
    }

    @Test
    public void parseDoubleOrNull_dotAsSeparator_parsed() {
        assertEquals(Double.valueOf(1.5), Parsers.parseDoubleOrNull("1.5"));
    }

    @Test
    public void parseDoubleOrNull_stripsNonDigits() {
        assertEquals(Double.valueOf(123.45), Parsers.parseDoubleOrNull("$123.45 km"));
    }

    @Test
    public void parseDoubleOrNull_multipleDots_keepsFirst() {
        // "1..2" -> tras limpieza queda "1." -> "1" -> 1.0
        assertEquals(Double.valueOf(1.0), Parsers.parseDoubleOrNull("1..2"));
    }

    @Test
    public void parseDoubleOrNull_thousandsSeparatorMix() {
        // "1,234.56" -> coma a punto -> "1.234.56" -> mantiene primer punto -> "1.234"
        // (es el comportamiento actual; no es perfecto, pero es lo que ya hace el codigo)
        assertEquals(Double.valueOf(1.234), Parsers.parseDoubleOrNull("1,234.56"));
    }

    @Test
    public void parseDoubleOrNull_negativeIsStripped() {
        // El "-" se elimina porque no es digito ni punto: "-3.5" -> "3.5"
        // Comportamiento actual; los originales no soportan negativos.
        assertEquals(Double.valueOf(3.5), Parsers.parseDoubleOrNull("-3.5"));
    }
}
