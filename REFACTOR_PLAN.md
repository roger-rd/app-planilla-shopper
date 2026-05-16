# Plan de Refactor — Planilla Shopper

**App:** `cl.rdrp.planilla_shopper` (Java, Android nativo)
**Versión actual:** 1.0.10 (versionCode 27)
**Room DB:** `foxer.db` versión 10
**Principio rector:** estabilidad > elegancia. Ninguna fase rompe funcionalidad existente. Cada fase se commitea, se prueba y se taggea por separado.

---

## Principios generales (aplican a TODAS las fases)

- **Un commit por fase**, con un mensaje claro tipo `refactor(fase-1): limpieza de gradle e imports`.
- **Un tag git por fase completada y validada**, por ejemplo `refactor-fase1-ok`. Si algo sale mal, vuelves al tag anterior con `git reset --hard`.
- **Nunca subir `versionCode`** durante el refactor a menos que se publique. Si publicas a Play, súbelo al final del bloque de fases que vayan juntas.
- **No tocar el esquema de Room** sin una migración correspondiente (más detalle abajo).
- **Probar en un dispositivo real con datos reales** (o copia de tu base productiva) después de cada fase, no solo en el emulador.
- **Conservar el APK firmado de la versión actual** como respaldo de emergencia, además del backup del código fuente que ya hiciste.

---

## Fase 0 — Preparación y baseline

**Objetivo:** dejar un punto de retorno seguro y un set de pruebas manuales repetible.

**Archivos a modificar:** ninguno (solo trabajo de organización).

**Acciones:**

- Crear tag git `baseline-pre-refactor` sobre el commit actual.
- Instalar el APK actual (versión 1.0.10) en tu dispositivo de pruebas y dejarlo ahí como referencia visual.
- Exportar un backup completo desde la propia app (con `BackupActivity`) y guardarlo en una carpeta `backups/baseline/` fuera del proyecto.
- Hacer una copia del archivo `foxer.db` real (puedes sacarla desde `/data/data/cl.rdrp.planilla_shopper/databases/` si tienes acceso, o usar el backup JSON de la app).
- Tomar capturas de pantalla de cada actividad principal: Main, Dashboard, Monthly Summary, Vista General, Bencina, Historial Bencina, Parámetros, Backup. Esto te sirve para comparar visualmente después de cada fase.
- Definir tu **checklist de pruebas manuales estándar** (te lo dejo más abajo en la sección "Checklist").

**Riesgos:** ninguno, es solo preparación.

**Validación:** confirmas que tienes el tag, el APK, el backup y las capturas guardadas.

**Qué respaldar antes:** backup completo del proyecto (ya lo hiciste) + backup de datos.

---

## Fase 1 — Limpieza segura (sin tocar lógica)

**Objetivo:** quitar ruido y deuda menor que no afecta el comportamiento de la app.

**Archivos a modificar:**

- `app/build.gradle.kts` → eliminar la línea duplicada `annotationProcessor(libs.room.compiler)` (aparece dos veces).
- Cualquier archivo con imports sin usar o con imports con nombre completo redundante (por ejemplo, en `MainActivity.java` hay muchos `cl.rdrp.planilla_shopper.data.AppDatabase.get(this)` que podrían ser `AppDatabase.get(this)` si se importa una sola vez).
- Eliminar la clase interna `BackupData` de `MainActivity.java` si no se usa ahí (verificar dónde se referencia primero).
- Limpiar `TODO` y comentarios obsoletos (solo lectura/cosmético).

**Lo que NO se toca en esta fase:**

- Lógica de negocio (`Config.java` se mantiene tal cual).
- Esquemas de Room.
- Comportamiento de UI.
- Strings visibles al usuario.

**Riesgos:** muy bajos. El único riesgo real es eliminar un import que sí se usa o borrar `BackupData` si está referenciada — por eso se revisa con `Find Usages` antes de borrar.

**Cómo validar:**

- Compila sin warnings nuevos: `./gradlew assembleDebug`.
- Build release también compila: `./gradlew assembleRelease`.
- Lint no reporta nada crítico nuevo: `./gradlew lint`.

**Qué probar manualmente:** checklist completo (ver final del documento). Como no cambia lógica, todo debe verse y funcionar exactamente igual.

**Qué respaldar antes:** nada extra, basta con el tag de Fase 0.

**Criterio de éxito para taggear `refactor-fase1-ok`:** APK debug + release compilan, checklist pasa al 100%, comparas capturas con las de Fase 0 y no hay diferencias visuales.

---

## Fase 2 — Helpers reutilizables (utilidades puras)

