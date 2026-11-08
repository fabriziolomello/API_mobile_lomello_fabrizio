package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val repo = FakeAuthRepository()

    // viewModelScope usa Dispatchers.Main, que no existe en la JVM: se reemplaza por uno de test
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sesionInicialSeReflejaEnElEstado() {
        val repoConSesion = FakeAuthRepository(AuthUser("uid-9", "ana@mail.com"))
        val vm = AuthViewModel(repoConSesion)

        val estado = vm.uiState.value
        assertTrue(estado.sesionVerificada)
        assertTrue(estado.autenticado)
        assertEquals("uid-9", estado.usuario?.uid)
    }

    @Test
    fun correoInvalidoNoLlamaAFirebase() {
        val vm = AuthViewModel(repo)

        vm.login("ana-sin-arroba.com", "secreta123")

        assertEquals(AuthError.CorreoInvalido, vm.uiState.value.error)
        assertEquals(0, repo.llamadas)
    }

    @Test
    fun contrasenaCortaNoLlamaAFirebase() {
        val vm = AuthViewModel(repo)

        vm.register("ana@mail.com", "12345")

        assertEquals(AuthError.ContrasenaDebil, vm.uiState.value.error)
        assertEquals(0, repo.llamadas)
    }

    @Test
    fun loginExitosoAutenticaYRecortaElCorreo() {
        val vm = AuthViewModel(repo)

        vm.login("  ana@mail.com ", "123456")

        val estado = vm.uiState.value
        assertEquals("ana@mail.com", repo.ultimoCorreo)
        assertTrue(estado.autenticado)
        assertFalse(estado.cargando)
        assertNull(estado.error)
    }

    @Test
    fun errorDelRepositorioLlegaAlEstado() {
        val vm = AuthViewModel(repo)
        repo.falla = AuthError.CorreoYaRegistrado

        vm.register("ana@mail.com", "secreta123")

        val estado = vm.uiState.value
        assertEquals(AuthError.CorreoYaRegistrado, estado.error)
        assertFalse(estado.autenticado)
        assertFalse(estado.cargando)
    }

    @Test
    fun cadaErrorDelRepositorioSeMuestraTalCual() {
        val errores = listOf(
            AuthError.CredencialesIncorrectas, AuthError.SinRed,
            AuthError.CorreoInvalido, AuthError.ContrasenaDebil
        )
        for (error in errores) {
            val vm = AuthViewModel(repo)
            repo.falla = error
            vm.login("ana@mail.com", "secreta123")
            assertEquals(error, vm.uiState.value.error)
        }
    }

    @Test
    fun limpiarErrorBorraElError() {
        val vm = AuthViewModel(repo)
        vm.login("mal", "123456")

        vm.limpiarError()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun logoutCierraLaSesion() {
        val vm = AuthViewModel(repo)
        vm.login("ana@mail.com", "123456")

        vm.logout()

        assertFalse(vm.uiState.value.autenticado)
    }
}
