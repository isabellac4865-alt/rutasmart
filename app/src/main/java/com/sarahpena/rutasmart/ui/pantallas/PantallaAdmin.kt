@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.admin.EstadoServicio
import com.sarahpena.rutasmart.domain.admin.NivelAviso
import com.sarahpena.rutasmart.domain.admin.ResumenUso
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.ui.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Panel de administración con tres pestañas: Resumen, Líneas y Aviso. */
@Composable
fun PantallaAdmin(vm: AdminViewModel) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val ui by vm.ui.collectAsState()
    val estado by vm.estado.collectAsState()

    LaunchedEffect(Unit) { vm.cargarResumen() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pestana) {
            listOf("📊 Resumen", "🚇 Líneas", "📢 Aviso").forEachIndexed { i, t ->
                Tab(selected = pestana == i, onClick = { pestana = i; vm.limpiarMensajes() }, text = { Text(t) },
                    modifier = Modifier.testTag("tab_admin_$i"))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ui.mensaje?.let { Aviso(it, MaterialTheme.colorScheme.tertiary) }
            MensajeError(ui.error)
            if (ui.guardando) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Publicando…", style = MaterialTheme.typography.labelMedium)
            }
            when (pestana) {
                0 -> Resumen(ui.resumen, ui.cargandoResumen, estado, onRecargar = vm::cargarResumen)
                1 -> Lineas(vm.lineas, estado, ui.guardando, onCambiar = vm::cambiarLinea)
                else -> EditorAviso(estado, ui.guardando, onPublicar = vm::publicarAviso, onQuitar = vm::quitarAviso)
            }
        }
    }
}

// ------------------------------------------------------------------ Resumen

@Composable
private fun Resumen(r: ResumenUso?, cargando: Boolean, estado: EstadoServicio, onRecargar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Uso de RutaSmart", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Datos anónimos de las búsquedas de todos los usuarios", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onRecargar, enabled = !cargando, modifier = Modifier.semantics { contentDescription = "Actualizar estadísticas" }) {
            Text("🔄 Actualizar")
        }
    }
    if (cargando && r == null) { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }; return }
    if (r == null) return
    if (r.vacio) {
        Tarjeta { Text("Aún no hay búsquedas registradas. Cuando los usuarios con cuenta busquen rutas, verás aquí las estadísticas.") }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi("🔎", "${r.totalConsultas}", "Búsquedas", Modifier.weight(1f))
        Kpi("👥", "${r.usuariosActivos}", "Usuarios activos", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi("📅", "${r.consultasUltimos7Dias}", "Últimos 7 días", Modifier.weight(1f))
        Kpi("⭐", "${r.puntajePromedio}", "Puntaje promedio", Modifier.weight(1f))
    }
    Tarjeta {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metrica("⏱", "${r.tiempoPromedio} min", "Tiempo promedio")
            Metrica("💵", pesos(r.costoPromedio), "Costo promedio")
            Metrica("🚫", "${estado.lineasSuspendidas.size}", "Líneas suspendidas")
        }
    }
    Ranking("🔥 Rutas más buscadas", r.rutasMasBuscadas)
    Ranking("🏆 Opción más recomendada", r.medioMasRecomendado)
    Ranking("📍 Destinos populares", r.destinosPopulares)
}

