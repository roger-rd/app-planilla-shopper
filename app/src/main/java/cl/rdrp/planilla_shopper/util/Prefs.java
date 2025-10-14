package cl.rdrp.planilla_shopper.util;

import android.content.Context;

public class Prefs {
    private static final String NAME = "rdrp_prefs";
    private static final String K_LOCAL = "local_default";

    public static void setLocal(Context c, String local) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .edit().putString(K_LOCAL, local).apply();
    }
    public static String getLocal(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString(K_LOCAL, "");
    }
    public static boolean hasLocal(Context c) {
        return !getLocal(c).isEmpty();
    }
}