**Objetivo:** extraer funciones que están repetidas en muchas actividades a clases utilitarias *puras* (sin estado, sin dependencias de Android salvo `Context` cuando sea necesario). No se cambia ninguna actividad todavía: solo se crean los helpers y se prueban con tests unitarios.

**Archivos NUEVOS a crear:**

- `app/src/main/java/cl/rdrp/planilla_shopper/util/Fechas.java` — formateo y parseo de fechas (`hoyISO()`, `toLegacy()`, `parseISO()`, `formatISO(Date)`, etc.).
- `app/src/main/java/cl/rdrp/planilla_shopper/util/Parsers.java` — los `parseIntOnlyDigits`, `parseDoubleStrict`, `parseLongStrict` que están en `MainActivity`.
- `app/src/main/java/cl/rdrp/planilla_shopper/util/TextUtils.java` (opcional) — el helper `s(CharSequence)` que limpia y trimea.

**Archivos a modificar:** ninguno todavía en esta fase. Solo se crean los helpers.

**Archivos NUEVOS de test:**

- `app/src/test/java/cl/rdrp/planilla_shopper/util/FechasTest.java`
- `app/src/test/java/cl/rdrp/planilla_shopper/util/ParsersTest.java`

**Riesgos:** prácticamente nulos porque los helpers no se llaman desde ningún lado todavía. El único riesgo es escribir helpers que se comporten *distinto* a la lógica embebida actual, pero eso se previene cubriéndolos con tests basados en los mismos inputs que usa hoy la app (números con coma, con punto, con símbolos basura, vacíos, nulos, etc.).

**Cómo validar:**

- `./gradlew test` corre verde.
- `./gradlew assembleDebug` compila sin warnings nuevos.

**Qué probar manualmente:** nada nuevo, pero igual corre el checklist completo para verificar que no rompiste imports por accidente.

**Qué respaldar antes:** tag de Fase 1.

**Sub-fase 2.1 (separada y opcional):** *recién después* de tener los helpers probados, en un commit aparte, se sustituyen los usos en `MainActivity` y en las demás actividades. Esto se hace clase por clase, no todo de un viaje. Sugerencia de orden: `MainActivity` → `DashboardActivity` → `ParametrosCalculosActivity` → `MonthlySummaryActivity` → el resto.

---

## Fase 3 — Executor único para acceso a base de datos

**Objetivo:** eliminar el patrón `Executors.newSingleThreadExecutor()` que aparece en muchas operaciones de BD, y centralizarlo en un único executor reutilizable. Esto reduce creación de hilos y prepara el terreno para Fase 6 (ViewModel).

**Archivos a modificar:**

- `app/src/main/java/cl/rdrp/planilla_shopper/data/AppDatabase.java` → añadir un campo estático `public static final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();`.
- Todas las actividades que actualmente crean executors: `MainActivity`, `DashboardActivity`, `BencinaActivity`, `HistorialBencinaActivity`, `VistaGeneralActivity`, `MonthlySummaryActivity`, `BackupActivity`, `ParametrosCalculosActivity` — reemplazar `Executors.newSingleThreadExecutor().execute(...)` por `AppDatabase.ioExecutor.execute(...)`.

**Riesgos:**

- Si dos pantallas hacen operaciones largas a la vez, ahora se serializan (antes corrían en paralelo en sus propios hilos). Para una app de este tamaño es **mejor**, no peor (Room ya serializa internamente sus escrituras), pero conviene tenerlo presente.
- Si por error se hace `runOnUiThread` desde un contexto en que la activity ya fue destruida, podrías ver un crash. Pero ya tenías ese mismo riesgo antes; no es nuevo de esta fase.

**Cómo validar:**

- Compila: `./gradlew assembleDebug`.
- Buscar en todo el código (`grep -r "newSingleThreadExecutor" app/src/main/`) para confirmar que ya no queda ninguno fuera de `AppDatabase`.

**Qué probar manualmente:**

- Guardar varios registros seguidos rapidito.
- Editar y eliminar un registro inmediatamente después de crearlo.
- Agregar y eliminar bonos.
- Crear un combustible.
- Abrir Dashboard y Vista General con muchos datos para ver que la lista carga bien.
- Hacer Backup y Restore.
- Rotar el teléfono mientras una operación de BD está en curso (no debe crashear).

**Qué respaldar antes:** tag de Fase 2 (con sub-fase 2.1 cerrada).

---

## Fase 4 — SharedPreferences para parámetros de cálculo

