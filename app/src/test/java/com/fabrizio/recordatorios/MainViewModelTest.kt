package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.ui.auth.EstadoSesion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun mientrasSeRevalidaSigueVerificandoYLuegoPasaAAutenticado() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(AuthUser("uid-1", "ana@mail.com"))
        auth.bloqueoRevalidacion = CompletableDeferred()
        val vm = MainViewModel(FakePreferenciasRepository(), auth)
        val coleccion = launch { vm.sesion.collect {} }

        // Hay usuario guardado, pero el reload no respondió: sigue el splash (sin flash de login)
        assertEquals(EstadoSesion.Verificando, vm.sesion.value)

        auth.bloqueoRevalidacion!!.complete(Unit)
        assertEquals(EstadoSesion.Autenticado("uid-1"), vm.sesion.value)
        coleccion.cancel()
    }

    @Test
    fun siLaRevalidacionCierraLaSesionVaDirectoASinSesion() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuthRepository(AuthUser("uid-1", "ana@mail.com"))
        auth.bloqueoRevalidacion = CompletableDeferred()
        val vm = MainViewModel(FakePreferenciasRepository(), auth)
        val estados = mutableListOf<EstadoSesion>()
        val coleccion = launch { vm.sesion.collect { estados += it } }

        auth.logout() // el reload detectó una cuenta deshabilitada
        auth.bloqueoRevalidacion!!.complete(Unit)

        // Nunca llegó a mostrarse el listado
        assertEquals(listOf(EstadoSesion.Verificando, EstadoSesion.SinSesion), estados)
        coleccion.cancel()
    }
}
