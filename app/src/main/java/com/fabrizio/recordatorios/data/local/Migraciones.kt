package com.fabrizio.recordatorios.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migraciones explícitas de Room. Cada una lleva la base de una versión a la siguiente;
// el JSON exportado en app/schemas/ de cada versión es la referencia del esquema esperado
// (MigracionesTest compara este SQL con el de 2.json).

// 1 → 2: aislamiento por usuario (columna user_id NOT NULL + índice).
// DECISIÓN: las filas de la versión 1 eran datos de prueba y no tienen dueño conocido,
// así que NO se migran: se descarta la tabla y se recrea con el esquema nuevo.
// (Un ALTER TABLE ADD COLUMN NOT NULL exigiría inventar un user_id por defecto,
// que es justamente lo que el aislamiento quiere evitar.)
internal val SQL_MIGRACION_1_2: List<String> = listOf(
    "DROP TABLE IF EXISTS `reminders`",
    "CREATE TABLE IF NOT EXISTS `reminders` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`client_id` TEXT NOT NULL, " +
        "`user_id` TEXT NOT NULL, " +
        "`remote_id` TEXT, " +
        "`title` TEXT NOT NULL, " +
        "`description` TEXT, " +
        "`category` TEXT NOT NULL, " +
        "`priority` TEXT NOT NULL, " +
        "`is_completed` INTEGER NOT NULL, " +
        "`due_date` INTEGER, " +
        "`created_at` INTEGER NOT NULL, " +
        "`updated_at` INTEGER NOT NULL, " +
        "`sync_status` TEXT NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_client_id` ON `reminders` (`client_id`)",
    "CREATE INDEX IF NOT EXISTS `index_reminders_user_id` ON `reminders` (`user_id`)",
    "CREATE INDEX IF NOT EXISTS `index_reminders_remote_id` ON `reminders` (`remote_id`)",
    "CREATE INDEX IF NOT EXISTS `index_reminders_sync_status` ON `reminders` (`sync_status`)"
)

val MIGRACION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Room ya ejecuta migrate() dentro de una transacción
        SQL_MIGRACION_1_2.forEach(db::execSQL)
    }
}
