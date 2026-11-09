package com.sarahpena.rutasmart

import com.sarahpena.rutasmart.domain.model.Alternativa
import com.sarahpena.rutasmart.domain.model.CriteriosIncompletosException
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Geo
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.MedioDeTransporte
import com.sarahpena.rutasmart.domain.model.Medios
import com.sarahpena.rutasmart.domain.model.Parada
import com.sarahpena.rutasmart.domain.model.Ruta
import com.sarahpena.rutasmart.domain.model.Tarifas
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.domain.model.Tramo
import com.sarahpena.rutasmart.domain.motor.MotorPuntaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del modelo orientado a objetos: paradas, rutas, medios (polimorfismo), alternativas y criterios. */
class ModeloTest {
    private val a = Parada("a", "A", 4.60, -74.08)
    private val b = Parada("b", "B", 4.61, -74.08)   // ~1,11 km al norte de A
    private val c = Parada("c", "C", 4.62, -74.08)
    private val lineaTm = Linea("B", "TM B", TipoMedio.METRO)
    private val lineaTm2 = Linea("K", "TM K", TipoMedio.METRO)
    private val lineaSitp = Linea("S", "SITP", TipoMedio.BUS)

    @Test(expected = IllegalArgumentException::class)
    fun paradaConCoordenadasInvalidasSeRechaza() { Parada("x", "X", 95.0, 0.0) }

    @Test(expected = IllegalArgumentException::class)
    fun rutaNecesitaDosParadas() { Ruta(listOf(a)) }

    @Test fun haversineCalculaDistanciaConocida() {
        // 0,01° de latitud ≈ 1,112 km
        assertEquals(1.112, Geo.distanciaKm(a, b), 0.01)
    }

    @Test fun rutaSumaLaDistanciaDeTodosLosTramos() {
        val r = Ruta(listOf(a, b, c))
        assertEquals(Geo.distanciaKm(a, b) + Geo.distanciaKm(b, c), r.calcularDistanciaTotal(), 1e-9)
        assertEquals(a, r.origen); assertEquals(c, r.destino)
    }

    @Test fun losCincoMediosCumplenElContratoComun() {
        val medios: Array<MedioDeTransporte> = Medios.todos()
        assertEquals(5, medios.size)
        for (m in medios) { // polimorfismo: se tratan igual sin conocer la clase concreta
            assertTrue(m.nombre, m.calcularDuracion(5.0, false) > 0)
            assertTrue(m.calcularCosto(5.0) >= 0)
            assertTrue(m.comodidad in 1..5)
        }
    }

    @Test fun caminarYPedalearSonGratisYNoContaminan() {
        assertEquals(0, Medios.caminata.calcularCosto(3.0)); assertEquals(0, Medios.bicicleta.calcularCosto(3.0))
        assertEquals(0, Medios.caminata.co2GramosPorKm); assertEquals(0, Medios.bicicleta.co2GramosPorKm)
    }

    @Test fun taxiCobraMinimoYLuegoPorKilometro() {
        assertEquals(Tarifas.TAXI_MINIMO, Medios.taxi.calcularCosto(0.5))
        assertEquals(Tarifas.TAXI_ARRANQUE + Tarifas.TAXI_POR_KM * 10, Medios.taxi.calcularCosto(10.0))
    }

    @Test fun horaPicoHaceMasLentosLosBusesYTaxis() {
        assertTrue(Medios.bus.calcularDuracion(10.0, true) > Medios.bus.calcularDuracion(10.0, false))
        assertTrue(Medios.taxi.calcularDuracion(10.0, true) > Medios.taxi.calcularDuracion(10.0, false))
        assertEquals(Medios.caminata.calcularDuracion(2.0, true), Medios.caminata.calcularDuracion(2.0, false), 1e-9)
        assertEquals(1, Medios.metro.comodidadEn(true))
    }

    @Test fun transbordosCuentanSoloLosCambiosDeVehiculo() {
        val alt = Alternativa("x", "X", listOf(
            Tramo(Medios.caminata, Ruta(listOf(a, b))),
            Tramo(Medios.bus, Ruta(listOf(b, c)), lineaSitp),
            Tramo(Medios.metro, Ruta(listOf(c, a)), lineaTm)
        ), false)
        assertEquals(1, alt.transbordos)
    }

    @Test fun costoCobraUnPasajeYTransbordoEntreSistemas() {
        val alt = Alternativa("x", "X", listOf(
            Tramo(Medios.bus, Ruta(listOf(a, b)), lineaSitp),
            Tramo(Medios.metro, Ruta(listOf(b, c)), lineaTm)
        ), false)
        assertEquals(Tarifas.PASAJE_PUBLICO + Tarifas.TRANSBORDO_ENTRE_SISTEMAS, alt.costoTotal)
    }

    @Test fun cambiarDeTroncalDeTransMilenioNoSeCobra() {
        val alt = Alternativa("x", "X", listOf(
            Tramo(Medios.metro, Ruta(listOf(a, b)), lineaTm),
            Tramo(Medios.metro, Ruta(listOf(b, c)), lineaTm2)
        ), false)
        assertEquals(Tarifas.PASAJE_PUBLICO, alt.costoTotal)
    }

    @Test fun unaSolaAlternativaObtienePuntajePerfecto() {
        val alt = Alternativa("t", "Taxi", listOf(Tramo(Medios.taxi, Ruta(listOf(a, c)))), false)
        val p = MotorPuntaje.clasificar(listOf(alt), CriteriosUsuario())
        assertEquals(100.0, p.first().puntaje, 1e-9)
    }

    @Test(expected = CriteriosIncompletosException::class)
    fun criteriosEnCeroNoSonValidos() { CriteriosUsuario(0, 0, 0, 0, 0).validar() }

    @Test(expected = CriteriosIncompletosException::class)
    fun sinMediosPermitidosNoEsValido() { CriteriosUsuario(mediosPermitidos = emptySet()).validar() }

    @Test(expected = IllegalArgumentException::class)
    fun pesoFueraDeRangoSeRechaza() { CriteriosUsuario(tiempo = 6) }

    @Test fun resumenMuestraLasPrioridadesMasAltas() {
        assertEquals("prioriza costo y ambiente · hora pico", CriteriosUsuario(1, 5, 0, 0, 4, horaPico = true).resumen())
    }
}
