# Checklist de pruebas manuales: Actividad 3

Completar la columna **Resultado** con ✅ / ❌ y el nombre de la captura.
Usar un build **debug**, porque el simulador de fallas 🔧 solo existe ahí.

## Preparación

- [ ] `local.properties` tiene `API_BASE_URL` apuntando al proyecto de MockAPI.
- [ ] El recurso `reminders` responde `[]` (o datos válidos) en el navegador.
- [ ] La app se instaló desde cero: desinstalar la versión anterior o borrar sus datos.
- [ ] Logcat abierto con el filtro `okhttp.OkHttpClient`, para ver las peticiones.
- [ ] Database Inspector abierto (App Inspection → `recordatorios.db` → `reminders`).

**Comandos útiles**
```bash
adb shell am force-stop com.fabrizio.recordatorios   # cerrar la app del todo
adb shell cmd connectivity airplane-mode enable      # modo avión
adb shell cmd connectivity airplane-mode disable
```

---

## A. Preferencias (DataStore)

| # | Prueba | Pasos | Resultado esperado | Resultado |
|---|---|---|---|---|
| A1 | Tema oscuro y claro | ⚙️ Ajustes → Tema: Oscuro, después Claro | Toda la app cambia al instante, incluidos los íconos de la barra de estado | |
| A2 | Tema "Sistema" | Tema: Sistema → cambiar el modo oscuro del sistema | La app sigue al sistema | |
| A3 | Mostrar completados | Apagar "Mostrar completados" | Los completados desaparecen del listado | |
| A4 | Orden | Ordenar por Título y después por Prioridad | La lista se reordena sin recargar | |
| A5 | Filtro de prioridad | Filtro "Alta" | Solo se ven los de prioridad alta | |
| A6 | Sin coincidencias | Filtro que no deje ninguno visible | "Ningún recordatorio coincide…" con el botón "Revisar ajustes" | |
| A7 | **Persistencia tras cerrar** | Configurar Oscuro, sin completados, orden Título y filtro Alta → `force-stop` → abrir | Los 4 ajustes se mantienen; no aparece un instante el tema claro | |
| A8 | **Valor inválido** | Ajustes → "Prueba: guardar tema inválido" | No crashea; el tema vuelve a "Sistema"; los otros ajustes no cambian | |
| A9 | Valor inválido tras reiniciar | Después de A8: `force-stop` → abrir | Abre sin errores, con tema "Sistema" | |

## B. Persistencia y CRUD (Room)

| # | Prueba | Pasos | Resultado esperado | Resultado |
|---|---|---|---|---|
| B1 | Crear | Crear un recordatorio con fecha límite | Aparece en el listado con "Vence: …" | |
| B2 | Ver detalle | Tocar la tarjeta | Se ven todos los datos y el estado de sincronización | |
| B3 | Completar | Detalle → "Marcar como completado" | Cambia al instante; en el listado aparece tachado | |
| B4 | Editar | Detalle → ✏️ → cambiar título, prioridad y fecha → Guardar | Vuelve al detalle con los cambios aplicados | |
| B5 | Sin duplicados al editar | Database Inspector después de B4 | Mismo `id` y mismo `client_id`; no hay filas nuevas | |
| B6 | Eliminar | Detalle → 🗑️ → Cancelar, y después 🗑️ → Eliminar | Cancelar no hace nada; Eliminar vuelve al listado sin el registro | |
| B7 | **CRUD persistente tras reiniciar** | Después de B1–B6: `force-stop` → abrir | Todos los cambios se mantienen | |
| B8 | Persistencia tras reiniciar el emulador | Reiniciar el dispositivo → abrir la app | Los datos siguen ahí | |

## C. Offline y sincronización

