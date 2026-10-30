package com.fabrizio.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fabrizio.recordatorios.BuildConfig

// Versionado: la entrega sale con la versión 1. Cada cambio de esquema
// (columna nueva, índice, tabla) sube `version` y agrega una Migration explícita
// con addMigrations(...). El JSON exportado en app/schemas permite compararlas.
@Database(
    entities = [ReminderEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val NOMBRE = "recordatorios.db"

        // @Volatile: todos los hilos ven el mismo valor apenas se asigna
        @Volatile
        private var instancia: AppDatabase? = null

        // Singleton: abrir varias instancias sobre el mismo archivo desperdicia
        // recursos y puede dar lecturas inconsistentes.
        fun getInstance(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: crear(context.applicationContext).also { instancia = it }
            }

        private fun crear(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, NOMBRE)
            // Solo en desarrollo: si falta una migración se borra y recrea la base
            // en vez de crashear. En release, una migración faltante es un error
            // que hay que corregir (nunca se borran datos del usuario en silencio).
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }
            return builder.build()
        }
    }
}