**Objetivo:** mover las constantes de `Config.java` a `SharedPreferences` y conectarlas con `ParametrosCalculosActivity`, *manteniendo los valores actuales como defaults*. Esto te permite cambiar tarifas sin recompilar.

**Esta es la fase más delicada del refactor.** Aquí es donde más cuidado hay que tener para no afectar cálculos históricos.

**Archivos a modificar:**

- `app/src/main/java/cl/rdrp/planilla_shopper/util/Prefs.java` → extender con getters/setters para cada parámetro (`getValorUnitSku()`, `getValorUnitKm()`, `getComisionPorc()`, `getRendimientoKmPorLitro()`, `getPrecioLitro()`, `getBonoPorKmDomLunMar()`).
- `app/src/main/java/cl/rdrp/planilla_shopper/util/Config.java` → cada método/constante pasa a leer de `Prefs` con el valor actual como default. Las constantes `public static final` se mantienen como `DEFAULT_*` para fallback.
- `app/src/main/java/cl/rdrp/planilla_shopper/ui/ParametrosCalculosActivity.java` → conectar los EditText con los getters/setters de `Prefs` (load al abrir, save al confirmar).

**Lo que NO se toca:**

- La lógica de `basePorSku()` (los tramos) — esos quedan duros por ahora. Si querés que también sean configurables, va en una sub-fase 4.1 aparte.
- La lógica de bonos por día de semana (lunes/martes/domingo) — la regla queda dura, solo el monto pasa a ser configurable.
- Las fechas de cambio de tarifa (2026-03-01 y 2026-03-26) — quedan duras. Mover eso a Prefs es complicado porque rompe la coherencia histórica.

**Riesgos (importantes):**

1. **Cálculos históricos:** si un registro se creó con `VALOR_UNIT_KM = 234` y luego el usuario lo cambia a `300`, al reabrir el Dashboard de ese día se recalculará con `300`. Esto **ya pasa hoy** (la app no persiste el monto calculado, lo recalcula al vuelo desde los KM crudos), pero al hacer los valores configurables se vuelve mucho más visible. Hay que avisarle al usuario.
2. **Defaults consistentes:** el primer arranque tras el upgrade debe leer exactamente los mismos defaults que las constantes actuales, o el primer cálculo después del update va a dar distinto.
3. **Validación de inputs:** la pantalla de parámetros debe rechazar valores no numéricos / negativos, o vas a guardar basura en Prefs.

**Cómo validar:**

- **Antes** de tocar nada, anota en un papel: total de un día específico, total de comisión, valor KM, base por SKU. Saca capturas del Dashboard.
- Después de la fase, sin tocar parámetros, abre la app y verifica que esos mismos números dan exactamente igual. Cualquier diferencia de un peso significa que un default está mal.
- Cambia un parámetro (por ejemplo `VALOR_UNIT_KM` de 234 a 235), confirma que el Dashboard refleja el cambio, vuélvelo a 234 y verifica que vuelve al valor original.
- Test unitario nuevo en `ConfigTest.java` que verifique que los defaults coinciden con las constantes actuales.

**Qué probar manualmente:** checklist completo + un día completo de operación simulada.

**Qué respaldar antes:** tag de Fase 3 + backup JSON desde la app + captura de los totales actuales para cada día con datos.

---

## Fase 5 — Backup completo (Combustible + Bonos)

**Objetivo:** que el backup incluya **todas** las tablas, no solo `Registro`. Hoy `BackupData` solo guarda `registros`, lo que significa que un usuario que restaure pierde combustible y bonos. Esto es un bug latente.

**Esta fase es crítica para compatibilidad de backups.** Hay que versionar el formato.

**Archivos a modificar:**

- `app/src/main/java/cl/rdrp/planilla_shopper/ui/BackupActivity.java` (principal).
- Crear `app/src/main/java/cl/rdrp/planilla_shopper/data/BackupDataV2.java` (o similar) con campos `int version = 2`, `List<Registro> registros`, `List<Combustible> combustibles`, `List<BonoExtra> bonos`.
- Añadir DAOs `getAllSync()`, `insertAllSync()`, `deleteAllSync()` en `CombustibleDao` y `BonoDao` si no existen (verificar primero — `RegistroDao` ya los tiene).

**Estrategia de compatibilidad (no negociable):**

- El restore debe **detectar la versión** del archivo JSON. Si no tiene campo `version` o es `version = 1`, se trata como el formato viejo (solo `registros`).
- El export siempre escribe en `version = 2`.
- Nunca eliminar la capacidad de leer V1.

**Riesgos:**

