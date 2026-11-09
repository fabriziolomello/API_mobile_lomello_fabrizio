# Registro del release

## Datos de la versión

| Campo | Valor |
|---|---|
| applicationId | `com.fabrizio.recordatorios` |
| versionName | `1.0` |
| versionCode | `1` |
| buildType | `release` (sin product flavors; un único proyecto Firebase) |
| Fecha de compilación | 2026-11-09 16:16 (UTC-3) |
| AGP | 9.4.1 |
| Gradle | 9.6.0 |
| Kotlin | 2.2.10 |
| R8 | 9.4.24 (min API 24) |
| JDK (verificación) | JBR de Android Studio, OpenJDK 25.0.3 |
| build-tools (verificación) | 36.0.0 |

## Artefactos

| Artefacto | Ruta | Tamaño | SHA-256 |
|---|---|---|---|
| APK | `app/build/outputs/apk/release/app-release.apk` | 3.280.922 bytes (3,1 MB) | `1f44c5d5558b7c686830e12e41a6785fb53038f29ad4d9894d86da55cd11a451` |
| AAB | `app/build/outputs/bundle/release/app-release.aab` | 7.162.133 bytes (6,8 MB) | `4be17be09e5d403681d301a0e8e1c2ae81b9495103a19973a260ca71eb768b4e` |

El mapping de R8 de esta versión queda en `app/build/outputs/mapping/release/mapping.txt`
(también va embebido en el AAB). Hay que guardarlo para poder des-ofuscar los stack traces
de este release.

## Firma

La firma se configura en `app/build.gradle.kts` a partir de `keystore.properties` (raíz del
proyecto). Ni ese archivo ni el `.jks` se suben a git (`.gitignore`). En este registro no se
incluyen contraseñas ni datos de la keystore.

- Alias de la clave: `recordatorios`
- **APK** (`apksigner verify --verbose --print-certs`, build-tools 36.0.0): **Verifies**.
  - Esquema v2 (APK Signature Scheme v2): verificado. v1, v3 y v4: no se usan (con minSdk 24 alcanza con v2).
  - 1 firmante, clave RSA de 2048 bits.
- **AAB** (`jarsigner -verify -verbose`, JBR de Android Studio): **jar verified**.
  - Las 234 entradas están firmadas y listadas en el manifiesto.
  - SHA256withRSA, clave RSA de 2048 bits.
  - Advertencias esperables: el certificado es autofirmado (clave de subida propia, no la emite
    una CA), la firma no tiene sello de tiempo, y jarsigner avisa de "internal inconsistencies"
    (JarFile/JarInputStream) porque bundletool no escribe el manifiesto como primera entrada.
    Ninguna invalida la firma.

Sin `keystore.properties`, `assembleDebug` y los tests funcionan igual; `assembleRelease` y
`bundleRelease` fallan con el mensaje "No se puede firmar el release: ..." (lo hace la tarea
`verificarFirmaRelease`) y no generan artefactos sin firmar.

## R8 y reglas keep

`optimization { enable = true }` (DSL de AGP 9) activa R8 (shrink + optimización + ofuscación)
y la reducción de recursos. Se quitó el `packageScope` que limitaba la optimización a
`androidx.**`, `kotlin.**` y `kotlinx.**`: ahora R8 procesa todo el código, incluido el de la app.

Reglas propias agregadas en `app/src/main/keepRules/rules.keep`:

| Regla | Motivo |
|---|---|
| `-keepattributes SourceFile,LineNumberTable` | Conservar los números de línea para que los stack traces del release se puedan des-ofuscar con el `mapping.txt`. |
| `-renamesourcefileattribute SourceFile` | Ocultar el nombre real de los archivos fuente en esos stack traces. |

No se agregó ningún `-keep` de clases de la app. Lo que depende de reflexión ya lo cubren las
reglas de consumidor de las librerías:

- Room: `-keep class * extends androidx.room.RoomDatabase { void <init>(); }` (room-runtime).
- DataStore: reglas de protobuf lite (datastore-preferences-core).
- Firestore: el mapeo `Map <-> CloudReminder` es manual (`data/cloud/CloudMappers.kt`, sin `toObject()`).

R8 no informó clases faltantes (`missing_rules.txt` vacío). De las 248 clases de la app,
solo 4 conservan su nombre: `MainActivity`, `RecordatoriosApplication`, `AppDatabase` y
`AppDatabase_Impl` (las referencia el manifiesto o Room).

Observaciones:

- El cliente de MockAPI (Retrofit, `NetworkModule`, `ReminderApi`, `ReminderDto`) no está
  conectado desde la Actividad 4. R8 lo elimina del release; el código fuente sigue en el repo.
- `HttpLoggingInterceptor` (nivel BODY) solo se agregaba en `NetworkModule` y con
  `BuildConfig.DEBUG`. En el release no se instancia nunca: `NetworkModule` no existe en el
  dex. La clase sigue en el APK porque reCAPTCHA (dependencia transitiva de firebase-auth)
  trae la regla `-keep class okhttp3.**`.
- Recursos de Firebase (`google_app_id`, `google_api_key`, `project_id`,
  `gcm_defaultSenderId`) presentes en el APK después de la reducción de recursos.

## Pruebas finales en el emulador

Fecha: 2026-11-09. Todas las pruebas se hicieron con `app-release.apk` (el de la tabla de artefactos).

| # | Prueba | Resultado |
|---|---|---|
| 1 | Instalación: se desinstaló la versión debug y se instaló `app-release.apk` en el emulador (`adb install`: Success). | OK |
| 2 | Los SHA-256 de las copias del APK y del AAB coinciden con los de la tabla de artefactos. | OK |
| 3 | Login con una cuenta existente: los recordatorios reaparecen desde Firestore. | OK |
| 4 | Crear, editar y borrar un recordatorio; los cambios se reflejan en Firestore. | OK |
| 5 | Preferencias (tema, orden, filtros): persisten al cerrar y reabrir la app. | OK |
| 6 | Contraseña incorrecta: se muestra el mensaje propio de la app, sin texto de Firebase. | OK |
| 7 | Modo avión: un recordatorio creado offline se sincroniza al volver la red, sin duplicarse. | OK |
| 8 | Logout y login con otra cuenta: no se ven datos de la cuenta anterior. | OK |
| 9 | Registro de una cuenta nueva. | OK |
| 10 | Menú de debug (SimuladorFallas): no aparece en release; está condicionado a `BuildConfig.DEBUG` en `ListadoScreen.kt:111`. Las clases quedan ofuscadas en el APK, sin efecto. | OK |

