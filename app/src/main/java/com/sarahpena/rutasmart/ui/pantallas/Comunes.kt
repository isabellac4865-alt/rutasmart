package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.ui.tema.colorDe

@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() } }
}

@Composable
fun MensajeError(texto: String?) {
    if (texto == null) return
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) { Text("⚠️ $texto", color = MaterialTheme.colorScheme.onErrorContainer) }
}

/** Etiqueta de color con el emoji y nombre del medio (el color nunca es la única señal). */
@Composable
fun EtiquetaMedio(tipo: TipoMedio) {
    val color = colorDe(tipo)
    Text(
        "${tipo.emoji} ${tipo.etiqueta}",
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
fun Metrica(icono: String, valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icono $valor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Barra horizontal simple de 0 a 100. */
@Composable
fun BarraPuntaje(puntaje: Double, color: Color = MaterialTheme.colorScheme.primary) {
    val fraccion = (puntaje / 100.0).coerceIn(0.0, 1.0).toFloat()
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraccion.coerceAtLeast(0.02f))
                .background(color, RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp)
        )
    }
}

fun pesos(valor: Int): String = com.sarahpena.rutasmart.domain.casosdeuso.RecomendarRutaUseCase.formatearPesos(valor)
