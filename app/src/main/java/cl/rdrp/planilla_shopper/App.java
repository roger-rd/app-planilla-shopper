package cl.rdrp.planilla_shopper;

import android.app.Application;
import android.content.Context;

/**
 * Application class de la app.
 *
 * Su unico proposito es exponer el Context de aplicacion como un campo
 * estatico, para que helpers como Config (que tienen metodos estaticos)
 * puedan acceder a SharedPreferences sin pedir Context en cada firma.
 *
 * Registrada en AndroidManifest.xml via android:name=".App".
 *
 * Android garantiza que Application.onCreate() corre ANTES de cualquier
 * Activity.onCreate, por lo que App.get() siempre tiene el context listo
 * cuando una pantalla intenta leer parametros. Si esta app llegara a
 * tener ContentProviders propios, recordar que esos se inicializan
 * antes de Application.onCreate: en ese caso ese provider no debe
 * tocar Config en su onCreate().
 */
public class App extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    /**
     * Devuelve el Context de aplicacion. Nunca null en codigo normal
     * (post-Application.onCreate). Si se llama desde un punto extrano
     * en que aun no se inicializo, lanza IllegalStateException.
     */
    public static Context get() {
        if (appContext == null) {
            throw new IllegalStateException(
                "App context no inicializado. Verifica que AndroidManifest.xml " +
                "tenga android:name=\".App\" en la etiqueta <application>.");
        }
        return appContext;
    }
}
