package com.sarahpena.rutasmart.domain.model

import kotlin.math.roundToInt

/**
 * Prioridades que define el usuario (encapsula los pesos de cada criterio, de 0 a 5).
 * La clase [Alternativa] la usa por composición para calcular su puntaje.
 */
data class CriteriosUsuario(
    val tiempo: Int = 5,
    val costo: Int = 3,
    val comodidad: Int = 3,
    val transbordos: Int = 2,
    val ambiental: Int = 1,
    val horaPico: Boolean = false,
    val mediosPermitidos: Set<TipoMedio> = TipoMedio.entries.toSet()
) {
    init {
        for (peso in listOf(tiempo, costo, comodidad, transbordos, ambiental)) {
            require(peso in 0..PESO_MAXIMO) { "Cada criterio debe estar entre 0 y $PESO_MAXIMO." }
        }
    }

    val sumaPesos: Int get() = tiempo + costo + comodidad + transbordos + ambiental

    /** Lanza [CriteriosIncompletosException] si no hay ningún criterio o ningún medio permitido. */
    fun validar(): CriteriosUsuario {
        if (sumaPesos == 0) throw CriteriosIncompletosException("Define al menos un criterio (tiempo, costo, comodidad, transbordos o ambiente).")
        if (mediosPermitidos.isEmpty()) throw CriteriosIncompletosException("Selecciona al menos un medio de transporte.")
        return this
    }

    /** Texto corto con las prioridades más altas, para la explicación y el historial. */
    fun resumen(): String {
        val pares = listOf("tiempo" to tiempo, "costo" to costo, "comodidad" to comodidad, "pocos transbordos" to transbordos, "ambiente" to ambiental)
        val altas = pares.filter { it.second > 0 }.sortedByDescending { it.second }.take(2).map { it.first }
        val base = if (altas.isEmpty()) "sin prioridades" else "prioriza " + altas.joinToString(" y ")
        return if (horaPico) "$base · hora pico" else base
    }

    companion object { const val PESO_MAXIMO = 5 }
}

/** Mínimos y máximos de cada métrica entre las alternativas de una búsqueda (para normalizar). */
data class Rangos(
    val tiempo: ClosedFloatingPointRange<Double>,
    val costo: ClosedFloatingPointRange<Double>,
    val comodidad: ClosedFloatingPointRange<Double>,
    val transbordos: ClosedFloatingPointRange<Double>,
    val co2: ClosedFloatingPointRange<Double>
)

/**
 * Una forma completa de ir del origen al destino: agrega uno o varios [Tramo] (agregación)
 * y calcula sus métricas y su puntaje.
 */
class Alternativa(
    val id: String,
    val titulo: String,
    val tramos: List<Tramo>,
    val horaPico: Boolean
) {
    init {
        require(tramos.isNotEmpty()) { "Una alternativa debe tener al menos un tramo." }
    }

    val tiempoTotalMinutos: Int get() = tramos.sumOf { it.duracionMinutos(horaPico) }.roundToInt()

    val distanciaTotalKm: Double get() = tramos.sumOf { it.distanciaKm }

    /** Tramos en vehículo (todo lo que no es caminar). */
    private val tramosEnVehiculo: List<Tramo> get() = tramos.filter { it.medio.tipo != TipoMedio.CAMINATA }

    /** Cambios de vehículo: abordajes menos uno. */
    val transbordos: Int get() = (tramosEnVehiculo.size - 1).coerceAtLeast(0)

    /**
     * Costo total en COP. El primer abordaje del transporte público paga pasaje; los cambios entre
     * líneas de TransMilenio son gratis (mismo sistema) y pasar entre SITP y TransMilenio cuesta el transbordo.
     */
    val costoTotal: Int
        get() {
            var total = 0
            var anteriorPublico: TipoMedio? = null
            for (tramo in tramos) {
                val tipo = tramo.medio.tipo
                if (tipo.esPublico) {
                    total += when {
                        anteriorPublico == null -> tramo.medio.calcularCosto(tramo.distanciaKm)
                        anteriorPublico == tipo && tipo == TipoMedio.METRO -> 0
                        else -> Tarifas.TRANSBORDO_ENTRE_SISTEMAS
                    }
                    anteriorPublico = tipo
                } else {
                    total += tramo.medio.calcularCosto(tramo.distanciaKm)
                }
            }
            return total
        }

    /** Comodidad promedio ponderada por la duración de cada tramo (1 a 5). */
    val comodidadPromedio: Double
        get() {
            var suma = 0.0
            var minutos = 0.0
            tramos.forEach { t ->
                val m = t.duracionMinutos(horaPico)
                suma += t.medio.comodidadEn(horaPico) * m
                minutos += m
            }
            return if (minutos == 0.0) 0.0 else suma / minutos
        }

    val co2Gramos: Int get() = tramos.sumOf { it.co2Gramos() }.roundToInt()

    /** Medios usados, en orden y sin repetir. */
    val medios: List<TipoMedio> get() = tramos.map { it.medio.tipo }.distinct()

    /** Firma para detectar alternativas repetidas (mismos medios y líneas). */
    val firma: String get() = tramos.joinToString(">") { it.linea?.id ?: it.medio.tipo.name }

    /**
     * Puntaje de 0 a 100. Cada métrica se compara con la mejor alternativa de la búsqueda por proporción
     * (mejor / propia, con un pequeño margen para que diferencias mínimas no pesen de más) y se pondera
     * con el peso que eligió el usuario.
     */
    fun calcularPuntaje(criterios: CriteriosUsuario, rangos: Rangos): Double {
        if (criterios.sumaPesos == 0) return 0.0
        val suma = criterios.tiempo * menorEsMejor(tiempoTotalMinutos.toDouble(), rangos.tiempo.start, MARGEN_TIEMPO) +
            criterios.costo * menorEsMejor(costoTotal.toDouble(), rangos.costo.start, MARGEN_COSTO) +
            criterios.comodidad * mayorEsMejor(comodidadPromedio, rangos.comodidad.endInclusive) +
            criterios.transbordos * menorEsMejor(transbordos.toDouble(), rangos.transbordos.start, MARGEN_TRANSBORDOS) +
            criterios.ambiental * menorEsMejor(co2Gramos.toDouble(), rangos.co2.start, MARGEN_CO2)
        return (1000.0 * suma / criterios.sumaPesos).roundToInt() / 10.0
    }

    private fun menorEsMejor(valor: Double, mejor: Double, margen: Double): Double = (mejor + margen) / (valor + margen)

    private fun mayorEsMejor(valor: Double, mejor: Double): Double = if (mejor <= 0.0) 1.0 else valor / mejor

    companion object {
        const val MARGEN_TIEMPO = 10.0                                  // minutos
        const val MARGEN_COSTO = 3200.0                                 // pesos (un pasaje)
        const val MARGEN_TRANSBORDOS = 1.0
        const val MARGEN_CO2 = 500.0                                    // gramos
    }

    override fun toString() = "$titulo ($tiempoTotalMinutos min, $$costoTotal)"
}

/** Alternativa con su puntaje calculado y su posición en el ranking. */
data class AlternativaPuntuada(
    val alternativa: Alternativa,
    val puntaje: Double,
    val posicion: Int
) {
    val esRecomendada: Boolean get() = posicion == 1
}
