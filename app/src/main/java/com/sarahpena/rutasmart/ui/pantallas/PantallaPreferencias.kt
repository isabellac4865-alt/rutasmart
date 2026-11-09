@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sarahpena.rutasmart.ui.pantallas

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.ui.RutaViewModel
import kotlin.math.roundToInt

@Composable
fun PantallaPreferencias(vm: RutaViewModel, onGuardado: () -> Unit) {
    val guardados by vm.criterios.collectAsState()
    val ui by vm.ui.collectAsState()
    var c by remember(guardados) { mutableStateOf(guardados) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("¿Qué es más importante para ti?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("0 = no me importa · 5 = muy importante. El puntaje de cada ruta se calcula con estos pesos.", style = MaterialTheme.typography.bodySmall)

        Tarjeta {
            Peso("⏱️ Tiempo de viaje", c.tiempo) { c = c.copy(tiempo = it) }
            Peso("💲 Costo", c.costo) { c = c.copy(costo = it) }
            Peso("😌 Comodidad", c.comodidad) { c = c.copy(comodidad = it) }
            Peso("🔁 Pocos transbordos", c.transbordos) { c = c.copy(transbordos = it) }
            Peso("🌱 Impacto ambiental", c.ambiental) { c = c.copy(ambiental = it) }
        }

        Tarjeta {
            Text("Medios que quiero usar", fontWeight = FontWeight.SemiBold)
            TipoMedio.entries.chunked(3).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { tipo ->
                        val activo = tipo in c.mediosPermitidos
                        FilterChip(
                            selected = activo,
                            onClick = {
                                c = c.copy(mediosPermitidos = if (activo) c.mediosPermitidos - tipo else c.mediosPermitidos + tipo)
                            },
                            label = { Text("${tipo.emoji} ${tipo.etiqueta}") },
                            modifier = Modifier.semantics { contentDescription = "${tipo.etiqueta}: ${if (activo) "permitido" else "no permitido"}" }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🚦 Viajo en hora pico")
                Switch(checked = c.horaPico, onCheckedChange = { c = c.copy(horaPico = it) })
            }
        }

        MensajeError(ui.error)
        Button(
            onClick = { vm.guardarCriterios(c, onGuardado) },
            modifier = Modifier.fillMaxWidth().testTag("btn_guardar_pref")
        ) { Text("Guardar preferencias") }
        TextButton(onClick = { c = CriteriosUsuario() }, modifier = Modifier.fillMaxWidth()) { Text("Restablecer valores recomendados") }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun Peso(titulo: String, valor: Int, onCambio: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(titulo)
            Text("$valor / ${CriteriosUsuario.PESO_MAXIMO}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = valor.toFloat(),
            onValueChange = { onCambio(it.roundToInt()) },
            valueRange = 0f..CriteriosUsuario.PESO_MAXIMO.toFloat(),
            steps = CriteriosUsuario.PESO_MAXIMO - 1,
            modifier = Modifier.semantics { contentDescription = "$titulo, importancia $valor de ${CriteriosUsuario.PESO_MAXIMO}" }
        )
    }
}
