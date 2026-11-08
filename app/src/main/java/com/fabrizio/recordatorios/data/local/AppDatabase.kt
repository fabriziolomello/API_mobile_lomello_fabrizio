package com.fabrizio.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Versionado: cada cambio de esquema (columna nueva, índice, tabla) sube `version`
// y agrega una Migration explícita con addMigrations(...) (ver Migraciones.kt).
// El JSON exportado en app/schemas permite compararlas.
//   v1: Actividad 3 (local-first con MockAPI)
//   v2: Actividad 4, columna user_id (aislamiento por usuario)
@Database(
    entities = [ReminderEntity::class],
    version = 2,
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

        // Sin fallbackToDestructiveMigration (ni siquiera en debug): si falta una
        // migración, Room falla al abrir la base en vez de borrar datos en silencio.
        // Así una migración olvidada se detecta en desarrollo y no llega a producción.
        private fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}
