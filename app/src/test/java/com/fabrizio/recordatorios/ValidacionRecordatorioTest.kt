package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.ValidacionRecordatorio
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionRecordatorioTest {

    @Test
    fun titulo_cuentaCaracteresDespuesDeTrim() {
        assertNotNull(ValidacionRecordatorio.validarTitulo("  abc  "))
        assertNull(ValidacionRecordatorio.validarTitulo("  abcd "))
    }

    @Test
    fun titulo_maximo200() {
        assertNull(ValidacionRecordatorio.validarTitulo("a".repeat(200)))
        assertNotNull(ValidacionRecordatorio.validarTitulo("a".repeat(201)))
    }

    @Test
    fun titulo_maximoSeMideDespuesDeTrim() {
        // 200 caracteres útiles + espacios alrededor: se guarda recortado, es válido
        assertNull(ValidacionRecordatorio.validarTitulo("  " + "a".repeat(200) + "  "))
    }

    @Test
    fun descripcion_maximo200() {
        assertNull(ValidacionRecordatorio.validarDescripcion("a".repeat(200)))
        assertNotNull(ValidacionRecordatorio.validarDescripcion("a".repeat(201)))
    }

    @Test
    fun categoriaYPrioridad_obligatorias() {
        assertNotNull(ValidacionRecordatorio.validarCategoria(null))
        assertNull(ValidacionRecordatorio.validarCategoria(Categoria.Trabajo))
        assertNotNull(ValidacionRecordatorio.validarPrioridad(null))
        assertNull(ValidacionRecordatorio.validarPrioridad(Prioridad.Baja))
    }
}
