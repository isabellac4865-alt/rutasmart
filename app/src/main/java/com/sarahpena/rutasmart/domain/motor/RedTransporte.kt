package com.sarahpena.rutasmart.domain.motor

import com.sarahpena.rutasmart.domain.model.Conexion
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.Parada

/**
 * Red de transporte representada como un grafo:
 *  - [paradas]: HashMap<String, Parada> (clave = id de la parada).
 *  - [conexiones]: lista de adyacencia; para cada parada, las paradas a las que se llega directamente y con qué línea.
 */
class RedTransporte {
    private val paradas = HashMap<String, Parada>()
    private val conexiones = HashMap<String, MutableList<Conexion>>()
    private val _lineas = mutableListOf<Linea>()
    private val recorridos = HashMap<String, List<String>>()

    val lineas: List<Linea> get() = _lineas

    fun agregarParada(parada: Parada) {
        require(!paradas.containsKey(parada.id)) { "La parada ${parada.id} ya existe." }
        paradas[parada.id] = parada
        conexiones.getOrPut(parada.id) { mutableListOf() }
    }

    /**
     * Registra una línea que pasa por [idsParadas] en ese orden. Con un ciclo for se crean
     * las conexiones entre paradas consecutivas (en ambos sentidos si [bidireccional]).
     */
    fun agregarLinea(linea: Linea, idsParadas: List<String>, bidireccional: Boolean = true) {
        require(idsParadas.size >= 2) { "La línea ${linea.nombre} necesita al menos dos paradas." }
        for (id in idsParadas) requireNotNull(paradas[id]) { "La línea ${linea.nombre} usa una parada inexistente: $id" }
        _lineas += linea
        recorridos[linea.id] = idsParadas.toList()
        for (i in 0 until idsParadas.size - 1) {
            conexiones.getValue(idsParadas[i]) += Conexion(idsParadas[i + 1], linea)
            if (bidireccional) conexiones.getValue(idsParadas[i + 1]) += Conexion(idsParadas[i], linea)
        }
    }

    fun parada(id: String): Parada? = paradas[id]

    fun conexionesDe(id: String): List<Conexion> = conexiones[id].orEmpty()

    fun todasLasParadas(): Collection<Parada> = paradas.values

    fun paradasDeLinea(lineaId: String): List<Parada> = recorridos[lineaId].orEmpty().mapNotNull { paradas[it] }

    val totalParadas: Int get() = paradas.size
}
