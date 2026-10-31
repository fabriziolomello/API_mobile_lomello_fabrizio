# Recordatorios

App Android de recordatorios hecha con Kotlin y Jetpack Compose para la materia
Programación Mobile. La **Actividad 3** suma persistencia local, preferencias, un
repositorio *local-first* y la sincronización con un servicio remoto, con
funcionamiento offline y sin duplicados.

| | |
|---|---|
| Paquete | `com.fabrizio.recordatorios` |
| minSdk / targetSdk | 24 / 37 |
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| Presentación | ViewModel + `UiState` + `StateFlow` |
| Datos | DataStore (Preferences), Room, Retrofit + OkHttp, kotlinx.serialization |
| Inyección de dependencias | Manual (`AppContainer`) |

## Funcionalidades

- Listado, creación, edición, detalle, completar y eliminar recordatorios.
- **Preferencias** persistentes (DataStore): tema claro/oscuro/sistema, mostrar
  completados, criterio de orden y filtro de prioridad.
- **Persistencia local** (Room): los datos sobreviven al cierre de la app.
- **Local-first**: todo cambio se guarda primero en el dispositivo y funciona sin conexión.
- **Sincronización** con un servicio REST (MockAPI.io):
  - automática al abrir la app, al recuperar la red y después de cada cambio;
  - manual con *pull-to-refresh* o con el botón ⟳.
- **Estado de sync visible** en cada recordatorio (pendiente / sincronizado / error),
  con opción de reintentar.
- **Sin duplicados**: clave de idempotencia (`clientId`), un único sync a la vez
  (`Mutex`) y verificación en el servidor antes de crear.

## Requisitos

- Android Studio con soporte para AGP 9.4 (Gradle 9.6, incluido en el wrapper).
- JDK 25 para el daemon de Gradle. Android Studio lo descarga solo; si no, ver
  `gradle/gradle-daemon-jvm.properties`.
- Emulador o dispositivo con Android 7.0 (API 24) o superior.

## Configuración

### 1. Servicio remoto (MockAPI.io)

1. Creá una cuenta gratis en <https://mockapi.io> y un proyecto nuevo
   (API prefix: `/api/v1`).
2. Creá el recurso **`reminders`**. Borrá los campos que vienen por defecto y
   definí estos:

   | Campo | Tipo | Contenido |
   |---|---|---|
   | `id` | Object ID | Lo asigna el servidor (en la app es el `remoteId`) |
   | `clientId` | String | UUID generado en el dispositivo (clave de idempotencia) |
   | `title` | String | |
   | `description` | String | Puede ser `null` |
   | `category` | String | `ESTUDIO`, `ACADEMICA`, `PERSONAL` o `TRABAJO` |
   | `priority` | String | `ALTA`, `MEDIA` o `BAJA` |
   | `completed` | Boolean | |
   | `dueDate` | Number | Epoch millis o `null` |
   | `createdAt` | Number | Epoch millis |
   | `updatedAt` | Number | Epoch millis |

3. No generes datos de ejemplo (cantidad 0).
4. Comprobá que `https://<id-proyecto>.mockapi.io/api/v1/reminders` responda `[]`.

### 2. URL base (`local.properties`)

La URL **no está en el código**: se lee de `local.properties` y llega a la app como
`BuildConfig.API_BASE_URL`. Ese archivo está en `.gitignore` y nunca se sube.

```bash
cp local.properties.example local.properties
```

Editá `local.properties`:

```properties
sdk.dir=/ruta/a/tu/Android/sdk
API_BASE_URL=https://<id-proyecto>.mockapi.io/api/v1/
```

- La URL tiene que terminar en `/` y **no** incluir `reminders`.
- Android Studio completa `sdk.dir` solo al abrir el proyecto.
- Si `API_BASE_URL` falta, la app compila igual: funciona en modo offline y la
  sincronización informa "Sin conexión".
- Después de cambiar la URL, hacé **Sync Project with Gradle Files** y volvé a compilar.

## Compilar y ejecutar

Desde Android Studio: abrir el proyecto, esperar el *Gradle sync* y ▶ **Run 'app'**.

Desde la terminal:

```bash
./gradlew assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # instala en el emulador o dispositivo conectado
./gradlew testDebugUnitTest    # tests unitarios (26)
```

## Herramientas de prueba (solo builds de debug)

- **Simulador de fallas** (ícono 🔧 en el listado): fuerza error 400, error 500,
  timeout, sin conexión o "timeout después de enviar". Es un interceptor de OkHttp
  que no existe en release. El modo elegido se olvida al cerrar la app.
- **Log de red**: en Logcat, filtrá por `okhttp.OkHttpClient` para ver cada petición
  y respuesta, incluidas las simuladas.
- **Prueba de valor inválido** (Ajustes): guarda un tema inexistente en DataStore
  para comprobar que la app vuelve al valor por defecto.
- **Database Inspector** (App Inspection en Android Studio): muestra la tabla
  `reminders` con `client_id`, `remote_id` y `sync_status`.

## Estructura del proyecto

```
app/src/main/java/com/fabrizio/recordatorios/
├── AppContainer.kt              Inyección manual: DataStore, Room, Retrofit, SyncManager
├── RecordatoriosApplication.kt  Crea el container e inicia el sync automático
├── MainActivity.kt / MainViewModel.kt   Aplica el tema elegido
├── data/
│   ├── ReminderRepository.kt          Contrato que usan los ViewModel
│   ├── LocalFirstReminderRepository.kt Implementación local-first
│   ├── local/        Room: ReminderEntity, ReminderDao, AppDatabase, Converters, SyncStatus
│   ├── remote/       Retrofit: ReminderDto, ReminderApi, DataResult, NetworkModule,
│   │                 ReminderRemoteDataSource, SimuladorFallas (debug)
│   ├── sync/         SyncManager, ConnectivityObserver, SyncResult
│   ├── mapper/       EntityMappers (Entity <-> dominio), DtoMappers (Entity <-> DTO)
│   └── preferencias/ DataStore: PreferenciasUsuario, PreferenciasRepository
├── model/            Modelo de dominio: Recordatorio, Categoria, Prioridad, Estado, EstadoSync
├── navigation/       Rutas y NavHost
└── ui/               Pantallas (listado, creacion, detalle, ajustes), componentes y tema
app/schemas/          Esquema exportado de Room (versión 1)
docs/                 Borrador del informe técnico y checklist de pruebas manuales
```

## Documentación

- [`docs/informe-tecnico.md`](docs/informe-tecnico.md): borrador de las secciones
  técnicas del informe, con el diagrama del flujo de datos.
- [`docs/checklist-pruebas.md`](docs/checklist-pruebas.md): pruebas manuales para
  ejecutar y registrar.

## Limitaciones conocidas

- **MockAPI** no soporta el header `Idempotency-Key`. Por eso el `clientId` viaja
  como campo y se verifica con `GET reminders?clientId=…` antes de cada creación.
- **MockAPI** filtra por coincidencia parcial y responde 404 cuando no hay resultados.
  La app compara el `clientId` exacto y trata ese 404 como "no existe".
- **MockAPI** rellena con datos aleatorios los campos que faltan en el JSON. Por eso
  la app envía todos los campos, incluidos los `null` (`encodeDefaults = true`).
- **Conflictos**: si un registro tiene cambios locales pendientes, gana la versión
  local; si está sincronizado, gana la del servidor. No hay fusión campo por campo.
- **Reintentos de 4xx**: una operación rechazada queda en estado de error hasta que
  el usuario toca "Reintentar". Si lo rechazado era un borrado, el registro vuelve a
  verse con el error y se puede eliminar de nuevo.
