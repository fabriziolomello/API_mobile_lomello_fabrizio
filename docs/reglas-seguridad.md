# Reglas de seguridad de Firestore

Archivo: [`firestore.rules`](../firestore.rules). Se publican pegándolas en la consola de
Firebase (Firestore Database → Reglas → Publicar). El repo no incluye `firebase.json`: no
se usa la CLI de Firebase para desplegar.

## Qué escribe la app

Cada recordatorio es un documento en `users/{uid}/reminders/{clientId}`, escrito siempre
completo con `set()` (`CloudReminder.toMap()` en `CloudMappers.kt`):

| Campo | Tipo en Firestore | ¿Puede ser null? | Notas |
|---|---|---|---|
| `clientId` | string | no | UUID; igual al ID del documento |
| `title` | string | no | la UI exige entre 4 y 200 caracteres (con trim) |
| `description` | string | **sí** (se escribe `null`) | la UI limita a 200 caracteres |
| `category` | string | no | `ESTUDIO`, `ACADEMICA`, `PERSONAL`, `TRABAJO` |
| `priority` | string | no | `ALTA`, `MEDIA`, `BAJA` |
| `completed` | booleano | no | |
| `dueDate` | entero (int64) | **sí** (se escribe `null`) | epoch millis, medianoche UTC |
| `createdAt` | entero (int64) | no | epoch millis |
| `updatedAt` | entero (int64) | no | epoch millis del dispositivo |

**Límite del título:** las reglas exigen `title.size() <= 200` y la app valida lo mismo
(`ValidacionRecordatorio.TITULO_MAX_CARACTERES = 200`). Los dos valores tienen que
coincidir: si la app permitiera títulos más largos, se guardarían en Room pero Firestore
rechazaría el `set()` y la fila quedaría en error de sincronización.

Los nueve campos se escriben siempre: los opcionales van como `null`, no se omiten. Las
reglas aceptan igual que falten, por si en el futuro el mapper deja de escribirlos.

## Reglas

```
rules_version = '2';

// Reglas de Firestore de Recordatorios.
// Único dato accesible: users/{uid}/reminders/{reminderId}, y solo para su dueño.
// Cualquier otra ruta no tiene match, así que queda denegada por defecto.
service cloud.firestore {
  match /databases/{database}/documents {

    // Hay sesión y es la del dueño de la ruta (nadie lee ni escribe datos ajenos)
    function esDueno(uid) {
      return request.auth != null && request.auth.uid == uid;
    }

    // Contenido de un recordatorio tal como lo escribe CloudReminder.toMap().
    // Protege contra documentos con campos de más, tipos incorrectos o tamaños abusivos.
    function esRecordatorioValido(reminderId) {
      let d = request.resource.data;
      return
        // Solo los campos conocidos; los obligatorios siempre presentes
        d.keys().hasOnly(['clientId', 'title', 'description', 'category', 'priority',
                          'completed', 'dueDate', 'createdAt', 'updatedAt'])
        && d.keys().hasAll(['title', 'category', 'priority', 'completed',
                            'createdAt', 'updatedAt'])
        // Texto: título no vacío y acotado; descripción opcional y acotada
        && d.title is string && d.title.size() > 0 && d.title.size() <= 200
        && (!('description' in d) || d.description == null
            || (d.description is string && d.description.size() <= 2000))
        // Códigos estables de Categoria y Prioridad
        && d.category is string && d.category in ['ESTUDIO', 'ACADEMICA', 'PERSONAL', 'TRABAJO']
        && d.priority is string && d.priority in ['ALTA', 'MEDIA', 'BAJA']
        && d.completed is bool
        // Fechas en epoch millis (Long en la app -> entero en Firestore)
        && (!('dueDate' in d) || d.dueDate == null || d.dueDate is int)
        && d.createdAt is int && d.createdAt > 0
        && d.updatedAt is int && d.updatedAt > 0
        // El clientId del contenido no puede apuntar a otro documento
        && (!('clientId' in d) || d.clientId == reminderId);
    }

    match /users/{uid}/reminders/{reminderId} {
      // Lectura de un documento y de la colección: solo el dueño
      allow get, list: if esDueno(uid);

      // La app siempre hace set() del documento completo: mismo chequeo para alta y edición
      allow create, update: if esDueno(uid) && esRecordatorioValido(reminderId);

      // Borrado: solo el dueño
      allow delete: if esDueno(uid);
    }
  }
}
```

