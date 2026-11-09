package com.sarahpena.rutasmart.domain.model

import com.sarahpena.rutasmart.domain.motor.Bitacora

/** Una búsqueda guardada en el historial (local en Room y, si hay sesión, en Firestore). */
data class ConsultaHistorial(
    val id: String,
    val origen: String,
    val destino: String,
    val fechaMillis: Long,
    val recomendada: String,
    val puntaje: Double,
    val tiempoMinutos: Int,
    val costo: Int,
    val alternativas: Int,
    val criterios: String,
    val sincronizada: Boolean = false
) {
    companion object {
        /** Crea el registro a partir del resultado de una búsqueda. */
        fun desde(id: String, origen: Lugar, destino: Lugar, ranking: List<AlternativaPuntuada>, criterios: CriteriosUsuario, ahora: Long): ConsultaHistorial {
            val mejor = ranking.first()
            return ConsultaHistorial(
                id = id, origen = origen.nombre, destino = destino.nombre, fechaMillis = ahora,
                recomendada = mejor.alternativa.titulo, puntaje = mejor.puntaje,
                tiempoMinutos = mejor.alternativa.tiempoTotalMinutos, costo = mejor.alternativa.costoTotal,
                alternativas = ranking.size, criterios = criterios.resumen()
            )
        }
    }
}

/**
 * Sube a la nube las consultas pendientes. Usa un ciclo while que se repite mientras haya
 * pendientes Y exista conexión de red, como define el diseño.
 */
object SincronizadorHistorial {

    data class Resultado(val subidas: Int, val pendientes: Int, val detenidoPorRed: Boolean)

    fun sincronizar(
        pendientes: List<ConsultaHistorial>,
        hayRed: () -> Boolean,
        subir: (ConsultaHistorial) -> Boolean,
        marcarSincronizada: (String) -> Unit,
        maxIntentosPorConsulta: Int = 2
    ): Resultado {
        val cola = ArrayDeque(pendientes)
        val intentos = HashMap<String, Int>()
        var subidas = 0
        while (cola.isNotEmpty() && hayRed()) {
            val consulta = cola.removeFirst()
            val ok = try {
                subir(consulta)
            } catch (e: Exception) {
                Bitacora.error("Sync", "Falló la subida de ${consulta.id}", e)
                false
            }
            if (ok) {
                marcarSincronizada(consulta.id)
                subidas++
            } else {
                val n = (intentos[consulta.id] ?: 0) + 1
                intentos[consulta.id] = n
                if (n < maxIntentosPorConsulta) cola.addLast(consulta)
            }
        }
        val restantes = pendientes.size - subidas
        return Resultado(subidas, restantes, detenidoPorRed = cola.isNotEmpty())
    }
}
