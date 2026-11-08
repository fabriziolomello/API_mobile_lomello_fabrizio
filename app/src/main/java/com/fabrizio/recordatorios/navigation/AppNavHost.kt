package com.fabrizio.recordatorios.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fabrizio.recordatorios.ui.ajustes.AjustesScreen
import com.fabrizio.recordatorios.ui.auth.EstadoSesion
import com.fabrizio.recordatorios.ui.auth.LoginScreen
import com.fabrizio.recordatorios.ui.auth.RegistroScreen
import com.fabrizio.recordatorios.ui.auth.SplashScreen
import com.fabrizio.recordatorios.ui.creacion.CreacionScreen
import com.fabrizio.recordatorios.ui.detalle.DetalleScreen
import com.fabrizio.recordatorios.ui.listado.ListadoScreen

// Composable de nivel superior: acá se crea el NavController y es el único lugar
// que lo conoce. Las pantallas solo reciben lambdas (onCrearRecordatorio, onVolver, ...).
// Login y registro NO navegan al autenticarse: lo hace el efecto que observa la sesión.
@Composable
fun RecordatoriosApp(
    sesion: EstadoSesion,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Navega a una ruta borrando TODO el historial (no se puede volver atrás con "Atrás")
    val irLimpiandoHistorial: (String) -> Unit = { ruta ->
        navController.navigate(ruta) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // La navegación reacciona al Flow de sesión (no a callbacks de las pantallas):
    // - con sesión y parado en el splash/login/registro -> listado
    // - sin sesión en cualquier otra pantalla (logout o sesión invalidada) -> login
    LaunchedEffect(sesion) {
        val rutaActual = navController.currentDestination?.route
        when (sesion) {
            EstadoSesion.Verificando -> Unit // el splash sigue visible
            EstadoSesion.SinSesion ->
                if (rutaActual !in Rutas.SIN_SESION) irLimpiandoHistorial(Rutas.LOGIN)
            is EstadoSesion.Autenticado ->
                if (rutaActual == Rutas.INICIO || rutaActual in Rutas.SIN_SESION) irLimpiandoHistorial(Rutas.LISTADO)
        }
    }

    // Vuelve al listado sacando la pantalla actual del historial.
    // Como el listado ya está abajo en la pila, no se crea una copia nueva.
    val volverAlListado: () -> Unit = {
        navController.popBackStack(Rutas.LISTADO, inclusive = false)
    }

    NavHost(
        navController = navController,
        // Siempre arranca en el splash: el destino real (listado o login) lo decide
        // el LaunchedEffect de arriba cuando Firebase informa si hay sesión.
        startDestination = Rutas.INICIO,
        modifier = modifier
    ) {
        composable(Rutas.INICIO) {
            SplashScreen()
        }

        composable(Rutas.LOGIN) {
            LoginScreen(
                onIrARegistro = {
                    navController.navigate(Rutas.REGISTRO) { launchSingleTop = true }
                }
            )
        }

        composable(Rutas.REGISTRO) {
            RegistroScreen(
                onIrALogin = {
                    // Normalmente el login está debajo; si no, se navega a uno nuevo
                    if (!navController.popBackStack(Rutas.LOGIN, inclusive = false)) {
                        irLimpiandoHistorial(Rutas.LOGIN)
                    }
                }
            )
        }

        composable(Rutas.LISTADO) {
            ListadoScreen(
                onCrearRecordatorio = {
                    // launchSingleTop evita abrir dos formularios con un doble toque
                    navController.navigate(Rutas.CREAR) { launchSingleTop = true }
                },
                onRecordatorioClick = { id ->
                    navController.navigate(Rutas.detalle(id)) { launchSingleTop = true }
                },
                onAbrirAjustes = {
                    navController.navigate(Rutas.AJUSTES) { launchSingleTop = true }
                }
            )
        }

        composable(Rutas.AJUSTES) {
            AjustesScreen(onVolver = volverAlListado)
        }

        composable(Rutas.CREAR) {
            CreacionScreen(
                onGuardar = volverAlListado,
                onCancelar = volverAlListado
            )
        }

        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.LongType })
        ) {
            // El id lo lee el DetalleViewModel desde su SavedStateHandle
            DetalleScreen(
                onVolver = { navController.popBackStack() },
                onEditar = { id ->
                    navController.navigate(Rutas.editar(id)) { launchSingleTop = true }
                }
            )
        }

        // Mismo formulario que "crear": el CreacionViewModel detecta el id y pasa a modo edición.
        // Al terminar vuelve al detalle, que ya muestra los cambios porque observa Room.
        composable(
            route = Rutas.EDITAR,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.LongType })
        ) {
            CreacionScreen(
                onGuardar = { navController.popBackStack() },
                onCancelar = { navController.popBackStack() }
            )
        }
    }
}
