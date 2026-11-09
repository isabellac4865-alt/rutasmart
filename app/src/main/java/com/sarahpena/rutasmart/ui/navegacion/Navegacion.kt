package com.sarahpena.rutasmart.ui.navegacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sarahpena.rutasmart.ui.AdminViewModel
import com.sarahpena.rutasmart.ui.AsistenteViewModel
import com.sarahpena.rutasmart.ui.RutaViewModel
import com.sarahpena.rutasmart.ui.SesionViewModel
import com.sarahpena.rutasmart.ui.pantallas.PantallaAdmin
import com.sarahpena.rutasmart.ui.pantallas.PantallaAlternativas
import com.sarahpena.rutasmart.ui.pantallas.PantallaAsistente
import com.sarahpena.rutasmart.ui.pantallas.PantallaDetalle
import com.sarahpena.rutasmart.ui.pantallas.PantallaHistorial
import com.sarahpena.rutasmart.ui.pantallas.PantallaInicio
import com.sarahpena.rutasmart.ui.pantallas.PantallaLogin
import com.sarahpena.rutasmart.ui.pantallas.PantallaPreferencias

/** Rutas de navegación (Navigation Component de Jetpack, navegación por pila). */
object Destinos {
    const val INICIO = "inicio"
    const val PREFERENCIAS = "preferencias"
    const val ALTERNATIVAS = "alternativas"
    const val DETALLE = "detalle/{indice}"
    const val HISTORIAL = "historial"
    const val ASISTENTE = "asistente"
    const val ADMIN = "admin"
    fun detalle(indice: Int) = "detalle/$indice"

    fun titulo(ruta: String?): String = when (ruta) {
        PREFERENCIAS -> "Mis preferencias"
        ALTERNATIVAS -> "Alternativas"
        DETALLE -> "Detalle de la ruta"
        HISTORIAL -> "Historial"
        ASISTENTE -> "Asistente Rutina"
        ADMIN -> "Administración"
        else -> "RutaSmart"
    }
}

@Composable
fun RutaSmartRaiz(sesionVm: SesionViewModel = viewModel()) {
    val sesion by sesionVm.ui.collectAsState()
    val usuario = sesion.usuario
    if (usuario == null) {
        PantallaLogin(sesionVm)
        return
    }

    val rutaVm: RutaViewModel = viewModel()
    val asistenteVm: AsistenteViewModel = viewModel()
    LaunchedEffect(usuario.uid) {
        rutaVm.establecerUsuario(usuario)
        asistenteVm.reiniciar()
    }

    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rutaActual != null && rutaActual != Destinos.INICIO) {
                        TextButton(
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.semantics { contentDescription = "Volver" }
                        ) { Text("←", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge) }
                    } else {
                        Text("🧭", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        Destinos.titulo(rutaActual),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row {
                    // Solo los administradores ven este botón (las reglas de Firestore también lo exigen).
                    if (sesion.esAdmin && rutaActual != Destinos.ADMIN) {
                        TextButton(
                            onClick = { nav.navigate(Destinos.ADMIN) { launchSingleTop = true } },
                            modifier = Modifier
                                .testTag("btn_admin")
                                .semantics { contentDescription = "Abrir panel de administración" }
                        ) { Text("🛠", style = MaterialTheme.typography.titleLarge) }
                    }
                    if (rutaActual != Destinos.HISTORIAL) {
                        TextButton(
                            onClick = { nav.navigate(Destinos.HISTORIAL) { launchSingleTop = true } },
                            modifier = Modifier.semantics { contentDescription = "Ver historial" }
                        ) { Text("🕘", style = MaterialTheme.typography.titleLarge) }
                    }
                    TextButton(
                        onClick = { sesionVm.cerrarSesion() },
                        modifier = Modifier.testTag("btn_salir")
                    ) { Text("Salir", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        },
        floatingActionButton = {
            // El asistente de IA se puede abrir desde cualquier pantalla con este botón flotante.
            if (rutaActual != Destinos.ASISTENTE && rutaActual != Destinos.ADMIN) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Destinos.ASISTENTE) { launchSingleTop = true } },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .testTag("fab_asistente")
                        .semantics { contentDescription = "Abrir asistente de inteligencia artificial" }
                ) { Text("🤖  Asistente") }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Destinos.INICIO, modifier = Modifier.padding(padding)) {
            composable(Destinos.INICIO) {
                PantallaInicio(
                    vm = rutaVm,
                    invitado = usuario.esInvitado,
                    onBuscado = { nav.navigate(Destinos.ALTERNATIVAS) },
                    onPreferencias = { nav.navigate(Destinos.PREFERENCIAS) }
                )
            }
            composable(Destinos.PREFERENCIAS) {
                PantallaPreferencias(vm = rutaVm, onGuardado = { nav.popBackStack() })
            }
            composable(Destinos.ALTERNATIVAS) {
                PantallaAlternativas(
                    vm = rutaVm,
                    onDetalle = { i -> nav.navigate(Destinos.detalle(i)) },
                    onPreguntar = { nav.navigate(Destinos.ASISTENTE) }
                )
            }
            composable(Destinos.DETALLE, arguments = listOf(navArgument("indice") { type = NavType.IntType })) { e ->
                PantallaDetalle(vm = rutaVm, indice = e.arguments?.getInt("indice") ?: 0)
            }
            composable(Destinos.HISTORIAL) {
                PantallaHistorial(
                    vm = rutaVm,
                    invitado = usuario.esInvitado,
                    onRepetir = { consulta ->
                        rutaVm.repetir(consulta) {
                            nav.navigate(Destinos.ALTERNATIVAS) { popUpTo(Destinos.INICIO) }
                        }
                    }
                )
            }
            composable(Destinos.ADMIN) {
                if (sesion.esAdmin) {
                    val adminVm: AdminViewModel = viewModel()
                    PantallaAdmin(adminVm)
                } else {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                }
            }
            composable(Destinos.ASISTENTE) {
                PantallaAsistente(vm = asistenteVm, contexto = { rutaVm.contexto() })
            }
        }
    }
}