1. **Backups viejos en el celular del usuario:** si un usuario tiene un JSON exportado hace meses y lo restaura, tiene que seguir funcionando. Por eso la detección de versión.
2. **Restore parcial:** si el JSON V2 trae `bonos` pero la tabla `bonos` no existe (versión vieja de la app), Room debería tenerlo bien manejado porque ya forzamos `MIGRATION_9_10`, pero hay que verificar que el DAO de Bonos esté disponible.
3. **Duplicados al restaurar:** el restore actual hace `deleteAllSync()` + `insertAllSync()`. Hay que hacer lo mismo con las tablas nuevas para no quedarse con datos mezclados.

**Cómo validar:**

- Genera un backup en la versión actual (1.0.10) — ese es tu archivo V1 de referencia.
- Tras aplicar la fase: importa el V1 → debe restaurar solo registros, sin crashear, dejando bonos/combustible vacíos.
- Genera un backup nuevo → debe ser V2 con las tres listas.
- Importa el V2 → debe restaurar todo.
- Test unitario que parsea un JSON V1 hardcoded y un JSON V2 hardcoded.

**Qué probar manualmente:**

- Exportar backup.
- Borrar manualmente algunos registros, bonos y combustibles.
- Importar el backup → verificar que vuelve todo.
- Repetir con el JSON V1 viejo.

**Qué respaldar antes:** tag de Fase 4 + **dos archivos JSON de prueba** (uno V1 generado antes, uno V2 generado después) guardados en carpeta segura.

---

## Fase 6 — ViewModel y separación de lógica

**Objetivo:** mover la lógica de `MainActivity` (que tiene casi 500 líneas) a un `MainViewModel`, usando `LiveData` para que la UI observe cambios. Esto sobrevive a rotaciones de pantalla y separa responsabilidades.

**Empezar por UNA sola pantalla.** No hacer las 8 actividades a la vez.

**Archivos a modificar/crear (solo para MainActivity en esta fase):**

- Crear `app/src/main/java/cl/rdrp/planilla_shopper/ui/MainViewModel.java`.
- Modificar `app/src/main/java/cl/rdrp/planilla_shopper/ui/MainActivity.java` para que use el ViewModel.
- (Opcional) crear `app/src/main/java/cl/rdrp/planilla_shopper/data/RegistroRepository.java` y `BonoRepository.java` si querés una capa intermedia.

**Lo que NO se toca:**

- Las otras actividades (`DashboardActivity`, `BencinaActivity`, etc.) — eso es Fase 6.1, 6.2, etc., cada una en su propio ciclo.
- La estructura de Room.

**Riesgos:**

- Cambio de paradigma: si ya tenés costumbres particulares (por ejemplo recargar lista al volver de otra actividad con `onResume()`), hay que replicarlas con `LiveData` o se rompen sutilmente.
- Tests existentes pueden fallar si dependían de campos privados de la activity.

**Cómo validar:**

- Compila.
- La pantalla principal abre, guarda, edita, elimina y agrega bonos exactamente igual que antes.
- **Rotación:** rotar el teléfono no debe perder el estado de la fecha seleccionada ni la lista cargada (esto era frágil antes; ahora debería ser sólido).
- Volver desde Dashboard y otras actividades debe seguir mostrando la lista correcta.

**Qué probar manualmente:** checklist completo + casos de rotación + casos de "salir y volver".

**Qué respaldar antes:** tag de Fase 5.

**Sub-fases 6.1 a 6.n:** una por actividad, en orden de menor a mayor complejidad. Sugerencia: `BencinaActivity` (simple) → `HistorialBencinaActivity` → `MonthlySummaryActivity` → `VistaGeneralActivity` → `DashboardActivity` (la más compleja, al final).

---

## Fase 7 — Mejoras opcionales de futuro (no urgentes)

Ninguna de estas es necesaria. Las dejo listadas para que decidas si algún día las querés abordar, pero **no entran en este roadmap** salvo que me digas que sí.

- Renombrar la base de datos de `foxer.db` a `planilla_shopper.db` (requiere migración manual de archivo o aceptar que los usuarios actuales mantienen `foxer.db`).
- Migrar progresivamente a **Kotlin** (Android Studio lo facilita archivo por archivo, pero es trabajo grande).
- Inyección de dependencias con **Hilt** (overkill para esta app, pero ordena mucho).
- Pasar a **Jetpack Compose** las pantallas nuevas (no migrar las existentes, mezclar).
- **WorkManager** para backups automáticos diarios.

---

## Qué NO conviene tocar todavía (vale para TODAS las fases hasta nuevo aviso)

