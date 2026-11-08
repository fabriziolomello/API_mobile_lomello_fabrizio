package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.local.MIGRACION_1_2
import com.fabrizio.recordatorios.data.local.SQL_MIGRACION_1_2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Verifica, sin emulador, que la migración 1→2 crea EXACTAMENTE el esquema que Room
// exportó para la versión 2 (app/schemas/.../2.json). Si alguien cambia la entidad
// y regenera el JSON sin actualizar la migración, este test falla.
// (El directorio de trabajo de los tests unitarios es el módulo app/.)
class MigracionesTest {

    private val esquemaV2: JsonObject by lazy {
        val archivo = File("schemas/com.fabrizio.recordatorios.data.local.AppDatabase/2.json")
        assertTrue("Falta el esquema exportado: ${archivo.absolutePath}", archivo.exists())
        Json.parseToJsonElement(archivo.readText()).jsonObject["database"]!!.jsonObject
    }

    @Test
    fun migracionVaDe1A2() {
        assertEquals(1, MIGRACION_1_2.startVersion)
        assertEquals(2, MIGRACION_1_2.endVersion)
        assertEquals(2, esquemaV2["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun descartaLaTablaViejaAntesDeRecrearla() {
        assertEquals("DROP TABLE IF EXISTS `reminders`", SQL_MIGRACION_1_2.first())
    }

    @Test
    fun creaLaTablaYLosIndicesIgualQueElEsquemaExportado() {
        val tabla = esquemaV2["entities"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["tableName"]!!.jsonPrimitive.content == "reminders" }

        fun sql(obj: JsonObject) =
            obj["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", "reminders")

        val esperado = listOf(sql(tabla)) +
            tabla["indices"]!!.jsonArray.map { sql(it.jsonObject) }

        assertEquals(esperado, SQL_MIGRACION_1_2.drop(1))
    }
}
