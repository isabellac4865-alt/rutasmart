package com.sarahpena.rutasmart.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculos geográficos. */
object Geo {
    private const val RADIO_TIERRA_KM = 6371.0

    /** Distancia en línea recta entre dos coordenadas (fórmula de Haversine), en km. */
    fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * RADIO_TIERRA_KM * asin(sqrt(a))
    }

    fun distanciaKm(a: Parada, b: Parada) = distanciaKm(a.latitud, a.longitud, b.latitud, b.longitud)
    fun distanciaKm(a: Lugar, b: Lugar) = distanciaKm(a.latitud, a.longitud, b.latitud, b.longitud)
}

/** Secuencia ordenada de paradas (List<Parada>) que forma un recorrido. */
class Ruta(paradas: List<Parada>) {
    private val _paradas: List<Parada> = paradas.toList()
    val paradas: List<Parada> get() = _paradas

    init {
        require(_paradas.size >= 2) { "Una ruta necesita al menos dos paradas." }
    }

    val origen: Parada get() = _paradas.first()
    val destino: Parada get() = _paradas.last()

    /** Suma, con un ciclo for, la distancia en línea recta entre cada par de paradas consecutivas. */
    fun calcularDistanciaTotal(): Double {
        var total = 0.0
        for (i in 0 until _paradas.size - 1) {
            total += Geo.distanciaKm(_paradas[i], _paradas[i + 1])
        }
        return total
    }

    /** Minutos estimados para recorrer la ruta con el medio indicado (sin espera). */
    fun calcularTiempoEstimado(medio: MedioDeTransporte, horaPico: Boolean): Double =
        medio.calcularDuracion(calcularDistanciaTotal() * medio.factorRecorrido, horaPico)
}

/** Parte de un trayecto hecha con un solo medio (y una sola línea, si es transporte público). */
class Tramo(
    val medio: MedioDeTransporte,
    val ruta: Ruta,
    val linea: Linea? = null
) {
    /** Distancia real aproximada (línea recta × factor de recorrido del medio). */
    val distanciaKm: Double get() = ruta.calcularDistanciaTotal() * medio.factorRecorrido

    fun duracionMinutos(horaPico: Boolean): Double = medio.esperaMinutos + ruta.calcularTiempoEstimado(medio, horaPico)

    fun co2Gramos(): Double = distanciaKm * medio.co2GramosPorKm

    val descripcion: String
        get() = when {
            linea != null -> "${medio.tipo.emoji} ${linea.nombre}: ${ruta.origen.nombre} → ${ruta.destino.nombre}"
            else -> "${medio.tipo.emoji} ${medio.nombre}: ${ruta.origen.nombre} → ${ruta.destino.nombre}"
        }
}