- El esquema de Room (`@Entity` de `Registro`, `Combustible`, `BonoExtra`).
- El nombre de la base: `foxer.db`.
- El número de versión de Room (`version = 10`).
- Las migraciones existentes (6→7, 7→8, 8→9, 9→10).
- El `applicationId` `cl.rdrp.planilla_shopper`.
- Las fechas hardcoded de cambio de tarifa (`2026-03-01` y `2026-03-26`) en `Config.java`. Esas son verdad histórica.
- Los iconos, splash, y recursos drawable.

---

## Cambios que afectarían Room (cuidado especial)

Cualquiera de estos requiere una **nueva migración** y subir la versión de Room:

- Añadir, eliminar o renombrar una columna en `Registro`, `Combustible` o `BonoExtra`.
- Añadir o eliminar una tabla.
- Cambiar el tipo de una columna (por ejemplo `int` → `long`).
- Cambiar índices.
- Cambiar el `primary key`.

**Regla práctica:** si tocás un archivo en `data/` que tenga `@Entity`, `@Dao` con un `@Query` nuevo, o `AppDatabase.java`, parate y pensá si necesitás migración. En las fases 1–6 de este plan, **nada de esto debería pasar**.

---

## Cambios que pueden romper backups existentes

- Renombrar campos de `Registro`, `Combustible` o `BonoExtra` (Gson serializa por nombre).
- Cambiar tipos (por ejemplo `long` → `String`).
- Eliminar la clase `BackupData` original sin reemplazo compatible.
- Cambiar el formato JSON sin un campo `version` que permita detectarlo.

**Regla práctica:** todo cambio en el formato del backup tiene que ser **aditivo** y **versionado**. La Fase 5 está diseñada con esto en mente.

---

## Checklist de pruebas manuales (correr después de CADA fase)

Esta lista es tu red de seguridad. Si algo de aquí falla después de una fase, no taguées como OK — investigá primero.

**Registros (Main):**

- Crear un registro nuevo con la fecha de hoy.
- Crear un registro con fecha de hace una semana.
- Editar un registro existente (cambiar SKU, KM, ventana).
- Eliminar un registro y confirmar que desaparece.
- Cambiar la fecha en el selector y ver que la lista se actualiza al día seleccionado.

**Bonos:**

- Agregar un bono con descripción.
- Agregar un bono sin descripción.
- Eliminar un bono.

**Combustible:**

- Registrar una carga de bencina nueva.
- Ver el historial de cargas.
- Verificar que kmRecorridos se calcula bien.

**Dashboard:**

- Abrir Dashboard y comparar el total con el de antes de la fase (mismo día → mismo número, al peso).
- Ver que los gráficos cargan.

**Vista General y Monthly Summary:**

- Abrir cada pantalla con datos.
- Verificar que los totales mensuales coinciden con lo esperado.

**Backup:**

- Exportar backup.
- Restaurar el backup recién exportado y verificar que no se duplica ni se pierde nada.

**Parámetros (especialmente después de Fase 4):**

- Cambiar un valor, ver impacto en Dashboard, volverlo al original.

**Navegación general:**

- Abrir y cerrar el drawer.
- Navegar a cada actividad desde el drawer y volver con back.
- Rotar el teléfono en cada actividad sin perder estado.

**Edge cases:**

- App en segundo plano y volver.
- Sin conexión (no debería importar, todo es local, pero confirmar).
- Cambio de día (medianoche): si dejás la app abierta y cruzás medianoche, la fecha "hoy" debería actualizarse al volver a Main.

---

## Resumen de orden recomendado

| Fase | Riesgo | Reversibilidad | Tiempo estimado |
|------|--------|----------------|-----------------|
| 0 — Preparación | Nulo | Total | 30 min |
| 1 — Limpieza | Muy bajo | Fácil | 1 h |
| 2 — Helpers (crear) | Nulo | Trivial | 2 h |
| 2.1 — Helpers (sustituir usos) | Bajo | Fácil | 2–3 h |
| 3 — Executor único | Bajo | Fácil | 1 h |
| 4 — SharedPreferences | **Medio-alto** | Media | 3–4 h |
| 5 — Backup completo | **Alto** (afecta datos) | Media (requiere V1/V2) | 3 h |
| 6 — ViewModel (Main) | Medio | Media | 3 h |
| 6.x — ViewModel (resto) | Medio | Media | 2 h cada una |

---

**Cuando estés listo, me dices "arrancamos con Fase 1" (o la que quieras) y vamos paso a paso. Antes de cada fase te recuerdo qué respaldar y al final te paso el checklist para que pruebes.**
