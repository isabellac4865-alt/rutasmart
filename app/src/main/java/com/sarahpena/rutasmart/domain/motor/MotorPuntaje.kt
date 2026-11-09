package com.sarahpena.rutasmart.domain.motor

import com.sarahpena.rutasmart.domain.model.Alternativa
import com.sarahpena.rutasmart.domain.model.AlternativaPuntuada
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Rangos

/**
 * Motor de puntuación determinístico: compara todas las alternativas con los criterios del usuario
 * y devuelve el ranking (la recomendada queda en la posición 1).
 */
object MotorPuntaje {

    fun calcularRangos(alternativas: List<Alternativa>): Rangos {
        require(alternativas.isNotEmpty()) { "No hay alternativas para comparar." }
        var tMin = Double.MAX_VALUE; var tMax = -Double.MAX_VALUE
        var cMin = Double.MAX_VALUE; var cMax = -Double.MAX_VALUE
        var kMin = Double.MAX_VALUE; var kMax = -Double.MAX_VALUE
        var bMin = Double.MAX_VALUE; var bMax = -Double.MAX_VALUE
        var eMin = Double.MAX_VALUE; var eMax = -Double.MAX_VALUE
        for (a in alternativas) { // un solo recorrido para todos los mínimos y máximos
            val t = a.tiempoTotalMinutos.toDouble(); tMin = minOf(tMin, t); tMax = maxOf(tMax, t)
            val c = a.costoTotal.toDouble(); cMin = minOf(cMin, c); cMax = maxOf(cMax, c)
            val k = a.comodidadPromedio; kMin = minOf(kMin, k); kMax = maxOf(kMax, k)
            val b = a.transbordos.toDouble(); bMin = minOf(bMin, b); bMax = maxOf(bMax, b)
            val e = a.co2Gramos.toDouble(); eMin = minOf(eMin, e); eMax = maxOf(eMax, e)
        }
        return Rangos(tMin..tMax, cMin..cMax, kMin..kMax, bMin..bMax, eMin..eMax)
    }

    /** Puntúa y ordena. Ante empate gana la más rápida. */
    fun clasificar(alternativas: List<Alternativa>, criterios: CriteriosUsuario): List<AlternativaPuntuada> {
        val rangos = calcularRangos(alternativas)
        val puntuadas = alternativas.map { it to it.calcularPuntaje(criterios, rangos) }
            .sortedWith(compareByDescending<Pair<Alternativa, Double>> { it.second }.thenBy { it.first.tiempoTotalMinutos })
        return puntuadas.mapIndexed { i, (alt, p) -> AlternativaPuntuada(alt, p, i + 1) }
    }

    /** Selección de la mejor con una variable acumuladora, como describe el diseño. */
    fun mejor(alternativas: List<Alternativa>, criterios: CriteriosUsuario): Alternativa {
        val rangos = calcularRangos(alternativas)
        var mejor = alternativas.first()
        var mejorPuntaje = -1.0
        for (a in alternativas) {
            val p = a.calcularPuntaje(criterios, rangos)
            if (p > mejorPuntaje) { mejorPuntaje = p; mejor = a }
        }
        return mejor
    }
}
