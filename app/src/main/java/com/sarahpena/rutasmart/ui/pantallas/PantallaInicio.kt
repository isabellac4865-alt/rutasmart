@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.ui.RutaViewModel

@Composable
fun PantallaInicio(vm: RutaViewModel, invitado: Boolean, onBuscado: () -> Unit, onPreferencias: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val criterios by vm.criterios.collectAsState()
    val servicio by vm.estadoServicio.collectAsState()
    var campoActivo by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("¿A dónde vas hoy?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (invitado) Text("Modo invitado · tu historial se guarda solo en este teléfono", style = MaterialTheme.typography.labelSmall)
        NovedadesServicio(servicio, vm::nombreLinea)

        Tarjeta {
            CampoLugar(
                etiqueta = "Origen", icono = "🟢", texto = ui.textoOrigen, tag = "campo_origen",
                sugerencias = if (campoActivo == "o" && ui.origen == null) vm.sugerencias(ui.textoOrigen) else emptyList(),
                onTexto = { campoActivo = "o"; vm.cambiarOrigen(it) },
                onElegir = { vm.elegirOrigen(it); campoActivo = null }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { vm.intercambiar() }, modifier = Modifier.semantics { contentDescription = "Intercambiar origen y destino" }) {
                    Text("⇅ Intercambiar")
                }
            }
            CampoLugar(
                etiqueta = "Destino", icono = "📍", texto = ui.textoDestino, tag = "campo_destino",
                sugerencias = if (campoActivo == "d" && ui.destino == null) vm.sugerencias(ui.textoDestino) else emptyList(),
                onTexto = { campoActivo = "d"; vm.cambiarDestino(it) },
                onElegir = { vm.elegirDestino(it); campoActivo = null }
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("🚦 Hora pico", fontWeight = FontWeight.SemiBold)
                    Text("Más tráfico y transporte lleno", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = criterios.horaPico, onCheckedChange = { vm.cambiarHoraPico(it) }, modifier = Modifier.testTag("switch_pico"))
            }
            Text("Prioridades: ${criterios.resumen()}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onPreferencias, modifier = Modifier.fillMaxWidth()) { Text("⚙️ Ajustar preferencias") }
        }

        MensajeError(ui.error)

        Button(
            onClick = { campoActivo = null; vm.buscar(onBuscado) },
            enabled = !ui.cargando,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_buscar")
        ) {
            if (ui.cargando) CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("🔎 Buscar rutas", style = MaterialTheme.typography.titleMedium)
        }

        Text("Lugares populares", fontWeight = FontWeight.SemiBold)
        val populares = vm.lugares.take(8)
        populares.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { l ->
                    AssistChip(
                        onClick = { if (ui.origen == null) vm.elegirOrigen(l) else vm.elegirDestino(l) },
                        label = { Text(l.nombre, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(72.dp)) // espacio para el botón flotante
    }
}

@Composable
private fun CampoLugar(
    etiqueta: String, icono: String, texto: String, tag: String,
    sugerencias: List<Lugar>, onTexto: (String) -> Unit, onElegir: (Lugar) -> Unit
) {
    Column {
        OutlinedTextField(
            value = texto,
            onValueChange = onTexto,
            label = { Text("$icono $etiqueta") },
            placeholder = { Text("Ej: Universidad de San Buenaventura") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag)
        )
        sugerencias.forEach { l ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onElegir(l) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("📌 ${l.nombre}", modifier = Modifier.weight(1f))
                Text(l.zona, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
