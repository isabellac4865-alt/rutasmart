package com.sarahpena.rutasmart.ui.pantallas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.model.Alternativa
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.ui.RutaViewModel
import com.sarahpena.rutasmart.ui.tema.colorDe
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PantallaDetalle(vm: RutaViewModel, indice: Int) {
    val ui by vm.ui.collectAsState()
    val p = ui.ranking.getOrNull(indice)
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("La alternativa ya no está disponible.") }
        return
    }
    val a = p.alternativa
    val contexto = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(a.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Puntaje ${p.puntaje.roundToInt()}/100 · ${a.tiempoTotalMinutos} min · ${pesos(a.costoTotal)} · ${"%.1f".format(a.distanciaTotalKm)} km")

        Tarjeta {
            MapaRuta(a, Modifier.fillMaxWidth().height(260.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { a.medios.forEach { EtiquetaMedio(it) } }
            Text("Mapa esquemático con coordenadas reales de las paradas (norte arriba).", style = MaterialTheme.typography.labelSmall)
        }

        Tarjeta {
            Text("Paso a paso", fontWeight = FontWeight.Bold)
            a.tramos.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(colorDe(t.medio.tipo), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Text(t.descripcion, fontWeight = FontWeight.SemiBold)
                        val paradasIntermedias = (t.ruta.paradas.size - 2).coerceAtLeast(0)
                        Text(
                            "${t.duracionMinutos(a.horaPico).roundToInt()} min · ${"%.1f".format(t.distanciaKm)} km" +
                                if (paradasIntermedias > 0) " · $paradasIntermedias paradas intermedias" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (paradasIntermedias in 1..8) {
                            Text(
                                t.ruta.paradas.drop(1).dropLast(1).joinToString(" · ") { it.nombre },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Tarjeta {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metrica("🔁", "${a.transbordos}", "transbordos")
                Metrica("😌", "%.1f".format(a.comodidadPromedio), "comodidad /5")
                Metrica("🌱", "${a.co2Gramos} g", "CO₂")
            }
        }

        OutlinedButton(
            onClick = {
                val o = a.tramos.first().ruta.origen
                val d = a.tramos.last().ruta.destino
                val modo = when {
                    TipoMedio.METRO in a.medios || TipoMedio.BUS in a.medios -> "transit"
                    TipoMedio.TAXI in a.medios -> "driving"
                    TipoMedio.BICICLETA in a.medios -> "bicycling"
                    else -> "walking"
                }
                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${o.latitud},${o.longitud}&destination=${d.latitud},${d.longitud}&travelmode=$modo")
                runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗺️ Abrir en Google Maps") }
        Spacer(Modifier.height(72.dp))
    }
}

/** Dibuja los tramos proyectando latitud/longitud sobre el lienzo (sin depender de una API de mapas). */
@Composable
private fun MapaRuta(a: Alternativa, modifier: Modifier) {
    val fondo = MaterialTheme.colorScheme.surfaceVariant
    val borde = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .background(fondo, RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Mapa de la ruta ${a.titulo} con ${a.tramos.size} tramos" }
    ) {
        val puntos = a.tramos.flatMap { it.ruta.paradas }
        val minLat = puntos.minOf { it.latitud }; val maxLat = puntos.maxOf { it.latitud }
        val minLon = puntos.minOf { it.longitud }; val maxLon = puntos.maxOf { it.longitud }
        val margen = 28f
        val ancho = size.width - 2 * margen
        val alto = size.height - 2 * margen
        val rango = max(maxLat - minLat, maxLon - minLon).coerceAtLeast(0.002)
        fun aPantalla(lat: Double, lon: Double) = Offset(
            margen + ((lon - minLon) / rango * ancho).toFloat() + ((ancho - ((maxLon - minLon) / rango * ancho).toFloat()) / 2f),
            margen + ((maxLat - lat) / rango * alto).toFloat() + ((alto - ((maxLat - minLat) / rango * alto).toFloat()) / 2f)
        )
        a.tramos.forEach { t ->
            val path = Path()
            t.ruta.paradas.forEachIndexed { i, pr ->
                val o = aPantalla(pr.latitud, pr.longitud)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            val esCaminata = t.medio.tipo == TipoMedio.CAMINATA
            drawPath(
                path, colorDe(t.medio.tipo),
                style = Stroke(
                    width = if (esCaminata) 5f else 9f, cap = StrokeCap.Round,
                    pathEffect = if (esCaminata) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null
                )
            )
            t.ruta.paradas.forEach { pr -> drawCircle(colorDe(t.medio.tipo), 6f, aPantalla(pr.latitud, pr.longitud)) }
        }
        val inicio = a.tramos.first().ruta.origen
        val fin = a.tramos.last().ruta.destino
        drawCircle(borde, 13f, aPantalla(inicio.latitud, inicio.longitud))
        drawCircle(colorDe(TipoMedio.BICICLETA), 10f, aPantalla(inicio.latitud, inicio.longitud))
        drawCircle(borde, 13f, aPantalla(fin.latitud, fin.longitud))
        drawCircle(colorDe(TipoMedio.METRO), 10f, aPantalla(fin.latitud, fin.longitud))
    }
}