| # | Prueba | Pasos | Resultado esperado | Resultado |
|---|---|---|---|---|
| C1 | Sync exitoso | 🔧 "Sin fallas" → crear un recordatorio | En 1–2 s pasa de "Pendiente de sincronizar" a "Sincronizado"; aparece en el navegador | |
| C2 | **Crear offline** | Modo avión → crear 2 recordatorios | Se guardan; barra roja "Sin conexión… · 2 cambios pendientes"; tarjetas "Pendiente de sincronizar" | |
| C3 | **Editar offline** | En modo avión: editar y completar un recordatorio ya sincronizado | Se guarda; queda "Pendiente de sincronizar" (`PENDING_UPDATE` en el Inspector) | |
| C4 | Eliminar offline | En modo avión: eliminar un recordatorio sincronizado | Desaparece del listado (`PENDING_DELETE` en el Inspector) | |
| C5 | Sync manual sin red | En modo avión: ⟳ o pull-to-refresh | Snackbar "Sin conexión… se enviarán al reconectar" con Reintentar; los datos no cambian | |
| C6 | **Reconectar y sincronizar** | Desactivar el modo avión | Sync automático; todo queda "Sincronizado"; lo eliminado en C4 desaparece del servidor | |
| C7 | **Sin duplicados** | Después de C6: contar en la app, en el navegador y en el Inspector | Misma cantidad en los tres lados; cada `client_id` aparece una vez en el servidor; todos tienen `remote_id` | |
| C8 | Varias reconexiones seguidas | Activar y desactivar el modo avión 3 veces rápido | No aparecen duplicados (un solo sync a la vez) | |
| C9 | **Timeout + reintento sin duplicar** | 🔧 "Timeout después de enviar" → crear "X" → aparece el snackbar de timeout → 🔧 "Sin fallas" → ⟳ | En el servidor hay un solo "X"; Logcat: `GET ?clientId=` 200 → `PUT`, sin un segundo `POST` | |

## D. Errores del servicio

| # | Prueba | Pasos | Resultado esperado | Resultado |
|---|---|---|---|---|
| D1 | **Error 4xx** | 🔧 "Error 400" → editar un recordatorio | Snackbar "El servidor rechazó la solicitud (error 400)…"; la tarjeta muestra "⚠ Error al sincronizar" | |
| D2 | 4xx no se reenvía solo | Después de D1: `force-stop` → abrir (simulador en "Sin fallas") | Sigue en "Error al sincronizar" | |
| D3 | Reintentar un 4xx | 🔧 "Sin fallas" → detalle → "Reintentar sincronización" | Pasa a "Sincronizado"; el servidor tiene el cambio | |
| D4 | **Error 5xx** | 🔧 "Error 500" → editar | Snackbar "El servidor tuvo un problema (error 500)… quedan guardados"; sigue "Pendiente" | |
| D5 | Recuperación del 5xx | 🔧 "Sin fallas" → "Reintentar" en el snackbar (o ⟳) | Se sincroniza | |
| D6 | **Timeout** | 🔧 "Timeout" → ⟳ | A los ~1,5 s: "El servidor tardó demasiado en responder…"; los datos no cambian | |
| D7 | **Sin conexión (simulado)** | 🔧 "Sin conexión" → editar | Snackbar "Sin conexión…"; el cambio queda pendiente | |
| D8 | Datos intactos tras errores | Después de D1–D7: `force-stop` → abrir | Todos los cambios locales siguen ahí | |

## E. Evidencias sugeridas para el informe

- [ ] Listado en tema claro y en tema oscuro (A1).
- [ ] Ajustes antes y después de reiniciar (A7).
- [ ] Antes y después del valor inválido (A8).
- [ ] Database Inspector después de editar: mismo `id` y `client_id` (B5).
- [ ] Listado en modo avión con la barra roja y cambios pendientes (C2).
- [ ] La app, el navegador y el Inspector con la misma cantidad de registros (C7).
- [ ] Logcat de C9: `GET ?clientId` → `PUT`, sin un segundo `POST`.
- [ ] Tarjeta con "Error al sincronizar" y snackbar de 400 (D1).
- [ ] Snackbars de 500, timeout y sin conexión (D4, D6, D7).
- [ ] Salida de `./gradlew testDebugUnitTest` (26 tests en verde).
