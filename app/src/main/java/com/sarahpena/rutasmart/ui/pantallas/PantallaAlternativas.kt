package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.model.AlternativaPuntuada
import com.sarahpena.rutasmart.ui.RutaViewModel
import kotlin.math.roundToInt

@Composable
fun PantallaAlternativas(vm: RutaViewModel, onDetalle: (Int) -> Unit, onPreguntar: () -> Unit) {
    val ui by vm.ui.collectAsState()
    if (ui.ranking.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aún no hay alternativas. Vuelve a Inicio y busca una ruta.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("lista_alternativas"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("${ui.origen?.nombre ?: ""}  →  ${ui.destino?.nombre ?: ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${ui.ranking.size} alternativas ordenadas por puntaje según tus prioridades", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Tarjeta {
                Text("💡 Recomendación", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(vm.explicacionLocal(), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPreguntar, modifier = Modifier.fillMaxWidth()) { Text("🤖 Preguntarle al asistente por qué") }
            }
        }
        items(ui.ranking, key = { it.alternativa.id }) { p ->
            TarjetaAlternativa(p) { onDetalle(p.posicion - 1) }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TarjetaAlternativa(p: AlternativaPuntuada, onClick: () -> Unit) {
    val a = p.alternativa
    Tarjeta(
        Modifier
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Opción ${p.posicion}: ${a.titulo}, ${a.tiempoTotalMinutos} minutos, ${a.costoTotal} pesos, " +
                    "puntaje ${p.puntaje.roundToInt()}${if (p.esRecomendada) ", recomendada" else ""}"
            }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${p.posicion}. ${a.titulo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (p.esRecomendada) Text("⭐ Recomendada", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { a.medios.forEach { EtiquetaMedio(it) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metrica("⏱️", "${a.tiempoTotalMinutos} min", "tiempo")
            Metrica("💲", pesos(a.costoTotal), "costo")
            Metrica("🔁", "${a.transbordos}", "transbordos")
            Metrica("🌱", "${a.co2Gramos} g", "CO₂")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Puntaje ${p.puntaje.roundToInt()}/100", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        BarraPuntaje(p.puntaje, if (p.esRecomendada) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        Text("Ver ruta en el mapa ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}
