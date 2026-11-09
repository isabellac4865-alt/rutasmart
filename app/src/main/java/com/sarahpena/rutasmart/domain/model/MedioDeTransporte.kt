package com.sarahpena.rutasmart.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Tipos de medio de transporte que maneja la aplicación. */
enum class TipoMedio(val etiqueta: String, val emoji: String, val esPublico: Boolean) {
    CAMINATA("Caminata", "🚶", false),
    BICICLETA("Bicicleta", "🚲", false),
    BUS("Bus SITP", "🚌", true),
    METRO("TransMilenio", "🚇", true),
    TAXI("Taxi", "🚕", false)
}

/**
 * Tarifas y parámetros de referencia (Bogotá). Son valores aproximados para comparar alternativas;
 * se centralizan aquí para actualizarlos en un solo lugar.
 */
object Tarifas {
    const val PASAJE_PUBLICO = 3_200          // COP por abordaje del sistema (TransMilenio / SITP)
    const val TRANSBORDO_ENTRE_SISTEMAS = 200 // COP al pasar de SITP a TransMilenio o viceversa
    const val TAXI_ARRANQUE = 4_800
    const val TAXI_POR_KM = 1_300
    const val TAXI_MINIMO = 7_200
}

/**
 * Contrato común de todos los medios de transporte (polimorfismo).
 * El motor de recomendación los usa a través de esta interfaz sin conocer la clase concreta.
 */
interface MedioDeTransporte {
    val tipo: TipoMedio
    val nombre: String get() = tipo.etiqueta

    /** Comodidad percibida de 1 (muy incómodo) a 5 (muy cómodo). */
    val comodidad: Int

    /** Emisiones aproximadas por pasajero, en gramos de CO₂ por kilómetro. */
    val co2GramosPorKm: Int

    /** Cuánto más largo es el recorrido real frente a la línea recta (calles, desvíos). */
    val factorRecorrido: Double

    /** Minutos de espera promedio antes de abordar. */
    val esperaMinutos: Int

    /** Duración en minutos para recorrer [distanciaKm] (ya incluye el factor de recorrido). */
    fun calcularDuracion(distanciaKm: Double, horaPico: Boolean): Double

    /** Costo en pesos colombianos para recorrer [distanciaKm]. */
    fun calcularCosto(distanciaKm: Double): Int

    /** Comodidad ajustada a la hora del día (en hora pico el transporte público va lleno). */
    fun comodidadEn(horaPico: Boolean): Int = comodidad
}

/** Tiempo en minutos a partir de una velocidad en km/h. */
internal fun minutosA(distanciaKm: Double, kmPorHora: Double): Double = distanciaKm / kmPorHora * 60.0

class Caminata : MedioDeTransporte {
    override val tipo = TipoMedio.CAMINATA
    override val comodidad = 3
    override val co2GramosPorKm = 0
    override val factorRecorrido = 1.25
    override val esperaMinutos = 0
    override fun calcularDuracion(distanciaKm: Double, horaPico: Boolean) = minutosA(distanciaKm, 4.8)
    override fun calcularCosto(distanciaKm: Double) = 0
}

class Bicicleta : MedioDeTransporte {
    override val tipo = TipoMedio.BICICLETA
    override val comodidad = 2 // exige esfuerzo físico
    override val co2GramosPorKm = 0
    override val factorRecorrido = 1.3
    override val esperaMinutos = 0
    override fun calcularDuracion(distanciaKm: Double, horaPico: Boolean) = minutosA(distanciaKm, 14.0)
    override fun calcularCosto(distanciaKm: Double) = 0
}

class Bus : MedioDeTransporte {
    override val tipo = TipoMedio.BUS
    override val comodidad = 3
    override val co2GramosPorKm = 70
    override val factorRecorrido = 1.2
    override val esperaMinutos = 8
    override fun calcularDuracion(distanciaKm: Double, horaPico: Boolean) =
        minutosA(distanciaKm, if (horaPico) 11.0 else 17.0)
    override fun calcularCosto(distanciaKm: Double) = Tarifas.PASAJE_PUBLICO
    override fun comodidadEn(horaPico: Boolean) = if (horaPico) comodidad - 1 else comodidad
}

/** Sistema de transporte masivo (TransMilenio / metro): carril exclusivo, poco afectado por el tráfico. */
class Metro : MedioDeTransporte {
    override val tipo = TipoMedio.METRO
    override val comodidad = 2
    override val co2GramosPorKm = 45
    override val factorRecorrido = 1.1
    override val esperaMinutos = 5
    override fun calcularDuracion(distanciaKm: Double, horaPico: Boolean) =
        minutosA(distanciaKm, if (horaPico) 22.0 else 26.0)
    override fun calcularCosto(distanciaKm: Double) = Tarifas.PASAJE_PUBLICO
    override fun comodidadEn(horaPico: Boolean) = if (horaPico) 1 else comodidad
}

class Taxi : MedioDeTransporte {
    override val tipo = TipoMedio.TAXI
    override val comodidad = 5
    override val co2GramosPorKm = 170
    override val factorRecorrido = 1.35
    override val esperaMinutos = 4
    override fun calcularDuracion(distanciaKm: Double, horaPico: Boolean) =
        minutosA(distanciaKm, if (horaPico) 13.0 else 24.0)
    override fun calcularCosto(distanciaKm: Double): Int {
        val bruto = Tarifas.TAXI_ARRANQUE + Tarifas.TAXI_POR_KM * distanciaKm
        return max(Tarifas.TAXI_MINIMO, (bruto / 100.0).roundToInt() * 100)
    }
}

/** Fábrica de los medios disponibles; devuelve un arreglo (Array) como indica el diseño. */
object Medios {
    val caminata = Caminata()
    val bicicleta = Bicicleta()
    val bus = Bus()
    val metro = Metro()
    val taxi = Taxi()

    fun todos(): Array<MedioDeTransporte> = arrayOf(caminata, bicicleta, bus, metro, taxi)

    fun de(tipo: TipoMedio): MedioDeTransporte = when (tipo) {
        TipoMedio.CAMINATA -> caminata
        TipoMedio.BICICLETA -> bicicleta
        TipoMedio.BUS -> bus
        TipoMedio.METRO -> metro
        TipoMedio.TAXI -> taxi
    }
}
