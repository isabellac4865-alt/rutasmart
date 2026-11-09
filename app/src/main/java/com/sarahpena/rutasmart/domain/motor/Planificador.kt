package com.sarahpena.rutasmart.domain.motor

import com.sarahpena.rutasmart.domain.model.Alternativa
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Geo
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.domain.model.Medios
import com.sarahpena.rutasmart.domain.model.Parada
import com.sarahpena.rutasmart.domain.model.Ruta
import com.sarahpena.rutasmart.domain.model.RutaNoEncontradaException
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.domain.model.Tramo
import com.sarahpena.rutasmart.domain.model.UbicacionInvalidaException
import java.util.PriorityQueue

/**
 * Genera las alternativas para ir de un lugar a otro:
 *  - directas: caminata, bicicleta y taxi;
 *  - transporte público: búsqueda del camino más rápido (Dijkstra) en el grafo de [RedTransporte],
 *    para TransMilenio, SITP y la combinación de ambos.
 */
class Planificador(private val red: RedTransporte) {

    companion object {
        const val DISTANCIA_MINIMA_KM = 0.2
        const val MAX_CAMINATA_DIRECTA_KM = 3.5     // caminar todo el trayecto (≈ 55 min)
        const val MAX_BICICLETA_KM = 12.0
        const val MAX_CAMINATA_ACCESO_KM = 1.3      // caminar hasta/desde una parada
        const val MAX_CAMINATA_TRANSBORDO_KM = 0.45 // caminar entre paradas cercanas de distinto sistema
        const val PENALIDAD_TRANSBORDO_MIN = 3.0
        private const val ORIGEN = "#origen"
        private const val DESTINO = "#destino"
    }

    /**
     * @param suspendidas ids de líneas que el administrador suspendió: no se usan en la búsqueda.
     */
    fun generar(
        origen: Lugar, destino: Lugar, criterios: CriteriosUsuario, suspendidas: Set<String> = emptySet()
    ): MutableList<Alternativa> {
        criterios.validar()
        val distancia = Geo.distanciaKm(origen, destino)
        if (distancia < DISTANCIA_MINIMA_KM) {
            throw UbicacionInvalidaException("El origen y el destino están en el mismo lugar. Elige un destino diferente.")
        }
        val permitidos = criterios.mediosPermitidos
        val pico = criterios.horaPico
        val alternativas = mutableListOf<Alternativa>()
        val directa = Ruta(listOf(origen.comoParada(), destino.comoParada()))

        if (TipoMedio.CAMINATA in permitidos && distancia * Medios.caminata.factorRecorrido <= MAX_CAMINATA_DIRECTA_KM) {
            alternativas += Alternativa("caminata", "Caminando", listOf(Tramo(Medios.caminata, directa)), pico)
        }
        if (TipoMedio.BICICLETA in permitidos && distancia * Medios.bicicleta.factorRecorrido <= MAX_BICICLETA_KM) {
            alternativas += Alternativa("bicicleta", "En bicicleta", listOf(Tramo(Medios.bicicleta, directa)), pico)
        }
        if (TipoMedio.TAXI in permitidos) {
            alternativas += Alternativa("taxi", "En taxi", listOf(Tramo(Medios.taxi, directa)), pico)
        }

        val combinaciones = listOf(
            setOf(TipoMedio.METRO) to "Solo TransMilenio",
            setOf(TipoMedio.BUS) to "Solo bus SITP",
            setOf(TipoMedio.METRO, TipoMedio.BUS) to "TransMilenio + SITP"
        )
        for ((tipos, titulo) in combinaciones) {
            if (!permitidos.containsAll(tipos)) continue
            val alt = buscarTransportePublico(origen, destino, tipos, pico, titulo, suspendidas) ?: continue
            if (alternativas.none { it.firma == alt.firma }) alternativas += alt
        }

        if (alternativas.isEmpty()) throw RutaNoEncontradaException()
        return alternativas
    }

    // ------------------------------------------------------------------ Dijkstra

    /** [abordo] = ya se usó al menos un vehículo (evita rutas de solo caminata). */
    private data class Estado(val nodo: String, val linea: Linea?, val abordo: Boolean)

    private data class Paso(val desde: Estado, val linea: Linea?)

    private data class Entrada(val estado: Estado, val minutos: Double)