@Composable
private fun Kpi(icono: String, valor: String, etiqueta: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$etiqueta: $valor" }
    ) {
        Text(icono, style = MaterialTheme.typography.titleLarge)
        Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Ranking(titulo: String, filas: List<Pair<String, Int>>) {
    if (filas.isEmpty()) return
    val max = filas.maxOf { it.second }.toDouble()
    Tarjeta {
        Text(titulo, fontWeight = FontWeight.Bold)
        filas.forEachIndexed { i, (nombre, n) ->
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Puesto ${i + 1}: $nombre, $n búsquedas" }) {
                Row(Modifier.fillMaxWidth()) {
                    Text("${i + 1}. $nombre", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("$n", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                BarraPuntaje(n / max * 100.0)
            }
        }
    }
}

// ------------------------------------------------------------------ Líneas

@Composable
private fun Lineas(lineas: List<Linea>, estado: EstadoServicio, guardando: Boolean, onCambiar: (Linea, Boolean) -> Unit) {
    var confirmar by remember { mutableStateOf<Linea?>(null) }
    Text("Estado de las líneas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "Si una línea está cerrada (obras, manifestación, falla), apágala: dejará de usarse en las rutas de todos los usuarios y verán un aviso.",
        style = MaterialTheme.typography.bodySmall
    )
    for ((tipo, titulo) in listOf(TipoMedio.METRO to "🚇 TransMilenio", TipoMedio.BUS to "🚌 SITP")) {
        Tarjeta {
            Text(titulo, fontWeight = FontWeight.Bold)
            lineas.filter { it.tipo == tipo }.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider()
                val enServicio = l.id !in estado.lineasSuspendidas
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.nombre, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (enServicio) "✅ En servicio" else "⛔ Suspendida",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enServicio) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = enServicio,
                        enabled = !guardando,
                        onCheckedChange = { activar -> if (activar) onCambiar(l, false) else confirmar = l },
                        modifier = Modifier
                            .testTag("switch_linea_${l.id}")
                            .semantics { contentDescription = "${l.nombre}: ${if (enServicio) "en servicio" else "suspendida"}" }
                    )
                }
            }
        }
    }
    confirmar?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text("¿Suspender ${l.nombre}?") },
            text = { Text("Todos los usuarios dejarán de recibir rutas por esta línea hasta que la vuelvas a activar.") },
            confirmButton = { Button(onClick = { onCambiar(l, true); confirmar = null }) { Text("Suspender") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------------ Aviso

@Composable
private fun EditorAviso(estado: EstadoServicio, guardando: Boolean, onPublicar: (String, NivelAviso) -> String?, onQuitar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf(estado.aviso) }
    var nivel by rememberSaveable { mutableStateOf(estado.nivelAviso) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Aviso para los usuarios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Aparece en la pantalla de inicio de todos. Úsalo para cierres, desvíos o eventos.", style = MaterialTheme.typography.bodySmall)

    Tarjeta {
        Text("Así se ve hoy:", style = MaterialTheme.typography.labelMedium)
        if (estado.hayAviso) TarjetaAviso(estado.aviso, estado.nivelAviso)
        else Text("Sin aviso publicado.", style = MaterialTheme.typography.bodySmall)
        if (estado.actualizadoMillis > 0) {
            Text(
                "Última actualización: " + SimpleDateFormat("d MMM yyyy, h:mm a", Locale("es", "CO")).format(Date(estado.actualizadoMillis)),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Text("Importancia", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NivelAviso.entries.forEach { n ->
            FilterChip(selected = nivel == n, onClick = { nivel = n }, label = { Text("${n.emoji} ${n.etiqueta}") })
        }
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { if (it.length <= EstadoServicio.MAX_AVISO) { texto = it; error = null } },
        label = { Text("Mensaje") },
        placeholder = { Text("Ej.: Estación Calle 72 cerrada por obras hasta las 6 p. m.") },
        supportingText = { Text(error ?: "${texto.length}/${EstadoServicio.MAX_AVISO}") },
        isError = error != null,
        minLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("campo_aviso")
    )
    if (texto.isNotBlank()) {
        Text("Vista previa:", style = MaterialTheme.typography.labelMedium)
        TarjetaAviso(texto, nivel)
    }
    Button(
        onClick = { error = onPublicar(texto, nivel) },
        enabled = !guardando,
        modifier = Modifier.fillMaxWidth().testTag("btn_publicar_aviso")
    ) { Text("📢 Publicar aviso") }
    if (estado.hayAviso) {
        OutlinedButton(onClick = { onQuitar(); texto = "" }, enabled = !guardando, modifier = Modifier.fillMaxWidth()) { Text("Quitar aviso") }
    }
}

// ------------------------------------------------------------------ Componentes compartidos con el inicio

fun colorNivel(n: NivelAviso): Color = when (n) {
    NivelAviso.INFO -> Color(0xFF1B5FD9)
    NivelAviso.ALERTA -> Color(0xFFF29F05)
    NivelAviso.URGENTE -> Color(0xFFD93025)
}

@Composable
fun TarjetaAviso(texto: String, nivel: NivelAviso) {
    val color = colorNivel(nivel)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Aviso ${nivel.etiqueta}: $texto" },
        verticalAlignment = Alignment.Top
    ) {
        Text(nivel.emoji, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(start = 10.dp)) {
            Text(nivel.etiqueta, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Aviso(texto: String, color: Color) {
    Text(
        "✅ $texto",
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

/** Aviso del administrador + líneas suspendidas, en la pantalla de inicio de todos los usuarios. */
@Composable
fun NovedadesServicio(estado: EstadoServicio, nombreLinea: (String) -> String) {
    if (!estado.hayNovedades) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("novedades_servicio")) {
        if (estado.hayAviso) TarjetaAviso(estado.aviso, estado.nivelAviso)
        if (estado.lineasSuspendidas.isNotEmpty()) {
            TarjetaAviso(
                "Fuera de servicio: " + estado.lineasSuspendidas.sorted().joinToString(", ") { nombreLinea(it) } +
                    ". Tus rutas ya no las usan.",
                NivelAviso.ALERTA
            )
        }
    }
}
