package cl.rdrp.planilla_shopper.util;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

/** Configuración inmutable del bono nuevo; no depende de Android ni de la policy legacy. */
public final class BonoConfig {
    public final boolean activo;
    public final double monto;
    public final Set<Integer> dias;
    public final Set<String> salas;

    public BonoConfig(boolean activo, double monto, Collection<Integer> dias, Collection<String> salas) {
        this.activo = activo;
        this.monto = monto;
        TreeSet<Integer> d = new TreeSet<>();
        if (dias != null) for (Integer dia : dias) {
            if (dia != null && dia >= Calendar.SUNDAY && dia <= Calendar.SATURDAY) d.add(dia);
        }
        TreeSet<String> s = new TreeSet<>();
        if (salas != null) for (String sala : salas) {
            if (sala != null && !sala.trim().isEmpty()) s.add(sala.trim());
        }
        this.dias = Collections.unmodifiableSet(d);
        this.salas = Collections.unmodifiableSet(s);
    }

    public static Set<Integer> parseDias(String texto) {
        Set<Integer> dias = new TreeSet<>();
        if (texto != null) for (String token : texto.split(",")) {
            try {
                int dia = Integer.parseInt(token.trim());
                if (dia >= Calendar.SUNDAY && dia <= Calendar.SATURDAY) dias.add(dia);
            } catch (NumberFormatException ignored) { /* Ignora tokens dañados. */ }
        }
        return dias;
    }

    public static Set<String> parseSalas(String texto) {
        Set<String> salas = new TreeSet<>();
        if (texto != null) for (String token : texto.split(",")) {
            if (!token.trim().isEmpty()) salas.add(token.trim());
        }
        return salas;
    }

    /** ISO yyyy-MM-dd completo y válido, independiente del locale del dispositivo. */
    public static boolean derecho(BonoConfig config, String fechaIso, String sala) {
        if (!config.activo || sala == null || !config.salas.contains(sala.trim())
                || fechaIso == null || !fechaIso.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        parser.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        Date fecha = parser.parse(fechaIso, pos);
        if (fecha == null || pos.getIndex() != fechaIso.length()) return false;
        Calendar cal = Calendar.getInstance(Locale.ROOT);
        cal.setTime(fecha);
        return config.dias.contains(cal.get(Calendar.DAY_OF_WEEK));
    }
}
