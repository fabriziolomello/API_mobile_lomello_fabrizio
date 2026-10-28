package com.fabrizio.recordatorios.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fabrizio.recordatorios.ui.creacion.CreacionScreen
import com.fabrizio.recordatorios.ui.detalle.DetalleScreen
import com.fabrizio.recordatorios.ui.listado.ListadoScreen

// Composable de nivel superior: acá se crea el NavController y es el único lugar
// que lo conoce. Las pantallas solo reciben lambdas (onCrearRecordatorio, onVolver, ...).
@Composable
fun RecordatoriosApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Vuelve al listado sacando la pantalla actual del historial.
    // Como el listado ya está abajo en la pila, no se crea una copia nueva.
    val volverAlListado: () -> Unit = {
        navController.popBackStack(Rutas.LISTADO, inclusive = false)
    }

    NavHost(
        navController = navController,
        startDestination = Rutas.LISTADO,
        modifier = modifier
    ) {
        composable(Rutas.LISTADO) {
            ListadoScreen(
                onCrearRecordatorio = {
                    // launchSingleTop evita abrir dos formularios con un doble toque
                    navController.navigate(Rutas.CREAR) { launchSingleTop = true }
                },
                onRecordatorioClick = { id ->
                    navController.navigate(Rutas.detalle(id)) { launchSingleTop = true }
                }
            )
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
            DetalleScreen(onVolver = { navController.popBackStack() })
        }
    }
}
