package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.revalidarSesion
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Las excepciones de Firebase no se pueden instanciar en tests JVM (usan android.text.TextUtils),
// así que se simulan con estas dos y se inyecta la clasificación equivalente a cuentaYaNoEsValida.
private class CuentaInvalida : Exception("usuario deshabilitado / borrado / token inválido")
private class ErrorDeRed : Exception("sin red")

// Reapertura de la app: reload() contra el servidor de la sesión guardada
class RevalidacionSesionTest {

    private var cierres = 0

    private fun revalidar(recargar: suspend () -> Unit) = runBlocking {
        revalidarSesion(
            recargar = recargar,
            cerrarSesion = { cierres++ },
            timeoutMs = 200,
            esErrorDeCuenta = { it is CuentaInvalida }
        )
    }

    @Test
    fun reloadConErrorDeRedNoCierraLaSesion() {
        val cerro = revalidar { throw ErrorDeRed() }

        assertFalse(cerro)
        assertEquals(0, cierres)
    }

    @Test
    fun reloadQueNoRespondeATiempoNoCierraLaSesion() {
        val cerro = revalidar { awaitCancellation() }

        assertFalse(cerro)
        assertEquals(0, cierres)
    }

    @Test
    fun cuentaInvalidaCierraLaSesion() {
        val cerro = revalidar { throw CuentaInvalida() }

        assertTrue(cerro)
        assertEquals(1, cierres)
    }

    @Test
    fun reloadExitosoNoCierraLaSesion() {
        val cerro = revalidar { }

        assertFalse(cerro)
        assertEquals(0, cierres)
    }
}