    private fun buscarTransportePublico(
        origen: Lugar, destino: Lugar, tipos: Set<TipoMedio>, pico: Boolean, titulo: String,
        suspendidas: Set<String>
    ): Alternativa? {
        val pOrigen = origen.comoParada()
        val pDestino = destino.comoParada()
        val nodos = HashMap<String, Parada>()
        red.todasLasParadas().forEach { nodos[it.id] = it }
        nodos[ORIGEN] = pOrigen
        nodos[DESTINO] = pDestino

        val inicio = Estado(ORIGEN, null, false)
        val mejor = HashMap<Estado, Double>()
        val previo = HashMap<Estado, Paso>()
        val cola = PriorityQueue<Entrada>(compareBy { it.minutos })
        mejor[inicio] = 0.0
        cola.add(Entrada(inicio, 0.0))
        var fin: Estado? = null

        while (cola.isNotEmpty()) {
            val (estado, minutos) = cola.poll() ?: break
            if (minutos > (mejor[estado] ?: Double.MAX_VALUE)) continue
            if (estado.nodo == DESTINO) { fin = estado; break }
            val aqui = nodos.getValue(estado.nodo)

            fun relajar(siguiente: Estado, costo: Double, linea: Linea?) {
                val total = minutos + costo
                if (total < (mejor[siguiente] ?: Double.MAX_VALUE)) {
                    mejor[siguiente] = total
                    previo[siguiente] = Paso(estado, linea)
                    cola.add(Entrada(siguiente, total))
                }
            }

            // 1) Caminar: del origen a paradas cercanas, de una parada al destino o entre paradas cercanas.
            if (estado.nodo == ORIGEN) {
                for (p in red.todasLasParadas()) {
                    val km = Geo.distanciaKm(pOrigen, p)
                    if (km <= MAX_CAMINATA_ACCESO_KM) relajar(Estado(p.id, null, false), caminar(km, pico), null)
                }
            } else {
                val kmDestino = Geo.distanciaKm(aqui, pDestino)
                if (estado.abordo && kmDestino <= MAX_CAMINATA_ACCESO_KM) relajar(Estado(DESTINO, null, true), caminar(kmDestino, pico), null)
                if (estado.linea != null) { // bajarse del vehículo y caminar a otra parada cercana
                    for (p in red.todasLasParadas()) {
                        if (p.id == aqui.id) continue
                        val km = Geo.distanciaKm(aqui, p)
                        if (km <= MAX_CAMINATA_TRANSBORDO_KM) relajar(Estado(p.id, null, true), caminar(km, pico) + PENALIDAD_TRANSBORDO_MIN, null)
                    }
                }
            }

            // 2) Viajar en una línea permitida hasta la siguiente parada.
            if (estado.nodo != ORIGEN) {
                for (c in red.conexionesDe(estado.nodo)) {
                    if (c.linea.tipo !in tipos || c.linea.id in suspendidas) continue
                    val medio = Medios.de(c.linea.tipo)
                    val destinoParada = nodos.getValue(c.destinoId)
                    val km = Geo.distanciaKm(aqui, destinoParada) * medio.factorRecorrido
                    var costo = medio.calcularDuracion(km, pico)
                    if (estado.linea?.id != c.linea.id) {
                        costo += medio.esperaMinutos + if (estado.linea != null) PENALIDAD_TRANSBORDO_MIN else 0.0
                    }
                    relajar(Estado(c.destinoId, c.linea, true), costo, c.linea)
                }
            }
        }

        val llegada = fin ?: return null
        // Reconstruir el camino (del destino hacia atrás) y agrupar pasos consecutivos de la misma línea.
        val pasos = mutableListOf<Pair<String, Linea?>>()
        var actual: Estado = llegada
        while (actual != inicio) {
            val paso = previo[actual] ?: return null
            pasos.add(0, actual.nodo to paso.linea)
            actual = paso.desde
        }
        val tramos = mutableListOf<Tramo>()
        var paradasTramo = mutableListOf(pOrigen)
        var lineaTramo: Linea? = pasos.firstOrNull()?.second
        for ((nodo, linea) in pasos) {
            val parada = nodos.getValue(nodo)
            if (linea?.id != lineaTramo?.id) {
                tramos += crearTramo(paradasTramo, lineaTramo)
                paradasTramo = mutableListOf(paradasTramo.last())
                lineaTramo = linea
            }
            paradasTramo += parada
        }
        tramos += crearTramo(paradasTramo, lineaTramo)
        val conVehiculo = tramos.filter { it.linea != null }
        if (conVehiculo.isEmpty()) return null // solo caminata: ya está cubierta por la alternativa directa
        val id = "publico-" + tipos.joinToString("-") { it.name.lowercase() }
        return Alternativa(id, titulo, tramos.filter { it.ruta.calcularDistanciaTotal() > 0.0 }, pico)
    }

    private fun crearTramo(paradas: List<Parada>, linea: Linea?): Tramo {
        val medio = if (linea == null) Medios.caminata else Medios.de(linea.tipo)
        return Tramo(medio, Ruta(paradas), linea)
    }

    private fun caminar(kmLineaRecta: Double, pico: Boolean): Double =
        Medios.caminata.calcularDuracion(kmLineaRecta * Medios.caminata.factorRecorrido, pico)
}
