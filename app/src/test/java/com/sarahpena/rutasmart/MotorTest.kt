package com.sarahpena.rutasmart

import com.sarahpena.rutasmart.data.red.RedBogota
import com.sarahpena.rutasmart.domain.casosdeuso.BuscarLugarUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.ObtenerAlternativasUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.RecomendarRutaUseCase
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.domain.model.Parada
import com.sarahpena.rutasmart.domain.model.RutaNoEncontradaException
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.domain.model.UbicacionInvalidaException
import com.sarahpena.rutasmart.domain.motor.MotorPuntaje
import com.sarahpena.rutasmart.domain.motor.Planificador
import com.sarahpena.rutasmart.domain.motor.RedTransporte
import com.sarahpena.rutasmart.domain.model.Linea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del grafo de transporte, el planificador de rutas, el motor de puntaje y los casos de uso. */
class MotorTest {
    private val red = RedBogota.crear()
    private val planificador = Planificador(red)
    private val buscar = BuscarLugarUseCase(RedBogota.LUGARES)
    private val obtener = ObtenerAlternativasUseCase(red)
    private fun lugar(n: String): Lugar = buscar(n, "lugar")

    // ---------------- RedTransporte (HashMap + lista de adyacencia)

    @Test fun redDeBogotaSeConstruyeCompleta() {
        assertEquals(10, red.lineas.size)
        assertTrue(red.totalParadas >= 45)
        assertNotNull(red.parada("tm-portal-norte"))
    }

