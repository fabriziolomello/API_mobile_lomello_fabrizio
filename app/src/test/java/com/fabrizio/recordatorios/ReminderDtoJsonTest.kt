package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.remote.NetworkModule
import com.fabrizio.recordatorios.data.remote.ReminderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDtoJsonTest {

    @Test
    fun seEnvianLosCamposConValorPorDefecto() {
        // Si completed=false o los null no viajan, el servidor conserva el valor anterior
        val json = NetworkModule.json.encodeToString(ReminderDto.serializer(), ReminderDto(clientId = "abc", title = "X"))
        assertTrue(json, json.contains("\"completed\":false"))
        assertTrue(json, json.contains("\"description\":null"))
        assertTrue(json, json.contains("\"dueDate\":null"))
    }

    @Test
    fun ignoraCamposDesconocidosDelServidor() {
        val dto = NetworkModule.json.decodeFromString(
            ReminderDto.serializer(),
            """{"id":"1","clientId":"abc","title":"X","createAt":33,"campoNuevo":true}"""
        )
        assertEquals("1", dto.id)
        assertEquals("abc", dto.clientId)
    }
}