## Casos de prueba (Rules Playground)

En la consola: Firestore Database → Reglas → **Rules Playground**. Para cada caso elegir la
operación, poner la ruta y, en "Autenticación", activar *Authenticated* con el UID
indicado (o dejarla desactivada para "sin autenticar"). Los UIDs son ficticios:
`uidDueno` es el dueño y `uidOtro` es otra cuenta.

Para los casos de `get`/`delete`/`update` sobre un documento existente, el Playground
simula la base vacía; las reglas de lectura y borrado no miran el contenido, así que el
resultado no cambia. Para los de escritura, pegar el JSON indicado como datos del documento
(los números sin decimales se toman como enteros).

| # | Operación | Ruta | Autenticado como | Resultado esperado |
|---|---|---|---|---|
| 1 | get | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Permitido |
| 2 | get | `/users/uidDueno/reminders/rec-1` | `uidOtro` | Denegado |
| 3 | get | `/users/uidDueno/reminders/rec-1` | sin autenticar | Denegado |
| 4 | create (set) — JSON [A](#a-documento-válido) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Permitido |
| 5 | update (set) — JSON [A](#a-documento-válido) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Permitido |
| 6 | create — JSON [B](#b-campo-extra) (campo extra) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Denegado |
| 7 | create — JSON [C](#c-title-vacío) (title vacío) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Denegado |
| 8 | create — JSON [D](#d-tipo-incorrecto) (tipo incorrecto) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Denegado |
| 9 | create — JSON [E](#e-clientid-distinto-del-id) (clientId ≠ ID) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Denegado |
| 10 | create — JSON [F](#f-opcionales-ausentes) (sin opcionales) | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Permitido |
| 11 | create — JSON [A](#a-documento-válido) | `/users/uidDueno/reminders/rec-1` | `uidOtro` | Denegado |
| 12 | get | `/users/uidDueno` | `uidDueno` | Denegado |
| 13 | get | `/otraColeccion/doc1` | `uidDueno` | Denegado |
| 14 | create — JSON [A](#a-documento-válido) | `/otraColeccion/doc1` | `uidDueno` | Denegado |
| 15 | delete | `/users/uidDueno/reminders/rec-1` | `uidDueno` | Permitido |
| 16 | delete | `/users/uidDueno/reminders/rec-1` | `uidOtro` | Denegado |

> El Playground no simula `list` (consultas). Esa regla usa el mismo chequeo que `get`
> (`esDueno(uid)`); se verifica desde la app con la sincronización.

### Documentos de prueba

#### A. Documento válido

Mismo formato que escribe la app (opcionales en `null`).

```json
{
  "clientId": "rec-1",
  "title": "Entregar TP de Android",
  "description": null,
  "category": "ESTUDIO",
  "priority": "ALTA",
  "completed": false,
  "dueDate": null,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000
}
```

#### B. Campo extra

`userId` no está en la lista de campos permitidos.

```json
{
  "clientId": "rec-1",
  "title": "Entregar TP de Android",
  "description": "Subirlo al campus",
  "category": "ESTUDIO",
  "priority": "ALTA",
  "completed": false,
  "dueDate": 1763164800000,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000,
  "userId": "uidDueno"
}
```

#### C. Title vacío

```json
{
  "clientId": "rec-1",
  "title": "",
  "description": null,
  "category": "ESTUDIO",
  "priority": "ALTA",
  "completed": false,
  "dueDate": null,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000
}
```

#### D. Tipo incorrecto

`completed` como string en vez de booleano.

```json
{
  "clientId": "rec-1",
  "title": "Entregar TP de Android",
  "description": null,
  "category": "ESTUDIO",
  "priority": "ALTA",
  "completed": "false",
  "dueDate": null,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000
}
```

#### E. clientId distinto del ID

El documento es `rec-1` pero dice ser `rec-2`.

```json
{
  "clientId": "rec-2",
  "title": "Entregar TP de Android",
  "description": null,
  "category": "ESTUDIO",
  "priority": "ALTA",
  "completed": false,
  "dueDate": null,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000
}
```

#### F. Opcionales ausentes

Sin `description`, `dueDate` ni `clientId`.

```json
{
  "title": "Entregar TP de Android",
  "category": "PERSONAL",
  "priority": "BAJA",
  "completed": true,
  "createdAt": 1762560000000,
  "updatedAt": 1762560000000
}
```
