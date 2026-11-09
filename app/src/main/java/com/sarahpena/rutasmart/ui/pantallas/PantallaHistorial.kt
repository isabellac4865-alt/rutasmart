package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.ui.RutaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PantallaHistorial(vm: RutaViewModel, invitado: Boolean, onRepetir: (ConsultaHistorial) -> Unit) {
    val lista by vm.historial.collectAsState()
    var confirmarBorrado by remember { mutableStateOf(false) }
    val formato = remember { SimpleDateFormat("d MMM yyyy · h:mm a", Locale("es", "CO")) }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("Borrar historial") },
            text = { Text("Se eliminarán ${lista.size} búsquedas${if (invitado) "" else " de este teléfono y de la nube"}. ¿Continuar?") },
            confirmButton = { TextButton(onClick = { vm.borrarHistorial(); confirmarBorrado = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } }
        )
    }

    if (lista.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Todavía no tienes búsquedas guardadas.\nCada vez que buscas una ruta queda aquí.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("lista_historial"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${lista.size} búsquedas", fontWeight = FontWeight.Bold)
                Row {
                    if (!invitado) TextButton(onClick = { vm.sincronizar() }) { Text("☁️ Sincronizar") }
                    TextButton(onClick = { confirmarBorrado = true }) { Text("Borrar todo") }
                }
            }
        }
        items(lista, key = { it.id }) { c ->
            Tarjeta(Modifier.semantics(mergeDescendants = true) { contentDescription = "Búsqueda de ${c.origen} a ${c.destino}, recomendada ${c.recomendada}" }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c.origen} → ${c.destino}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (invitado) "📱" else if (c.sincronizada) "☁️" else "⏳",
                        modifier = Modifier.semantics {
                            contentDescription = if (invitado) "Guardada en el teléfono" else if (c.sincronizada) "Sincronizada en la nube" else "Pendiente de sincronizar"
                        }
                    )
                }
                Text(formato.format(Date(c.fechaMillis)), style = MaterialTheme.typography.labelSmall)
                Text("⭐ ${c.recomendada} · ${c.tiempoMinutos} min · ${pesos(c.costo)} · ${c.puntaje.roundToInt()} pts", style = MaterialTheme.typography.bodyMedium)
                Text("${c.alternativas} alternativas · ${c.criterios}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRepetir(c) }) { Text("🔁 Buscar de nuevo") }
                    TextButton(
                        onClick = { vm.eliminar(c) },
                        modifier = Modifier.semantics { contentDescription = "Eliminar búsqueda de ${c.origen} a ${c.destino}" }
                    ) { Text("🗑️") }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