    @Test fun lasConexionesSonBidireccionales() {
        assertTrue(red.conexionesDe("tm-toberin").any { it.destinoId == "tm-portal-norte" })
        assertTrue(red.conexionesDe("tm-portal-norte").any { it.destinoId == "tm-toberin" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun paradaDuplicadaSeRechaza() {
        val r = RedTransporte(); val p = Parada("x", "X", 4.6, -74.0)
        r.agregarParada(p); r.agregarParada(p)
    }

    @Test(expected = IllegalArgumentException::class)
    fun lineaConParadaInexistenteSeRechaza() {
        val r = RedTransporte(); r.agregarParada(Parada("x", "X", 4.6, -74.0))
        r.agregarLinea(Linea("L", "L", TipoMedio.BUS), listOf("x", "no-existe"))
    }

    // ---------------- Planificador

    @Test fun trayectoLargoIncluyeTaxiYTransportePublico() {
        val alts = planificador.generar(lugar("Universidad de San Buenaventura"), lugar("Plaza de Bolívar"), CriteriosUsuario())
        assertTrue(alts.any { it.id == "taxi" })
        val publico = alts.first { it.medios.any { m -> m.esPublico } }
        assertEquals("Universidad de San Buenaventura", publico.tramos.first().ruta.origen.nombre)
        assertEquals("Plaza de Bolívar", publico.tramos.last().ruta.destino.nombre)
        assertFalse(alts.any { it.id == "caminata" }) // demasiado lejos para caminar
    }

    @Test fun trayectoCortoPermiteCaminar() {
        val alts = planificador.generar(lugar("Zona T"), lugar("Parque de la 93"), CriteriosUsuario())
        assertTrue(alts.any { it.id == "caminata" })
    }

    @Test fun respetaLosMediosPermitidos() {
        val solo = CriteriosUsuario(mediosPermitidos = setOf(TipoMedio.BUS, TipoMedio.METRO, TipoMedio.CAMINATA))
        val alts = planificador.generar(lugar("Unicentro"), lugar("Universidad Nacional"), solo)
        assertTrue(alts.none { TipoMedio.TAXI in it.medios || TipoMedio.BICICLETA in it.medios })
    }

    @Test fun noRepiteAlternativasIguales() {
        val alts = planificador.generar(lugar("Portal 80"), lugar("Zona T"), CriteriosUsuario())
        assertEquals(alts.size, alts.map { it.firma }.toSet().size)
    }

    @Test(expected = UbicacionInvalidaException::class)
    fun origenIgualAlDestinoLanzaUbicacionInvalida() {
        planificador.generar(lugar("Unicentro"), lugar("Unicentro"), CriteriosUsuario())
    }

    @Test(expected = RutaNoEncontradaException::class)
    fun sinCombinacionPosibleLanzaRutaNoEncontrada() {
        // Desde el aeropuerto no hay estación de TransMilenio a distancia caminable.
        planificador.generar(lugar("Aeropuerto"), lugar("Plaza de Bolívar"), CriteriosUsuario(mediosPermitidos = setOf(TipoMedio.METRO)))
    }

    @Test fun transportePublicoUsaTransbordoEntreSistemas() {
        val alts = planificador.generar(lugar("Universidad de San Buenaventura"), lugar("Plaza de Bolívar"), CriteriosUsuario())
        val mixta = alts.first { TipoMedio.BUS in it.medios && TipoMedio.METRO in it.medios }
        assertTrue(mixta.transbordos >= 1)
    }

    // ---------------- Motor de puntaje

    @Test fun rankingOrdenadoYRecomendadaEsLaDeMayorPuntaje() {
        val r = obtener(lugar("Unicentro"), lugar("Universidad Nacional"), CriteriosUsuario())
        assertEquals((1..r.size).toList(), r.map { it.posicion })
        assertTrue(r.zipWithNext().all { (x, y) -> x.puntaje >= y.puntaje })
        assertTrue(r.first().esRecomendada)
        assertTrue(r.all { it.puntaje in 0.0..100.0 })
    }

    @Test fun siSoloImportaElCostoGanaLaMasBarata() {
        val crit = CriteriosUsuario(tiempo = 0, costo = 5, comodidad = 0, transbordos = 0, ambiental = 0)
        val r = obtener(lugar("Universidad de San Buenaventura"), lugar("Plaza de Bolívar"), crit)
        assertEquals(r.minOf { it.alternativa.costoTotal }, r.first().alternativa.costoTotal)
    }

    @Test fun siSoloImportaElTiempoGanaLaMasRapida() {
        val crit = CriteriosUsuario(tiempo = 5, costo = 0, comodidad = 0, transbordos = 0, ambiental = 0)
        val r = obtener(lugar("Portal Américas"), lugar("Centro Internacional"), crit)
        assertEquals(r.minOf { it.alternativa.tiempoTotalMinutos }, r.first().alternativa.tiempoTotalMinutos)
    }

    @Test fun mejorConAcumuladorCoincideConElRanking() {
        val crit = CriteriosUsuario()
        val alts = planificador.generar(lugar("Titán Plaza"), lugar("Parque Simón Bolívar"), crit)
        assertEquals(MotorPuntaje.clasificar(alts, crit).first().alternativa.id, MotorPuntaje.mejor(alts, crit).id)
    }

    @Test fun enHoraPicoElTaxiTardaMas() {
        val o = lugar("Universidad de San Buenaventura"); val d = lugar("Plaza de Bolívar")
        val normal = planificador.generar(o, d, CriteriosUsuario()).first { it.id == "taxi" }
        val pico = planificador.generar(o, d, CriteriosUsuario(horaPico = true)).first { it.id == "taxi" }
        assertTrue(pico.tiempoTotalMinutos > normal.tiempoTotalMinutos)
    }

    // ---------------- Casos de uso

    @Test fun buscarLugarIgnoraTildesYMayusculas() {
        assertEquals("Plaza de Bolívar", buscar("PLAZA DE BOLIVAR", "destino").nombre)
        assertEquals("Titán Plaza", buscar("titan", "destino").nombre)
    }

    @Test(expected = UbicacionInvalidaException::class)
    fun lugarAmbiguoPideElegirDeLaLista() { buscar("portal", "origen") } // Portal Norte, Portal 80, Portal Américas

    @Test(expected = UbicacionInvalidaException::class)
    fun lugarVacioSeRechaza() { buscar("  ", "origen") }

    @Test fun sugerenciasFiltranYLimitan() {
        val s = buscar.sugerencias("portal", max = 2)
        assertEquals(2, s.size)
        assertTrue(s.all { it.nombre.startsWith("Portal") })
        assertTrue(buscar.sugerencias("").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun obtenerAlternativasSinOrigenFallaTemprano() { obtener(null, lugar("Unicentro"), CriteriosUsuario()) }

    @Test fun explicacionMencionaLaRecomendadaYSusDatos() {
        val r = obtener(lugar("Universidad de San Buenaventura"), lugar("Plaza de Bolívar"), CriteriosUsuario())
        val texto = RecomendarRutaUseCase().explicar(r, CriteriosUsuario())
        assertTrue(texto.contains(r.first().alternativa.titulo))
        assertTrue(texto.contains("${r.first().alternativa.tiempoTotalMinutos} min"))
    }

    @Test fun formatoDePesosColombianos() {
        assertEquals("$36.900", RecomendarRutaUseCase.formatearPesos(36900))
        assertEquals("$1.234.567", RecomendarRutaUseCase.formatearPesos(1234567))
        assertEquals("$0", RecomendarRutaUseCase.formatearPesos(0))
    }
}
